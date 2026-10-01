# Shift-use Mending validation — combined shim 1.0.1+26.3

Tested the exact released combined JAR SHA-256 `55787adf8c25f0b3b0891a40f8441d8247d9599d9312f9b9f2eb2cfdaf2745b8`, with all supplied original native client mods and the full server Polymer/shim/mod/addon stack. Separate fresh JVMs and disposable worlds were used; no user server, client instance or world was modified. Production artifacts remained unchanged.

## Default configuration: passed

The native client activated Shift and called the normal `gameMode.useItem` path (right-click in air, not use-on-block). This exercises native Fabric callbacks, client prediction, packet transport, server repair and resulting native inventory synchronization. Items were deliberately seeded to isolate use behavior; their acquisition/enchanting was not retested here.

- Mending diamond pickaxe: damage **780 → 259**, diamonds **64 → 63**, repair count **0 → 1**.
- Mending + Efficiency I + Unbreaking I, with matching stored enchantments on the whetstone: same exact repair and consumption; all enchantments retained.
- An earlier incompatible whetstone did not prevent selecting a later compatible whetstone.
- Three same-tick uses repaired damage **1560 → 0**, consumed exactly **3** diamonds, and set repair count **3**. The feature adds no repair cooldown; one material per successful use is expected.
- A damaged Mending bow repaired damage **383 → 255**, consumed one string and synchronized correctly.
- No Shift, no material, no Mending, incompatible whetstone and an explicitly seeded active cooldown produced no repair or consumption.
- All twelve default-case final client/server snapshots agreed on target damage, materials, repair count and first whetstone damage. Enchantments remained intact. Whetstone degradation is randomized by the original recipe; observed wear remained within the original per-use behavior. XP was not independently asserted.

The two additional default cases began with SSO's `BROKEN=true` flag. With default `autoRepairOnBreak=true`, the server automatically repaired each item before the manual use. The client saw `BROKEN=false` by click time. One subsequent click repaired it again, totaling two repairs/two materials. These demonstrate automatic-plus-manual behavior, **not manual repair of a still-broken native item**.

[Default observations and exact mod hashes](mending-use-native-defaults-1.0.1.json).

## Isolated still-broken manual use: upstream native-client limitation

To isolate manual use, a separate focused run disabled only `autoRepairOnBreak` in disposable client/server memory. The positive control continued to repair normally. A still-broken diamond pickaxe and bow both returned native-client `FAIL` before any use packet reached the server: no repair, no consumption, no inventory disagreement. Their `BROKEN=true` flag was verified on both sides.

An identical original-mods-and-addon-only server, without Polymer or shims, reproduced both blocked cases. The source and callback audit explains this: original SSO rejects use of broken items in its client Fabric callback; Fabric cancels before transmitting the packet. The server shim cannot handle a packet that was never sent. Default automatic repair normally handles these items before the manual-use path is needed.

[Combined-server isolated observations](mending-use-native-manual-broken-1.0.1.json) · [Original-mods control](mending-use-original-manual-broken.json).

No production JAR changed. This validation supports normal damaged-item Shift repair on the supplied native stack, while documenting the explicit still-broken/manual-only limitation. It does not cover offhand use, use-on-block interactions, every modded subclass, or independently assert XP behavior.
