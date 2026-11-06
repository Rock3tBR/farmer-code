package com.javafarmer.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.List;

import static com.javafarmer.render.ClayPalette.*;

/**
 * Immediate-mode UI helpers in the Cozy Clay style: soft rounded "clay" slabs with a darker lip,
 * pill buttons, chips with tiny procedural icons and outlined titles.
 *
 * <p>Buttons are registered while drawing and hit-tested on click. Owns its batches and font.</p>
 */
public final class ClayUi implements Disposable {
    public enum Icon { NONE, SUN, STAR, COIN, SEED, PLAY, CODE, FLAG, HOME }

    private static final int ARC_SEGMENTS = 10;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    /** Line height (px) that a text scale of 1.0 maps to; all scales in the game are relative to it. */
    private static final float BASE_LINE_HEIGHT = 20f;

    private final List<Texture> fontTextures = new ArrayList<>();
    private final BitmapFont font;
    private final BitmapFont mono;
    private final float fontUnit;
    private final float monoUnit;
    private final GlyphLayout layout = new GlyphLayout();
    private final List<Button> buttons = new ArrayList<>();
    private final Color scratch = new Color();
    private final Color scratchLip = new Color();
    private float width;
    private float height;

    public ClayUi() {
        font = loadFont("fonts/fredoka-ui");
        mono = loadFont("fonts/jetbrains-mono");
        fontUnit = BASE_LINE_HEIGHT / font.getData().lineHeight;
        monoUnit = BASE_LINE_HEIGHT / mono.getData().lineHeight;
    }

    /**
     * Loads a baked BMFont (see tools/bake_fonts.py) with a mipmapped atlas so it stays crisp when
     * drawn downscaled. Falls back to libGDX's built-in font if the files are missing.
     */
    private BitmapFont loadFont(String basePath) {
        BitmapFont result;
        try {
            FileHandle fnt = Gdx.files.internal(basePath + ".fnt");
            FileHandle png = Gdx.files.internal(basePath + ".png");
            if (!fnt.exists() || !png.exists()) throw new IllegalStateException("font missing: " + basePath);
            Texture texture = new Texture(png, true);
            texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            fontTextures.add(texture);
            result = new BitmapFont(new BitmapFont.BitmapFontData(fnt, false), new TextureRegion(texture), false);
        } catch (RuntimeException ex) {
            Gdx.app.error("ClayUi", "Using built-in font: " + ex.getMessage());
            result = new BitmapFont();
            result.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        }
        result.setUseIntegerPositions(false);
        return result;
    }

    public BitmapFont font() { return font; }
    public BitmapFont mono() { return mono; }
    /** Converts a game text scale (1.0 = 20 px line) into the raw scale for the code font. */
    public float monoScale(float scale) { return scale * monoUnit; }
    public SpriteBatch batch() { return batch; }
    /** Starts a raw filled-shape pass with blending enabled. Close it with {@code end()}. */
    public ShapeRenderer beginRawShapes() {
        beginShapes();
        return shapes;
    }
    public float width() { return width; }
    public float height() { return height; }

