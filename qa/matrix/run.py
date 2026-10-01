#!/usr/bin/env python3
"""Optional-mod startup/reload matrix; exact production JARs plus a test-only fixture."""
from pathlib import Path
import argparse, concurrent.futures, hashlib, importlib.util, json, os, queue, shutil, signal, subprocess, sys, threading, time, zipfile

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
BUNDLE=ROOT/'Minecraft/polymer-shim-test-bundle'
STAGE=BUNDLE/'staging-2026-10-01'
sys.path.insert(0,str(BUNDLE))
spec=importlib.util.spec_from_file_location('matrix_smoke',BUNDLE/'smoke_test.py')
smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)
MODULES=[('simple-smithing','simple_smithing_overhaul'),('tiered-backpacks','tiered_backpacks'),('tool-pouch','toolpouch'),('mapstitch','mapstitch')]
OLD_SHIMS={'chalk_polymer_compat','mapstitch_polymer_compat','simple_smithing_polymer_compat','tiered_backpacks_polymer_compat','toolpouch_polymer_compat'}
PORT=ROOT/'Builds/Minecraft/Simple Smithing Overhaul/Main Plugin - Version Port/2.9.14-port.1 - Fabric 26.3/simple_smithing_overhaul-fabric-2.9.14-port.1+26.3.jar'

def metadata(path):
    with zipfile.ZipFile(path) as z:return json.loads(z.read('fabric.mod.json'))
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def fixture(cp):
    build=HERE/'fixtures';build.mkdir(exist_ok=True)
    paths=[cp]
    for jar in sorted((STAGE/'mods').glob('*.jar')):
        paths.append(str(jar))
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                if name.endswith('.jar'):
                    out=build/Path(name).name;out.write_bytes(z.read(name));paths.append(str(out))
    classes=build/'classes';classes.mkdir(exist_ok=True)
    subprocess.run(['/usr/bin/javac','--release','25','-proc:none','-cp',os.pathsep.join(paths),'-d',str(classes),str(HERE/'src/MatrixQa.java')],check=True)
    output=build/'optional-matrix-qa.jar'
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'optional_matrix_qa','version':'1','environment':'server','entrypoints':{'main':['qa.MatrixQa']},'depends':{'fabric-api':'*','polymer-core':'*'}}))
        for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
    return output

