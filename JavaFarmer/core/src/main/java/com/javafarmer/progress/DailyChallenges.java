package com.javafarmer.progress;

import com.javafarmer.runtime.ProgramFeature;
import com.javafarmer.world.CropType;
import com.javafarmer.world.Farm;
import com.javafarmer.world.FarmTile;
import com.javafarmer.world.FarmWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates the "desafio do dia". Difficulty grows with the in-game day:
 * <ol>
 *   <li>days 1-2: simple totals (plant / harvest a few wheat);</li>
 *   <li>days 3-5: one program + for;</li>
 *   <li>days 6-9: one program + if / while / water;</li>
 *   <li>days 10-14: fill an area with nested loops, earn coins;</li>
 *   <li>days 15-20: checkerboards with %, big harvests with your own methods;</li>
 *   <li>day 21+: whole-farm automation with arrays and methods, amounts keep growing.</li>
 * </ol>
 * Only challenges that are possible with the player's current crops, upgrades and land are offered.
 */
public final class DailyChallenges {
    private DailyChallenges() {}

    public enum Kind { PLANT, HARVEST, WATER, EARN, FILL, CHECKER }

    /**
     * @param crop       crop id for PLANT/HARVEST/FILL ("" = any crop), or "A/B" for CHECKER
     * @param amount     target count (1 for FILL/CHECKER)
     * @param size       side of the area for FILL/CHECKER (starting at farm corner 0,0)
     * @param feature    Java concept the program must use (null = none)
     * @param singleRun  true when the goal must be reached by one program
     */
    public record Challenge(int day, int tier, Kind kind, String crop, int amount, int size,
                            ProgramFeature feature, boolean singleRun, int rewardCoins, int rewardPoints) { }

    public static int tierForDay(int day) {
        if (day <= 2) return 1;
        if (day <= 5) return 2;
        if (day <= 9) return 3;
        if (day <= 14) return 4;
        if (day <= 20) return 5;
        return 6;
    }

    private static int tierStart(int tier) {
        return switch (tier) { case 1 -> 1; case 2 -> 3; case 3 -> 6; case 4 -> 10; case 5 -> 15; default -> 21; };
    }

    public static Challenge generate(int day, Farm farm) {
        int tier = tierForDay(day);
        int step = Math.max(0, day - tierStart(tier)); // grows inside a tier
        Random random = new Random(farm.world.seed() * 31L + day * 7919L);
        List<CropType> crops = new ArrayList<>(farm.unlocked);
        CropType crop = crops.get(random.nextInt(crops.size()));
        int size = farm.world.farmSize();
        boolean regador = farm.upgrades.contains(Farm.Upgrade.REGADOR);
        List<Challenge> options = new ArrayList<>();

        switch (tier) {
            case 1 -> {
                options.add(make(day, tier, Kind.PLANT, "WHEAT", 2 + step, 0, null, false));
                options.add(make(day, tier, Kind.HARVEST, "WHEAT", 2 + step, 0, null, false));
            }
            case 2 -> {
                options.add(make(day, tier, Kind.PLANT, crop.name(), Math.min(size * size, 3 + step), 0, ProgramFeature.FOR, true));
                options.add(make(day, tier, Kind.HARVEST, "", 3 + step, 0, null, false));
            }
            case 3 -> {
                options.add(make(day, tier, Kind.HARVEST, crop.name(), Math.min(size * size, 3 + step), 0, ProgramFeature.IF, true));
                options.add(make(day, tier, Kind.PLANT, "", Math.min(size * size, 5 + step), 0, ProgramFeature.WHILE, true));
                if (regador) options.add(make(day, tier, Kind.WATER, "", Math.min(size * size, 4 + step), 0, null, true));
            }
            case 4 -> {
                options.add(make(day, tier, Kind.FILL, crop.name(), 1, Math.min(size, 3), ProgramFeature.NESTED_LOOP, true));
                options.add(withAmount(make(day, tier, Kind.EARN, "", 1, 0, null, false), 60 + day * 6));
                options.add(make(day, tier, Kind.HARVEST, "", Math.min(size * size, 6 + step), 0, ProgramFeature.VARIABLE, true));
            }
            case 5 -> {
                if (crops.size() >= 2) {
                    CropType other = crops.get((crops.indexOf(crop) + 1) % crops.size());
                    options.add(make(day, tier, Kind.CHECKER, crop.name() + "/" + other.name(), 1, Math.min(size, 4), ProgramFeature.MODULO, true));
                }
                options.add(make(day, tier, Kind.HARVEST, "", Math.min(size * size, 9 + step), 0, ProgramFeature.METHOD, true));
                options.add(make(day, tier, Kind.FILL, crop.name(), 1, Math.min(size, 4), ProgramFeature.METHOD, true));
            }
            default -> {
                int extra = Math.min(30, step);
                options.add(make(day, tier, Kind.HARVEST, "", Math.min(size * size, 14 + extra), 0,
                        step % 2 == 0 ? ProgramFeature.ARRAY : ProgramFeature.METHOD, true));
                if (crops.size() >= 2) {
                    CropType other = crops.get((crops.indexOf(crop) + 1) % crops.size());
                    options.add(make(day, tier, Kind.CHECKER, crop.name() + "/" + other.name(), 1, Math.min(size, 8), ProgramFeature.MODULO, true));
                }
                options.add(make(day, tier, Kind.FILL, crop.name(), 1, Math.min(size, 8), ProgramFeature.RETURN_VALUE, true));
                options.add(withAmount(make(day, tier, Kind.EARN, "", 1, 0, null, false), 200 + day * 15));
            }
        }
        return options.get(random.nextInt(options.size()));
    }

