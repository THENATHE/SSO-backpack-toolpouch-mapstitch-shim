#!/usr/bin/env python3
import os,sys,json,zipfile,subprocess,time,shutil,hashlib,importlib.util
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[4]
BUNDLE=ROOT/'Minecraft/polymer-shim-test-bundle'
sys.path.insert(0,str(BUNDLE))
spec=importlib.util.spec_from_file_location('smoke',BUNDLE/'smoke_test.py');smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)
AUDIT=ROOT/'Minecraft/tiered-backpacks-polymer-compat-26.3/qa/runs/atlas-fixed/native/launch-audit.json'
base=json.loads(AUDIT.read_text())['command']
server_cp=smoke.classpath()
client_cp=base[base.index('-cp')+1]
fixture_build=HERE/'fixtures';fixture_build.mkdir(exist_ok=True)
compile_cp=server_cp+os.pathsep+client_cp
for jar in (BUNDLE/'staging/mods').glob('*.jar'):
 compile_cp+=os.pathsep+str(jar)
 with zipfile.ZipFile(jar) as z:
  for n in z.namelist():
   if n.endswith('.jar'):
    dest=fixture_build/Path(n).name;dest.write_bytes(z.read(n));compile_cp+=os.pathsep+str(dest)
classes=fixture_build/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['/usr/bin/javac','--release','25','-proc:none','-cp',compile_cp,'-d',str(classes),*map(str,(HERE/'src').glob('*.java'))],check=True)
for side in ['server','client']:
 with zipfile.ZipFile(fixture_build/(side+'.jar'),'w',zipfile.ZIP_DEFLATED) as z:
  z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'creative_qa_'+side,'version':'1','environment':side,'entrypoints':{'main' if side=='server' else 'client':['qa.Creative'+side.title()]},'depends':{'fabric-api':'*'}}))
  for p in classes.rglob('*.class'):
   if side.title() in p.name:z.write(p,p.relative_to(classes))
suite=sys.argv[1];run=HERE/'runs'/suite;run.mkdir(parents=True,exist_ok=True)
control=run/'control';control.mkdir(exist_ok=True)
assert not (control/'request').exists(),'Use a fresh suite name'
server=run/'server';client=run/'client'
selection=sys.argv[3] if len(sys.argv)>3 else 'combined'
server_source=BUNDLE/'staging'/('mods' if selection=='combined' else 'individual/'+selection+'/server/mods')
client_source=BUNDLE/'staging'/('native-client/mods' if selection=='combined' else 'individual/'+selection+'/native-client/mods')
for side,dest,source in [('server',server,server_source),('client',client,client_source)]:
 shutil.copytree(source,dest/'mods',dirs_exist_ok=True)
 shutil.copy2(fixture_build/(side+'.jar'),dest/'mods'/('creative-qa-'+side+'.jar'))
if len(sys.argv)>2 and sys.argv[2]!='original':
 for p in (server/'mods').glob('*.jar'):
  if p.name.startswith(('tiered-backpacks-polymer-compat-', 'simple-smithing-polymer-compat-', 'toolpouch-polymer-compat-', 'mapstitch-polymer-compat-')):p.unlink()
 p=Path(sys.argv[2]).resolve();shutil.copy2(p,server/'mods'/p.name)
# Keep the existing port target isolated from developer-only dependencies.
if os.environ.get('SSO_QA_TRACK') == 'port':
 port=ROOT/'Builds/Minecraft/Simple Smithing Overhaul/Main Plugin - Version Port/2.9.14-port.1 - Fabric 26.3/simple_smithing_overhaul-fabric-2.9.14-port.1+26.3.jar'
 assert port.is_file(),port
 for dest in (server,client):
  for jar in (dest/'mods').glob('*.jar'):
   if jar.name.startswith(('simple_smithing_overhaul-', 'defaulted-', 'codecui-')):jar.unlink()
  shutil.copy2(port,dest/'mods'/port.name)
