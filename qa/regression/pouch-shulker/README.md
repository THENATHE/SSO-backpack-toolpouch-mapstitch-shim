# Native pouch/shulker conservation regression

This fixture distinguishes permanent duplication/loss from stale client inventory displays. It uses the unchanged developer Tool Pouch 1.1.10+26.3, Minecraft 26.3, Java 25 and Fabric Loader 0.19.5. The original-only control has Fabric API, Kotlin and Fzzy Config; the combined profile uses the original four mods, Polymer, the selected shim and the separate atlas/elytra addon. Exact input hashes are recorded for every run.

From the combined shim source directory:

```sh
python3 qa/regression/pouch-shulker/run.py --track original --label original-control
python3 qa/regression/pouch-shulker/run.py --track released --label released-control
python3 qa/regression/pouch-shulker/run.py --track released --label candidate --shim build/libs/SSO-backpack-toolpouch-mapstitch-shim-1.0.3+26.3.jar --expect-conserved
```

The runner also accepts `--track sso-port` for the separately maintained SSO-port profile, `--boundaries` for directed server guard checks, and `--resume` with the same label to restart its saved world and verify persistence without reseeding. Use `--expect-conserved` for every candidate run.

`released` selects the combined profile, including when `--shim` supplies a new candidate. Without that option, its control uses the preserved 1.0.2 release. `--case leggings-beds` isolates the reported equipped-leggings/bed workflow; `--case leggings-beds-x-repeat` adds another X activation while the child screen is open. These cases drive the original native key mapping/widget code and actual menu click packets; they do not test physical keyboard hardware. Other cases send the original open payload directly.

The fixture seeds uniquely named/damaged items and counts them recursively in the authoritative player inventory, cursor and nearby dropped item entities after closing and reopening the menu. It does not count the detached live child menu and its stored backing snapshot together. Settled client totals are compared with the server. Conservation checks include item identity and durability as well as count, so an overwritten second shulker cannot pass merely by retaining the same number of item stacks.

Cases cover ordinary withdrawal; repeated open before closing; moving, dropping and swapping source pouches; deposits before reopen; editing the parent pouch before opening its child; moving/dropping after withdrawal; and beds withdrawn from a leggings-attached pouch using X, then rearranged with pickup/place clicks. Candidate runs must pass `--expect-conserved`; control runs intentionally retain failure evidence.

The runner uses disposable localhost servers (ports 25876/25877 by default), the workspace graphics session and existing cached game/loader classpaths from the earlier MapStitch harness. It copies the existing accepted test EULA. No user world, launcher account credentials or original mod JAR is changed. Original JAR hashes are checked again after shutdown. Raw worlds, logs, fixture JARs and launch commands remain ignored under `runs/`; sanitized published evidence contains artifact hashes, request/menu traces and item-accounting results. Recreating the local dependency/cache prerequisites is required on another machine.
