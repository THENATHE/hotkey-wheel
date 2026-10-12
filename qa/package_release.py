from pathlib import Path
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
JAR_NAME = f'hotkey-wheel-{VERSION}.jar'
JAR = SOURCE / 'build/libs' / JAR_NAME
REPORTS = {
    'minimal': ['results.json', 'key-management-results.json'],
    'integrated': ['results.json', 'key-management-results.json', 'buildprint-menu-results.json'],
    'suite': ['results.json', 'key-management-results.json', 'buildprint-menu-results.json', 'toolpouch-results.json'],
    'suite-polymer': ['results.json', 'key-management-results.json', 'buildprint-menu-results.json', 'toolpouch-results.json'],
}


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
for mode, names in REPORTS.items():
    root = SOURCE / 'qa' / mode
    provenance = json.loads((root / 'provenance.json').read_text())
    require(provenance['minecraft'] == '26.3' and provenance['loader'] == '0.19.5', f'{mode}: unexpected game target')
    require(provenance.get('integrated') is (mode != 'minimal') and provenance.get('suite') is mode.startswith('suite')
            and provenance.get('polymer') is (mode == 'suite-polymer'),
            f'{mode}: profile provenance mismatch')
    require(provenance['mods'].get(JAR_NAME) == release_hash, f'{mode}: tests did not use the final release JAR')
    require(sha256(root / 'game/mods' / JAR_NAME) == release_hash, f'{mode}: retained test JAR differs from release')
    addon_names = {name for name in provenance['mods'] if name.startswith('hotkey-wheel-') and name != 'hotkey-wheel-qa.jar'}
    require(addon_names == {JAR_NAME}, f'{mode}: conflicting Hotkey Wheel artifacts in test profile')
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
        if name == 'toolpouch-results.json':
            require(result.get('suite') == '1.3.0+26.3' and result.get('toolpouch') == '1.1.10',
                    f'{mode}: unexpected native suite/Tool Pouch version')
            require(result.get('polymer_client') is (mode == 'suite-polymer'), f'{mode}: Polymer runtime mismatch')
            if mode == 'suite-polymer':
                require(result.get('polymer') == '0.18.4+26.3', f'{mode}: unexpected Polymer version')
        counts[name] = len(checks)
    counts['total'] = sum(counts.values())
    results[mode] = counts
    profile_totals[mode] = counts['total']
total_assertions = sum(profile_totals.values())

RELEASE.mkdir(parents=True, exist_ok=True)
for name in [JAR_NAME, f'hotkey-wheel-{VERSION}-sources.jar']:
    shutil.copy2(SOURCE / 'build/libs' / name, RELEASE / name)
for name in ['README.md', 'QA.md', 'ICON.md', 'LICENSE']:
    shutil.copy2(SOURCE / name, RELEASE / name)
shutil.copy2(SOURCE / 'build.log', RELEASE / 'build.log')
if (SOURCE / 'qa/regressions').is_dir():
    shutil.copytree(SOURCE / 'qa/regressions', RELEASE / 'qa/regressions', dirs_exist_ok=True)
for mode, names in REPORTS.items():
    root = SOURCE / 'qa' / mode
    destination = RELEASE / 'qa' / mode
    destination.mkdir(parents=True, exist_ok=True)
    for name in [*names, 'provenance.json', 'console.log']:
        shutil.copy2(root / name, destination / name)
    screenshots = destination / 'screenshots'
    if screenshots.exists():
        shutil.rmtree(screenshots)
    screenshots.mkdir()
    for screenshot in (root / 'game/screenshots').glob('000*.png'):
        if screenshot.stat().st_mtime_ns >= (root / 'provenance.json').stat().st_mtime_ns:
            shutil.copy2(screenshot, screenshots / screenshot.name)

source_files = [SOURCE / name for name in [
    '.gitignore', 'LICENSE', 'README.md', 'QA.md', 'ICON.md', 'build.gradle', 'gradle.properties',
    'settings.gradle', 'gradlew', 'qa/run.py', 'qa/package_release.py']]
source_files += list((SOURCE / 'qa').glob('*Test.java'))
source_files += [path for directory in ['src', 'gradle']
                 for path in (SOURCE / directory).rglob('*') if path.is_file()]
