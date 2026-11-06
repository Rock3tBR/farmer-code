package com.javafarmer.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.javafarmer.game.JavaFarmerGame;

public final class Lwjgl3Launcher {
    private Lwjgl3Launcher() {}

    public static void main(String[] args) {
        // Use libGDX's async GLFW shim so macOS can launch without requiring -XstartOnFirstThread.
        Lwjgl3ApplicationConfiguration.useGlfwAsync();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Java Farmer - Aprenda Java cultivando ideias");
        config.setWindowedMode(1440, 900);
        config.setWindowIcon("icon-128.png", "icon-64.png", "icon-32.png");
        // 24-bit depth buffer avoids z-fighting between stacked clay shapes; 4x MSAA smooths their rounded edges.
        config.setBackBufferConfig(8, 8, 8, 8, 24, 0, 4);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setAudioConfig(32, 512, 9);
        new Lwjgl3Application(new JavaFarmerGame(), config);
    }
}
