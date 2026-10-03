# Validation — 1.0.2+26.3

Recorded 2026-10-02. Minecraft 26.3; Java 25; Fabric Loader 0.19.5; Fabric API 0.161.0+26.3; Polymer Bundled 0.18.2+26.3. Original developer JARs and the separate existing SSO port remain unchanged.

Combined shim SHA-256: `d71a659db9c2025542953839facc5263cf2f80085644608a753913d50786e7d4`.

Companion atlas/elytra addon 1.0.1 SHA-256: `77dc668b164bca182eec1e558b2948bd98ee40545ef30881bcdd471f43af1b0a`.

## Reproduced defects and fixes

| Defect | Reproduction and correction |
| --- | --- |
| Seed map present in atlas, absent from minimap | A real native client received the map packet and active atlas ID, but the minimap cached a null center. Nested maps can miss the direct-inventory metadata hook. Server atlas ticks now restore missing/incorrect centers from authoritative saved map data, preserving IDs, terrain and custom metadata. Existing atlases repair without discarding their maps. |
| Stale atlas active-map ID | Removed/nonexistent IDs could prevent reselection. Atlas ticks now invalidate selections absent from their actual contents or current dimension, allowing original selection logic to recover. Missing saved maps are preserved rather than deleted. |
| Stacked-map crafting duplication and unstable preview | The old recipe inserted all 16 seed-map copies while consuming one ingredient. Its mutable cached seed was also consumed by assembly, and stale scale/input could leak into subsequent previews. Each assembly now validates current ingredients, uses one copied seed and supplies its center; nonexistent saved-map data is rejected. |
| Duplicate Tool Pouch controls heading | The actual vanilla controls list showed two Tool Pouch headings. The addon created a different category object with the same ID; controls group by identity. Addon 1.0.1 reuses the original category and retains the key identifier. |
| Map-center cache survives world changes | Actual Nether travel retained a deliberately marked stale cache entry with the old addon. Addon 1.0.1 invalidates cached centers when the client world or atlas components change. |
| Maximum-lore pouch conversion exception | Both pouch variants with valid 256-entry lore threw `IllegalArgumentException` in the old shim after its notice became entry 257. The new bounded helper keeps every original entry and appends the notice inside the last component when full. |
| Backpack custom lore discarded | The old fallback replaced custom lore with its notice. The new fallback preserves custom lore alongside the notice. A second boundary reproduction found Polymer discarding all generated tooltip lines when 256 custom entries were combined with container/dye lines; the pre-conversion hook now folds overflow components into the final entry while preserving their text and style. Saved source stacks remain unchanged. |

## Runtime checks

