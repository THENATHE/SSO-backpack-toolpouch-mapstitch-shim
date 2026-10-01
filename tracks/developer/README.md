# developer compatibility track

Minecraft 26.3, Fabric Loader 0.19.5, Java 25. Exact runtime JAR identities and hashes are in `runtime.lock.json`. The developer track uses actual developer originals. The sso-port track substitutes only the pre-existing ChatGPT SSO 2.9.14-port.1+26.3 artifact and its separately documented dependency stack. Chalk is independently installed and is not a combined module.

The single combined binary is independently verified against both targets; it is not an untested relabeling. Shared source is fixed by the release tag, with separate immutable source snapshots and release copies alongside each track's local release. Compile inputs for this common binary are pinned in the root `dependencies.lock.json`; port runtime dependencies remain isolated from developer-only Defaulted/CodecUI.

See ../../VALIDATION.md for exact track-specific native and vanilla-wire checks. No other original-mod Minecraft version port exists in this release inventory. Existing same-version feature modifications are not substitutes for these originals.