# Test-only custom dimension using a vanilla dimension type and generator.
pack=server/'world/datapacks/qa-extra';(pack/'data/qa/dimension').mkdir(parents=True,exist_ok=True)
(pack/'pack.mcmeta').write_text(json.dumps({'pack':{'description':'QA extra dimension','min_format':[121,0],'max_format':[121,0]}}))
(pack/'data/qa/dimension/extra.json').write_text(json.dumps({'type':'minecraft:overworld','generator':{'type':'minecraft:flat','settings':{'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:grass_block','height':1}]}}}))
shutil.copy2(BUNDLE/'qa/chalk/eula.txt',server/'eula.txt')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25726\nonline-mode=false\nwhite-list=false\nenforce-secure-profile=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\npause-when-empty-seconds=0\ngenerate-structures=false\n')
(client/'options.txt').write_text('graphicsMode:0\nrenderDistance:3\nsimulationDistance:3\nmaxFps:30\nmaxFpsInactive:30\nsoundCategory_master:0.0\njoinedFirstServer:true\n')
variant='-Dwhetstone.enchanted='+os.environ.get('WHETSTONE_ENCHANTED','false')
server_cmd=['/usr/lib/jvm/java-25-openjdk/bin/java',variant,'-Xmx1G','-XX:ActiveProcessorCount=2','-Dcreative.qa.control='+str(control),'-cp',server_cp,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui']
client_cmd=[s for s in base if not s.startswith('-Dbackpack.')]
client_cmd.insert(1,'-Dcreative.qa.control='+str(control))
client_cmd.insert(1,variant)
for key,value in [('--gameDir',str(client)),('--quickPlayMultiplayer','127.0.0.1:25726')]:client_cmd[client_cmd.index(key)+1]=value
for prefix,folder in [('java.library.path','java'),('jna.tmpdir','jna'),('org.lwjgl.system.SharedLibraryExtractPath','lwjgl'),('io.netty.native.workdir','netty')]:
 client_cmd=[('-D'+prefix+'='+str(client/'natives'/folder)) if s.startswith('-D'+prefix+'=') else s for s in client_cmd]
# Preserve offline identity from historical QA audit, never read launcher account credentials.
env=os.environ.copy();env.update(SDL_VIDEODRIVER='x11',SDL_VIDEO_X11_XINPUT2='0',LP_NUM_THREADS='4')
processes=[];handles=[]
try:
 for kind,dest,command in [('server',server,server_cmd),('client',client,client_cmd)]:
  log=(dest/'console.log').open('w');handles.append(log)
  p=subprocess.Popen(command,cwd=dest,env=env,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True);processes.append(p)
  if kind=='server':
   for _ in range(180):
    if p.poll() is not None:raise RuntimeError('Server exited; inspect log')
    if 'Done (' in (dest/'console.log').read_text(errors='replace'):break
    time.sleep(1)
   else:raise TimeoutError('server ready')
   print('SERVER_READY',suite,flush=True)
 start=time.monotonic();last=''
 while time.monotonic()-start<240:
  result=control/'client-result';progress=control/'server-progress'
  if progress.exists() and progress.read_text()!=last:
   last=progress.read_text();print(last,flush=True)
  if result.exists():print(result.read_text(),flush=True);break
  if (control/'disconnect').exists():print((control/'disconnect').read_text(),flush=True);break
  if processes[1].poll() is not None:print('CLIENT_EXIT',processes[1].returncode,flush=True);break
  time.sleep(1)
finally:
 for p in processes[1:]:
  if p.poll() is None:p.terminate()
 if processes and processes[0].poll() is None:
  processes[0].stdin.write('stop\n');processes[0].stdin.flush()
 for p in processes:
  try:p.wait(timeout=25)
  except subprocess.TimeoutExpired:p.kill();p.wait()
 for h in handles:h.close()
 result={f.name:f.read_text() for f in control.glob('*') if f.is_file()}
 result['mods']={side:[{'file':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted((d/'mods').glob('*.jar'))] for side,d in [('server',server),('client',client)]}
 result['suite']=suite;result['pass']=result.get('client-result','').startswith('PASS') and result.get('server-result','').startswith('PASS')
 (run/'result.json').write_text(json.dumps(result,indent=2)+'\n')
 print('RESULT',result['pass'],flush=True)
