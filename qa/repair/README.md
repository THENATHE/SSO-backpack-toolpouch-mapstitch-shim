# Native SSO packet regression fixtures

Each subdirectory contains the preserved test-only server/client fixtures and a runner adapted for the combined shim. Run `python3 qa/repair/whetstone-qa/run.py <fresh-suite> <candidate.jar>` from the workspace project. Requires the exact local baseline staging directory and offline client/server caches from `Minecraft/polymer-shim-test-bundle`, plus graphical display access; downloaded game files and native-client launch credentials are not published.

`WHETSTONE_QA_SCENARIO=inventory|table|commands` selects the repair route. The anvil runner checks Survival input placement and output collection. The Creative runner checks native whetstone packets; `WHETSTONE_ENCHANTED=true` enables its stored-enchantment case. Results preserve per-side artifact hashes. Chalk stays installed with its standalone shim, and the four former shims are removed.
