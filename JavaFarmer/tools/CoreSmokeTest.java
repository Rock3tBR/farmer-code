import com.javafarmer.progress.DailyChallenges;
import com.javafarmer.progress.Missions;
import com.javafarmer.progress.RunReport;
import com.javafarmer.runtime.JavaProgramRunner;
import com.javafarmer.runtime.ProgramFeature;
import com.javafarmer.save.GameSave;
import com.javafarmer.save.SaveMapper;
import com.javafarmer.world.CropType;
import com.javafarmer.world.Farm;
import com.javafarmer.world.FarmTile;
import com.javafarmer.world.FarmWorld;

import java.util.List;

public final class CoreSmokeTest {
    public static void main(String[] args) {
        worldRules();
        farmEconomy();
        runnerAndSensors();
        validatorRejectsUnsafeCode();
        missionHintsAreValidPrograms();
        dailyChallengesScaleWithDays();
        saveMigrationAndRoundTrip();
        System.out.println("CORE SMOKE TEST: PASS");
    }

    private static void worldRules() {
        FarmWorld world = new FarmWorld(12345L);
        require(world.till(), "spawn soil should be tillable");
        require(world.plant("WHEAT"), "wheat should plant on tilled soil");
        world.waitCycles(3);
        require(world.harvest(), "crop should be harvestable after three cycles");
        require(!world.harvest(), "harvested tile should be empty");
        require(!world.plant("BANANA"), "unknown crops are rejected");

        // time: 1 tick per action, 10 per cycle, 240 per day starting at 06:00
        FarmWorld clock = new FarmWorld(1L);
        require(clock.day() == 1 && clock.clockMinutes() == 6 * 60, "day 1 starts at 06:00");
        clock.waitCycles(24);
        require(clock.day() == 2 && clock.clockMinutes() == 6 * 60, "24 cycles = one full day");
        clock.move("EAST");
        require(clock.clockMinutes() == 6 * 60 + 6, "an action costs six in-game minutes");

        // watering doubles growth speed
        FarmWorld wet = new FarmWorld(2L);
        wet.plant("WHEAT");
        wet.water();
        wet.waitCycles(2);
        require(wet.tileAt(0, 0).isReady(), "watered wheat (30 ticks) matures in 2 cycles");
        FarmWorld dry = new FarmWorld(2L);
        dry.plant("WHEAT");
        dry.move("NORTH");
        dry.move("SOUTH");
        dry.waitCycles(2);
        require(!dry.tileAt(0, 0).isReady(), "dry wheat is not ready after 2 cycles");

        // ownership: only the farm square can be farmed, and it grows when land is bought
        FarmWorld land = new FarmWorld(3L);
        land.restorePlayer(3, 0, 0);
        require(!land.till(), "tile x=3 is outside a 3x3 farm");
        land.setFarmSize(4);
        require(land.till(), "after expanding to 4x4, x=3 is yours");
        for (int x = 0; x < FarmWorld.MAX_FARM_SIZE; x++) for (int z = 0; z > -FarmWorld.MAX_FARM_SIZE; z--)
            require(land.isWalkable(x, z), "the farm area never has water or props");

        // farmyard props are obstacles
        FarmWorld yard = new FarmWorld(12345L);
        require(yard.structureAt(-2, 2) == FarmWorld.Structure.BARN, "barn should be at (-2,2)");
        require(!yard.isWalkable(-2, 2), "barn tile must not be walkable");
        require(yard.tileAt(-2, 2).terrain == FarmTile.Terrain.GRASS, "props stand on grass");
        yard.restorePlayer(-2, 1, 0);
        require(!yard.move("SOUTH"), "robot must be blocked by the barn");
        require(yard.playerZ() == 1, "blocked move must not change position");
    }

