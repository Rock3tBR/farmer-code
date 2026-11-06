package com.javafarmer.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ScreenUtils;
import com.javafarmer.render.FarmRenderer;
import com.javafarmer.runtime.JavaProgramRunner;
import com.javafarmer.save.GameSave;
import com.javafarmer.save.GameSettings;
import com.javafarmer.ui.ClayUi;
import com.javafarmer.ui.ClayUi.Icon;
import com.javafarmer.world.FarmTile;
import com.javafarmer.world.FarmWorld;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.javafarmer.render.ClayPalette.*;

/** First playable vertical slice of Java Farmer. */
public final class JavaFarmerGame extends ApplicationAdapter {
    private enum Screen { MENU, PLAYING, SETTINGS, CHALLENGES }

    private static final String SEP = "  ·  "; // middle dot exists in the default Latin-1 font (• does not)
    private static final float CODE_SCALE = 0.92f;
    private static final float CODE_LINE_HEIGHT = 21f;
    private static final Set<String> JAVA_KEYWORDS = Set.of("public", "private", "static", "void", "class", "import",
            "int", "long", "double", "float", "boolean", "char", "String", "for", "while", "do", "if", "else",
            "return", "new", "true", "false", "final", "var", "break", "continue");

    private final Json json = new Json();
    private final JavaProgramRunner programRunner = new JavaProgramRunner();

    private Screen screen = Screen.MENU;
    private Screen screenBeforeChallenges = Screen.PLAYING;
    private boolean terminalOpen;
    private final StringBuilder code = new StringBuilder();
    private int cursor;
    private int scrollTop;
    private int codeVersion;
    private int highlightedVersion = -1;
    private final List<String> highlightedLines = new ArrayList<>();
    private String terminalMessage = "Terminal pronto. Escreva Java e execute seu primeiro programa.";
    private boolean terminalMessageOk = true;
    private String lastLog = "Bem-vindo à fazenda! Use WASD para explorar.";
    private GameSave save;
    private GameSettings settings;
    private FarmWorld world;

    private FarmRenderer farmRenderer;
    private ClayUi ui;
    private final GlyphLayout measure = new GlyphLayout();
    private final Color overlay = new Color();

    private Music ambience;
    private Sound clickSound;
    private Sound harvestSound;
    private float screenWidth;
    private float screenHeight;
    private boolean saveLoaded;
    private int frameCounter;
    private float uiTime;
    /** Set when the terminal is opened with the T key, so that key's typed character is not inserted. */
    private boolean swallowNextTyped;

    @Override
    public void create() {
        loadData();
        farmRenderer = new FarmRenderer();
        ui = new ClayUi();
        createExampleProgram();
        loadAudio();
        Gdx.input.setInputProcessor(new GameInput());
        Gdx.graphics.setForegroundFPS(settings.targetFps());
    }

    // ------------------------------------------------------------------ data

    private void loadData() {
        save = readJson("saves/slot1.json", GameSave.class, new GameSave());
        settings = readJson("saves/settings.json", GameSettings.class, new GameSettings());
        sanitizeLoadedData();
        rollDailyChallengeIfNeeded();
        world = new FarmWorld(save.worldSeed);
        world.restorePlayer(save.playerX, save.playerZ, save.cycles);
        if (save.tiles != null) {
            for (GameSave.TileSnapshot tile : save.tiles) {
                world.restoreTile(tile.x, tile.z, tile.tilled, tile.crop, tile.growth, tile.watered);
            }
        }
        saveLoaded = save.totalPlants > 0 || save.totalHarvests > 0 || save.playerX != 0 || save.playerZ != 0;
    }

    private <T> T readJson(String path, Class<T> type, T fallback) {
        try {
            var file = Gdx.files.local(path);
            // Reject unexpectedly large/corrupt local save files before parsing them.
            if (file.exists() && file.length() <= 8L * 1024L * 1024L) {
                T value = json.fromJson(type, file);
                if (value != null) return value;
            }
        } catch (Exception ignored) { }
        return fallback;
    }

    private void sanitizeLoadedData() {
        settings.soundVolume = Math.max(0f, Math.min(1f, settings.soundVolume));
        settings.brightness = Math.max(45, Math.min(115, settings.brightness));
        if (!List.of("BAIXO", "MEDIO", "ALTO").contains(settings.graphics)) settings.graphics = "MEDIO";
        save.coins = Math.max(0, save.coins);
        save.points = Math.max(0, save.points);
        save.seeds = Math.max(0, save.seeds);
        save.playerX = Math.max(-1_000_000, Math.min(1_000_000, save.playerX));
        save.playerZ = Math.max(-1_000_000, Math.min(1_000_000, save.playerZ));
        save.cycles = Math.max(0, save.cycles);
        save.totalPlants = Math.max(0, save.totalPlants);
        save.totalHarvests = Math.max(0, save.totalHarvests);
        save.dailyTarget = Math.max(1, Math.min(100, save.dailyTarget));
        save.dailyProgress = Math.max(0, Math.min(save.dailyTarget, save.dailyProgress));
        if (save.dailyKind == null) save.dailyKind = "PLANT";
        if (save.dailyTitle == null) save.dailyTitle = "";
        if (save.tiles == null) save.tiles = new ArrayList<>();
        if (save.tiles.size() > 10_000) save.tiles = new ArrayList<>(save.tiles.subList(0, 10_000));
    }

