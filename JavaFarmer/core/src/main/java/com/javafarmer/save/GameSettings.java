package com.javafarmer.save;

public final class GameSettings {
    public float soundVolume = 0.45f;
    public int brightness = 100;
    public String graphics = "MEDIO";
    public boolean fullscreen = false;

    public int visibleRadius() {
        return switch (graphics) {
            case "BAIXO" -> 6;
            case "ALTO" -> 10;
            default -> 8;
        };
    }

    public int targetFps() {
        return switch (graphics) {
            case "BAIXO" -> 30;
            case "ALTO" -> 120;
            default -> 60;
        };
    }
}
