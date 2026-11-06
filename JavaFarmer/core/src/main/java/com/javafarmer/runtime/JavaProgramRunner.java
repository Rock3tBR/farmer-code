package com.javafarmer.runtime;

import com.javafarmer.api.FarmerBot;
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

import javax.tools.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Compiles a deliberately restricted Java subset and runs it in a short-lived child JVM. */
public final class JavaProgramRunner {
    private static final int MAX_SOURCE_CHARS = 18_000;
    private static final int MAX_OUTPUT_BYTES = 256 * 1024;
    private static final int MAX_ACTIONS = 250;
    private static final long TIMEOUT_SECONDS = 3;
    private static final Set<String> API_METHODS = Set.of(
            "move", "till", "plant", "water", "waitCycles", "harvest", "sell", "log",
            "getX", "getY", "getFarmSize", "canHarvest", "getCrop", "getGround", "isWatered", "isMyLand",
            "getSeeds", "getItems", "getCoins", "getDay"
    );
    private static final Set<String> SENSOR_METHODS = Set.of(
            "getX", "getY", "getFarmSize", "canHarvest", "getCrop", "getGround", "isWatered", "isMyLand",
            "getSeeds", "getItems", "getCoins", "getDay"
    );
    /** Pure math helpers from java.lang.Math. */
    private static final Set<String> MATH_METHODS = Set.of(
            "abs", "min", "max", "floorMod", "floorDiv", "sqrt", "pow", "round", "random"
    );
    /** String methods callable on values (receivers can only be primitives, Strings or arrays). */
    private static final Set<String> VALUE_METHODS = Set.of(
            "equals", "equalsIgnoreCase", "isEmpty", "length", "startsWith", "endsWith", "contains",
            "charAt", "toUpperCase", "toLowerCase", "trim", "indexOf", "substring", "compareTo"
    );
    private static final Set<String> BASE_TYPES = Set.of("int", "long", "double", "float", "boolean", "char", "String");

    public ProgramResult run(String source) {
        return run(source, null);
    }

    /**
     * Validates, compiles and runs the program in a child JVM.
     *
     * @param farmSnapshot optional {@code Farm.snapshot()} text; it is written into the program's private
     *                     temporary directory so {@code FarmerBot} sensors can mirror the game state.
     */
    public ProgramResult run(String source, String farmSnapshot) {
        if (source == null || source.isBlank()) return ProgramResult.error("Digite um programa Java antes de executar.");
        if (source.length() > MAX_SOURCE_CHARS) return ProgramResult.error("O programa ultrapassou o limite de 18.000 caracteres.");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return ProgramResult.error("Compilador Java não encontrado. Instale um JDK (não apenas JRE) para executar programas no terminal.");
        }

