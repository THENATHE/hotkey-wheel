from pathlib import Path
from datetime import date
import hashlib
import json
import os
import shutil
import struct
import zipfile
import zlib

SOURCE = Path(__file__).resolve().parents[1]
WORKSPACE = SOURCE.parents[1]
VERSION = next(line.split('=', 1)[1].strip() for line in (SOURCE / 'gradle.properties').read_text().splitlines()
               if line.startswith('mod_version='))
RELEASE = Path(os.environ.get('HOTKEY_WHEEL_RELEASE_DIR',
                             str(WORKSPACE / 'Builds/Minecraft/Hotkey Wheel/Main Plugin' / VERSION)))
ORIGINAL_RELEASE = RELEASE
REVIEW_DATE = os.environ.get('HOTKEY_WHEEL_REVIEW_DATE')
REVIEW_QUALIFIER = os.environ.get('HOTKEY_WHEEL_REVIEW_QUALIFIER')
if REVIEW_DATE:
    if date.fromisoformat(REVIEW_DATE).isoformat() != REVIEW_DATE:
        raise ValueError('HOTKEY_WHEEL_REVIEW_DATE must use YYYY-MM-DD.')
    RELEASE = RELEASE / 'reviews' / REVIEW_DATE
    if REVIEW_QUALIFIER:
        if not REVIEW_QUALIFIER[0].isalnum() or not all(character.isalnum() or character in '.+-_' for character in REVIEW_QUALIFIER):
            raise ValueError('HOTKEY_WHEEL_REVIEW_QUALIFIER must be a plain folder label.')
        RELEASE = RELEASE / REVIEW_QUALIFIER
elif REVIEW_QUALIFIER:
    raise ValueError('HOTKEY_WHEEL_REVIEW_QUALIFIER requires HOTKEY_WHEEL_REVIEW_DATE.')
REVIEW_SUFFIX = REVIEW_DATE
if REVIEW_DATE and REVIEW_QUALIFIER:
    REVIEW_SUFFIX += f'-{REVIEW_QUALIFIER}'
JAR_NAME = f'hotkey-wheel-{VERSION}.jar'
JAR = SOURCE / 'build/libs' / JAR_NAME
BUILD_LOG = Path(os.environ.get('HOTKEY_WHEEL_BUILD_LOG', str(SOURCE / 'build.log')))
ARTIFACT_DIFF = os.environ.get('HOTKEY_WHEEL_ARTIFACT_DIFF')
SOURCE_REBUILD = os.environ.get('HOTKEY_WHEEL_SOURCE_REBUILD')
SIDE_MOUSE_REGRESSION = SOURCE / 'qa/regressions/1.0.7-side-mouse-editor-close'
UPSTREAM_REGRESSION = SOURCE / 'qa/regressions/1.0.8-upstream-2026-11'
SERVER_REGRESSION = SOURCE / 'qa/regressions/1.0.9-server-camera-question'
DOWNGRADE_REGRESSION = SOURCE / 'qa/regressions/1.1.0-downgrade-to-1.0.11'
UPGRADE_REGRESSION = SOURCE / 'qa/regressions/1.1.2-upgrade-from-1.0.11-and-1.1.1'
BUILDPRINT_VERSION = os.environ.get('HOTKEY_WHEEL_BUILDPRINT_VERSION', '0.1.39+26.3')
# Every profile also has a restart run: a second game that loads the settings the first one saved.
NEW_REPORTS = ['icon-results.json', 'control-folder-results.json', 'editor-layout-results.json', 'layout-scale-results.json',
               'upgrade-results.json']
SUITE_REPORTS = ['results.json', 'key-management-results.json', 'buildprint-menu-results.json', 'side-mouse-editor-results.json',
                 'toolpouch-results.json', 'brbr-integration-results.json', 'toolpouch-toggle-results.json', 'move-slot-results.json',
                 'mod-settings-results.json', *NEW_REPORTS, 'folder-results.json']