def main():
    parser=argparse.ArgumentParser();parser.add_argument('suite');parser.add_argument('candidate',type=Path)
    parser.add_argument('--track',choices=['developer','sso-port'],default='developer')
    parser.add_argument('--masks',default='all');parser.add_argument('--workers',type=int,default=2)
    parser.add_argument('--with-standalone-chalk',action='store_true')
    args=parser.parse_args();candidate=args.candidate.resolve();assert candidate.is_file()
    masks=list(range(16)) if args.masks=='all' else [int(v) for v in args.masks.split(',')]
    assert all(0<=mask<16 for mask in masks)
    if args.track=='sso-port':assert all(mask&1 for mask in masks),'Port cases must contain SSO'
    assert 1<=args.workers<=2
    suite=HERE/'runs'/args.suite;assert not suite.exists(),'Use a fresh suite name';suite.mkdir(parents=True)
    frozen=suite/'candidate';frozen.mkdir();shutil.copy2(candidate,frozen/candidate.name);candidate=frozen/candidate.name
    cp=smoke.classpath();qa=fixture(cp)
    interrupted=threading.Event()
    for signum in (signal.SIGINT,signal.SIGTERM):signal.signal(signum,lambda *_:interrupted.set())
    jars={metadata(p)['id']:p for p in (STAGE/'mods').glob('*.jar')}
    profiles=json.loads((STAGE/'MANIFEST.json').read_text())['profiles']
    ports=queue.Queue()
    for port in range(25800,25800+args.workers):ports.put(port)
    def run(mask):
        port=ports.get()
        try:
            if interrupted.is_set():return {'mask':mask,'pass':False,'interrupted':True,'not_started':True}
            selected=[pair for i,pair in enumerate(MODULES) if mask&(1<<i)]
            name=f'{mask:02d}-'+('-'.join(p[0] for p in selected) or 'none')
            run=suite/name;mods=run/'mods';control=run/'control';mods.mkdir(parents=True);control.mkdir()
            ids={'fabric-api','polymer-bundled'}
            for profile,_ in selected:ids.update(set(profiles[profile])-OLD_SHIMS)
            if args.with_standalone_chalk:ids.update(profiles['chalk'])
            sources=[jars[id] for id in sorted(ids)]
            if args.track=='sso-port':
                sources=[p for p in sources if metadata(p)['id'] not in {'simple_smithing_overhaul','defaulted','codecui'}]+[PORT]
            for p in sources+[candidate,qa]:shutil.copy2(p,mods/p.name)
            shutil.copy2(BUNDLE/'qa/chalk/eula.txt',run/'eula.txt')
            (run/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nwhite-list=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=0\ngenerate-structures=false\nlevel-type=minecraft:normal\nmax-players=1\n')
            expected=[id for _,id in selected]+(['chalk'] if args.with_standalone_chalk else [])
            command=['/usr/lib/jvm/java-25-openjdk/bin/java','-Xms256M','-Xmx1G','-XX:ActiveProcessorCount=2','-Dmatrix.qa.control='+str(control),'-Dmatrix.qa.mods='+','.join(expected),'-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui']
            started=time.monotonic();reload_sent=False;stop_sent=False;timeout=False
            with (run/'console.log').open('w') as output:
                process=subprocess.Popen(command,cwd=run,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True)
                try:
                    while process.poll() is None:
                        if interrupted.is_set() and not stop_sent:
                            process.terminate();stop_sent=True
                        if not interrupted.is_set() and (control/'startup.txt').exists() and not reload_sent:
                            process.stdin.write('reload\n');process.stdin.flush();reload_sent=True
                        if not interrupted.is_set() and (control/'reload.txt').exists() and not stop_sent:
                            process.stdin.write('save-all flush\nstop\n');process.stdin.flush();stop_sent=True
                        if time.monotonic()-started>180:timeout=True;process.terminate();break
                        time.sleep(.2)
                    process.wait(timeout=20)
                finally:
                    if process.poll() is None:process.kill();process.wait()
                    process.stdin.close()
            checks={p.stem:p.read_text() for p in control.glob('*.txt')}
            log=(run/'console.log').read_text(errors='replace')
            errors=[line for line in log.splitlines() if '/ERROR]' in line or 'Caused by:' in line or 'Exception' in line]
            result={'mask':mask,'profile':name,'track':args.track,'expected_modules':[id for _,id in selected],'standalone_chalk':args.with_standalone_chalk,'candidate_sha256':digest(candidate),'mods':[{'file':p.name,'id':metadata(p)['id'],'version':metadata(p)['version'],'sha256':digest(p)} for p in sorted(mods.glob('*.jar'))],'checks':checks,'exit_code':process.returncode,'stop_requested':stop_sent,'timed_out':timeout,'interrupted':interrupted.is_set(),'errors':errors,'elapsed_seconds':round(time.monotonic()-started,1),'pass':all(checks.get(k,'').startswith('PASS') for k in ['startup','reload']) and process.returncode==0 and not errors and stop_sent and not timeout and not interrupted.is_set(),'gameplay_tested':False}
            (run/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(name,'PASS' if result['pass'] else 'FAIL',flush=True)
            return result
        finally:ports.put(port)
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:results=list(pool.map(run,masks))
    (suite/'RESULTS.json').write_text(json.dumps(results,indent=2)+'\n')
    print('MATRIX',sum(r['pass'] for r in results),'/',len(results),flush=True)
    return 0 if all(r['pass'] for r in results) else 1
if __name__=='__main__':raise SystemExit(main())
