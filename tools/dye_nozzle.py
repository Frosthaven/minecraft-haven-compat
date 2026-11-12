#!/usr/bin/env python3
"""Generate the Brass Dye Nozzle assets and data (dyeing/): run from the repo root, `python3 tools/dye_nozzle.py`.

The Controlled Fan Dyeing Catalyst is Create Connected's fan dyeing catalyst (its `fan_catalyst/with_content` model,
copied below as BASE) with a brass nozzle on each face. Each face's nozzle cap shows the crafting grid the fan builds:
an 8 px nozzle, a 1 px brass rim, then cells 1 | 4 | 1; lit cells (the dye's slots) in the dye colour, the 4x4 centre
(the item's cell, where Create's value box prints the position letter) at 20 % of it. The core panels' outer faces are
cut around the nozzle so no two faces share a plane (they flickered). Standard library only (PNG written by hand).
"""
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
NS = "haven_compat"
COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
          "blue", "brown", "green", "red", "black"]
# Average colour of each `<color>_concrete_powder` texture: the catalyst's content and the nozzle's lit cells.
DYE = {"white": (226, 227, 228), "orange": (227, 132, 32), "magenta": (193, 84, 185), "light_blue": (74, 181, 213),
       "yellow": (233, 199, 55), "lime": (125, 189, 42), "pink": (229, 153, 181), "gray": (77, 81, 85),
       "light_gray": (155, 155, 148), "cyan": (37, 148, 157), "purple": (132, 56, 178), "blue": (70, 73, 167),
       "brown": (126, 85, 54), "green": (97, 119, 45), "red": (168, 54, 51), "black": (25, 27, 32)}
RIM = (206, 160, 90)        # create:block/brass_block's edge
RECESS = (38, 28, 22)
OFF = (58, 44, 34)          # a cell without dye
CENTRE = 0.20               # the item's cell: this share of the dye colour
POSITIONS = {"left": [(0, 1)], "right": [(2, 1)], "top": [(1, 0)], "bottom": [(1, 2)],
             "surround": [(c, r) for c in range(3) for r in range(3) if (c, r) != (1, 1)]}
LO, HI = 4, 12              # the nozzle's cross-section, in model pixels
SIDES = ["north", "east", "south", "west", "up", "down"]
OPP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}