REPORTS = {
    'minimal': ['results.json', 'key-management-results.json', 'move-slot-results.json', 'mod-settings-results.json',
                *NEW_REPORTS, 'folder-results.json'],
    'integrated': ['results.json', 'key-management-results.json', 'buildprint-menu-results.json', 'side-mouse-editor-results.json',
                   'server-camera-results.json', 'move-slot-results.json', 'mod-settings-results.json', *NEW_REPORTS, 'folder-results.json'],
    'toolpouch': ['results.json', 'key-management-results.json', 'buildprint-menu-results.json', 'side-mouse-editor-results.json',
                  'toolpouch-results.json', 'toolpouch-toggle-results.json', 'move-slot-results.json', 'mod-settings-results.json',
                  *NEW_REPORTS, 'folder-results.json'],
    'suite': SUITE_REPORTS,
    'suite-polymer': SUITE_REPORTS,
}
for profile in list(REPORTS):
    REPORTS[profile + '-restart'] = ['restart-results.json']


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_png(data):
    require(data.startswith(b'\x89PNG\r\n\x1a\n'), 'Mod icon must be a PNG')
    offset = 8
    chunks = []
    dimensions = None
    while offset < len(data):
        require(offset + 12 <= len(data), 'Truncated PNG icon chunk')
        length = struct.unpack('>I', data[offset:offset + 4])[0]
        end = offset + 12 + length
        require(end <= len(data), 'Truncated PNG icon data')
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        checksum = struct.unpack('>I', data[end - 4:end])[0]
        require(zlib.crc32(kind + payload) & 0xffffffff == checksum, 'Mod icon PNG checksum mismatch')
        if kind == b'IHDR':
            require(not chunks and length == 13, 'Invalid PNG icon header')
            dimensions = struct.unpack('>II', payload[:8])
        chunks.append(kind)
        offset = end
        if kind == b'IEND':
            require(length == 0 and offset == len(data), 'Invalid PNG icon ending')
            break
    require(dimensions is not None and dimensions[0] == dimensions[1] and dimensions[0] > 0,
            'Mod icon must have positive square dimensions')
    require(chunks[-1] == b'IEND' and b'IDAT' in chunks, 'Incomplete PNG icon')
    return dimensions


release_hash = sha256(JAR)
existing_jar = RELEASE / JAR_NAME
require(not existing_jar.exists() or sha256(existing_jar) == release_hash,
        f'{RELEASE} already holds a different {JAR_NAME}; release folders are never overwritten')
# Historical evidence keeps its recorded outcome; the side-mouse fixture itself runs in every Buildprint profile below.
require(SIDE_MOUSE_REGRESSION.is_dir(), 'Missing side-mouse baseline and fixed-candidate regression evidence')
for attempt, passed in [('shipping-1.0.7-baseline', False), ('priority-only-1.0.8-failed-attempt', False),
                        ('final-order900-1.0.8', True)]:
    result = json.loads((SIDE_MOUSE_REGRESSION / attempt / 'side-mouse-editor-results.json').read_text())
    require(result.get('passed') is passed, f'{attempt}: unexpected regression result')
# The shipped 1.0.8 JAR must fail exactly the two problems this release fixes.
SHIPPED_1_0_8 = 'ed47efefe6f06e7940a5250974b022228a2b61b441ca704571f9a44d3134d4ca'
for attempt, report_name, failure in [
        ('shipping-1.0.8-reserved-keys', 'buildprint-menu-results.json', 'Original opener C closes the wheel-owned editor'),
        ('shipping-1.0.8-config', 'key-management-results.json', 'An unreadable configuration file is copied aside')]:
    result = json.loads((UPSTREAM_REGRESSION / attempt / report_name).read_text())
    provenance = json.loads((UPSTREAM_REGRESSION / attempt / 'provenance.json').read_text())
    require(result.get('passed') is False and failure in result.get('failure', ''),
            f'{attempt}: baseline does not show the expected failure')
    require(provenance['mods'].get('hotkey-wheel-1.0.8+26.3.jar') == SHIPPED_1_0_8, f'{attempt}: baseline did not use shipped 1.0.8')
