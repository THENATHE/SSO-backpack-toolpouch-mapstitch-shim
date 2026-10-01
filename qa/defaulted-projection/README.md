# Defaulted selector projection checks

Run `python3 qa/defaulted-projection/run.py <fresh-suite> <candidate.jar>` in the existing workspace. The runner needs the private bundle staging dependencies and cached Fabric/Minecraft classpath; it starts a temporary localhost server on port 26086 and stops it after the assertions finish. No user server directory is changed.

The fixture uses real Defaulted 1.3.8 packets and the loaded server Chalk tag. It checks global empty-list and all-empty-holder-set semantics, visible targets, mixed visible/hidden targets, hidden-only targets, fully visible Chalk targets, generator/component/priority preservation, and non-mutation of the original packet, selectors and server tag. A test-only proxy generator throws if projection attempts to execute it.

The implementation projects only outgoing Defaulted selectors for a connection with the consolidated registry mapping. It leaves server patch application and original dependency JARs intact. Scoped hidden-only patches are omitted; visible targets become an explicit nonempty direct set. This prevents Defaulted's all-empty-target selector from becoming a global patch after Polymer filters a tag. Original global patches retain Defaulted's existing semantics.

Sanitized results are in `qa/evidence/defaulted-projection-1.0.1.json`. Full server logs and dependency hashes remain in the ignored run folder. These focused assertions supplement actual native-client gameplay tests; they do not replace them.
