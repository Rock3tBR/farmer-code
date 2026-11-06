package com.javafarmer.save;

import java.util.ArrayList;
import java.util.List;

/**
 * Local save (JSON). Version history:
 * <ul>
 *   <li>v1 (0.1.x): coins, single seed counter, cycles, calendar-based daily challenge.</li>
 *   <li>v2 (0.3.0): ticks/in-game days, farm size, seeds/items per crop, unlocks, missions,
 *       day-scaled challenge, saved program. v1 fields are kept for backward compatibility and migrated on load.</li>
 * </ul>
 */
public final class GameSave {
    public static final int CURRENT_VERSION = 2;

    /** 0/1 for legacy saves (field absent). */
    public int version;

    public long worldSeed = 20261106L;
    public int coins = 25;
    public int points;
    public int playerX;
    public int playerZ;
    public int totalHarvests;
    public int totalPlants;
    public boolean tutorialCompleted;
    public List<TileSnapshot> tiles = new ArrayList<>();

    // ----- v1 legacy fields (read for migration only)
    public int seeds = 12;
    public int cycles;
    public String dailyDate = "";
    public String dailyTitle = "";
    public String dailyKind = "PLANT";
    public int dailyProgress;
    public int dailyTarget = 3;
    public boolean dailyClaimed;

    // ----- v2
    public long ticks;
    public int farmSize = 3;
    /** Indexed by CropType ordinal: WHEAT, CARROT, SUNFLOWER, PUMPKIN. */
    public int[] seedStock;
    public int[] itemStock;
    public String[] unlockedCrops;
    public String[] upgrades;
    public int missionsCompleted;
    public int itemsSold;
    public int coinsEarned;
    public String program;

    public int challengeDay;
    public int challengeTier;
    public String challengeKind;
    public String challengeCrop = "";
    public int challengeAmount;
    public int challengeSize;
    public String challengeFeature;
    public boolean challengeSingleRun;
    public int challengeRewardCoins;
    public int challengeRewardPoints;
    public int challengeProgress;
    public boolean challengeClaimed;
    public int challengesCompleted;

    public static final class TileSnapshot {
        public int x;
        public int z;
        public boolean tilled;
        public String crop = "";
        /** v1 growth stage (0..3); still written for older readers. */
        public int growth;
        public boolean watered;
        /** v2 growth ticks; -1 when absent (legacy save). */
        public int progress = -1;
        public int waterTicks;
    }
}
