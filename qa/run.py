from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent
PROFILE = Path(os.environ.get('HOTKEY_WHEEL_PROFILE', str(Path.home() / '.local/share/ModrinthApp/profiles/Vanilla Fast')))
META = Path(os.environ.get('HOTKEY_WHEEL_META', str(PROFILE.parents[1] / 'meta')))
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
SUITE = '--suite' in sys.argv
INTEGRATED = '--integrated' in sys.argv or SUITE
RUN = ROOT / ('suite' if SUITE else 'integrated' if INTEGRATED else 'minimal')
RUN.mkdir(exist_ok=True)
GAME = RUN / 'game'
MODS = GAME / 'mods'
MODS.mkdir(parents=True, exist_ok=True)
for old in MODS.glob('*.jar'):
    old.unlink()
patterns = ['fabric-api-*.jar']
if INTEGRATED:
    patterns += ['Controlling-*.jar', 'Searchables-*.jar', 'modmenu-*.jar',
                 'sorted-fabric-*.jar', 'buildprint-fabric-*.jar', 'buildprint-toning-*.jar',
                 'yet_another_config_lib*.jar']
if SUITE:
    patterns += ['vanilla-plusplus-quality-of-life-suite-1.1.6+26.3.jar', 'fzzy_config-0.7.7+fix3+26.3.jar',
                 'cloth-config-*.jar', 'fabric-language-kotlin-*.jar', 'codecui-*.jar', 'mixson-*.jar']
for pattern in patterns:
    for source in (PROFILE / 'mods').glob(pattern):
        shutil.copy2(source, MODS / source.name)
version = next(line.split('=', 1)[1].strip() for line in (ROOT.parent / 'gradle.properties').read_text().splitlines()
               if line.startswith('mod_version='))
addon = ROOT.parent / 'build/libs' / f'hotkey-wheel-{version}.jar'
shutil.copy2(addon, MODS / addon.name)
gametest = next((CACHE / 'net.fabricmc.fabric-api/fabric-client-gametest-api-v1/6.0.7+4be74c3f5d').glob('*/*5d.jar'))
shutil.copy2(gametest, MODS / gametest.name)
info = json.loads((META / 'versions/26.3-0.19.5/26.3-0.19.5.json').read_text())
classpath = [META / 'versions/26.3/26.3.jar']
for library in info['libraries']:
    path = library.get('downloads', {}).get('artifact', {}).get('path')
    if path:
        artifact = META / 'libraries' / path
    else:
        group, name, version = library['name'].split(':')[:3]
        artifact = META / 'libraries' / group.replace('.', '/') / name / version / f'{name}-{version}.jar'
    if artifact.is_file() and '-natives-' not in artifact.name:
        classpath.append(artifact)
dependencies = RUN / 'dependencies'
dependencies.mkdir(exist_ok=True)
with zipfile.ZipFile(next(MODS.glob('fabric-api-*.jar'))) as archive:
    for name in archive.namelist():
        if name.startswith('META-INF/jars/') and name.endswith('.jar'):
            (dependencies / Path(name).name).write_bytes(archive.read(name))
if SUITE:
    with zipfile.ZipFile(next(MODS.glob('vanilla-plusplus-quality-of-life-suite-*.jar'))) as archive:
        for name in archive.namelist():
            if name.startswith('META-INF/jars/') and name.endswith('.jar'):
                (dependencies / Path(name).name).write_bytes(archive.read(name))
