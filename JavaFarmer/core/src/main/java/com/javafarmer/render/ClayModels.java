package com.javafarmer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.utils.Disposable;
import com.javafarmer.world.FarmWorld;

import java.util.ArrayList;
import java.util.List;

import static com.javafarmer.render.ClayPalette.*;

/**
 * Builds every procedural Cozy Clay model once and owns their disposal.
 *
 * <p>Conventions: one world tile is 1x1 unit, the walkable top surface is at y = 0 and every
 * model's "front" faces +X (rotated at runtime by the renderer).</p>
 */
final class ClayModels implements Disposable {
    static final String[] CROPS = {"WHEAT", "CARROT", "SUNFLOWER", "PUMPKIN"};

    private final List<Model> owned = new ArrayList<>();
    private final ClayKit kit = new ClayKit(owned);

    final Model dirt;
    final Model tilled;
    final Model wet;
    final Model[] grass = new Model[3];
    /** Grass of farm land that is not yours yet (paler, with tall tufts). */
    final Model grassWild;
    /** "À venda" sign placed just outside the farm, where the next expansion goes. */
    final Model saleSign;
    final Model water;
    final Model sprout;
    /** [crop index][growth 1..3] (index 0 unused: growth 0 uses {@link #sprout}). */
    final Model[][] crops = new Model[CROPS.length][4];
    final Model tuft;
    final Model flowerPink;
    final Model flowerWhite;
    final Model pebbles;
    final Model cactus;
    final Model tractor;
    final Model drone;
    final Model rotor;
    final Model shadow;
    final Model barn;
    final Model crate;
    final Model chest;
    final Model solarPanel;
    final Model pump;

    ClayModels() {
        dirt = kit.begin().at(0, -0.46f, 0).box(0.96f, 0.46f, 0.96f, 0.12f, DIRT, 0.38f).end();
        tilled = buildTilled(TILLED, TILLED_RIDGE);
        wet = buildTilled(WET, WET_RIDGE);
        for (int i = 0; i < grass.length; i++) grass[i] = buildGrass(i);
        grassWild = buildWildGrass();
        saleSign = buildSaleSign();
        water = buildWater();
        sprout = buildSprout();
        for (int c = 0; c < CROPS.length; c++) {
            for (int g = 1; g <= 3; g++) crops[c][g] = buildCrop(c, g);
        }
        tuft = buildTuft();
        flowerPink = buildFlower(FLOWER_PINK);
        flowerWhite = buildFlower(FLOWER_WHITE);
        pebbles = buildPebbles();
        cactus = buildCactus();
        tractor = buildTractor();
        drone = buildDrone();
        rotor = kit.begin()
                .at(0, 0, 0).box(0.36f, 0.016f, 0.055f, 0.008f, METAL_DARK, 0f)
                .at(0, 0, 0).rotY(90).box(0.36f, 0.016f, 0.055f, 0.008f, METAL_DARK, 0f)
                .end();
        shadow = buildShadow();
        barn = buildBarn();
        crate = buildCrate();
        chest = buildChest();
        solarPanel = buildSolarPanel();
        pump = buildPump();
    }

    static int cropIndex(String crop) {
        if (crop == null) return 0;
        for (int i = 0; i < CROPS.length; i++) if (CROPS[i].equalsIgnoreCase(crop)) return i;
        return 0; // unknown legacy names render as wheat, matching FarmWorld.normalizeCrop
    }

    Model crop(int cropIndex, int growth) {
        if (growth <= 0) return sprout;
        return crops[cropIndex][Math.min(3, growth)];
    }

    Model structure(FarmWorld.Structure structure) {
        return switch (structure) {
            case BARN -> barn;
            case CRATE -> crate;
            case CHEST -> chest;
            case SOLAR_PANEL -> solarPanel;
            case PUMP -> pump;
            case NONE -> null;
        };
    }

    // ------------------------------------------------------------------ terrain

    private Model buildTilled(Color base, Color ridge) {
        kit.begin().at(0, -0.46f, 0).box(0.96f, 0.42f, 0.96f, 0.12f, base, 0.38f);
        for (int i = -1; i <= 1; i++) {
            kit.at(0, -0.09f, i * 0.28f).box(0.82f, 0.13f, 0.2f, 0.065f, ridge, 0.28f);
        }
        return kit.end();
    }

