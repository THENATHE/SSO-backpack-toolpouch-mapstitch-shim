# Tiered Backpacks 1.0.20 compatibility verification

Recorded 2026-10-02 for **Multi-Shim 1.0.3+26.3** and the official **Tiered Backpacks 1.0.20+26.3 / Fabric** release. The existing shim binary is used throughout; no production class, original JAR or baseline dependency checkout was changed.

**Result: compatible in all tested profiles. No shim update is required.** Continue using Multi-Shim 1.0.3+26.3.

## Exact targets

| Artifact | SHA-256 |
| --- | --- |
| Multi-Shim 1.0.3+26.3 | `d5d08c6a5164206c9c2d47a58a89fec5f56ae03c7fcb12cbe04c98ed7d259722` |
| Tiered Backpacks 1.0.20+26.3 | `b129b9b61ec1da62842dc2dfa5b166a6df8e91c796666dcee70f7b8acf1ecc33` |

[Official upstream release](https://modrinth.com/mod/tiered-backpacks/version/GHnqX59p) · [Recorded release metadata](upstream-version.json) · [Binary/API audit](audit.json).

Both upstream binary and source downloads matched the publisher's SHA-512 hashes. Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 and Polymer Bundled 0.18.2+26.3 were used. Developer SSO 2.9.14 and existing SSO port 2.9.14-port.1 retain separate dependency sets. Exact server/client mod inventories are in the evidence.

## Upstream changes

Thirty-seven of 39 classes are byte-identical to the previously tested 1.0.19. The two changed classes draw the backpack using vanilla container textures and adjust slot coordinates. Their public APIs are unchanged. All shim target classes, network payloads, component codecs, item/recipe registrations and fallback item assets are unchanged; required dependencies are unchanged too.

The shim's old `suggests` entry mentions 1.0.19. Fabric Loader 0.19.5 does not treat `suggests` as a requirement or emit a version-mismatch warning for it. The actual 1.0.20 profiles start successfully. The compile-input lock remains pinned to the original 1.0.19 build provenance; the new runtime target is recorded separately here rather than rewriting the old release record.

## Runtime results

| Check | Result |
| --- | --- |
| Developer optional modules | All eight combinations containing Tiered Backpacks passed startup, real reload, safe fallbacks, preserved original item identities and applicable SSO recipe checks. [Evidence](evidence/matrix-developer.json). |
| Existing SSO-port combinations | All four combinations containing both Tiered Backpacks and SSO passed independently with the port's own dependencies. [Evidence](evidence/matrix-sso-port.json). |
| Creative and commands | All 72 actual network cases passed: six tiers, operator/nonoperator Creative acquisition and player-issued give commands across Overworld, Nether, End and a custom dimension. [Evidence](evidence/creative.json). |
| Recipe-book networking | Native client decoded the full 1,078 recipe collections and remained connected. [Evidence](evidence/recipe.json). |
| Storage, serialization and mixed clients | Both SSO tracks passed 1,767 server assertions each and actual native, native-with-Polymer and unmodified vanilla client sessions. All six tiers passed transfers, component preservation, attachment and reopen checks. [Developer](evidence/storage-developer.json), [SSO port](evidence/storage-sso-port.json). |
| Current companion addon | Storage/client tests on both tracks included released Atlas/Elytra addon 1.0.2+26.3 (`e08a8a45c2a89b4a7d64fadf27dd20e6a2bf1b1635da50c64333b8bd42de3748`). This verifies coexistence; the addon repository retains its separate XP-Mending tests. |
| Native GUI/resource-pack rendering | All six tiers passed default-resource inspection and six more screenshots passed with client-side Polymer and custom vanilla chest/slot textures. [GUI fixture, screenshots and limits](gui/README.md). |

## Reproduction

The official JAR belongs in the ignored `fixtures/` directory for these commands. Existing baseline libraries and staging profiles stay intact.

```sh
python3 qa/matrix/run.py tiered20-developer build/libs/SSO-backpack-toolpouch-mapstitch-shim-1.0.3+26.3.jar --tiered-jar qa/tiered-1.0.20/fixtures/tiered_backpacks-fabric-1.0.20+26.3.jar --masks 2,3,6,7,10,11,14,15
python3 qa/matrix/run.py tiered20-port build/libs/SSO-backpack-toolpouch-mapstitch-shim-1.0.3+26.3.jar --tiered-jar qa/tiered-1.0.20/fixtures/tiered_backpacks-fabric-1.0.20+26.3.jar --track sso-port --masks 3,7,11,15
TIERED_QA_JAR=qa/tiered-1.0.20/fixtures/tiered_backpacks-fabric-1.0.20+26.3.jar python3 qa/regression/storage/run.py tiered20-storage build/libs/SSO-backpack-toolpouch-mapstitch-shim-1.0.3+26.3.jar
```

The Creative and recipe runners accept the same `TIERED_QA_JAR` override. Storage additionally accepts `SSO_QA_TRACK=sso-port` and `STORAGE_QA_PORT` for isolated parallel profiles. GUI and addon replay options are documented in the regression runner/GUI fixture. Tests use disposable localhost worlds and cached game libraries; no user server, save or configuration was changed. Raw worlds, launch commands and downloaded original JARs remain excluded from this repository.

Use matching Tiered Backpacks 1.0.20 Fabric 26.3 on the server and native clients for the tested target. This verification does not certify NeoForge/other Minecraft builds, arbitrary third-party GUI packs, third-party accessory integrations, mixed old/new native clients or long-duration production-world migrations. No Minecraft version port of Tiered Backpacks is part of this release inventory. Earlier 1.0.19 verification and original source/build records remain available.
