# Validation — 1.0.3+26.3

Recorded 2026-10-02. Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Polymer Bundled 0.18.2+26.3, developer Tool Pouch 1.1.10+26.3. Exact original dependencies remain unchanged and are recorded in the evidence and separate [developer](tracks/developer/runtime.lock.json) / [SSO-port](tracks/sso-port/runtime.lock.json) locks.

Combined shim SHA-256: `d5d08c6a5164206c9c2d47a58a89fec5f56ae03c7fcb12cbe04c98ed7d259722`.

The unchanged atlas/elytra addon 1.0.1 SHA-256 is `77dc668b164bca182eec1e558b2948bd98ee40545ef30881bcdd471f43af1b0a`.

## What reproduced

The reported sequence was an equipped leggings pouch, X quick menu, withdrawal of beds and inventory rearrangement. A clean single opening with three uniquely marked beds passed on both original Tool Pouch alone and the released 1.0.2 combined stack: each bed remained one item after closing and reopening, and settled client counts matched the server. This basic sequence did not establish the cause of the user's incident.

Repeated activation of the original X key mapping while rearranging those beds did reproduce permanent bed duplication in both controls. Traces show additional open requests reaching an already open shulker. This is deliberate key-mapping stress, not a claim that a physical keyboard automatically repeated in the user's incident. It invokes the native widget and real packets; physical keyboard hardware was not tested.

Separate controlled cases established these inherited Tool Pouch defects:

| Trigger | Authoritative result before the fix |
| --- | --- |
| Withdraw, then open the child again before closing | Stored contents were copied before the old menu saved: one sword and seven diamonds became two swords and fourteen diamonds. |
| Deposit an unstackable item, then reopen before closing | The stale snapshot discarded the deposited pickaxe. |
| Move, drop or swap source pouches while a child is open | Closing saved to the newly preferred pouch. The second pouch's shulker was overwritten, losing its items and duplicating the first shulker's contents. |
| Reorder shulkers in the open parent, then open a child | The opener read the parent before its edits saved, duplicating one child and losing another. |

The expanded nine-case control had 16 failing conservation observations out of 18, on both the original-only and combined profiles; ordinary withdrawal conserved contents. Those observations include after-close and after-reopen checks of the same cases, not 16 independent defects. Settled client inventories agreed with the server even in the failing cases: these were persistent server-side changes, not merely ghost icons.

No general “refill from inventory” feature was identified in the tested Tool Pouch, backpack or addon sources. An unspecified third-party refill feature/modpack was not available for testing, so no conclusion is drawn about it.

## Repair

The server now closes/saves the previous menu before selecting a child and rejects invalid or absent selections. Each child session binds the original owner stack and its physical slot, rather than resolving the currently preferred pouch during saveback. Every edit is saved immediately; writes preserve unrelated current parent contents and require the expected child still to be present. The actual shulker slot rejects nesting its owning pouch/leggings inside itself.

If the owner is dropped, picked up onto the cursor or replaced, the child menu closes once its binding is no longer valid. The moved/dropped copy already contains the latest edits. Moving the same owner reference between inventory slots remains supported. External child replacement invalidates the old menu without overwriting the replacement. Original JARs are not modified, no dependency is replaced, and clients do not need a new addon for this server-side repair.

The repair prevents these future transactions from duplicating or losing items. Existing duplicated or lost items require manual correction or recovery from a known-good world backup; their original history cannot be inferred reliably from the resulting inventory.

## Exact release regressions

| Pouch/shulker check | Result and evidence |
| --- | --- |
| Original-only and released controls | Nine scenarios, 18 conservation observations per profile; 16 persistent mismatches each. [Original](qa/evidence/1.0.3/shulker-original-baseline.json), [1.0.2 stack](qa/evidence/1.0.3/shulker-1.0.2-baseline.json). |
| Exact basic leggings/X/bed sequence | Two observations per control, both conserved and matched the client. [Original](qa/evidence/1.0.3/beds-original-single-x.json), [1.0.2](qa/evidence/1.0.3/beds-1.0.2-single-x.json). |
| Repeated native X activation with beds | Both controls permanently duplicated beds: two failing after-close/reopen observations each, with client/server agreement. [Original](qa/evidence/1.0.3/beds-original-repeated-x.json), [1.0.2](qa/evidence/1.0.3/beds-1.0.2-repeated-x.json). |
| Fixed developer stack | Eleven scenarios, all 22 conservation observations passed with zero settled client/server mismatches. [Evidence](qa/evidence/1.0.3/shulker-developer.json). |
| Fixed SSO-port stack | Independently repeated all 22 observations with its exact port and separate dependencies: zero conservation or client mismatches. [Evidence](qa/evidence/1.0.3/shulker-sso-port.json). |
| Native process restart | Both server and client processes restarted from each saved world without reseeding. Two observations per track passed after reload and another child reopen; server/client counts agreed. [Developer](qa/evidence/1.0.3/shulker-developer-restart.json), [SSO port](qa/evidence/1.0.3/shulker-sso-port-restart.json). |
| Closed-shulker bed rearrangement and guards | Three conservation/client-agreement observations passed, including bed pickup/place in the normal InventoryScreen after closing the child. Six directed server groups passed: negative/MAX/missing selection; immediate save/empty physical-slot gap; unrelated parent update; external child replacement; owner reordering; owner-nesting rejection with legal-armor control. [Evidence](qa/evidence/1.0.3/shulker-boundaries-and-bed-inventory.json). |

