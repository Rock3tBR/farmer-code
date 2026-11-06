package com.javafarmer.api;

import com.javafarmer.world.Farm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * API exposed to player programs.
 *
 * <p>Actions are serialized as data for the game process, which replays them on the authoritative farm.
 * To make sensors such as {@link #canHarvest()} meaningful, this class also keeps a local copy of the farm
 * (rebuilt from a snapshot the game writes next to the program) and applies every action to it with the
 * exact same rules. The player's code never touches files: only this trusted class reads the snapshot.</p>
 */
public final class FarmerBot {
    /** Snapshot file written by the game into the program's private temporary directory. */
    public static final String SNAPSHOT_FILE = "farm-state.txt";

    private static final Farm FARM = loadFarm();

    private FarmerBot() {}

    private static Farm loadFarm() {
        try {
            Path file = Path.of(SNAPSHOT_FILE);
            if (Files.isRegularFile(file) && Files.size(file) < 4L * 1024 * 1024) {
                return Farm.fromSnapshot(Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // fall back to a fresh farm: sensors still work, the game remains authoritative
        }
        return new Farm(0L);
    }

    private static boolean emit(String action, String value) {
        String safe = value == null ? "" : value.replace("|", "/").replace("\n", " ");
        System.out.println("@@JF|" + action + "|" + safe);
        return FARM.apply(action, safe).ok();
    }

    // ----- actions (each one takes time in the game: 1 tick, or 10 ticks per waited cycle)

    /** Moves one tile: "NORTH", "SOUTH", "EAST" or "WEST". Returns false if blocked. */
    public static boolean move(String direction) { return emit("MOVE", direction); }
    /** Prepares the soil of the current tile (only on land you own). */
    public static boolean till() { return emit("TILL", ""); }
    /** Plants a crop ("WHEAT", "CARROT", "SUNFLOWER", "PUMPKIN") using one seed. */
    public static boolean plant(String crop) { return emit("PLANT", crop); }
    /** Waters the crop here (needs the Regador upgrade). Watered crops grow twice as fast. */
    public static boolean water() { return emit("WATER", ""); }
    /** Lets time pass: 1 cycle = 1 in-game hour. */
    public static void waitCycles(int cycles) { emit("WAIT", Integer.toString(cycles)); }
    /** Harvests the crop here if it is ready. */
    public static boolean harvest() { return emit("HARVEST", ""); }
    /** Sells all harvested units of a crop at the market. */
    public static boolean sell(String crop) { return emit("SELL", crop); }
    /** Sells up to {@code amount} harvested units of a crop. */
    public static boolean sell(String crop, int amount) { return emit("SELL", crop + ":" + amount); }
    /** Shows a message in the game log. */
    public static void log(String message) { emit("LOG", message); }

    // ----- sensors (instant, no time cost)

    /** Robot x on the farm (0 = west edge, grows to the east). */
    public static int getX() { return FARM.getX(); }
    /** Robot y on the farm (0 = south edge, grows to the north). */
    public static int getY() { return FARM.getY(); }
    /** Side of your square farm, in tiles. */
    public static int getFarmSize() { return FARM.world.farmSize(); }
    /** True if the crop on this tile is ready to harvest. */
    public static boolean canHarvest() { return FARM.canHarvest(); }
    /** Crop on this tile ("WHEAT", ...) or "" when empty. */
    public static String getCrop() { return FARM.getCrop(); }
    /** Ground of this tile: "GRASS", "DIRT", "TILLED" or "WATER". */
    public static String getGround() { return FARM.getGround(); }
    /** True while the soil here is still wet. */
    public static boolean isWatered() { return FARM.isWatered(); }
    /** True if this tile belongs to your farm. */
    public static boolean isMyLand() { return FARM.isOwnedHere(); }
    /** Seeds you have of a crop. */
    public static int getSeeds(String crop) { return FARM.getSeeds(crop); }
    /** Harvested units you have of a crop. */
    public static int getItems(String crop) { return FARM.getItems(crop); }
    /** Coins you have. */
    public static int getCoins() { return FARM.coins; }
    /** Current in-game day (starts at 1). */
    public static int getDay() { return FARM.world.day(); }
}
