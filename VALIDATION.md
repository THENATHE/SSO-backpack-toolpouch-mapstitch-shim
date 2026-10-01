# Validation — 1.0.1+26.3

Minecraft 26.3; Java 25; Fabric Loader 0.19.5; Fabric API 0.161.0+26.3; Polymer Bundled 0.18.2+26.3. Production candidate SHA-256:

`55787adf8c25f0b3b0891a40f8441d8247d9599d9312f9b9f2eb2cfdaf2745b8`

## Confirmed defect and correction

The user's actual server used the expected original JARs and released combined shim 1.0.0. Its saved enchanted diamond pickaxe contained an explicit `minecraft:repairable` override accepting only calcite. Matching enchantments and one diamond therefore could not satisfy the original portable repair recipe. Read-only inspection did not alter the server or world; private player/world data is not published.

The cause is a client-side component-rule expansion. SSO configures a calcite repair rule for `#chalk:chalks` through Defaulted. Polymer hides Chalk's original item registry entries, leaving that tag empty on the native client. Defaulted 1.3.8 interprets an all-empty selector as global. Thus unrelated client item defaults became calcite-repairable, and a native Creative item packet could persist that incorrect component on the server. Actual Creative acquisition → anvil book enchantment → matching enchanted whetstone + one diamond reproduced the failure with 1.0.0.

Version 1.0.1 projects outgoing Defaulted selectors into explicit client-visible item holders. Scoped rules with no visible target remain inapplicable, instead of becoming global; genuine server wildcard rules, patch values, generators and priority are preserved. The original packet and authoritative server rules remain intact. Defaulted is retained unchanged; neither original SSO nor Chalk JARs are modified. The existing SSO port, which has no Defaulted dependency, skips the optional hook.

An operator-only `/sso-shim repair-held` command explicitly restores a saved unexpected single-calcite override to the fresh server item default. All other components remain intact. Recovery is not automatic: a deliberately command-created calcite override cannot be distinguished from this bug, so the operator must select the affected held item. Legitimate calcite-repairable Chalk and unselected items are preserved.

## New release checks

| Check | Result |
| --- | --- |
| Offline build | Passed; exact original compile-input hashes checked, Java 25 bytecode |
| Original bad-state replay | 32 cases across the released combined stack and a control without it. A calcite-overridden enchanted pickaxe rejects diamond1 through direct recipe, recipe manager, 2×2 and 3×3 menus; restoring only target repairability restores output. The diamond's own repairability is irrelevant |
| Defaulted selector projection | 29 runtime assertions passed: genuine wildcards, visible/mixed/hidden scopes, original immutability, nonempty generators, component patch and priority preservation, fully visible Chalk targets |
| Acquisition and enchantment | All nine crafted/`give`/Creative tool-and-whetstone pairs passed actual anvil/book Efficiency I enchanting followed by diamond1 repair (damage3 → 0, enchantment retained, diamond consumed, whetstone retained). Client/server components stayed diamond/quartz-repairable; the oversized diamond9 negative control remained rejected |
| Saved-item recovery | Developer operator command passed: enchanted pickaxe and whetstone recovered while damage, enchantments and other components were preserved; clean, legitimate Chalk, custom non-calcite, empty-hand and non-operator negative cases passed. The same checks passed on the separate SSO port |
| Optional module and port tracks | Six cases passed: developer modules none, SSO only, Tool Pouch+MapStitch only, and all four; SSO port alone and with all four. Startup and reload passed in every case; portable repair checks passed where SSO was present |
| Native anvil | Actual Mending application/result collection and 41-level cost display/payment passed, including prior repair-count component |
| Creative/commands, recipe book, storage | Passed: 72 Creative/command cases across six backpack tiers and four dimensions; 1,762 recipes unlocked / 1,078 collections decoded; 162 storage assertions plus actual native, native+Polymer and zero-mod vanilla sessions. See [network report](qa/evidence/defaulted-network-report-1.0.1.md) |
| Test-kit staging | Passed: all 97 production JAR copies across seven server/native-client profile pairs have valid archives and matching manifest hashes; every combined shim copy is the tested 1.0.1 candidate. Release assembly verifies the final ZIP and records its checksum separately |

Every final result records exact JAR hashes in [qa/evidence](qa/evidence). [Developer runtime lock](tracks/developer/runtime.lock.json) and [SSO port runtime lock](tracks/sso-port/runtime.lock.json) remain separate. Only the existing SSO target is substituted in the port track; developer Defaulted/CodecUI dependencies are preserved and are not imposed on the port.

## Follow-up: moving-click ghost cursor

A saved explicit calcite repair component is normalized away on the 1.0.0 native client because its incorrect default already equals calcite, while the authoritative server still has that component as an explicit override. Their predicted item hashes disagree. A moving click sends quick-craft start/add/end packets; the first phases trigger nonempty cursor corrections, but the end already predicts an empty cursor and produces no clearing correction. The delayed nonempty corrections then recreate a visual cursor stack after the server has placed the real item. The screenshot and packet trace reproduce this on an ordinary diamond stack.

With the existing 1.0.1 JAR and a fresh client, the saved override remains explicit on both sides, so the predicted hashes agree. Cursor synchronization does not require recovering every saved override first; `/sso-shim repair-held` is still needed to restore affected repair gameplay. The binary is unchanged. Exact follow-up cases and controls are recorded in [the cursor report](qa/evidence/cursor-motion-1.0.1.md).

## Follow-up: native Mending Shift-use

The unchanged 1.0.1 binary passed ten ordinary-use/control cases through native Shift input and the normal right-click-in-air path, with exact damage/material/repair-count checks and client/server synchronization. Two further default cases verified automatic break repair followed by one manual repair. A focused automatic-repair-disabled comparison found the same still-broken-item client guard with and without Polymer/shims. [Full results, configuration distinction and evidence](qa/evidence/mending-use-1.0.1.md). XP, offhand and use-on-block behavior were not independently asserted by this suite.

## Coverage boundaries

The negative 1.0.0 acquisition run passed six crafted/given tool combinations before its Creative-origin enchanted pickaxe failed. We do not claim that every fresh Survival-crafted item independently failed. The demonstrated problem is incorrect client defaults and persisted repair components, regardless of the item's earlier provenance. Prior tests seeded valid server-side enchanted items and checked item identity/enchantments while missing this repair-material corruption; those passes did not rule it out.

The original recipe also rejects an oversized material stack for a lightly damaged tool. That separate behavior is retained and tested as a negative control; it is not used to explain away the one-diamond failure.

The [1.0.0 validation record](https://github.com/THENATHE/SSO-backpack-toolpouch-mapstitch-shim/blob/v1.0.0%2B26.3/VALIDATION.md) contains the earlier 16-subset, dimension, isolated-module, atlas/elytra, restart and cursor checks. Those historical results are not presented as new runs of 1.0.1. The atlas/elytra addon and separate Chalk shim are unchanged. Their existing duplicate keybind heading / full-stack Chalk Creative coverage limits remain documented. Follow-up [moving-cursor tests](qa/evidence/cursor-motion-1.0.1.md) reproduce the saved-calcite ghost mechanism on 1.0.0 and verify the same item state on 1.0.1. These tests cover direct inventory-screen mouse events in fresh client processes; they do not establish that every possible ghost-cursor cause or third-party client configuration is fixed. The earlier stationary cursor passes missed this combination of movement and saved component state.

Native clients should fully exit and restart after updating the server so old client component defaults are discarded. Existing saved corrupted items need explicit recovery. No client companion or new dependency is required. Passing the listed regressions does not certify unrelated mods, every configuration, or every production-world migration.
