# Consolidated networking regression results

Candidate: `SSO-backpack-toolpouch-mapstitch-shim-1.0.0+26.3.jar`  
SHA-256: `2ab08d31dc335b03e2e4c16bfd2b1538de8e7e5a6e1760aac4164ed807e49fe3`

All cases below ran against this exact candidate on Minecraft 26.3, Fabric Loader 0.19.5 and Java 25. The combined server contained the original four developer mods and dependencies, Polymer, the consolidated shim, and the separately retained Chalk port/shim: 15 production JARs plus a test-only fixture. Native clients used the supplied 12 original/dependency JARs plus a fixture; Polymer was added only to the explicitly named native+Polymer storage session. The unmodded client used zero mods. Exact JAR filenames and SHA-256 hashes are in the linked JSON records.

| Suite | Observed result | Evidence |
| --- | --- | --- |
| Combined native Creative and commands | PASS, 72 cases: six backpack tiers × four dimensions × Creative non-op / Creative op / Survival op `/give`; 48 Creative item packets and 24 player-issued commands | [Creative](network-creative.json) |
| Tiered-only native Creative and commands | PASS, same 72 cases with SSO, Tool Pouch, MapStitch and Chalk originals/shims absent; server has six production JARs and native client four | [Isolated Tiered](network-creative-tiered-only.json) |
| Combined native recipe book | PASS, server unlocked 1,762 recipes; native client decoded 1,078 recipe collections and remained connected | [Recipes](network-recipe.json) |
| Storage and connection variants | PASS, 162 synthetic fallback/serialization assertions; native and native+Polymer each retained six tiers, tier/dye/content components, 42 diamonds, attached armor and four gold ingots, native menus/keybinds, and Atlas world map; true vanilla connected through 40 gameplay seconds with fallback guards | [Storage](network-storage.json) |
| Delayed native Survival cursor | PASS, six real inventory-screen mouse pickup/place cycles; 246 after-place cursor observations all empty; server verified identity/count, map ID, tool damage and repair count | [Cursor](network-cursor.json) |
| Same-process delayed cursor reconnect | PASS, two six-item cycles separated by explicit network disconnection and normal reconnect in the same client JVM; 492 after-place observations all empty; both server verification cycles passed | [Reconnect](network-cursor-rejoin.json) |

The four dimensions were Overworld, Nether, End and a test-only custom dimension using vanilla type/generator definitions. Both cursor suites added 100 ms delay to each direction of a localhost TCP relay and used a one-tick click-delay threshold. The six cursor item types were diamonds, empty maps, filled maps, an Atlas, a damaged diamond pickaxe and a pickaxe carrying SSO repair metadata. Filled maps were verified by identity/count/map ID because MapStitch legitimately adds map-center metadata.

The first reconnect trial only cleared the client world and left the old socket until duplicate-login replacement. That trial is preserved in the local ignored run directory but is excluded from normal reconnect evidence. The recorded reconnect suite explicitly closes the network connection; the server log confirms `Disconnected` followed by a second login, without duplicate-login replacement.

These tests did not reproduce the reported ghost cursor and do not establish its cause or claim a fix. They do not cover same-connection configuration restart, resource reload, every mod subset with live clients, every gameplay system, external protection plugins, or native Chalk Creative acquisition. The standalone Chalk shim's policy was not changed by consolidation. SSO anvil/whetstone and addon-specific tests are recorded separately by their owning suites.

Fixture source and runnable local scripts are under [qa/regression](../regression/README.md). Full local logs, worlds, packet observations, screenshots and unsanitized launch metadata remain in ignored run folders; public evidence omits absolute launch paths and commands. The storage fixture's synthetic native context uses test-only reflection; real connection checks use negotiated runtime state. All clients, servers and proxies launched for these networking suites were shut down after each run.
