package com.javafarmer.world;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The complete, rule-bearing farm: world + inventory + economy.
 *
 * <p>This class is pure Java on purpose. The game process uses it as the authoritative state, and the
 * child JVM that runs the player's program rebuilds an identical copy from {@link #snapshot()} so that
 * sensors such as {@code canHarvest()} see exactly what the game will see when the actions are replayed.</p>
 */
public final class Farm {
    public enum Upgrade {
        REGADOR("Regador", 60, "Libera FarmerBot.water(): cultivos molhados crescem 2x mais rápido."),
        MERCADO("Contrato com o mercado", 250, "Todos os produtos vendem 25% mais caro.");

        public final String label;
        public final int price;
        public final String description;

        Upgrade(String label, int price, String description) {
            this.label = label;
            this.price = price;
            this.description = description;
        }

        public static Upgrade parse(String name) {
            for (Upgrade upgrade : values()) if (upgrade.name().equalsIgnoreCase(name)) return upgrade;
            return null;
        }
    }

    /** Result of one action: success flag, a friendly message and (for harvest/sell) the crop involved. */
    public record Result(boolean ok, String message, CropType crop, int coins) {
        static Result ok(String message) { return new Result(true, message, null, 0); }
        static Result fail(String message) { return new Result(false, message, null, 0); }
    }

    private static final int[] LAND_PRICES = {0, 0, 0, 0, 40, 100, 180, 300, 450, 650, 900, 1200, 1600};

    public final FarmWorld world;
    public int coins = 25;
    public final int[] seeds = new int[CropType.values().length];
    public final int[] items = new int[CropType.values().length];
    public final Set<CropType> unlocked = EnumSet.of(CropType.WHEAT);
    public final Set<Upgrade> upgrades = EnumSet.noneOf(Upgrade.class);

    public Farm(long seed) {
        world = new FarmWorld(seed);
        seeds[CropType.WHEAT.ordinal()] = 10;
    }

    // ------------------------------------------------------------------ program actions

    /** Applies one FarmerBot action. The same code runs in the game and in the player's child JVM. */
    public Result apply(String type, String value) {
        return switch (type) {
            case "MOVE" -> world.move(value)
                    ? Result.ok("Robô andou para " + direction(value) + ".")
                    : Result.fail("Caminho bloqueado para " + direction(value) + " (água, construção ou direção inválida).");
            case "TILL" -> world.till()
                    ? Result.ok("Solo preparado.")
                    : Result.fail(world.isOwned(world.playerX(), world.playerZ())
                        ? "Não dá para arar aqui: já existe um cultivo." : "Este terreno não é seu. Compre mais terra na loja.");
            case "PLANT" -> plant(value);
            case "WATER" -> {
                if (!upgrades.contains(Upgrade.REGADOR)) {
                    world.advance(1);
                    yield Result.fail("Você ainda não tem o Regador. Compre-o na loja.");
                }
                yield world.water() ? Result.ok("Cultivo regado: cresce 2x mais rápido por um tempo.")
                        : Result.fail("Não há cultivo neste bloco para regar.");
            }
            case "WAIT" -> {
                int amount;
                try { amount = Integer.parseInt(value.trim()); } catch (NumberFormatException ex) { amount = 1; }
                int grown = world.waitCycles(amount);
                yield Result.ok("O tempo passou. " + grown + " cultivo(s) amadureceram.");
            }
            case "HARVEST" -> {
                CropType crop = world.harvestCrop();
                if (crop == null) yield Result.fail("Nada pronto para colher aqui.");
                items[crop.ordinal()]++;
                yield new Result(true, "Colheu 1 " + crop.label + ".", crop, 0);
            }
            case "SELL" -> sellFromProgram(value);
            case "LOG" -> Result.ok(value);
            default -> Result.fail("Ação desconhecida: " + type);
        };
    }

