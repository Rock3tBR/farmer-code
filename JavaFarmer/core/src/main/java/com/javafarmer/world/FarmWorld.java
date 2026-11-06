package com.javafarmer.world;

import java.util.HashMap;
import java.util.Map;

/**
 * Procedural chunkless world: terrain is deterministic for every coordinate and crop state is stored lazily.
 *
 * <p>Time is measured in ticks. Every robot action costs one tick, {@code waitCycles(n)} costs
 * {@code n * 10} ticks and a game day lasts {@value #TICKS_PER_DAY} ticks (24 cycles, one per hour,
 * starting at 06:00). Crops grow while time passes.</p>
 *
 * <p>The player's farm is a square that starts at the spawn tile (0,0) and grows east (+x) and north
 * (-z). Inside the maximum farm area there is never water; only owned tiles can be tilled or planted.</p>
 */
public final class FarmWorld {
    /** Fixed farmyard props near spawn. They are decorative obstacles: not walkable, tillable or plantable. */
    public enum Structure { NONE, BARN, CRATE, CHEST, SOLAR_PANEL, PUMP }

    public static final int TICKS_PER_CYCLE = 10;
    public static final int TICKS_PER_DAY = 240;
    public static final int START_HOUR = 6;
    public static final int MIN_FARM_SIZE = 3;
    public static final int MAX_FARM_SIZE = 12;
    public static final int WATER_TICKS = 60;

    private final long seed;
    private final Map<Long, FarmTile> tiles = new HashMap<>();
    private int playerX;
    private int playerZ;
    private long ticks;
    private int farmSize = MIN_FARM_SIZE;

    public FarmWorld(long seed) {
        this.seed = seed;
    }

    public long seed() { return seed; }
    public int playerX() { return playerX; }
    public int playerZ() { return playerZ; }
    public long ticks() { return ticks; }
    public int cycles() { return (int) Math.min(Integer.MAX_VALUE, ticks / TICKS_PER_CYCLE); }

    /** Current game day, starting at 1. */
    public int day() { return (int) Math.min(Integer.MAX_VALUE, 1 + ticks / TICKS_PER_DAY); }

    /** Minutes since midnight of the in-game clock (each tick is six minutes; days start at 06:00). */
    public int clockMinutes() {
        int minutesIntoDay = (int) (ticks % TICKS_PER_DAY) * 6;
        return (START_HOUR * 60 + minutesIntoDay) % (24 * 60);
    }

    public int farmSize() { return farmSize; }

    public void setFarmSize(int size) {
        farmSize = Math.max(MIN_FARM_SIZE, Math.min(MAX_FARM_SIZE, size));
    }

    /** Farm coordinates used by player programs: x grows to the east, y grows to the north. */
    public int farmX(int worldX) { return worldX; }
    public int farmY(int worldZ) { return -worldZ; }

    public boolean inFarmArea(int x, int z) {
        return x >= 0 && x < MAX_FARM_SIZE && z <= 0 && z > -MAX_FARM_SIZE;
    }

    public boolean isOwned(int x, int z) {
        return x >= 0 && x < farmSize && z <= 0 && z > -farmSize;
    }

    public FarmTile tileAt(int x, int z) {
        long key = key(x, z);
        return tiles.computeIfAbsent(key, ignored -> new FarmTile(x, z, terrainAt(x, z)));
    }

    // ------------------------------------------------------------------ actions (each costs time)

    public boolean move(String direction) {
        int nx = playerX;
        int nz = playerZ;
        switch (direction == null ? "" : direction.toUpperCase()) {
            case "NORTH", "N", "NORTE" -> nz--;
            case "SOUTH", "S", "SUL" -> nz++;
            case "EAST", "E", "LESTE" -> nx++;
            case "WEST", "W", "OESTE" -> nx--;
            default -> { return false; }
        }
        advance(1);
        if (!isWalkable(nx, nz)) return false;
        playerX = nx;
        playerZ = nz;
        tileAt(playerX, playerZ);
        trimTransientTiles(playerX, playerZ, 22);
        return true;
    }

    /** True when the robot may enter the given tile (no water and no farmyard structure). */
    public boolean isWalkable(int x, int z) {
        return terrainAt(x, z) != FarmTile.Terrain.WATER && structureAt(x, z) == Structure.NONE;
    }

    /** Deterministic farmyard layout just outside the farm area (south-west of spawn). */
    public Structure structureAt(int x, int z) {
        if (z == 2) {
            if (x == -2) return Structure.BARN;
            if (x == 0) return Structure.CRATE;
            if (x == 1) return Structure.CHEST;
        }
        if (x == -2 && z == -2) return Structure.SOLAR_PANEL;
        if (x == -2 && z == -4) return Structure.PUMP;
        return Structure.NONE;
    }

    public boolean till() {
        advance(1);
        FarmTile tile = tileAt(playerX, playerZ);
        if (!isOwned(playerX, playerZ) || tile.terrain == FarmTile.Terrain.WATER || tile.hasCrop()) return false;
        tile.tilled = true;
        return true;
    }

