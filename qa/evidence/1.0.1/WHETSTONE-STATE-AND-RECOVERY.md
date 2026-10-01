# Whetstone component-state replay and opt-in recovery

The observed repair failure is reproduced by the affected pickaxe's explicit `minecraft:repairable` override requiring calcite. This overrides its correct server default, which accepts diamonds. Matching Efficiency I enchantments on the pickaxe and whetstone do not overcome the wrong repair-material component.

## Causal replay

Only sanitized item values were reconstructed: diamond pickaxe damage 3, matching Efficiency I on tool and whetstone (plus plain-item controls), optional explicit single-calcite repair components, and one or nine diamonds. The user's SSO configuration was copied read-only into disposable servers; its SHA-256 is `95a2bcd2eaca6502d4c438bbc0cf0cd501a486518fc4e6943feba11802a72e6a`. No player identity, player file, coordinates or world data was loaded.

The exact existing server mod set was tested with released combined shim 1.0.0 (`2ab08d31dc335b03e2e4c16bfd2b1538de8e7e5a6e1760aac4164ed807e49fe3`), then compared with the same set excluding only that combined shim. Separate Chalk, Polymer and addon dependencies remained fixed. Each stack ran 16 item-state combinations.

| Target pickaxe repair material | Diamond count | Direct original recipe | Recipe manager | Player grid / crafting table |
| --- | --- | --- | --- | --- |
| Explicit calcite override | 1 | No match | No match | Empty / empty |
| Explicit calcite override | 9 | No match | No match | Empty / empty |
| Correct canonical diamond default | 1 | Match | Match | Repaired pickaxe, damage 0 / damage 0 |
| Correct canonical diamond default | 9 | No match | No match | Empty / empty |

These outcomes were identical with and without the combined shim and for matching enchanted/plain controls. The diamond ingredient's own calcite-repair override did not affect matching. Both servers had the portable recipe registered, correct authoritative pickaxe defaults, and neither diamonds nor pickaxes belonged to `#chalk:chalks`.

Nine diamonds are separately rejected by the original recipe's quantity restriction for a tool with only three damage; one diamond remains rejected when the target has the corrupt calcite override. This identifies the component defect without attributing the user's failure solely to material quantity or acquisition method. The original developer and existing SSO port have byte-identical portable repair, ModUtil, whetstone and relevant enchanting/anvil mixin classes.

The replay invokes the real recipe manager and menu grid calculation on server-side test players. It is not a graphical client/network test and does not itself establish how the incorrect client component was introduced. The Defaulted packet projection investigation and client prevention regression are recorded separately.

Evidence: [32 causal cases](whetstone-userstate-causal-replay.json).

## Candidate recovery and optional dependencies

Candidate 1.0.1 SHA-256: `55787adf8c25f0b3b0891a40f8441d8247d9599d9312f9b9f2eb2cfdaf2745b8`.

The actual Brigadier `/sso-shim repair-held` command was executed against both the developer SSO track and the existing port track. Each passed:

- OP-selected corrupted pickaxe: restored canonical diamond repair material while preserving damage 3, Efficiency I, custom name, count and all other components; another corrupted inventory item remained untouched.
- Corrupted whetstone: restored canonical quartz repair material while preserving damage 2, stored Efficiency I and count.
- Clean item, legitimate calcite-repairable Chalk, explicit non-calcite custom override and empty hand: unchanged.
- Non-OP invocation: parser rejected the command and the item remained unchanged.

The command is explicit selected-item recovery. It does not automatically rewrite inventories or world data. A deliberately customized single-calcite repair override cannot be distinguished from an accidentally introduced identical override; players should invoke the recovery command only on items they intend to restore to server defaults.

The candidate also passed startup/reload, enabled-module, safe-fallback and actual portable-repair checks for developer masks 0, 1, 12 and 15, and independent port masks 1 and 15. The port runs had neither Defaulted nor CodecUI, verifying the new optional integration does not force those libraries onto the port. These six cases supplement the prior full 16-subset matrix; they are not described as a new full matrix.

Evidence: [recovery command checks](repair-held-recovery.json) and [six optional-module cases](optional-module-matrix.json). Exact production and disposable-fixture JAR hashes are recorded. All replay, recovery and matrix server processes exited and were stopped. The user's actual server directory was read-only throughout this work.
