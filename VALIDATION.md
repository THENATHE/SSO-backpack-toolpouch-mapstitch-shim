# Validation — 1.0.0+26.3

Tested locally on 2026-10-01 with Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 and Polymer 0.18.2+26.3. Candidate SHA-256:

`2ab08d31dc335b03e2e4c16bfd2b1538de8e7e5a6e1760aac4164ed807e49fe3`

The production JAR stayed unchanged throughout the final tests. The private test kit contains 97 verified production JAR copies across seven server/native-client profile pairs; every archive, manifest hash, dependency ID and absence of duplicate mod IDs was checked. Exact original/dependency JAR hashes are recorded per suite in [qa/evidence](qa/evidence), the [target inventory](qa/matrix/TARGET-ARTIFACTS.json), and separate [developer](tracks/developer/runtime.lock.json) / [SSO port](tracks/sso-port/runtime.lock.json) locks. Original JARs were not edited.

| Check | Result and actual scope |
| --- | --- |
| Build | Offline Gradle build passed, Java 25 bytecode; all original compile-input hashes verified |
| Optional modules | All 16 installed-mod subsets passed startup and actual datapack reload, including zero originals and each original alone; expected enabled modules, every custom-item fallback and preserved source stacks checked |
| SSO port | Separate port-only and full-four port-target startup/reload passed; actual enchanted repair recipe passed; port dependencies isolated from developer Defaulted/CodecUI |
| Chalk coexistence | All four modules plus the unchanged separate Chalk shim and requested 26.3 Chalk port passed startup/reload; Chalk excluded from combined module list |
| Native Creative and commands | 144 checks passed across full-stack and Tiered-only profiles (72 each): six backpack tiers, operator/non-operator Creative actions and player-issued `/give`, across Overworld, Nether, End and a custom dimension |
| Recipe book | Full recipe unlock decoded 1,078 native recipe collections without disconnection |
| Native storage | All six backpack menus, keybinds, retained 42 diamonds, equipped backpack with four gold, Atlas world map; passed native-client and native-client-plus-Polymer sessions; 162 supplemental server assertions |
| Unmodified vanilla | Zero-mod vanilla client connected and remained in gameplay for 40 seconds; safe placeholders/unsupported-action guards checked |
| Native Survival anvil | Actual input placement, result pickup and storage passed for Mending pickaxe; repaired-component input and an injected 41-level menu cost verified native displayed cost and actual 41-level XP payment |
| Native whetstone | Developer inventory and crafting-table repair passed (damage 1000 → 479, Efficiency I/Unbreaking I retained, diamond consumed, whetstone retained); separate SSO port and SSO-only developer profile also passed actual client clicks |
| Native Creative whetstone | Enchanted whetstone packet preserved original item identity and stored Efficiency I/Unbreaking I without disconnect |
| SSO vanilla-wire gameplay | Both developer and port tracks passed four actual client transactions each: free anvil, 65-level anvil, 5-level smithing and grindstone. This used a test-only Fabric API client without content mods and with its registry-sync receiver removed; it is explicitly not an unmodified vanilla client |
| Atlas/elytra addon | Unchanged addon 1.0.0+26.3, original Tool Pouch/MapStitch, combined server plus separate Chalk, native client without Polymer: atlas 30 assertions and elytra 68 assertions passed |
| Closed-pouch atlas updates | Actual terrain changes reached native maps with the pouch closed, both in inventory and attached to leggings; minimap/world map, renderer, compass and clock checked |
| Elytra toggle and persistence | Registered key → client/server toggle packets → synchronized state, inventory/leggings, chest fallback, cosmetic behavior, respawn and server restart passed |
| Survival cursor | Six item types, actual screen mouse clicks and 100 ms delay each direction passed; all 246 sampled post-placement cursor states empty, with server item counts/identity/map ID/damage/repair components preserved |
| Same-process reconnect | Same client JVM explicitly disconnected and rejoined; 12 moves across two cycles, 492 empty post-placement cursor samples, and both server invariant checks passed |
| Isolated native clients | SSO-only repair, Tool Pouch-only native contents/menu editing, MapStitch-only crafting/map sync/world map, and all 72 Tiered-only Creative/command/dimension checks passed |

## Limits and interpretation

The earlier reported ghost cursor was not reproduced reliably on the old 1.0.1 stack. These are regression passes, **not proof that its original cause was found or fixed**. Restart clients fully when changing the server mod stack; reconnect coverage is reported separately above.

Chalk is outside the combined shim. Coexistence does not certify all Chalk gameplay or fix the separate shim's native Creative behavior. The atlas/elytra addon remains unchanged, including its duplicate Controls category heading; that client-side heading cannot be fixed by this server-only JAR.

SSO and Tiered native detection retains strict Fabric registry validation: a Fabric client advertising registry sync needs matching original mods. The Type A modules do not provide native backpack/pouch/atlas systems to vanilla clients. The listed native feature paths were tested with the pinned originals; other feature-modified originals and arbitrary additional mods are not certified.

The matrix tests initialization/reload and bounded recipe/fallback invariants, not every gameplay path in every subset. The tests cover initial connections and the listed dimension/restart/reconnect scenarios, not every same-connection reconfiguration or production-world migration. Pack generation/integrity and specific native rendering checks do not prove every vanilla texture on every renderer.

Initial QA-only defects were preserved in local runs and corrected before final evidence: flat-world settings emitted an unrelated configuration error; anvil file markers raced arriving click packets; a manually injected map color was immediately overwritten by ordinary terrain scanning. A first reconnect fixture cleared only the client world and was excluded because it left the original socket open; the final replay explicitly closes the network connection and verifies two normal server logins. Final anvil assertions wait for server processing; final atlas assertions change actual terrain. No production fixes were made merely to satisfy these fixture assumptions.

## Reproducing the checks

[Matrix instructions](qa/matrix/README.md), [network/cursor instructions](qa/regression/README.md), [native repair fixtures](qa/repair/README.md), and [matrix/SSO evidence notes](qa/evidence/MATRIX-AND-SSO-VANILLA-VALIDATION.md) describe the local harnesses. Test-only fixtures are never packaged in the production JAR or test kit. Harnesses depend on the existing local Minecraft caches, original-artifact staging and offline QA launch baseline; proprietary game files, dependency JARs, launch credentials, worlds and full logs are excluded from GitHub.

These checks support testing this exact stack in a disposable copy of your gameplay environment. They are not a blanket production certification for every permission setup, extra dimension, modpack or long-running world.