# create_connected:block/fan_catalyst/with_content (Create Connected 1.3.3): brass frame, six core panels, content cube.
BASE = [
    {"name": "frame", "from": [0, 0, 0], "to": [16, 16, 16], "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#0"} for f in SIDES}},
    {"name": "side", "from": [2, 2, 2], "to": [14, 14, 0], "faces": {
        "north": {"uv": [2, 2, 14, 14], "texture": "#1"}, "east": {"uv": [0, 2, 2, 14], "rotation": 180, "texture": "#1"},
        "west": {"uv": [14, 2, 16, 14], "rotation": 180, "texture": "#1"}, "up": {"uv": [2, 2, 14, 0], "rotation": 180, "texture": "#1"},
        "down": {"uv": [2, 14, 14, 16], "texture": "#1"}}},
    {"name": "side", "from": [2, 2, 16], "to": [14, 14, 14], "faces": {
        "east": {"uv": [14, 2, 16, 14], "rotation": 180, "texture": "#1"}, "south": {"uv": [2, 2, 14, 14], "texture": "#1"},
        "west": {"uv": [0, 2, 2, 14], "rotation": 180, "texture": "#1"}, "up": {"uv": [2, 0, 14, 2], "rotation": 180, "texture": "#1"},
        "down": {"uv": [2, 14, 14, 16], "rotation": 180, "texture": "#1"}}},
    {"name": "side", "from": [16, 2, 2], "to": [14, 14, 14], "faces": {
        "north": {"uv": [14, 2, 16, 14], "rotation": 180, "texture": "#1"}, "east": {"uv": [2, 2, 14, 14], "texture": "#1"},
        "south": {"uv": [0, 2, 2, 14], "rotation": 180, "texture": "#1"}, "up": {"uv": [2, 0, 14, 2], "rotation": 90, "texture": "#1"},
        "down": {"uv": [2, 14, 14, 16], "rotation": 270, "texture": "#1"}}},
    {"name": "side", "from": [2, 2, 2], "to": [0, 14, 14], "faces": {
        "north": {"uv": [0, 2, 2, 14], "rotation": 180, "texture": "#1"}, "south": {"uv": [14, 2, 16, 14], "rotation": 180, "texture": "#1"},
        "west": {"uv": [2, 2, 14, 14], "texture": "#1"}, "up": {"uv": [2, 2, 14, 0], "rotation": 90, "texture": "#1"},
        "down": {"uv": [2, 14, 14, 16], "rotation": 90, "texture": "#1"}}},
    {"name": "side", "from": [2, 16, 2], "to": [14, 14, 14], "faces": {
        "north": {"uv": [2, 0, 14, 2], "texture": "#1"}, "east": {"uv": [14, 2, 16, 14], "rotation": 270, "texture": "#1"},
        "south": {"uv": [2, 14, 14, 16], "rotation": 180, "texture": "#1"}, "west": {"uv": [0, 2, 2, 14], "rotation": 90, "texture": "#1"},
        "up": {"uv": [2, 2, 14, 14], "texture": "#1"}}},
    {"name": "side", "from": [2, 2, 2], "to": [14, 0, 14], "faces": {
        "north": {"uv": [2, 14, 14, 16], "rotation": 180, "texture": "#1"}, "east": {"uv": [14, 2, 16, 14], "rotation": 90, "texture": "#1"},
        "south": {"uv": [2, 0, 14, 2], "texture": "#1"}, "west": {"uv": [0, 2, 2, 14], "rotation": 270, "texture": "#1"},
        "down": {"uv": [2, 2, 14, 14], "texture": "#1"}}},
    {"from": [2.1, 2.1, 2.1], "to": [13.9, 13.9, 13.9], "faces": {f: {"texture": "#content"} for f in SIDES}},
]


def png(path, pixels):
    """16x16 RGBA PNG; pixels[y][x] = (r, g, b, a)."""
    raw = b"".join(b"\0" + bytes(c for px in row for c in px) for row in pixels)
    chunk = lambda kind, data: struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def cap(dye, lit):
    """The nozzle's end: rim, then the 1 | 4 | 1 grid. dye None = the item icon's neutral cap."""
    px = [[RECESS + (255,) for _ in range(16)] for _ in range(16)]
    lo, hi = LO, HI - 1
    for i in range(lo, hi + 1):
        for x, y in ((i, lo), (lo, i), (i, hi), (hi, i)):
            px[y][x] = RIM + (255,)
    span = [(lo + 1, 1), (lo + 2, 4), (lo + 6, 1)]
    for c in range(3):
        for r in range(3):
            if (c, r) == (1, 1):
                col = tuple(round(v * CENTRE) for v in dye) if dye else (30, 24, 20)
            else:
                col = dye if dye and (c, r) in lit else OFF
            for dx in range(span[c][1]):
                for dy in range(span[r][1]):
                    px[span[r][0] + dy][span[c][0] + dx] = col + (255,)
    return px


