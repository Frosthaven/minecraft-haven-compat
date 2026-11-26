"""Aged copper textures for Create: Oxidation (createoxidized), for blocks it does not cover.

Run: uv run --with pillow python -I tools/oxidation/age_textures.py <assets root> <createoxidized jar dir> <out dir> <texture refs file>

<assets root>: extracted `assets/` of the pack's jars (assets/<ns>/textures/...). <createoxidized jar dir>: the extracted
Create: Oxidation jar. <texture refs file>: one `<ns>:block/<path>` per line.

Only copper pixels change. The colour table comes from Create: Oxidation's own 104 hand-made pairs (an original Create
texture and its exposed/weathered/oxidized version): for every copper pixel of a pair, original colour -> the aged colours
it became per stage, with how often (a pixel the hand-made stage left unchanged counts too: the copper flecks a weathered
stage keeps). A pixel of a new texture is aged only when its colour is in that table, exactly or within TOLERANCE (RGB
distance) of a table colour; it then takes that colour's aged colour, or, inside the stage's flecks, the earlier stage's
colour, part of the way toward the new one (as far as the hand-made flecks sit, measured). Flecks are patches of a smooth pattern stretched along the diagonal (as the hand-made patina runs), sized to the
hand-made share per stage and fixed per texture, never single scattered pixels. Every other pixel is copied unchanged, alpha
included. Each output is checked against its input: a changed pixel whose input colour is not a copper colour fails the
texture (nothing is written for it).

Writes <out>/assets/createoxidized/textures/block/<stage>/compat/<ns>/<path>.png (the path Create: Oxidation's
QuadRemapper looks up for a non-`create` texture), copies a `.png.mcmeta` beside it, and prints one line per texture:
copper pixels changed / all pixels.
"""
import json, os, sys
from collections import Counter, defaultdict
from PIL import Image

STAGES = ("exposed", "weathered", "oxidized")
TOLERANCE = 10.0


def rgba(path):
    return Image.open(path).convert("RGBA")


def is_copper(c):
    """copper's reddish orange: hue 2 to 24 degrees, saturation 0.45 to 0.75 (Create's and vanilla's copper shades all
    fall there; brass is hue 26 to 50, greys under 0.3 saturation, redstone red at full saturation, maroons over 340)"""
    import colorsys
    h, sat, _ = colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)
    return 2 <= h * 360 <= 24 and 0.45 <= sat <= 0.75


# Create's metals that are not copper: any colour in them is never aged, though some of copper's shades share its hue
OTHER_METALS = [f"create:block/{n}" for n in (
    "brass_block", "brass_casing", "brass_casing_connected", "brass_gearbox", "brass_belt_cover", "brass_door_side",
    "brass_door_top", "brass_door_bottom", "brass_encased_cogwheel_side", "andesite_block", "andesite_casing",
    "andesite_casing_connected", "andesite_belt_cover", "zinc_block", "railway_casing", "railway_casing_side",
    "industrial_iron_block", "industrial_iron_block_top", "weathered_iron_block", "refined_radiance_casing",
    "shadow_steel_casing", "cardboard_block_side")] + ["create:item/brass_ingot", "create:item/brass_sheet", "create:item/brass_nugget"]


def other_metals(assets):
    cols = set()
    for ref in OTHER_METALS:
        ns, path = ref.split(":", 1)
        f = f"{assets}/{ns}/textures/{path}.png"
        if os.path.exists(f):
            cols.update(p[:3] for p in rgba(f).getdata() if p[3] > 0)
    return cols


