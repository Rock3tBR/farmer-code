package com.javafarmer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.ConeShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.List;

/**
 * Small procedural "clay" modelling kit on top of {@link ModelBuilder}.
 *
 * <p>Every shape is written with per-vertex colour into a single white material, so most models
 * become one mesh / one draw call. Rounded boxes receive a baked vertical gradient that imitates
 * soft ambient occlusion, which is what gives the blocks their handmade clay look.</p>
 *
 * <p>Models are created once at startup; ownership is handed to the list passed to the constructor.</p>
 */
final class ClayKit {
    static final long ATTRIBUTES = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
            | VertexAttributes.Usage.ColorUnpacked;
    private static final int BEVEL_STEPS = 4;

    private final ModelBuilder modelBuilder = new ModelBuilder();
    private final MeshPartBuilder.VertexInfo vertex = new MeshPartBuilder.VertexInfo();
    private final Matrix4 transform = new Matrix4();
    private final Vector3 cube = new Vector3();
    private final Vector3 inner = new Vector3();
    private final Vector3 normal = new Vector3();
    private final Color shaded = new Color();
    private final List<Model> owner;
    private MeshPartBuilder part;
    private int partCounter;

    ClayKit(List<Model> owner) {
        this.owner = owner;
    }

    /** Starts a model whose first part uses a matte white material (colours come from vertices). */
    ClayKit begin() {
        return beginWith(new Material(ColorAttribute.createDiffuse(Color.WHITE)));
    }

    /** Starts a model whose first part uses the given material. */
    ClayKit beginWith(Material material) {
        modelBuilder.begin();
        partCounter = 0;
        return material(material);
    }

    /** Switches following shapes to a new part with a custom material (e.g. glossy water, blended shadow). */
    ClayKit material(Material material) {
        part = modelBuilder.part("clay" + partCounter++, GL20.GL_TRIANGLES, ATTRIBUTES, material);
        transform.idt();
        return this;
    }

    Model end() {
        Model model = modelBuilder.end();
        owner.add(model);
        part = null;
        return model;
    }

    // ----- transform helpers (applied to the shapes that follow) -----

    ClayKit at(float x, float y, float z) {
        transform.setToTranslation(x, y, z);
        return this;
    }

    ClayKit rotX(float degrees) { transform.rotate(Vector3.X, degrees); return this; }
    ClayKit rotY(float degrees) { transform.rotate(Vector3.Y, degrees); return this; }
    ClayKit rotZ(float degrees) { transform.rotate(Vector3.Z, degrees); return this; }

    ClayKit move(float x, float y, float z) {
        transform.translate(x, y, z);
        return this;
    }

    // ----- shapes -----