    private static Challenge make(int day, int tier, Kind kind, String crop, int amount, int size, ProgramFeature feature, boolean single) {
        int reward = 12 * tier + amount * 2 + size * size * 2;
        return new Challenge(day, tier, kind, crop, Math.max(1, amount), size, feature, single, reward, 40 * tier);
    }

    private static Challenge withAmount(Challenge c, int amount) {
        return new Challenge(c.day(), c.tier(), c.kind(), c.crop(), amount, c.size(), c.feature(), c.singleRun(),
                12 * c.tier() + amount / 4, c.rewardPoints());
    }

    /** Human readable goal, e.g. "Em um programa com laço for, plante 4 Cenoura". */
    public static String describe(Challenge c) {
        String crop = cropLabel(c.crop());
        String prefix = c.singleRun()
                ? "Em um único programa" + (c.feature() != null ? " usando " + c.feature().label : "") + ", "
                : "Hoje, ";
        String goal = switch (c.kind()) {
            case PLANT -> "plante " + c.amount() + " " + crop;
            case HARVEST -> "colha " + c.amount() + " " + crop;
            case WATER -> "regue " + c.amount() + " cultivos";
            case EARN -> "ganhe " + c.amount() + " moedas com vendas";
            case FILL -> "deixe a área " + c.size() + "x" + c.size() + " a partir de (0,0) toda plantada com " + crop;
            case CHECKER -> "faça um xadrez " + c.size() + "x" + c.size() + " a partir de (0,0) com " + crop.replace("/", " e ");
        };
        return prefix + goal + ".";
    }

    private static String cropLabel(String crop) {
        if (crop == null || crop.isEmpty()) return "cultivos (qualquer tipo)";
        if (crop.contains("/")) {
            String[] parts = crop.split("/");
            return label(parts[0]) + "/" + label(parts[1]);
        }
        return label(crop);
    }

    private static String label(String id) {
        CropType type = CropType.parse(id);
        return type == null ? id : type.label;
    }

    /**
     * Updates progress after a program run (report != null) or a shop sale (report == null, coins = earned).
     * Returns the new progress value.
     */
    public static int progress(Challenge c, int current, RunReport report, int shopCoins, Farm farm) {
        if (c.singleRun()) {
            if (report == null || !report.has(c.feature())) return current;
            int value = switch (c.kind()) {
                case PLANT -> count(report.planted, c.crop());
                case HARVEST -> count(report.harvested, c.crop());
                case WATER -> report.waters;
                case EARN -> report.coinsEarned;
                case FILL -> filled(farm.world, c.size(), c.crop(), null) ? 1 : 0;
                case CHECKER -> {
                    String[] two = c.crop().split("/");
                    yield filled(farm.world, c.size(), two[0], two[1]) ? 1 : 0;
                }
            };
            return Math.max(current, value);
        }
        int delta = switch (c.kind()) {
            case PLANT -> report == null ? 0 : count(report.planted, c.crop());
            case HARVEST -> report == null ? 0 : count(report.harvested, c.crop());
            case WATER -> report == null ? 0 : report.waters;
            case EARN -> (report == null ? 0 : report.coinsEarned) + shopCoins;
            default -> 0;
        };
        return Math.min(c.amount(), current + delta);
    }

    private static int count(int[] perCrop, String crop) {
        CropType type = CropType.parse(crop);
        if (type != null) return perCrop[type.ordinal()];
        int total = 0;
        for (int v : perCrop) total += v;
        return total;
    }

    /** FILL (second == null) or CHECKER check on the k x k area that starts at the farm corner (0,0). */
    static boolean filled(FarmWorld world, int k, String first, String second) {
        if (k <= 0) return false;
        for (int fy = 0; fy < k; fy++) {
            for (int fx = 0; fx < k; fx++) {
                FarmTile tile = world.tileAt(fx, -fy);
                String expected = second == null || (fx + fy) % 2 == 0 ? first : second;
                if (!tile.hasCrop() || !tile.crop.equalsIgnoreCase(expected)) return false;
            }
        }
        return true;
    }
}
