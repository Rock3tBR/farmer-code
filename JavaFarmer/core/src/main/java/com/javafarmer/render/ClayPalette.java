package com.javafarmer.render;

import com.badlogic.gdx.graphics.Color;

/** Restrained "Cozy Clay" palette (docs/referencia_visual_cozy_clay.png). Shared by 3D models and UI. */
public final class ClayPalette {
    private ClayPalette() {}

    // Scene
    public static final Color BACKGROUND = rgb(0xE8E4DC);

    // Terrain
    public static final Color DIRT = rgb(0x9C613D);
    public static final Color DIRT_DARK = rgb(0x84502F);
    public static final Color TILLED = rgb(0x7E4B2D);
    public static final Color TILLED_RIDGE = rgb(0x8E5735);
    public static final Color WET = rgb(0x553321);
    public static final Color WET_RIDGE = rgb(0x613B26);
    public static final Color GRASS = rgb(0x7CC556);
    public static final Color GRASS_LIGHT = rgb(0x95D867);
    public static final Color GRASS_DARK = rgb(0x5FA843);
    public static final Color WATER = rgb(0x4CC0EE);
    public static final Color PEBBLE = rgb(0xB9AFA3);

    // Crops
    public static final Color SPROUT = rgb(0x8BD35E);
    public static final Color LEAF = rgb(0x52AE3F);
    public static final Color LEAF_DARK = rgb(0x3F9233);
    public static final Color STEM = rgb(0x5E9B3A);
    public static final Color WHEAT_YOUNG = rgb(0x9FCB4A);
    public static final Color WHEAT_MID = rgb(0xD3CF4E);
    public static final Color WHEAT_RIPE = rgb(0xF0C64A);
    public static final Color WHEAT_HEAD = rgb(0xF6D262);
    public static final Color CARROT = rgb(0xF27B2B);
    public static final Color SUNFLOWER_PETAL = rgb(0xF8C631);
    public static final Color SUNFLOWER_CENTER = rgb(0x7A4A22);
    public static final Color PUMPKIN = rgb(0xF2892B);
    public static final Color PUMPKIN_DARK = rgb(0xDB7322);
    public static final Color CACTUS = rgb(0x5CAE4E);
    public static final Color FLOWER_PINK = rgb(0xF59AB8);
    public static final Color FLOWER_WHITE = rgb(0xFFF6E6);
    public static final Color FLOWER_CENTER = rgb(0xF6C744);

    // Robot / drone
    public static final Color TRACTOR = rgb(0xE2553B);
    public static final Color TRACTOR_DARK = rgb(0xB9412D);
    public static final Color TRACTOR_LIGHT = rgb(0xEE7258);
    public static final Color GLASS = rgb(0x9CC9DA);
    public static final Color TRACK = rgb(0x3D3B40);
    public static final Color METAL = rgb(0x9EA3A8);
    public static final Color METAL_DARK = rgb(0x5E636A);
    public static final Color EYE_WHITE = rgb(0xF7F3EA);
    public static final Color PUPIL = rgb(0x26262C);
    public static final Color DRONE = rgb(0xF4C743);
    public static final Color DRONE_BAND = rgb(0xA4A9AE);

    // Props
    public static final Color BARN = rgb(0xC9432F);
    public static final Color ROOF = rgb(0xE9C46C);
    public static final Color ROOF_DARK = rgb(0xD3AA52);
    public static final Color TRIM = rgb(0xF4EAD5);
    public static final Color WOOD = rgb(0xBE8550);
    public static final Color WOOD_DARK = rgb(0x8D5A33);
    public static final Color GOLD = rgb(0xE5AE3F);
    public static final Color SOLAR = rgb(0x3E6FC6);
    public static final Color PUMP = rgb(0x4F6E8A);

    // UI
    public static final Color UI_CREAM = rgb(0xFBF5EA);
    public static final Color UI_CREAM_LIP = rgb(0xDCCFB9);
    public static final Color UI_INK = rgb(0x4A3326);
    public static final Color UI_INK_SOFT = rgb(0x8A7262);
    public static final Color UI_TEXT_LIGHT = rgb(0xFFF8EC);
    public static final Color UI_GREEN = rgb(0x66BF4E);
    public static final Color UI_GREEN_LIP = rgb(0x47933A);
    public static final Color UI_BLUE = rgb(0x3FA3E8);
    public static final Color UI_BLUE_LIP = rgb(0x2B7BBA);
    public static final Color UI_ORANGE = rgb(0xF58A35);
    public static final Color UI_ORANGE_LIP = rgb(0xC96A22);
    public static final Color UI_NEUTRAL = rgb(0xC9BBA6);
    public static final Color UI_NEUTRAL_LIP = rgb(0xA4937C);
    public static final Color UI_TERMINAL_FRAME = rgb(0x9DA2A8);
    public static final Color UI_TERMINAL_FRAME_LIP = rgb(0x767B82);
    public static final Color UI_TERMINAL_SCREEN = rgb(0x22272E);
    public static final Color UI_TERMINAL_HEADER = rgb(0x2D333B);

    private static Color rgb(int hex) {
        return new Color(((hex >> 16) & 0xff) / 255f, ((hex >> 8) & 0xff) / 255f, (hex & 0xff) / 255f, 1f);
    }
}