    private static void farmEconomy() {
        Farm farm = new Farm(9L);
        require(farm.seeds[CropType.WHEAT.ordinal()] == 10 && farm.coins == 25, "new farm: 10 wheat seeds, 25 coins");
        require(!farm.apply("PLANT", "CARROT").ok(), "carrots start locked");
        require(farm.apply("PLANT", "WHEAT").ok(), "plant wheat");
        require(farm.seeds[CropType.WHEAT.ordinal()] == 9, "planting uses a seed");
        require(!farm.apply("WATER", "").ok(), "watering needs the Regador upgrade");
        farm.apply("WAIT", "3");
        Farm.Result harvest = farm.apply("HARVEST", "");
        require(harvest.ok() && harvest.crop() == CropType.WHEAT, "harvest returns the crop");
        require(farm.items[CropType.WHEAT.ordinal()] == 1, "harvest goes to the stock");
        Farm.Result sold = farm.apply("SELL", "WHEAT");
        require(sold.ok() && sold.coins() == 3 && farm.coins == 28, "selling wheat pays 3 coins");
        require(farm.nextLandPrice() == 40, "first expansion costs 40");
        require(!farm.buyLand(), "cannot afford land yet");
        farm.coins = 1000;
        require(farm.buyLand() && farm.world.farmSize() == 4, "buy land expands the farm");
        require(farm.buyUpgrade(Farm.Upgrade.REGADOR), "buy Regador");
        farm.unlocked.add(CropType.CARROT);
        require(farm.buySeeds(CropType.CARROT, 10), "buy carrot seeds");

        Farm copy = Farm.fromSnapshot(farm.snapshot());
        require(copy.coins == farm.coins && copy.world.farmSize() == 4 && copy.upgrades.contains(Farm.Upgrade.REGADOR)
                && copy.seeds[CropType.CARROT.ordinal()] == 10 && copy.world.ticks() == farm.world.ticks(),
                "snapshot round trip keeps economy, land, upgrades and time");
    }

    private static void runnerAndSensors() {
        JavaProgramRunner runner = new JavaProgramRunner();
        String validProgram = """
                import com.javafarmer.api.FarmerBot;
                public class PlayerProgram {
                    public static void main(String[] args) {
                        FarmerBot.till();
                        FarmerBot.plant("WHEAT");
                        for (int i = 0; i < 3; i++) {
                            FarmerBot.waitCycles(1);
                        }
                        FarmerBot.harvest();
                    }
                }
                """;
        JavaProgramRunner.ProgramResult valid = runner.run(validProgram);
        require(valid.success(), "valid Java program should compile and run: " + valid.message());
        require(valid.actions().size() == 6, "expected six FarmerBot actions");
        require(valid.features().contains(ProgramFeature.FOR) && !valid.features().contains(ProgramFeature.VARIABLE),
                "for detected; the loop counter alone is not a 'variable'");

        // Sensors read the snapshot of the real farm and follow the actions the program takes.
        Farm farm = new Farm(4L);
        farm.apply("PLANT", "WHEAT");
        String sensing = """
                import com.javafarmer.api.FarmerBot;
                public class PlayerProgram {
                    static int esperas = 0;
                    static boolean pronto() { return FarmerBot.canHarvest(); }
                    public static void main(String[] args) {
                        while (!pronto()) { FarmerBot.waitCycles(1); esperas++; }
                        FarmerBot.harvest();
                        int[] xs = {1, 2};
                        for (int x : xs) { FarmerBot.move("EAST"); }
                        FarmerBot.log("x=" + FarmerBot.getX() + " esperas=" + esperas + " itens=" + FarmerBot.getItems("WHEAT"));
                    }
                }
                """;
        JavaProgramRunner.ProgramResult sensed = runner.run(sensing, farm.snapshot());
        require(sensed.success(), "sensor program should run: " + sensed.message());
        List<JavaProgramRunner.Action> actions = sensed.actions();
        require(actions.get(actions.size() - 1).value().equals("x=2 esperas=3 itens=1"),
                "sensors must mirror the farm: " + actions.get(actions.size() - 1));
        require(sensed.features().containsAll(List.of(ProgramFeature.WHILE, ProgramFeature.METHOD, ProgramFeature.ARRAY,
                ProgramFeature.SENSE, ProgramFeature.RETURN_VALUE, ProgramFeature.VARIABLE, ProgramFeature.LOG)),
                "features detected: " + sensed.features());
        for (JavaProgramRunner.Action action : actions) farm.apply(action.type(), action.value());
        require(farm.items[CropType.WHEAT.ordinal()] == 1 && farm.getX() == 2, "replaying actions gives the same result in the game");

        String logPayload = "x".repeat(10_000);
        String outputFlood = """
                import com.javafarmer.api.FarmerBot;
                public class PlayerProgram {
                    public static void main(String[] args) {
                        while (true) {
                            FarmerBot.log("%s");
                        }
                    }
                }
                """.formatted(logPayload);
        JavaProgramRunner.ProgramResult flooded = runner.run(outputFlood);
        require(!flooded.success() && flooded.message().contains("saída demais"),
                "excessive child output should be stopped by the output cap");
    }

