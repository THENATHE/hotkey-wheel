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
BASELINE = '--api-baseline' in sys.argv
PREVIOUS = '--api-previous' in sys.argv
LEGACY = '--legacy' in sys.argv
POLYMER = '--polymer' in sys.argv
SUITE = '--suite' in sys.argv or POLYMER
INTEGRATED = '--integrated' in sys.argv or SUITE or BASELINE or PREVIOUS or LEGACY
BUILDPRINT_VERSION = '0.1.16+26.x' if LEGACY else '0.1.17+26.x' if BASELINE else '0.1.18+26.x' if PREVIOUS else '0.1.19+26.x'
BLOCKTONE_VERSION = '0.1.20' if LEGACY else '0.1.23' if BASELINE or PREVIOUS else '0.1.24'
SUITE_VERSION = os.environ.get('HOTKEY_WHEEL_SUITE_VERSION', '1.4.4+26.3')
BRBR_VERSION = os.environ.get('HOTKEY_WHEEL_BRBR_VERSION', '1.11.7+26.3')
POLYMER_VERSION = os.environ.get('HOTKEY_WHEEL_POLYMER_VERSION', '0.18.2+26.3')
WORKSPACE = ROOT.parents[2]
if sum([BASELINE, PREVIOUS, LEGACY, SUITE]) > 1:
    raise SystemExit('Baseline and legacy modes are separate integrated profiles.')
RUN = ROOT / ('legacy' if LEGACY else 'api-baseline' if BASELINE else 'api-previous' if PREVIOUS else 'suite-polymer' if POLYMER else 'suite' if SUITE else 'integrated' if INTEGRATED else 'minimal')
RUN.mkdir(exist_ok=True)
GAME = RUN / 'game'
MODS = GAME / 'mods'
MODS.mkdir(parents=True, exist_ok=True)
for old in MODS.glob('*.jar'):
    old.unlink()
patterns = ['fabric-api-*.jar']
if INTEGRATED:
    patterns += ['Controlling-*.jar', 'Searchables-*.jar', 'modmenu-*.jar',
                 'sorted-fabric-*.jar',
                 'yet_another_config_lib*.jar']
if SUITE:
    patterns += ['fzzy_config-0.7.7+fix3+26.3.jar',
                 'cloth-config-*.jar', 'fabric-language-kotlin-*.jar', 'codecui-*.jar', 'mixson-*.jar']
for pattern in patterns:
    matches = list((PROFILE / 'mods').glob(pattern))
    if len(matches) != 1:
        raise SystemExit(f'Expected one profile dependency for {pattern}; found {len(matches)}')
    shutil.copy2(matches[0], MODS / matches[0].name)
if SUITE:
    for variable, default, mod_id in [
        ('HOTKEY_WHEEL_SUITE_JAR', WORKSPACE / 'Builds/Minecraft/Vanilla++ Quality of Life Suite/Main Plugin' / SUITE_VERSION / f'vanilla-plusplus-quality-of-life-suite-{SUITE_VERSION}.jar', 'vpp_mod_suite'),
        ('HOTKEY_WHEEL_BRBR_JAR', WORKSPACE / 'Builds/Minecraft/Better Recipe Book/Main Plugin - Version Port' / BRBR_VERSION / f'better-recipe-book-rebetterered-{BRBR_VERSION}.jar', 'brb'),
    ]:
        source = Path(os.environ.get(variable, str(default)))
        if not source.is_file():
            raise SystemExit(f'Missing pinned integration artifact: {source}')
        with zipfile.ZipFile(source) as archive:
            metadata = json.loads(archive.read('fabric.mod.json'))
        if metadata['id'] != mod_id:
            raise SystemExit(f'Unexpected integration mod ID in {source}: {metadata["id"]}')
        if mod_id == 'vpp_mod_suite':
            SUITE_VERSION = metadata['version']
        else:
            BRBR_VERSION = metadata['version']
        shutil.copy2(source, MODS / source.name)
if POLYMER:
    source = Path(os.environ.get('HOTKEY_WHEEL_POLYMER_JAR', str(WORKSPACE / 'Builds/Minecraft/Polymer/Main Plugin' / POLYMER_VERSION / f'polymer-bundled-{POLYMER_VERSION}.jar')))
    if not source.is_file():
        raise SystemExit(f'Missing pinned Polymer artifact: {source}')
    with zipfile.ZipFile(source) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
    if metadata['id'] != 'polymer-bundled':
        raise SystemExit(f'Unexpected Polymer mod ID: {metadata["id"]}')
    POLYMER_VERSION = metadata['version']
    shutil.copy2(source, MODS / source.name)