    /** Rolls a new daily challenge when the local date changed. Returns true if a new one was created. */
    private boolean rollDailyChallengeIfNeeded() {
        LocalDate today = LocalDate.now();
        if (today.toString().equals(save.dailyDate)) return false;
        save.dailyDate = today.toString();
        int variant = Math.floorMod((int) today.toEpochDay(), 3);
        save.dailyProgress = 0;
        save.dailyClaimed = false;
        switch (variant) {
            case 0 -> { save.dailyTitle = "Plante 3 cultivos"; save.dailyKind = "PLANT"; save.dailyTarget = 3; }
            case 1 -> { save.dailyTitle = "Colha 2 cultivos"; save.dailyKind = "HARVEST"; save.dailyTarget = 2; }
            default -> { save.dailyTitle = "Execute 1 programa Java"; save.dailyKind = "PROGRAM"; save.dailyTarget = 1; }
        }
        return true;
    }

    private void saveGame() {
        try {
            save.playerX = world.playerX();
            save.playerZ = world.playerZ();
            save.cycles = world.cycles();
            save.tiles = new ArrayList<>();
            for (FarmTile tile : world.getTiles().values()) {
                if (!tile.tilled && !tile.hasCrop()) continue;
                GameSave.TileSnapshot snapshot = new GameSave.TileSnapshot();
                snapshot.x = tile.x;
                snapshot.z = tile.z;
                snapshot.tilled = tile.tilled;
                snapshot.crop = tile.crop;
                snapshot.growth = tile.growth;
                snapshot.watered = tile.watered;
                save.tiles.add(snapshot);
            }
            var saveFile = Gdx.files.local("saves/slot1.json");
            saveFile.parent().mkdirs();
            saveFile.writeString(json.prettyPrint(save), false, "UTF-8");
            var settingsFile = Gdx.files.local("saves/settings.json");
            settingsFile.parent().mkdirs();
            settingsFile.writeString(json.prettyPrint(settings), false, "UTF-8");
            lastLog = "Progresso salvo com sucesso.";
        } catch (Exception ex) {
            lastLog = "Não foi possível salvar: " + ex.getMessage();
        }
    }

    private void createExampleProgram() {
        code.append("import com.javafarmer.api.FarmerBot;\n\n")
                .append("public class PlayerProgram {\n")
                .append("    public static void main(String[] args) {\n")
                .append("        FarmerBot.till();\n")
                .append("        FarmerBot.plant(\"WHEAT\");\n")
                .append("        FarmerBot.waitCycles(3);\n")
                .append("        FarmerBot.harvest();\n")
                .append("    }\n")
                .append("}\n");
        cursor = code.length();
        codeVersion++;
    }