def build_table(assets, ox):
    """original copper colour -> {stage: aged colour} (each stage's most common change), and per stage the share of
    copper pixels the hand-made textures left at the earlier stage's colour (the flecks)"""
    votes = defaultdict(lambda: defaultdict(Counter))
    base = f"{ox}/assets/createoxidized/textures/block"
    for stage in STAGES:
        root = f"{base}/{stage}"
        for dirpath, _, files in os.walk(root):
            for f in files:
                if not f.endswith(".png"):
                    continue
                rel = os.path.relpath(os.path.join(dirpath, f), root)[:-4]
                if rel.startswith("compat/"):
                    ns, path = rel.split("/", 2)[1], rel.split("/", 2)[2]
                else:
                    ns, path = "create", rel
                orig = f"{assets}/{ns}/textures/block/{path}.png"
                if not os.path.exists(orig):
                    continue
                a, b = rgba(orig), rgba(os.path.join(dirpath, f))
                if a.size != b.size:
                    continue
                for p, q in zip(a.getdata(), b.getdata()):
                    if p[3] > 0:
                        votes[p[:3]][stage][q[:3]] += 1
    not_copper = other_metals(assets)
    NOT_COPPER.clear()
    NOT_COPPER.update(not_copper)
    table = {}
    for col, per in votes.items():
        if not all(per[s] for s in STAGES):
            continue
        if not is_copper(col) or col in not_copper:
            continue  # brass, grey, redstone red, maroon: the hand-made pairs tint some of them, they are not copper
        changed = {s: [c for c, _ in per[s].most_common() if c != col] for s in STAGES}
        if not any(changed[s] for s in STAGES):
            continue  # a copper shade every stage leaves alone
        table[col] = {s: (changed[s][0] if changed[s] else col) for s in STAGES}
    flecks, blend = {}, {}
    for i, s in enumerate(STAGES):
        kept = total = 0
        tsum = 0.0
        for col, per in votes.items():
            if col not in table:
                continue
            earlier = col if i == 0 else table[col][STAGES[i - 1]]
            now = table[col][s]
            for c, n in per[s].items():
                total += n
                if _dist(c, earlier) < _dist(c, now):  # nearer the earlier stage's colour: a fleck
                    kept += n
                    seg = [now[j] - earlier[j] for j in range(3)]
                    ll = sum(v * v for v in seg)
                    if ll:
                        t = sum((c[j] - earlier[j]) * seg[j] for j in range(3)) / ll
                        tsum += max(0.0, min(1.0, t)) * n
        flecks[s] = kept / total if total else 0.0
        blend[s] = tsum / kept if kept else 0.0  # how far a hand-made fleck sits from the earlier colour toward the new one
    import colorsys
    CLEAN_KEYS.clear()
    for col, st in table.items():
        h, sat, _ = colorsys.rgb_to_hsv(*(v / 255 for v in st["oxidized"]))
        if 130 <= h * 360 <= 190 and sat >= 0.25:
            CLEAN_KEYS.append(col)
    flecks["oxidized"] = 0.0  # fully oxidized copper is even green in game: no weathered flecks left (Shane, 2026-11-25)
    return table, flecks, blend


def _dist(a, b):
    return ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2) ** 0.5


def _hash(ix, iy, seed):
    h = (ix * 374761393 + iy * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return (h ^ (h >> 16)) / 0xFFFFFFFF


def _smooth(u, v, seed):
    """value noise, bilinear between lattice points"""
    iu, iv = int(u // 1), int(v // 1)
    fu, fv = u - iu, v - iv
    fu, fv = fu * fu * (3 - 2 * fu), fv * fv * (3 - 2 * fv)
    a, b = _hash(iu, iv, seed), _hash(iu + 1, iv, seed)
    c, d = _hash(iu, iv + 1, seed), _hash(iu + 1, iv + 1, seed)
    return (a + (b - a) * fu) + ((c + (d - c) * fu) - (a + (b - a) * fu)) * fv


def streaks(x, y, w, seed):
    """patches stretched along the diagonal, as the hand-made patina runs: slow along x+y, quicker across it"""
    s = w / 16.0
    u, v = (x + y) / (7.0 * s), (x - y) / (2.5 * s)
    return 0.65 * _smooth(u, v, seed) + 0.35 * _smooth(u * 2.1, v * 2.1, seed + 7)


NOT_COPPER = set()   # filled by build_table: the other metals' colours


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def copper_like(c):
    """a copper pixel of any mod's shade: copper's reddish orange (hue 2 to 26 degrees, saturation 0.35 to 0.8, not
    nearly black), and no colour of Create's brass, andesite, zinc or iron textures"""
    import colorsys
    if c in NOT_COPPER:
        return False
    h, sat, v = colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)
    return 2 <= h * 360 <= 26 and 0.35 <= sat <= 0.8 and v >= 0.18


def nearest(table, keys, col):
    """the table's copper shade to age `col` like: itself, or for another mod's shade the Create copper shade nearest
    in brightness (the aged colour is then scaled to `col`'s own brightness, so its shading stays)"""
    if col in table:
        return col
    if not copper_like(col):
        return None
    return by_lum(keys, col)


GROW_STEP = 70.0   # the largest colour change between neighbouring pixels a copper area grows across
GROW_DEPTH = 2     # pixels a copper area grows from a clearly copper pixel: a part's highlights and shadows, not a painted panel
WIDE = False       # --wide (textures Shane sent back, 2026-11-25): the whole connected copper part, burgundy to tan centres included


def copper_family(c):
    """wider than copper_like: copper's pale highlights and dark red shadows too (hue 0 to 34 or 345 to 360,
    saturation from 0.15), never a colour of Create's other metals; only reached by growing from copper (copper_area)"""
    import colorsys
    if c in NOT_COPPER:
        return False
    h, sat, v = colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)
    h *= 360
    if WIDE:   # copper's whole interior: burgundy through brown to tan, and its palest yellow highlights
        return (h <= 45 or h >= 340 or (h <= 55 and v >= 0.9)) and sat >= 0.15 and v >= 0.15
    return (h <= 34 or h >= 345) and sat >= 0.15 and v >= 0.15


