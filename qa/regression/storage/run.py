#!/usr/bin/env python3
"""Rerun existing Tiered storage fixtures on the combined bundle; no production edits."""
import hashlib, importlib.util, json, os, shutil, subprocess, sys, time, zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
BUNDLE = next(p for p in HERE.parents if (p / "Minecraft/polymer-shim-test-bundle").is_dir()) / "Minecraft/polymer-shim-test-bundle"
ROOT = BUNDLE.parents[1]
PROJECT = ROOT / 'Minecraft/tiered-backpacks-polymer-compat-26.3'
QA = PROJECT / 'qa'
sys.path.insert(0, str(BUNDLE))

def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value

smoke = module('storage_smoke', BUNDLE / 'smoke_test.py')
old = module('storage_old', QA / 'launch.py')
suite = sys.argv[1]
candidate = Path(sys.argv[2]).resolve()
run = HERE / 'runs' / suite
run.mkdir(parents=True, exist_ok=False)
control = run / 'control'
control.mkdir()
build = run / 'fixture-build'
classes = build / 'classes'
classes.mkdir(parents=True)
server_cp = smoke.classpath()
audit = json.loads((QA / 'runs/atlas-fixed/native/launch-audit.json').read_text())
client_cp = audit['command'][audit['command'].index('-cp') + 1]
compile_paths = [server_cp, client_cp, str(candidate)]
for jar in (BUNDLE / 'staging/mods').glob('*.jar'):
    compile_paths.append(str(jar))
    with zipfile.ZipFile(jar) as archive:
        for member in archive.namelist():
            if member.endswith('.jar'):
                target = build / Path(member).name
                target.write_bytes(archive.read(member))
                compile_paths.append(str(target))
subprocess.run(['/usr/bin/javac', '--release', '25', '-proc:none', '-cp', os.pathsep.join(compile_paths),
                '-d', str(classes), *map(str, (HERE / 'src').glob('*.java'))], check=True)
for side in ['server', 'client']:
    with zipfile.ZipFile(build / (side + '.jar'), 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'backpack_qa_' + side,
            'version': '1', 'environment': side, 'entrypoints': {'main' if side == 'server' else 'client':
            ['qa.Backpack' + side.title() + 'Qa']}, 'depends': {'tiered_backpacks': '*', 'fabric-api': '*'}}))
        for path in classes.rglob('*.class'):
            if side.title() in path.name:
                archive.write(path, path.relative_to(classes))

env = os.environ.copy()
env.update(SDL_VIDEODRIVER='x11', SDL_VIDEO_X11_XINPUT2='0', LP_NUM_THREADS='2')
server = run / 'server'
shutil.copytree(BUNDLE / 'staging/mods', server / 'mods')
for jar in (server / 'mods').glob('*.jar'):
    with zipfile.ZipFile(jar) as z: mod_id=json.loads(z.read('fabric.mod.json'))['id']
    if mod_id in {'tiered_backpacks_polymer_compat','toolpouch_polymer_compat','mapstitch_polymer_compat','simple_smithing_polymer_compat'}: jar.unlink()
shutil.copy2(candidate, server / 'mods' / candidate.name)
shutil.copy2(build / 'server.jar', server / 'mods/backpack-qa-server.jar')
shutil.copy2(BUNDLE / 'qa/chalk/eula.txt', server / 'eula.txt')
(server / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25936\nonline-mode=false\n'
    'white-list=false\nenforce-secure-profile=false\nview-distance=3\nsimulation-distance=3\n'
    'spawn-protection=0\npause-when-empty-seconds=0\ngenerate-structures=false\ndifficulty=peaceful\n')
server_command = ['/usr/lib/jvm/java-25-openjdk/bin/java', '-Xmx1G', '-XX:ActiveProcessorCount=2',
    '-Dbackpack.qa.control=' + str(control), '-cp', server_cp, 'net.fabricmc.loader.impl.launch.knot.KnotServer', 'nogui']

def record(directory, command):
    data = {'command': command, 'mods': [{'file': jar.name, 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest()}
        for jar in sorted((directory / 'mods').glob('*.jar'))]}
    (directory / 'launch-audit.json').write_text(json.dumps(data, indent=2) + '\n')
    return data

def stop(process, is_server=False):
    if process.poll() is None:
        if is_server:
            process.stdin.write('stop\n')
            process.stdin.flush()
        else:
            process.terminate()
    try:
        process.wait(timeout=25)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()

