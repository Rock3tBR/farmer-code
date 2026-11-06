package com.javafarmer.world;

import java.util.Locale;

/** Crops available in the game, with growth time (ticks) and shop prices (coins). */
public enum CropType {
    WHEAT("Trigo", 30, 1, 3),
    CARROT("Cenoura", 50, 5, 8),
    SUNFLOWER("Girassol", 70, 9, 15),
    PUMPKIN("Abóbora", 100, 14, 26);

    public final String label;
    /** Ticks needed to mature without water (1 cycle = 10 ticks). Watered crops grow twice as fast. */
    public final int growTicks;
    public final int seedPrice;
    public final int sellPrice;

    CropType(String label, int growTicks, int seedPrice, int sellPrice) {
        this.label = label;
        this.growTicks = growTicks;
        this.seedPrice = seedPrice;
        this.sellPrice = sellPrice;
    }

    /** Accepts English ids and Portuguese names ("WHEAT", "trigo", "Abóbora"...). Returns null if unknown. */
    public static CropType parse(String name) {
        if (name == null) return null;
        return switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "WHEAT", "TRIGO" -> WHEAT;
            case "CARROT", "CARROTS", "CENOURA" -> CARROT;
            case "SUNFLOWER", "GIRASSOL" -> SUNFLOWER;
            case "PUMPKIN", "ABOBORA", "ABÓBORA" -> PUMPKIN;
            default -> null;
        };
    }
}
