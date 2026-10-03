# Elytra and controls regression

This fixture connects a native Fabric client to an isolated combined-shim server, then restarts both processes and reconnects to the same world. It uses the original Tool Pouch artifact and the separately built Atlas and Elytra addon.

Run from the combined-shim repository with the local launch prerequisites described by the other runtime QA suites:

```sh
python3 qa/addon/run.py --suite elytra \
  --shim build/libs/SSO-backpack-toolpouch-mapstitch-shim-1.0.2+26.3.jar \
  --addon ../toolpouch-atlas-elytra-compat-26.3/build/libs/toolpouch-atlas-elytra-compat-1.0.1+26.3.jar
```

Repeat with `--without-mapstitch` to verify that the optional atlas dependency is absent on both sides. The default port is 25866; override it with `--port`. Runs replace the suite's disposable `runs/` and `control/` directories, so preserve evidence before running another configuration.

The 101 assertions cover:

- Actual vanilla `KeyBindsList` entries: exactly one translated Tool Pouch category heading, one toggle binding, the upstream category instance, and an unbound default.
- Registered key activation, production C2S toggle and S2C preference payloads, actionbar feedback, and functional/cosmetic pouch lookups.
- Pouches in inventory and attached to leggings; disabling pouch flight while preserving chest Elytra flight.
- Respawn, full process restart/reconnect, and synchronization after a real server-driven Nether dimension transition.
- Non-operator `/toolpouch-elytra`, `on`, and `off` commands, including repeated `off` remaining disabled.

The original addon 1.0.0 fails the first controls-heading assertion with two Tool Pouch headings. Its baseline is recorded in `qa/evidence/keybind-heading-baseline.json`.

This is an instrumented Fabric client test. Key presses are injected through `KeyMapping.click`; the fixture does not simulate a physical keyboard or claim an unmodified vanilla-client test. The controls check inspects the real menu widgets rather than taking a screenshot. Each run records exact artifact hashes and checks that its inputs were not modified.
