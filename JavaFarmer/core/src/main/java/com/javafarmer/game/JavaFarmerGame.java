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
import com.javafarmer.progress.DailyChallenges;
import com.javafarmer.progress.Missions;
import com.javafarmer.progress.RunReport;
import com.javafarmer.render.FarmRenderer;
import com.javafarmer.runtime.JavaProgramRunner;
import com.javafarmer.save.GameSave;
import com.javafarmer.save.GameSettings;
import com.javafarmer.save.SaveMapper;
import com.javafarmer.ui.ClayUi;
import com.javafarmer.ui.ClayUi.Icon;
import com.javafarmer.world.CropType;
import com.javafarmer.world.Farm;
import com.javafarmer.world.FarmWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.javafarmer.render.ClayPalette.*;

/**
 * Java Farmer: program a farming robot in Java. Owns the game loop, screens and input; the rules live
 * in {@link Farm}, the curriculum in {@link Missions} and the day-scaled challenges in {@link DailyChallenges}.
 */
public final class JavaFarmerGame extends ApplicationAdapter {
    private enum Screen { MENU, PLAYING, SETTINGS, OBJECTIVES, SHOP }

    private static final String SEP = "  ·  "; // middle dot exists in every bundled font
    private static final float CODE_SCALE = 0.92f;
    private static final float CODE_LINE_HEIGHT = 21f;
    private static final float[] SPEEDS = {1f, 2f, 4f, 8f};
    private static final float STEP_SECONDS = 0.22f;
    private static final Set<String> JAVA_KEYWORDS = Set.of("public", "private", "static", "void", "class", "import",
            "int", "long", "double", "float", "boolean", "char", "String", "for", "while", "do", "if", "else",
            "return", "new", "true", "false", "final", "var", "break", "continue");
    private static final String[] API_REFERENCE = {
            "#AÇÕES (gastam tempo)",
            "move(\"NORTH\"|\"SOUTH\"|\"EAST\"|\"WEST\")",
            "till()", "plant(\"WHEAT\")", "water()", "harvest()", "waitCycles(horas)", "sell(\"WHEAT\")", "log(texto)",
            "",
            "#SENSORES (instantâneos)",
            "getX()  getY()  getFarmSize()", "canHarvest()  getCrop()", "getGround()  isWatered()", "isMyLand()  getDay()",
            "getSeeds(\"WHEAT\")", "getItems(\"WHEAT\")  getCoins()",
            "",
            "#CULTIVOS", "WHEAT  CARROT  SUNFLOWER  PUMPKIN"
    };

    private final Json json = new Json();
    private final JavaProgramRunner programRunner = new JavaProgramRunner();

    private Screen screen = Screen.MENU;
    private Screen screenBeforeOverlay = Screen.PLAYING;
    private boolean terminalOpen;
    private final StringBuilder code = new StringBuilder();
    private int cursor;
    private int scrollTop;
    private int codeVersion;
    private int highlightedVersion = -1;
    private final List<String> highlightedLines = new ArrayList<>();
    private String terminalMessage = "Terminal pronto. Escreva Java e clique em EXECUTAR.";
    private boolean terminalMessageOk = true;
    private String lastLog = "Bem-vindo à fazenda! Abra o terminal (T) e execute seu primeiro programa.";

    private GameSave save;
    private GameSettings settings;
    private Farm farm;
    private DailyChallenges.Challenge challenge;
    private int selectedMission;
    private boolean confirmNewGame;

    // program execution
    private final AtomicReference<JavaProgramRunner.ProgramResult> pendingResult = new AtomicReference<>();
    private boolean compiling;
    private List<JavaProgramRunner.Action> playback = List.of();
    private int playbackIndex;
    private float playbackTimer;
    private int speedIndex = 1;
    private RunReport report;

    // feedback
    private String banner = "";
    private String bannerDetail = "";
    private float bannerTime;

    private FarmRenderer farmRenderer;
    private ClayUi ui;
    private final GlyphLayout measure = new GlyphLayout();
    private final Color overlay = new Color();
    private final Color bannerLip = new Color();
    private final Color bannerText = new Color();

    private Music ambience;
    private Sound clickSound;
    private Sound harvestSound;
    private float screenWidth;
    private float screenHeight;
    private int frameCounter;
    private float uiTime;
    /** Set when a key opens the terminal, so that key's typed character is not inserted. */
    private boolean swallowNextTyped;

    @Override
    public void create() {
        loadData();
        farmRenderer = new FarmRenderer();
        ui = new ClayUi();
        loadAudio();
        Gdx.input.setInputProcessor(new GameInput());
        Gdx.graphics.setForegroundFPS(settings.targetFps());
    }

    // ------------------------------------------------------------------ data