    private static void validatorRejectsUnsafeCode() {
        JavaProgramRunner runner = new JavaProgramRunner();
        String[] unsafe = {
                "System.exit(0);",
                "Runtime.getRuntime();",
                "String s = new String(\"x\");",
                "Object o = null;",
                "int[] a = new int[2]; a.getClass();",
                "Character.getName(65);",
                "Thread.sleep(10);",
                "String p = FarmerBot.SNAPSHOT_FILE;",
                "java.io.File f = null;",
        };
        for (String body : unsafe) {
            String program = "import com.javafarmer.api.FarmerBot;\npublic class PlayerProgram { public static void main(String[] args) { "
                    + body + " } }";
            require(!runner.run(program).success(), "should be rejected: " + body);
        }
        String instanceMethod = "import com.javafarmer.api.FarmerBot;\npublic class PlayerProgram { void x() {} public static void main(String[] args) { } }";
        require(!runner.run(instanceMethod).success(), "instance methods are rejected");
        String otherImport = "import java.io.File;\npublic class PlayerProgram { public static void main(String[] args) { } }";
        require(!runner.run(otherImport).success(), "other imports are rejected");
    }

    private static void missionHintsAreValidPrograms() {
        JavaProgramRunner runner = new JavaProgramRunner();
        Farm farm = new Farm(5L);
        farm.unlocked.addAll(List.of(CropType.values()));
        farm.upgrades.add(Farm.Upgrade.REGADOR);
        for (int i = 0; i < CropType.values().length; i++) farm.seeds[i] = 50;
        for (int i = 0; i < Missions.ALL.size(); i++) {
            Missions.Mission mission = Missions.ALL.get(i);
            JavaProgramRunner.ProgramResult result = runner.run(mission.hint(), farm.snapshot());
            require(result.success(), "hint of mission " + (i + 1) + " must run: " + result.message() + "\n" + mission.hint());
        }
        // A hint should satisfy (or clearly progress) its own mission when run on a suitable farm.
        Farm fresh = new Farm(6L);
        RunReport report = replay(runner, Missions.ALL.get(3).hint(), fresh);
        require(Missions.ALL.get(3).check().test(new Missions.Context(report, fresh, new Missions.Lifetime())),
                "the 'for' hint completes the 'for' mission");
        Farm grid = new Farm(7L);
        grid.unlocked.add(CropType.CARROT);
        grid.seeds[CropType.CARROT.ordinal()] = 10;
        report = replay(runner, Missions.ALL.get(11).hint(), grid);
        require(Missions.ALL.get(11).check().test(new Missions.Context(report, grid, new Missions.Lifetime())),
                "the checkerboard hint completes the '%' mission");
    }

    private static RunReport replay(JavaProgramRunner runner, String source, Farm farm) {
        JavaProgramRunner.ProgramResult result = runner.run(source, farm.snapshot());
        require(result.success(), result.message());
        RunReport report = new RunReport(result.features());
        for (JavaProgramRunner.Action action : result.actions()) {
            Farm.Result r = farm.apply(action.type(), action.value());
            if (!r.ok()) continue;
            switch (action.type()) {
                case "MOVE" -> report.moves++;
                case "TILL" -> report.tills++;
                case "WATER" -> report.waters++;
                case "PLANT" -> report.planted[r.crop().ordinal()]++;
                case "HARVEST" -> report.harvested[r.crop().ordinal()]++;
                default -> { }
            }
        }
        return report;
    }

