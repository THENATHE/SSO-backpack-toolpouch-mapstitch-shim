# Native Shift-use Mending regression

Uses the supplied native client mods and complete server mods/shims/addon stack, with fresh Minecraft JVMs and a disposable flat world. Does not load or modify a user world. Production JARs are hashed before and after each run.

The server seeds controlled Survival item fixtures (Mending diamond pickaxe, optionally Efficiency I and Unbreaking I, enchanted whetstone with matching `STORED_ENCHANTMENTS`, and 64 diamonds). The client activates its Shift key and invokes the normal right-click-in-air `gameMode.useItem` path, including the original native Fabric callback and prediction. It never directly invokes a server repair helper or bypasses the client with a fabricated use packet.

The default run covers ordinary and three-enchantment repairs, no Shift, no material, no Mending, incompatible whetstone, a later compatible whetstone, three same-tick uses, a bow subclass, and an explicitly seeded cooldown. Exact final damage/material/repair count and enchantments are checked; client/server inventory snapshots are compared. Stone wear is randomized by the original recipe and is recorded, not forced. XP was not independently asserted.

Default `autoRepairOnBreak=true` repairs explicitly broken fixtures before manual use. Those two cases represent automatic repair followed by a manual click. `--isolate-broken` disables only automatic repair in both disposable processes and runs the ordinary positive control and two explicitly `BROKEN=true` items. This isolates the native broken-item callback. `--original-only` removes all server Polymer and shims, retaining the original mods and addon, to attribute behavior.

```
DISPLAY=:1 python3 qa/regression/mending-use/run.py --shim build/libs/SSO-backpack-toolpouch-mapstitch-shim-1.0.1+26.3.jar
python3 qa/regression/mending-use/export_evidence.py native-defaults-1.0.1
DISPLAY=:1 python3 qa/regression/mending-use/run.py --shim build/libs/SSO-backpack-toolpouch-mapstitch-shim-1.0.1+26.3.jar --isolate-broken
python3 qa/regression/mending-use/export_evidence.py native-manual-broken-1.0.1
DISPLAY=:1 python3 qa/regression/mending-use/run.py --shim build/libs/SSO-backpack-toolpouch-mapstitch-shim-1.0.1+26.3.jar --isolate-broken --original-only
python3 qa/regression/mending-use/export_evidence.py original-manual-broken
```

The run completion marker means the observation fixture completed, not that every gameplay expectation passed. Exported evidence includes per-case verdicts. The packet handler counter sees both network dispatch and server rescheduling: two invocations correspond to one packet; zero indicates none arrived. Ignored launcher audit files can contain local credentials and must never be published. Export only sanitized evidence.
