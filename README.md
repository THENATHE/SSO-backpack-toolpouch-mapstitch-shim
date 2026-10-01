# SSO-backpack-toolpouch-mapstitch-shim

Unofficial server-side Polymer compatibility for Minecraft **26.3 / Fabric**, with four independently enabled modules. A module activates only when its original mod is installed. The original mods and their dependencies stay separate.

[Releases](https://github.com/THENATHE/SSO-backpack-toolpouch-mapstitch-shim/releases) · [Validation](VALIDATION.md) · [Credits](NOTICE.md)

**1.0.1 fixes incorrect repair materials on native clients.** Polymer-hidden Chalk items could empty a Defaulted selector and turn the Chalk-only calcite repair rule into a rule for every client item. Enchanted tools could consequently retain the wrong repair material and reject diamond repairs. This release keeps outgoing Defaulted rules scoped to the items actually present in the client registry, while retaining the original library and server rules.

**Moving-click ghost items:** follow-up tests reproduced a placed diamond stack also remaining on the cursor in 1.0.0 when the item carried the saved incorrect calcite override. Movement invokes quick-craft packets; the mismatched client defaults cause intermediate carried-item corrections to arrive after placement. The same saved override stays synchronized with a fresh native client on 1.0.1. See [moving-cursor reproduction and controls](qa/evidence/cursor-motion-1.0.1.md).

Replace combined shim 1.0.0 with 1.0.1 on the server, then fully restart the server and Minecraft clients. Already saved incorrect overrides persist until repaired: hold an affected item and, as an operator, run `/sso-shim repair-held`. This restores only the unexpected calcite repair-material component to its server default, preserving enchantments, damage, names, contents and other components. It does not alter other inventory items or legitimate calcite-repairable Chalk. Because an intentional command-created calcite override is indistinguishable from this bug's override, recovery is explicit and selected-item-only.


| Module | Original target | Client without the original mod |
| --- | --- | --- |
| Simple Smithing Overhaul | Developer 2.9.14+26.3; separately verified port 2.9.14-port.1+26.3 | Type B: server-side smithing, repair and anvil systems with vanilla interfaces and guidance |
| Tiered Backpacks | Developer 1.0.19+26.3 | Type A: safe display and client-mod-required guards |
| Tool Pouch | Developer 1.1.10+26.3 | Type A: safe display and client-mod-required guards |
| MapStitch | Developer 1.1.6+26.3 | Type A: safe display and client-mod-required guards |

Native clients use the matching original mods for their native systems. **Chalk stays separate**: keep its 26.3 port and standalone Polymer shim when desired. This combined JAR does not supply Chalk compatibility.

## Installation

1. Install Java 25+, Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 and Polymer Bundled 0.18.2+26.3 on the server.
2. Keep the original mods you want and their required dependencies. Add this single combined shim to the server's `mods/` folder.
3. Remove the four separate SSO, Tiered Backpacks, Tool Pouch and MapStitch Polymer shims. Fabric rejects duplicate shim installations to prevent competing registry translations. Keep the separate Chalk shim if using Chalk.
4. For native play, install matching original mods and dependencies on the client. The combined shim is server-only; Polymer is not required on native clients.
5. For the Tool Pouch atlas/elytra integration, keep `toolpouch-atlas-elytra-compat-1.0.0+26.3.jar` on **both** server and native client, together with Tool Pouch and MapStitch. It remains a separate addon.
6. Restart the server and fully restart clients after replacing mods. The server log prints `SSO_STACK_MODULES` with enabled modules.

Polymer's generated resource pack supplies custom visual assets. Configure Polymer pack hosting/distribution and have vanilla players accept the pack for these visuals; safe vanilla fallback items remain available without it. Pack generation/hosting is provided by Polymer, not a custom client mod.

Native detection uses original Tool Pouch and MapStitch networking channels. SSO and Tiered Backpacks do not expose equivalent unique channels, so this release retains strict Fabric registry validation: **Fabric clients advertising registry sync must have the matching installed SSO/Tiered originals**. Unmodified vanilla clients use the fallback path. A Fabric client missing these originals is not treated as an unmodified vanilla client.

## Mending Shift-use

On the tested native stack, hold a damaged Mending item and Shift + right-click in air with its repair material and a usable whetstone in the player inventory. The whetstone must store every enchantment type on the tool, including Mending. Each successful activation consumes one repair material; the original recipe controls durability restored and random whetstone wear.

[Native 1.0.1 tests](qa/evidence/mending-use-1.0.1.md) verified normal repair, matching additional enchantments, repeated uses, compatible-whetstone selection and negative controls. With default automatic break repair enabled, broken items repair automatically when suitable supplies are available. If that option is disabled, an item still marked broken is blocked by the original native client's use callback before it reaches the server; the same limitation reproduces without Polymer/shims.

## Exact dependency tracks

All four developer targets use Fzzy Config 0.7.7+fix2+26.3 and Fabric Language Kotlin 1.14.1+kotlin.2.4.20. Developer SSO additionally requires Defaulted 1.3.8.release-26.3, CodecUI 26.3-1.4.3 and Mixson 2.2.1. Original metadata and hashes are in [the target inventory](qa/matrix/TARGET-ARTIFACTS.json).

The existing ChatGPT SSO port is an independent target, using its recorded Fzzy Config, Kotlin and Mixson stack. It does not require Defaulted/CodecUI. That is the existing port's architecture; this shim does not remove dependencies from the developer release. Track-specific locks, release records and source snapshots are preserved separately. There are no Minecraft version ports of the other three modules in this release inventory; their existing same-version feature modifications are not certified by the developer-target tests.

## Build and design

Supply the exact unmodified local developer JARs listed in `dependencies.lock.json` in `libs/`. Some targets are developer releases without public downloads; they are not republished as dependencies in this repository. `verifyOriginalInputs` rejects mismatched hashes. With JDK 25:

```sh
./gradlew build
```

Output: `build/libs/SSO-backpack-toolpouch-mapstitch-shim-1.0.1+26.3.jar`. This workspace also supports a JDK 27 compiler with `-PcompilerVersion=27`, producing Java 25 bytecode while Gradle runs on JDK 25.

Optional mixins are gated before original-mod classes load. One shared connection-scoped coordinator negotiates native capabilities, restores allowed registry entries and assigns a single dense item/component/recipe/menu mapping. Both packet directions, registry tags and component preservation share this mapping. This replaces the former competing per-shim coordinators. Original item objects and saved data remain owned by the original mods.

See [VALIDATION.md](VALIDATION.md) for tested coverage and limitations. Automated regression checks cannot certify every gameplay combination or an arbitrary production modpack.