# Unpublished 1.0.9 must fail the dedicated-server camera question that 1.0.10 fixes.
UNPUBLISHED_1_0_9 = 'b3f6fcf41e0ee23728515434caada70ec2eea92f0ac448b0e06e24e6c8a12c0f'
result = json.loads((SERVER_REGRESSION / 'shipping-1.0.9/server-camera-results.json').read_text())
provenance = json.loads((SERVER_REGRESSION / 'shipping-1.0.9/provenance.json').read_text())
require(result.get('passed') is False and 'closes the editor started from the camera question' in result.get('failure', '')
        and provenance['mods'].get('hotkey-wheel-1.0.9+26.3.jar') == UNPUBLISHED_1_0_9,
        'Unpublished 1.0.9 baseline does not show the server camera-question failure')
# The published 1.0.11 JAR must load 1.1.0 folder settings without losing the file or any key.
RELEASED_1_0_11 = '02ea7a0b77bbf723a348baa42ebbdce2e4f4e394da606cb65cd72a2d0388d28b'
result = json.loads((DOWNGRADE_REGRESSION / 'release-1.0.11/downgrade-results.json').read_text())
provenance = json.loads((DOWNGRADE_REGRESSION / 'release-1.0.11/provenance.json').read_text())
require(result.get('passed') is True and provenance['mods'].get('hotkey-wheel-1.0.11+26.3.jar') == RELEASED_1_0_11,
        'Downgrade evidence does not show the published 1.0.11 JAR loading 1.1.0 settings')
# Settings files written by the published 1.0.11 and 1.1.1 JARs themselves, which UpgradeTest loads.
RELEASED_1_1_1 = 'dd45d3f106071f61e48f0dd723bf9cd59caa2275514d5c9fca16dc63a4d686f2'
for release, checksum in [('1.0.11', RELEASED_1_0_11), ('1.1.1', RELEASED_1_1_1)]:
    provenance = json.loads((UPGRADE_REGRESSION / f'release-{release}/provenance.json').read_text())
    require(provenance['mods'].get(f'hotkey-wheel-{release}+26.3.jar') == checksum
            and (UPGRADE_REGRESSION / f'release-{release}/upgrade/settings-{release}+26.3.json').is_file(),
            f'Upgrade evidence does not show the published {release} JAR writing its own settings')
if SOURCE_REBUILD:
    rebuild = json.loads(Path(SOURCE_REBUILD).read_text())
    require(rebuild.get('passed') is True and rebuild.get('compile_from_exported_source') is True,
            'Source rebuild proof must show a successful exported-source build')
    for name in [JAR_NAME, f'hotkey-wheel-{VERSION}-sources.jar']:
        proof = rebuild.get('artifacts', {}).get(name, {})
        require(proof.get('byte_identical') is True and proof.get('sha256') == sha256(SOURCE / 'build/libs' / name),
                f'Source rebuild proof does not match final {name}')
if ARTIFACT_DIFF:
    difference = json.loads(Path(ARTIFACT_DIFF).read_text())
    require(difference.get('candidate_sha256') == release_hash and difference.get('archive_integrity') == 'passed',
            'Artifact comparison does not match the final candidate')
if REVIEW_DATE:
    original_jar = ORIGINAL_RELEASE / JAR_NAME
    require(original_jar.is_file() and sha256(original_jar) == release_hash,
            'A dated review must preserve the original released production binary')
