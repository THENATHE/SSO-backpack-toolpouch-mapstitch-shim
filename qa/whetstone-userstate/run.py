#!/usr/bin/env python3
"""Read-only copy of server mods/config into disposable sanitized item-state replays."""
from pathlib import Path
import hashlib,importlib.util,json,os,shutil,subprocess,sys,time,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3];BUNDLE=ROOT/'Minecraft/polymer-shim-test-bundle'
SOURCE=Path(os.environ['SSO_USERSTATE_SERVER'])
sys.path.insert(0,str(BUNDLE));spec=importlib.util.spec_from_file_location('userstate_smoke',BUNDLE/'smoke_test.py');smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def meta(p):
    with zipfile.ZipFile(p) as z:return json.loads(z.read('fabric.mod.json'))
def main():
    suite=sys.argv[1];run=HERE/'runs'/suite;assert not run.exists(),'Use fresh suite';run.mkdir(parents=True)
    cp=smoke.classpath();jars=sorted((SOURCE/'mods').glob('*.jar'));build=HERE/'fixtures';build.mkdir(exist_ok=True);paths=[cp]
    for jar in jars:
        paths.append(str(jar))
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                if name.endswith('.jar'):
                    out=build/Path(name).name;out.write_bytes(z.read(name));paths.append(str(out))
    classes=build/'classes';classes.mkdir(exist_ok=True)
    subprocess.run(['/usr/bin/javac','--release','25','-proc:none','-cp',os.pathsep.join(paths),'-d',str(classes),*map(str,(HERE/'src').glob('*.java'))],check=True)
    fixture=build/'userstate-qa.jar'
    with zipfile.ZipFile(fixture,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'whetstone_userstate_qa','version':'1','environment':'server','entrypoints':{'main':['qa.UserStateQa']},'depends':{'fabric-api':'*','simple_smithing_overhaul':'*'}}))
        for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
    results=[]
    candidate=Path(os.environ['SSO_USERSTATE_CANDIDATE']).resolve() if os.environ.get('SSO_USERSTATE_CANDIDATE') else None
    modes=['candidate-combined'] if candidate else ['original-without-combined','released-combined']
    for index,mode in enumerate(modes):
        server=run/mode;mods=server/'mods';control=server/'control';mods.mkdir(parents=True);control.mkdir()
        selected=[p for p in jars if mode=='released-combined' or meta(p)['id']!='sso_backpack_toolpouch_mapstitch_shim']
        if candidate:selected.append(candidate)
        if os.environ.get('SSO_USERSTATE_TRACK')=='sso-port':
            selected=[p for p in selected if meta(p)['id'] not in {'simple_smithing_overhaul','defaulted','codecui'}]
            selected.append(ROOT/'Builds/Minecraft/Simple Smithing Overhaul/Main Plugin - Version Port/2.9.14-port.1 - Fabric 26.3/simple_smithing_overhaul-fabric-2.9.14-port.1+26.3.jar')
        for p in selected+[fixture]:shutil.copy2(p,mods/p.name)
        config=server/'config/simple_smithing_overhaul/config-v2.toml';config.parent.mkdir(parents=True);shutil.copy2(SOURCE/'config/simple_smithing_overhaul/config-v2.toml',config)
        shutil.copy2(BUNDLE/'qa/chalk/eula.txt',server/'eula.txt')
        (server/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={25814+index}\nonline-mode=false\nwhite-list=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\ngenerate-structures=false\npause-when-empty-seconds=0\n')
        command=['/usr/lib/jvm/java-25-openjdk/bin/java','-Xmx1G','-XX:ActiveProcessorCount=2','-Duserstate.qa.control='+str(control),'-Duserstate.qa.recovery='+('true' if os.environ.get('SSO_USERSTATE_RECOVERY')=='1' else 'false'),'-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui']
        started=time.monotonic();stopped=False
        with (server/'console.log').open('w') as log:
            proc=subprocess.Popen(command,cwd=server,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
            try:
                while proc.poll() is None:
                    if (control/'observations.json').exists() and not stopped:proc.stdin.write('stop\n');proc.stdin.flush();stopped=True
                    if time.monotonic()-started>150:proc.terminate();break
                    time.sleep(.2)
                proc.wait(timeout=25)
            finally:
                if proc.poll() is None:proc.kill();proc.wait()
        obs=json.loads((control/'observations.json').read_text()) if (control/'observations.json').exists() else {}
        result={'mode':mode,'ssoconfig_sha256':sha(config),'mods':[{'id':meta(p)['id'],'version':meta(p)['version'],'sha256':sha(p),'file':p.name} for p in sorted(mods.glob('*.jar'))],'exit_code':proc.returncode,'observations':obs,'no_user_world_or_player_data_loaded':True}
        (server/'result.json').write_text(json.dumps(result,indent=2)+'\n');results.append(result)
        print(mode,'completed',obs.get('completed'),'exit',proc.returncode,flush=True)
    (run/'RESULTS.json').write_text(json.dumps(results,indent=2)+'\n')
    return 0 if all(r['observations'].get('completed') and r['exit_code']==0 for r in results) else 1
if __name__=='__main__':raise SystemExit(main())
