#!/usr/bin/env python3
"""Generate the Bleach Bucket item texture: `python3 tools/bleach_bucket.py <minecraft client jar>` from the repo root.

NeoForge's fluid container model tints the bucket's fluid with the fluid's own tint, alpha included, and bleach is
almost clear in the world (0x91 alpha), so the bucket looked empty. The item gets its own texture instead: vanilla's
water bucket with the water pixels redrawn in bleach's colour, opaque, keeping their light and shade.
"""
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

TINT = (226, 236, 172)   # bleach #ECF1C4, a little stronger so it reads at 16 px
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/haven_compat/textures/item/bleach_bucket.png"


def main(jar):
    with zipfile.ZipFile(jar) as z:
        bucket = Image.open(io.BytesIO(z.read("assets/minecraft/textures/item/bucket.png"))).convert("RGBA")
        water = Image.open(io.BytesIO(z.read("assets/minecraft/textures/item/water_bucket.png"))).convert("RGBA")
    out = water.copy()
    lum = [sum(water.getpixel((x, y))[:3]) / 3 for y in range(16) for x in range(16)
           if water.getpixel((x, y)) != bucket.getpixel((x, y))]
    top = max(lum)
    for y in range(16):
        for x in range(16):
            p = water.getpixel((x, y))
            if p != bucket.getpixel((x, y)) and p[3] > 0:
                k = 0.72 + 0.28 * (sum(p[:3]) / 3) / top   # keep the water's shading, lifted toward the tint
                out.putpixel((x, y), tuple(min(255, round(c * k)) for c in TINT) + (255,))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    out.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main(sys.argv[1])
