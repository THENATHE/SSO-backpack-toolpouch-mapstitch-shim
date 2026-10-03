# Tiered Backpacks 1.0.20 native GUI compatibility probe

This fixture checks the official 1.0.20 client GUI with the released Multi-Shim 1.0.3. It is QA material, not an installable gameplay addon.

`make_pack.py` produces a small artificial resource pack from code, with no copied or downloaded artwork. It replaces the vanilla chest texture at `assets/minecraft/textures/gui/container/generic_54.png` and vanilla slot sprite at `assets/minecraft/textures/gui/sprites/container/slot.png`. Orange borders, cyan panels, and magenta slot outlines make resource-path selection visible. Resource format is 97.1 for Minecraft 26.3.

The storage fixture captures `screenshots/backpack-tier-0.png` through `backpack-tier-5.png` after each of the six native BackpackScreen menus has been open for 25 client ticks and before withdrawing its contents. The ordinary native client uses default resources; the native-plus-Polymer client uses the probe pack via `TIERED_QA_GUI_PACK`. The same suite checks ordinary withdrawal, closing, reopening, and content conservation.

Source review: the developer changed BackpackScreen from the mod-specific background sprite to fixed-coordinate pieces of vanilla `generic_54.png`, and draws a vanilla `container/slot` sprite for every actual menu slot. Multi-Shim does not replace client GUI rendering; its server-side open-screen guard allows original-mod clients to use the native screen.

Scope: the probe tests the six configured default tier layouts and replacement resources using vanilla texture coordinates. It does not establish compatibility with every third-party GUI pack, altered chest UV layout, custom dimension configuration, or GUI scale. Render results and artifact hashes are recorded in `evidence.json` after the runtime completes.

## Observed result

PASS: all twelve 1280×800 screenshots were visually inspected. Default resources render the native panels, item icons, labels, and slot grids correctly for the six default tiers (3×9, 4×9, 5×9, 6×9, 6×11, 6×13). With the QA pack and client-side Polymer, every tier visibly uses the orange/cyan vanilla chest replacement and magenta vanilla slot replacement. Automated screenshot checks also found more than 100 exact pixels of each marker color in every packed screenshot. Both clients passed the ordinary storage interaction suite.

The tested stack includes official Tiered Backpacks 1.0.20, released Multi-Shim 1.0.3 on the server, and current Atlas/Elytra addon 1.0.2. Exact server artifacts, client manifests, screenshot hashes, and color counts are in `evidence.json`. Screenshots are retained in `screenshots/native/` and `screenshots/native-polymer/`.

Some new-player tutorial/advancement toasts cover portions of the screens, so the screenshots do not prove every pixel of every slot. The test establishes the new resource paths are active and the inspected native layouts remain usable; it does not establish compatibility with all existing resource packs.