with zipfile.ZipFile(JAR) as archive:
    require(archive.testzip() is None, 'Installable JAR integrity failed')
    metadata = json.loads(archive.read('fabric.mod.json'))
    require(metadata['name'] == 'Hotkey Wheel' and metadata['id'] == 'hotkey_wheel'
            and metadata['version'] == VERSION and metadata['environment'] == 'client', 'JAR metadata mismatch')
    test_classes = {path.stem for path in (SOURCE / 'qa').glob('*Test.java')}
    require(not any('qa/' in name or any(test in name for test in test_classes) for name in archive.namelist()),
            'Development QA classes were packaged in the installable JAR')
    icon = metadata.get('icon')
    require(isinstance(icon, str) and not Path(icon).is_absolute() and '..' not in Path(icon).parts,
            'fabric.mod.json must reference a packaged mod icon')
    icon_data = archive.read(icon)
    icon_dimensions = validate_png(icon_data)
    require(icon_data == (SOURCE / 'src/main/resources' / icon).read_bytes(), 'Packaged icon differs from source')

results = {}
profile_totals = {}
shared_inputs = {}
integration_versions = {}
for mode, names in REPORTS.items():
    root = SOURCE / 'qa' / mode
    profile = mode.removesuffix('-restart')
    provenance = json.loads((root / 'provenance.json').read_text())
    require(provenance['minecraft'] == '26.3' and provenance['loader'] == '0.19.5', f'{mode}: unexpected game target')
    require(provenance.get('integrated') is (profile != 'minimal') and provenance.get('suite') is profile.startswith('suite')
            and provenance.get('polymer') is (profile == 'suite-polymer')
            and (provenance.get('toolpouch_standalone') is not None) is (profile == 'toolpouch'),
            f'{mode}: profile provenance mismatch')
    if profile != 'minimal':
        blocktone_version = provenance.get('blocktone')
        # A --no-blocktone run records no Blocktone version and cannot be packaged.
        require(provenance.get('buildprint') == BUILDPRINT_VERSION and isinstance(blocktone_version, str) and blocktone_version,
                f'{mode}: unexpected Buildprint/Blocktone versions')
        for filename in [f'buildprint-fabric-{BUILDPRINT_VERSION}.jar', f'buildprint-toning-{blocktone_version}.jar']:
            require(provenance['mods'].get(filename) == sha256(root / 'game/mods' / filename),
                    f'{mode}: retained integration artifact differs from tested dependency')
    require(provenance['mods'].get(JAR_NAME) == release_hash, f'{mode}: tests did not use the final release JAR')
    require(sha256(root / 'game/mods' / JAR_NAME) == release_hash, f'{mode}: retained test JAR differs from release')
    addon_names = {name for name in provenance['mods'] if name.startswith('hotkey-wheel-') and name != 'hotkey-wheel-qa.jar'}
    require(addon_names == {JAR_NAME}, f'{mode}: conflicting Hotkey Wheel artifacts in test profile')
    executed_source = json.loads((root / 'executed-source.json').read_text())
    require(isinstance(executed_source, list) and executed_source, f'{mode}: missing executed-source manifest')
    for entry in executed_source:
        filename = entry['file']
        require(Path(filename).name == filename, f'{mode}: unexpected executed-source path')
        require(sha256(root / 'executed-source' / filename) == entry['sha256']
                and sha256(SOURCE / 'qa' / filename) == entry['sha256'],
                f'{mode}: packaged QA source differs from the executed fixture: {filename}')
    order = json.loads((root / 'callback-order.json').read_text())
    callbacks = order['onButton_callbacks']
    require(callbacks and 'hotkey_wheel' in callbacks[0]['method'],
            f'{mode}: transformed mouse callback does not run before other input handlers')
    keyboard = order['keyPress_callbacks']
    require(keyboard and keyboard[0]['method'].endswith('hotkeyWheel$menuReturn'),
            f'{mode}: owned-menu keyboard return does not run before other key handlers')
    if profile != 'minimal':
        methods = [callback['method'] for callback in keyboard]
        editor_keys = [index for index, method in enumerate(methods) if method.endswith('buildprint$toolKeys')]
        wheel_input = [index for index, method in enumerate(methods) if method.endswith('hotkeyWheel$input')]
        require(editor_keys and wheel_input and editor_keys[0] < wheel_input[0],
                f'{mode}: Buildprint editor keys no longer keep precedence over opening wheels')
    for filename, checksum in provenance['mods'].items():
        if filename.startswith(('buildprint-fabric-', 'buildprint-toning-', 'toolpouch-fabric-', 'vanilla-plusplus-quality-of-life-suite-', 'better-recipe-book-rebetterered-', 'polymer-bundled-')):
            require(shared_inputs.setdefault(filename, checksum) == checksum,
                    f'{mode}: integration input changed between profiles: {filename}')
            dependency = root / 'game/mods' / filename
            require(sha256(dependency) == checksum, f'{mode}: retained integration JAR differs from tested input: {filename}')
            with zipfile.ZipFile(dependency) as archive:
                dependency_metadata = json.loads(archive.read('fabric.mod.json'))
            identity = dependency_metadata['id']
            version = dependency_metadata['version']
            require(integration_versions.setdefault(identity, (version, checksum)) == (version, checksum),
                    f'{mode}: integration version or binary changed between profiles: {identity}')
    counts = {}
    for name in names:
        path = root / name
        result = json.loads(path.read_text())
        require(result.get('passed') is True, f'{mode}/{name}: missing passing result')
        checks = result.get('checks')
        require(isinstance(checks, list) and checks and all(isinstance(check, str) for check in checks),
                f'{mode}/{name}: missing assertion evidence')
        require(path.stat().st_mtime_ns >= (root / 'provenance.json').stat().st_mtime_ns,
                f'{mode}/{name}: result predates this profile run')
        if name == 'buildprint-menu-results.json':
            require(result.get('public_editor_api') is True and result.get('public_editor_close_api') is True
                    and result.get('blocktone') is True,
                    f'{mode}: public editor integration or Blocktone scenarios were not exercised')
        if name == 'toolpouch-results.json':
            if profile == 'toolpouch':
                require(result.get('toolpouch') == provenance.get('toolpouch_standalone') and 'suite' not in result,
                        f'{mode}: unexpected standalone Tool Pouch version')
            else:
                require(result.get('suite') == provenance.get('suite_version')
                        and result.get('toolpouch') == provenance.get('suite_toolpouch'),
                        f'{mode}: unexpected native suite/Tool Pouch version')
            require(result.get('polymer_client') is (mode == 'suite-polymer'), f'{mode}: Polymer runtime mismatch')
            if mode == 'suite-polymer':
                require(result.get('polymer') == provenance.get('polymer_version'), f'{mode}: unexpected Polymer version')
        if name == 'brbr-integration-results.json':
            require(result.get('brbr') == provenance.get('brbr'), f'{mode}: unexpected BRBR version')
        if name == 'toolpouch-toggle-results.json':
            require(result.get('suite') is profile.startswith('suite'), f'{mode}: slot toggle run has the wrong suite state')
            # The release must show the old suite elytra slots working after the suite removed its own mapping.
            require(result.get('suite_elytra_mapping') is False, f'{mode}: the suite elytra mapping was still registered')
        if name == 'folder-results.json':
            # Controlling replaces the vanilla key list, so the minimal profile checks vanilla and the others Controlling.
            require(result.get('buildprint') is (profile != 'minimal') and result.get('controlling') is (profile != 'minimal'),
                    f'{mode}: folder fixture ran with the wrong integrations')
            marker = 'vanilla_marker_pixels' if profile == 'minimal' else 'controlling_marker_pixels'
            require(isinstance(result.get(marker), int), f'{mode}: the Controls marker was not checked on its key list')
        if name == 'server-camera-results.json':
            require(len(result.get('scenarios', [])) == 6, f'{mode}: dedicated-server camera scenarios are incomplete')
            require(result.get('server'), f'{mode}: camera-question test did not record its dedicated server')
        if name == 'side-mouse-editor-results.json':
            require(result.get('opener') == 'key.mouse.4', f'{mode}: side-mouse regression did not use the reported opener')
            require(isinstance(result.get('input_path'), str) and 'native mouse callbacks' in result['input_path'],
                    f'{mode}: side-mouse regression did not exercise native input callbacks')
            require(isinstance(result.get('scenarios'), list) and result['scenarios'],
                    f'{mode}: missing side-mouse scenario evidence')
        if name in ('toolpouch-results.json', 'brbr-integration-results.json'):
            if name == 'brbr-integration-results.json':
                prefix, runtime_version = 'better-recipe-book-rebetterered-', result.get('brbr')
            elif profile == 'toolpouch':
                prefix, runtime_version = 'toolpouch-fabric-', result.get('toolpouch')
            else:
                prefix, runtime_version = 'vanilla-plusplus-quality-of-life-suite-', result.get('suite')
            inputs = [filename for filename in provenance['mods'] if filename.startswith(prefix)]
            require(len(inputs) == 1, f'{mode}: missing or duplicate input for {name}')
            dependency = root / 'game/mods' / inputs[0]
            require(provenance['mods'][inputs[0]] == sha256(dependency), f'{mode}: retained candidate differs from tested dependency')
            with zipfile.ZipFile(dependency) as archive:
                tested_metadata = json.loads(archive.read('fabric.mod.json'))
            require(tested_metadata['version'] == runtime_version, f'{mode}: candidate metadata differs from runtime version')
        counts[name] = len(checks)
    counts['total'] = sum(counts.values())
    results[mode] = counts
    profile_totals[mode] = counts['total']
