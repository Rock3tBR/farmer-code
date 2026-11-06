import com.javafarmer.runtime.JavaProgramRunner;
import com.javafarmer.world.FarmWorld;

public final class CoreSmokeTest {
    public static void main(String[] args) {
        FarmWorld world = new FarmWorld(12345L);
        require(world.till(), "spawn soil should be tillable");
        require(world.plant("WHEAT"), "wheat should plant on tilled soil");
        world.waitCycles(3);
        require(world.harvest(), "crop should be harvestable after three cycles");
        require(!world.harvest(), "harvested tile should be empty");

        // Farmyard props are fixed obstacles placed outside the spawn clearing.
        FarmWorld yard = new FarmWorld(12345L);
        require(yard.structureAt(-1, -5) == FarmWorld.Structure.BARN, "barn should be at (-1,-5)");
        require(!yard.isWalkable(-1, -5), "barn tile must not be walkable");
        require(yard.tileAt(-1, -5).terrain == com.javafarmer.world.FarmTile.Terrain.GRASS, "props stand on grass");
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            require(yard.structureAt(x, z) == FarmWorld.Structure.NONE, "spawn clearing must stay free");
        yard.restorePlayer(-1, -4, 0);
        require(!yard.move("NORTH"), "robot must be blocked by the barn");
        require(yard.playerZ() == -4, "blocked move must not change position");

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

        String unsafeProgram = """
                import com.javafarmer.api.FarmerBot;
                public class PlayerProgram {
                    public static void main(String[] args) {
                        System.exit(0);
                    }
                }
                """;
        JavaProgramRunner.ProgramResult unsafe = runner.run(unsafeProgram);
        require(!unsafe.success(), "unsafe Java API should be rejected");

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

        System.out.println("CORE SMOKE TEST: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