The boundary helpers use an isolated synthetic server player with a test-only seeded native capability. The native gameplay rows use actual connected clients and packets. The current fixture includes the supplemental normal-inventory movement in its full run; the recorded 22-observation full runs precede that fixture addition and are supplemented explicitly above.

| Broader regression | Result |
| --- | --- |
| Developer optional-module matrix | All 16 subsets passed startup, reload, original identity/fallback checks and bounded SSO gameplay checks where installed. [Evidence](qa/evidence/1.0.3/matrix-developer.json). |
| Existing SSO port | All eight subsets containing SSO passed against the exact existing SSO 2.9.14-port.1+26.3 and its separate dependencies. [Evidence](qa/evidence/1.0.3/matrix-sso-port.json). |
| Storage and native/vanilla networking | 1,767 assertions passed, plus actual native, native-with-Polymer and unmodified vanilla client sessions. Includes six backpack tiers, source/serialization preservation, lore, storage withdrawal/reopen, fallback guards and atlas coexistence. [Evidence](qa/evidence/1.0.3/storage-network.json). |
| Atlas/minimap and crafting | 39 native client and 38 server assertions passed, including inventory/leggings pouch rendering, metadata repair, 144-item preservation, dimension cache invalidation and real crafting with one seed map consumed. [Evidence](qa/evidence/1.0.3/atlas.json). |

Build: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew --offline --no-daemon build -PcompilerVersion=27`, producing Java 25 bytecode. Original compile hashes are enforced by `verifyOriginalInputs`. Optional Tool Pouch mixins remain disabled when the original mod is absent.

## Evidence, reproduction and limits

[Runnable shulker fixture](qa/regression/pouch-shulker/README.md) · [Optional matrix](qa/matrix/README.md) · [Storage fixtures](qa/regression/README.md).

Accounting includes unique item names, durability, nested containers, inventory, cursor and dropped entities. Authoritative conservation is measured after closing the child, avoiding false double-counting of its live menu and backing snapshot. Client totals are compared after synchronization. Profiles are disposable localhost test worlds; no user world or original mod JAR was changed. Exact hashes and sanitized traces are published; local worlds, raw launch commands and game libraries are not.

The broader [1.0.2 QA record](https://github.com/THENATHE/SSO-backpack-toolpouch-mapstitch-shim/blob/6d159bc7631962c135deda4fab9b5b48b6a51fa9/VALIDATION.md) documents the atlas, controls, lore, recipe, Creative, delayed-cursor, repair and elytra findings/tests. Those historical results are not relabeled as new 1.0.3 tests. Atlas/controls/lore fixes remain included; addon 1.0.1 remains current on server/native clients.

This is bounded regression testing, not a guarantee against every modpack interaction. Third-party accessory APIs, arbitrary refill mods, physical keyboard hardware and long-duration multiplayer load were not exercised. SSO retains Type B vanilla gameplay; other modules retain Type A display/guards and matching-original native gameplay. No new feature limitation or dependency removal was introduced.

The prior 1.0.1 and 1.0.2 releases remain available in the standalone Multi-Shim build family. The new release retains separate source snapshots, dependency locks and release copies for developer targets and the existing SSO port.

## Additional runtime target: Tiered Backpacks 1.0.20

On 2026-10-02 the unchanged released 1.0.3 binary passed independent compatibility verification against official Tiered Backpacks 1.0.20+26.3. Both SSO tracks and current Atlas/Elytra addon 1.0.2 were covered. Results: twelve applicable optional-module combinations, 1,767 storage/serialization assertions per track, native/native-with-Polymer/vanilla clients per track, 72 Creative/command cases, 1,078 recipe collections and twelve GUI screenshots. No production fix or new shim build was needed. Exact artifacts, test evidence and scope limits are in the [separate compatibility report](qa/tiered-1.0.20/README.md); the original release provenance and historical results above are preserved.