total_assertions = sum(profile_totals.values())

RELEASE.mkdir(parents=True, exist_ok=True)
for name in [JAR_NAME, f'hotkey-wheel-{VERSION}-sources.jar']:
    shutil.copy2(SOURCE / 'build/libs' / name, RELEASE / name)
for name in ['README.md', 'QA.md', 'API-MIGRATION.md', 'AUDIT-2026-11-08.md', 'CHANGELOG.md', 'ICON.md', 'LICENSE']:
    shutil.copy2(SOURCE / name, RELEASE / name)
shutil.copy2(SOURCE / 'qa/buildprint-api-audit.json', RELEASE / 'buildprint-api-audit.json')
shutil.copy2(SOURCE / 'qa/upstream-inputs.json', RELEASE / 'upstream-inputs.json')
shutil.copy2(BUILD_LOG, RELEASE / 'build.log')
if ARTIFACT_DIFF:
    shutil.copy2(Path(ARTIFACT_DIFF), RELEASE / 'ARTIFACT-DIFF.json')
if SOURCE_REBUILD:
    shutil.copy2(Path(SOURCE_REBUILD), RELEASE / 'SOURCE-REBUILD.json')
    rebuild_log = Path(rebuild['log'])
    shutil.copy2(rebuild_log, RELEASE / 'SOURCE-REBUILD.log')
