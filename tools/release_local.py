#!/usr/bin/env python3
"""Package a verified, committed Multi-Shim release into its standalone family."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import subprocess
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
WORKSPACE = PROJECT.parents[1]
REPO = 'https://github.com/THENATHE/SSO-backpack-toolpouch-mapstitch-shim'
VERSION = re.search(r"^version = '([^']+)'", (PROJECT/'build.gradle').read_text(), re.M)[1]
JAR = PROJECT/'build/libs'/f'SSO-backpack-toolpouch-mapstitch-shim-{VERSION}.jar'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=PROJECT, text=True).strip(), 'Commit reviewed source and evidence before packaging'
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=PROJECT, text=True).strip()
    with zipfile.ZipFile(JAR) as archive:
        assert archive.testzip() is None
        metadata = json.loads(archive.read('fabric.mod.json'))
        assert metadata['version'] == VERSION
        assert metadata['id'] == 'sso_backpack_toolpouch_mapstitch_shim'
    digest = sha(JAR)
    validation = (PROJECT/'VALIDATION.md').read_text()
    assert digest in validation, 'Validation must identify the exact release JAR'
    releases = []
    for track, component in [('developer', 'Combined Compatibility Shim - Developer Release Targets'), ('sso-port', 'Combined Compatibility Shim - SSO Version Port Target')]:
        out = WORKSPACE/'Builds/Minecraft/Multi-Shim'/component/VERSION
        assert not out.exists(), f'Refusing to overwrite existing release: {out}'
        out.mkdir(parents=True)
        shutil.copy2(JAR, out/JAR.name)
        source = out/f'SSO-backpack-toolpouch-mapstitch-shim-{VERSION}-source.zip'
        subprocess.run(['git', 'archive', '--format=zip', '--prefix=SSO-backpack-toolpouch-mapstitch-shim/', '-o', str(source), revision], cwd=PROJECT, check=True)
        with zipfile.ZipFile(source) as archive:
            assert archive.testzip() is None
        shutil.copy2(source, PROJECT/'tracks'/track/'source-snapshot.zip')
        shutil.copy2(PROJECT/'tracks'/track/'runtime.lock.json', out/'runtime.lock.json')
        shutil.copy2(PROJECT/'dependencies.lock.json', out/'compile-inputs.lock.json')
        (out/'SOURCE-REVISION.txt').write_text(revision+'\n')
        # Revision links remain valid before a GitHub release tag is published.
        external = re.sub(r'\]\((?!https?://)([^)]+)\)', lambda m: ']('+REPO+'/blob/'+revision+'/'+m[1]+')', validation)
        (out/'VALIDATION.md').write_text(external)
        (out/'SHA256SUMS.sha256').write_text(''.join(sha(path)+'  '+path.name+'\n' for path in [out/JAR.name, source]))
        (out/'README.md').write_text(f'''# Multi-Shim {VERSION}

Component: {component}. Target track: **{track}**.

[Installable JAR]({JAR.name}) · [Checksums](SHA256SUMS.sha256) · [Validation](VALIDATION.md) · [Source snapshot]({source.name}) · [Repository]({REPO})

Install this JAR on the server only. Replace the earlier combined shim; remove separate SSO, Tiered Backpacks, Tool Pouch and MapStitch shims. Keep original mods and dependencies. Chalk remains separate. Restart server and clients after updating.

Requires Minecraft 26.3, Java 25+, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 and Polymer Bundled 0.18.2+26.3. Developer targets are SSO 2.9.14+26.3, Tiered Backpacks 1.0.19+26.3, Tool Pouch 1.1.10+26.3 and MapStitch 1.1.6+26.3, with Fzzy Config 0.7.7+fix2+26.3 and Kotlin 1.14.1+kotlin.2.4.20. Developer SSO also uses Defaulted 1.3.8.release-26.3, CodecUI 26.3-1.4.3 and Mixson 2.2.1. The SSO port target instead uses 2.9.14-port.1+26.3 and its preserved Fzzy/Kotlin/Mixson stack. See [exact runtime input lock](runtime.lock.json). Both tracks are independently tested; the JAR is shared.

This release fixes persistent duplication and item loss when reopening pouch-held shulkers or moving their source pouch. Saves remain anchored to the original pouch and changes persist immediately. Previous atlas metadata, crafting and fallback-lore corrections remain included. Original mod JARs remain intact. See validation for the reported leggings-pouch/bed workflow and exact regression results.

For the separate Toggle Tool Pouch Elytra heading fix, also replace the atlas/elytra addon with **toolpouch-atlas-elytra-compat-1.0.1+26.3.jar on both server and native clients**. [Addon repository](https://github.com/THENATHE/toolpouch-atlas-elytra-modification). The server shim cannot change a client's controls menu by itself.

SSO retains Type B vanilla gameplay; the other modules retain Type A display/guards and native systems. Matching original client mods are required for native pouch/backpack/atlas gameplay. Polymer pack acceptance supplies custom vanilla visuals. Native clients do not require Polymer. See validation for exercised cases and coverage boundaries.

Source: `Minecraft/SSO-backpack-toolpouch-mapstitch-shim`, revision `{revision}`. Original compile input hashes are in `compile-inputs.lock.json`. The pre-QA 1.0.1 release is preserved alongside this version under Multi-Shim.
''')
        releases.append({'track': track, 'component': component, 'directory': str(out.relative_to(WORKSPACE)), 'jar': str((out/JAR.name).relative_to(WORKSPACE)), 'sha256': digest, 'source_revision': revision})
    record = {'version': VERSION, 'releases': releases}
    (PROJECT/'release-record.json').write_text(json.dumps(record, indent=2)+'\n')
    print(json.dumps(record, indent=2))


if __name__ == '__main__':
    main()
