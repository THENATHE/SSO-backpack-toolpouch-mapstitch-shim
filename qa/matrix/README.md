# Optional-module matrix and target provenance

This disposable dedicated-server harness installs the unified shim with every subset of its four optional base mods. The 16-case developer matrix includes no base mods, each base mod alone, every intermediate combination and all four. Minecraft 26.3, Fabric Loader 0.19.5 and Java 25 are fixed. Fabric API and Polymer are always installed; remaining original dependencies are the union required by each selected profile. The four former individual shims are never installed. Chalk remains separate and is installed only for explicit coexistence cases.

```sh
python3 qa/matrix/run.py developer-candidate-1 build/libs/<candidate>.jar
python3 qa/matrix/run.py sso-port-candidate-1 build/libs/<candidate>.jar --track sso-port --masks 1,15
```

Run from this project's root in the authorized local-network QA environment. Each suite name must be new. At most two dedicated servers run at once, using ports 25800 and 25801. Each case has a fresh world, mods directory, configuration and logs. The harness reuses cached Minecraft/loader libraries; it does not download or alter original mod JARs. Accepted local test EULA material is copied from the existing bundle QA. All servers bind only to localhost and are stopped after checks.

Mask bits, in order: Simple Smithing Overhaul=1, Tiered Backpacks=2, Tool Pouch=4, MapStitch=8. `--masks 0,1,2,4,8,15` selects a smoke subset. `--workers 1` serializes execution. `--with-standalone-chalk --masks 15` adds the retained Chalk 26.3 port and its separate shim alongside all four unified modules; Chalk must not appear in the unified module list.

At startup and after a real datapack reload, the test-only fixture verifies the exact unified `Modules.enabled()` set and which optional base mods are loaded; checks absent mod namespaces contain no registered items; converts every present module item through Polymer to a safe vanilla fallback; and verifies the original stack identity/count is preserved. When SSO is present, its actual recipe is invoked for an enchanted diamond pickaxe repair: damage 1000 → 479, preserved enchantments/input, consumed material, retained whetstone, and rejection of incorrect material or flint for enchanted gear. No original-mod classes are statically referenced by the fixture. This detects initialization, optional-class linkage, reload and bounded recipe/fallback failures; it is not native or vanilla client gameplay testing.

Each case retains `mods/`, `console.log`, `control/startup.txt`, `control/reload.txt` and `result.json`. Per-case results record exact installed versions and SHA-256 hashes. `RESULTS.json` aggregates the suite. PASS requires both fixture stages, clean exit, requested save/stop, no timeout, and no error/exception log lines.

## Separate target tracks

`TARGET-ARTIFACTS.json` records the exact artifact provenance, metadata constraints and SHA-256 hashes. It also records the existing SSO and Chalk port source revisions/status. This matrix makes no changes to those sources or their declared dependencies.

| Family | Default 26.3 target | Other existing track |
| --- | --- | --- |
| Simple Smithing Overhaul | Developer 2.9.14+26.3 with Defaulted 1.3.8, CodecUI 26.3-1.4.3, Fzzy Config 0.7.7+fix2+26.3, Mixson 2.2.1, Kotlin 1.14.1+kotlin.2.4.20 | Existing ChatGPT port 2.9.14-port.1+26.3, tested separately using Fzzy/Mixson/Kotlin and no Defaulted/CodecUI, matching its recorded release dependencies. |
| Chalk (separate coexistence test only) | Requested ChatGPT port 3.2.1+26.3 and standalone Polymer shim with Cloth Config 26.3.159 | Official developer 3.2.0+26.2 and its separate Polymer shim 1.0.0+26.2 remain available. They are not 26.3 targets and are not mixed into this matrix. |
| MapStitch | Original developer 1.1.6+26.3 with Fzzy/Kotlin | Existing same-version Tool Pouch integration modification is a separate artifact, not an additional Minecraft version port. |
| Tiered Backpacks | Original developer 1.0.19+26.3 with Fzzy/Kotlin | Existing same-version tooltip modification is separate; no Minecraft version port exists in the release inventory. |
| Tool Pouch | Original developer 1.1.10+26.3 with Fzzy/Kotlin | Existing same-version atlas/Elytra modifications are separate artifacts, not additional Minecraft version ports. |

The SSO port's prior source already replaced its Defaulted use with Fabric's component API. This task preserves that exact existing artifact and independently tests it; it does not silently apply that dependency change to the developer target. The developer SSO track continues to use the actual original Defaulted and CodecUI dependencies. See the existing port's `Minecraft/simple-smithing-overhaul-26.3/README.md` and `QA.md` for its historical implementation and validation.

The retained Chalk developer shim is at `Builds/Minecraft/Chalk/Polymer Compatibility Shim - Developer Release/1.0.0+26.2/`. A new unified 26.2 port is outside this 26.3 task. Same-version feature-modified base mods and optional addons are not implicitly certified by the original-mod matrix.

To verify a newer Tiered Backpacks release without rewriting baseline inputs, pass `--tiered-jar /path/to/original.jar`. The runner checks its mod ID and requires unchanged dependency declarations, freezes a copy per run, and records its exact metadata/hash. A changed dependency set needs a separately resolved profile. [1.0.20 verification](../tiered-1.0.20/README.md) exercises every developer/SSO-port combination containing the updated mod.