if (SOURCE / 'qa/regressions').is_dir():
    shutil.copytree(SOURCE / 'qa/regressions', RELEASE / 'qa/regressions', dirs_exist_ok=True)
for mode, names in REPORTS.items():
    root = SOURCE / 'qa' / mode
    destination = RELEASE / 'qa' / mode
    destination.mkdir(parents=True, exist_ok=True)
    for name in [*names, 'provenance.json', 'executed-source.json', 'callback-order.json', 'console.log']:
        shutil.copy2(root / name, destination / name)
    shutil.copytree(root / 'executed-source', destination / 'executed-source', dirs_exist_ok=True)
    screenshots = destination / 'screenshots'
    if screenshots.exists():
        shutil.rmtree(screenshots)
    screenshots.mkdir()
    for screenshot in (root / 'game/screenshots').glob('*.png'):
        if screenshot.stat().st_mtime_ns >= (root / 'provenance.json').stat().st_mtime_ns:
            shutil.copy2(screenshot, screenshots / screenshot.name)

source_files = [SOURCE / name for name in [
    '.gitignore', 'LICENSE', 'README.md', 'QA.md', 'API-MIGRATION.md', 'AUDIT-2026-11-08.md', 'CHANGELOG.md', 'ICON.md', 'build.gradle',
    'gradle.properties', 'settings.gradle', 'gradlew', 'qa/run.py', 'qa/package_release.py', 'qa/buildprint-api-audit.json',
    'qa/upstream-inputs.json', 'art/draw_glyphs.py']]