    private Model buildGrass(int variant) {
        kit.begin()
                .at(0, -0.46f, 0).box(0.96f, 0.30f, 0.96f, 0.10f, DIRT, 0.38f)
                .at(0, -0.19f, 0).box(0.98f, 0.21f, 0.98f, 0.09f, GRASS, 0.20f);
        int bumps = variant == 0 ? 0 : variant + 1;
        for (int i = 0; i < bumps; i++) {
            float x = (ClayKit.hash01(variant * 31L + i * 7L) - 0.5f) * 0.56f;
            float z = (ClayKit.hash01(variant * 17L + i * 13L + 5) - 0.5f) * 0.56f;
            float s = 0.22f + ClayKit.hash01(variant * 3L + i) * 0.12f;
            kit.at(x, 0.02f, z).ball(s, s * 0.55f, s, GRASS_LIGHT);
        }
        return kit.end();
    }

    private Model buildWildGrass() {
        kit.begin()
                .at(0, -0.46f, 0).box(0.96f, 0.30f, 0.96f, 0.10f, DIRT, 0.38f)
                .at(0, -0.19f, 0).box(0.98f, 0.21f, 0.98f, 0.09f, GRASS_WILD, 0.20f);
        for (int i = 0; i < 3; i++) {
            float x = (ClayKit.hash01(i * 41L + 3) - 0.5f) * 0.6f;
            float z = (ClayKit.hash01(i * 29L + 9) - 0.5f) * 0.6f;
            for (int b = 0; b < 3; b++) {
                kit.at(x, 0.0f, z).rotY(b * 120f + i * 40f).rotZ(16f).move(0, 0.08f, 0).ball(0.035f, 0.19f, 0.035f, GRASS_WILD_DARK);
            }
        }
        return kit.end();
    }

    private Model buildSaleSign() {
        return kit.begin()
                .at(0, 0.22f, 0).cylinder(0.05f, 0.44f, 0.05f, WOOD_DARK)
                .at(0, 0.34f, 0).rotY(-45).box(0.04f, 0.22f, 0.34f, 0.03f, TRIM, 0.1f)
                .at(0, 0.45f, 0).rotY(-45).move(0.025f, 0, 0).rotZ(-90).cylinder(0.12f, 0.02f, 0.12f, GOLD)
                .end();
    }

    private Model buildWater() {
        kit.begin().at(0, -0.46f, 0).box(0.96f, 0.26f, 0.96f, 0.10f, DIRT, 0.38f);
        Material glossy = new Material(ColorAttribute.createDiffuse(Color.WHITE),
                ColorAttribute.createSpecular(0.55f, 0.6f, 0.65f, 1f), FloatAttribute.createShininess(26f));
        kit.material(glossy).at(0, -0.24f, 0).box(0.96f, 0.17f, 0.96f, 0.08f, WATER, 0.12f);
        return kit.end();
    }

