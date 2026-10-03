#!/usr/bin/env python3
"""Real Fabric addon regression: combined server and original native client, with a selectable addon."""
import argparse, hashlib, importlib.util, json, os, shutil, subprocess, time, zipfile
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
MC = ROOT / 'Minecraft'
BUNDLE = MC / 'polymer-shim-test-bundle/staging-2026-10-01'
ADDON = ROOT / 'Builds/Minecraft/Tool Pouch/Feature Addon - Atlas and Elytra/1.0.0+26.3/toolpouch-atlas-elytra-compat-1.0.0+26.3.jar'
spec = importlib.util.spec_from_file_location('launch', MC / 'mapstitch-polymer-compat-26.3/qa/launch.py')
launch = importlib.util.module_from_spec(spec); spec.loader.exec_module(launch)

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def build(where, mods, suite):
    classes = where/'classes'; shutil.rmtree(classes, ignore_errors=True); classes.mkdir()
    server, client = launch.audits()
    paths = launch.cp(server['command']) + launch.cp(client['command']) + list(map(str, mods))
    for jar in mods:
        with zipfile.ZipFile(jar) as archive:
            for member in archive.namelist():
                if member.endswith('.jar'):
                    path = where/'nested'/Path(member).name; path.parent.mkdir(exist_ok=True)
                    path.write_bytes(archive.read(member)); paths.append(str(path))
    subprocess.run(['javac', '--release', '25', '-proc:none', '-cp', os.pathsep.join(dict.fromkeys(paths)), '-d', str(classes), *map(str,where.glob('*.java'))], check=True)
    for side in ('server','client'):
        with zipfile.ZipFile(where/f'{side}-qa.jar','w') as archive:
            name = ('elytraqa.Elytra' if suite=='elytra' else 'pouchqa.Pouch') + side.title() + 'Qa'
            archive.writestr('fabric.mod.json', json.dumps({'schemaVersion':1,'id':f'combined_addon_{suite}_{side}_qa','version':'1','environment':side,'entrypoints':{'main' if side=='server' else 'client':[name]},'depends':{'toolpouch_atlas_elytra_compat':'*'}}))
            for path in classes.rglob('*'+side.title()+'*.class'): archive.write(path,path.relative_to(classes))