    /** Plants on the robot's tile (tilling it automatically). Unknown crop names are rejected. */
    public boolean plant(String crop) {
        advance(1);
        CropType type = CropType.parse(crop);
        FarmTile tile = tileAt(playerX, playerZ);
        if (type == null || !isOwned(playerX, playerZ) || tile.terrain == FarmTile.Terrain.WATER || tile.hasCrop()) return false;
        tile.tilled = true;
        tile.crop = type.name();
        tile.progress = 0;
        tile.refreshStage();
        return true;
    }

    public boolean water() {
        advance(1);
        FarmTile tile = tileAt(playerX, playerZ);
        if (!tile.hasCrop()) return false;
        tile.waterTicks = WATER_TICKS;
        tile.refreshStage();
        return true;
    }

    /** Waits 1..24 cycles. Returns how many crops became ready while waiting. */
    public int waitCycles(int amount) {
        int safeAmount = Math.max(1, Math.min(amount, 24));
        return advance((long) safeAmount * TICKS_PER_CYCLE);
    }

    public boolean harvest() {
        return harvestCrop() != null;
    }

    /** Harvests a ready crop on the robot's tile and returns its type, or null. */
    public CropType harvestCrop() {
        advance(1);
        FarmTile tile = tileAt(playerX, playerZ);
        if (!tile.isReady()) return null;
        CropType type = tile.cropType();
        tile.crop = "";
        tile.progress = 0;
        tile.waterTicks = 0;
        tile.tilled = true;
        tile.refreshStage();
        return type;
    }

    /** Lets time pass: crops grow (twice as fast while watered). Returns how many crops matured. */
    public int advance(long amount) {
        if (amount <= 0) return 0;
        ticks += amount;
        int matured = 0;
        for (FarmTile tile : tiles.values()) {
            CropType type = tile.cropType();
            if (type == null) continue;
            boolean wasReady = tile.progress >= type.growTicks;
            long wet = Math.min(amount, tile.waterTicks);
            long growth = amount + wet;
            tile.progress = (int) Math.min(type.growTicks, tile.progress + growth);
            tile.waterTicks -= (int) wet;
            tile.refreshStage();
            if (!wasReady && tile.progress >= type.growTicks) matured++;
        }
        return matured;
    }

    // ------------------------------------------------------------------ persistence helpers

    public void restorePlayer(int x, int z, long savedTicks) {
        playerX = x;
        playerZ = z;
        ticks = Math.max(0, savedTicks);
        tileAt(x, z);
    }

    public Map<Long, FarmTile> getTiles() {
        return tiles;
    }

    public void trimTransientTiles(int centerX, int centerZ, int keepRadius) {
        tiles.entrySet().removeIf(entry -> {
            FarmTile tile = entry.getValue();
            return Math.max(Math.abs(tile.x - centerX), Math.abs(tile.z - centerZ)) > keepRadius
                    && !tile.tilled && !tile.hasCrop();
        });
    }

    /** Legacy (save v1) restore: growth stage 0..3 is converted to growth ticks. */
    public void restoreTile(int x, int z, boolean tilled, String crop, int growth, boolean watered) {
        CropType type = CropType.parse(crop);
        int progress = type == null ? 0 : Math.max(0, Math.min(3, growth)) * type.growTicks / 3;
        restoreTile(x, z, tilled, crop, progress, watered ? WATER_TICKS : 0);
    }

    public void restoreTile(int x, int z, boolean tilled, String crop, int progress, int waterTicks) {
        FarmTile tile = tileAt(x, z);
        CropType type = CropType.parse(crop);
        tile.tilled = tilled || type != null;
        tile.crop = type == null ? "" : type.name();
        tile.progress = type == null ? 0 : Math.max(0, Math.min(type.growTicks, progress));
        tile.waterTicks = Math.max(0, Math.min(WATER_TICKS, waterTicks));
        tile.refreshStage();
    }

    private FarmTile.Terrain terrainAt(int x, int z) {
        // The whole farm area is fertile grass, so loops over the farm never hit water.
        if (inFarmArea(x, z)) return FarmTile.Terrain.GRASS;
        // Props always stand on grass so they never float on water.
        if (structureAt(x, z) != Structure.NONE) return FarmTile.Terrain.GRASS;
        // A small dirt yard around spawn keeps the farmyard reachable.
        if (x >= -4 && x <= 3 && z >= -5 && z <= 4) return FarmTile.Terrain.DIRT;
        long value = mix(seed ^ (((long) x) << 32) ^ (z & 0xffffffffL));
        int roll = (int) Math.floorMod(value, 100);
        if (roll < 9) return FarmTile.Terrain.WATER;
        if (roll < 43) return FarmTile.Terrain.DIRT;
        return FarmTile.Terrain.GRASS;
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