    /**
     * Rounded box whose <b>base</b> sits at the current origin (it grows upwards by {@code h}).
     * {@code ao} darkens the bottom of the box (0 = flat colour, 0.4 = strong clay shading).
     */
    ClayKit box(float w, float h, float d, float radius, Color color, float ao) {
        part.setVertexTransform(transform);
        part.setColor(null);
        float hx = w * 0.5f;
        float hy = h * 0.5f;
        float hz = d * 0.5f;
        float r = Math.max(0.001f, Math.min(radius, Math.min(hx, Math.min(hy, hz)) * 0.999f));
        inner.set(hx - r, hy - r, hz - r);
        float[] xs = axis(hx, r);
        float[] ys = axis(hy, r);
        float[] zs = axis(hz, r);
        float[][] axes = {xs, ys, zs};
        float[] half = {hx, hy, hz};
        int n = xs.length;
        short[] grid = new short[n * n];
        for (int a = 0; a < 3; a++) {
            int u = (a + 1) % 3;
            int v = (a + 2) % 3;
            for (int sign = -1; sign <= 1; sign += 2) {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        set(cube, a, sign * half[a]);
                        set(cube, u, axes[u][i]);
                        set(cube, v, axes[v][j]);
                        float px = MathUtils.clamp(cube.x, -inner.x, inner.x);
                        float py = MathUtils.clamp(cube.y, -inner.y, inner.y);
                        float pz = MathUtils.clamp(cube.z, -inner.z, inner.z);
                        normal.set(cube.x - px, cube.y - py, cube.z - pz);
                        if (normal.len2() < 1e-10f) {
                            normal.setZero();
                            set(normal, a, sign);
                        } else {
                            normal.nor();
                        }
                        float x = px + normal.x * r;
                        float y = py + normal.y * r;
                        float z = pz + normal.z * r;
                        float t = (y + hy) / h; // 0 at the bottom, 1 at the top
                        float shade = (1f - ao) + ao * (t * t * (3f - 2f * t));
                        if (normal.y > 0.6f) shade *= 1.05f; // slightly lifted top faces
                        shaded.set(Math.min(1f, color.r * shade), Math.min(1f, color.g * shade),
                                Math.min(1f, color.b * shade), color.a);
                        vertex.set(null, null, null, null);
                        vertex.setPos(x, y + hy, z).setNor(normal).setCol(shaded);
                        grid[i * n + j] = part.vertex(vertex);
                    }
                }
                for (int i = 0; i < n - 1; i++) {
                    for (int j = 0; j < n - 1; j++) {
                        short p00 = grid[i * n + j];
                        short p10 = grid[(i + 1) * n + j];
                        short p11 = grid[(i + 1) * n + j + 1];
                        short p01 = grid[i * n + j + 1];
                        if (sign > 0) {
                            part.triangle(p00, p10, p11);
                            part.triangle(p00, p11, p01);
                        } else {
                            part.triangle(p00, p11, p10);
                            part.triangle(p00, p01, p11);
                        }
                    }
                }
            }
        }
        part.setVertexTransform(null);
        return this;
    }

    /** Ellipsoid centred on the current origin. */
    ClayKit ball(float w, float h, float d, Color color) {
        int div = Math.max(w, Math.max(h, d)) > 0.25f ? 18 : 12;
        part.setVertexTransform(transform);
        part.setColor(color);
        SphereShapeBuilder.build(part, w, h, d, div, div * 2 / 3);
        part.setVertexTransform(null);
        part.setColor(null);
        return this;
    }

    /** Capped cylinder along Y, centred on the current origin. */
    ClayKit cylinder(float w, float h, float d, Color color) {
        part.setVertexTransform(transform);
        part.setColor(color);
        CylinderShapeBuilder.build(part, w, h, d, 16);
        part.setVertexTransform(null);
        part.setColor(null);
        return this;
    }

    /** Cone along Y (apex at +h/2), centred on the current origin. */
    ClayKit cone(float w, float h, float d, Color color) {
        part.setVertexTransform(transform);
        part.setColor(color);
        ConeShapeBuilder.build(part, w, h, d, 14);
        part.setVertexTransform(null);
        part.setColor(null);
        return this;
    }

    /** Coordinates along one axis: dense in the two rounded bevels, a single span in the flat middle. */
    private static float[] axis(float half, float r) {
        float[] values = new float[(BEVEL_STEPS + 1) * 2];
        for (int i = 0; i <= BEVEL_STEPS; i++) {
            float f = (float) i / BEVEL_STEPS;
            values[i] = -half + r * f;
            values[values.length - 1 - i] = half - r * f;
        }
        return values;
    }

    private static void set(Vector3 vector, int axis, float value) {
        if (axis == 0) vector.x = value;
        else if (axis == 1) vector.y = value;
        else vector.z = value;
    }

    /** Small deterministic hash used to place variations without allocating Random instances. */
    static float hash01(long seed) {
        seed = (seed ^ (seed >>> 33)) * 0xff51afd7ed558ccdL;
        seed = (seed ^ (seed >>> 33)) * 0xc4ceb9fe1a85ec53L;
        seed ^= seed >>> 33;
        return (seed >>> 40) / (float) (1L << 24);
    }
}