    private Result plant(String value) {
        CropType crop = CropType.parse(value);
        if (crop == null) {
            world.advance(1);
            return Result.fail("Cultivo desconhecido: \"" + value + "\". Use WHEAT, CARROT, SUNFLOWER ou PUMPKIN.");
        }
        if (!unlocked.contains(crop)) {
            world.advance(1);
            return Result.fail(crop.label + " ainda está bloqueado. Avance nas missões.");
        }
        if (seeds[crop.ordinal()] <= 0) {
            world.advance(1);
            return Result.fail("Sem sementes de " + crop.label + ". Compre na loja.");
        }
        if (!world.plant(crop.name())) {
            return Result.fail(world.isOwned(world.playerX(), world.playerZ())
                    ? "Não dá para plantar aqui: o bloco já tem um cultivo." : "Este terreno não é seu. Compre mais terra na loja.");
        }
        seeds[crop.ordinal()]--;
        return new Result(true, crop.label + " plantado.", crop, 0);
    }

    /** value = "CROP" (sell all) or "CROP:amount". */
    private Result sellFromProgram(String value) {
        world.advance(1);
        String[] parts = value.split(":", 2);
        CropType crop = CropType.parse(parts[0]);
        if (crop == null) return Result.fail("Produto desconhecido: " + parts[0]);
        int amount = items[crop.ordinal()];
        if (parts.length == 2) {
            try { amount = Math.min(amount, Integer.parseInt(parts[1].trim())); } catch (NumberFormatException ignored) { }
        }
        int earned = sell(crop, amount);
        if (earned <= 0) return Result.fail("Nada de " + crop.label + " para vender.");
        return new Result(true, "Vendeu " + crop.label + " por " + earned + " moedas.", crop, earned);
    }

    // ------------------------------------------------------------------ shop (called by the game UI)

    public int sellPrice(CropType crop) {
        return upgrades.contains(Upgrade.MERCADO) ? (crop.sellPrice * 5 + 3) / 4 : crop.sellPrice;
    }

    /** Sells up to {@code amount} units. Returns coins earned. */
    public int sell(CropType crop, int amount) {
        int n = Math.max(0, Math.min(amount, items[crop.ordinal()]));
        if (n == 0) return 0;
        items[crop.ordinal()] -= n;
        int earned = n * sellPrice(crop);
        coins += earned;
        return earned;
    }

    public boolean buySeeds(CropType crop, int amount) {
        int cost = crop.seedPrice * amount;
        if (amount <= 0 || !unlocked.contains(crop) || coins < cost) return false;
        coins -= cost;
        seeds[crop.ordinal()] += amount;
        return true;
    }

    /** Price of the next land expansion, or -1 when the farm is already at maximum size. */
    public int nextLandPrice() {
        int next = world.farmSize() + 1;
        return next > FarmWorld.MAX_FARM_SIZE ? -1 : LAND_PRICES[next];
    }

    public boolean buyLand() {
        int price = nextLandPrice();
        if (price < 0 || coins < price) return false;
        coins -= price;
        world.setFarmSize(world.farmSize() + 1);
        return true;
    }

    public boolean buyUpgrade(Upgrade upgrade) {
        if (upgrades.contains(upgrade) || coins < upgrade.price) return false;
        coins -= upgrade.price;
        upgrades.add(upgrade);
        return true;
    }

    public int totalSeeds() { int t = 0; for (int s : seeds) t += s; return t; }
    public int totalItems() { int t = 0; for (int s : items) t += s; return t; }

    // ------------------------------------------------------------------ sensors (no time cost)

    public int getX() { return world.farmX(world.playerX()); }
    public int getY() { return world.farmY(world.playerZ()); }

    public boolean canHarvest() {
        return world.tileAt(world.playerX(), world.playerZ()).isReady();
    }

    public String getCrop() {
        FarmTile tile = world.tileAt(world.playerX(), world.playerZ());
        return tile.hasCrop() ? tile.crop : "";
    }

    public String getGround() {
        FarmTile tile = world.tileAt(world.playerX(), world.playerZ());
        if (tile.terrain == FarmTile.Terrain.WATER) return "WATER";
        if (tile.tilled) return "TILLED";
        return tile.terrain.name();
    }

    public boolean isWatered() {
        return world.tileAt(world.playerX(), world.playerZ()).watered;
    }

    public boolean isOwnedHere() {
        return world.isOwned(world.playerX(), world.playerZ());
    }

    public int getSeeds(String crop) {
        CropType type = CropType.parse(crop);
        return type == null ? 0 : seeds[type.ordinal()];
    }

    public int getItems(String crop) {
        CropType type = CropType.parse(crop);
        return type == null ? 0 : items[type.ordinal()];
    }

    // ------------------------------------------------------------------ snapshot (game -> child JVM)