    private Model buildShadow() {
        Material blended = new Material(ColorAttribute.createDiffuse(Color.WHITE),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.16f),
                new DepthTestAttribute(GL20.GL_LEQUAL, false));
        return kit.beginWith(blended)
                .at(0, 0, 0).cylinder(1f, 0.004f, 1f, PUPIL)
                .at(0, 0.002f, 0).cylinder(0.7f, 0.004f, 0.7f, PUPIL)
                .end();
    }

    // ------------------------------------------------------------------ crops

    private Model buildSprout() {
        kit.begin();
        float[][] spots = {{-0.17f, -0.12f}, {0.16f, -0.08f}, {0.0f, 0.17f}};
        for (float[] spot : spots) {
            kit.at(spot[0], 0.07f, spot[1]).cylinder(0.035f, 0.14f, 0.035f, STEM);
            kit.at(spot[0], 0.14f, spot[1]).rotZ(28).move(-0.07f, 0, 0).ball(0.15f, 0.055f, 0.09f, SPROUT);
            kit.at(spot[0], 0.14f, spot[1]).rotZ(-28).move(0.07f, 0, 0).ball(0.15f, 0.055f, 0.09f, SPROUT);
        }
        return kit.end();
    }

    private Model buildCrop(int crop, int growth) {
        kit.begin();
        switch (crop) {
            case 1 -> carrot(growth);
            case 2 -> sunflower(growth);
            case 3 -> pumpkin(growth);
            default -> wheat(growth);
        }
        return kit.end();
    }

    private void wheat(int growth) {
        float height = growth == 1 ? 0.22f : growth == 2 ? 0.36f : 0.48f;
        Color stalk = growth == 1 ? WHEAT_YOUNG : growth == 2 ? WHEAT_MID : WHEAT_RIPE;
        float[][] spots = {{0, 0}, {-0.22f, -0.2f}, {0.2f, -0.22f}, {-0.24f, 0.18f}, {0.22f, 0.2f},
                {0, -0.3f}, {0, 0.3f}, {-0.3f, 0}, {0.3f, 0}};
        for (int i = 0; i < spots.length; i++) {
            float jitterX = (ClayKit.hash01(i * 11L + growth) - 0.5f) * 0.08f;
            float jitterZ = (ClayKit.hash01(i * 23L + growth) - 0.5f) * 0.08f;
            float h = height * (0.82f + ClayKit.hash01(i * 5L) * 0.3f);
            float tiltZ = (ClayKit.hash01(i * 3L + 1) - 0.5f) * 16f;
            float tiltX = (ClayKit.hash01(i * 7L + 2) - 0.5f) * 16f;
            float x = spots[i][0] + jitterX;
            float z = spots[i][1] + jitterZ;
            kit.at(x, 0, z).rotZ(tiltZ).rotX(tiltX).move(0, h * 0.5f, 0).cylinder(0.04f, h, 0.04f, stalk);
            kit.at(x, 0, z).rotZ(tiltZ).rotX(tiltX).move(0, h * 0.28f, 0).rotZ(40).move(0.04f, 0, 0)
                    .ball(0.035f, 0.14f, 0.035f, growth == 3 ? WHEAT_MID : LEAF);
            if (growth >= 2) {
                Color head = growth == 2 ? WHEAT_MID : WHEAT_HEAD;
                kit.at(x, 0, z).rotZ(tiltZ).rotX(tiltX).move(0, h + 0.06f, 0).ball(0.085f, 0.2f, 0.085f, head);
            }
        }
    }

    private void carrot(int growth) {
        float len = growth == 1 ? 0.2f : growth == 2 ? 0.3f : 0.38f;
        float[][] clusters = growth == 1 ? new float[][]{{0, 0}} : new float[][]{{-0.15f, -0.1f}, {0.16f, 0.12f}};
        for (int c = 0; c < clusters.length; c++) {
            float cx = clusters[c][0];
            float cz = clusters[c][1];
            if (growth >= 2) {
                float s = growth == 2 ? 0.13f : 0.19f;
                kit.at(cx, 0.03f, cz).ball(s, s * 0.75f, s, CARROT);
            }
            int leaves = 3 + growth;
            for (int i = 0; i < leaves; i++) {
                float angle = i * (360f / leaves) + c * 25f;
                kit.at(cx, 0.05f, cz).rotY(angle).rotZ(24f).move(0, len * 0.5f, 0)
                        .ball(0.07f, len, 0.05f, i % 2 == 0 ? LEAF : LEAF_DARK);
            }
        }
    }

    private void sunflower(int growth) {
        float h = growth == 1 ? 0.26f : growth == 2 ? 0.48f : 0.62f;
        kit.at(0, h * 0.5f, 0).cylinder(0.065f, h, 0.065f, STEM);
        kit.at(0, h * 0.42f, 0).rotY(45).rotZ(55).move(0, 0.11f, 0).ball(0.09f, 0.24f, 0.15f, LEAF);
        kit.at(0, h * 0.42f, 0).rotY(225).rotZ(55).move(0, 0.11f, 0).ball(0.09f, 0.24f, 0.15f, LEAF_DARK);
        if (growth == 1) {
            kit.at(0, h + 0.03f, 0).ball(0.1f, 0.1f, 0.1f, SPROUT);
            return;
        }
        if (growth == 2) {
            kit.at(0, h + 0.05f, 0).ball(0.17f, 0.15f, 0.17f, WHEAT_YOUNG);
            for (int i = 0; i < 6; i++) {
                kit.at(0, h + 0.05f, 0).rotY(i * 60f).move(0.07f, 0.03f, 0).ball(0.06f, 0.08f, 0.06f, SUNFLOWER_PETAL);
            }
            return;
        }
        // Mature head faces the isometric camera (+X+Z) and tilts slightly upwards.
        for (int i = 0; i < 12; i++) {
            kit.at(0, h + 0.04f, 0).rotY(-45).rotZ(25).rotX(i * 30f).move(-0.01f, 0.17f, 0)
                    .ball(0.05f, 0.15f, 0.085f, SUNFLOWER_PETAL);
        }
        kit.at(0, h + 0.04f, 0).rotY(-45).rotZ(25).rotZ(-90).cylinder(0.23f, 0.07f, 0.23f, SUNFLOWER_CENTER);
    }

    private void pumpkin(int growth) {
        float leafScale = growth == 1 ? 0.75f : 1f;
        float[] leafAngles = {120f, 200f, 280f};
        for (int i = 0; i < leafAngles.length; i++) {
            kit.at(0, 0.04f, 0).rotY(leafAngles[i]).move(0.22f * leafScale, 0, 0).rotZ(-12)
                    .ball(0.34f * leafScale, 0.06f, 0.24f * leafScale, i % 2 == 0 ? LEAF : LEAF_DARK);
        }
        if (growth == 1) {
            kit.at(0.05f, 0.1f, 0.05f).ball(0.09f, 0.09f, 0.09f, SUNFLOWER_PETAL);
            return;
        }
        float k = growth == 2 ? 0.55f : 1f;
        float cx = 0.04f;
        float cz = 0.05f;
        float cy = 0.15f * k + 0.01f;
        for (int i = 0; i < 5; i++) {
            kit.at(cx, cy, cz).rotY(i * 36f).ball(0.42f * k, 0.3f * k, 0.18f * k, i % 2 == 0 ? PUMPKIN : PUMPKIN_DARK);
        }
        kit.at(cx, cy + 0.15f * k + 0.03f, cz).rotZ(15).cylinder(0.05f, 0.1f, 0.05f, STEM);
    }

    // ------------------------------------------------------------------ decorations

    private Model buildTuft() {
        kit.begin();
        for (int i = 0; i < 5; i++) {
            float angle = i * 72f;
            kit.at(0, 0.0f, 0).rotY(angle).rotZ(18f + i * 3f).move(0, 0.07f, 0).ball(0.035f, 0.17f, 0.035f, GRASS_DARK);
        }
        return kit.end();
    }

    private Model buildFlower(Color petal) {
        kit.begin();
        float[][] flowers = {{0, 0, 1f}, {0.12f, 0.08f, 0.75f}};
        for (float[] f : flowers) {
            float x = f[0];
            float z = f[1];
            float s = f[2];
            float h = 0.17f * s;
            kit.at(x, h * 0.5f, z).cylinder(0.025f, h, 0.025f, STEM);
            kit.at(x, 0.04f, z).rotZ(40).move(0.04f, 0, 0).ball(0.09f * s, 0.035f, 0.05f * s, LEAF);
            for (int i = 0; i < 5; i++) {
                kit.at(x, h, z).rotY(i * 72f).move(0.045f * s, 0, 0).ball(0.075f * s, 0.035f, 0.065f * s, petal);
            }
            kit.at(x, h + 0.01f, z).ball(0.05f * s, 0.045f * s, 0.05f * s, FLOWER_CENTER);
        }
        return kit.end();
    }

    private Model buildPebbles() {
        return kit.begin()
                .at(0, 0.02f, 0).ball(0.15f, 0.08f, 0.12f, PEBBLE)
                .at(0.13f, 0.015f, 0.07f).ball(0.1f, 0.06f, 0.09f, PEBBLE)
                .at(-0.06f, 0.012f, 0.13f).ball(0.07f, 0.045f, 0.07f, METAL)
                .end();
    }

    private Model buildCactus() {
        return kit.begin()
                .at(0, 0.24f, 0).ball(0.24f, 0.5f, 0.24f, CACTUS)
                .at(0.13f, 0.24f, 0).rotZ(-60).ball(0.08f, 0.16f, 0.08f, CACTUS)
                .at(0.19f, 0.32f, 0).ball(0.1f, 0.2f, 0.1f, CACTUS)
                .at(-0.12f, 0.2f, 0.02f).rotZ(60).ball(0.07f, 0.13f, 0.07f, CACTUS)
                .at(-0.17f, 0.26f, 0.02f).ball(0.08f, 0.15f, 0.08f, CACTUS)
                .at(0, 0.5f, 0).ball(0.09f, 0.07f, 0.09f, FLOWER_PINK)
                .end();
    }

    // ------------------------------------------------------------------ robot & drone

    private Model buildTractor() {
        kit.begin();
        for (int side = -1; side <= 1; side += 2) {
            float z = side * 0.25f;
            kit.at(0, 0, z).box(0.8f, 0.24f, 0.2f, 0.1f, TRACK, 0.2f);
            for (int w = -1; w <= 1; w++) {
                kit.at(w * 0.25f, 0.12f, z + side * 0.1f).rotX(90).cylinder(0.15f, 0.03f, 0.15f, METAL);
            }
        }
        kit.at(0.02f, 0.18f, 0).box(0.62f, 0.3f, 0.44f, 0.12f, TRACTOR, 0.22f);
        kit.at(-0.13f, 0.42f, 0).box(0.36f, 0.28f, 0.4f, 0.1f, TRACTOR_LIGHT, 0.15f);
        kit.at(0.05f, 0.5f, 0).box(0.03f, 0.14f, 0.3f, 0.015f, GLASS, 0f);
        kit.at(-0.13f, 0.5f, 0.195f).box(0.24f, 0.14f, 0.03f, 0.015f, GLASS, 0f);
        kit.at(-0.13f, 0.5f, -0.195f).box(0.24f, 0.14f, 0.03f, 0.015f, GLASS, 0f);
        kit.at(-0.13f, 0.69f, 0).box(0.42f, 0.06f, 0.46f, 0.03f, TRACTOR_DARK, 0.1f);
        kit.at(0.16f, 0.56f, -0.13f).cylinder(0.06f, 0.18f, 0.06f, METAL_DARK);
        for (int side = -1; side <= 1; side += 2) {
            float z = side * 0.11f;
            kit.at(0.31f, 0.34f, z).ball(0.1f, 0.17f, 0.15f, EYE_WHITE);
            kit.at(0.355f, 0.335f, z).ball(0.05f, 0.09f, 0.08f, PUPIL);
            kit.at(0.378f, 0.365f, z - 0.02f).ball(0.02f, 0.03f, 0.03f, EYE_WHITE);
            kit.at(0.38f, 0.15f, side * 0.13f).box(0.16f, 0.05f, 0.05f, 0.02f, METAL_DARK, 0.1f);
            kit.at(0.46f, 0.09f, side * 0.13f).box(0.05f, 0.12f, 0.05f, 0.02f, METAL, 0.1f);
        }
        return kit.end();
    }

    private Model buildDrone() {
        return kit.begin()
                .at(0, 0, 0).ball(0.36f, 0.32f, 0.36f, DRONE)
                .at(0, 0, 0).cylinder(0.375f, 0.07f, 0.375f, DRONE_BAND)
                .at(0.17f, 0, 0).ball(0.06f, 0.13f, 0.13f, PUPIL)
                .at(0.195f, 0.03f, -0.03f).ball(0.015f, 0.03f, 0.03f, EYE_WHITE)
                .at(0, 0.05f, 0).box(0.06f, 0.04f, 0.66f, 0.02f, METAL_DARK, 0f)
                .at(0, 0.1f, 0.3f).cylinder(0.07f, 0.06f, 0.07f, METAL_DARK)
                .at(0, 0.1f, -0.3f).cylinder(0.07f, 0.06f, 0.07f, METAL_DARK)
                .end();
    }

    // ------------------------------------------------------------------ props

    private Model buildBarn() {
        float top = 0.5f;
        float gable = 0.49f;
        float halfDiagonal = gable * 0.7071f;
        float ridge = top + halfDiagonal + 0.05f;
        float slab = 0.58f;
        kit.begin()
                .at(0, 0, 0).box(0.8f, top, 0.7f, 0.06f, BARN, 0.25f)
                .at(0, top, 0).rotX(45).move(0, -gable * 0.5f, 0).box(0.78f, gable, gable, 0.03f, BARN, 0.05f)
                .at(0, ridge, 0).rotX(45).move(0, -0.06f, slab * 0.5f).box(0.92f, 0.06f, slab, 0.03f, ROOF, 0.15f)
                .at(0, ridge, 0).rotX(-45).move(0, -0.06f, -slab * 0.5f).box(0.92f, 0.06f, slab, 0.03f, ROOF, 0.15f)
                .at(0, ridge - 0.01f, 0).rotZ(90).cylinder(0.08f, 0.94f, 0.08f, ROOF_DARK)
                // door on the +X gable end
                .at(0.4f, 0, 0).box(0.03f, 0.37f, 0.32f, 0.02f, TRIM, 0f)
                .at(0.413f, 0.025f, 0).box(0.02f, 0.32f, 0.26f, 0.015f, TRACTOR_DARK, 0f)
                .at(0.428f, 0.185f, 0).rotX(40).move(0, -0.17f, 0).box(0.015f, 0.34f, 0.035f, 0.01f, TRIM, 0f)
                .at(0.428f, 0.185f, 0).rotX(-40).move(0, -0.17f, 0).box(0.015f, 0.34f, 0.035f, 0.01f, TRIM, 0f)
                .at(0.405f, 0.56f, 0).box(0.02f, 0.13f, 0.13f, 0.035f, TRIM, 0f)
                // corner trims on the visible +Z side
                .at(0.38f, 0, 0.34f).box(0.05f, top, 0.03f, 0.015f, TRIM, 0f)
                .at(-0.38f, 0, 0.34f).box(0.05f, top, 0.03f, 0.015f, TRIM, 0f);
        return kit.end();
    }

    private Model buildCrate() {
        kit.begin().at(0, 0, 0).box(0.52f, 0.42f, 0.52f, 0.05f, WOOD, 0.25f);
        for (float y : new float[]{0.08f, 0.27f}) {
            kit.at(0.262f, y, 0).box(0.02f, 0.06f, 0.46f, 0.01f, WOOD_DARK, 0f);
            kit.at(0, y, 0.262f).box(0.46f, 0.06f, 0.02f, 0.01f, WOOD_DARK, 0f);
        }
        kit.at(0, 0.4f, 0).ball(0.44f, 0.1f, 0.44f, DIRT_DARK);
        for (int i = 0; i < 6; i++) {
            float x = (ClayKit.hash01(i * 9L) - 0.5f) * 0.3f;
            float z = (ClayKit.hash01(i * 19L + 4) - 0.5f) * 0.3f;
            kit.at(x, 0.445f, z).ball(0.05f, 0.03f, 0.04f, ROOF);
        }
        return kit.end();
    }

    private Model buildChest() {
        return kit.begin()
                .at(0, 0, 0).box(0.58f, 0.3f, 0.42f, 0.06f, GOLD, 0.25f)
                .at(0, 0.27f, 0).box(0.6f, 0.17f, 0.44f, 0.08f, ROOF, 0.12f)
                .at(-0.18f, 0, 0.205f).box(0.06f, 0.44f, 0.03f, 0.015f, WOOD_DARK, 0f)
                .at(0.18f, 0, 0.205f).box(0.06f, 0.44f, 0.03f, 0.015f, WOOD_DARK, 0f)
                .at(0.285f, 0, 0).box(0.03f, 0.44f, 0.06f, 0.015f, WOOD_DARK, 0f)
                .at(0, 0.2f, 0.225f).box(0.08f, 0.1f, 0.03f, 0.02f, METAL, 0f)
                .end();
    }

    private Model buildSolarPanel() {
        kit.begin()
                .at(0, 0, 0).box(0.3f, 0.06f, 0.3f, 0.03f, METAL, 0.2f)
                .at(0, 0.21f, 0).cylinder(0.07f, 0.34f, 0.07f, METAL_DARK)
                .at(0, 0.36f, 0).rotX(28).box(0.76f, 0.04f, 0.54f, 0.02f, METAL, 0f);
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz += 2) {
                kit.at(0, 0.36f, 0).rotX(28).move(cx * 0.24f, 0.04f, cz * 0.125f)
                        .box(0.22f, 0.014f, 0.23f, 0.008f, SOLAR, 0f);
            }
        }
        return kit.end();
    }

    private Model buildPump() {
        return kit.begin()
                .at(0, 0, 0).box(0.46f, 0.12f, 0.46f, 0.04f, WOOD, 0.2f)
                .at(0, 0.36f, 0).cylinder(0.17f, 0.48f, 0.17f, PUMP)
                .at(0, 0.62f, 0).ball(0.22f, 0.1f, 0.22f, PUMP)
                .at(0.19f, 0.46f, 0).rotZ(-90).cylinder(0.07f, 0.22f, 0.07f, PUMP)
                .at(0.3f, 0.43f, 0).cylinder(0.08f, 0.08f, 0.08f, METAL_DARK)
                .at(-0.13f, 0.64f, 0).rotZ(-20).box(0.3f, 0.035f, 0.04f, 0.015f, METAL_DARK, 0f)
                .at(-0.27f, 0.7f, 0).ball(0.065f, 0.065f, 0.065f, WOOD_DARK)
                .at(0.24f, 0.12f, 0.12f).box(0.16f, 0.12f, 0.16f, 0.04f, METAL, 0.2f)
                .at(0.24f, 0.235f, 0.12f).ball(0.12f, 0.02f, 0.12f, WATER)
                .end();
    }

    @Override
    public void dispose() {
        for (Model model : owned) model.dispose();
        owned.clear();
    }
}
