#!/usr/bin/env python3
import hashlib,importlib.util,json,os,shutil,subprocess,sys,time,zipfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=next(p for p in HERE.parents if (p/'Minecraft/polymer-shim-test-bundle').is_dir())
BUNDLE=ROOT/'Minecraft/polymer-shim-test-bundle'
sys.path.insert(0,str(BUNDLE));spec=importlib.util.spec_from_file_location('lore_smoke',BUNDLE/'smoke_test.py');smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)
suite=sys.argv[1];candidate=Path(sys.argv[2]).resolve();run=HERE/'runs'/suite;run.mkdir(parents=True,exist_ok=False)
mods=run/'mods';shutil.copytree(BUNDLE/'staging/mods',mods)
for p in mods.glob('*.jar'):
 with zipfile.ZipFile(p) as z:meta=json.loads(z.read('fabric.mod.json'))
 if meta['id'] in {'tiered_backpacks_polymer_compat','toolpouch_polymer_compat','mapstitch_polymer_compat','simple_smithing_polymer_compat','sso_backpack_toolpouch_mapstitch_shim'}:p.unlink()
shutil.copy2(candidate,mods/candidate.name)
cp=smoke.classpath();paths=[str(candidate),cp];nested=run/'nested';nested.mkdir()
for p in mods.glob('*.jar'):
 paths.append(str(p))
 with zipfile.ZipFile(p) as z:
  for n in z.namelist():
   if n.endswith('.jar'):
    dest=nested/Path(n).name;dest.write_bytes(z.read(n));paths.append(str(dest))
classes=run/'classes';classes.mkdir()
subprocess.run(['/usr/bin/javac','--release','25','-proc:none','-cp',os.pathsep.join(paths),'-d',str(classes),str(HERE/'src/LoreBoundaryQa.java')],check=True)
with zipfile.ZipFile(mods/'lore-boundary-qa.jar','w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'lore_boundary_qa','version':'1','environment':'server','entrypoints':{'main':['qa.LoreBoundaryQa']},'depends':{'fabric-api':'*','toolpouch':'*'}}))
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
shutil.copy2(BUNDLE/'qa/chalk/eula.txt',run/'eula.txt')
(run/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=26087\nonline-mode=false\nview-distance=2\nsimulation-distance=2\ngenerate-structures=false\n')
command=['/usr/lib/jvm/java-25-openjdk/bin/java','-Xmx1G','-XX:ActiveProcessorCount=2','-Dlore.qa.expectOverflow='+('true' if '--expect-overflow' in sys.argv[3:] else 'false'),'-Dlore.qa.result='+str(run/'assertions.txt'),'-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui']
process=None
try:
 with (run/'console.log').open('w') as log:
  process=subprocess.Popen(command,cwd=run,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
  for _ in range(180):
   if (run/'assertions.txt').exists():break
   if process.poll() is not None:raise RuntimeError('Server exited; inspect console')
   time.sleep(1)
  else:raise TimeoutError('Lore boundary assertions')
finally:
 if process is not None and process.poll() is None:
  process.stdin.write('stop\n');process.stdin.flush()
  try:process.wait(timeout=30)
  except subprocess.TimeoutExpired:process.kill();process.wait()
result={'suite':suite,'candidate_sha256':hashlib.sha256((mods/candidate.name).read_bytes()).hexdigest(),'expect_overflow':'--expect-overflow' in sys.argv[3:],'exit_code':process.returncode if process is not None else None,'assertions':(run/'assertions.txt').read_text() if (run/'assertions.txt').exists() else 'FAIL no assertions','mods':[{'file':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(mods.glob('*.jar'))]}
result['pass']=result['assertions'].startswith('PASS') and result['exit_code']==0;(run/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(result['assertions']);sys.exit(0 if result['pass'] else 1)