    /** Line-based text snapshot with everything the rules and sensors need. */
    public String snapshot() {
        StringBuilder out = new StringBuilder(256);
        out.append("v=1\n");
        out.append("seed=").append(world.seed()).append('\n');
        out.append("pos=").append(world.playerX()).append(',').append(world.playerZ()).append('\n');
        out.append("ticks=").append(world.ticks()).append('\n');
        out.append("size=").append(world.farmSize()).append('\n');
        out.append("coins=").append(coins).append('\n');
        out.append("seeds=").append(join(seeds)).append('\n');
        out.append("items=").append(join(items)).append('\n');
        out.append("unlocked=").append(String.join(",", names(unlocked))).append('\n');
        out.append("upgrades=").append(String.join(",", names(upgrades))).append('\n');
        for (FarmTile tile : world.getTiles().values()) {
            if (!tile.tilled && !tile.hasCrop()) continue;
            out.append("tile=").append(tile.x).append(',').append(tile.z).append(',').append(tile.tilled ? 1 : 0)
                    .append(',').append(tile.crop == null ? "" : tile.crop).append(',').append(tile.progress)
                    .append(',').append(tile.waterTicks).append('\n');
        }
        return out.toString();
    }

    public static Farm fromSnapshot(String text) {
        long seed = 0;
        for (String line : text.split("\n")) if (line.startsWith("seed=")) seed = parseLong(line.substring(5), 0);
        Farm farm = new Farm(seed);
        farm.seeds[CropType.WHEAT.ordinal()] = 0;
        int px = 0, pz = 0;
        long ticks = 0;
        List<String> tileLines = new ArrayList<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq);
            String value = line.substring(eq + 1).trim();
            switch (key) {
                case "pos" -> { String[] p = value.split(","); px = (int) parseLong(p[0], 0); pz = (int) parseLong(p.length > 1 ? p[1] : "0", 0); }
                case "ticks" -> ticks = parseLong(value, 0);
                case "size" -> farm.world.setFarmSize((int) parseLong(value, FarmWorld.MIN_FARM_SIZE));
                case "coins" -> farm.coins = (int) parseLong(value, 0);
                case "seeds" -> split(value, farm.seeds);
                case "items" -> split(value, farm.items);
                case "unlocked" -> {
                    farm.unlocked.clear();
                    for (String name : value.split(",")) { CropType c = CropType.parse(name); if (c != null) farm.unlocked.add(c); }
                    farm.unlocked.add(CropType.WHEAT);
                }
                case "upgrades" -> { for (String name : value.split(",")) { Upgrade u = Upgrade.parse(name); if (u != null) farm.upgrades.add(u); } }
                case "tile" -> tileLines.add(value);
                default -> { }
            }
        }
        farm.world.restorePlayer(px, pz, ticks);
        for (String value : tileLines) {
            String[] p = value.split(",", -1);
            if (p.length < 6) continue;
            farm.world.restoreTile((int) parseLong(p[0], 0), (int) parseLong(p[1], 0), "1".equals(p[2]), p[3],
                    (int) parseLong(p[4], 0), (int) parseLong(p[5], 0));
        }
        return farm;
    }

    private static String direction(String value) {
        return switch (value == null ? "" : value.toUpperCase(Locale.ROOT)) {
            case "NORTH", "N", "NORTE" -> "o norte";
            case "SOUTH", "S", "SUL" -> "o sul";
            case "EAST", "E", "LESTE" -> "o leste";
            case "WEST", "W", "OESTE" -> "o oeste";
            default -> "\"" + value + "\"";
        };
    }

    private static String join(int[] values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) { if (i > 0) out.append(','); out.append(values[i]); }
        return out.toString();
    }

    private static void split(String value, int[] target) {
        String[] parts = value.split(",");
        for (int i = 0; i < target.length && i < parts.length; i++) target[i] = (int) Math.max(0, parseLong(parts[i], 0));
    }

    private static <E extends Enum<E>> List<String> names(Set<E> set) {
        Set<String> out = new LinkedHashSet<>();
        for (E e : set) out.add(e.name());
        return new ArrayList<>(out);
    }

    private static long parseLong(String text, long fallback) {
        try { return Long.parseLong(text.trim()); } catch (RuntimeException ex) { return fallback; }
    }
}
