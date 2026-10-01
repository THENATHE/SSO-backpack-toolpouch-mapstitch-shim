#!/usr/bin/env python3
"""Build a private local test kit from hash-pinned, pre-existing developer artifacts."""
from pathlib import Path
import json,hashlib,shutil,zipfile
P=Path(__file__).resolve().parents[1]; W=P.parents[1]
BASE=W/'Minecraft/polymer-shim-test-bundle/staging'
STAGE=P/'test-kit'; STAGE.mkdir(exist_ok=True)
JAR=P/'build/libs/SSO-backpack-toolpouch-mapstitch-shim-1.0.0+26.3.jar'
ADDON=W/'Builds/Minecraft/Tool Pouch/Feature Addon - Atlas and Elytra/1.0.0+26.3/toolpouch-atlas-elytra-compat-1.0.0+26.3.jar'
OLD=('simple-smithing-polymer-compat-', 'tiered-backpacks-polymer-compat-', 'toolpouch-polymer-compat-', 'mapstitch-polymer-compat-')
def copy_mods(source,dest,unified=False,addon=False):
 dest.mkdir(parents=True,exist_ok=True)
 for old in dest.glob('*.jar'):old.unlink()
 for f in source.glob('*.jar'):
  if not f.name.startswith(OLD):shutil.copy2(f,dest/f.name)
 if unified:shutil.copy2(JAR,dest/JAR.name)
 if addon:shutil.copy2(ADDON,dest/ADDON.name)
copy_mods(BASE/'mods',STAGE/'mods',True,True)
copy_mods(BASE/'native-client/mods',STAGE/'native-client/mods',False,True)
for case in ('chalk','simple-smithing','tiered-backpacks','tool-pouch','mapstitch'):
 for side in ('server','native-client'):
  copy_mods(BASE/'individual'/case/side/'mods',STAGE/'individual'/case/side/'mods',side=='server' and case!='chalk')
for side in ('server','native-client'):
 dest=STAGE/'individual/tool-pouch-atlas-addon'/side/'mods'
 copy_mods(BASE/'individual/tool-pouch'/side/'mods',dest,side=='server',True)
 for f in (BASE/'individual/mapstitch'/side/'mods').glob('*.jar'):
  if not f.name.startswith(OLD):shutil.copy2(f,dest/f.name)
records=[]
for f in sorted(STAGE.rglob('*.jar')):
 with zipfile.ZipFile(f) as z:m=json.loads(z.read('fabric.mod.json'))
 records.append({'path':str(f.relative_to(STAGE)), 'id':m['id'], 'version':m['version'],'sha256':hashlib.sha256(f.read_bytes()).hexdigest()})
(STAGE/'MANIFEST.json').write_text(json.dumps(records,indent=2)+'\n')
(STAGE/'SHA256SUMS.sha256').write_text(''.join(r['sha256']+'  '+r['path']+'\n' for r in records))
(STAGE/'README.md').write_text("""# Local Polymer test kit 2026-10-01.1+26.3

Full combined server: copy `mods/` into a fresh Minecraft 26.3 Fabric 0.19.5 server running Java 25+. Native client: use `native-client/mods/` with the same Minecraft/loader. The supplied native client has no Polymer or server shim. The atlas/elytra addon is installed on both sides in this full profile.

Only one server shim handles SSO, Tiered Backpacks, Tool Pouch and MapStitch: SSO-backpack-toolpouch-mapstitch-shim 1.0.0+26.3. Each module activates only for its installed original. Remove the four former standalone shims if updating an existing server. Chalk uses its separate shim and the explicitly requested 26.3 port.

`individual/` contains isolated server and matching native-client mods folders for the four modules, Chalk, and the Tool Pouch + MapStitch atlas/elytra integration. Use one profile at a time. Keep the original mods and all included dependencies. These are untouched developer releases except the explicitly identified Chalk port; hashes and declared versions are in MANIFEST.json.

The addon remains version 1.0.0+26.3. Its duplicate keybind category is a known client-side behavior of that unchanged addon. The combined server shim cannot change client Controls headings.

Vanilla players can use SSO's server-side compatibility; backpack, pouch and atlas native systems require their original client mods. Fabric clients advertising registry sync need matching SSO and Tiered originals when those are on the server. Restart server and clients after changing JARs. Configure Polymer resource-pack hosting for custom vanilla visuals.

See VALIDATION.md for exact tested coverage and outstanding limits. Passing regression tests is not a guarantee for every production world or extra mod. Use a disposable world first, including your real configuration, dimensions, permissions, persistence and restart workflow.
""")
print(json.dumps({'stage':str(STAGE),'jar_copies':len(records),'server_mods':len(list((STAGE/'mods').glob('*.jar'))),'native_mods':len(list((STAGE/'native-client/mods').glob('*.jar')))}))
