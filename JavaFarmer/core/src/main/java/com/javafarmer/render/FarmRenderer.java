package com.javafarmer.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.LongMap;
import com.javafarmer.world.FarmTile;
import com.javafarmer.world.FarmWorld;

/**
 * Renders the farm in the Cozy Clay style: isometric orthographic camera, soft warm lighting,
 * rounded procedural blocks, animated robot-tractor and its companion drone.
 *
 * <p>Owns the {@link ModelBatch} and the {@link ClayModels}; dispose it with the game.</p>
 */
public final class FarmRenderer implements Disposable {
    private static final float CAMERA_OFFSET_XZ = 10f;
    private static final float CAMERA_OFFSET_Y = 12f;
    /** Crops are modelled at 1:1 and drawn slightly larger so they read well at isometric zoom. */
    private static final float CROP_SCALE = 1.3f;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final ModelBatch batch = new ModelBatch();
    private final Environment environment = new Environment();
    private final ClayModels models = new ClayModels();
    private final LongMap<TileVisual> visuals = new LongMap<>();

    private final ModelInstance tractor;
    private final ModelInstance drone;
    private final ModelInstance rotorA;
    private final ModelInstance rotorB;
    private final ModelInstance tractorShadow;
    private final ModelInstance droneShadow;

    private final Vector3 botPosition = new Vector3();
    private final Vector3 dronePosition = new Vector3();
    private final Vector3 focus = new Vector3();
    private float botYaw;
    private float targetYaw;
    private float droneYaw;
    private int lastX = Integer.MIN_VALUE;
    private int lastZ = Integer.MIN_VALUE;
    private float time;
    private float viewWidth = 18f;

    public FarmRenderer() {
        environment.set(ColorAttribute.createAmbientLight(0.58f, 0.56f, 0.54f, 1f));
        // Key light from the upper-left of the screen, warm; soft cool fill from the right.
        environment.add(new DirectionalLight().set(0.6f, 0.56f, 0.5f, 0.45f, -1.25f, -1.05f));
        environment.add(new DirectionalLight().set(0.16f, 0.18f, 0.22f, -0.9f, -0.35f, 0.75f));

        tractor = new ModelInstance(models.tractor);
        drone = new ModelInstance(models.drone);
        rotorA = new ModelInstance(models.rotor);
        rotorB = new ModelInstance(models.rotor);
        tractorShadow = new ModelInstance(models.shadow);
        droneShadow = new ModelInstance(models.shadow);

        camera.near = 0.1f;
        camera.far = 80f;
    }

    /** Width of the visible world in tiles (smaller = closer camera). Smoothly interpolated. */
    public void setViewWidth(float width) {
        viewWidth = width;
    }

    /** Drops cached tile instances (e.g. after a graphics-quality change). Models are kept. */
    public void invalidate() {
        visuals.clear();
    }

    /** Removes cached tiles that are far away from the robot so the cache stays bounded. */
    public void trimCache(int centerX, int centerZ, int keepRadius) {
        LongMap.Keys keys = visuals.keys();
        while (keys.hasNext) {
            long key = keys.next();
            int x = (int) (key >> 32);
            int z = (int) key;
            if (Math.max(Math.abs(x - centerX), Math.abs(z - centerZ)) > keepRadius) keys.remove();
        }
    }

