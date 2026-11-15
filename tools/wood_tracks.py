#!/usr/bin/env python3
"""Generate the resources of the pale oak and poplar train tracks (tracks/WoodTracks):

    uv run --with pillow python -I tools/wood_tracks.py <Steam 'n' Rails jar> <Minecraft 1.21.1 client jar> \
        <haven-backports src/main/resources>

Steam 'n' Rails makes tracks for other mods' woods from one pattern (its Biomes O' Plenty fir is the template here):
blockstates, models, loot tables and sequenced assembly recipes are that wood's files with the ids changed. The textures
are its birch track textures with the wood recoloured: a pixel is wood where the birch and cherry textures differ, and
it keeps its light and shade relative to vanilla's birch planks, moved to the new wood's planks. Tags are merged into
the files the mod already has.
"""
import io
import json
import re
import sys
import zipfile
from pathlib import Path

from PIL import Image

WOODS_MOD = "haven_backports"
WOODS = {"pale_oak": "Pale Oak", "poplar": "Poplar"}
GAUGES = {"": "", "_narrow": "Narrow ", "_wide": "Wide "}
RES = Path(__file__).resolve().parent.parent / "src/main/resources"
T_MOD, T_WOOD = "biomesoplenty", "fir"   # the template wood
BLOCK_TEXTURES = ["standard_track", "standard_track_mip", "standard_track_crossing", "portal_track", "portal_track_mip"]
CONDITIONS = [{"type": "neoforge:mod_loaded", "modid": "railways"}, {"type": "neoforge:mod_loaded", "modid": WOODS_MOD}]
BLOCK_TAGS = ["minecraft:mineable/pickaxe", "create:tracks", "create:girdable_tracks", "c:relocation_not_supported",
              "railways:internal/relocation_not_supported"]


def image(z, path):
    return Image.open(io.BytesIO(z.read(path))).convert("RGBA")


def mean(pixels):
    pixels = list(pixels)
    return [sum(p[c] for p in pixels) / len(pixels) for c in range(3)]


def recolour(birch, cherry, from_wood, to_wood, cherry_wood=None, standard=None):
    """`birch` with its wood pixels (those that differ from `cherry`) moved from `from_wood`, the mean colour of
    vanilla's birch planks, to `to_wood`. A portal texture (`cherry_wood` given) is wood under a purple glow: a
    pixel's wood share is how far the cherry texture moved it, as a part of the move from birch to cherry planks
    (the mean over the pixel's row: the glow changes from row to row, and the two drawings differ pixel by pixel).
    Where a portal texture repeats `standard` (the birch texture without glow, and its recoloured copy), it takes
    the copy's pixel."""
    out = birch.copy()
    wood = [(x, y) for y in range(birch.height) for x in range(birch.width)
            if birch.getpixel((x, y)) != cherry.getpixel((x, y)) and birch.getpixel((x, y))[3] > 0]
    share = {}
    if cherry_wood:
        whole = [c - b for b, c in zip(from_wood, cherry_wood)]
        length = sum(w * w for w in whole)
        moved = {p: sum((cherry.getpixel(p)[i] - birch.getpixel(p)[i]) * whole[i] for i in range(3)) / length for p in wood}
        for y in {y for _, y in wood}:
            row = [moved[p] for p in wood if p[1] == y]
            share[y] = max(0.0, min(1.0, sum(row) / len(row)))
    for p in wood:
        b = birch.getpixel(p)
        if standard and standard[0].getpixel(p) == b:
            out.putpixel(p, standard[1].getpixel(p))
            continue
        if cherry_wood:
            rgb = [b[i] + share[p[1]] * (to_wood[i] - from_wood[i]) for i in range(3)]
        else:
            rgb = [b[i] * to_wood[i] / from_wood[i] for i in range(3)]
        out.putpixel(p, tuple(max(0, min(255, round(v))) for v in rgb) + (b[3],))
    return out


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text if isinstance(text, str) else json.dumps(text, indent=2) + "\n")