| Check | Result and evidence |
| --- | --- |
| Developer optional-module matrix | All 16 subsets passed startup, datapack reload, safe fallback conversion and bounded SSO repair checks where applicable. [Exact inputs/results](qa/evidence/1.0.2/matrix-developer.json). |
| Existing SSO port matrix | All eight subsets containing SSO independently passed the same checks against SSO 2.9.14-port.1+26.3 and its isolated dependencies. [Exact inputs/results](qa/evidence/1.0.2/matrix-sso-port.json). |
| Defaulted and saved repair states | 29 projection assertions passed. Both SSO tracks passed 16 reconstructed states across direct recipes, recipe manager, 2×2 and 3×3 menus plus seven operator/recovery controls each. Ten final lore cases passed, including both pouch variants and 256 custom entries combined with generated native tooltip lines; baseline overflow and interim silent-loss reproductions are retained. [Core regression evidence](qa/evidence/1.0.2/core-audit-regressions.json). |
| Creative packets and commands | All 72 cases passed: 48 native Creative packets as operator/nonoperator and 24 actual player `/give` commands, six backpack tiers across Overworld, Nether, End and custom dimension. [Evidence](qa/evidence/1.0.2/creative.json). |
| Recipe book | Full native unlock decoded 1,078 recipe collections and remained connected. [Evidence](qa/evidence/1.0.2/recipe-network.json). |
| Storage and native/vanilla connections | 1,767 assertions passed, including six tiers, custom lore text/styles, absent/accepted/declined pack states, source preservation and exact serialization roundtrips. Actual native, native-with-Polymer and unmodified vanilla clients passed inventory/equipped storage, native menus, withdrawals/reopen, atlas coexistence and fallback guards. [Evidence](qa/evidence/1.0.2/storage-network.json). |
| Cursor under delay and reconnect | Two six-item pickup/place cycles, 24 actual click packets, 100 ms delay, same client JVM disconnect/rejoin. Item counts/identity, map ID, damage/repair count and empty cursor invariants passed. [Evidence](qa/evidence/1.0.2/cursor-rejoin-network.json). |
| Controls and elytra addon | Exact final addon passed 101 assertions with MapStitch and 101 without: controls grouping/registration, key/payload state, commands without operator permission, flight eligibility, cosmetics, chest fallback, respawn, full restart/rejoin and actual Nether transition. [With MapStitch](qa/evidence/keybind-elytra-with-mapstitch.json), [without MapStitch](qa/evidence/keybind-elytra-without-mapstitch.json). These addon checks used the initial server candidate; its changed classes are recorded in [candidate comparison](qa/evidence/1.0.2/candidate-diff.json), with no elytra-path change. |
| Atlas gameplay, metadata and crafting | 39 client and 38 server assertions passed with the exact final shim/addon pair: seed-map minimap/world-map rendering in inventory and leggings pouches, terrain deltas, configuration opt-outs, scales 0–4 and boundaries, stale IDs, names/order/selection, 144-item preservation, current-input/repeated recipe previews, invalid maps, dimension cache invalidation and actual native crafting with one book/map consumed. [Evidence](qa/evidence/1.0.2/atlas.json), [baseline failures](qa/evidence/1.0.2/atlas-baseline.json), [minimap](qa/evidence/atlas-minimap.png), [world map](qa/evidence/atlas-worldmap.png). |

## Reproduction and release records

The runnable fixtures are under [qa/matrix](qa/matrix/README.md), [qa/regression](qa/regression/README.md), [qa/addon](qa/addon), [qa/lore-boundary](qa/lore-boundary) and [qa/defaulted-projection](qa/defaulted-projection). They reuse the workspace's cached original game/mod inputs and disposable localhost profiles; they are not standalone downloadable game installations. No user server/world was edited. Raw profiles/worlds/logs stay ignored; published evidence contains results and artifact hashes.

The source audit also covered centralized registry IDs, both packet directions, native capability detection, optional mixin gating, fallback guards, Defaulted selectors, SSO repair/whetstone selection and resource registration. No additional concrete defect was established in those reviewed paths. Original mod dependencies and native feature paths remain in place.

Fixture issues were distinguished from production failures: a fixed eight-client-tick Creative assertion could inspect an inventory before its dimension-change packets arrived, so the fixture now waits for the bounded observed state; Polymer wraps tooltip components while retaining their rendered style, so lore regression checks compare rendered content/style rather than component-tree identity. The initial sandbox startup attempt could not bind localhost; runtime checks above ran with authorized local networking.

The pre-QA 1.0.1 release was copied and byte-verified before source changes into `Builds/Minecraft/Multi-Shim/`, preserving both target tracks. New releases include a source revision/snapshot, exact runtime locks, compile-input hashes and checksums under that same standalone family. The addon remains separately installable on server and native clients; a server-only shim cannot fix the client controls menu.

## Coverage limits and retained behavior

This is bounded regression testing, not proof of no remaining issues in every modpack. Physical keyboard hardware, third-party accessory integrations, long-duration multiplayer load and arbitrary production-world migrations were not tested. Native clients require their matching originals; Fabric clients advertising registry sync must include installed SSO/Tiered originals. Vanilla players retain Type A restrictions for pouches/backpacks/atlases and Type B SSO support. Custom fallback visuals still require Polymer pack delivery/acceptance. No new dependency was substituted or removed. See [investigation notes](qa/evidence/1.0.2/QA-NOTES.md) for baseline/intermediate failures and corrections.

Historical repair-material, moving-cursor and Mending results remain available in the [1.0.1 validation record](https://github.com/THENATHE/SSO-backpack-toolpouch-mapstitch-shim/blob/426721e/VALIDATION.md); they are not relabeled as new 1.0.2 tests. In particular, the original native client's still-broken-item use guard when automatic break repair is disabled remains an upstream limitation. Existing unexpected calcite overrides still require the explicit selected-item recovery command; no inventory-wide migration runs.
