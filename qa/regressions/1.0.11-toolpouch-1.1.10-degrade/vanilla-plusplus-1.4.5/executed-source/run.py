from pathlib import Path
import hashlib
import io
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
# Standalone Tool Pouch with Buildprint, for Tool Pouch releases newer than the copy nested in the suite.
TOOLPOUCH = '--toolpouch' in sys.argv
INTEGRATED = '--integrated' in sys.argv or SUITE or TOOLPOUCH or BASELINE or PREVIOUS or LEGACY
# Blocktone pins one exact Buildprint release, so a new Buildprint can be checked before its Blocktone build exists.
BLOCKTONE = INTEGRATED and '--no-blocktone' not in sys.argv
CURRENT_BUILDPRINT = '0.1.38+26.3'
BUILDPRINT_VERSION = '0.1.16+26.x' if LEGACY else '0.1.17+26.x' if BASELINE else '0.1.18+26.x' if PREVIOUS else CURRENT_BUILDPRINT
BLOCKTONE_VERSION = '0.1.20' if LEGACY else '0.1.23' if BASELINE or PREVIOUS else '0.1.38'
SUITE_VERSION = os.environ.get('HOTKEY_WHEEL_SUITE_VERSION', '1.5.2+26.3')
TOOLPOUCH_VERSION = os.environ.get('HOTKEY_WHEEL_TOOLPOUCH_VERSION', '1.2.0+26.3')
SUITE_TOOLPOUCH_VERSION = None
BRBR_VERSION = os.environ.get('HOTKEY_WHEEL_BRBR_VERSION', '1.11.8+26.3')
POLYMER_VERSION = os.environ.get('HOTKEY_WHEEL_POLYMER_VERSION', '0.18.7+26.3')
WORKSPACE = ROOT.parents[2]
if sum([BASELINE, PREVIOUS, LEGACY, SUITE, TOOLPOUCH]) > 1:
    raise SystemExit('Baseline and legacy modes are separate integrated profiles.')
RUN = Path(os.environ.get('HOTKEY_WHEEL_QA_RUN_ROOT', str(ROOT / ('legacy' if LEGACY else 'api-baseline' if BASELINE else 'api-previous' if PREVIOUS else 'suite-polymer' if POLYMER else 'suite' if SUITE else 'toolpouch' if TOOLPOUCH else 'integrated' if INTEGRATED else 'minimal'))))
RUN.mkdir(parents=True, exist_ok=True)
GAME = RUN / 'game'
MODS = GAME / 'mods'
# Mod settings saved by an earlier run, such as Buildprint's remembered camera mode, must not leak into this one.
if (GAME / 'config').exists():
    shutil.rmtree(GAME / 'config')
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
if TOOLPOUCH:
    patterns += ['fzzy_config-0.7.7+fix3+26.3.jar', 'fabric-language-kotlin-*.jar']
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
            # The suite bundles its own Tool Pouch; the Tool Pouch fixture checks that exact version.
            with zipfile.ZipFile(source) as archive:
                nested = [name for name in archive.namelist() if name.startswith('META-INF/jars/toolpouch-fabric-')]
                if len(nested) != 1:
                    raise SystemExit(f'Expected one nested Tool Pouch in {source}; found {len(nested)}')
                with zipfile.ZipFile(io.BytesIO(archive.read(nested[0]))) as toolpouch:
                    SUITE_TOOLPOUCH_VERSION = json.loads(toolpouch.read('fabric.mod.json'))['version']
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
if TOOLPOUCH:
    source = Path(os.environ.get('HOTKEY_WHEEL_TOOLPOUCH_JAR', str(WORKSPACE / 'Builds/Minecraft/Tool Pouch/Main Plugin' / TOOLPOUCH_VERSION / f'toolpouch-fabric-{TOOLPOUCH_VERSION}.jar')))
    if not source.is_file():
        raise SystemExit(f'Missing pinned Tool Pouch artifact: {source}')
    # Tool Pouch publishes Fabric and NeoForge files under the same version number.
    with zipfile.ZipFile(source) as archive:
        if 'fabric.mod.json' not in archive.namelist():
            raise SystemExit(f'Expected the Fabric Tool Pouch JAR: {source}')
        metadata = json.loads(archive.read('fabric.mod.json'))
    if metadata['id'] != 'toolpouch':
        raise SystemExit(f'Unexpected Tool Pouch mod ID: {metadata["id"]}')
    TOOLPOUCH_VERSION = metadata['version']
    shutil.copy2(source, MODS / source.name)
