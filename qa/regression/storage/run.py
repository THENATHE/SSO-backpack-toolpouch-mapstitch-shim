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

# Explicit QA-only target override: never edit staging or production libraries.
tiered_override = Path(os.environ['TIERED_QA_JAR']).resolve() if os.environ.get('TIERED_QA_JAR') else None
addon_override = Path(os.environ['STORAGE_QA_ADDON_JAR']).resolve() if os.environ.get('STORAGE_QA_ADDON_JAR') else None
if addon_override:
    with zipfile.ZipFile(addon_override) as archive:
        addon_metadata = json.loads(archive.read('fabric.mod.json'))
    assert addon_metadata['id'] == 'toolpouch_atlas_elytra_compat', 'STORAGE_QA_ADDON_JAR must contain the Atlas/Elytra addon'
    addon_before = hashlib.sha256(addon_override.read_bytes()).hexdigest()
if tiered_override:
    with zipfile.ZipFile(tiered_override) as archive:
        tiered_metadata = json.loads(archive.read('fabric.mod.json'))
    assert tiered_metadata['id'] == 'tiered_backpacks', 'TIERED_QA_JAR must contain Tiered Backpacks'
    tiered_before = hashlib.sha256(tiered_override.read_bytes()).hexdigest()

def qa_mods(directory):
    paths = list(directory.glob('*.jar'))
    if tiered_override:
        paths = [path for path in paths if not path.name.startswith('tiered_backpacks-')]
        paths.append(tiered_override)
    if addon_override:
        paths = [path for path in paths if not path.name.startswith('toolpouch-atlas-elytra-compat-')]
        paths.append(addon_override)
    if os.environ.get('SSO_QA_TRACK') == 'sso-port':
        paths = [path for path in paths if not path.name.startswith(('simple_smithing_overhaul-', 'defaulted-', 'codecui-'))]
        paths.append(ROOT / 'Builds/Minecraft/Simple Smithing Overhaul/Main Plugin - Version Port/2.9.14-port.1 - Fabric 26.3/simple_smithing_overhaul-fabric-2.9.14-port.1+26.3.jar')
    elif os.environ.get('SSO_QA_TRACK', 'developer') != 'developer':
        raise ValueError('SSO_QA_TRACK must be developer or sso-port')
    return paths

def override_record():
    if not tiered_override:
        return None
    after = hashlib.sha256(tiered_override.read_bytes()).hexdigest()
    assert after == tiered_before, 'Tiered QA input changed during run'
    return {'file': tiered_override.name, 'version': tiered_metadata['version'], 'sha256': after, 'original_unchanged': True}


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value

smoke = module('storage_smoke', BUNDLE / 'smoke_test.py')
old = module('storage_old', QA / 'launch.py')
suite = sys.argv[1]
candidate = Path(sys.argv[2]).resolve()
server_port = int(os.environ.get('STORAGE_QA_PORT', '25936'))
client_modes = os.environ.get('STORAGE_QA_CLIENT_MODES', 'native,native-polymer,vanilla').split(',')
assert client_modes and set(client_modes) <= {'native', 'native-polymer', 'vanilla'}, 'Invalid STORAGE_QA_CLIENT_MODES'
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
for jar in qa_mods(BUNDLE / 'staging/mods'):
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
(server / 'mods').mkdir(parents=True)
for jar in qa_mods(BUNDLE / 'staging/mods'):
    shutil.copy2(jar, server / 'mods' / jar.name)
for jar in (server / 'mods').glob('*.jar'):
    with zipfile.ZipFile(jar) as z: mod_id=json.loads(z.read('fabric.mod.json'))['id']
    if mod_id in {'tiered_backpacks_polymer_compat','toolpouch_polymer_compat','mapstitch_polymer_compat','simple_smithing_polymer_compat'}: jar.unlink()
shutil.copy2(candidate, server / 'mods' / candidate.name)
shutil.copy2(build / 'server.jar', server / 'mods/backpack-qa-server.jar')
shutil.copy2(BUNDLE / 'qa/chalk/eula.txt', server / 'eula.txt')
(server / 'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={server_port}\nonline-mode=false\n'
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
            if mode not in client_modes:
                continue
            directory = run / mode
            (directory / 'mods').mkdir(parents=True)
            if mode != 'vanilla':
                for jar in qa_mods(BUNDLE / 'staging/native-client/mods'):
                    shutil.copy2(jar, directory / 'mods' / jar.name)
                shutil.copy2(build / 'client.jar', directory / 'mods/backpack-qa-client.jar')
            if mode == 'native-polymer':
                polymer = next((BUNDLE / 'staging/mods').glob('polymer-bundled-*.jar'))
                shutil.copy2(polymer, directory / 'mods' / polymer.name)
            gui_pack = Path(os.environ['TIERED_QA_GUI_PACK']).resolve() if mode == 'native-polymer' and os.environ.get('TIERED_QA_GUI_PACK') else None
            if gui_pack:
                (directory / 'resourcepacks').mkdir()
                shutil.copy2(gui_pack, directory / 'resourcepacks' / gui_pack.name)
            (directory / 'options.txt').write_text('graphicsMode:0\nrenderDistance:3\nsimulationDistance:3\n'
                'maxFps:25\nmaxFpsInactive:25\nsoundCategory_master:0.0\njoinedFirstServer:true\n')
            if gui_pack:
                with (directory / 'options.txt').open('a') as options:
                    options.write('resourcePacks:' + json.dumps(['vanilla', 'file/' + gui_pack.name]) + '\n')
            command = old.base_command(mode, directory, server_port)
            command = [argument.replace('-XX:ActiveProcessorCount=4', '-XX:ActiveProcessorCount=2') for argument in command]
            if mode != 'vanilla':
                command.insert(1, '-Dbackpack.qa.control=' + str(control))
            results['clients'][mode] = record(directory, command)
            if gui_pack:
                results['clients'][mode]['gui_pack'] = {'file': gui_pack.name, 'sha256': hashlib.sha256(gui_pack.read_bytes()).hexdigest()}
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
    results['tiered_override'] = override_record()
    results['sso_track'] = os.environ.get('SSO_QA_TRACK', 'developer')
    if addon_override:
        addon_after = hashlib.sha256(addon_override.read_bytes()).hexdigest()
        assert addon_after == addon_before, 'Addon QA input changed during run'
        results['addon_override'] = {'file': addon_override.name, 'version': addon_metadata['version'], 'sha256': addon_after, 'original_unchanged': True}
    (run / 'result.json').write_text(json.dumps(results, indent=2) + '\n')
    print('STORAGE_RESULT', results['pass'], str(run / 'result.json'), flush=True)
sys.exit(0 if results['pass'] else 1)