def _sat(c):
    import colorsys
    return colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)[1]


CLEAN_KEYS = []   # table shades whose oxidized colour is a real patina green/teal (filled by build_table)


def by_lum(keys, c):
    """the copper shade nearest `c` in brightness, among the shades that end green when oxidized (some hand-made dark
    shades stay brownish: aging another mod's shade by them left it brown)"""
    pool = CLEAN_KEYS or keys
    l = lum(c)
    return min(pool, key=lambda k: abs(lum(k) - l))


def copper_area(img, table, keys, cache):
    """the copper pixels of a texture: the clearly copper ones (seeds), grown to touching pixels of copper's wider family
    while each step is a small colour change. Highlights and shadows on a copper part are reached; paint, gold or brass
    in parts of their own are not"""
    src = img.load()
    w, h = img.size
    area = {}
    todo = []
    for x in range(w):
        for y in range(h):
            p = src[x, y]
            if p[3] == 0:
                continue
            k = cache.get(p[:3], "?")
            if k == "?":
                k = cache[p[:3]] = nearest(table, keys, p[:3])
            if k is None and WIDE and copper_family(p[:3]) and _sat(p[:3]) >= 0.35:
                k = by_lum(keys, p[:3])   # a copper part of its own (a burgundy bar): a seed too
            elif k is not None and WIDE and CLEAN_KEYS and k not in CLEAN_KEYS:
                k = by_lum(keys, p[:3])   # a shade the hand-made textures leave brown when oxidized
            if k is not None:
                area[(x, y)] = k
                todo.append((x, y, 0))
    while todo:
        x, y, depth = todo.pop(0)
        if depth >= GROW_DEPTH and not WIDE:
            continue
        c = src[x, y][:3]
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if not (0 <= nx < w and 0 <= ny < h) or (nx, ny) in area:
                continue
            q = src[nx, ny]
            if q[3] == 0 or not copper_family(q[:3]) or _dist(c, q[:3]) > (110.0 if WIDE else GROW_STEP):
                continue
            area[(nx, ny)] = by_lum(keys, q[:3])
            todo.append((nx, ny, depth + 1))
    return area


def mask_source(ref):
    """a texture whose copper sits in the same pixels as another's, where paint makes colour useless: Steam 'n' Rails'
    palettes (copper wrapping on 17 paints; orange, brown and red paint are copper's own colour) take the black
    palette's copper pixels"""
    import re
    m = re.match(r"railways:block/palettes/([a-z_]+)/(.+)$", ref)
    if m and m.group(1) != "black":
        return f"railways:block/palettes/black/{m.group(2)}"
    return None