if INTEGRATED:
    current = BUILDPRINT_VERSION == CURRENT_BUILDPRINT
    buildprint = WORKSPACE / 'Builds/Minecraft/Buildprint/Main Plugin' / BUILDPRINT_VERSION / f'buildprint-fabric-{BUILDPRINT_VERSION}.jar'
    blocktone = WORKSPACE / 'Builds/Minecraft/Buildprint/Feature Addon - Blocktone Toning' / BLOCKTONE_VERSION / f'buildprint-toning-{BLOCKTONE_VERSION}.jar'
    if current:
        buildprint = Path(os.environ.get('HOTKEY_WHEEL_BUILDPRINT_JAR', str(buildprint)))
        blocktone = Path(os.environ.get('HOTKEY_WHEEL_BLOCKTONE_JAR', str(blocktone)))
    for source in [buildprint, blocktone] if BLOCKTONE else [buildprint]:
        if not source.is_file():
            raise SystemExit(f'Missing pinned integration artifact: {source}')
        with zipfile.ZipFile(source) as archive:
            metadata = json.loads(archive.read('fabric.mod.json'))
        if source == blocktone:
            if metadata['id'] != 'buildprint_toning':
                raise SystemExit(f'Unexpected Blocktone mod ID: {metadata["id"]}')
            BLOCKTONE_VERSION = metadata['version']
        else:
            if metadata['id'] != 'buildprint':
                raise SystemExit(f'Unexpected Buildprint mod ID: {metadata["id"]}')
            BUILDPRINT_VERSION = metadata['version']
        shutil.copy2(source, MODS / source.name)
version = next(line.split('=', 1)[1].strip() for line in (ROOT.parent / 'gradle.properties').read_text().splitlines()
               if line.startswith('mod_version='))
addon = Path(os.environ.get('HOTKEY_WHEEL_JAR', str(ROOT.parent / 'build/libs' / f'hotkey-wheel-{version}.jar')))
with zipfile.ZipFile(addon) as archive:
    wheel_metadata = json.loads(archive.read('fabric.mod.json'))
if wheel_metadata['id'] != 'hotkey_wheel':
    raise SystemExit('Focused wheel override must be a Hotkey Wheel JAR')
shutil.copy2(addon, MODS / addon.name)
# The client gametest module is published separately; use the build that matches the profile's Fabric API.
fabric_api = next(MODS.glob('fabric-api-*.jar')).name.removeprefix('fabric-api-').removesuffix('.jar')
fabric_pom = next((CACHE / 'net.fabricmc.fabric-api/fabric-api' / fabric_api).glob('*/*.pom'), None)
if fabric_pom is None:
    raise SystemExit(f'Resolve Fabric API {fabric_api} with Gradle before running QA')
pom = fabric_pom.read_text()
gametest_version = pom.split('<artifactId>fabric-client-gametest-api-v1</artifactId>', 1)[1].split('<version>', 1)[1].split('</version>', 1)[0]
gametest = next((CACHE / 'net.fabricmc.fabric-api/fabric-client-gametest-api-v1' / gametest_version).glob('*/*.jar'))
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
    if BUILDPRINT_VERSION == CURRENT_BUILDPRINT:
        test_names.append('SideMouseEditorTest')
        # Joins a dedicated server started in the same JVM, so it runs once, in the integrated profile.
        if not SUITE and not TOOLPOUCH:
            test_names.append('ServerCameraChoiceTest')