    public void render(FarmWorld world, int radius, float delta) {
        delta = Math.min(delta, 0.1f);
        time += delta;
        updateRobot(world, delta);
        updateCamera(delta);

        batch.begin(camera);
        int cx = world.playerX();
        int cz = world.playerZ();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                renderTile(world, x, z, radius);
            }
        }
        renderRobot();
        batch.end();
    }

    // ------------------------------------------------------------------ tiles

    private void renderTile(FarmWorld world, int x, int z, int radius) {
        // Island edge: tiles near the view radius shrink and sink, giving a soft diorama border.
        float dist = Math.max(Math.abs(x - botPosition.x), Math.abs(z - botPosition.z));
        float s = MathUtils.clamp((radius + 0.6f - dist) / 1.4f, 0f, 1f);
        if (s <= 0.3f) return; // tiny far tiles read as noise; the island simply ends there
        s = s * s * (3f - 2f * s);
        float sink = (1f - s) * 0.7f;

        FarmTile tile = world.tileAt(x, z);
        FarmWorld.Structure structure = world.structureAt(x, z);
        long key = (((long) x) << 32) ^ (z & 0xffffffffL);
        int signature = signature(tile, structure);
        TileVisual visual = visuals.get(key);
        if (visual == null || visual.signature != signature) {
            visual = createVisual(tile, structure, x, z, signature);
            visuals.put(key, visual);
        }

        float bob = 0f;
        if (tile.terrain == FarmTile.Terrain.WATER) bob = MathUtils.sin(time * 1.7f + x * 0.8f + z * 1.1f) * 0.012f;
        visual.ground.transform.setToTranslationAndScaling(x, -sink + bob, z, s, s, s);
        batch.render(visual.ground, environment);

        if (visual.crop != null) {
            float sway = MathUtils.sin(time * 1.6f + visual.phase) * 2.5f;
            visual.crop.transform.setToTranslation(x, -sink, z).rotate(Vector3.Y, visual.yaw)
                    .rotate(Vector3.Z, sway).scale(s * CROP_SCALE, s * CROP_SCALE, s * CROP_SCALE);
            batch.render(visual.crop, environment);
        }
        boolean robotHere = x == world.playerX() && z == world.playerZ();
        if (visual.decoration != null && !robotHere) {
            visual.decoration.transform.setToTranslation(x + visual.offsetX * s, -sink, z + visual.offsetZ * s)
                    .rotate(Vector3.Y, visual.yaw).scale(s, s, s);
            batch.render(visual.decoration, environment);
        }
    }

    private static int signature(FarmTile tile, FarmWorld.Structure structure) {
        int crop = tile.hasCrop() ? ClayModels.cropIndex(tile.crop) + 1 : 0;
        return tile.terrain.ordinal()
                | (tile.tilled ? 1 << 2 : 0)
                | (tile.watered ? 1 << 3 : 0)
                | (crop << 4)
                | (Math.min(3, Math.max(0, tile.growth)) << 8)
                | (structure.ordinal() << 12);
    }

    private TileVisual createVisual(FarmTile tile, FarmWorld.Structure structure, int x, int z, int signature) {
        long seed = (((long) x) * 73856093L) ^ (((long) z) * 19349663L);
        float roll = ClayKit.hash01(seed);
        Model ground;
        if (tile.terrain == FarmTile.Terrain.WATER) ground = models.water;
        else if (tile.tilled) ground = tile.watered ? models.wet : models.tilled;
        else if (tile.terrain == FarmTile.Terrain.GRASS) {
            // Mostly flat grass keeps visual noise low; a few tiles get soft clay bumps.
            float variant = ClayKit.hash01(seed + 7);
            ground = models.grass[variant < 0.62f ? 0 : variant < 0.84f ? 1 : 2];
        }
        else ground = models.dirt;

        TileVisual visual = new TileVisual(new ModelInstance(ground), signature);
        visual.phase = roll * MathUtils.PI2;
        if (tile.hasCrop()) {
            visual.crop = new ModelInstance(models.crop(ClayModels.cropIndex(tile.crop), tile.growth));
            // Sunflowers keep facing the camera; other crops get a small random turn for a hand-placed feel.
            visual.yaw = "SUNFLOWER".equalsIgnoreCase(tile.crop) ? 0f : (int) (roll * 4f) * 90f;
            return visual;
        }

        Model decoration = models.structure(structure);
        boolean inSpawnClearing = Math.abs(x) <= 3 && Math.abs(z) <= 3;
        if (decoration == null && !tile.tilled && !inSpawnClearing) {
            float pick = ClayKit.hash01(seed + 11);
            if (tile.terrain == FarmTile.Terrain.GRASS) {
                if (pick < 0.10f) decoration = models.tuft;
                else if (pick < 0.135f) decoration = models.flowerPink;
                else if (pick < 0.16f) decoration = models.flowerWhite;
            } else if (tile.terrain == FarmTile.Terrain.DIRT) {
                if (pick < 0.07f) decoration = models.pebbles;
                else if (pick < 0.09f) decoration = models.cactus;
            }
            if (decoration != null) {
                visual.offsetX = (ClayKit.hash01(seed + 3) - 0.5f) * 0.4f;
                visual.offsetZ = (ClayKit.hash01(seed + 5) - 0.5f) * 0.4f;
                visual.yaw = roll * 360f;
            }
        }
        if (decoration != null) visual.decoration = new ModelInstance(decoration);
        return visual;
    }

    // ------------------------------------------------------------------ robot & camera

    private void updateRobot(FarmWorld world, float delta) {
        int px = world.playerX();
        int pz = world.playerZ();
        if (lastX == Integer.MIN_VALUE || Math.abs(px - botPosition.x) + Math.abs(pz - botPosition.z) > 8f) {
            botPosition.set(px, 0, pz); // first frame or teleport (save load): snap
            dronePosition.set(px - 0.6f, 1.35f, pz - 0.6f);
            focus.set(px, 0, pz);
        } else if (px != lastX || pz != lastZ) {
            int dx = px - lastX;
            int dz = pz - lastZ;
            if (dx != 0 || dz != 0) targetYaw = MathUtils.atan2(-dz, dx) * MathUtils.radiansToDegrees;
        }
        lastX = px;
        lastZ = pz;

        float follow = 1f - (float) Math.exp(-11f * delta);
        botPosition.x += (px - botPosition.x) * follow;
        botPosition.z += (pz - botPosition.z) * follow;
        botYaw = approachAngle(botYaw, targetYaw, 1f - (float) Math.exp(-14f * delta));

        float droneFollow = 1f - (float) Math.exp(-3.5f * delta);
        dronePosition.x += (botPosition.x - 0.6f - dronePosition.x) * droneFollow;
        dronePosition.z += (botPosition.z - 0.6f - dronePosition.z) * droneFollow;
        dronePosition.y = 1.35f + MathUtils.sin(time * 2.1f) * 0.07f;
        droneYaw = approachAngle(droneYaw, botYaw, 1f - (float) Math.exp(-3f * delta));
    }

    private void updateCamera(float delta) {
        float follow = 1f - (float) Math.exp(-7f * delta);
        focus.x += (botPosition.x - focus.x) * follow;
        focus.z += (botPosition.z - focus.z) * follow;
        float screenW = Math.max(1f, Gdx.graphics.getWidth());
        float screenH = Math.max(1f, Gdx.graphics.getHeight());
        float zoom = 1f - (float) Math.exp(-5f * delta);
        float width = camera.viewportWidth <= 0f ? viewWidth : camera.viewportWidth + (viewWidth - camera.viewportWidth) * zoom;
        camera.viewportWidth = width;
        camera.viewportHeight = width * (screenH / screenW);
        camera.position.set(focus.x + CAMERA_OFFSET_XZ, CAMERA_OFFSET_Y, focus.z + CAMERA_OFFSET_XZ);
        camera.up.set(Vector3.Y);
        camera.lookAt(focus.x, 0f, focus.z);
        camera.update();
    }

    private void renderRobot() {
        float moving = Math.min(1f, (Math.abs(lastX - botPosition.x) + Math.abs(lastZ - botPosition.z)) * 3f);
        float bounce = Math.abs(MathUtils.sin(time * 16f)) * 0.045f * moving + MathUtils.sin(time * 2.4f) * 0.006f;
        tractor.transform.setToTranslation(botPosition.x, 0.02f + bounce, botPosition.z).rotate(Vector3.Y, botYaw);
        batch.render(tractor, environment);
        tractorShadow.transform.setToTranslationAndScaling(botPosition.x, 0.05f, botPosition.z, 0.95f, 1f, 0.95f);
        batch.render(tractorShadow, environment);

        drone.transform.setToTranslation(dronePosition).rotate(Vector3.Y, droneYaw)
                .rotate(Vector3.X, MathUtils.sin(time * 1.3f) * 4f);
        batch.render(drone, environment);
        float spin = time * 900f;
        rotorA.transform.set(drone.transform).translate(0, 0.135f, 0.3f).rotate(Vector3.Y, spin);
        rotorB.transform.set(drone.transform).translate(0, 0.135f, -0.3f).rotate(Vector3.Y, -spin);
        batch.render(rotorA, environment);
        batch.render(rotorB, environment);
        droneShadow.transform.setToTranslationAndScaling(dronePosition.x, 0.05f, dronePosition.z, 0.45f, 1f, 0.45f);
        batch.render(droneShadow, environment);
    }

    private static float approachAngle(float current, float target, float t) {
        float diff = ((target - current) % 360f + 540f) % 360f - 180f;
        return current + diff * t;
    }

    @Override
    public void dispose() {
        visuals.clear();
        batch.dispose();
        models.dispose();
    }

    private static final class TileVisual {
        final ModelInstance ground;
        final int signature;
        ModelInstance crop;
        ModelInstance decoration;
        float yaw;
        float phase;
        float offsetX;
        float offsetZ;

        TileVisual(ModelInstance ground, int signature) {
            this.ground = ground;
            this.signature = signature;
        }
    }
}
