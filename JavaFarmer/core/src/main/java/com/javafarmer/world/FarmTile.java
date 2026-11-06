package com.javafarmer.world;

public final class FarmTile {
    public enum Terrain { GRASS, DIRT, WATER }

    public final int x;
    public final int z;
    public final Terrain terrain;
    public boolean tilled;
    public String crop = "";
    /** Visual growth stage 0..3, derived from {@link #progress} (kept for renderer and old saves). */
    public int growth;
    /** True while the soil is still wet ({@link #waterTicks} &gt; 0). */
    public boolean watered;
    /** Growth ticks accumulated by the current crop. */
    public int progress;
    /** Remaining ticks of double-speed growth from watering. */
    public int waterTicks;

    FarmTile(int x, int z, Terrain terrain) {
        this.x = x;
        this.z = z;
        this.terrain = terrain;
    }

    public boolean hasCrop() {
        return crop != null && !crop.isBlank();
    }

    public CropType cropType() {
        return hasCrop() ? CropType.parse(crop) : null;
    }

    public boolean isReady() {
        CropType type = cropType();
        return type != null && progress >= type.growTicks;
    }

    /** Recomputes {@link #growth} and {@link #watered} from the tick counters. */
    void refreshStage() {
        watered = waterTicks > 0;
        CropType type = cropType();
        if (type == null) { growth = 0; return; }
        if (progress <= 0) growth = 0;
        else if (progress >= type.growTicks) growth = 3;
        else growth = 1 + (progress * 2) / type.growTicks;
    }
}