source_files += list((SOURCE / 'qa').glob('*Test.java'))
# Console logs stay local; they are ignored by Git and not part of the public archives.
regression_files = sorted(path for path in [*SIDE_MOUSE_REGRESSION.rglob('*'), *UPSTREAM_REGRESSION.rglob('*'),
                                            *SERVER_REGRESSION.rglob('*'), *DOWNGRADE_REGRESSION.rglob('*'),
                                            *UPGRADE_REGRESSION.rglob('*')]
                          if path.is_file() and path.suffix != '.log')
require(not any(path.suffix in ('.class', '.jar') for path in regression_files),
        'Regression source/evidence must not redistribute exported classes or mod JARs')
source_files += regression_files
source_files += [path for directory in ['src', 'gradle']
                 for path in (SOURCE / directory).rglob('*') if path.is_file()]
source_zip = RELEASE / (f'hotkey-wheel-{VERSION}-reviewed-source-{REVIEW_SUFFIX}.zip' if REVIEW_DATE
                        else f'hotkey-wheel-{VERSION}-source.zip')
with zipfile.ZipFile(source_zip, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(source_files):
        archive.write(path, Path('hotkey-wheel') / path.relative_to(SOURCE))

verification_zip = RELEASE / (f'hotkey-wheel-{VERSION}-combined-QA-{REVIEW_SUFFIX}.zip' if REVIEW_DATE
                              else f'hotkey-wheel-{VERSION}-verification.zip')
with zipfile.ZipFile(verification_zip, 'w', zipfile.ZIP_DEFLATED) as archive:
    archive.write(SOURCE / 'qa/buildprint-api-audit.json', 'hotkey-wheel-qa/buildprint-api-audit.json')
    for name in ['build.log', 'ARTIFACT-DIFF.json', 'SOURCE-REBUILD.json', 'SOURCE-REBUILD.log']:
        evidence = RELEASE / name
        if evidence.is_file():
            archive.write(evidence, Path('hotkey-wheel-qa') / name)
    for mode, names in REPORTS.items():
        evidence = RELEASE / 'qa' / mode
        for path in [*(evidence / name for name in [*names, 'provenance.json', 'executed-source.json', 'callback-order.json', 'console.log']),
                     *sorted((evidence / 'executed-source').glob('*')),
                     *sorted((evidence / 'screenshots').glob('*.png'))]:
            archive.write(path, Path('hotkey-wheel-qa') / path.relative_to(RELEASE / 'qa'))
    for name in ['QA.md', 'README.md', 'API-MIGRATION.md', 'AUDIT-2026-11-08.md', 'CHANGELOG.md', 'ICON.md', 'upstream-inputs.json']:
        archive.write(RELEASE / name, Path('hotkey-wheel-qa') / name)
    for path in regression_files:
        archive.write(path, Path('hotkey-wheel-qa/regressions') / path.relative_to(SOURCE / 'qa/regressions'))

for archive_path in [RELEASE / JAR_NAME, RELEASE / f'hotkey-wheel-{VERSION}-sources.jar', source_zip, verification_zip]:
    with zipfile.ZipFile(archive_path) as archive:
        require(archive.testzip() is None, f'{archive_path.name}: archive integrity failed')
with zipfile.ZipFile(source_zip) as archive:
    require({name.split('/')[0] for name in archive.namelist()} == {'hotkey-wheel'}, 'Source archive root mismatch')

artifacts = sorted([*RELEASE.glob('*.jar'), *RELEASE.glob('*.zip')])
(RELEASE / 'SHA256SUMS.sha256').write_text(''.join(f'{sha256(path)}  {path.name}\n' for path in artifacts))
report = {
    'component': 'Hotkey Wheel / Main Plugin', 'version': VERSION,
    'reviewDate': REVIEW_DATE, 'reviewQualifier': REVIEW_QUALIFIER,
    'productionBinaryChanged': not bool(REVIEW_DATE),
    'track': 'original release', 'portCounterpart': None,
    'minecraft': '26.3', 'loader': '0.19.5', 'compileFabricApi': '0.165.2+26.3',
    'testedFabricApi': '0.165.2+26.3', 'java': '25',
    'compileInputs': 'upstream-inputs.json',
    'integrationInputs': shared_inputs, 'legacyRetest': f'older API fallbacks retained but not retested; current audit targets exact developer Buildprint {BUILDPRINT_VERSION}',
    'integrationVersions': {mod_id: version for mod_id, (version, checksum) in integration_versions.items()},
    'source': 'Minecraft/hotkey-wheel', 'repository': 'https://github.com/THENATHE/hotkey-wheel',
    'sourceSnapshot': source_zip.name, 'publicVerification': verification_zip.name, 'sourceSnapshotSha256': sha256(source_zip), 'jarSha256': release_hash,
    'assertions': results, 'profileTotals': profile_totals, 'totalAssertions': total_assertions,
    'icon': {'path': icon, 'dimensions': icon_dimensions, 'sha256': hashlib.sha256(icon_data).hexdigest()},
    'archiveIntegrity': 'passed',
    'buildLogSha256': sha256(BUILD_LOG),
    'artifactDiff': 'ARTIFACT-DIFF.json' if ARTIFACT_DIFF else None,
    'sourceRebuild': 'SOURCE-REBUILD.json' if SOURCE_REBUILD else None,
    'sideMouseRegression': 'qa/regressions/1.0.7-side-mouse-editor-close',
    'upstreamRegression': 'qa/regressions/1.0.8-upstream-2026-11', 'audit': 'AUDIT-2026-11-08.md',
    'serverCameraRegression': 'qa/regressions/1.0.9-server-camera-question',
    'downgradeRegression': 'qa/regressions/1.1.0-downgrade-to-1.0.11',
    'upgradeRegression': 'qa/regressions/1.1.2-upgrade-from-1.0.11-and-1.1.1',
    'apiAudit': 'buildprint-api-audit.json',
    'sourceFiles': {str(path.relative_to(SOURCE)): sha256(path) for path in sorted(source_files)},
    'verification': 'QA.md', 'published': False, 'installedIntoUserProfile': False,
}
(RELEASE / 'BUILD-REPORT.json').write_text(json.dumps(report, indent=2) + '\n')

index_path = WORKSPACE / 'Builds/Minecraft/BUILD-INDEX.json'
if index_path.is_file() and RELEASE.is_relative_to(WORKSPACE) and not REVIEW_DATE and os.environ.get('HOTKEY_WHEEL_UPDATE_INDEX') == '1':
    index = json.loads(index_path.read_text())
    index = [entry for entry in index if not (entry.get('id') == 'hotkey_wheel' and entry.get('version') == VERSION)]
    index.append({
        'size': JAR.stat().st_size, 'sha256': release_hash, 'metadata': metadata,
        'loader': 'Fabric', 'id': 'hotkey_wheel', 'version': VERSION,
        'family': 'Hotkey Wheel', 'component': 'Main Plugin', 'source': 'Minecraft/hotkey-wheel',
        'new': str((RELEASE / JAR_NAME).relative_to(WORKSPACE)),
        'status': (f'Local original client release; {total_assertions} passing assertions across minimal, integrated, standalone Tool Pouch, '
                   'Vanilla++, BRBR native and Polymer profiles; selection-editor camera/input return, contextual Tool Pouch radial, '
                   'Buildprint menu return, main-menu settings, and Controlling verified.'),
        'verification': str((RELEASE / 'BUILD-REPORT.json').relative_to(WORKSPACE)),
    })
    index_path.write_text(json.dumps(index, indent=2) + '\n')
print(json.dumps({'release': str(RELEASE), 'jarSha256': release_hash, 'assertions': results,
                  'totalAssertions': total_assertions, 'archives': [path.name for path in artifacts]}, indent=2))