if SUITE:
    test_names += ['ToolPouchClientTest', 'BrbrIntegrationTest']
if TOOLPOUCH:
    test_names.append('ToolPouchClientTest')
# Slot toggles arrived in Tool Pouch 1.2.0; the fixture compiles against its classes.
if TOOLPOUCH or SUITE and not SUITE_TOOLPOUCH_VERSION.startswith('1.1.'):
    test_names.append('ToolPouchToggleTest')
if os.environ.get('HOTKEY_WHEEL_QA_TESTS'):
    test_names = os.environ['HOTKEY_WHEEL_QA_TESTS'].split(',')
    if any(not name.isidentifier() or not (ROOT / (name + '.java')).is_file() for name in test_names):
        raise SystemExit('Unknown focused QA fixture')
executed = RUN / 'executed-source'
executed.mkdir(exist_ok=True)
source_audit = []
for source in [Path(__file__), *(ROOT / (name + '.java') for name in test_names)]:
    destination = executed / source.name
    shutil.copy2(source, destination)
    source_audit.append({'file': source.name, 'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()})
(RUN / 'executed-source.json').write_text(json.dumps(source_audit, indent=2))
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
if 'ServerCameraChoiceTest' in test_names:
    # The in-process dedicated server needs an accepted EULA, as in the workspace's other local test servers.
    (GAME / 'eula.txt').write_text('eula=true\n')
    if (GAME / 'world').exists():
        shutil.rmtree(GAME / 'world')
(RUN / 'results.json').unlink(missing_ok=True)
(RUN / 'callback-order.json').unlink(missing_ok=True)
if (GAME / '.mixin.out').exists():
    shutil.rmtree(GAME / '.mixin.out')
for name in ['key-management-results.json', 'buildprint-menu-results.json', 'toolpouch-results.json', 'brbr-integration-results.json',
             'side-mouse-editor-results.json', 'server-camera-results.json', 'toolpouch-toggle-results.json']:
    (RUN / name).unlink(missing_ok=True)
command = [str(compiler.parent / 'java'), '-Xmx3G' if 'ServerCameraChoiceTest' in test_names else '-Xmx2G', '-XX:ActiveProcessorCount=4',
           '-Djava.library.path=' + str(META / 'natives/26.3-0.19.5/lwjgl/3.4.3-snapshot/x64'),
           '-Dfabric.client.gametest', '-Dfabric.client.gametest.timeout=2400',
           '-Dwheel.qa.root=' + str(RUN), '-Dwheel.qa.suite.version=' + SUITE_VERSION,
           '-Dwheel.qa.brbr.version=' + BRBR_VERSION, '-Dwheel.qa.blocktone=' + str(BLOCKTONE).lower(),
           '-Dwheel.qa.toolpouch.version=' + (TOOLPOUCH_VERSION.split('+')[0] if TOOLPOUCH else SUITE_TOOLPOUCH_VERSION or ''),
           '-Dfabric.log.disableAnsi=true',
           '-cp', os.pathsep.join(map(str, classpath)), 'net.fabricmc.loader.impl.launch.knot.KnotClient',
           '--gameDir', str(GAME), '--version', '26.3', '--username', 'WheelQA', '--accessToken', '0',
           '--assetsDir', str(META / 'assets'), '--assetIndex', '34', '--width', '1280', '--height', '800']
if os.environ.get('HOTKEY_WHEEL_QA_EXPORT') == 'true':
    command.insert(1, '-Dmixin.debug.export=true')
(RUN / 'provenance.json').write_text(json.dumps({
    'minecraft': '26.3', 'loader': '0.19.5', 'integrated': INTEGRATED, 'suite': SUITE, 'polymer': POLYMER,
    'toolpouch_standalone': TOOLPOUCH_VERSION if TOOLPOUCH else None, 'suite_toolpouch': SUITE_TOOLPOUCH_VERSION,
    'buildprint': BUILDPRINT_VERSION if INTEGRATED else None, 'blocktone': BLOCKTONE_VERSION if BLOCKTONE else None,
    'suite_version': SUITE_VERSION if SUITE else None, 'brbr': BRBR_VERSION if SUITE else None,
    'polymer_version': POLYMER_VERSION if POLYMER else None,
    'wheel_version': wheel_metadata['version'], 'wheel_source': str(addon.resolve()),
    'tests': test_names, 'command': command,
    'mods': {source.name: hashlib.sha256(source.read_bytes()).hexdigest() for source in sorted(MODS.glob('*.jar'))}}, indent=2))
if '--prepare-only' in sys.argv:
    print('Prepared isolated QA profile:', RUN)
    raise SystemExit(0)
environment = os.environ.copy()
environment['SDL_VIDEODRIVER'] = 'x11'
# SDL's GLX path cannot load Xvfb's Mesa GL; EGL works on Xvfb and on hardware displays.
environment.setdefault('SDL_VIDEO_FORCE_EGL', '1')
with (RUN / 'console.log').open('w') as log:
    process = subprocess.Popen(command, cwd=GAME, env=environment, stdout=log, stderr=subprocess.STDOUT)
    try:
        code = process.wait(timeout=600)
    except subprocess.TimeoutExpired:
        process.terminate()
        process.wait(timeout=20)
        raise
print('EXIT', code)


def handler_order(class_file, method):
    # Mixin inlines each @Inject handler as a call at the top of the target method; list them in call order.
    listing = subprocess.run([str(compiler.parent / 'javap'), '-c', '-p', str(class_file)],
                             check=True, capture_output=True, text=True).stdout
    callbacks = []
    inside = False
    for line in listing.splitlines():
        if line.strip().startswith(('public ', 'private ', 'protected ')) or line.strip().startswith(('void ', 'boolean ')):
            inside = f' {method}(' in line
            continue
        if inside and 'invoke' in line and 'handler$' in line:
            offset = int(line.split(':', 1)[0].strip())
            callbacks.append({'offset': offset, 'method': line.split('Method ', 1)[1].split(':', 1)[0].split('.')[-1]})
    return callbacks


exported = GAME / '.mixin.out/class/net/minecraft/client'
if os.environ.get('HOTKEY_WHEEL_QA_EXPORT') == 'true':
    if not (exported / 'MouseHandler.class').is_file() or not (exported / 'KeyboardHandler.class').is_file():
        raise SystemExit('Mixin export did not write the transformed input handlers')
    (RUN / 'callback-order.json').write_text(json.dumps({
        'transformed_mouse_handler_sha256': hashlib.sha256((exported / 'MouseHandler.class').read_bytes()).hexdigest(),
        'transformed_keyboard_handler_sha256': hashlib.sha256((exported / 'KeyboardHandler.class').read_bytes()).hexdigest(),
        'onButton_callbacks': handler_order(exported / 'MouseHandler.class', 'onButton'),
        'keyPress_callbacks': handler_order(exported / 'KeyboardHandler.class', 'keyPress'),
    }, indent=2))
print((RUN / 'console.log').read_text()[-5000:])
report_names = {'WheelClientTest': 'results.json', 'KeyManagementTest': 'key-management-results.json',
                'BuildprintMenuTest': 'buildprint-menu-results.json', 'ToolPouchClientTest': 'toolpouch-results.json',
                'BrbrIntegrationTest': 'brbr-integration-results.json', 'SideMouseEditorTest': 'side-mouse-editor-results.json',
                'ServerCameraChoiceTest': 'server-camera-results.json', 'ToolPouchToggleTest': 'toolpouch-toggle-results.json'}
reports = [report_names[name] for name in test_names]
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
