package com.javafarmer.world;

public final class FarmTile {
    public enum Terrain { GRASS, DIRT, WATER }

    public final int x;
    public final int z;
    public final Terrain terrain;
    public boolean tilled;
    public String crop = "";
    public int growth;
    public boolean watered;

    FarmTile(int x, int z, Terrain terrain) {
        this.x = x;
        this.z = z;
        this.terrain = terrain;
    }

    public boolean hasCrop() {
        return crop != null && !crop.isBlank();
    }

    public boolean isReady() {
        return hasCrop() && growth >= 3;
    }
}
