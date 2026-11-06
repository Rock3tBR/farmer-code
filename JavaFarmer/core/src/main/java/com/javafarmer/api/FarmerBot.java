package com.javafarmer.api;

/** API exposed to player programs. Calls are serialized as data for the game process. */
public final class FarmerBot {
    private FarmerBot() {}

    private static void emit(String action, String value) {
        System.out.println("@@JF|" + action + "|" + value.replace("|", "/").replace("\n", " "));
    }

    public static void move(String direction) { emit("MOVE", direction); }
    public static void till() { emit("TILL", ""); }
    public static void plant(String crop) { emit("PLANT", crop); }
    public static void water() { emit("WATER", ""); }
    public static void waitCycles(int cycles) { emit("WAIT", Integer.toString(cycles)); }
    public static void harvest() { emit("HARVEST", ""); }
    public static void log(String message) { emit("LOG", message); }
}
