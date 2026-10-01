# Moving-cursor ghost reproduction and 1.0.1 verification

Date: 2026-10-01. Combined shim 1.0.1+26.3 SHA-256: `55787adf8c25f0b3b0891a40f8441d8247d9599d9312f9b9f2eb2cfdaf2745b8`.

The ghost was reproduced on 1.0.0 when a native client moved the mouse during placement of a stack with the historical incorrect explicit calcite repair component. The same input state and moving screen-event sequence passed on the already released 1.0.1. No further production change was necessary for this reproduced failure.

## Causal trace

The failing synthetic input was 16 diamonds with an explicit calcite-only `minecraft:repairable` component. The previously identified Defaulted/Chalk projection bug in 1.0.0 made calcite the native client's default repair component, so its decoded item patch omitted that component. Server and client consequently described the same item using different component patches.

A two-pixel drag inside the destination slot was sufficient to select that slot for quick crafting. Actual outgoing traffic was QUICK_CRAFT start, add-slot, end. The client predicted the final carried stack as empty. During the intermediate phases, the server sent two corrections containing 16 carried diamonds. At the end the server had 16 diamonds in destination slot 9 and an empty cursor, matching the final predicted empty cursor; it sent a destination slot correction but no new empty-cursor correction. The queued intermediate corrections then restored 16 diamonds to the client cursor. Both target slot and cursor visibly showed 16 diamonds at the screenshot taken after 30 ticks, and the cursor still contained that stack after 41 ticks. The server had only one real stack.

This explains why the movement matters: even motion within one slot changes a placement into a three-packet quick-craft operation. The stationary control emitted zero QUICK_CRAFT packets and passed with the same affected item state on 1.0.0.

## Matrix

Every passing run moved six item types: diamonds, empty maps, a filled map, a MapStitch atlas, a damaged diamond pickaxe, and an SSO previously repaired pickaxe. Fresh item cases used their server defaults. Affected-state cases set an explicit calcite repair component on synthetic copies of all six inputs; no user inventory, world, or player file was loaded.

| Server stack | Input state | Mouse sequence | Result |
| --- | --- | --- | --- |
| Original mods + Polymer/shims 1.0.0 | Fresh defaults | Same-slot left-click drag/release | PASS, six items |
| Original mods + Polymer/shims 1.0.1 | Fresh defaults | Same-slot left-click drag/release | PASS, six items |
| Original mods + Polymer/shims 1.0.0 | Explicit calcite override | Same-slot left-click drag/release | FAIL: first diamonds reproduced cursor ghost; stopped at failure |
| Original mods + Polymer/shims 1.0.0 | Explicit calcite override | No drag event between press/release | PASS, six items |
| Original mods + Polymer/shims 1.0.1 | Explicit calcite override | Same-slot left-click drag/release | PASS, six items |
| Original mods + Polymer/shims 1.0.1 | Explicit calcite override | Three-tick hold with movement; 100 ms proxy delay each direction | PASS, six items |
| Original mods and atlas/elytra addon, no Polymer or shims | Explicit calcite override | Same-slot left-click drag/release | PASS, six items |

The 1.0.1 affected-state tests passed without `/sso-shim repair-held`: synchronization became consistent even while the deliberately incorrect repair material remained. That command is still needed to restore the intended repair material on affected saved items.

## Evidence and limits

[Sanitized machine-readable observations](cursor-motion-1.0.1.json) include artifact hashes, client/server outcomes, quick-craft counts, packet traces and cursor observations. Some historical raw success messages use the generic word “moving”; the exported per-case mode and actual packet counts distinguish the no-drag control. [Fixture and run instructions](../regression/cursor-motion/README.md).

The client fixture invokes real screen methods and real multiplayer packet paths. It does not replay OS input. Coverage is Survival left-click movement within one slot, including a three-tick held click and bounded latency, in the player inventory. It is not evidence for right-click, multi-slot distributions, every container UI, every possible network condition, or unrelated client mods. All tested native clients started fresh JVMs. Use a full Minecraft restart after updating the server; reconnect-only stale defaults were not established safe here.