        try {
            SafeProgramValidator validator = new SafeProgramValidator(compiler);
            String validationError = validator.validate(source);
            if (validationError != null) return ProgramResult.error(validationError);
            Set<ProgramFeature> features = validator.features();

            Path tempRoot = Files.createTempDirectory("java-farmer-program-");
            try {
                Path classes = Files.createDirectories(tempRoot.resolve("classes"));
                Path sourceFile = tempRoot.resolve("PlayerProgram.java");
                Path outputFile = tempRoot.resolve("program-output.txt");
                Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
                if (farmSnapshot != null && farmSnapshot.length() < 4 * 1024 * 1024) {
                    Files.writeString(tempRoot.resolve(FarmerBot.SNAPSHOT_FILE), farmSnapshot, StandardCharsets.UTF_8);
                }

                DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
                try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
                    Iterable<? extends JavaFileObject> units = manager.getJavaFileObjects(sourceFile.toFile());
                    List<String> options = List.of(
                            "-proc:none", "-encoding", "UTF-8", "-classpath", absoluteClasspath(),
                            "-d", classes.toString(), "-g:none", "-nowarn"
                    );
                    Boolean compiled = compiler.getTask(null, manager, diagnostics, options, null, units).call();
                    if (!Boolean.TRUE.equals(compiled)) return ProgramResult.error(formatDiagnostics(diagnostics));
                }

                // The child runs inside the temporary directory, so relative classpath entries must be resolved first.
                String classpath = classes + System.getProperty("path.separator") + absoluteClasspath();
                Path javaExecutable = javaExecutable();
                Process process = new ProcessBuilder(
                        javaExecutable.toString(), "-Xms16m", "-Xmx64m", "-XX:MaxMetaspaceSize=48m",
                        "-Djava.awt.headless=true", "-cp", classpath, "PlayerProgram"
                ).directory(tempRoot.toFile()).redirectErrorStream(true).start();

                // Drain child output with a hard cap; checking the file only after exit would
                // allow a fast logging loop to consume unbounded disk space before timeout.
                AtomicBoolean outputLimitExceeded = new AtomicBoolean(false);
                Thread outputReader = new Thread(() -> copyBoundedOutput(process, outputFile, outputLimitExceeded),
                        "java-farmer-program-output");
                outputReader.setDaemon(true);
                outputReader.start();

                boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    outputReader.join(1_000);
                    return ProgramResult.error("Seu programa demorou mais de 3 segundos. Verifique se existe um loop infinito.");
                }
                outputReader.join(1_000);
                if (outputLimitExceeded.get()) {
                    return ProgramResult.error("O programa gerou saída demais e foi interrompido.");
                }
                if (outputReader.isAlive()) {
                    process.destroyForcibly();
                    return ProgramResult.error("Não foi possível finalizar a leitura da saída do programa.");
                }
                List<Action> actions = parseActions(Files.exists(outputFile)
                        ? Files.readAllLines(outputFile, StandardCharsets.UTF_8) : List.of());
                if (actions.size() > MAX_ACTIONS) return ProgramResult.error("Limite de 250 ações por execução excedido.");
                if (process.exitValue() != 0) return ProgramResult.error("O programa terminou com erro. Revise o código e tente novamente.");
                return ProgramResult.success(actions, features);
            } finally {
                deleteTree(tempRoot);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ProgramResult.error("A execução foi interrompida.");
        } catch (Exception ex) {
            return ProgramResult.error("Não foi possível executar o programa: " + ex.getMessage());
        }
    }

    private static void copyBoundedOutput(Process process, Path outputFile, AtomicBoolean limitExceeded) {
        byte[] buffer = new byte[4_096];
        long total = 0;
        try (InputStream input = process.getInputStream(); OutputStream output = Files.newOutputStream(outputFile)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (total + read > MAX_OUTPUT_BYTES) {
                    limitExceeded.set(true);
                    process.destroyForcibly();
                    return;
                }
                output.write(buffer, 0, read);
                total += read;
            }
        } catch (IOException ignored) {
            // Process termination closes the stream; the caller checks timeout/overflow state.
        }
    }

    private static String absoluteClasspath() {
        String separator = System.getProperty("path.separator");
        StringJoiner joined = new StringJoiner(separator);
        for (String entry : System.getProperty("java.class.path", "").split(java.util.regex.Pattern.quote(separator))) {
            if (!entry.isBlank()) joined.add(Path.of(entry).toAbsolutePath().normalize().toString());
        }
        return joined.toString();
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }

    private static List<Action> parseActions(List<String> lines) {
        List<Action> result = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("@@JF|")) continue;
            String[] parts = line.split("\\|", 3);
            if (parts.length >= 2) result.add(new Action(parts[1], parts.length == 3 ? parts[2] : ""));
            if (result.size() > MAX_ACTIONS) break;
        }
        return result;
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder out = new StringBuilder("O Java encontrou um erro de compilação:\n");
        int count = 0;
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() != Diagnostic.Kind.ERROR) continue;
            out.append("Linha ").append(diagnostic.getLineNumber()).append(": ")
                    .append(diagnostic.getMessage(Locale.forLanguageTag("pt-BR"))).append('\n');
            if (++count >= 5) break;
        }
        return out.toString().trim();
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    public record Action(String type, String value) { }

    public record ProgramResult(boolean success, String message, List<Action> actions, Set<ProgramFeature> features) {
        static ProgramResult error(String message) { return new ProgramResult(false, message, List.of(), Set.of()); }
        static ProgramResult success(List<Action> actions, Set<ProgramFeature> features) {
            return new ProgramResult(true, "Programa executado com sucesso!", List.copyOf(actions), Set.copyOf(features));
        }
    }

    /**
     * A strict syntax allowlist. Allowed: one class {@code PlayerProgram} with static methods/fields of
     * primitive, String or array types; local variables; if/else, for, for-each, while, do-while, break,
     * continue, return; arithmetic/logic/ternary operators; arrays of primitives/Strings; calls to
     * {@code FarmerBot}, a few pure {@code Math} helpers, a few String methods and the player's own methods.
     * Not allowed: object creation, other classes, imports, reflection, files, network, threads, lambdas.
     */
    private static final class SafeProgramValidator {
        private final JavaCompiler compiler;
        private final Set<String> declaredMethods = new HashSet<>();
        private ClassTree clazz;

        private SafeProgramValidator(JavaCompiler compiler) { this.compiler = compiler; }

        String validate(String source) throws IOException {
            JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///PlayerProgram.java"), JavaFileObject.Kind.SOURCE) {
                @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
            };
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics,
                    List.of("-proc:none", "-encoding", "UTF-8"), null, List.of(file));
            CompilationUnitTree unit = task.parse().iterator().next();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) return "Erro de sintaxe na linha " + diagnostic.getLineNumber() + ".";
            }
            for (ImportTree imported : unit.getImports()) {
                if (imported.isStatic() || !imported.getQualifiedIdentifier().toString().equals("com.javafarmer.api.FarmerBot")) {
                    return "Por segurança, o terminal só permite importar com.javafarmer.api.FarmerBot.";
                }
            }
            if (unit.getTypeDecls().size() != 1 || !(unit.getTypeDecls().get(0) instanceof ClassTree declared)) {
                return "Crie apenas uma classe chamada PlayerProgram.";
            }
            clazz = declared;
            if (!clazz.getSimpleName().contentEquals("PlayerProgram") || clazz.getExtendsClause() != null
                    || !clazz.getImplementsClause().isEmpty() || !clazz.getTypeParameters().isEmpty()
                    || clazz.getKind() != Tree.Kind.CLASS) {
                return "A classe precisa se chamar PlayerProgram e não pode herdar de outras classes.";
            }
            boolean hasMain = false;
            for (Tree member : clazz.getMembers()) {
                if (member instanceof MethodTree method) {
                    if (method.getReturnType() == null) return "Construtores não são permitidos: use apenas métodos static.";
                    declaredMethods.add(method.getName().toString());
                }
            }
            for (Tree member : clazz.getMembers()) {
                if (member instanceof MethodTree method) {
                    String error = validateMethod(method);
                    if (error != null) return error;
                    if (isMain(method)) hasMain = true;
                } else if (member instanceof VariableTree field) {
                    if (!onlyModifiers(field.getModifiers(), true) || !field.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.STATIC)) {
                        return "Campos da classe precisam ser static (ex.: static int contador = 0;).";
                    }
                    if (!allowedType(field.getType()) || (field.getInitializer() != null && !validExpression(field.getInitializer()))) {
                        return "Campo \"" + field.getName() + "\" usa um tipo ou expressão não permitido.";
                    }
                } else {
                    return "Dentro de PlayerProgram use apenas métodos static e campos static.";
                }
            }
            if (!hasMain) return "Use exatamente: public static void main(String[] args).";
            return null;
        }

        private static boolean isMain(MethodTree method) {
            return method.getName().contentEquals("main") && method.getReturnType().toString().equals("void")
                    && method.getModifiers().getFlags().containsAll(Set.of(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.STATIC))
                    && method.getParameters().size() == 1 && method.getParameters().get(0).getType().toString().equals("String[]");
        }

        private String validateMethod(MethodTree method) {
            String name = method.getName().toString();
            if (!onlyModifiers(method.getModifiers(), false) || !method.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.STATIC)) {
                return "O método " + name + " precisa ser static (ex.: static void plantarLinha(int n)).";
            }
            if (!method.getTypeParameters().isEmpty() || !method.getThrows().isEmpty() || method.getBody() == null) {
                return "O método " + name + " usa recursos não permitidos (genéricos ou throws).";
            }
            boolean main = isMain(method);
            String returnType = method.getReturnType().toString();
            if (!main && !returnType.equals("void") && !allowedType(method.getReturnType())) {
                return "O método " + name + " retorna um tipo não permitido. Use int, double, boolean, String ou arrays.";
            }
            for (VariableTree parameter : method.getParameters()) {
                if (!main && !allowedType(parameter.getType())) return "Parâmetro \"" + parameter.getName() + "\" tem tipo não permitido.";
            }
            if (!validStatement(method.getBody())) {
                return "O método " + name + " usa um recurso Java bloqueado nesta versão. Permitido: variáveis, arrays, "
                        + "if/else, for, while, métodos static, Math e chamadas FarmerBot.";
            }
            return null;
        }

        private static boolean onlyModifiers(ModifiersTree modifiers, boolean field) {
            if (!modifiers.getAnnotations().isEmpty()) return false;
            Set<javax.lang.model.element.Modifier> allowed = field
                    ? Set.of(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.PRIVATE,
                        javax.lang.model.element.Modifier.STATIC, javax.lang.model.element.Modifier.FINAL)
                    : Set.of(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.PRIVATE,
                        javax.lang.model.element.Modifier.STATIC);
            return allowed.containsAll(modifiers.getFlags());
        }

        private static boolean allowedType(Tree type) {
            if (type == null) return false;
            String text = type.toString().replace(" ", "");
            int dims = 0;
            while (text.endsWith("[]")) { text = text.substring(0, text.length() - 2); dims++; }
            return dims <= 2 && BASE_TYPES.contains(text);
        }

        private boolean validStatement(StatementTree statement) {
            if (statement == null) return true;
            if (statement instanceof BlockTree block) return !block.isStatic() && block.getStatements().stream().allMatch(this::validStatement);
            if (statement instanceof ExpressionStatementTree expr) return validExpression(expr.getExpression());
            if (statement instanceof VariableTree variable) {
                return variable.getModifiers().getFlags().stream().allMatch(m -> m == javax.lang.model.element.Modifier.FINAL)
                        && variable.getModifiers().getAnnotations().isEmpty()
                        && allowedType(variable.getType())
                        && (variable.getInitializer() == null || validExpression(variable.getInitializer()));
            }
            if (statement instanceof ForLoopTree loop) {
                return loop.getInitializer().stream().allMatch(this::validStatement)
                        && (loop.getCondition() == null || validExpression(loop.getCondition()))
                        && loop.getUpdate().stream().allMatch(update -> validExpression(update.getExpression()))
                        && validStatement(loop.getStatement());
            }
            if (statement instanceof EnhancedForLoopTree loop) {
                return allowedType(loop.getVariable().getType()) && validExpression(loop.getExpression()) && validStatement(loop.getStatement());
            }
            if (statement instanceof WhileLoopTree loop) return validExpression(loop.getCondition()) && validStatement(loop.getStatement());
            if (statement instanceof DoWhileLoopTree loop) return validExpression(loop.getCondition()) && validStatement(loop.getStatement());
            if (statement instanceof IfTree conditional) return validExpression(conditional.getCondition())
                    && validStatement(conditional.getThenStatement())
                    && (conditional.getElseStatement() == null || validStatement(conditional.getElseStatement()));
            if (statement instanceof ReturnTree ret) return ret.getExpression() == null || validExpression(ret.getExpression());
            if (statement instanceof BreakTree br) return br.getLabel() == null;
            if (statement instanceof ContinueTree co) return co.getLabel() == null;
            return statement instanceof EmptyStatementTree;
        }

        private boolean validExpression(ExpressionTree expression) {
            if (expression == null) return true;
            if (expression instanceof LiteralTree) return true;
            if (expression instanceof IdentifierTree) return true; // unresolved identifiers are rejected by javac itself.
            if (expression instanceof MethodInvocationTree call) {
                if (!call.getTypeArguments().isEmpty() || !call.getArguments().stream().allMatch(this::validExpression)) return false;
                ExpressionTree target = call.getMethodSelect();
                if (target instanceof IdentifierTree own) return declaredMethods.contains(own.getName().toString());
                if (!(target instanceof MemberSelectTree select)) return false;
                String method = select.getIdentifier().toString();
                if (select.getExpression() instanceof IdentifierTree owner) {
                    String ownerName = owner.getName().toString();
                    if (ownerName.equals("FarmerBot")) return API_METHODS.contains(method);
                    if (ownerName.equals("Math")) return MATH_METHODS.contains(method);
                    // Upper-case identifiers are class names: no other classes may be called.
                    if (!ownerName.isEmpty() && Character.isUpperCase(ownerName.charAt(0))) return false;
                }
                return VALUE_METHODS.contains(method) && validExpression(select.getExpression());
            }
            if (expression instanceof MemberSelectTree select) {
                return select.getIdentifier().contentEquals("length") && validExpression(select.getExpression())
                        && !(select.getExpression() instanceof IdentifierTree id && !id.getName().isEmpty()
                            && Character.isUpperCase(id.getName().charAt(0)));
            }
            if (expression instanceof NewArrayTree array) {
                if (array.getType() != null && !allowedType(array.getType())) return false;
                return array.getDimensions().stream().allMatch(this::validExpression)
                        && (array.getInitializers() == null || array.getInitializers().stream().allMatch(this::validExpression));
            }
            if (expression instanceof ArrayAccessTree access) return validExpression(access.getExpression()) && validExpression(access.getIndex());
            if (expression instanceof BinaryTree binary) return validExpression(binary.getLeftOperand()) && validExpression(binary.getRightOperand());
            if (expression instanceof UnaryTree unary) return validExpression(unary.getExpression());
            if (expression instanceof ConditionalExpressionTree ternary) return validExpression(ternary.getCondition())
                    && validExpression(ternary.getTrueExpression()) && validExpression(ternary.getFalseExpression());
            if (expression instanceof TypeCastTree cast) return allowedType(cast.getType()) && !cast.getType().toString().equals("String")
                    && validExpression(cast.getExpression());
            if (expression instanceof AssignmentTree assignment) return validTarget(assignment.getVariable())
                    && validExpression(assignment.getExpression());
            if (expression instanceof CompoundAssignmentTree assignment) return validTarget(assignment.getVariable())
                    && validExpression(assignment.getExpression());
            if (expression instanceof ParenthesizedTree parenthesized) return validExpression(parenthesized.getExpression());
            return false;
        }

        private boolean validTarget(ExpressionTree target) {
            return target instanceof IdentifierTree || (target instanceof ArrayAccessTree access && validExpression(access));
        }

        /** Detects which Java concepts the (already validated) program uses. */
        Set<ProgramFeature> features() {
            EnumSet<ProgramFeature> found = EnumSet.noneOf(ProgramFeature.class);
            Set<String> called = new HashSet<>();
            new TreeScanner<Void, Integer>() {
                @Override public Void visitMethod(MethodTree node, Integer depth) {
                    if (!isMain(node)) {
                        if (!node.getReturnType().toString().equals("void")) found.add(ProgramFeature.RETURN_VALUE);
                    }
                    scan(node.getBody(), 0);
                    return null;
                }
                @Override public Void visitVariable(VariableTree node, Integer depth) {
                    found.add(ProgramFeature.VARIABLE);
                    if (node.getType() != null && node.getType().toString().contains("[]")) found.add(ProgramFeature.ARRAY);
                    return super.visitVariable(node, depth);
                }
                @Override public Void visitForLoop(ForLoopTree node, Integer depth) {
                    found.add(ProgramFeature.FOR);
                    if (depth != null && depth > 0) found.add(ProgramFeature.NESTED_LOOP);
                    // The loop counter (int i = 0) does not count as "using a variable" for missions.
                    for (StatementTree init : node.getInitializer()) {
                        if (init instanceof VariableTree counter) scan(counter.getInitializer(), depth);
                        else scan(init, depth);
                    }
                    scan(node.getCondition(), depth);
                    scan(node.getUpdate(), depth);
                    scan(node.getStatement(), depth + 1);
                    return null;
                }
                @Override public Void visitEnhancedForLoop(EnhancedForLoopTree node, Integer depth) {
                    found.add(ProgramFeature.FOR);
                    return loop(depth, () -> super.visitEnhancedForLoop(node, depth + 1));
                }
                @Override public Void visitWhileLoop(WhileLoopTree node, Integer depth) {
                    found.add(ProgramFeature.WHILE);
                    return loop(depth, () -> super.visitWhileLoop(node, depth + 1));
                }
                @Override public Void visitDoWhileLoop(DoWhileLoopTree node, Integer depth) {
                    found.add(ProgramFeature.WHILE);
                    return loop(depth, () -> super.visitDoWhileLoop(node, depth + 1));
                }
                private Void loop(Integer depth, java.util.function.Supplier<Void> body) {
                    if (depth != null && depth > 0) found.add(ProgramFeature.NESTED_LOOP);
                    return body.get();
                }
                @Override public Void visitIf(IfTree node, Integer depth) {
                    found.add(ProgramFeature.IF);
                    return super.visitIf(node, depth);
                }
                @Override public Void visitConditionalExpression(ConditionalExpressionTree node, Integer depth) {
                    found.add(ProgramFeature.TERNARY);
                    return super.visitConditionalExpression(node, depth);
                }
                @Override public Void visitBinary(BinaryTree node, Integer depth) {
                    if (node.getKind() == Tree.Kind.REMAINDER) found.add(ProgramFeature.MODULO);
                    if (node.getKind() == Tree.Kind.CONDITIONAL_AND || node.getKind() == Tree.Kind.CONDITIONAL_OR) found.add(ProgramFeature.LOGICAL);
                    return super.visitBinary(node, depth);
                }
                @Override public Void visitCompoundAssignment(CompoundAssignmentTree node, Integer depth) {
                    if (node.getKind() == Tree.Kind.REMAINDER_ASSIGNMENT) found.add(ProgramFeature.MODULO);
                    return super.visitCompoundAssignment(node, depth);
                }
                @Override public Void visitNewArray(NewArrayTree node, Integer depth) {
                    found.add(ProgramFeature.ARRAY);
                    return super.visitNewArray(node, depth);
                }
                @Override public Void visitArrayAccess(ArrayAccessTree node, Integer depth) {
                    found.add(ProgramFeature.ARRAY);
                    return super.visitArrayAccess(node, depth);
                }
                @Override public Void visitMethodInvocation(MethodInvocationTree node, Integer depth) {
                    ExpressionTree target = node.getMethodSelect();
                    if (target instanceof IdentifierTree own) called.add(own.getName().toString());
                    if (target instanceof MemberSelectTree select && select.getExpression() instanceof IdentifierTree owner
                            && owner.getName().contentEquals("FarmerBot")) {
                        String method = select.getIdentifier().toString();
                        if (method.equals("log")) found.add(ProgramFeature.LOG);
                        if (SENSOR_METHODS.contains(method)) found.add(ProgramFeature.SENSE);
                    }
                    return super.visitMethodInvocation(node, depth);
                }
            }.scan(clazz, 0);
            for (String name : declaredMethods) {
                if (!name.equals("main") && called.contains(name)) found.add(ProgramFeature.METHOD);
            }
            return found;
        }
    }
}
