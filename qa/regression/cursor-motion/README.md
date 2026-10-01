# Moving-cursor inventory regression

This fixture reproduces a native-client cursor ghost that stationary click tests missed. It uses all supplied native client mods and the original server mods, addon, separate Chalk shim, Polymer, and selected combined shim from the current local test kit. Every run starts fresh disposable server/client processes. It never opens the user's world or player files.

The six Survival inventory inputs are diamonds ×16, empty maps ×3, one filled map, a MapStitch atlas, a damaged diamond pickaxe, and a damaged pickaxe with an SSO repair count. `CURSOR_QA_CORRUPTED=true` gives each synthetic stack an explicit calcite repair component, reproducing the relevant historical saved-item state without copying user data.

The client calls the real `InventoryScreen.mouseMoved`, `mouseClicked`, `mouseDragged`, and `mouseReleased` methods. SDL button 1 means left mouse in Minecraft 26.3. A two-pixel move while placing adds the same target slot to quick crafting, producing QUICK_CRAFT start/add/end packets. `CURSOR_QA_HOLD=true` instead keeps the press active across three client ticks with movement each tick. The screen's `isQuickCrafting` and selected slot count are observed. Client cursor/slot updates and server click packets are recorded without modifying them. Each move observes the cursor for 41 ticks after placement begins and requires the final cursor to be empty; server validation checks exact item counts, map identity, damage, repair count, and repair material.

Minecraft 26.3 renders the cursor directly from the menu's carried stack. The failure screenshot and traces show a nonempty carried stack while the same count is already in its destination slot. This is a synchronization failure, not a separate retained screen dragging-item object.

Run from the repository root, using the existing local launcher fixture prerequisites:

```sh
CURSOR_QA_CORRUPTED=true python3 qa/regression/cursor-motion/run.py unique-suite-name /path/to/combined-shim.jar
```

Options:

- `CURSOR_QA_MOTION=false`: omit the drag event while clicking/releasing; stationary-click control.
- `CURSOR_QA_HOLD=true`: hold each click for three client ticks with movement.
- `CURSOR_QA_DELAY_MS=100`: TCP proxy adds 100 ms in each direction.
- `CURSOR_QA_ORIGINAL_ONLY=true`: remove Polymer and all server shims from the disposable copy; retain original mods and Tool Pouch atlas/elytra addon.

`export_evidence.py` exports the named result set into `qa/evidence/cursor-motion-1.0.1.json`. It exports synthetic observations and production artifact hashes; it does not export credentials, launcher commands, full logs, worlds, or player data. Runtime folders are ignored by Git. The launcher uses the historical offline QA identity and never reads launcher account credentials.

Scope: direct screen-event automation, not operating-system mouse event replay. Physical button events are suppressed in the disposable QA window to prevent accidental clicks. Tests cover left-click same-slot movement and a three-tick hold in the player's inventory. They do not claim right-click, multi-slot drag distribution, arbitrary high latency, or chest-screen coverage. All successful runs use fresh JVMs; reconnect-only stale client defaults are not a passing scenario established by these tests.
