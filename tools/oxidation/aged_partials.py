"""Aged copies of moving-part models (Flywheel partial models) for Create: Oxidation, for blocks it does not cover.

Run: python3 -I tools/oxidation/aged_partials.py <assets root> <out resources dir>

<assets root>: extracted `assets/` of the pack's jars. Reads src/main/resources/haven_compat/oxidation/partials.txt (one
`<ns>:block/<path>` model per line) and textures.txt (the copper textures with aged versions). For each partial and stage
writes <out>/assets/haven_compat/models/block/oxidation/<stage>/<ns>/<path>.json: the original model's parent and
elements, with every listed copper texture replaced by its aged version (createoxidized:block/<stage>/compat/<ns>/<path>).
A partial with no listed texture fails (nothing would age).
"""
import json, os, sys

STAGES = ("exposed", "weathered", "oxidized")
HERE = os.path.dirname(os.path.abspath(__file__))
LISTS = os.path.join(HERE, "..", "..", "src", "main", "resources", "haven_compat", "oxidation")


def lines(name):
    with open(os.path.join(LISTS, name), encoding="utf-8") as f:
        return [l.strip() for l in f if l.strip() and not l.startswith("#")]


def main(assets, out):
    textures = set(lines("textures.txt"))
    failed = 0
    for ref in lines("partials.txt"):
        ns, path = ref.split(":", 1)
        with open(os.path.join(assets, ns, "models", path + ".json"), encoding="utf-8") as f:
            model = json.load(f)
        tex = model.get("textures", {})
        aged = [k for k, v in tex.items() if (v if ":" in v else "minecraft:" + v) in textures]
        if not aged:
            print(f"FAIL {ref}: no copper texture with an aged version"); failed += 1; continue
        for stage in STAGES:
            copy = dict(model)
            copy["textures"] = {k: (f"createoxidized:block/{stage}/compat/{v.split(':', 1)[0]}/{v.split(':', 1)[1][len('block/'):]}"
                                    if k in aged else v) for k, v in tex.items()}
            dest = os.path.join(out, "assets", "haven_compat", "models", "block", "oxidation", stage, ns, path[len("block/"):] + ".json")
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with open(dest, "w", encoding="utf-8") as f:
                json.dump(copy, f, indent=2); f.write("\n")
        print(f"{ref}: {', '.join(aged)} aged")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