    private void loadData() {
        save = readJson("saves/slot1.json", GameSave.class, new GameSave());
        settings = readJson("saves/settings.json", GameSettings.class, new GameSettings());
        sanitizeSettings();
        farm = SaveMapper.toFarm(save);
        save.missionsCompleted = Math.max(0, Math.min(Missions.ALL.size(), save.missionsCompleted));
        selectedMission = Math.min(save.missionsCompleted, Missions.ALL.size() - 1);
        challenge = SaveMapper.challenge(save);
        ensureChallenge();
        setCode(save.program != null && !save.program.isBlank() && save.program.length() <= 18_000 ? save.program : defaultProgram());
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

    private void sanitizeSettings() {
        settings.soundVolume = Math.max(0f, Math.min(1f, settings.soundVolume));
        settings.brightness = Math.max(45, Math.min(115, settings.brightness));
        if (!List.of("BAIXO", "MEDIO", "ALTO").contains(settings.graphics)) settings.graphics = "MEDIO";
        save.points = Math.max(0, save.points);
    }

    private static String defaultProgram() {
        return """
                import com.javafarmer.api.FarmerBot;

                public class PlayerProgram {
                    public static void main(String[] args) {
                        // Seu primeiro programa: ande, prepare o solo e plante trigo.
                        FarmerBot.move("EAST");
                        FarmerBot.till();
                        FarmerBot.plant("WHEAT");
                    }
                }
                """;
    }

    private void setCode(String text) {
        code.setLength(0);
        code.append(text);
        cursor = code.length();
        scrollTop = 0;
        codeVersion++;
    }

    private void saveGame() {
        try {
            SaveMapper.fromFarm(farm, save);
            save.program = code.toString();
            if (challenge != null) {
                int progress = save.challengeProgress;
                boolean claimed = save.challengeClaimed;
                SaveMapper.storeChallenge(challenge, save);
                save.challengeProgress = progress;
                save.challengeClaimed = claimed;
            }
            var saveFile = Gdx.files.local("saves/slot1.json");
            saveFile.parent().mkdirs();
            saveFile.writeString(json.prettyPrint(save), false, "UTF-8");
            var settingsFile = Gdx.files.local("saves/settings.json");
            settingsFile.parent().mkdirs();
            settingsFile.writeString(json.prettyPrint(settings), false, "UTF-8");
        } catch (Exception ex) {
            lastLog = "Não foi possível salvar: " + ex.getMessage();
        }
    }

    private void newGame() {
        GameSave fresh = new GameSave();
        fresh.version = GameSave.CURRENT_VERSION;
        fresh.seedStock = new int[]{10, 0, 0, 0};
        save = fresh;
        farm = SaveMapper.toFarm(save);
        challenge = null;
        ensureChallenge();
        selectedMission = 0;
        setCode(defaultProgram());
        playback = List.of();
        farmRenderer.invalidate();
        lastLog = "Nova fazenda! Abra o terminal (T) e execute seu primeiro programa.";
        showBanner("DIA 1", "Uma fazenda 3x3, 25 moedas e 10 sementes de trigo.");
        saveGame();
        screen = Screen.PLAYING;
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

    // ------------------------------------------------------------------ progression

    /** Makes sure there is a challenge for today: a new one appears each new day once the last was finished. */
    private void ensureChallenge() {
        int today = farm.world.day();
        if (challenge == null || (save.challengeClaimed && today > challenge.day())) {
            challenge = DailyChallenges.generate(today, farm);
            SaveMapper.storeChallenge(challenge, save);
        }
    }

    private Missions.Lifetime lifetime() {
        Missions.Lifetime life = new Missions.Lifetime();
        life.harvests = save.totalHarvests;
        life.plants = save.totalPlants;
        life.itemsSold = save.itemsSold;
        life.coinsEarned = save.coinsEarned;
        return life;
    }

    /** Called after a program finishes or a shop transaction. Missions may complete in sequence. */
    private void checkProgress(RunReport run, int shopCoins) {
        while (save.missionsCompleted < Missions.ALL.size()) {
            Missions.Mission mission = Missions.ALL.get(save.missionsCompleted);
            if (!mission.check().test(new Missions.Context(run, farm, lifetime()))) break;
            completeMission(mission);
        }
        if (challenge != null && !save.challengeClaimed) {
            save.challengeProgress = DailyChallenges.progress(challenge, save.challengeProgress, run, shopCoins, farm);
            if (save.challengeProgress >= challenge.amount()) {
                save.challengeClaimed = true;
                save.challengesCompleted++;
                farm.coins += challenge.rewardCoins();
                save.points += challenge.rewardPoints();
                showBanner("DESAFIO DO DIA CONCLUÍDO!", "+" + challenge.rewardCoins() + " moedas" + SEP + "+" + challenge.rewardPoints()
                        + " pontos" + SEP + "um novo desafio, mais difícil, chega amanhã");
            }
        }
        ensureChallenge();
        helpIfBroke();
    }

    private void completeMission(Missions.Mission mission) {
        save.missionsCompleted++;
        farm.coins += mission.rewardCoins();
        save.points += 50 + 25 * save.missionsCompleted;
        StringBuilder detail = new StringBuilder("+" + mission.rewardCoins() + " moedas");
        if (mission.unlockCrop() != null) {
            farm.unlocked.add(mission.unlockCrop());
            detail.append(SEP).append(mission.unlockCrop().label).append(" liberado");
        }
        if (mission.rewardSeeds() > 0) {
            CropType seedType = mission.unlockCrop() != null ? mission.unlockCrop() : CropType.WHEAT;
            farm.seeds[seedType.ordinal()] += mission.rewardSeeds();
            detail.append(SEP).append("+").append(mission.rewardSeeds()).append(" sementes de ").append(seedType.label);
        }
        int done = save.missionsCompleted;
        if (done - 1 == Missions.LAND_UNLOCK_MISSION) detail.append(SEP).append("terra à venda na loja");
        if (done - 1 == Missions.REGADOR_UNLOCK_MISSION) detail.append(SEP).append("Regador na loja");
        if (done - 1 == Missions.MERCADO_UNLOCK_MISSION) detail.append(SEP).append("contrato com o mercado na loja");
        selectedMission = Math.min(done, Missions.ALL.size() - 1);
        showBanner("MISSÃO CONCLUÍDA: " + mission.title().toUpperCase(Locale.ROOT), detail.toString());
        lastLog = done < Missions.ALL.size()
                ? "Próxima missão: " + Missions.ALL.get(done).title() + " (veja em OBJETIVOS)."
                : "Você concluiu todas as missões. Parabéns, mestre fazendeiro!";
    }

    /** Avoids a dead end: no coins for seeds, no seeds, nothing to sell and nothing growing. */
    private void helpIfBroke() {
        if (farm.coins >= CropType.WHEAT.seedPrice || farm.totalSeeds() > 0 || farm.totalItems() > 0) return;
        boolean growing = farm.world.getTiles().values().stream().anyMatch(t -> t.hasCrop());
        if (growing) return;
        farm.seeds[CropType.WHEAT.ordinal()] += 5;
        lastLog = "A cooperativa doou 5 sementes de trigo para você recomeçar.";
    }

    private void showBanner(String title, String detail) {
        banner = title;
        bannerDetail = detail;
        bannerTime = 5f;
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        uiTime += delta;
        frameCounter++;
        if (frameCounter % 90 == 0) farmRenderer.trimCache(farm.world.playerX(), farm.world.playerZ(), settings.visibleRadius() + 3);
        pollProgramResult();
        updatePlayback(delta);
        if (bannerTime > 0f) bannerTime -= delta;

        Color sky = farmRenderer.skyColor(farm.world);
        // Clearing the depth buffer every frame is essential, otherwise tiles stripe when the camera moves.
        ScreenUtils.clear(sky.r, sky.g, sky.b, 1f, true);
        screenWidth = Gdx.graphics.getWidth();
        screenHeight = Gdx.graphics.getHeight();

        boolean world = screen == Screen.PLAYING;
        farmRenderer.setViewWidth(world ? 17f : 23f);
        farmRenderer.render(farm.world, settings.visibleRadius(), delta);

        ui.begin(screenWidth, screenHeight);
        switch (screen) {
            case MENU -> renderMenu();
            case SETTINGS -> renderSettings();
            case OBJECTIVES -> renderObjectives();
            case SHOP -> renderShop();
            case PLAYING -> renderHud();
        }
        if (terminalOpen) renderTerminal();
        if (bannerTime > 0f) renderBanner();
        applyBrightnessOverlay();
        ui.end();
    }

    // ------------------------------------------------------------------ program execution

    private void runProgram() {
        if (compiling || isPlaying()) return;
        compiling = true;
        terminalMessage = "Compilando e executando seu programa...";
        terminalMessageOk = true;
        String source = code.toString();
        String snapshot = farm.snapshot();
        Thread worker = new Thread(() -> pendingResult.set(programRunner.run(source, snapshot)), "java-farmer-runner");
        worker.setDaemon(true);
        worker.start();
    }

    private void pollProgramResult() {
        JavaProgramRunner.ProgramResult result = pendingResult.getAndSet(null);
        if (result == null) return;
        compiling = false;
        if (!result.success()) {
            terminalMessage = result.message();
            terminalMessageOk = false;
            lastLog = "O programa não foi executado. Veja a mensagem no terminal.";
            return;
        }
        if (result.actions().isEmpty()) {
            terminalMessage = "O programa rodou, mas não deu nenhuma ordem ao robô.";
            terminalMessageOk = false;
            report = new RunReport(result.features());
            checkProgress(report, 0);
            return;
        }
        terminalMessage = "Executando " + result.actions().size() + " ação(ões)...";
        terminalMessageOk = true;
        playback = result.actions();
        playbackIndex = 0;
        playbackTimer = 0f;
        report = new RunReport(result.features());
        terminalOpen = false;
        screen = Screen.PLAYING;
    }

    private boolean isPlaying() {
        return playbackIndex < playback.size();
    }

    private void updatePlayback(float delta) {
        if (!isPlaying()) return;
        playbackTimer += delta * SPEEDS[speedIndex];
        while (isPlaying() && playbackTimer >= STEP_SECONDS) {
            JavaProgramRunner.Action action = playback.get(playbackIndex++);
            playbackTimer -= stepCost(action);
            applyAction(action);
        }
        if (!isPlaying()) finishRun(false);
    }

    private static float stepCost(JavaProgramRunner.Action action) {
        return switch (action.type()) {
            case "LOG" -> 0f;
            case "WAIT" -> STEP_SECONDS * 2.5f;
            default -> STEP_SECONDS;
        };
    }

    private void applyAction(JavaProgramRunner.Action action) {
        int dayBefore = farm.world.day();
        Farm.Result result = farm.apply(action.type(), action.value());
        lastLog = result.message();
        if (result.ok()) {
            switch (action.type()) {
                case "MOVE" -> report.moves++;
                case "TILL" -> report.tills++;
                case "WATER" -> report.waters++;
                case "PLANT" -> {
                    if (result.crop() != null) report.planted[result.crop().ordinal()]++;
                    save.totalPlants++;
                }
                case "HARVEST" -> {
                    if (result.crop() != null) report.harvested[result.crop().ordinal()]++;
                    save.totalHarvests++;
                    if (harvestSound != null && settings.soundVolume > 0f) harvestSound.play(settings.soundVolume * 0.6f);
                }
                case "SELL" -> {
                    report.coinsEarned += result.coins();
                    save.coinsEarned += result.coins();
                    int units = result.crop() == null ? 0 : result.coins() / Math.max(1, farm.sellPrice(result.crop()));
                    report.itemsSold += units;
                    save.itemsSold += units;
                }
                default -> { }
            }
        }
        if (farm.world.day() > dayBefore) onNewDay();
    }

    private void onNewDay() {
        DailyChallenges.Challenge old = challenge;
        ensureChallenge();
        String detail = challenge != old && challenge != null
                ? "Novo desafio (nível " + challenge.tier() + "): " + DailyChallenges.describe(challenge)
                : "Termine o desafio de ontem para receber um novo.";
        showBanner("DIA " + farm.world.day(), detail);
    }

    private void finishRun(boolean stopped) {
        int executed = playbackIndex;
        playback = List.of();
        playbackIndex = 0;
        if (report == null) return;
        terminalMessage = (stopped ? "Execução interrompida: " : "Programa concluído: ") + executed + " ação(ões) executada(s).";
        terminalMessageOk = !stopped;
        checkProgress(report, 0);
        saveGame();
    }

    private void skipPlayback() {
        while (isPlaying()) applyAction(playback.get(playbackIndex++));
        finishRun(false);
    }

    // ------------------------------------------------------------------ shop actions

    private void sell(CropType crop, int amount) {
        int earned = farm.sell(crop, amount);
        if (earned <= 0) return;
        int units = earned / Math.max(1, farm.sellPrice(crop));
        save.itemsSold += units;
        save.coinsEarned += earned;
        lastLog = "Vendeu " + units + " " + crop.label + " por " + earned + " moedas.";
        checkProgress(null, earned);
        saveGame();
    }

    private void sellAll() {
        for (CropType crop : CropType.values()) sell(crop, farm.items[crop.ordinal()]);
    }

    private void buySeeds(CropType crop, int amount) {
        if (farm.buySeeds(crop, amount)) {
            lastLog = "Comprou " + amount + " sementes de " + crop.label + ".";
            saveGame();
        }
    }

    private void buyLand() {
        if (!Missions.landAvailable(save.missionsCompleted)) return;
        if (farm.buyLand()) {
            int size = farm.world.farmSize();
            showBanner("FAZENDA " + size + "x" + size + "!", "Agora você tem " + size * size + " blocos para cultivar.");
            farmRenderer.invalidate();
            checkProgress(null, 0);
            saveGame();
        }
    }

    private void buyUpgrade(Farm.Upgrade upgrade) {
        if (!Missions.upgradeAvailable(upgrade, save.missionsCompleted)) return;
        if (farm.buyUpgrade(upgrade)) {
            showBanner(upgrade.label.toUpperCase(Locale.ROOT) + " COMPRADO", upgrade.description);
            checkProgress(null, 0);
            saveGame();
        }
    }

    // ------------------------------------------------------------------ screens

    private void dim(float alpha) {
        overlay.set(0.95f, 0.92f, 0.86f, alpha);
        ui.fill(0, 0, screenWidth, screenHeight, overlay);
    }

    private boolean hasProgress() {
        return save.missionsCompleted > 0 || save.totalPlants > 0 || farm.world.ticks() > 0;
    }

    private void renderMenu() {
        dim(0.18f);
        float cx = screenWidth / 2f;
        float top = screenHeight * 0.5f + 300f;
        ui.title("JAVA FARMER", cx, Math.min(screenHeight - 50f, top + 20f), 3.6f, UI_TEXT_LIGHT, WOOD_DARK);
        ui.pill("Programe seu robô em Java e faça a fazenda crescer.", cx, Math.min(screenHeight - 70f, top) - 92f, 1.05f, true);

        float cardW = 380f;
        float cardH = 400f;
        float cardX = cx - cardW / 2f;
        float cardY = Math.max(70f, Math.min(screenHeight - 175f, top - 110f) - cardH);
        ui.card(cardX, cardY, cardW, cardH);
        float bx = cardX + 30f;
        float bw = cardW - 60f;
        float y = cardY + cardH - 88f;
        if (hasProgress()) {
            ui.button(bx, y, bw, 54, "CONTINUAR" + SEP + "DIA " + farm.world.day(), UI_GREEN, UI_GREEN_LIP, Icon.PLAY, () -> screen = Screen.PLAYING);
            ui.button(bx, y - 72, bw, 54, confirmNewGame ? "CLIQUE DE NOVO PARA APAGAR" : "NOVO JOGO",
                    confirmNewGame ? TRACTOR : UI_BLUE, confirmNewGame ? TRACTOR_DARK : UI_BLUE_LIP, Icon.NONE,
                    () -> { if (confirmNewGame) { confirmNewGame = false; newGame(); } else confirmNewGame = true; });
        } else {
            ui.button(bx, y, bw, 54, "COMEÇAR A JOGAR", UI_GREEN, UI_GREEN_LIP, Icon.PLAY, () -> screen = Screen.PLAYING);
            ui.button(bx, y - 72, bw, 54, "OBJETIVOS", UI_BLUE, UI_BLUE_LIP, Icon.FLAG, () -> openOverlay(Screen.OBJECTIVES));
        }
        ui.button(bx, y - 144, bw, 54, "LOJA", UI_ORANGE, UI_ORANGE_LIP, Icon.CART, () -> openOverlay(Screen.SHOP));
        ui.button(bx, y - 216, bw, 54, "CONFIGURAÇÕES", UI_NEUTRAL, UI_NEUTRAL_LIP, Icon.NONE, () -> { confirmNewGame = false; screen = Screen.SETTINGS; });
        ui.button(bx, y - 288, bw, 54, "SALVAR E SAIR", UI_NEUTRAL, UI_NEUTRAL_LIP, Icon.NONE, () -> { saveGame(); Gdx.app.exit(); });
        ui.pill("VERSÃO 0.3.0" + SEP + "MISSÕES " + save.missionsCompleted + "/" + Missions.ALL.size() + SEP + "COZY CLAY", cx, 16, 0.8f, true);
    }

    private void openOverlay(Screen target) {
        if (screen != Screen.OBJECTIVES && screen != Screen.SHOP) screenBeforeOverlay = screen;
        terminalOpen = false;
        confirmNewGame = false;
        if (target == Screen.OBJECTIVES) selectedMission = Math.min(save.missionsCompleted, Missions.ALL.size() - 1);
        if (target == Screen.SHOP) helpIfBroke();
        screen = target;
    }

    private String clock() {
        int minutes = farm.world.clockMinutes();
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private boolean isNight() {
        int hour = farm.world.clockMinutes() / 60;
        return hour >= 19 || hour < 6;
    }

    private void renderHud() {
        float chipH = 40f;
        float x = 20f;
        float y = screenHeight - 20f - chipH;
        x += ui.chip(x, y, isNight() ? Icon.MOON : Icon.SUN, "DIA " + farm.world.day() + SEP + clock(), chipH) + 12f;
        x += ui.chip(x, y, Icon.COIN, Integer.toString(farm.coins), chipH) + 12f;
        x += ui.chip(x, y, Icon.STAR, Integer.toString(save.points), chipH) + 12f;
        x += ui.chip(x, y, Icon.SEED, Integer.toString(farm.totalSeeds()), chipH) + 12f;
        ui.chip(x, y, Icon.BOX, Integer.toString(farm.totalItems()), chipH);

        int size = farm.world.farmSize();
        String where = farm.isOwnedHere() ? "ROBÔ EM x=" + farm.getX() + " y=" + farm.getY() : "ROBÔ FORA DA FAZENDA";
        ui.pill("FAZENDA " + size + "x" + size + SEP + where, 20, y - 44, 0.8f, false);

        renderTracker(20f, y - 58f);

        float bw = 200f;
        float bx = screenWidth - bw - 20f;
        ui.button(bx, screenHeight - 72, bw, 50, "TERMINAL [T]", UI_GREEN, UI_GREEN_LIP, Icon.CODE, this::openTerminal);
        ui.button(bx, screenHeight - 138, bw, 50, "OBJETIVOS [O]", UI_BLUE, UI_BLUE_LIP, Icon.FLAG, () -> openOverlay(Screen.OBJECTIVES));
        ui.button(bx, screenHeight - 204, bw, 50, "LOJA [L]", UI_ORANGE, UI_ORANGE_LIP, Icon.CART, () -> openOverlay(Screen.SHOP));
        ui.button(bx, screenHeight - 270, bw, 50, "MENU [ESC]", UI_NEUTRAL, UI_NEUTRAL_LIP, Icon.HOME, () -> { saveGame(); screen = Screen.MENU; });

        float logW = Math.min(720f, screenWidth - 40f - (isPlaying() ? 470f : 0f));
        ui.slab(20, 26, logW, 70, 20, UI_CREAM, UI_CREAM_LIP);
        ShapeRenderer tail = ui.beginRawShapes();
        tail.setColor(UI_CREAM);
        tail.triangle(46, 96, 76, 96, 52, 114);
        tail.end();
        ui.text(fit(lastLog, logW - 40f, 0.95f), 38, 74, 0.95f, UI_INK, false);
        ui.text("WASD: mover" + SEP + "T: terminal" + SEP + "O: objetivos" + SEP + "L: loja" + SEP + "ESC: menu", 38, 48, 0.75f, UI_INK_SOFT, false);

        if (isPlaying() || compiling) renderPlaybackBar();
    }

    /** Current mission + daily challenge, always visible while playing. */
    private void renderTracker(float x, float top) {
        float w = 430f;
        float inner = w - 36f;
        String missionTitle;
        String missionGoal;
        if (save.missionsCompleted < Missions.ALL.size()) {
            Missions.Mission m = Missions.ALL.get(save.missionsCompleted);
            missionTitle = "MISSÃO " + (save.missionsCompleted + 1) + "/" + Missions.ALL.size() + SEP + m.title();
            missionGoal = m.goal();
        } else {
            missionTitle = "TODAS AS MISSÕES CONCLUÍDAS";
            missionGoal = "Continue com os desafios diários: eles ficam mais difíceis a cada dia.";
        }
        String challengeTitle = challenge == null ? "" : "DESAFIO DO DIA " + challenge.day() + SEP + "NÍVEL " + challenge.tier();
        String challengeGoal = challenge == null ? "" : DailyChallenges.describe(challenge);
        float h1 = ui.wrappedHeight(missionGoal, inner, 0.82f);
        float h2 = ui.wrappedHeight(challengeGoal, inner, 0.82f);
        float h = 18 + 20 + 8 + h1 + 22 + 20 + 8 + h2 + 34;
        float y = top - h;
        ui.slab(x, y, w, h, 18, UI_CREAM, UI_CREAM_LIP);
        float cy = top - 18;
        ui.text(fit(missionTitle, inner, 0.9f), x + 18, cy, 0.9f, UI_INK, false);
        cy -= 28;
        cy -= ui.textWrapped(missionGoal, x + 18, cy, inner, 0.82f, UI_INK_SOFT) + 22;
        ui.fill(x + 18, cy + 11, inner, 2, UI_CREAM_LIP);
        ui.text(challengeTitle, x + 18, cy, 0.9f, UI_ORANGE_LIP, false);
        cy -= 28;
        cy -= ui.textWrapped(challengeGoal, x + 18, cy, inner, 0.82f, UI_INK_SOFT) + 10;
        if (challenge != null) {
            String status = save.challengeClaimed ? "CONCLUÍDO" : save.challengeProgress + " / " + challenge.amount();
            float barW = inner - 90f;
            float progress = Math.min(1f, save.challengeProgress / (float) challenge.amount());
            ui.roundRect(x + 18, cy - 14, barW, 12, 6, UI_CREAM_LIP);
            if (progress > 0f) ui.roundRect(x + 18, cy - 14, Math.max(12f, barW * progress), 12, 6, UI_GREEN);
            ui.text(status, x + 18 + barW + 12, cy - 2, 0.8f, save.challengeClaimed ? UI_GREEN_LIP : UI_INK, false);
        }
    }

    private void renderPlaybackBar() {
        float w = 450f;
        float h = 70f;
        float x = screenWidth - w - 20f;
        float y = 26f;
        ui.slab(x, y, w, h, 20, UI_TERMINAL_SCREEN, UI_TERMINAL_FRAME_LIP);
        String label = compiling ? "COMPILANDO..." : "EXECUTANDO " + playbackIndex + "/" + playback.size();
        ui.text(label, x + 18, y + h / 2f + ui.capHeight(0.95f) / 2f, 0.95f, SUNFLOWER_PETAL, false);
        if (compiling) return;
        ui.button(x + 190, y + 14, 72, 42, (int) SPEEDS[speedIndex] + "x", UI_BLUE, UI_BLUE_LIP, Icon.NONE,
                () -> speedIndex = (speedIndex + 1) % SPEEDS.length);
        ui.button(x + 270, y + 14, 80, 42, "PULAR", UI_GREEN, UI_GREEN_LIP, Icon.NONE, this::skipPlayback);
        ui.button(x + 358, y + 14, 76, 42, "PARAR", UI_ORANGE, UI_ORANGE_LIP, Icon.NONE, () -> finishRun(true));
    }

    private void renderBanner() {
        float alpha = Math.min(1f, bannerTime / 0.6f);
        float scale = 1.25f;
        float w = Math.max(ui.labelWidth(banner, scale), ui.labelWidth(bannerDetail, 0.85f)) + 60f;
        w = Math.min(w, screenWidth - 40f);
        float h = 84f;
        float x = (screenWidth - w) / 2f;
        float y = screenHeight - 120f - h;
        overlay.set(UI_GREEN.r, UI_GREEN.g, UI_GREEN.b, alpha);
        bannerLip.set(UI_GREEN_LIP.r, UI_GREEN_LIP.g, UI_GREEN_LIP.b, alpha);
        ui.roundRect(x, y - 6, w, h, 24, bannerLip);
        ui.roundRect(x, y, w, h, 24, overlay);
        Color text = bannerText.set(UI_TEXT_LIGHT.r, UI_TEXT_LIGHT.g, UI_TEXT_LIGHT.b, alpha);
        ui.text(fit(banner, w - 40, scale), screenWidth / 2f, y + h - 18, scale, text, true);
        ui.text(fit(bannerDetail, w - 40, 0.85f), screenWidth / 2f, y + 32, 0.85f, text, true);
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

    // ------------------------------------------------------------------ objectives screen

    private void renderObjectives() {
        dim(0.5f);
        float w = Math.min(1180f, screenWidth - 40f);
        float h = Math.min(700f, screenHeight - 120f);
        float x = (screenWidth - w) / 2f;
        float y = (screenHeight - h) / 2f - 24f;
        ui.title("OBJETIVOS", screenWidth / 2f, y + h + 66f, 2.3f, UI_TEXT_LIGHT, WOOD_DARK);
        ui.card(x, y, w, h);

        // mission list
        float listW = 360f;
        float rowH = Math.min(34f, (h - 110f) / Missions.ALL.size());
        float rowY = y + h - 30f;
        ui.text("MISSÕES" + SEP + save.missionsCompleted + "/" + Missions.ALL.size(), x + 28, rowY, 0.95f, UI_INK, false);
        rowY -= 18f;
        for (int i = 0; i < Missions.ALL.size(); i++) {
            float ry = rowY - (i + 1) * rowH;
            boolean done = i < save.missionsCompleted;
            boolean current = i == save.missionsCompleted;
            boolean selected = i == selectedMission;
            if (selected) ui.roundRect(x + 18, ry, listW, rowH - 3, 10, UI_CREAM_LIP);
            else if (ui.hovered(x + 18, ry, listW, rowH - 3)) {
                overlay.set(UI_CREAM_LIP.r, UI_CREAM_LIP.g, UI_CREAM_LIP.b, 0.5f);
                ui.roundRect(x + 18, ry, listW, rowH - 3, 10, overlay);
            }
            float cy = ry + (rowH - 3) / 2f;
            ui.dot(x + 38, cy, 10, done ? UI_GREEN : current ? UI_ORANGE : UI_NEUTRAL);
            if (done) ui.icon(Icon.CHECK, x + 38, cy, 13, UI_TEXT_LIGHT);
            else if (!current) ui.icon(Icon.LOCK, x + 38, cy, 12, UI_TEXT_LIGHT);
            Color ink = done || current ? UI_INK : UI_INK_SOFT;
            ui.text(fit((i + 1) + ". " + Missions.ALL.get(i).title(), listW - 56, 0.82f), x + 58, cy + ui.capHeight(0.82f) / 2f, 0.82f, ink, false);
            final int index = i;
            ui.hotspot(x + 18, ry, listW, rowH - 3, () -> selectedMission = index);
        }

        // mission detail
        float dx = x + listW + 44f;
        float dw = w - listW - 72f;
        Missions.Mission m = Missions.ALL.get(selectedMission);
        boolean unlocked = selectedMission <= save.missionsCompleted;
        float cy = y + h - 30f;
        String state = selectedMission < save.missionsCompleted ? "CONCLUÍDA" : selectedMission == save.missionsCompleted ? "MISSÃO ATUAL" : "BLOQUEADA";
        ui.text(state + SEP + "CONCEITO: " + m.concept().toUpperCase(Locale.ROOT), dx, cy, 0.8f, UI_ORANGE_LIP, false);
        cy -= 26;
        ui.text(m.title(), dx, cy, 1.5f, UI_INK, false);
        cy -= 44;
        cy -= ui.textWrapped("Objetivo: " + m.goal(), dx, cy, dw, 0.95f, UI_INK) + 14;
        if (unlocked) {
            cy -= ui.textWrapped(m.explanation(), dx, cy, dw, 0.85f, UI_INK_SOFT) + 16;
            String[] lines = m.hint().split("\n");
            float codeH = Math.min(lines.length, 16) * 19f + 20f;
            ui.roundRect(dx, cy - codeH, dw, codeH, 14, UI_TERMINAL_SCREEN);
            drawCodeLines(lines, dx + 16, cy - 12, 16, 0.8f);
            cy -= codeH + 18;
            ui.button(dx, cy - 44, 330, 44, "USAR EXEMPLO NO TERMINAL", UI_GREEN, UI_GREEN_LIP, Icon.CODE, () -> {
                setCode(m.hint());
                terminalMessage = "Exemplo da missão \"" + m.title() + "\" carregado. Ajuste e clique em EXECUTAR.";
                terminalMessageOk = true;
                screen = Screen.PLAYING;
                terminalOpen = true;
            });
        } else {
            ui.textWrapped("Conclua as missões anteriores para ver a explicação e o exemplo.", dx, cy, dw, 0.85f, UI_INK_SOFT);
        }

        // daily challenge strip
        if (challenge != null) {
            float sx = dx;
            float sy = y + 92f;
            float sw = dw;
            ui.roundRect(sx, sy - 66, sw, 92, 16, UI_CREAM_LIP);
            ui.text("DESAFIO DO DIA " + challenge.day() + SEP + "NÍVEL " + challenge.tier() + SEP + "RECOMPENSA "
                    + challenge.rewardCoins() + " MOEDAS", sx + 16, sy + 10, 0.8f, UI_ORANGE_LIP, false);
            ui.text(fit(DailyChallenges.describe(challenge), sw - 32, 0.85f), sx + 16, sy - 16, 0.85f, UI_INK, false);
            String status = save.challengeClaimed ? "CONCLUÍDO" : "PROGRESSO " + save.challengeProgress + " / " + challenge.amount();
            ui.text(status, sx + 16, sy - 42, 0.8f, save.challengeClaimed ? UI_GREEN_LIP : UI_INK_SOFT, false);
        }
        ui.button(x + 28, y + 26, 200, 48, "VOLTAR", UI_ORANGE, UI_ORANGE_LIP, Icon.NONE, () -> screen = screenBeforeOverlay);
    }

    private void drawCodeLines(String[] lines, float x, float top, int maxLines, float scale) {
        BitmapFont mono = ui.mono();
        float oldX = mono.getData().scaleX;
        float oldY = mono.getData().scaleY;
        mono.getData().setScale(ui.monoScale(scale));
        mono.getData().markupEnabled = true;
        ui.batch().begin();
        for (int i = 0; i < lines.length && i < maxLines; i++) {
            String line = lines[i].length() > 90 ? lines[i].substring(0, 90) : lines[i];
            mono.setColor(Color.WHITE);
            mono.draw(ui.batch(), highlightLine(line), x, top - i * 19f);
        }
        mono.getData().markupEnabled = false;
        ui.batch().end();
        mono.getData().setScale(oldX, oldY);
    }

    // ------------------------------------------------------------------ shop screen

    private void renderShop() {
        dim(0.5f);
        float w = Math.min(1180f, screenWidth - 40f);
        float h = Math.min(600f, screenHeight - 130f);
        float x = (screenWidth - w) / 2f;
        float y = (screenHeight - h) / 2f - 24f;
        ui.title("LOJA", screenWidth / 2f, y + h + 66f, 2.3f, UI_TEXT_LIGHT, WOOD_DARK);
        ui.card(x, y, w, h);
        ui.chip(x + w - 170, y + h - 60, Icon.COIN, farm.coins + " moedas", 40);

        float colGap = 18f;
        float colW = (w - 56f - colGap * 3) / 4f;
        float colTop = y + h - 84f;
        float colH = h - 170f;
        String[] heads = {"VENDER COLHEITA", "SEMENTES", "TERRENO", "MELHORIAS"};
        for (int c = 0; c < 4; c++) {
            float cx = x + 28 + c * (colW + colGap);
            ui.roundRect(cx, colTop - colH, colW, colH, 16, UI_CREAM_LIP);
            ui.roundRect(cx + 3, colTop - colH + 3, colW - 6, colH - 6, 14, UI_CREAM);
            ui.text(heads[c], cx + 16, colTop - 18, 0.85f, UI_ORANGE_LIP, false);
        }

        // sell
        float sx = x + 28;
        float ry = colTop - 64;
        for (CropType crop : CropType.values()) {
            int have = farm.items[crop.ordinal()];
            ui.dot(sx + 24, ry, 9, cropColor(crop));
            ui.text(crop.label + "  x" + have, sx + 40, ry + 7, 0.85f, UI_INK, false);
            ui.text(farm.sellPrice(crop) + " moedas cada", sx + 40, ry - 13, 0.7f, UI_INK_SOFT, false);
            if (have > 0) ui.button(sx + colW - 92, ry - 14, 76, 34, "VENDER", UI_GREEN, UI_GREEN_LIP, Icon.NONE, () -> sell(crop, have));
            else ui.disabledButton(sx + colW - 92, ry - 14, 76, 34, "VENDER");
            ry -= 62;
        }
        if (farm.totalItems() > 0) ui.button(sx + 16, colTop - colH + 22, colW - 32, 42, "VENDER TUDO", UI_GREEN, UI_GREEN_LIP, Icon.NONE, this::sellAll);

        // seeds
        float bx = x + 28 + colW + colGap;
        ry = colTop - 64;
        for (CropType crop : CropType.values()) {
            boolean open = farm.unlocked.contains(crop);
            ui.dot(bx + 24, ry, 9, open ? cropColor(crop) : UI_NEUTRAL);
            ui.text(crop.label + "  (" + farm.seeds[crop.ordinal()] + ")", bx + 40, ry + 7, 0.85f, open ? UI_INK : UI_INK_SOFT, false);
            if (open) {
                ui.text(crop.seedPrice + " moedas" + SEP + crop.growTicks / 10 + "h", bx + 40, ry - 13, 0.7f, UI_INK_SOFT, false);
                if (farm.coins >= crop.seedPrice) ui.button(bx + colW - 112, ry - 14, 46, 34, "+1", UI_BLUE, UI_BLUE_LIP, Icon.NONE, () -> buySeeds(crop, 1));
                else ui.disabledButton(bx + colW - 112, ry - 14, 46, 34, "+1");
                if (farm.coins >= crop.seedPrice * 10) ui.button(bx + colW - 60, ry - 14, 46, 34, "+10", UI_BLUE, UI_BLUE_LIP, Icon.NONE, () -> buySeeds(crop, 10));
                else ui.disabledButton(bx + colW - 60, ry - 14, 46, 34, "+10");
            } else {
                ui.text("Missão " + (Missions.unlockMissionFor(crop) + 1) + " libera", bx + 40, ry - 13, 0.7f, UI_INK_SOFT, false);
            }
            ry -= 62;
        }

        // land
        float lx = x + 28 + 2 * (colW + colGap);
        int size = farm.world.farmSize();
        float ly = colTop - 60;
        ui.text("Sua fazenda: " + size + "x" + size, lx + 16, ly, 1.0f, UI_INK, false);
        ly -= 34;
        drawFarmPreview(lx + 16, ly - 130, 130, size);
        ly -= 150;
        if (!Missions.landAvailable(save.missionsCompleted)) {
            ui.textWrapped("Conclua a missão " + (Missions.LAND_UNLOCK_MISSION + 1) + " (Hora de vender) para comprar terra.", lx + 16, ly, colW - 32, 0.8f, UI_INK_SOFT);
        } else if (farm.nextLandPrice() < 0) {
            ui.textWrapped("Tamanho máximo alcançado!", lx + 16, ly, colW - 32, 0.85f, UI_GREEN_LIP);
        } else {
            int next = size + 1;
            ui.textWrapped("Expandir para " + next + "x" + next + ": +" + (next * next - size * size) + " blocos", lx + 16, ly, colW - 32, 0.8f, UI_INK_SOFT);
            String label = "COMPRAR" + SEP + farm.nextLandPrice();
            if (farm.coins >= farm.nextLandPrice()) ui.button(lx + 16, colTop - colH + 22, colW - 32, 42, label, UI_ORANGE, UI_ORANGE_LIP, Icon.NONE, this::buyLand);
            else ui.disabledButton(lx + 16, colTop - colH + 22, colW - 32, 42, label);
        }

        // upgrades
        float ux = x + 28 + 3 * (colW + colGap);
        float uy = colTop - 60;
        for (Farm.Upgrade upgrade : Farm.Upgrade.values()) {
            boolean available = Missions.upgradeAvailable(upgrade, save.missionsCompleted);
            boolean owned = farm.upgrades.contains(upgrade);
            ui.text(upgrade.label, ux + 16, uy, 0.9f, available || owned ? UI_INK : UI_INK_SOFT, false);
            uy -= 24;
            String desc = available || owned ? upgrade.description
                    : "Liberado após a missão " + ((upgrade == Farm.Upgrade.REGADOR ? Missions.REGADOR_UNLOCK_MISSION : Missions.MERCADO_UNLOCK_MISSION) + 1) + ".";
            uy -= ui.textWrapped(desc, ux + 16, uy, colW - 32, 0.72f, UI_INK_SOFT) + 10;
            if (owned) ui.disabledButton(ux + 16, uy - 34, colW - 32, 34, "COMPRADO");
            else if (available && farm.coins >= upgrade.price) ui.button(ux + 16, uy - 34, colW - 32, 34, "COMPRAR" + SEP + upgrade.price, UI_ORANGE, UI_ORANGE_LIP, Icon.NONE, () -> buyUpgrade(upgrade));
            else ui.disabledButton(ux + 16, uy - 34, colW - 32, 34, "COMPRAR" + SEP + upgrade.price);
            uy -= 70;
        }

        ui.button(x + 28, y + 26, 200, 48, "VOLTAR", UI_ORANGE, UI_ORANGE_LIP, Icon.NONE, () -> screen = screenBeforeOverlay);
        ui.text(fit(lastLog, w - 300, 0.85f), x + 250, y + 56, 0.85f, UI_INK_SOFT, false);
    }

    private void drawFarmPreview(float x, float y, float side, int size) {
        int max = FarmWorld.MAX_FARM_SIZE;
        float cell = side / max;
        for (int gy = 0; gy < max; gy++) {
            for (int gx = 0; gx < max; gx++) {
                Color color = gx < size && gy < size ? GRASS : gx < size + 1 && gy < size + 1 ? UI_NEUTRAL : UI_CREAM_LIP;
                ui.fill(x + gx * cell + 1, y + gy * cell + 1, cell - 2, cell - 2, color);
            }
        }
    }

    private static Color cropColor(CropType crop) {
        return switch (crop) {
            case WHEAT -> WHEAT_RIPE;
            case CARROT -> CARROT;
            case SUNFLOWER -> SUNFLOWER_PETAL;
            case PUMPKIN -> PUMPKIN;
        };
    }

    // ------------------------------------------------------------------ terminal

    private void renderTerminal() {
        overlay.set(0.16f, 0.12f, 0.09f, 0.35f);
        ui.fill(0, 0, screenWidth, screenHeight, overlay);
        float w = Math.min(1180, screenWidth - 44);
        float h = Math.min(700, screenHeight - 70);
        float x = (screenWidth - w) / 2f;
        float y = (screenHeight - h) / 2f + 4f;

        ui.slab(x, y, w, h, 28, UI_TERMINAL_FRAME, UI_TERMINAL_FRAME_LIP);
        float refW = w >= 1000 ? 300f : 0f;
        float sx = x + 16;
        float sy = y + 84;
        float sw = w - 32 - (refW > 0 ? refW + 12 : 0);
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

        if (refW > 0) {
            float rx = sx + sw + 12;
            ui.roundRect(rx, sy, refW, sh, 18, UI_CREAM);
            ui.text("API FarmerBot", rx + 16, sy + sh - 18, 1.0f, UI_INK, false);
            float ly = sy + sh - 50;
            for (String line : API_REFERENCE) {
                if (line.isEmpty()) { ly -= 8; continue; }
                boolean header = line.startsWith("#");
                ui.text(header ? line.substring(1) : line, rx + 16, ly, header ? 0.72f : 0.74f, header ? UI_ORANGE_LIP : UI_INK, false);
                ly -= header ? 22 : 20;
            }
            ly -= 6;
            if (save.missionsCompleted < Missions.ALL.size()) {
                ui.text("MISSÃO ATUAL", rx + 16, ly, 0.72f, UI_ORANGE_LIP, false);
                ly -= 20;
                ui.textWrapped(Missions.ALL.get(save.missionsCompleted).goal(), rx + 16, ly, refW - 32, 0.74f, UI_INK_SOFT);
            }
        }

        ui.text("Cada ação gasta 6 min do jogo; waitCycles(1) = 1 hora. Máximo de 250 ações por execução.",
                x + 26, y + 66, 0.8f, UI_INK, false);
        ui.text(fit(terminalMessage, w - 400f, 0.85f), x + 26, y + 38, 0.85f, terminalMessageOk ? UI_GREEN_LIP : TRACTOR_DARK, false);
        if (compiling) ui.disabledButton(x + w - 336, y + 18, 170, 46, "COMPILANDO...");
        else ui.button(x + w - 336, y + 18, 170, 46, "EXECUTAR", UI_GREEN, UI_GREEN_LIP, Icon.PLAY, this::runProgram);
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

    // ------------------------------------------------------------------ misc actions

    private void openTerminal() {
        if (isPlaying()) return;
        if (screen != Screen.PLAYING) screen = Screen.PLAYING;
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
        String single = text.replace('\n', ' ');
        if (ui.labelWidth(single, scale) <= maxWidth) return single;
        int end = single.length();
        while (end > 4 && ui.labelWidth(single.substring(0, end) + "...", scale) > maxWidth) end -= 2;
        return single.substring(0, Math.max(0, end)) + "...";
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
                else if (screen == Screen.OBJECTIVES || screen == Screen.SHOP) screen = screenBeforeOverlay;
                else if (screen == Screen.SETTINGS) { saveGame(); screen = Screen.MENU; }
                confirmNewGame = false;
                return true;
            }
            if (screen == Screen.PLAYING) {
                if (keycode == Input.Keys.T) { openTerminal(); swallowNextTyped = true; return true; }
                if (keycode == Input.Keys.O || keycode == Input.Keys.C) { openOverlay(Screen.OBJECTIVES); return true; }
                if (keycode == Input.Keys.L) { openOverlay(Screen.SHOP); return true; }
                if (isPlaying() || compiling) return false;
                if (keycode == Input.Keys.W || keycode == Input.Keys.UP) return move("NORTH");
                if (keycode == Input.Keys.S || keycode == Input.Keys.DOWN) return move("SOUTH");
                if (keycode == Input.Keys.A || keycode == Input.Keys.LEFT) return move("WEST");
                if (keycode == Input.Keys.D || keycode == Input.Keys.RIGHT) return move("EAST");
            } else if (screen == Screen.OBJECTIVES || screen == Screen.SHOP) {
                if (keycode == Input.Keys.O && screen == Screen.OBJECTIVES) { screen = screenBeforeOverlay; return true; }
                if (keycode == Input.Keys.L && screen == Screen.SHOP) { screen = screenBeforeOverlay; return true; }
            }
            return false;
        }

        private boolean move(String direction) {
            int dayBefore = farm.world.day();
            if (!farm.world.move(direction)) lastLog = "Caminho bloqueado: água ou construção à frente.";
            if (farm.world.day() > dayBefore) onNewDay();
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
                    int start = cursor == 0 ? 0 : code.lastIndexOf("\n", cursor - 1) + 1;
                    int indent = 0;
                    while (start + indent < cursor && code.charAt(start + indent) == ' ') indent++;
                    if (cursor > 0 && code.charAt(cursor - 1) == '{') indent += 4;
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
        if (isPlaying()) skipPlayback();
        saveGame();
        if (ambience != null) ambience.pause();
    }

    @Override
    public void resume() {
        if (ambience != null && settings.soundVolume > 0.001f) ambience.play();
    }

    @Override
    public void dispose() {
        if (isPlaying()) skipPlayback();
        saveGame();
        if (ambience != null) ambience.dispose();
        if (clickSound != null) clickSound.dispose();
        if (harvestSound != null) harvestSound.dispose();
        farmRenderer.dispose();
        ui.dispose();
    }
}
