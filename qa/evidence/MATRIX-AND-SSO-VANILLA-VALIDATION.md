# Optional modules and SSO vanilla-wire gameplay validation

Verified locally on 2026-10-01 against the exact unified production candidate:

`SSO-backpack-toolpouch-mapstitch-shim-1.0.0+26.3.jar`

SHA-256: `2ab08d31dc335b03e2e4c16bfd2b1538de8e7e5a6e1760aac4164ed807e49fe3`

Minecraft 26.3, Fabric Loader 0.19.5, Java 25, Fabric API 0.161.0+26.3 and Polymer 0.18.2+26.3 were used throughout. Original production artifacts were not modified. Each result records the installed production and test-fixture JAR identities, versions and SHA-256 hashes.

## Optional-module and track matrix: 19/19 PASS

- All 16 subsets of the four optional developer mods passed dedicated-server startup, datapack reload, save and shutdown. This includes no base mods, every mod individually, every pair/triple and all four together.
- The existing SSO 2.9.14-port.1+26.3 track independently passed alone and with the other three original mods, using its exact Fzzy/Mixson/Kotlin dependency stack without Defaulted or CodecUI.
- All four unified modules plus the separately retained Chalk 3.2.1+26.3 port and standalone Chalk shim passed coexistence checks. Chalk did not appear in the unified module list.

The fixture asserted the exact `Modules.enabled()` set; absence of missing mods' registered items; safe vanilla representations for every registered item belonging to present modules; and preservation of original item identity/count. Every SSO case also invoked the actual target's portable repair recipe before and after reload: an Efficiency I/Unbreaking I diamond pickaxe repaired from damage 1000 to 479, retained enchantments and unmodified input, consumed its repair material, retained its whetstone, and rejected an incorrect repair material or flint for enchanted gear.

The developer SSO track retained Defaulted 1.3.8 and CodecUI 26.3-1.4.3. The existing port's differing dependency implementation predates this task; this work tests that exact separate artifact and does not apply its dependency change to the developer release. The Chalk developer 26.2 shim remains separate and checksum/ZIP intact; no new combined 26.2 port was created.

Evidence: [developer matrix](matrix/developer-results.json), [SSO port matrix](matrix/sso-port-results.json), [separate Chalk coexistence](matrix/standalone-chalk-coexistence-results.json), and [target artifact/source provenance](matrix/TARGET-ARTIFACTS.json). Sanitized startup/reload/shutdown excerpts accompany each matrix JSON.

## SSO menu transactions through vanilla registry wire behavior: 8/8 PASS

Both the developer and port target completed these four real client/server click transactions with the generated server resource pack loaded:

| Transaction | Server-verified outcome |
| --- | --- |
| Free unenchanted anvil repair | Repaired result taken at zero XP, remaining XP 0. |
| Expensive anvil enchantment combination | Real cost 65, XP 100 → 35; vanilla client display receives cost 0 to avoid its hardcoded expensive label. |
| Enchantment-upgrade smithing | Sharpness I → II, cost 5, XP 100 → 95, template/lapis consumed. |
| Netherite-scrap grindstone | Prior-work cost 31 → 15, enchantment retained, XP unchanged at 100. |

The client contains only Fabric API and a disposable QA driver. The fixture deliberately removes the `fabric:registry/sync` receiver before connection to select vanilla registry wire behavior, and asserts that its item registry contains only vanilla entries. It then uses ordinary menu-click packets. **This is a test-only vanilla-wire simulation, not an unmodified official vanilla client.** Official vanilla join checks belong to the separate root validation suite; this report does not establish them. No menu implementation or registry entries are patched on this fixture client.

The anvil inputs are initially prepared by the server fixture; the client takes the result. Smithing and grindstone also exercise real client insertion of inventory ingredients. Exact XP, enchantment, material-consumption and result invariants are checked on the server after packet arrival. This bounded suite does not certify every SSO feature.

Evidence: [developer wire transactions](sso-vanilla-wire/developer-result.json) and [port wire transactions](sso-vanilla-wire/sso-port-result.json). These include client/server transaction evidence, exact mod hashes, resource-pack integrity/hash and client pack-load log lines. The original standalone SSO network suite remains unchanged.

## Test setup correction and limits

The first matrix attempt used a flat-world type without its required layer settings. Its initial fixture checks passed, but Minecraft emitted a world-generation settings error; the harness correctly rejected those cases. That attempt was stopped and preserved locally as `qa/matrix/runs/unified-developer-v1`. Only the disposable world setting changed to normal generation. The fresh `unified-developer-v2` suite passed all 16 cases without error/exception log lines, using the same candidate hash. No production fix was inferred from the test-world setup error.

All test processes were stopped. Raw local logs/control files remain under `qa/matrix/runs/` and `qa/sso-vanilla/runs/`; exported evidence omits launch commands, credentials, game directories and absolute workspace paths. Native client rendering, high-cost native display, inventory cursor behavior, backpack/pouch interactions and official vanilla joins are covered by separate suites and are not implied by the startup matrix or simulated vanilla-wire results.
