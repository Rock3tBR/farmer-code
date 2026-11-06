package com.javafarmer.runtime;

import com.javafarmer.api.FarmerBot;
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;

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
            "move", "till", "plant", "water", "waitCycles", "harvest", "log"
    );

    public ProgramResult run(String source) {
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

            Path tempRoot = Files.createTempDirectory("java-farmer-program-");
            try {
                Path classes = Files.createDirectories(tempRoot.resolve("classes"));
                Path sourceFile = tempRoot.resolve("PlayerProgram.java");
                Path outputFile = tempRoot.resolve("program-output.txt");
                Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

                DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
                try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
                    Iterable<? extends JavaFileObject> units = manager.getJavaFileObjects(sourceFile.toFile());
                    List<String> options = List.of(
                            "-proc:none", "-encoding", "UTF-8", "-classpath", System.getProperty("java.class.path"),
                            "-d", classes.toString(), "-g:none", "-nowarn"
                    );
                    Boolean compiled = compiler.getTask(null, manager, diagnostics, options, null, units).call();
                    if (!Boolean.TRUE.equals(compiled)) return ProgramResult.error(formatDiagnostics(diagnostics));
                }

                String classpath = classes + System.getProperty("path.separator") + System.getProperty("java.class.path");
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
                return ProgramResult.success(actions);
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

    public record ProgramResult(boolean success, String message, List<Action> actions) {
        static ProgramResult error(String message) { return new ProgramResult(false, message, List.of()); }
        static ProgramResult success(List<Action> actions) { return new ProgramResult(true, "Programa executado com sucesso!", List.copyOf(actions)); }
    }

    /** A strict syntax allowlist: no reflection, file/network APIs, object creation, imports or arbitrary calls. */
    private static final class SafeProgramValidator {
        private final JavaCompiler compiler;
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
            if (unit.getTypeDecls().size() != 1 || !(unit.getTypeDecls().get(0) instanceof ClassTree clazz)) {
                return "Crie apenas uma classe chamada PlayerProgram.";
            }
            if (!clazz.getSimpleName().contentEquals("PlayerProgram") || clazz.getExtendsClause() != null || !clazz.getImplementsClause().isEmpty()) {
                return "A classe precisa se chamar PlayerProgram e não pode herdar de outras classes.";
            }
            if (clazz.getMembers().size() != 1 || !(clazz.getMembers().get(0) instanceof MethodTree method)) {
                return "A classe deve conter somente o método main padrão.";
            }
            if (!method.getName().contentEquals("main") || method.getReturnType() == null || !method.getReturnType().toString().equals("void")
                    || !method.getModifiers().getFlags().containsAll(Set.of(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.STATIC))
                    || method.getParameters().size() != 1 || !method.getParameters().get(0).getType().toString().equals("String[]")
                    || method.getBody() == null || !method.getThrows().isEmpty()) {
                return "Use exatamente: public static void main(String[] args).";
            }
            if (!validStatement(method.getBody())) {
                return "O terminal aceita variáveis simples, operações, if/else, for, while e chamadas FarmerBot. Outros recursos Java ficam bloqueados nesta versão para proteger o jogo.";
            }
            return null;
        }

        private boolean validStatement(StatementTree statement) {
            if (statement instanceof BlockTree block) return block.getStatements().stream().allMatch(this::validStatement);
            if (statement instanceof ExpressionStatementTree expr) return validExpression(expr.getExpression());
            if (statement instanceof VariableTree variable) {
                String type = variable.getType() == null ? "" : variable.getType().toString();
                return Set.of("int", "long", "double", "float", "boolean", "String").contains(type)
                        && variable.getInitializer() != null && validExpression(variable.getInitializer());
            }
            if (statement instanceof ForLoopTree loop) {
                return loop.getInitializer().stream().allMatch(this::validStatement)
                        && (loop.getCondition() == null || validExpression(loop.getCondition()))
                        && loop.getUpdate().stream().allMatch(update -> validExpression(update.getExpression()))
                        && validStatement(loop.getStatement());
            }
            if (statement instanceof WhileLoopTree loop) return validExpression(loop.getCondition()) && validStatement(loop.getStatement());
            if (statement instanceof IfTree conditional) return validExpression(conditional.getCondition())
                    && validStatement(conditional.getThenStatement())
                    && (conditional.getElseStatement() == null || validStatement(conditional.getElseStatement()));
            if (statement instanceof ReturnTree ret) return ret.getExpression() == null;
            return statement instanceof EmptyStatementTree || statement instanceof BreakTree || statement instanceof ContinueTree;
        }

        private boolean validExpression(ExpressionTree expression) {
            if (expression instanceof LiteralTree) return true;
            if (expression instanceof IdentifierTree) return true; // unresolved identifiers are rejected by javac itself.
            if (expression instanceof MethodInvocationTree call) {
                if (!(call.getMethodSelect() instanceof MemberSelectTree select)) return false;
                if (!(select.getExpression() instanceof IdentifierTree owner) || !owner.getName().contentEquals("FarmerBot")) return false;
                if (!API_METHODS.contains(select.getIdentifier().toString()) || !call.getTypeArguments().isEmpty()) return false;
                return call.getArguments().stream().allMatch(this::validExpression);
            }
            if (expression instanceof BinaryTree binary) return validExpression(binary.getLeftOperand()) && validExpression(binary.getRightOperand());
            if (expression instanceof UnaryTree unary) return validExpression(unary.getExpression());
            if (expression instanceof AssignmentTree assignment) return assignment.getVariable() instanceof IdentifierTree
                    && validExpression(assignment.getExpression());
            if (expression instanceof CompoundAssignmentTree assignment) return assignment.getVariable() instanceof IdentifierTree
                    && validExpression(assignment.getExpression());
            if (expression instanceof ParenthesizedTree parenthesized) return validExpression(parenthesized.getExpression());
            return false;
        }
    }
}
