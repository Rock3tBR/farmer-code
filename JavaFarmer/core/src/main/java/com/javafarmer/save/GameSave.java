package com.javafarmer.save;

import java.util.ArrayList;
import java.util.List;

public final class GameSave {
    public long worldSeed = 20261106L;
    public int coins = 25;
    public int points;
    public int seeds = 12;
    public int playerX;
    public int playerZ;
    public int cycles;
    public String dailyDate = "";
    public String dailyTitle = "";
    public String dailyKind = "PLANT";
    public int dailyProgress;
    public int dailyTarget = 3;
    public boolean dailyClaimed;
    public int totalHarvests;
    public int totalPlants;
    public boolean tutorialCompleted;
    public List<TileSnapshot> tiles = new ArrayList<>();

    public static final class TileSnapshot {
        public int x;
        public int z;
        public boolean tilled;
        public String crop = "";
        public int growth;
        public boolean watered;
    }
}
