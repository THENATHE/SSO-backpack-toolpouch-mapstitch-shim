# Defaulted projection and networking regression — 1.0.1

Tested candidate: `SSO-backpack-toolpouch-mapstitch-shim-1.0.1+26.3.jar`
SHA-256: `55787adf8c25f0b3b0891a40f8441d8247d9599d9312f9b9f2eb2cfdaf2745b8`

Minecraft 26.3, Fabric Loader 0.19.5, Java 25. Original dependency JARs are unchanged. Each combined server used the four developer originals, their dependencies, Polymer, the combined candidate and the separate Chalk port/shim, plus its test-only fixture. Native clients used the supplied originals/dependencies. Exact per-side JAR hashes are in each evidence record.

| Check | Actual result | Evidence |
| --- | --- | --- |
| Defaulted packet projection | PASS, 29 assertions using actual Defaulted packets and server Chalk tag (2 entries), with controlled visibility predicates | [Projection](defaulted-projection-1.0.1.json) |
| Native Creative and commands | PASS, 72 cases: 48 Creative packets and 24 player-issued `/give` commands; six backpack tiers, op/non-op, Overworld/Nether/End/custom dimension | [Creative](network-creative-1.0.1.json) |
| Native recipe book | PASS, 1,762 recipes unlocked; 1,078 recipe collections decoded; client remained connected | [Recipes](network-recipe-1.0.1.json) |
| Storage and client variants | PASS, 162 synthetic fallback/serialization assertions and real native, native+Polymer, and zero-mod vanilla sessions | [Storage](network-storage-1.0.1.json) |

Projection assertions cover empty-list and all-empty-holder-set global wildcard semantics; visible, mixed and hidden-only scoped targets; preservation of component patch, nonempty generator list and priority; fully visible legitimate Chalk targets; and non-mutation of original packet/list/selectors/server tag. The proxy generator is test-only and throws if projection attempts to execute it. Production code retains the original generator references.

Both native storage variants checked all six tiers, original native menus, tier/dye/content components, close/reopen persistence, 42 diamonds, inventory and equipped keybind payloads, attached armor and four gold ingots, and Atlas world-map coexistence. The true vanilla client contained no mods and passed fallback interaction checks through 40 gameplay seconds.

The projection changes outgoing Defaulted selectors only when a connection has the combined registry mapping. Server patch application, data/generators/priority, global wildcard behavior, original JARs and standalone Chalk native policy remain intact. Optional/port startup and actual enchanted whetstone/anvil failure-to-pass/recovery checks are recorded separately by their owning suites.

Historical blind spot: the 1.0.0 native Creative-whetstone fixture checked identity and stored enchantments but did not reject an unrelated `minecraft:repairable` override. Its saved test whetstone carried calcite repairability despite that earlier test passing. Existing historical reports are retained; this narrower original assertion is not treated as evidence that repair-material defaults were correct.

No cursor or reconnect suite was rerun for this update; the earlier cursor symptom remains without a demonstrated cause or fix. These runs used fresh client JVMs. No user server files were modified. Full local worlds/logs/fixtures remain under ignored run directories; sanitized evidence omits launch commands and absolute paths. All owned test clients, servers and proxies were stopped after completion.
