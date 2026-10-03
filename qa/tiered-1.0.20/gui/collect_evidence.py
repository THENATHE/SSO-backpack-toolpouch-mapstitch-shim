#!/usr/bin/env python3
"""Collect screenshot artifacts and exact color counts from completed QA clients."""
import hashlib, json, shutil, sys
from pathlib import Path
from PIL import Image

here = Path(__file__).resolve().parent
run = Path(sys.argv[1]).resolve()
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
result = {"run": str(run), "status": "pending manual visual review", "pack_sha256": sha(here / "qa-vanilla-container-sprites.zip"), "screenshots": []}
result["server_artifacts"] = [{"file": path.name, "sha256": sha(path)} for path in sorted((run / "server" / "mods").glob("*.jar")) if path.name.startswith(("SSO-", "tiered_backpacks-", "toolpouch-atlas-"))]
colors = {"orange_border": (255, 130, 30), "cyan_panel": (105, 195, 230), "magenta_slot": (210, 30, 200)}
for mode in ("native", "native-polymer"):
    audit = json.loads((run / mode / "launch-audit.json").read_text())
    result.setdefault("launch_audits", {})[mode] = {key: value for key, value in audit.items() if key != "command"}
    target = here / "screenshots" / mode
    target.mkdir(parents=True, exist_ok=True)
    for tier in range(6):
        source = run / mode / "screenshots" / f"backpack-tier-{tier}.png"
        destination = target / source.name
        shutil.copy2(source, destination)
        image = Image.open(source).convert("RGB")
        counts = {name: sum(count for count, color in image.getcolors(image.width * image.height) if color == expected) for name, expected in colors.items()}
        if mode == "native-polymer":
            assert all(value > 100 for value in counts.values()), (tier, counts)
        result["screenshots"].append({"mode": mode, "tier": tier, "path": str(destination.relative_to(here)), "sha256": sha(source), "size": list(image.size), "probe_color_pixels": counts})
(here / "evidence.json").write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps({"screenshots": len(result["screenshots"]), "evidence": str(here / "evidence.json")}))
