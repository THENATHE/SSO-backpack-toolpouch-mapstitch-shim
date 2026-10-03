# Combined networking regressions

These copied fixtures exercise the consolidated JAR with the original developer mods and the separately installed Chalk port/shim. Legacy fixture sources and results are preserved. The runners remove the four old shim JARs by Fabric mod ID before inserting the candidate.

Run each suite with `python3 qa/regression/<suite>/run.py <fresh-run-name> <candidate-jar>`, where `<suite>` is `creative`, `recipe`, `storage`, `cursor`, or `cursor-rejoin`. For delayed cursor coverage set `CURSOR_QA_DELAY_MS=100 CURSOR_QA_CLICK_DELAY=1`. The Creative runner accepts an optional fourth argument `tiered-backpacks` to exercise the isolated original-mod profile. All servers bind localhost; ports are 25916, 25926, 25936, and 25946 (cursor proxy 25947). A rendered client requires the local graphics session. Clients are stopped and servers shut down by the runners.

The scripts reuse the workspace's existing `polymer-shim-test-bundle/staging` dependency set, cached Minecraft/Fabric launch classpaths, and the historical offline QA launch audit. They do not read launcher account credentials. These local prerequisites must be recreated before running on another machine; the repository does not redistribute the original mods, game libraries, account data, or run worlds.

- Creative: 48 native item packets (operator and non-operator) and 24 player-issued `/give` commands for six backpack tiers across Overworld, Nether, End, and a custom dimension.
- Recipe: native client's full recipe unlock and decoded recipe-book collections.
- Storage: existing 162 fallback/serialization assertions plus actual native, native-with-Polymer, and true unmodded client sessions, including six-tier inventories, keybinds, equipped storage and Atlas integration.
- Cursor: real inventory-screen mouse callbacks in Survival with six item types, server/client packet observations, and delayed localhost TCP relay. Input isolation is test-only. Filled maps are compared by identity/count/map ID because MapStitch legitimately enriches their metadata.

The `cursor-rejoin` fixture repeats the six cursor moves after an actual disconnect/rejoin in the same client JVM, using the delayed proxy on port 25947. Run this suite with the same two cursor environment variables; its endpoint intentionally targets the proxy. This does not exercise resource reload or same-connection configuration restart.

The synthetic storage assertions seed the central capability context through reflection only in the QA fixture. Actual client connection classification assertions use negotiated runtime state. Production source is not altered by these runners.

Sanitized results and exact artifact hashes are recorded under `qa/evidence`; full local logs, mod manifests, fixture JARs and worlds remain in ignored `runs/` directories. Passing these bounded regressions is not a claim that all gameplay features or external server plugins have been tested.

The separate [pouch/shulker regression](pouch-shulker/README.md) exercises original-only controls, native quick-menu bed interactions, repeated opening, source movement and server/client conservation. Its CLI differs from the older suite runners above.