def corners(face, a, b):
    """tl, tr, br, bl of a face as seen from outside (Minecraft's UV orientation)."""
    x0, y0, z0 = a
    x1, y1, z1 = b
    return {"north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
            "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
            "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
            "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
            "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]}[face]


def cut_panels(elements):
    """The core panels' main faces lose the square the nozzle covers: up to 4 flat strips. Minecraft puts a north, west or
    down face at the element's `from` and a south, east or up face at its `to`, unsorted; the panels are written
    from 2 to 0, so their faces sit at 2 / 14 (inside the frame), and the strips must stay on that same plane."""
    out = []
    normal = {"north": (2, 0), "south": (2, 16), "west": (0, 0), "east": (0, 16), "down": (1, 0), "up": (1, 16)}
    for e in elements:
        a = [min(e["from"][i], e["to"][i]) for i in range(3)]
        b = [max(e["from"][i], e["to"][i]) for i in range(3)]
        faces = dict(e["faces"])
        extra = []
        for face, f in e["faces"].items():
            axis, bound = normal[face]
            plane = e["from"][axis] if bound == 0 else e["to"][axis]
            outer = a[axis] if bound == 0 else b[axis]
            if outer != bound or f["texture"] != "#1" or f.get("rotation"):
                continue
            i, j = [k for k in range(3) if k != axis]
            if not (a[i] <= LO and b[i] >= HI and a[j] <= LO and b[j] >= HI):
                continue
            del faces[face]
            tl, tr, _, bl = corners(face, a, b)
            s_ax = next(k for k in range(3) if tr[k] != tl[k])
            t_ax = next(k for k in range(3) if bl[k] != tl[k])
            u0, v0, u1, v1 = f["uv"]
            for ri, rj in (((a[i], LO), (a[j], b[j])), ((HI, b[i]), (a[j], b[j])), ((LO, HI), (a[j], LO)), ((LO, HI), (HI, b[j]))):
                if ri[1] <= ri[0] or rj[1] <= rj[0]:
                    continue
                fr, to = [0, 0, 0], [0, 0, 0]
                fr[axis] = to[axis] = plane
                fr[i], to[i], fr[j], to[j] = ri[0], ri[1], rj[0], rj[1]
                c = corners(face, fr, to)
                s0 = (c[0][s_ax] - tl[s_ax]) / (tr[s_ax] - tl[s_ax])
                s1 = (c[1][s_ax] - tl[s_ax]) / (tr[s_ax] - tl[s_ax])
                t0 = (c[0][t_ax] - tl[t_ax]) / (bl[t_ax] - tl[t_ax])
                t1 = (c[3][t_ax] - tl[t_ax]) / (bl[t_ax] - tl[t_ax])
                uv = [u0 + (u1 - u0) * s0, v0 + (v1 - v0) * t0, u0 + (u1 - u0) * s1, v0 + (v1 - v0) * t1]
                extra.append({"from": fr, "to": to, "faces": {face: {"uv": [round(v, 4) for v in uv], "texture": "#1"}}})
        out.append({**e, "faces": faces})
        out.extend(extra)
    return out


def nozzle_elements():
    """One brass tube per face, from the inner block (2.1 / 13.9) out to the block bound, capped with #cap."""
    els = []
    for face in SIDES:
        box = {"north": ([LO, LO, 0], [HI, HI, 2.1]), "south": ([LO, LO, 13.9], [HI, HI, 16]),
               "west": ([0, LO, LO], [2.1, HI, HI]), "east": ([13.9, LO, LO], [16, HI, HI]),
               "down": ([LO, 0, LO], [HI, 2.1, HI]), "up": ([LO, 13.9, LO], [HI, 16, HI])}[face]
        faces = {f: {"uv": [LO, 0, HI, 2], "texture": "#brass"} for f in SIDES if f not in (face, OPP[face])}
        faces[face] = {"uv": [LO, LO, HI, HI], "texture": "#cap"}
        els.append({"name": "nozzle_" + face, "from": box[0], "to": box[1], "faces": faces})
    return els


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def mod_loaded(*mods):
    return [{"type": "neoforge:mod_loaded", "modid": m} for m in mods]


def main():
    assets, data = ROOT / "assets" / NS, ROOT / "data" / NS
    elements = cut_panels(BASE) + nozzle_elements()
    lang_path = assets / "lang/en_us.json"
    lang = json.loads(lang_path.read_text())
    blocks = []
    for color in COLORS:
        block = f"controlled_{color}_fan_dyeing_catalyst"
        blocks.append(block)
        variants = {}
        for pos, lit in POSITIONS.items():
            png(assets / f"textures/block/dye_nozzle/cap_{color}_{pos}.png", cap(DYE[color], lit))
            write(assets / f"models/block/controlled_catalyst/{color}_{pos}.json", {
                "parent": "minecraft:block/block", "render_type": "minecraft:cutout_mipped",
                "textures": {"0": "create:block/funnel/brass_funnel_frame", "1": "create_connected:block/fan_catalyst_core",
                             "content": f"minecraft:block/{color}_concrete_powder", "brass": "create:block/brass_block",
                             "cap": f"{NS}:block/dye_nozzle/cap_{color}_{pos}", "particle": "create:block/funnel/brass_funnel_frame"},
                "elements": elements})
            variants[f"position={pos}"] = {"model": f"{NS}:block/controlled_catalyst/{color}_{pos}"}
        write(assets / f"blockstates/{block}.json", {"variants": variants})
        write(assets / f"models/item/{block}.json", {"parent": f"{NS}:block/controlled_catalyst/{color}_left"})
        name = " ".join(w.capitalize() for w in color.split("_"))
        lang[f"block.{NS}.{block}"] = f"Controlled {name} Fan Dyeing Catalyst"
        write(data / f"loot_table/blocks/{block}.json", {
            "type": "minecraft:block", "neoforge:conditions": mod_loaded("create_connected", "create_dragons_plus"),
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        write(data / f"recipe/dyeing/{block}.json", {
            "neoforge:conditions": mod_loaded("create_connected", "create_dragons_plus"),
            "type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [{"item": f"create_connected:{color}_fan_dyeing_catalyst"}, {"item": f"{NS}:brass_dye_nozzle"}],
            "result": {"id": f"{NS}:{block}", "count": 1}})

    png(assets / "textures/item/dye_nozzle_cap.png", cap(None, []))
    write(assets / "models/item/brass_dye_nozzle.json", {
        "parent": "minecraft:block/block",
        "textures": {"brass": "create:block/brass_block", "cap": f"{NS}:item/dye_nozzle_cap", "particle": "create:block/brass_block"},
        "elements": [
            {"from": [LO, LO, 3], "to": [HI, HI, 13], "faces": {
                "north": {"uv": [LO, LO, HI, HI], "texture": "#cap"}, "south": {"uv": [LO, LO, HI, HI], "texture": "#cap"},
                **{f: {"uv": [LO, 0, HI, 10], "texture": "#brass"} for f in ("east", "west", "up", "down")}}},
            {"from": [2, 2, 7], "to": [14, 14, 9], "faces": {f: {"uv": [2, 2, 14, 14] if f in ("north", "south") else [2, 7, 14, 9],
                                                                  "texture": "#brass"} for f in SIDES}}]})
    lang[f"item.{NS}.brass_dye_nozzle"] = "Brass Dye Nozzle"
    lang[f"item.{NS}.brass_dye_nozzle.tooltip"] = "Use on a Fan Dyeing Catalyst to choose where the fan puts the dye in the crafting grid."
    lang[f"{NS}.dyeing.position"] = "Dye Position"
    lang[f"{NS}.dyeing.change"] = "Right-click to change"
    for pos in POSITIONS:
        lang[f"{NS}.dyeing.position.{pos}"] = pos.capitalize()
    lang_path.write_text(json.dumps(dict(sorted(lang.items())), indent=2, ensure_ascii=False) + "\n")

    write(data / "recipe/dyeing/brass_dye_nozzle.json", {
        "neoforge:conditions": mod_loaded("create_connected", "create_dragons_plus"),
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["SPS", " S "],
        "key": {"S": {"tag": "c:plates/brass"}, "P": {"item": "create:fluid_pipe"}},
        "result": {"id": f"{NS}:brass_dye_nozzle", "count": 1}})
    ids = [f"{NS}:{b}" for b in blocks]
    for tag in ("data/create/tags/block/fan_transparent.json", "data/minecraft/tags/block/mineable/pickaxe.json"):
        # Merge: other blocks in the tag (the bleaching catalyst) are not this tool's to drop.
        path = ROOT / tag
        old = json.loads(path.read_text())["values"] if path.exists() else []
        keep = [v for v in old if (v["id"] if isinstance(v, dict) else v) not in ids]
        write(path, {"values": [{"id": v, "required": False} for v in ids] + keep})
    print(f"{len(blocks)} blocks, {len(blocks) * len(POSITIONS)} models and caps")


if __name__ == "__main__":
    main()
