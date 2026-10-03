# Atlas regression QA

Run from the combined shim repository with the built server shim and client/server addon:

```sh
python qa/addon/run.py --suite atlas --shim build/libs/SSO-backpack-toolpouch-mapstitch-shim-1.0.2+26.3.jar --addon ../toolpouch-atlas-elytra-compat-26.3/build/libs/toolpouch-atlas-elytra-compat-1.0.1+26.3.jar
```

The runner uses the existing local Fabric launch audits, immutable developer-release dependency bundle, accepted test-server EULA, and a working display. It opens only a local offline QA server. `--port` selects a different port. `--build-only` compiles the fixture without starting Minecraft. `--atlas-regressions-only` skips rendering and runs the server recipe/metadata regression group through the same client/server harness.

The native client has the original mods and addon, without Polymer. Fixtures check actual HUD rendering prerequisites and world-map tiles for atlases inside inventory and leggings pouches, subsequent terrain updates while the pouch stays closed, and configuration opt-outs. Seed maps deliberately lack the center component before ticking. The minimap assertion checks a **non-null** cached center; a map key associated with null does not prove it can render.

Server checks cover recipe output conservation with sixteen identical map ingredients, repeated previews, changed crafting inputs, nonexistent map data, automatic repair of old/malformed atlas metadata, preservation of names/counts/order/selection and 144-item contents, stale active map IDs, and selection inside/outside map boundaries at scales 0 through 4. Client checks also take a real inventory crafting result and verify the server and native client consume exactly one book and one map.

An actual Overworld → Nether → Overworld round trip verifies minimap cache invalidation and reconstruction. Cache invalidation applies when the client world instance or atlas components change, preventing map IDs from another world or outdated atlas metadata from being reused.

The preserved 1.0.1 server baseline reproduces the missing minimap seed center and stacked-map recipe duplication. A separate pre-cache-fix addon run reproduces retained center IDs after changing dimensions. Final evidence and hashes are recorded under `qa/evidence/`; full runtime logs/worlds remain ignored local artifacts. This is automated native rendering-path and inventory/network verification, not a claim of exhaustive manual visual coverage or arbitrary third-party-mod compatibility.
