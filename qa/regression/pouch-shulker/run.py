#!/usr/bin/env python3
"""Native packet/accounting reproduction of Tool Pouch nested shulker transactions."""
import argparse, hashlib, importlib.util, json, os, shutil, subprocess, time, zipfile
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = next(path for path in HERE.parents if (path/'Minecraft/polymer-shim-test-bundle').is_dir())
MC = ROOT/'Minecraft'
BUNDLE = MC/'polymer-shim-test-bundle/staging-2026-10-01'
spec = importlib.util.spec_from_file_location('launch', MC/'mapstitch-polymer-compat-26.3/qa/launch.py')
launch = importlib.util.module_from_spec(spec); spec.loader.exec_module(launch)

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--track', choices=['released','original','sso-port'], required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--port', type=int)
    parser.add_argument('--expect-conserved', action='store_true')
    parser.add_argument('--case', choices=['leggings-beds','leggings-beds-x-repeat'])
    parser.add_argument('--resume', action='store_true', help='Restart same test world and verify final inventory persistence without reseeding')
    parser.add_argument('--boundaries', action='store_true')
    parser.add_argument('--shim', type=Path, default=ROOT/'Builds/Minecraft/Multi-Shim/Combined Compatibility Shim - Developer Release Targets/1.0.2+26.3/SSO-backpack-toolpouch-mapstitch-shim-1.0.2+26.3.jar')
    parser.add_argument('--addon', type=Path, default=ROOT/'Builds/Minecraft/Tool Pouch/Feature Addon - Atlas and Elytra/1.0.1+26.3/toolpouch-atlas-elytra-compat-1.0.1+26.3.jar')
    args=parser.parse_args(); port=args.port or (25876 if args.track=='released' else 25877)
    run=HERE/'runs'/args.label
    if args.resume:
        assert (run/'evidence.json').is_file(), 'A completed first run is required'
        for filename in ('result.txt','command','ack'):(run/'control'/filename).unlink(missing_ok=True)
    else:run.mkdir(parents=True,exist_ok=False)
    control=run/'control';control.mkdir(exist_ok=args.resume);build=run/'fixtures';build.mkdir(exist_ok=args.resume);classes=build/'classes';classes.mkdir(exist_ok=args.resume)
    native=list((BUNDLE/'native-client/mods').glob('*.jar'))
    if args.track=='original':
        native=[p for p in native if p.name.startswith(('toolpouch-fabric-','fabric-api-','fabric-language-kotlin-','fzzy_config-'))]
        server=list(native)
    else:
        server=[p for p in (BUNDLE/'mods').glob('*.jar') if not p.name.startswith(('simple-smithing-polymer-compat-','tiered-backpacks-polymer-compat-','toolpouch-polymer-compat-','mapstitch-polymer-compat-'))]+[args.shim.resolve(),args.addon.resolve()]
        native+=[args.addon.resolve()]
        if args.track=='sso-port':
            port_artifact=ROOT/'Builds/Minecraft/Simple Smithing Overhaul/Main Plugin - Version Port/2.9.14-port.1 - Fabric 26.3/simple_smithing_overhaul-fabric-2.9.14-port.1+26.3.jar'
            server=[p for p in server if not p.name.startswith(('simple_smithing_overhaul-','defaulted-','codecui-'))]+[port_artifact]
            native=[p for p in native if not p.name.startswith(('simple_smithing_overhaul-','defaulted-','codecui-'))]+[port_artifact]
    mods=list(dict.fromkeys(server+native)); server_audit,client_audit=launch.audits()
    paths=launch.cp(server_audit['command'])+launch.cp(client_audit['command'])+list(map(str,mods))
    for jar in mods:
        with zipfile.ZipFile(jar) as archive:
            for member in archive.namelist():
                if member.endswith('.jar'):
                    target=build/Path(member).name;target.write_bytes(archive.read(member));paths.append(str(target))
    sources=[p for p in (HERE/'src').glob('*.java') if args.boundaries or p.name!='ShulkerServerBoundaryChecks.java']
    compile_result=subprocess.run(['javac','--release','25','-proc:none','-cp',os.pathsep.join(dict.fromkeys(paths)),'-d',str(classes),*map(str,sources)])
    if compile_result.returncode:raise RuntimeError('QA fixture compilation failed; see compiler diagnostics above')
    for side in ('server','client'):
        with zipfile.ZipFile(build/f'{side}.jar','w') as archive:
            metadata={'schemaVersion':1,'id':'pouch_shulker_qa_'+side,'version':'1','environment':side,'entrypoints':{'main' if side=='server' else 'client':['shulkerqa.Shulker'+side.title()+'Qa']},'depends':{'toolpouch':'*','fabric-api':'*'}}
            if side=='server':
                metadata['mixins']=['shulker-qa.mixins.json']
                archive.writestr('shulker-qa.mixins.json',json.dumps({'required':True,'package':'shulkerqa.mixin','compatibilityLevel':'JAVA_25','mixins':['ShulkerServerTraceMixin'],'injectors':{'defaultRequire':1}}))
            archive.writestr('fabric.mod.json',json.dumps(metadata))
            for path in classes.rglob('*'+side.title()+'*.class'):archive.write(path,path.relative_to(classes))
    before={str(p):sha(p) for p in mods};children=[];audits={}
    env=os.environ.copy();env.update(SDL_VIDEODRIVER='x11',SDL_VIDEO_X11_XINPUT2='0',LP_NUM_THREADS='3')
    try:
        for side,mode,selected in [('server','server',server),('client','native',native)]:
            directory=run/side;moddir=directory/'mods';moddir.mkdir(parents=True,exist_ok=args.resume)
            selected=selected+[build/f'{side}.jar']
            for path in selected:shutil.copy2(path,moddir/path.name)
            if side=='server':
                eula=MC/'toolpouch-atlas-elytra-compat-26.3/qa/elytra-client/runs/server/eula.txt';assert 'eula=true' in eula.read_text().splitlines();shutil.copy2(eula,directory/'eula.txt')
                (directory/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nwhite-list=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\nlevel-name=qa-world\nlevel-seed=927436\nlevel-type=minecraft:flat\ngenerator-settings={{"layers":[{{"block":"minecraft:bedrock","height":1}},{{"block":"minecraft:dirt","height":2}},{{"block":"minecraft:grass_block","height":1}}],"biome":"minecraft:plains"}}\ngenerate-structures=false\ndifficulty=peaceful\n')
            else:(directory/'options.txt').write_text('pauseOnLostFocus:false\ngraphicsMode:0\nrenderDistance:3\nsimulationDistance:5\nmaxFps:30\nmaxFpsInactive:30\nsoundCategory_master:0.0\njoinedFirstServer:true\n')
            command=launch.base_command(mode,directory,port);command.insert(1,'-Dshulker.qa.control='+str(control))
            if args.case:
                command.insert(1,'-Dshulker.qa.singleCase=true')
                command.insert(1,'-Dshulker.qa.startCase='+str(9 if args.case=='leggings-beds' else 10))
            if args.resume:command.insert(1,'-Dshulker.qa.reconnect=true')
            if args.boundaries:command.insert(1,'-Dshulker.qa.boundaries=true')
            audits[side]={'mods':[{'filename':p.name,'sha256':sha(p)} for p in selected],'qa_fixture':True}
            suffix='-reconnect' if args.resume else ''
            (directory/f'launch-audit{suffix}.json').write_text(json.dumps({'command':command,**audits[side]},indent=2))
            logpath=directory/f'console{suffix}.log';log=logpath.open('w');child=subprocess.Popen(command,cwd=directory,env=env,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True);children.append((child,log,side))
            if side=='server':
                for _ in range(150):
                    if child.poll() is not None:raise RuntimeError('Server exited: '+str(logpath))
                    if 'Done (' in logpath.read_text():break
                    time.sleep(1)
                else:raise TimeoutError('server startup')
        for _ in range(420):
            result=control/'result.txt'
            if result.exists():
                print(result.read_text(),flush=True)
                if not result.read_text().startswith('COMPLETE'):raise RuntimeError('Fixture failed')
                if args.expect_conserved and 'persistent mismatches=0' not in result.read_text():raise AssertionError('Candidate did not conserve authoritative item counts')
                if args.expect_conserved and 'client_mismatches=0' not in result.read_text():raise AssertionError('Candidate client disagreed with authoritative settled inventory counts')
                break
            if any(child.poll() is not None for child,_,_ in children):raise RuntimeError('Process exited')
            time.sleep(1)
        else:raise TimeoutError('client suite')
    finally:
        for child,log,side in reversed(children):
            if child.poll() is None:
                if side=='server':
                    try:child.stdin.write('stop\n');child.stdin.flush()
                    except BrokenPipeError:pass
                else:child.terminate()
                try:child.wait(timeout=25)
                except subprocess.TimeoutExpired:child.kill();child.wait()
            log.close()
        after={str(p):sha(p) for p in mods}
        evidence={'track':args.track,'artifacts':audits,'original_jars_unchanged':before==after}
        for filename in ('result.txt','observations.txt','server-snapshots.txt','menu-trace.txt','payload-trace.txt','boundary-results.txt'):
            if (control/filename).exists():evidence[filename]=(control/filename).read_text()
        (run/('evidence-reconnect.json' if args.resume else 'evidence.json')).write_text(json.dumps(evidence,indent=2)+'\n')
        assert before==after
if __name__=='__main__':main()
