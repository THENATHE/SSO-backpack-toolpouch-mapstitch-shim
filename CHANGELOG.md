# Changelog

## 1.0.1+26.3 — 2026-10-01

Fix native-client default repair-material corruption when SSO/Defaulted coexist with Polymer-hidden Chalk items. An emptied Chalk selector could incorrectly apply the calcite repair rule to every client item. Native Creative packets could then persist that wrong component, making enchanted whetstone repairs reject the correct material.

Outgoing Defaulted patch selectors now use only the recipient's visible targets. A scoped patch with no client targets stays inapplicable. True wildcard patches, generators, values, priority and server-side rules are retained. Original mod/dependency JARs and separate Chalk behavior remain unchanged.

Add operator-only `/sso-shim repair-held` for explicit recovery of an already saved unexpected calcite override. The command restores the item's server-default repair material and preserves all other components. No automatic migration runs; deliberately customized calcite overrides require operator judgment.

Fully restart native clients after updating the server. Exact reproduction, corrected acquisition/enchantment cases, recovery tests and regressions are documented in [VALIDATION.md](VALIDATION.md).

## 1.0.0+26.3

Initial four-module release. Superseded by 1.0.1 because its tests missed the repair-material corruption described above. Historical artifacts and validation records remain preserved.