def age(img, table, keys, flecks, blend, stage, cache, seed, mask=None):
    """each copper pixel takes its colour's aged colour; the pixels in the strongest patches of the streak pattern (the
    stage's fleck share of the copper pixels) keep the earlier stage's colour instead. The same pattern for every stage,
    so the copper patches shrink as it ages"""
    out = img.copy()
    src, dst = img.load(), out.load()
    w = img.width
    if mask is None:
        area = copper_area(img, table, keys, cache)
    else:   # the same pixels as the mask texture's copper; each takes the copper shade nearest its brightness
        area = {}
        for (x, y) in mask:
            if x < img.width and y < img.height and src[x, y][3] > 0:
                area[(x, y)] = by_lum(keys, src[x, y][:3])
    copper = [(x, y, k, streaks(x, y % w, w, seed)) for (x, y), k in area.items()]
    i = STAGES.index(stage)
    keep = set()
    if copper and flecks[stage] > 0:
        ranked = sorted(copper, key=lambda c: -c[3])
        keep = {(c[0], c[1]) for c in ranked[:int(len(ranked) * flecks[stage])]}
    for x, y, k, _ in copper:
        if (x, y) in keep:
            e = k if i == 0 else table[k][STAGES[i - 1]]
            n_ = table[k][stage]
            t = blend[stage]
            a = tuple(round(e[j] + (n_[j] - e[j]) * t) for j in range(3))
        else:
            a = table[k][stage]
        p = src[x, y]
        if p[:3] != k:   # another mod's copper shade: keep its own brightness
            f = lum(p[:3]) / max(1.0, lum(k))
            a = tuple(max(0, min(255, round(v * f))) for v in a)
        dst[x, y] = (a[0], a[1], a[2], p[3])
    return out, len(copper)


def verify(img, aged, cache):
    """alpha never changes, and every changed pixel is of copper's family (never another metal's colour)"""
    for p, q in zip(img.getdata(), aged.getdata()):
        if p[3] != q[3]:
            return False
        if p != q and not (cache.get(p[:3]) is not None or copper_family(p[:3])):
            return False
    return True


def main():
    global WIDE
    assets, ox, out, refs = sys.argv[1:5]
    WIDE = "--wide" in sys.argv[5:]
    table, flecks, blend = build_table(assets, ox)
    keys = list(table)
    print(f"colour table: {len(table)} copper colours from Create: Oxidation's pairs; fleck shares " + ", ".join(f"{s} {flecks[s]:.0%}" for s in STAGES))
    cache = {}
    results = {}
    for ref in [l.strip() for l in open(refs) if l.strip() and not l.startswith("#")]:
        if "brass" in ref:
            print(f"SKIP {ref}: brass (Shane: no textures with brass in the name)")
            continue
        ns, path = ref.split(":", 1)
        path = path[len("block/"):] if path.startswith("block/") else path
        src = f"{assets}/{ns}/textures/block/{path}.png"
        if not os.path.exists(src):
            print(f"MISSING {ref}")
            results[ref] = "missing"
            continue
        img = rgba(src)
        line = []
        ok = True
        mask = None
        ms = mask_source(ref)
        if ms:
            mns, mpath = ms.split(":", 1)
            mf = f"{assets}/{mns}/textures/{mpath}.png"
            if os.path.exists(mf):
                mimg = rgba(mf)
                if mimg.size == img.size:
                    mask = set(copper_area(mimg, table, keys, cache))
        for stage in STAGES:
            aged, n = age(img, table, keys, flecks, blend, stage, cache, sum(map(ord, ref)), mask)
            if not (verify(img, aged, cache) if mask is None else all(p[3] == q[3] for p, q in zip(img.getdata(), aged.getdata()))):
                ok = False
                break
            dest = f"{out}/assets/createoxidized/textures/block/{stage}/compat/{ns}/{path}.png"
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            aged.save(dest)
            if os.path.exists(src + ".mcmeta"):
                open(dest + ".mcmeta", "w").write(open(src + ".mcmeta").read())
            line.append(n)
        total = sum(1 for p in img.getdata() if p[3] > 0)
        results[ref] = {"copper": line[0] if line else 0, "pixels": total, "ok": ok}
        print(f"{'OK  ' if ok else 'FAIL'} {ref}: {line[0] if line else 0} of {total} pixels are copper")
    json.dump(results, open(f"{out}/report.json", "w"), indent=1)


if __name__ == "__main__":
    main()
