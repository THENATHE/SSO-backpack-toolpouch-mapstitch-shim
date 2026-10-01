# Sanitized item-state and recovery replay

`UserStateQa` reconstructs only the relevant item components; it never reads player files or world data. It compares direct original recipe matching, the actual recipe manager and 2×2/3×3 server menu results for 16 combinations of enchantment state, target repair override, material repair override and material count.

Point `SSO_USERSTATE_SERVER` at an authorized local server directory. The harness reads only `mods/*.jar` and `config/simple_smithing_overhaul/config-v2.toml`, copying them into fresh disposable run directories. All test servers bind to localhost ports 25814/25815. An accepted local QA EULA and cached Minecraft/loader libraries are reused.

```sh
SSO_USERSTATE_SERVER=/path/to/authorized/server python3 qa/whetstone-userstate/run.py fresh-replay-name
```

The default compares the exact original stack without the combined shim against that same stack with the released shim. All other installed mods remain fixed. The test-only fixture is additional to both.

For a candidate and the opt-in recovery command checks:

```sh
SSO_USERSTATE_SERVER=/path/to/authorized/server \
SSO_USERSTATE_CANDIDATE=build/libs/<candidate>.jar \
SSO_USERSTATE_RECOVERY=1 \
python3 qa/whetstone-userstate/run.py fresh-recovery-name
```

Set `SSO_USERSTATE_TRACK=sso-port` for the separately retained port and its exact runtime dependency stack without Defaulted/CodecUI. Recovery checks execute the actual command with OP and non-OP permission sets on disposable server-side test players; they do not claim real graphical client input. `RepairHeldChecks` verifies component preservation, selected-item scope and legitimate/custom negative cases.

Every run retains config/mod hashes, observations, actual installed mods and server exit status. Raw runs and dependency JARs are ignored by Git. Publish only sanitized evidence such as `qa/evidence/1.0.1/`, never user server/player files.