    private static void dailyChallengesScaleWithDays() {
        Farm early = new Farm(11L);
        int lastTier = 0;
        for (int day = 1; day <= 60; day++) {
            DailyChallenges.Challenge c = DailyChallenges.generate(day, early);
            require(c.tier() >= lastTier, "tier never decreases with days");
            lastTier = c.tier();
            require(c.size() <= early.world.farmSize(), "area challenges fit the farm");
            if (c.crop() != null && !c.crop().isEmpty()) {
                for (String id : c.crop().split("/")) require(early.unlocked.contains(CropType.parse(id)), "only unlocked crops: " + c);
            }
            require(!DailyChallenges.describe(c).isBlank(), "challenge has a description");
        }
        require(DailyChallenges.generate(1, early).tier() == 1 && DailyChallenges.generate(30, early).tier() == 6, "day 1 easy, day 30 hard");
        DailyChallenges.Challenge first = DailyChallenges.generate(1, early);
        require(!first.singleRun() && first.feature() == null, "day 1 challenges need no special Java concept");

        Farm late = new Farm(12L);
        late.unlocked.addAll(List.of(CropType.values()));
        late.world.setFarmSize(8);
        boolean sawAdvanced = false;
        for (int day = 21; day <= 40; day++) {
            DailyChallenges.Challenge c = DailyChallenges.generate(day, late);
            if (c.feature() == ProgramFeature.ARRAY || c.feature() == ProgramFeature.MODULO || c.feature() == ProgramFeature.RETURN_VALUE
                    || c.feature() == ProgramFeature.METHOD || c.kind() == DailyChallenges.Kind.EARN) sawAdvanced = true;
            if (c.kind() == DailyChallenges.Kind.HARVEST) require(c.amount() >= 14, "late harvest challenges are big");
        }
        require(sawAdvanced, "late challenges use advanced concepts");

        // progress: single-run challenges need the concept in that run
        DailyChallenges.Challenge forChallenge = new DailyChallenges.Challenge(3, 2, DailyChallenges.Kind.PLANT, "WHEAT", 3, 0,
                ProgramFeature.FOR, true, 10, 10);
        RunReport noFor = new RunReport(java.util.Set.of());
        noFor.planted[0] = 5;
        require(DailyChallenges.progress(forChallenge, 0, noFor, 0, early) == 0, "without for it does not count");
        RunReport withFor = new RunReport(java.util.Set.of(ProgramFeature.FOR));
        withFor.planted[0] = 5;
        require(DailyChallenges.progress(forChallenge, 0, withFor, 0, early) >= 3, "with for it counts");
    }

    private static void saveMigrationAndRoundTrip() {
        GameSave legacy = new GameSave(); // version 0 = 0.1/0.2 save
        legacy.seeds = 7;
        legacy.cycles = 30;
        legacy.coins = 40;
        legacy.playerX = 1;
        GameSave.TileSnapshot tile = new GameSave.TileSnapshot();
        tile.x = 1; tile.z = 0; tile.tilled = true; tile.crop = "WHEAT"; tile.growth = 3;
        legacy.tiles.add(tile);
        Farm migrated = SaveMapper.toFarm(legacy);
        require(migrated.seeds[CropType.WHEAT.ordinal()] == 7, "v1 seeds become wheat seeds");
        require(migrated.world.ticks() == 300 && migrated.world.day() == 2, "v1 cycles become ticks (30 cycles = day 2)");
        require(migrated.world.tileAt(1, 0).isReady(), "v1 growth 3 = ready crop");
        require(legacy.version == GameSave.CURRENT_VERSION, "save is upgraded to v2");

        GameSave out = new GameSave();
        migrated.world.setFarmSize(5);
        migrated.unlocked.add(CropType.CARROT);
        SaveMapper.fromFarm(migrated, out);
        out.missionsCompleted = 6;
        Farm again = SaveMapper.toFarm(out);
        require(again.world.farmSize() == 5 && again.unlocked.contains(CropType.CARROT)
                && again.world.tileAt(1, 0).isReady() && again.coins == 40, "v2 round trip");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