    private void loadAudio() {
        try {
            ambience = Gdx.audio.newMusic(Gdx.files.internal("audio/cozy-loop.wav"));
            ambience.setLooping(true);
            ambience.setVolume(settings.soundVolume);
            if (settings.soundVolume > 0.001f) ambience.play();
            clickSound = Gdx.audio.newSound(Gdx.files.internal("audio/ui-click.wav"));
            harvestSound = Gdx.audio.newSound(Gdx.files.internal("audio/harvest.wav"));
        } catch (Exception ex) {
            ambience = null; // Audio is optional; the game remains playable without a device.
            clickSound = null;
            harvestSound = null;
        }
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void render() {
        if (rollDailyChallengeIfNeeded()) saveGame();
        float delta = Gdx.graphics.getDeltaTime();
        uiTime += delta;
        frameCounter++;
        if (frameCounter % 90 == 0) farmRenderer.trimCache(world.playerX(), world.playerZ(), settings.visibleRadius() + 3);

        // Clearing the depth buffer every frame is essential: without it the 3D scene keeps
        // stale depth values and tiles flicker/stripe as soon as the camera moves.
        ScreenUtils.clear(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, 1f, true);
        screenWidth = Gdx.graphics.getWidth();
        screenHeight = Gdx.graphics.getHeight();

        farmRenderer.setViewWidth(screen == Screen.PLAYING ? 17f : 23f);
        farmRenderer.render(world, settings.visibleRadius(), delta);

        ui.begin(screenWidth, screenHeight);
        switch (screen) {
            case MENU -> renderMenu();
            case SETTINGS -> renderSettings();
            case CHALLENGES -> renderChallenges();
            case PLAYING -> renderHud();
        }
        if (terminalOpen) renderTerminal();
        applyBrightnessOverlay();
        ui.end();
    }

    // ------------------------------------------------------------------ screens

    private void dim(float alpha) {
        overlay.set(0.95f, 0.92f, 0.86f, alpha);
        ui.fill(0, 0, screenWidth, screenHeight, overlay);
    }

    private void renderMenu() {
        dim(0.18f);
        float cx = screenWidth / 2f;
        float top = screenHeight * 0.5f + 300f;
        ui.title("JAVA FARMER", cx, Math.min(screenHeight - 50f, top + 20f), 3.6f, UI_TEXT_LIGHT, WOOD_DARK);
        ui.pill("Aprenda Java cultivando ideias.", cx, Math.min(screenHeight - 70f, top) - 92f, 1.1f, true);

        float cardW = 380f;
        float cardH = 400f;
        float cardX = cx - cardW / 2f;
        float cardY = Math.max(70f, Math.min(screenHeight - 175f, top - 110f) - cardH);
        ui.card(cardX, cardY, cardW, cardH);
        float bx = cardX + 30f;
        float bw = cardW - 60f;
        float y = cardY + cardH - 88f;
        ui.button(bx, y, bw, 54, "COMEÇAR A JOGAR", UI_GREEN, UI_GREEN_LIP, Icon.PLAY, () -> screen = Screen.PLAYING);
        ui.button(bx, y - 72, bw, 54, saveLoaded ? "CONTINUAR" : "TREINAMENTO", UI_BLUE, UI_BLUE_LIP, Icon.NONE, () -> screen = Screen.PLAYING);
        ui.button(bx, y - 144, bw, 54, "DESAFIO DIÁRIO", UI_ORANGE, UI_ORANGE_LIP, Icon.FLAG, this::openChallenges);
        ui.button(bx, y - 216, bw, 54, "CONFIGURAÇÕES", UI_NEUTRAL, UI_NEUTRAL_LIP, Icon.NONE, () -> screen = Screen.SETTINGS);
        ui.button(bx, y - 288, bw, 54, "SALVAR E SAIR", UI_NEUTRAL, UI_NEUTRAL_LIP, Icon.NONE, () -> { saveGame(); Gdx.app.exit(); });
        ui.pill("VERSÃO 0.2.0" + SEP + "PROTÓTIPO JOGÁVEL" + SEP + "COZY CLAY", cx, 16, 0.8f, true);
    }

    private void renderHud() {
        float chipH = 40f;
        float x = 20f;
        float y = screenHeight - 20f - chipH;
        x += ui.chip(x, y, Icon.SUN, "DIA " + LocalDate.now().getDayOfMonth(), chipH) + 12f;
        x += ui.chip(x, y, Icon.STAR, Integer.toString(save.points), chipH) + 12f;
        x += ui.chip(x, y, Icon.COIN, Integer.toString(save.coins), chipH) + 12f;
        ui.chip(x, y, Icon.SEED, Integer.toString(save.seeds), chipH);
        ui.pill("CICLOS " + world.cycles() + SEP + "POSIÇÃO " + world.playerX() + ", " + world.playerZ(),
                20, y - 44, 0.8f, false);

        float bw = 190f;
        float bx = screenWidth - bw - 20f;
        ui.button(bx, screenHeight - 72, bw, 50, "TERMINAL [T]", UI_GREEN, UI_GREEN_LIP, Icon.CODE, this::openTerminal);
        ui.button(bx, screenHeight - 138, bw, 50, "DESAFIOS [C]", UI_BLUE, UI_BLUE_LIP, Icon.FLAG, this::openChallenges);
        ui.button(bx, screenHeight - 204, bw, 50, "MENU / SALVAR", UI_ORANGE, UI_ORANGE_LIP, Icon.HOME, () -> { saveGame(); screen = Screen.MENU; });

        // Speech-bubble log (bottom-left) and contextual tip (bottom-right): never overlap.
        float tip = 0f;
        String tipText = "Dica: o robô começa em solo fértil. Abra o terminal e execute o exemplo!";
        float tipW = ui.labelWidth(tipText, 0.9f) + 40f;
        boolean showTip = !terminalOpen && save.totalPlants == 0 && screenWidth > 1100;
        if (showTip) tip = tipW + 20f;
        float logW = Math.min(720f, screenWidth - 40f - tip);
        ui.slab(20, 26, logW, 70, 20, UI_CREAM, UI_CREAM_LIP);
        ShapeRenderer tail = ui.beginRawShapes();
        tail.setColor(UI_CREAM);
        tail.triangle(46, 96, 76, 96, 52, 114);
        tail.end();
        ui.text(fit(lastLog, logW - 40f, 0.95f), 38, 74, 0.95f, UI_INK, false);
        ui.text("WASD: mover" + SEP + "T: terminal" + SEP + "C: desafios" + SEP + "ESC: menu", 38, 48, 0.75f, UI_INK_SOFT, false);
        if (showTip) {
            float tx = screenWidth - tipW - 20f;
            ui.slab(tx, 26, tipW, 44, 22, UI_GREEN, UI_GREEN_LIP);
            ui.text(tipText, tx + 20f, 55, 0.9f, UI_TEXT_LIGHT, false);
        }
    }

    private void renderSettings() {
        dim(0.45f);
        float cx = screenWidth / 2f;
        float cardW = 560f;
        float cardH = 430f;
        float cardX = cx - cardW / 2f;
        float cardY = (screenHeight - cardH) / 2f - 20f;
        ui.title("CONFIGURAÇÕES", cx, cardY + cardH + 70f, 2.3f, UI_TEXT_LIGHT, WOOD_DARK);
        ui.card(cardX, cardY, cardW, cardH);
        float left = cardX + 40f;
        float y = cardY + cardH - 100f;
        ui.text("SOM: " + Math.round(settings.soundVolume * 100) + "%", left, y + 23 + ui.capHeight(1.1f) / 2f, 1.1f, UI_INK, false);
        ui.button(cardX + cardW - 190f, y, 66, 46, "-", UI_ORANGE, UI_ORANGE_LIP, Icon.NONE, () -> changeVolume(-0.1f));
        ui.button(cardX + cardW - 110f, y, 66, 46, "+", UI_GREEN, UI_GREEN_LIP, Icon.NONE, () -> changeVolume(0.1f));
        y -= 84;
        ui.text("BRILHO: " + settings.brightness + "%", left, y + 23 + ui.capHeight(1.1f) / 2f, 1.1f, UI_INK, false);
        ui.button(cardX + cardW - 190f, y, 66, 46, "-", UI_ORANGE, UI_ORANGE_LIP, Icon.NONE, () -> settings.brightness = Math.max(45, settings.brightness - 5));
        ui.button(cardX + cardW - 110f, y, 66, 46, "+", UI_GREEN, UI_GREEN_LIP, Icon.NONE, () -> settings.brightness = Math.min(115, settings.brightness + 5));
        y -= 84;
        ui.text("QUALIDADE: " + settings.graphics, left, y + 23 + ui.capHeight(1.1f) / 2f, 1.1f, UI_INK, false);
        ui.button(cardX + cardW - 190f, y, 146, 46, "ALTERAR", UI_BLUE, UI_BLUE_LIP, Icon.NONE, this::cycleGraphics);
        ui.text("As alterações são salvas em saves/settings.json.", cx, cardY + 105f, 0.85f, UI_INK_SOFT, true);
        ui.button(cx - 130, cardY + 30f, 260, 52, "VOLTAR", UI_GREEN, UI_GREEN_LIP, Icon.NONE, () -> { saveGame(); screen = Screen.MENU; });
    }

    private void renderChallenges() {
        dim(0.45f);
        float cx = screenWidth / 2f;
        float cardW = 600f;
        float cardH = 400f;
        float cardX = cx - cardW / 2f;
        float cardY = (screenHeight - cardH) / 2f - 20f;
        ui.title("DESAFIO DIÁRIO", cx, cardY + cardH + 70f, 2.3f, UI_TEXT_LIGHT, WOOD_DARK);
        ui.card(cardX, cardY, cardW, cardH);
        ui.text(save.dailyTitle, cx, cardY + cardH - 60f, 1.5f, UI_INK, true);

        // Progress bar in clay style
        float barW = cardW - 120f;
        float barX = cx - barW / 2f;
        float barY = cardY + cardH - 140f;
        float progress = Math.min(1f, save.dailyProgress / (float) Math.max(1, save.dailyTarget));
        ui.roundRect(barX, barY - 5, barW, 30, 15, UI_CREAM_LIP);
        ui.roundRect(barX, barY, barW, 30, 15, UI_NEUTRAL);
        if (progress > 0f) ui.roundRect(barX, barY, Math.max(30f, barW * progress), 30, 15, UI_GREEN);
        ui.text(Math.min(save.dailyProgress, save.dailyTarget) + " / " + save.dailyTarget, cx, barY + 21, 1.0f,
                progress > 0.5f ? UI_TEXT_LIGHT : UI_INK, true);

        ui.text("RECOMPENSA", cx, cardY + cardH - 185f, 0.85f, UI_INK_SOFT, true);
        float rx = cx - 190f;
        float ry = cardY + cardH - 245f;
        rx += ui.chip(rx, ry, Icon.STAR, "+100", 40) + 14f;
        rx += ui.chip(rx, ry, Icon.COIN, "+50", 40) + 14f;
        ui.chip(rx, ry, Icon.SEED, "+3", 40);

        if (save.dailyClaimed) {
            ui.text("Recompensa já resgatada hoje. Volte amanhã!", cx, cardY + 120f, 1.0f, UI_GREEN_LIP, true);
        } else if (save.dailyProgress >= save.dailyTarget) {
            ui.button(cx - 160, cardY + 100f, 320, 52, "RESGATAR RECOMPENSA", UI_ORANGE, UI_ORANGE_LIP, Icon.STAR, this::claimDaily);
        } else {
            ui.text("Continue programando para completar o desafio.", cx, cardY + 120f, 0.95f, UI_INK_SOFT, true);
        }
        ui.button(cx - 130, cardY + 30f, 260, 52, "VOLTAR", UI_GREEN, UI_GREEN_LIP, Icon.NONE, () -> screen = screenBeforeChallenges);
    }

    // ------------------------------------------------------------------ terminal

    private void renderTerminal() {
        overlay.set(0.16f, 0.12f, 0.09f, 0.35f);
        ui.fill(0, 0, screenWidth, screenHeight, overlay);
        float w = Math.min(1060, screenWidth - 44);
        float h = Math.min(680, screenHeight - 70);
        float x = (screenWidth - w) / 2f;
        float y = (screenHeight - h) / 2f + 4f;

        // Clay monitor: grey frame with lip, dark rounded screen inside.
        ui.slab(x, y, w, h, 28, UI_TERMINAL_FRAME, UI_TERMINAL_FRAME_LIP);
        float sx = x + 16;
        float sy = y + 84;
        float sw = w - 32;
        float sh = h - 100;
        ui.roundRect(sx, sy, sw, sh, 18, UI_TERMINAL_SCREEN);
        ui.roundRect(sx, sy + sh - 46, sw, 46, 18, UI_TERMINAL_HEADER);
        ui.fill(sx, sy + sh - 46, sw, 18, UI_TERMINAL_HEADER);
        ShapeRenderer dots = ui.beginRawShapes();
        dots.setColor(TRACTOR);
        dots.circle(sx + 26, sy + sh - 23, 7, 16);
        dots.setColor(DRONE);
        dots.circle(sx + 48, sy + sh - 23, 7, 16);
        dots.setColor(UI_GREEN);
        dots.circle(sx + 70, sy + sh - 23, 7, 16);
        dots.end();
        ui.text("Java", sx + 96, sy + sh - 16, 1.05f, SUNFLOWER_PETAL, false);
        ui.text("PlayerProgram.java", sx + 150, sy + sh - 16, 0.85f, UI_TERMINAL_FRAME, false);
        ui.text("ESC fecha", sx + sw - 100, sy + sh - 17, 0.8f, UI_TERMINAL_FRAME, false);

        renderCode(sx + 14, sy + 12, sw - 28, sh - 66);

        ui.text("Java básico + FarmerBot: variáveis, if/else, for, while e ações da fazenda.",
                x + 26, y + 66, 0.8f, UI_INK, false);
        ui.text(fit(terminalMessage, w - 360f, 0.85f), x + 26, y + 38, 0.85f, terminalMessageOk ? UI_GREEN_LIP : UI_ORANGE_LIP, false);
        ui.button(x + w - 316, y + 18, 150, 46, "EXECUTAR", UI_GREEN, UI_GREEN_LIP, Icon.PLAY, this::runProgram);
        ui.button(x + w - 154, y + 18, 134, 46, "FECHAR", UI_ORANGE, UI_ORANGE_LIP, Icon.NONE, this::closeTerminal);
    }

    private void renderCode(float editorX, float editorY, float editorW, float editorH) {
        refreshHighlight();
        BitmapFont font = ui.mono();
        String[] raw = code.toString().split("\n", -1);
        int cursorLine = 0;
        int lineStart = 0;
        for (int i = 0; i < cursor && i < code.length(); i++) {
            if (code.charAt(i) == '\n') { cursorLine++; lineStart = i + 1; }
        }
        int maxLines = Math.max(4, (int) (editorH / CODE_LINE_HEIGHT));
        if (cursorLine < scrollTop) scrollTop = cursorLine;
        if (cursorLine >= scrollTop + maxLines) scrollTop = cursorLine - maxLines + 1;
        scrollTop = Math.max(0, Math.min(scrollTop, Math.max(0, raw.length - 1)));

        float oldX = font.getData().scaleX;
        float oldY = font.getData().scaleY;
        font.getData().setScale(ui.monoScale(CODE_SCALE));
        float textX = editorX + 46;
        float lineY = editorY + editorH - 6;

        // current-line highlight + caret
        float caretRow = cursorLine - scrollTop;
        if (caretRow >= 0 && caretRow < maxLines) {
            overlay.set(1f, 1f, 1f, 0.05f);
            ui.fill(editorX, lineY - (caretRow + 1) * CODE_LINE_HEIGHT + 3, editorW, CODE_LINE_HEIGHT, overlay);
        }

        ui.batch().begin();
        font.getData().markupEnabled = true;
        int end = Math.min(raw.length, scrollTop + maxLines);
        for (int i = scrollTop; i < end; i++) {
            float baseline = lineY - (i - scrollTop) * CODE_LINE_HEIGHT;
            font.setColor(0.45f, 0.5f, 0.55f, 1f);
            font.draw(ui.batch(), String.format(Locale.ROOT, "%2d", i + 1), editorX + 4, baseline);
            font.setColor(Color.WHITE);
            if (i < highlightedLines.size()) font.draw(ui.batch(), highlightedLines.get(i), textX, baseline);
        }
        font.getData().markupEnabled = false;
        ui.batch().end();

        if (caretRow >= 0 && caretRow < maxLines && (uiTime % 1.0f) < 0.6f) {
            String before = code.substring(lineStart, Math.min(cursor, code.length()));
            if (before.length() > 110) before = before.substring(0, 110);
            measure.setText(font, before);
            float caretX = textX + measure.width;
            float baseline = lineY - caretRow * CODE_LINE_HEIGHT;
            ui.fill(caretX + 1, baseline - 16, 2, 18, SUNFLOWER_PETAL);
        }
        font.getData().setScale(oldX, oldY);
        font.setColor(Color.WHITE);
    }

    /** Simple Java syntax colouring using libGDX font markup (rebuilt only when the code changes). */
    private void refreshHighlight() {
        if (highlightedVersion == codeVersion) return;
        highlightedVersion = codeVersion;
        highlightedLines.clear();
        for (String line : code.toString().split("\n", -1)) {
            if (line.length() > 110) line = line.substring(0, 110);
            highlightedLines.add(highlightLine(line));
        }
    }

    private static String highlightLine(String line) {
        StringBuilder out = new StringBuilder(line.length() + 32);
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                out.append("[#7C8794]").append(escape(line.substring(i))).append("[]");
                break;
            }
            if (c == '"') {
                int j = i + 1;
                while (j < line.length() && line.charAt(j) != '"') { if (line.charAt(j) == '\\') j++; j++; }
                j = Math.min(line.length(), j + 1);
                out.append("[#9BE07A]").append(escape(line.substring(i, j))).append("[]");
                i = j;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int j = i;
                while (j < line.length() && Character.isJavaIdentifierPart(line.charAt(j))) j++;
                String word = line.substring(i, j);
                int k = j;
                while (k < line.length() && line.charAt(k) == ' ') k++;
                boolean call = k < line.length() && line.charAt(k) == '(';
                if (JAVA_KEYWORDS.contains(word)) out.append("[#F6A04D]").append(word).append("[]");
                else if (word.equals("FarmerBot")) out.append("[#6EC3FF]").append(word).append("[]");
                else if (call) out.append("[#F7D774]").append(word).append("[]");
                else out.append("[#E6EDF3]").append(word).append("[]");
                i = j;
                continue;
            }
            if (Character.isDigit(c)) {
                int j = i;
                while (j < line.length() && (Character.isDigit(line.charAt(j)) || line.charAt(j) == '.')) j++;
                out.append("[#F59AB8]").append(line, i, j).append("[]");
                i = j;
                continue;
            }
            out.append("[#C9D1D9]").append(c == '[' ? "[[" : String.valueOf(c)).append("[]");
            i++;
        }
        return out.toString();
    }

    private static String escape(String text) {
        return text.replace("[", "[[");
    }

    private void runProgram() {
        JavaProgramRunner.ProgramResult result = programRunner.run(code.toString());
        if (!result.success()) {
            terminalMessage = result.message();
            terminalMessageOk = false;
            lastLog = "O programa não foi executado. Corrija o código no terminal.";
            return;
        }
        int applied = 0;
        for (JavaProgramRunner.Action action : result.actions()) {
            if (applyAction(action.type(), action.value())) applied++;
        }
        save.dailyProgress += save.dailyKind.equals("PROGRAM") ? 1 : 0;
        terminalMessage = "Programa executado com sucesso! " + applied + " ação(ões) reconhecida(s).";
        terminalMessageOk = true;
        lastLog = terminalMessage;
        saveGame();
    }

    private boolean applyAction(String type, String value) {
        switch (type) {
            case "MOVE" -> {
                boolean moved = world.move(value);
                lastLog = moved ? "Robô movido para " + value + "." : "Não é possível andar nessa direção: existe água ou um obstáculo.";
                return moved;
            }
            case "TILL" -> {
                boolean ok = world.till();
                lastLog = ok ? "Solo preparado. Agora plante uma semente." : "Este terreno não pode ser preparado agora.";
                return ok;
            }
            case "PLANT" -> {
                if (save.seeds <= 0) { lastLog = "Você não tem sementes. Complete desafios ou colha cultivos."; return false; }
                boolean ok = world.plant(value);
                if (ok) {
                    save.seeds--;
                    save.totalPlants++;
                    if (save.dailyKind.equals("PLANT")) save.dailyProgress++;
                    lastLog = "Semente de " + value + " plantada.";
                } else lastLog = "Não foi possível plantar neste bloco.";
                return ok;
            }
            case "WATER" -> {
                boolean ok = world.water();
                lastLog = ok ? "Cultivo irrigado: crescerá mais rápido no próximo ciclo." : "Não existe cultivo neste bloco para irrigar.";
                return ok;
            }
            case "WAIT" -> {
                int amount;
                try { amount = Integer.parseInt(value); } catch (NumberFormatException ex) { amount = 1; }
                int grown = world.waitCycles(amount);
                lastLog = "O tempo avançou. " + grown + " cultivo(s) amadureceram.";
                return true;
            }
            case "HARVEST" -> {
                boolean ok = world.harvest();
                if (ok) {
                    save.coins += 10;
                    save.seeds++;
                    save.totalHarvests++;
                    if (save.dailyKind.equals("HARVEST")) save.dailyProgress++;
                    if (harvestSound != null && settings.soundVolume > 0f) harvestSound.play(settings.soundVolume);
                    lastLog = "Colheita concluída! +10 moedas e +1 semente.";
                } else lastLog = "O cultivo ainda não está pronto para colher.";
                return ok;
            }
            case "LOG" -> { lastLog = value; return true; }
            default -> { lastLog = "Ação desconhecida ignorada: " + type; return false; }
        }
    }

    // ------------------------------------------------------------------ actions

    private void openChallenges() {
        if (screen != Screen.CHALLENGES) screenBeforeChallenges = screen;
        terminalOpen = false;
        screen = Screen.CHALLENGES;
    }

    private void claimDaily() {
        if (save.dailyClaimed || save.dailyProgress < save.dailyTarget) return;
        save.dailyClaimed = true;
        save.points += 100;
        save.coins += 50;
        save.seeds += 3;
        saveGame();
        lastLog = "Desafio diário concluído! +100 pontos, +50 moedas e +3 sementes.";
    }

    private void openTerminal() {
        terminalOpen = true;
    }

    private void closeTerminal() {
        terminalOpen = false;
        saveGame();
    }

    private void playClick() {
        if (clickSound != null && settings.soundVolume > 0f) clickSound.play(settings.soundVolume * 0.35f);
    }

    private void changeVolume(float delta) {
        settings.soundVolume = Math.max(0f, Math.min(1f, Math.round((settings.soundVolume + delta) * 10f) / 10f));
        if (ambience != null) {
            ambience.setVolume(settings.soundVolume);
            if (settings.soundVolume <= 0.001f) ambience.pause();
            else if (!ambience.isPlaying()) ambience.play();
        }
    }

    private void cycleGraphics() {
        settings.graphics = switch (settings.graphics) {
            case "BAIXO" -> "MEDIO";
            case "MEDIO" -> "ALTO";
            default -> "BAIXO";
        };
        Gdx.graphics.setForegroundFPS(settings.targetFps());
        farmRenderer.invalidate();
    }

    private void applyBrightnessOverlay() {
        if (settings.brightness == 100) return;
        float amount = Math.min(0.24f, Math.abs(settings.brightness - 100) / 100f);
        if (settings.brightness < 100) overlay.set(0f, 0f, 0f, amount);
        else overlay.set(1f, 0.96f, 0.84f, amount);
        ui.fill(0, 0, screenWidth, screenHeight, overlay);
    }

    /** Shortens a message with an ellipsis so it fits the given width. */
    private String fit(String text, float maxWidth, float scale) {
        if (text == null) return "";
        if (ui.labelWidth(text, scale) <= maxWidth) return text;
        int end = text.length();
        while (end > 4 && ui.labelWidth(text.substring(0, end) + "...", scale) > maxWidth) end -= 2;
        return text.substring(0, Math.max(0, end)) + "...";
    }

    // ------------------------------------------------------------------ input

    private final class GameInput extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT) return false;
            if (ui.click(screenX, screenHeight - screenY)) { playClick(); return true; }
            return terminalOpen;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (terminalOpen) return terminalKey(keycode);
            if (keycode == Input.Keys.ESCAPE) {
                if (screen == Screen.PLAYING) { saveGame(); screen = Screen.MENU; }
                else if (screen == Screen.CHALLENGES) screen = screenBeforeChallenges;
                else if (screen == Screen.SETTINGS) { saveGame(); screen = Screen.MENU; }
                return true;
            }
            if (screen == Screen.PLAYING) {
                if (keycode == Input.Keys.W || keycode == Input.Keys.UP) return move("NORTH");
                if (keycode == Input.Keys.S || keycode == Input.Keys.DOWN) return move("SOUTH");
                if (keycode == Input.Keys.A || keycode == Input.Keys.LEFT) return move("WEST");
                if (keycode == Input.Keys.D || keycode == Input.Keys.RIGHT) return move("EAST");
                if (keycode == Input.Keys.T) { openTerminal(); swallowNextTyped = true; return true; }
                if (keycode == Input.Keys.C) { openChallenges(); return true; }
            }
            return false;
        }

        private boolean move(String direction) {
            if (!world.move(direction)) lastLog = "Caminho bloqueado: água ou construção à frente.";
            return true;
        }

        private boolean terminalKey(int keycode) {
            swallowNextTyped = false;
            boolean changed = true;
            switch (keycode) {
                case Input.Keys.ESCAPE -> { closeTerminal(); return true; }
                case Input.Keys.BACKSPACE -> { if (cursor > 0) code.deleteCharAt(--cursor); else changed = false; }
                case Input.Keys.FORWARD_DEL -> { if (cursor < code.length()) code.deleteCharAt(cursor); else changed = false; }
                case Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER -> {
                    if (code.length() >= 18_000) return true;
                    // Keep the indentation of the current line, like a real editor.
                    int start = code.lastIndexOf("\n", Math.max(0, cursor - 1)) + 1;
                    if (cursor == 0) start = 0;
                    int indent = 0;
                    while (start + indent < cursor && code.charAt(start + indent) == ' ') indent++;
                    String insert = "\n" + " ".repeat(indent);
                    code.insert(cursor, insert);
                    cursor += insert.length();
                }
                case Input.Keys.TAB -> {
                    if (code.length() + 4 > 18_000) return true;
                    code.insert(cursor, "    ");
                    cursor += 4;
                }
                case Input.Keys.LEFT -> { cursor = Math.max(0, cursor - 1); changed = false; }
                case Input.Keys.RIGHT -> { cursor = Math.min(code.length(), cursor + 1); changed = false; }
                case Input.Keys.UP -> { moveCursorVertically(-1); changed = false; }
                case Input.Keys.DOWN -> { moveCursorVertically(1); changed = false; }
                case Input.Keys.HOME -> { cursor = cursor == 0 ? 0 : code.lastIndexOf("\n", cursor - 1) + 1; changed = false; }
                case Input.Keys.END -> { int end = code.indexOf("\n", cursor); cursor = end < 0 ? code.length() : end; changed = false; }
                default -> { return false; }
            }
            if (changed) codeVersion++;
            return true;
        }

        private void moveCursorVertically(int direction) {
            int lineStart = cursor == 0 ? 0 : code.lastIndexOf("\n", cursor - 1) + 1;
            int column = cursor - lineStart;
            if (direction < 0) {
                if (lineStart == 0) { cursor = 0; return; }
                int prevStart = lineStart - 1 == 0 ? 0 : code.lastIndexOf("\n", lineStart - 2) + 1;
                cursor = Math.min(prevStart + column, lineStart - 1);
            } else {
                int lineEnd = code.indexOf("\n", cursor);
                if (lineEnd < 0) { cursor = code.length(); return; }
                int nextEnd = code.indexOf("\n", lineEnd + 1);
                if (nextEnd < 0) nextEnd = code.length();
                cursor = Math.min(lineEnd + 1 + column, nextEnd);
            }
        }

        @Override
        public boolean keyTyped(char character) {
            if (!terminalOpen) return false;
            if (swallowNextTyped) { swallowNextTyped = false; if (character == 't' || character == 'T') return true; }
            if (character >= 32 && character != 127 && code.length() < 18_000) {
                code.insert(cursor++, character);
                codeVersion++;
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void resize(int width, int height) {
        screenWidth = width;
        screenHeight = height;
    }

    @Override
    public void pause() {
        saveGame();
        if (ambience != null) ambience.pause();
    }

    @Override
    public void resume() {
        if (ambience != null && settings.soundVolume > 0.001f) ambience.play();
    }

    @Override
    public void dispose() {
        saveGame();
        if (ambience != null) ambience.dispose();
        if (clickSound != null) clickSound.dispose();
        if (harvestSound != null) harvestSound.dispose();
        farmRenderer.dispose();
        ui.dispose();
    }
}