source_zip = RELEASE / f'hotkey-wheel-{VERSION}-source.zip'
with zipfile.ZipFile(source_zip, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(source_files):
        archive.write(path, Path('hotkey-wheel') / path.relative_to(SOURCE))

verification_zip = RELEASE / f'hotkey-wheel-{VERSION}-verification.zip'
with zipfile.ZipFile(verification_zip, 'w', zipfile.ZIP_DEFLATED) as archive:
    for mode, names in REPORTS.items():
        evidence = RELEASE / 'qa' / mode
        for path in [*(evidence / name for name in [*names, 'provenance.json']),
                     *sorted((evidence / 'screenshots').glob('*.png'))]:
            archive.write(path, Path('hotkey-wheel-qa') / path.relative_to(RELEASE / 'qa'))
    for name in ['QA.md', 'README.md', 'ICON.md']:
        archive.write(RELEASE / name, Path('hotkey-wheel-qa') / name)

for archive_path in [RELEASE / JAR_NAME, RELEASE / f'hotkey-wheel-{VERSION}-sources.jar', source_zip, verification_zip]:
    with zipfile.ZipFile(archive_path) as archive:
        require(archive.testzip() is None, f'{archive_path.name}: archive integrity failed')
with zipfile.ZipFile(source_zip) as archive:
    require({name.split('/')[0] for name in archive.namelist()} == {'hotkey-wheel'}, 'Source archive root mismatch')

artifacts = sorted([*RELEASE.glob('*.jar'), *RELEASE.glob('*.zip')])
(RELEASE / 'SHA256SUMS.sha256').write_text(''.join(f'{sha256(path)}  {path.name}\n' for path in artifacts))
report = {
    'component': 'Hotkey Wheel / Main Plugin', 'version': VERSION,
    'track': 'original release', 'portCounterpart': None,
    'minecraft': '26.3', 'loader': '0.19.5', 'compileFabricApi': '0.161.0+26.3',
    'testedFabricApi': '0.162.0+26.3', 'java': '25',
    'source': 'Minecraft/hotkey-wheel', 'repository': 'https://github.com/THENATHE/hotkey-wheel',
    'sourceSnapshot': source_zip.name, 'publicVerification': verification_zip.name, 'sourceSnapshotSha256': sha256(source_zip), 'jarSha256': release_hash,
    'assertions': results, 'profileTotals': profile_totals, 'totalAssertions': total_assertions,
    'icon': {'path': icon, 'dimensions': icon_dimensions, 'sha256': hashlib.sha256(icon_data).hexdigest()},
    'archiveIntegrity': 'passed',
    'sourceFiles': {str(path.relative_to(SOURCE)): sha256(path) for path in sorted(source_files)},
    'verification': 'QA.md', 'published': False, 'installedIntoUserProfile': False,
}
(RELEASE / 'BUILD-REPORT.json').write_text(json.dumps(report, indent=2) + '\n')

index_path = WORKSPACE / 'Builds/Minecraft/BUILD-INDEX.json'
if index_path.is_file() and RELEASE.is_relative_to(WORKSPACE):
    index = json.loads(index_path.read_text())
    index = [entry for entry in index if not (entry.get('id') == 'hotkey_wheel' and entry.get('version') == VERSION)]
    index.append({
        'size': JAR.stat().st_size, 'sha256': release_hash, 'metadata': metadata,
        'loader': 'Fabric', 'id': 'hotkey_wheel', 'version': VERSION,
        'family': 'Hotkey Wheel', 'component': 'Main Plugin', 'source': 'Minecraft/hotkey-wheel',
        'new': str((RELEASE / JAR_NAME).relative_to(WORKSPACE)),
        'status': (f'Local original client release; {total_assertions} passing assertions across minimal, integrated, '
                   'Vanilla++ native and Polymer profiles; selection-editor camera/input return, contextual Tool Pouch radial, '
                   'Buildprint menu return, main-menu settings, and Controlling verified.'),
        'verification': str((RELEASE / 'BUILD-REPORT.json').relative_to(WORKSPACE)),
    })
    index_path.write_text(json.dumps(index, indent=2) + '\n')
print(json.dumps({'release': str(RELEASE), 'jarSha256': release_hash, 'assertions': results,
                  'totalAssertions': total_assertions, 'archives': [path.name for path in artifacts]}, indent=2))