if INTEGRATED:
    for source in [
        WORKSPACE / 'Builds/Minecraft/Buildprint/Main Plugin' / BUILDPRINT_VERSION / f'buildprint-fabric-{BUILDPRINT_VERSION}.jar',
        Path(os.environ.get('HOTKEY_WHEEL_BLOCKTONE_JAR', str(WORKSPACE / 'Builds/Minecraft/Buildprint/Feature Addon - Blocktone Toning' / BLOCKTONE_VERSION / f'buildprint-toning-{BLOCKTONE_VERSION}.jar'))) if BUILDPRINT_VERSION == '0.1.19+26.x' else WORKSPACE / 'Builds/Minecraft/Buildprint/Feature Addon - Blocktone Toning' / BLOCKTONE_VERSION / f'buildprint-toning-{BLOCKTONE_VERSION}.jar',
    ]:
        if not source.is_file():
            raise SystemExit(f'Missing pinned integration artifact: {source}')
        if source.name.startswith('buildprint-toning-'):
            with zipfile.ZipFile(source) as archive:
                metadata = json.loads(archive.read('fabric.mod.json'))
            if metadata['id'] != 'buildprint_toning':
                raise SystemExit(f'Unexpected Blocktone mod ID: {metadata["id"]}')
            BLOCKTONE_VERSION = metadata['version']
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
if dependencies.exists():
    shutil.rmtree(dependencies)
dependencies.mkdir()
archives = list(MODS.glob('*.jar'))
while archives:
    source = archives.pop()
    with zipfile.ZipFile(source) as archive:
        for name in archive.namelist():
            if name.startswith('META-INF/jars/') and name.endswith('.jar'):
                data = archive.read(name)
                destination = dependencies / (hashlib.sha256(data).hexdigest()[:16] + '-' + Path(name).name)
                if destination.exists():
                    if destination.read_bytes() != data:
                        raise SystemExit(f'Conflicting bundled dependency: {destination.name}')
                    continue
                destination.write_bytes(data)
                archives.append(destination)
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
    test_names += ['ToolPouchClientTest', 'BrbrIntegrationTest']
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
for name in ['key-management-results.json', 'buildprint-menu-results.json', 'toolpouch-results.json', 'brbr-integration-results.json']:
    (RUN / name).unlink(missing_ok=True)
command = [str(compiler.parent / 'java'), '-Xmx2G', '-XX:ActiveProcessorCount=4',
           '-Djava.library.path=' + str(META / 'natives/26.3-0.19.5/lwjgl/3.4.3-snapshot/x64'),
           '-Dfabric.client.gametest', '-Dfabric.client.gametest.timeout=2400',
           '-Dwheel.qa.root=' + str(RUN), '-Dwheel.qa.suite.version=' + SUITE_VERSION,
           '-Dwheel.qa.brbr.version=' + BRBR_VERSION, '-Dfabric.log.disableAnsi=true',
           '-cp', os.pathsep.join(map(str, classpath)), 'net.fabricmc.loader.impl.launch.knot.KnotClient',
           '--gameDir', str(GAME), '--version', '26.3', '--username', 'WheelQA', '--accessToken', '0',
           '--assetsDir', str(META / 'assets'), '--assetIndex', '34', '--width', '1280', '--height', '800']
(RUN / 'provenance.json').write_text(json.dumps({
    'minecraft': '26.3', 'loader': '0.19.5', 'integrated': INTEGRATED, 'suite': SUITE, 'polymer': POLYMER,
    'buildprint': BUILDPRINT_VERSION if INTEGRATED else None, 'blocktone': BLOCKTONE_VERSION if INTEGRATED else None,
    'suite_version': SUITE_VERSION if SUITE else None, 'brbr': BRBR_VERSION if SUITE else None,
    'polymer_version': POLYMER_VERSION if POLYMER else None,
    'mods': {source.name: hashlib.sha256(source.read_bytes()).hexdigest() for source in sorted(MODS.glob('*.jar'))}}, indent=2))
if '--prepare-only' in sys.argv:
    print('Prepared isolated QA profile:', RUN)
    raise SystemExit(0)
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
    reports += ['toolpouch-results.json', 'brbr-integration-results.json']
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