def main(railways_jar, client_jar, backports):
    with zipfile.ZipFile(client_jar) as z:
        birch_planks, cherry_planks = (mean(image(z, f"assets/minecraft/textures/block/{w}_planks.png").getdata())
                                       for w in ("birch", "cherry"))
    rw = zipfile.ZipFile(railways_jar)
    lang, tags = {}, {tag: [] for tag in BLOCK_TAGS}
    for wood, name in WOODS.items():
        planks = mean(Image.open(Path(backports) / f"assets/{WOODS_MOD}/textures/block/{wood}_planks.png")
                      .convert("RGBA").getdata())
        textures = [f"block/track/{{w}}/{t}_{{w}}" for t in BLOCK_TEXTURES]
        textures += [f"item/{kind}/{kind}_{{w}}{g}" for kind in ("track", "track_incomplete") for g in GAUGES]
        done = {}
        for path in textures:
            src = "assets/railways/textures/" + path + ".png"
            birch = image(rw, src.format(w="birch"))
            glow = "portal" in path   # portal_track(_mip) repeats parts of standard_track(_mip), made before it
            out = recolour(birch, image(rw, src.format(w="cherry")), birch_planks, planks,
                           cherry_planks if glow else None, done.get(path.replace("portal", "standard")))
            done[path] = (birch, out)
            target = RES / "assets/haven_compat/textures" / (path.format(w=wood) + ".png")
            target.parent.mkdir(parents=True, exist_ok=True)
            out.save(target)
        for gauge, prefix in GAUGES.items():
            block = f"track_{WOODS_MOD}_{wood}{gauge}"
            incomplete = f"track_incomplete_{WOODS_MOD}_{wood}{gauge}"
            template = f"{T_MOD}_{T_WOOD}{gauge}"
            lang[f"block.railways.{block}"] = f"{prefix}{name} Train Track"
            lang[f"item.railways.{incomplete}"] = f"Incomplete {prefix}{name} Track"
            for tag in tags.values():
                tag.append({"id": f"railways:{block}", "required": False})
            models = f"assets/railways/models/block/track/compat/{T_MOD}/{T_WOOD}{gauge}/"
            for entry in rw.namelist():
                if entry.startswith(models) and entry.endswith(".json"):
                    text = re.sub(rf'{T_MOD}:block/track/{T_WOOD}/(\w+)_{T_WOOD}"',
                                  rf'haven_compat:block/track/{wood}/\1_{wood}"', rw.read(entry).decode())
                    assert T_MOD not in text, entry
                    write(RES / f"assets/haven_compat/models/block/track/{wood}{gauge}" / entry[len(models):], text)
            text = rw.read(f"assets/railways/blockstates/track_{template}.json").decode()
            text = text.replace(f"railways:block/track/compat/{T_MOD}/{T_WOOD}", f"haven_compat:block/track/{wood}")
            assert T_MOD not in text
            write(RES / f"assets/railways/blockstates/{block}.json", text)
            for item, kind in ((block, "track"), (incomplete, "track_incomplete")):
                write(RES / f"assets/railways/models/item/{item}.json", {
                    "parent": "minecraft:item/generated",
                    "textures": {"layer0": f"haven_compat:item/{kind}/{kind}_{wood}{gauge}"}})
            write(RES / f"data/railways/loot_table/blocks/{block}.json", {
                "neoforge:conditions": CONDITIONS, "type": "minecraft:block", "random_sequence": f"railways:blocks/{block}",
                "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                           "entries": [{"type": "minecraft:item", "name": f"railways:{block}"}]}]})
            text = rw.read(f"data/railways/recipe/sequenced_assembly/track_{template}.json").decode()
            text = text.replace(f"{T_MOD}_{T_WOOD}", f"{WOODS_MOD}_{wood}")
            recipe = json.loads(text.replace(f'"tag": "railways:compat_slabs/{T_MOD}/{T_WOOD}"',
                                             f'"item": "{WOODS_MOD}:{wood}_slab"'))
            recipe["neoforge:conditions"] = CONDITIONS
            assert T_MOD not in json.dumps(recipe)
            write(RES / f"data/railways/recipe/sequenced_assembly/{block}.json", recipe)
    write(RES / "assets/railways/lang/en_us.json", lang)
    for tag, values in tags.items():
        namespace, path = tag.split(":")
        file = RES / f"data/{namespace}/tags/block/{path}.json"
        ours = {v["id"] for v in values}
        kept = [v for v in (json.loads(file.read_text())["values"] if file.exists() else [])
                if (v["id"] if isinstance(v, dict) else v) not in ours]
        write(file, {"values": kept + values})


if __name__ == "__main__":
    main(*sys.argv[1:])