results = {'suite': suite, 'pass': False, 'server': record(server, server_command), 'clients': {}}
server_process = None
client_process = None
try:
    with (server / 'console.log').open('w') as server_log:
        server_process = subprocess.Popen(server_command, cwd=server, env=env, stdin=subprocess.PIPE,
            stdout=server_log, stderr=subprocess.STDOUT, text=True)
        for _ in range(180):
            if server_process.poll() is not None:
                raise RuntimeError('Server exited during startup')
            if 'Done (' in (server / 'console.log').read_text(errors='replace'):
                break
            time.sleep(1)
        else:
            raise TimeoutError('Server startup')
        runtime_path = control / 'runtime-result.txt'
        for _ in range(30):
            if runtime_path.exists(): break
            if server_process.poll() is not None: raise RuntimeError('Server exited before runtime assertions')
            time.sleep(1)
        runtime = runtime_path.read_text()
        if not runtime.startswith('PASS'): raise RuntimeError(runtime)
        print('STORAGE_SERVER_READY', runtime.strip(), flush=True)
        for mode, username in [('native', 'BackpackNativeQA'), ('native-polymer', 'PackPolymerQA'), ('vanilla', 'PackVanillaQA')]:
            directory = run / mode
            (directory / 'mods').mkdir(parents=True)
            if mode != 'vanilla':
                for jar in (BUNDLE / 'staging/native-client/mods').glob('*.jar'):
                    shutil.copy2(jar, directory / 'mods' / jar.name)
                shutil.copy2(build / 'client.jar', directory / 'mods/backpack-qa-client.jar')
            if mode == 'native-polymer':
                polymer = next((BUNDLE / 'staging/mods').glob('polymer-bundled-*.jar'))
                shutil.copy2(polymer, directory / 'mods' / polymer.name)
            (directory / 'options.txt').write_text('graphicsMode:0\nrenderDistance:3\nsimulationDistance:3\n'
                'maxFps:25\nmaxFpsInactive:25\nsoundCategory_master:0.0\njoinedFirstServer:true\n')
            command = old.base_command(mode, directory, 25936)
            command = [argument.replace('-XX:ActiveProcessorCount=4', '-XX:ActiveProcessorCount=2') for argument in command]
            if mode != 'vanilla':
                command.insert(1, '-Dbackpack.qa.control=' + str(control))
            results['clients'][mode] = record(directory, command)
            expected = ['server-result.txt', 'survived.txt'] if mode == 'vanilla' else ['client-result.txt', 'native-notice-result.txt', 'atlas-result.txt', 'survived.txt']
            with (directory / 'console.log').open('w') as client_log:
                client_process = subprocess.Popen(command, cwd=directory, env=env, stdin=subprocess.PIPE,
                    stdout=client_log, stderr=subprocess.STDOUT, text=True)
                print('STORAGE_CLIENT_STARTED', mode, flush=True)
                for _ in range(260):
                    evidence = {name: (control / username / name).read_text() for name in expected if (control / username / name).exists()}
                    if any(value.startswith('FAIL') for value in evidence.values()):
                        raise RuntimeError(mode + ' fixture failure: ' + repr(evidence))
                    if len(evidence) == len(expected) and all(value.startswith('PASS') for value in evidence.values()):
                        results['clients'][mode]['results'] = evidence
                        print('STORAGE_CLIENT_PASS', mode, flush=True)
                        break
                    if client_process.poll() is not None:
                        raise RuntimeError(mode + ' exited before fixture finished')
                    time.sleep(1)
                else:
                    raise TimeoutError(mode + ' fixture: ' + repr(evidence))
                stop(client_process)
                client_process = None
        runtime = (control / 'runtime-result.txt').read_text()
        if not runtime.startswith('PASS'):
            raise RuntimeError(runtime)
        results['runtime'] = runtime
        results['pass'] = True
except Exception as error:
    results['error'] = repr(error)
    print('STORAGE_FAILURE', repr(error), flush=True)
finally:
    if client_process is not None:
        stop(client_process)
    if server_process is not None:
        stop(server_process, True)
    (run / 'result.json').write_text(json.dumps(results, indent=2) + '\n')
    print('STORAGE_RESULT', results['pass'], str(run / 'result.json'), flush=True)
sys.exit(0 if results['pass'] else 1)