classes = RUN / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
compilepath = classpath + [gametest, addon] + list(dependencies.glob('*.jar')) + list(MODS.glob('*.jar'))
compiler = Path(os.environ.get('HOTKEY_WHEEL_JDK', os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-25-openjdk'))) / 'bin/javac'
if not compiler.is_file():
    compiler = next((Path.home() / '.gradle/jdks').glob('*25*/bin/javac'), None)
if compiler is None or not compiler.is_file():
    raise SystemExit('Set HOTKEY_WHEEL_JDK to a complete Java 25 JDK.')
test_names = ['WheelClientTest', 'KeyManagementTest']
if INTEGRATED:
    test_names.append('BuildprintMenuTest')
if SUITE:
    test_names.append('ToolPouchClientTest')
subprocess.run([str(compiler), '--release', '25',
                '-proc:none', '-cp', os.pathsep.join(map(str, compilepath)), '-d', str(classes),
                *(str(ROOT / (name + '.java')) for name in test_names)], check=True)
with zipfile.ZipFile(MODS / 'hotkey-wheel-qa.jar', 'w', zipfile.ZIP_DEFLATED) as archive:
    archive.writestr('fabric.mod.json', json.dumps({
        'schemaVersion': 1, 'id': 'hotkey_wheel_qa', 'version': '1', 'environment': 'client',
        'entrypoints': {'fabric-client-gametest': ['com.thenathe.hotkeywheel.qa.' + name for name in test_names]},
        'depends': {'fabric-client-gametest-api-v1': '*', 'hotkey_wheel': '*'}}))
    for source in classes.rglob('*.class'):
        archive.write(source, source.relative_to(classes))
(GAME / 'options.txt').write_text('onboardAccessibility:false\nrenderDistance:2\nsimulationDistance:5\n'
                                  'guiScale:2\ntutorialStep:none\nmaxFps:60\npauseOnLostFocus:false\nfullscreen:false\n')
(RUN / 'results.json').unlink(missing_ok=True)
for name in ['key-management-results.json', 'buildprint-menu-results.json', 'toolpouch-results.json']:
    (RUN / name).unlink(missing_ok=True)
command = [str(compiler.parent / 'java'), '-Xmx2G', '-XX:ActiveProcessorCount=4',
           '-Djava.library.path=' + str(META / 'natives/26.3-0.19.5/lwjgl/3.4.3-snapshot/x64'),
           '-Dfabric.client.gametest', '-Dfabric.client.gametest.timeout=2400',
           '-Dwheel.qa.root=' + str(RUN), '-Dfabric.log.disableAnsi=true',
           '-cp', os.pathsep.join(map(str, classpath)), 'net.fabricmc.loader.impl.launch.knot.KnotClient',
           '--gameDir', str(GAME), '--version', '26.3', '--username', 'WheelQA', '--accessToken', '0',
           '--assetsDir', str(META / 'assets'), '--assetIndex', '34', '--width', '1280', '--height', '800']
(RUN / 'provenance.json').write_text(json.dumps({
    'minecraft': '26.3', 'loader': '0.19.5', 'integrated': INTEGRATED, 'suite': SUITE,
    'mods': {source.name: hashlib.sha256(source.read_bytes()).hexdigest() for source in sorted(MODS.glob('*.jar'))}}, indent=2))
environment = os.environ.copy()
environment['SDL_VIDEODRIVER'] = 'x11'
with (RUN / 'console.log').open('w') as log:
    process = subprocess.Popen(command, cwd=GAME, env=environment, stdout=log, stderr=subprocess.STDOUT)
    try:
        code = process.wait(timeout=600)
    except subprocess.TimeoutExpired:
        process.terminate()
        process.wait(timeout=20)
        raise
print('EXIT', code)
print((RUN / 'console.log').read_text()[-5000:])
reports = ['results.json', 'key-management-results.json']
if INTEGRATED:
    reports.append('buildprint-menu-results.json')
if SUITE:
    reports.append('toolpouch-results.json')
for name in reports:
    path = RUN / name
    if not path.exists():
        raise SystemExit(f'Missing test report: {name}')
    result = json.loads(path.read_text())
    print(name, 'passed:', result.get('passed'), 'checks:', len(result.get('checks', [])))
    if not result.get('passed'):
        print(path.read_text())
        raise SystemExit(f'Failed test report: {name}')
raise SystemExit(code)
