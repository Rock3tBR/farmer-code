package com.javafarmer.world;

import java.util.HashMap;
import java.util.Map;

/** Procedural chunkless world: terrain is deterministic for every coordinate and crop state is stored lazily. */
public final class FarmWorld {
    /** Fixed farmyard props near spawn. They are decorative obstacles: not walkable, tillable or plantable. */
    public enum Structure { NONE, BARN, CRATE, CHEST, SOLAR_PANEL, PUMP }

    private final long seed;
    private final Map<Long, FarmTile> tiles = new HashMap<>();
    private int playerX;
    private int playerZ;
    private int cycles;

    public FarmWorld(long seed) {
        this.seed = seed;
    }

    public int playerX() { return playerX; }
    public int playerZ() { return playerZ; }
    public int cycles() { return cycles; }

    public FarmTile tileAt(int x, int z) {
        long key = key(x, z);
        return tiles.computeIfAbsent(key, ignored -> new FarmTile(x, z, terrainAt(x, z)));
    }

    public boolean move(String direction) {
        int nx = playerX;
        int nz = playerZ;
        switch (direction.toUpperCase()) {
            case "NORTH", "N" -> nz--;
            case "SOUTH", "S" -> nz++;
            case "EAST", "E" -> nx++;
            case "WEST", "W" -> nx--;
            default -> { return false; }
        }
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

    /** Deterministic farmyard layout just outside the safe spawn clearing. */
    public Structure structureAt(int x, int z) {
        if (z == -5) {
            if (x == -1) return Structure.BARN;
            if (x == 1) return Structure.CRATE;
            if (x == 2) return Structure.CHEST;
        }
        if (x == 5 && z == -1) return Structure.SOLAR_PANEL;
        if (x == -5 && z == 1) return Structure.PUMP;
        return Structure.NONE;
    }

    public boolean till() {
        FarmTile tile = tileAt(playerX, playerZ);
        if (tile.terrain == FarmTile.Terrain.WATER || tile.hasCrop() || structureAt(playerX, playerZ) != Structure.NONE) return false;
        tile.tilled = true;
        return true;
    }

    public boolean plant(String crop) {
        FarmTile tile = tileAt(playerX, playerZ);
        if (tile.terrain == FarmTile.Terrain.WATER || tile.hasCrop() || structureAt(playerX, playerZ) != Structure.NONE) return false;
        if (!tile.tilled) tile.tilled = true;
        tile.crop = normalizeCrop(crop);
        tile.growth = 0;
        tile.watered = false;
        return true;
    }

    public boolean water() {
        FarmTile tile = tileAt(playerX, playerZ);
        if (!tile.hasCrop()) return false;
        tile.watered = true;
        return true;
    }

    public int waitCycles(int amount) {
        int safeAmount = Math.max(1, Math.min(amount, 24));
        cycles += safeAmount;
        int grown = 0;
        for (FarmTile tile : tiles.values()) {
            if (!tile.hasCrop()) continue;
            int growthPerCycle = tile.watered ? 2 : 1;
            int before = tile.growth;
            tile.growth = Math.min(3, tile.growth + safeAmount * growthPerCycle);
            if (before < 3 && tile.growth >= 3) grown++;
            tile.watered = false;
        }
        return grown;
    }

    public boolean harvest() {
        FarmTile tile = tileAt(playerX, playerZ);
        if (!tile.isReady()) return false;
        tile.crop = "";
        tile.growth = 0;
        tile.tilled = true;
        return true;
    }

    public void restorePlayer(int x, int z, int savedCycles) {
        playerX = x;
        playerZ = z;
        cycles = Math.max(0, savedCycles);
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

    public void restoreTile(int x, int z, boolean tilled, String crop, int growth, boolean watered) {
        FarmTile tile = tileAt(x, z);
        tile.tilled = tilled;
        tile.crop = crop == null ? "" : crop;
        tile.growth = Math.max(0, Math.min(3, growth));
        tile.watered = watered;
    }

    private FarmTile.Terrain terrainAt(int x, int z) {
        // A small safe clearing around spawn guarantees the first tutorial is never blocked.
        if (Math.abs(x) <= 3 && Math.abs(z) <= 3) return FarmTile.Terrain.DIRT;
        // Props always stand on grass so they never float on water.
        if (structureAt(x, z) != Structure.NONE) return FarmTile.Terrain.GRASS;
        long value = mix(seed ^ (((long) x) << 32) ^ (z & 0xffffffffL));
        int roll = (int) Math.floorMod(value, 100);
        if (roll < 9) return FarmTile.Terrain.WATER;
        if (roll < 43) return FarmTile.Terrain.DIRT;
        return FarmTile.Terrain.GRASS;
    }

    private static String normalizeCrop(String crop) {
        if (crop == null) return "WHEAT";
        return switch (crop.toUpperCase()) {
            case "CARROT", "CENOURA", "CARROTS" -> "CARROT";
            case "PUMPKIN", "ABOBORA", "ABÓBORA" -> "PUMPKIN";
            case "SUNFLOWER", "GIRASSOL" -> "SUNFLOWER";
            default -> "WHEAT";
        };
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