def stop(children):
    for child, log, side in reversed(children):
        if child.poll() is None:
            if side=='server':
                try: child.stdin.write('stop\n'); child.stdin.flush()
                except BrokenPipeError: pass
            else: child.terminate()
            try: child.wait(timeout=30)
            except subprocess.TimeoutExpired: child.kill(); child.wait()
        log.close()
    children.clear()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--shim',type=Path,required=True)
    parser.add_argument('--addon',type=Path,default=ADDON)
    parser.add_argument('--without-mapstitch',action='store_true')
    parser.add_argument('--atlas-regressions-only',action='store_true')
    parser.add_argument('--suite',choices=['atlas','elytra'],required=True)
    parser.add_argument('--port',type=int)
    parser.add_argument('--build-only',action='store_true')
    args=parser.parse_args(); suite=args.suite; where=HERE/suite
    port=args.port or (25856 if suite=='atlas' else 25866)
    originals=list((BUNDLE/'mods').glob('*.jar'))
    server_mods=[p for p in originals if not any(p.name.startswith(x) for x in ('simple-smithing-polymer-compat-', 'tiered-backpacks-polymer-compat-', 'toolpouch-polymer-compat-', 'mapstitch-polymer-compat-'))]+[args.shim.resolve(),args.addon.resolve()]
    native_mods=list((BUNDLE/'native-client/mods').glob('*.jar'))+[args.addon.resolve()]
    if args.without_mapstitch:
        server_mods=[p for p in server_mods if not p.name.startswith('mapstitch-')]
        native_mods=[p for p in native_mods if not p.name.startswith('mapstitch-')]
    tiered_override = Path(os.environ['TIERED_QA_JAR']).resolve() if os.environ.get('TIERED_QA_JAR') else None
    if tiered_override:
        with zipfile.ZipFile(tiered_override) as archive:
            assert json.loads(archive.read('fabric.mod.json'))['id'] == 'tiered_backpacks'
        server_mods = [p for p in server_mods if not p.name.startswith('tiered_backpacks-')] + [tiered_override]
        native_mods = [p for p in native_mods if not p.name.startswith('tiered_backpacks-')] + [tiered_override]
    assert not any('polymer' in p.name.lower() for p in native_mods)
    mods=list(dict.fromkeys(server_mods+native_mods)); build(where,mods,suite)
    if args.build_only:return
    control=where/'control'; shutil.rmtree(control,ignore_errors=True); control.mkdir()
    shutil.rmtree(where/'runs',ignore_errors=True)
    before={str(p):sha(p) for p in mods}; children=[]
    env=os.environ.copy(); env.update(SDL_VIDEODRIVER='x11',SDL_VIDEO_X11_XINPUT2='0',LP_NUM_THREADS='4')
    stages=(1,2) if suite=='elytra' else (1,)
    try:
        for stage in stages:
            for mode in ('server','native'):
                side='server' if mode=='server' else 'client'; run=where/'runs'/mode; run.mkdir(parents=True,exist_ok=True)
                mod_dir=run/'mods';mod_dir.mkdir(exist_ok=True)
                selected=(server_mods if side=='server' else native_mods)+[where/f'{side}-qa.jar']
                for p in selected:shutil.copy2(p,mod_dir/p.name)
                if side=='server':
                    eula=MC/'toolpouch-atlas-elytra-compat-26.3/qa/elytra-client/runs/server/eula.txt'
                    assert 'eula=true' in eula.read_text().splitlines();shutil.copy2(eula,run/'eula.txt')
                    (run/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nwhite-list=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\nlevel-name=qa-world\nlevel-seed=927436\nlevel-type=minecraft:flat\ngenerator-settings={{"layers":[{{"block":"minecraft:bedrock","height":1}},{{"block":"minecraft:dirt","height":2}},{{"block":"minecraft:grass_block","height":1}}],"biome":"minecraft:plains"}}\ngenerate-structures=false\ndifficulty=peaceful\n')
                else:(run/'options.txt').write_text('pauseOnLostFocus:false\ngraphicsMode:0\nrenderDistance:3\nsimulationDistance:5\nmaxFps:30\nmaxFpsInactive:30\nsoundCategory_master:0.0\njoinedFirstServer:true\n')
                command=launch.base_command(mode,run,port)
                prefix='elytra' if suite=='elytra' else 'pouch'
                command.insert(1,f'-D{prefix}.qa.control={control}')
                if args.atlas_regressions_only: command.insert(1,'-Dpouch.qa.regressionsOnly=true')
                if stage==2:command.insert(1,'-Delytra.qa.reconnect=true')
                (run/f'launch-audit-{stage}.json').write_text(json.dumps({'command':command,'mods':[{'path':str(p),'sha256':sha(p)} for p in selected],'qa_fixture':True,'client_has_polymer':False},indent=2))
                logpath=run/f'console-{stage}.log';log=logpath.open('w')
                child=subprocess.Popen(command,cwd=run,env=env,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True);children.append((child,log,side))
                if side=='server':
                    for _ in range(150):
                        if child.poll() is not None:raise RuntimeError(f'Server exited: {logpath}')
                        if 'Done (' in logpath.read_text():break
                        time.sleep(1)
                    else:raise TimeoutError('server startup')
            for _ in range(240):
                result=control/'result.txt'
                if result.exists():
                    message=result.read_text();print(message,flush=True)
                    expected='RECONNECT' if suite=='elytra' and stage==1 else 'PASS'
                    if not message.startswith(expected):raise RuntimeError('QA failed')
                    break
                if any(p.poll() is not None for p,_,_ in children):raise RuntimeError('Process exited')
                time.sleep(1)
            else:raise TimeoutError('client result')
            stop(children)
            if stage!=stages[-1]:
                for name in ('result.txt','command','ack'):(control/name).unlink(missing_ok=True)
    finally:
        stop(children);after={str(p):sha(p) for p in mods}
        (where/'integrity.json').write_text(json.dumps({'before':before,'after':after,'unchanged':before==after},indent=2));assert before==after
if __name__=='__main__':main()
