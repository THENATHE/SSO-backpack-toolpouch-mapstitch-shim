#!/usr/bin/env python3
"""Real SSO menu clicks using a test-only Fabric client with vanilla registry wire behavior."""
from pathlib import Path
import hashlib,importlib.util,json,os,shutil,subprocess,sys,time,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3];BUNDLE=ROOT/'Minecraft/polymer-shim-test-bundle';STAGE=BUNDLE/'staging-2026-10-01'
sys.path.insert(0,str(BUNDLE));spec=importlib.util.spec_from_file_location('vanilla_smoke',BUNDLE/'smoke_test.py');smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)
PORT=25810
def metadata(path):
    with zipfile.ZipFile(path) as z:return json.loads(z.read('fabric.mod.json'))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    suite=sys.argv[1];candidate=Path(sys.argv[2]).resolve();track=sys.argv[3] if len(sys.argv)>3 else 'developer';assert track in ['developer','sso-port']
    run=HERE/'runs'/suite;assert not run.exists(),'Use a fresh suite';run.mkdir(parents=True);control=run/'control';control.mkdir()
    base=json.loads((ROOT/'Minecraft/tiered-backpacks-polymer-compat-26.3/qa/runs/atlas-fixed/native/launch-audit.json').read_text())['command']
    server_cp=smoke.classpath();client_cp=base[base.index('-cp')+1]
    build=HERE/'fixtures';build.mkdir(exist_ok=True);paths=[server_cp,client_cp]
    jars=list((STAGE/'mods').glob('*.jar'))
    for jar in jars:
        paths.append(str(jar))
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                if name.endswith('.jar'):
                    p=build/Path(name).name;p.write_bytes(z.read(name));paths.append(str(p))
    classes=build/'classes';classes.mkdir(exist_ok=True)
    subprocess.run(['/usr/bin/javac','--release','25','-proc:none','-cp',os.pathsep.join(paths),'-d',str(classes),*map(str,(HERE/'src').glob('*.java'))],check=True)
    for side in ['server','client']:
        with zipfile.ZipFile(build/(side+'.jar'),'w',zipfile.ZIP_DEFLATED) as z:
            z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'sso_network_'+side+'_qa','version':'1','environment':side,'entrypoints':{'main' if side=='server' else 'client':['qa.Network'+side.title()+'Qa']},'depends':{'fabric-api':'*'}}))
            for p in classes.rglob('Network'+side.title()+'Qa*.class'):z.write(p,p.relative_to(classes))
    old={'mapstitch_polymer_compat','simple_smithing_polymer_compat','tiered_backpacks_polymer_compat','toolpouch_polymer_compat'}
    selected=[p for p in jars if metadata(p)['id'] not in old]
    if track=='sso-port':
        selected=[p for p in selected if metadata(p)['id'] not in {'simple_smithing_overhaul','defaulted','codecui'}]
        selected.append(ROOT/'Builds/Minecraft/Simple Smithing Overhaul/Main Plugin - Version Port/2.9.14-port.1 - Fabric 26.3/simple_smithing_overhaul-fabric-2.9.14-port.1+26.3.jar')
    selected.append(candidate);server=run/'server';client=run/'client'
    api=next(p for p in jars if metadata(p)['id']=='fabric-api')
    for side,dest,inputs in [('server',server,selected),('client',client,[api])]:
        (dest/'mods').mkdir(parents=True)
        for p in inputs:shutil.copy2(p,dest/'mods'/p.name)
        shutil.copy2(build/(side+'.jar'),dest/'mods'/('sso-network-'+side+'-qa.jar'))
    shutil.copy2(BUNDLE/'qa/chalk/eula.txt',server/'eula.txt')
    (server/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={PORT}\nonline-mode=false\nwhite-list=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\ngenerate-structures=false\nspawn-protection=0\ngamemode=survival\ndifficulty=peaceful\npause-when-empty-seconds=0\n')
    (client/'options.txt').write_text('graphicsMode:0\nrenderDistance:2\nsimulationDistance:5\nmaxFps:25\nmaxFpsInactive:25\nsoundCategory_master:0.0\njoinedFirstServer:true\nresourcePacks:["vanilla","file/sso-qa.zip"]\n')
    server_cmd=['/usr/lib/jvm/java-25-openjdk/bin/java','-Xmx1G','-XX:ActiveProcessorCount=2','-Dsso.network.control='+str(control),'-cp',server_cp,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui']
    client_cmd=[s for s in base if not s.startswith('-Dbackpack.')];client_cmd.insert(1,'-Dsso.network.control='+str(control))
    for key,val in [('--gameDir',str(client)),('--quickPlayMultiplayer',f'127.0.0.1:{PORT}'),('--username','StitchNativeQA')]:client_cmd[client_cmd.index(key)+1]=val
    for prefix,folder in [('java.library.path','java'),('jna.tmpdir','jna'),('org.lwjgl.system.SharedLibraryExtractPath','lwjgl'),('io.netty.native.workdir','netty')]:
        client_cmd=[('-D'+prefix+'='+str(client/'natives'/folder)) if s.startswith('-D'+prefix+'=') else s for s in client_cmd]
    env=os.environ.copy();env.update(SDL_VIDEODRIVER='x11',SDL_VIDEO_X11_XINPUT2='0',LP_NUM_THREADS='4')
    processes=[];handles=[];failure=None
    def wait(test,timeout=180):
        start=time.monotonic()
        while time.monotonic()-start<timeout:
            for name in ['client-failure','server-failure']:
                if (control/name).exists():raise RuntimeError((control/name).read_text())
            if test():return
            if any(p.poll() is not None for p in processes):raise RuntimeError('QA process exited early')
            time.sleep(.25)
        raise TimeoutError('QA wait exceeded')
    def launch(dest,command):
        output=(dest/'console.log').open('w');handles.append(output);p=subprocess.Popen(command,cwd=dest,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,env=env,text=True);processes.append(p);return p
    def pack_ready():
        try:
            with zipfile.ZipFile(server/'polymer/resource_pack.zip') as z:return z.testzip() is None
        except (OSError,zipfile.BadZipFile):return False
    try:
        proc=launch(server,server_cmd);wait(lambda:'Done (' in (server/'console.log').read_text(errors='replace'))
        print('SERVER_READY',suite,flush=True);proc.stdin.write('polymer generate-pack\n');proc.stdin.flush();wait(pack_ready)
        (client/'resourcepacks').mkdir();shutil.copy2(server/'polymer/resource_pack.zip',client/'resourcepacks/sso-qa.zip')
        launch(client,client_cmd);wait(lambda:(control/'network-result.txt').exists(),240);print((control/'network-result.txt').read_text(),flush=True)
    except Exception as error:failure=str(error);print('FAIL',failure,flush=True)
    finally:
        for p in reversed(processes):
            if p.poll() is None:
                if p is processes[0]:
                    try:p.stdin.write('stop\n');p.stdin.flush()
                    except Exception:p.terminate()
                else:p.terminate()
                try:p.wait(timeout=25)
                except subprocess.TimeoutExpired:p.kill();p.wait()
        for h in handles:h.close()
        result={'suite':suite,'track':track,'candidate_sha256':sha(server/'mods'/candidate.name),'client_type':'test-only FabricAPI client; fabric:registry/sync global receiver removed; no content mods; not an official vanilla client','official_vanilla_tested':False,'failure':failure,'control':{p.name:p.read_text() for p in control.glob('*') if p.is_file()},'mods':{side:[{'file':p.name,'id':metadata(p)['id'],'version':metadata(p)['version'],'sha256':sha(p)} for p in sorted((dest/'mods').glob('*.jar'))] for side,dest in [('server',server),('client',client)]}}
        result['pass']=failure is None and result['control'].get('network-result.txt','').startswith('PASS')
        (run/'result.json').write_text(json.dumps(result,indent=2)+'\n');print('RESULT',result['pass'],flush=True)
    return 0 if result['pass'] else 1
if __name__=='__main__':raise SystemExit(main())
