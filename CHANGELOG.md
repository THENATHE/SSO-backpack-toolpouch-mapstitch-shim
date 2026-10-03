# Changelog

## 1.0.2+26.3 — 2026-10-02

- Restore missing or incorrect atlas seed-map centers from saved map data; recover stale active-map selections, preserving map contents and metadata.
- Keep atlas crafting to one consumed seed map; rebuild each preview from current ingredients and reject nonexistent saved map data.
- Preserve custom backpack/pouch lore in vanilla fallback items, including 256 custom entries plus generated container/dye tooltip lines, without packet conversion overflow.
- Expand runtime QA for map rendering/crafting, item lore, native storage, optional modules and both SSO target tracks. Retain baseline failures and exact artifact evidence.
- Package releases under the standalone Multi-Shim family with separate developer and SSO-port records. The companion addon 1.0.1 carries the controls-category and world-cache fixes.


## 1.0.1+26.3 — 2026-10-01

Fix native-client default repair-material corruption when SSO/Defaulted coexist with Polymer-hidden Chalk items. An emptied Chalk selector could incorrectly apply the calcite repair rule to every client item. Native Creative packets could then persist that wrong component, making enchanted whetstone repairs reject the correct material.

Outgoing Defaulted patch selectors now use only the recipient's visible targets. A scoped patch with no client targets stays inapplicable. True wildcard patches, generators, values, priority and server-side rules are retained. Original mod/dependency JARs and separate Chalk behavior remain unchanged.

Add operator-only `/sso-shim repair-held` for explicit recovery of an already saved unexpected calcite override. The command restores the item's server-default repair material and preserves all other components. No automatic migration runs; deliberately customized calcite overrides require operator judgment.

Follow-up validation also reproduced moving-click ghost items caused by these mismatched defaults and saved explicit calcite overrides. The existing 1.0.1 binary keeps those item hashes synchronized; see [cursor reproduction](qa/evidence/cursor-motion-1.0.1.md).

Fully restart native clients after updating the server. Exact reproduction, corrected acquisition/enchantment cases, recovery tests and regressions are documented in [VALIDATION.md](VALIDATION.md).

## 1.0.0+26.3

Initial four-module release. Superseded by 1.0.1 because its tests missed the repair-material corruption described above. Historical artifacts and validation records remain preserved.
