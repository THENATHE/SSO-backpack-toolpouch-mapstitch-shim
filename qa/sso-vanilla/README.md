# SSO gameplay through vanilla registry wire behavior

This harness adapts the existing standalone SSO shim's four network menu transactions into isolated runs using the unified shim, all four original base mods, and the separately retained Chalk port/shim. The original standalone QA source and evidence remain unchanged.

```sh
python3 qa/sso-vanilla/run.py developer-candidate-1 build/libs/<candidate>.jar developer
python3 qa/sso-vanilla/run.py port-candidate-1 build/libs/<candidate>.jar sso-port
```

Each fresh suite creates a disposable localhost server on port 25810 and a graphical offline QA client. Run sequentially. The developer target uses actual SSO 2.9.14 and its Defaulted/CodecUI/Fzzy/Mixson/Kotlin dependencies. The port target uses actual SSO 2.9.14-port.1 and its independently recorded Fzzy/Mixson/Kotlin stack without Defaulted/CodecUI. Candidate and all installed production/fixture JAR hashes are captured in `result.json`.

## What this client is

The client has only Fabric API and the disposable test driver; it has no SSO, other content mods or Polymer. Its fixture deliberately unregisters the global `fabric:registry/sync` configuration receiver before connection, selecting the server's vanilla registry wire behavior. It verifies the resulting item registry contains only `minecraft` entries. This is a test-only simulation of vanilla registry wire behavior, **not an unmodified official vanilla client**. Official vanilla connection evidence must be recorded separately.

The driver uses ordinary `handleContainerInput` packets and loads the exact generated server resource pack. It does not modify menu behavior or registry entries. The server fixture prepares menus and inputs, then checks authoritative results and XP after real client clicks. The first two anvil cases start with server-prepared input slots; the smithing and grindstone cases also move their input items from inventory with real click packets. A 20-server-tick delay after the local click-completion marker avoids checking before the network packet reaches the server.

## Transactions

1. Free unenchanted anvil repair: result can be taken at zero XP and leaves XP unchanged.
2. Expensive anvil enchantment combination: the client receives zero wire display cost to suppress vanilla's hardcoded limit, while the server charges the real 65 levels (100 → 35).
3. Enchantment-upgrade smithing: actual ingredient insertion and result pickup, Sharpness I → II, correct five-level XP charge, template and lapis consumed.
4. Netherite-scrap grindstone recipe: actual scrap insertion and result pickup, prior-work cost 31 → 15, enchantment retained, no XP awarded.

These four transactions do not cover every SSO gameplay feature. Native client behavior, native high-cost display, whetstone crafting, the no-base-mod matrix and official vanilla joins are separate suites. Run directories retain full client/server logs and control evidence; concise sanitized publication evidence is exported beneath `qa/evidence/`.