    public void begin(float width, float height) {
        this.width = width;
        this.height = height;
        camera.setToOrtho(false, width, height);
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
        buttons.clear();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    public void end() {
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Runs the action of the top-most button under the point. Returns true when a button was hit. */
    public boolean click(float x, float y) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button b = buttons.get(i);
            if (b.contains(x, y)) {
                b.action.run();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ primitives

    /**
     * SpriteBatch.end() disables GL blending, so every shape pass re-enables it; otherwise translucent
     * shapes drawn after any text would become opaque.
     */
    private void beginShapes() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
    }

    public void fill(float x, float y, float w, float h, Color color) {
        beginShapes();
        shapes.setColor(color);
        shapes.rect(x, y, w, h);
        shapes.end();
    }

    /** Rounded rectangle drawn without overlapping triangles, so translucent colours stay uniform. */
    public void roundRect(float x, float y, float w, float h, float r, Color color) {
        r = Math.max(0f, Math.min(r, Math.min(w, h) * 0.5f));
        beginShapes();
        shapes.setColor(color);
        roundRectShape(x, y, w, h, r);
        shapes.end();
    }

    private void roundRectShape(float x, float y, float w, float h, float r) {
        if (r <= 0.5f) {
            shapes.rect(x, y, w, h);
            return;
        }
        shapes.rect(x + r, y, w - 2 * r, h);
        shapes.rect(x, y + r, r, h - 2 * r);
        shapes.rect(x + w - r, y + r, r, h - 2 * r);
        shapes.arc(x + r, y + r, r, 180f, 90f, ARC_SEGMENTS);
        shapes.arc(x + w - r, y + r, r, 270f, 90f, ARC_SEGMENTS);
        shapes.arc(x + w - r, y + h - r, r, 0f, 90f, ARC_SEGMENTS);
        shapes.arc(x + r, y + h - r, r, 90f, 90f, ARC_SEGMENTS);
    }

    /** A clay slab: soft drop shadow, darker lip underneath and the face on top. */
    public void slab(float x, float y, float w, float h, float r, Color face, Color lip) {
        float depth = Math.min(7f, h * 0.12f + 2f);
        scratch.set(0.18f, 0.12f, 0.08f, 0.16f);
        roundRect(x + 3, y - depth - 4, w, h, r, scratch);
        roundRect(x, y - depth, w, h, r, lip);
        roundRect(x, y, w, h, r, face);
        scratch.set(1f, 1f, 1f, 0.28f);
        if (h > 24) roundRect(x + r * 0.6f, y + h - 9, w - r * 1.2f, 4, 2, scratch);
    }

    /** Small cream label pill, used to keep text readable over the busy 3D scene. */
    public void pill(String text, float x, float y, float scale, boolean centered) {
        float w = labelWidth(text, scale) + 28f;
        float h = capHeight(scale) + 20f;
        float px = centered ? x - w / 2f : x;
        scratch.set(UI_CREAM.r, UI_CREAM.g, UI_CREAM.b, 0.92f);
        roundRect(px, y, w, h, h / 2f, scratch);
        text(text, px + 14f, y + h / 2f + capHeight(scale) / 2f, scale, UI_INK, false);
    }

    public void card(float x, float y, float w, float h) {
        slab(x, y, w, h, 22, UI_CREAM, UI_CREAM_LIP);
    }

    /** Clay pill button. The face lightens on hover and sinks while pressed. */
    public void button(float x, float y, float w, float h, String label, Color face, Color lip, Icon icon, Runnable action) {
        buttons.add(new Button(x, y - 6, w, h + 6, action));
        float mx = Gdx.input.getX();
        float my = height - Gdx.input.getY();
        boolean hover = mx >= x && mx <= x + w && my >= y - 6 && my <= y + h;
        boolean pressed = hover && Gdx.input.isButtonPressed(com.badlogic.gdx.Input.Buttons.LEFT);
        float depth = 6f;
        float sink = pressed ? 3f : 0f;
        float r = Math.min(h * 0.5f, 18f);
        scratch.set(0.18f, 0.12f, 0.08f, 0.18f);
        roundRect(x + 2, y - depth - 4, w, h, r, scratch);
        roundRect(x, y - depth, w, h, r, lip);
        scratch.set(face);
        if (hover) scratch.lerp(Color.WHITE, 0.12f);
        roundRect(x, y - sink, w, h, r, scratch);
        scratchLip.set(1f, 1f, 1f, 0.3f);
        roundRect(x + r * 0.7f, y - sink + h - 10, w - r * 1.4f, 5, 2.5f, scratchLip);

        boolean lightFace = face == UI_CREAM || face == UI_NEUTRAL;
        Color ink = lightFace ? UI_INK : UI_TEXT_LIGHT;
        float textScale = label.length() <= 2 ? 1.5f : h >= 50 ? 1.0f : 0.92f;
        float cy = y - sink + h / 2f;
        float labelW = labelWidth(label, textScale);
        float iconSize = icon == Icon.NONE ? 0 : h * 0.42f;
        float gap = icon == Icon.NONE ? 0 : 10f;
        float total = labelW + iconSize + gap;
        float startX = x + (w - total) / 2f;
        if (icon != Icon.NONE) icon(icon, startX + iconSize / 2f, cy, iconSize, lightFace ? UI_INK : UI_TEXT_LIGHT);
        float baseline = cy + capHeight(textScale) / 2f;
        if (!lightFace) {
            scratch.set(0f, 0f, 0f, 0.14f);
            text(label, startX + iconSize + gap, baseline - 1.5f, textScale, scratch, false);
        }
        text(label, startX + iconSize + gap, baseline, textScale, ink, false);
    }

    /** Small rounded chip with an icon and a value (HUD). Returns the chip width. */
    public float chip(float x, float y, Icon icon, String value, float h) {
        float scale = 0.95f;
        float w = labelWidth(value, scale) + h + 22f;
        slab(x, y, w, h, h / 2f, UI_CREAM, UI_CREAM_LIP);
        icon(icon, x + h * 0.55f, y + h / 2f, h * 0.56f, null);
        text(value, x + h + 4f, y + h / 2f + capHeight(scale) / 2f, scale, UI_INK, false);
        return w;
    }

    /** Height of capital letters for a text scale; used to centre labels vertically. */
    public float capHeight(float scale) {
        return font.getData().capHeight * scale * fontUnit;
    }

    public float labelWidth(String text, float scale) {
        float old = font.getData().scaleX;
        font.getData().setScale(scale * fontUnit);
        layout.setText(font, text);
        font.getData().setScale(old);
        return layout.width;
    }

    public void text(String text, float x, float y, float scale, Color color, boolean centered) {
        float oldX = font.getData().scaleX;
        float oldY = font.getData().scaleY;
        font.getData().setScale(scale * fontUnit);
        font.setColor(color);
        layout.setText(font, text);
        float drawX = centered ? x - layout.width / 2f : x;
        batch.begin();
        font.draw(batch, text, drawX, y);
        batch.end();
        font.getData().setScale(oldX, oldY);
        font.setColor(Color.WHITE);
    }

    /** Chunky extruded title, like the clay lettering in the reference sheet. */
    public void title(String text, float x, float y, float scale, Color face, Color outline) {
        float depth = Math.max(3f, scale * 2.2f);
        float o = Math.max(1.5f, scale * 0.9f);
        scratch.set(0.18f, 0.12f, 0.08f, 0.18f);
        text(text, x + depth * 0.4f, y - depth - 4f, scale, scratch, true);
        for (float d = depth; d > 0; d -= 1f) text(text, x, y - d, scale, outline, true);
        text(text, x - o, y, scale, outline, true);
        text(text, x + o, y, scale, outline, true);
        text(text, x, y + o, scale, outline, true);
        text(text, x, y, scale, face, true);
    }

    // ------------------------------------------------------------------ icons

    /** Draws a tiny procedural icon centred on (cx, cy). {@code tint} overrides icon colours when not null. */
    public void icon(Icon icon, float cx, float cy, float size, Color tint) {
        float r = size / 2f;
        beginShapes();
        switch (icon) {
            case SUN -> {
                shapes.setColor(tint != null ? tint : UI_ORANGE);
                for (int i = 0; i < 8; i++) {
                    float a = i * MathUtils.PI / 4f;
                    shapes.circle(cx + MathUtils.cos(a) * r * 0.82f, cy + MathUtils.sin(a) * r * 0.82f, r * 0.17f, 8);
                }
                shapes.circle(cx, cy, r * 0.58f, 18);
                if (tint == null) { shapes.setColor(FLOWER_CENTER); shapes.circle(cx - r * 0.1f, cy + r * 0.1f, r * 0.34f, 14); }
            }
            case STAR -> {
                shapes.setColor(tint != null ? tint : UI_BLUE);
                star(cx, cy, r, r * 0.45f);
            }
            case COIN -> {
                shapes.setColor(tint != null ? tint : UI_ORANGE_LIP);
                shapes.circle(cx, cy - r * 0.08f, r, 20);
                shapes.setColor(tint != null ? tint : GOLD);
                shapes.circle(cx, cy, r * 0.92f, 20);
                shapes.setColor(tint != null ? tint : ROOF);
                shapes.circle(cx, cy, r * 0.55f, 16);
            }
            case SEED -> {
                shapes.setColor(tint != null ? tint : WOOD_DARK);
                shapes.ellipse(cx - r * 0.42f, cy - r * 0.95f, r * 0.84f, r * 1.15f, 16);
                shapes.setColor(tint != null ? tint : WOOD);
                shapes.ellipse(cx - r * 0.3f, cy - r * 0.8f, r * 0.55f, r * 0.85f, 14);
                shapes.setColor(tint != null ? tint : UI_GREEN);
                shapes.rectLine(cx, cy + r * 0.1f, cx, cy + r * 0.55f, Math.max(1.5f, r * 0.14f));
                shapes.ellipse(cx - r * 0.95f, cy + r * 0.35f, r * 0.95f, r * 0.5f, 12);
                shapes.ellipse(cx + r * 0.02f, cy + r * 0.45f, r * 0.95f, r * 0.5f, 12);
            }
            case PLAY -> {
                shapes.setColor(tint != null ? tint : UI_TEXT_LIGHT);
                shapes.triangle(cx - r * 0.6f, cy - r * 0.8f, cx - r * 0.6f, cy + r * 0.8f, cx + r * 0.8f, cy);
            }
            case CODE -> {
                shapes.setColor(tint != null ? tint : UI_TEXT_LIGHT);
                float t = Math.max(2f, r * 0.24f);
                shapes.rectLine(cx - r * 0.25f, cy + r * 0.6f, cx - r * 0.9f, cy, t);
                shapes.rectLine(cx - r * 0.9f, cy, cx - r * 0.25f, cy - r * 0.6f, t);
                shapes.rectLine(cx + r * 0.25f, cy + r * 0.6f, cx + r * 0.9f, cy, t);
                shapes.rectLine(cx + r * 0.9f, cy, cx + r * 0.25f, cy - r * 0.6f, t);
            }
            case FLAG -> {
                shapes.setColor(tint != null ? tint : UI_TEXT_LIGHT);
                float t = Math.max(2f, r * 0.2f);
                shapes.rectLine(cx - r * 0.6f, cy - r, cx - r * 0.6f, cy + r, t);
                shapes.triangle(cx - r * 0.6f, cy + r, cx - r * 0.6f, cy + r * 0.1f, cx + r * 0.85f, cy + r * 0.55f);
            }
            case HOME -> {
                shapes.setColor(tint != null ? tint : UI_TEXT_LIGHT);
                float l = r * 0.25f;
                shapes.rect(cx - r * 0.8f, cy - r * 0.8f, r * 1.6f, l);
                shapes.rect(cx - r * 0.8f, cy - r * 0.15f, r * 1.6f, l);
                shapes.rect(cx - r * 0.8f, cy + r * 0.5f, r * 1.6f, l);
            }
            case NONE -> { }
        }
        shapes.end();
    }

    private void star(float cx, float cy, float outer, float inner) {
        for (int i = 0; i < 5; i++) {
            float a0 = MathUtils.PI / 2f + i * MathUtils.PI2 / 5f;
            float aL = a0 - MathUtils.PI / 5f;
            float aR = a0 + MathUtils.PI / 5f;
            shapes.triangle(cx, cy,
                    cx + MathUtils.cos(aL) * inner, cy + MathUtils.sin(aL) * inner,
                    cx + MathUtils.cos(a0) * outer, cy + MathUtils.sin(a0) * outer);
            shapes.triangle(cx, cy,
                    cx + MathUtils.cos(a0) * outer, cy + MathUtils.sin(a0) * outer,
                    cx + MathUtils.cos(aR) * inner, cy + MathUtils.sin(aR) * inner);
        }
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        mono.dispose();
        for (Texture texture : fontTextures) texture.dispose();
    }

    private record Button(float x, float y, float width, float height, Runnable action) {
        boolean contains(float px, float py) { return px >= x && px <= x + width && py >= y && py <= y + height; }
    }
}
