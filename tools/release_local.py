#!/usr/bin/env python3
"""Package the verified combined shim and local-only dependency test kit."""
from pathlib import Path
import hashlib,json,shutil,subprocess,zipfile,re
P=Path(__file__).resolve().parents[1];W=P.parents[1]
version='1.0.0+26.3';kitversion='2026-10-01.1+26.3';tag='v'+version
revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P,text=True).strip()
jar=P/'build/libs'/f'SSO-backpack-toolpouch-mapstitch-shim-{version}.jar'
expected='2ab08d31dc335b03e2e4c16bfd2b1538de8e7e5a6e1760aac4164ed807e49fe3'
assert hashlib.sha256(jar.read_bytes()).hexdigest()==expected
validation=(P/'VALIDATION.md').read_text();assert 'Pending final' not in validation and 'checks pending' not in validation
repo='https://github.com/THENATHE/SSO-backpack-toolpouch-mapstitch-shim'
def sha(f):return hashlib.sha256(f.read_bytes()).hexdigest()
def externalize(text):
 return re.sub(r'\]\((?!https?://)([^)]+)\)',lambda m:']('+repo+'/blob/'+tag+'/'+m.group(1)+')',text)
releases=[]
for track,component in [('developer','Combined Compatibility Shim - Developer Release Targets'),('sso-port','Combined Compatibility Shim - SSO Version Port Target')]:
 out=W/'Builds/Minecraft/Polymer'/component/version;out.mkdir(parents=True,exist_ok=True)
 shutil.copy2(jar,out/jar.name)
 source=out/f'SSO-backpack-toolpouch-mapstitch-shim-{version}-source.zip'
 subprocess.run(['git','archive','--format=zip','--prefix=SSO-backpack-toolpouch-mapstitch-shim/','-o',str(source),revision],cwd=P,check=True)
 shutil.copy2(source,P/'tracks'/track/'source-snapshot.zip')
 shutil.copy2(P/'tracks'/track/'runtime.lock.json',out/'runtime.lock.json')
 shutil.copy2(P/'dependencies.lock.json',out/'compile-inputs.lock.json')
 (out/'SOURCE-REVISION.txt').write_text(revision+'\n')
 (out/'VALIDATION.md').write_text(externalize(validation))
 (out/'SHA256SUMS.sha256').write_text(''.join(sha(f)+'  '+f.name+'\n' for f in [out/jar.name,source]))
 (out/'README.md').write_text(f"""# SSO-backpack-toolpouch-mapstitch-shim {version}

Component: {component}. Target track: **{track}**. Unofficial separate server-side Polymer shim; one binary with four optional modules. Chalk stays separate.

[Installable JAR]({jar.name}) · [Checksums](SHA256SUMS.sha256) · [Validation](VALIDATION.md) · [Repository]({repo}) · [Source snapshot]({source.name})

Requires Minecraft 26.3, Java 25+, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 and Polymer Bundled 0.18.2+26.3. Keep whichever original mods you use and their dependencies. Remove the four separate SSO/Tiered Backpacks/Tool Pouch/MapStitch Polymer shims, then add this JAR to server mods. Do not install this server shim on clients. Native clients use matching original mods; Polymer is optional for them.

Developer originals: SSO 2.9.14+26.3, Tiered Backpacks 1.0.19+26.3, Tool Pouch 1.1.10+26.3, MapStitch 1.1.6+26.3. Fzzy Config 0.7.7+fix2+26.3 and Kotlin 1.14.1+kotlin.2.4.20; developer SSO also uses Defaulted 1.3.8.release-26.3, CodecUI 26.3-1.4.3 and Mixson 2.2.1. The separately verified SSO port track instead uses existing SSO 2.9.14-port.1+26.3 with Fzzy/Kotlin/Mixson; see [exact runtime lock](runtime.lock.json). This is a shared binary independently tested against both targets, with separate release/source copies.

For Tool Pouch atlas/elytra integration, install the unchanged addon 1.0.0+26.3 on server and native client alongside original Tool Pouch and MapStitch. Its duplicate keybind category remains unchanged. Keep the separate Chalk shim with the requested Chalk 26.3 port if desired.

SSO retains Type B vanilla gameplay. The other three modules retain Type A display/guards and native-client systems. Fabric clients advertising registry sync need matching installed SSO/Tiered originals. Configure Polymer pack delivery for custom vanilla visuals. Full details and tested limits: [README]({repo}/blob/{tag}/README.md), [validation](VALIDATION.md).

Local source: `Minecraft/SSO-backpack-toolpouch-mapstitch-shim`; immutable source revision `{revision}`. [Compile input hashes](compile-inputs.lock.json), exact original artifact provenance and QA evidence are retained in the source snapshot. Original JARs were not modified or embedded.
""")
 releases.append({'track':track,'component':component,'directory':str(out.relative_to(W)),'jar':str((out/jar.name).relative_to(W)),'sha256':expected,'source_revision':revision})
stage=P/'test-kit';shutil.copy2(P/'VALIDATION.md',stage/'VALIDATION.md');(stage/'VALIDATION.md').write_text(externalize(validation))
kitout=W/'Builds/Minecraft/Polymer/Local Shim Test Bundle'/kitversion;kitout.mkdir(parents=True,exist_ok=True)
kit=kitout/f'polymer-shim-test-kit-{kitversion}.zip'
with zipfile.ZipFile(kit,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for f in sorted(stage.rglob('*')):
  if f.is_file():z.write(f,f.relative_to(stage))
with zipfile.ZipFile(kit) as z:assert z.testzip() is None
for name in ('README.md','VALIDATION.md','MANIFEST.json'):shutil.copy2(stage/name,kitout/name)
(kitout/'SHA256SUMS.sha256').write_text(sha(kit)+'  '+kit.name+'\n')
record={'version':version,'releases':releases,'test_kit':str(kit.relative_to(W)),'test_kit_sha256':sha(kit),'test_kit_bytes':kit.stat().st_size}
(P/'release-record.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record,indent=2))
