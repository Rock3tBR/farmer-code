#!/usr/bin/env python3
"""Bakes the game's bitmap fonts (AngelCode BMFont text format + PNG atlas) from OFL TrueType fonts.

Usage:  python3 tools/bake_fonts.py <Fredoka[wdth,wght].ttf> <JetBrainsMono[wght].ttf>

The TTF files are not shipped; download them from github.com/google/fonts (ofl/fredoka, ofl/jetbrainsmono).
Requires Pillow. Output: assets/fonts/*.fnt + *.png. Glyphs are rendered large and drawn
downscaled in game with mipmaps, so text stays crisp on standard and Retina displays.
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "assets" / "fonts"
CHARS = [c for c in range(32, 127)] + [c for c in range(160, 256)] + [0x2022, 0x2212, 0x2026, 0x2014, 0x2013, 0x201C, 0x201D]
PAD = 4  # transparent border around each glyph (prevents mipmap bleeding)


def load(path, size, weight):
    font = ImageFont.truetype(str(path), size)
    try:
        axes = font.get_variation_axes()
        values = []
        for axis in axes:
            name = axis.get("name", b"")
            name = name.decode() if isinstance(name, bytes) else str(name)
            if name.lower().startswith("weight"):
                values.append(weight)
            else:
                values.append(axis.get("default", axis["minimum"]))
        font.set_variation_by_axes(values)
    except OSError:
        pass  # static font
    return font


def bake(ttf, name, size, weight, atlas_w=1024):
    font = load(ttf, size, weight)
    ascent, descent = font.getmetrics()
    glyphs = []
    for code in CHARS:
        ch = chr(code)
        advance = font.getlength(ch)
        if code != 32 and advance <= 0:
            continue
        left, top, right, bottom = font.getbbox(ch, anchor="ls")
        w = max(0, right - left)
        h = max(0, bottom - top)
        img = Image.new("L", (w + 2 * PAD, h + 2 * PAD), 0)
        if w and h:
            ImageDraw.Draw(img).text((PAD - left, PAD - top), ch, font=font, fill=255, anchor="ls")
        glyphs.append((code, img, left, top, advance))

    # simple shelf packing
    x = y = shelf = 0
    placed = []
    for code, img, left, top, advance in sorted(glyphs, key=lambda g: -g[1].height):
        if x + img.width > atlas_w:
            x = 0
            y += shelf
            shelf = 0
        placed.append((code, img, x, y, left, top, advance))
        x += img.width
        shelf = max(shelf, img.height)
    atlas_h = 1
    while atlas_h < y + shelf:
        atlas_h *= 2

    alpha = Image.new("L", (atlas_w, atlas_h), 0)
    for code, img, gx, gy, *_ in placed:
        alpha.paste(img, (gx, gy))
    atlas = Image.merge("RGBA", (Image.new("L", alpha.size, 255),) * 3 + (alpha,))
    OUT.mkdir(parents=True, exist_ok=True)
    atlas.save(OUT / f"{name}.png", optimize=True)

    line_height = ascent + descent
    lines = [
        f'info face="{name}" size={size} bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0',
        f"common lineHeight={line_height} base={ascent} scaleW={atlas_w} scaleH={atlas_h} pages=1 packed=0",
        f'page id=0 file="{name}.png"',
        f"chars count={len(placed)}",
    ]
    for code, img, gx, gy, left, top, advance in sorted(placed, key=lambda p: p[0]):
        lines.append(
            f"char id={code} x={gx} y={gy} width={img.width} height={img.height} "
            f"xoffset={left - PAD} yoffset={ascent + top - PAD} xadvance={round(advance)} page=0 chnl=15")
    (OUT / f"{name}.fnt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{name}: {len(placed)} glyphs, atlas {atlas_w}x{atlas_h}, lineHeight {line_height}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    bake(Path(sys.argv[1]), "fredoka-ui", 56, 600)
    bake(Path(sys.argv[2]), "jetbrains-mono", 40, 500, atlas_w=512)
