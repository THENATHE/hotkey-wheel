"""A local Fabric 26.3 dedicated server for the fixtures that need a real, separate server.

Usage: test_server.py <run folder> <port> [mod.jar ...]

The server gets Fabric API 0.165.2 from the QA profile plus the mods named, a fresh flat world, and makes the
fixtures' offline player WheelQA an operator, so a fixture can set itself up with commands. It runs in the
foreground until it stops; write a console command to <run folder>/console-command.txt, or send SIGTERM.
The server has no window and opens no display.
"""
from pathlib import Path
import hashlib
import json
import os
import shutil
import signal
import subprocess
import sys
import time
import uuid
import zipfile

if len(sys.argv) < 3:
    raise SystemExit(__doc__)
RUN = Path(sys.argv[1]).resolve()
PORT = int(sys.argv[2])
EXTRA_MODS = [Path(argument).resolve() for argument in sys.argv[3:]]
PROFILE = Path(os.environ.get('HOTKEY_WHEEL_PROFILE', str(Path.home() / '.local/share/ModrinthApp/profiles/Vanilla Fast')))
CACHE = Path.home() / '.gradle/caches'
MODULES = CACHE / 'modules-2/files-2.1'
JAVA = Path(os.environ.get('HOTKEY_WHEEL_JDK', '/usr/lib/jvm/java-25-openjdk')) / 'bin/java'
PLAYER = 'WheelQA'


def artifact(group, name, version):
    return next((MODULES / group / name / version).glob(f'*/{name}-{version}.jar'))


def offline_uuid(name):
    digest = bytearray(hashlib.md5(f'OfflinePlayer:{name}'.encode()).digest())
    digest[6] = (digest[6] & 0x0f) | 0x30
    digest[8] = (digest[8] & 0x3f) | 0x80
    return str(uuid.UUID(bytes=bytes(digest)))


if RUN.exists():
    shutil.rmtree(RUN)
(RUN / 'mods').mkdir(parents=True)
(RUN / 'libraries').mkdir()

loader = artifact('net.fabricmc', 'fabric-loader', '0.19.5')
classpath = [loader]
with zipfile.ZipFile(loader) as archive:
    installer = json.loads(archive.read('fabric-installer.json'))
for library in installer['libraries']['common'] + installer['libraries']['server']:
    path = artifact(*library['name'].split(':'))
    if hashlib.sha1(path.read_bytes()).hexdigest() != library['sha1']:
        raise SystemExit(f'Checksum mismatch for {path}')
    classpath.append(path)
# The server bundle carries the server and its libraries; unpack them as the vanilla launcher does.
with zipfile.ZipFile(CACHE / 'fabric-loom/26.3/minecraft-server.jar') as archive:
    for kind in ['libraries', 'versions']:
        for line in archive.read(f'META-INF/{kind}.list').decode().splitlines():
            digest, _, path = line.split('\t')
            data = archive.read(f'META-INF/{kind}/{path}')
            if hashlib.sha256(data).hexdigest() != digest:
                raise SystemExit(f'Checksum mismatch for {path} in the server bundle')
            target = RUN / 'libraries' / Path(path).name
            target.write_bytes(data)
            classpath.append(target)

fabric_api = list((PROFILE / 'mods').glob('fabric-api-*.jar'))
if len(fabric_api) != 1:
    raise SystemExit('Expected one Fabric API in the QA profile')
for source in fabric_api + EXTRA_MODS:
    with zipfile.ZipFile(source) as archive:
        if json.loads(archive.read('fabric.mod.json')).get('environment') == 'client':
            raise SystemExit(f'{source.name} is a client-only mod')
    shutil.copy2(source, RUN / 'mods' / source.name)

(RUN / 'eula.txt').write_text('eula=true\n')
(RUN / 'server.properties').write_text(
    f'server-ip=127.0.0.1\nserver-port={PORT}\nonline-mode=false\nenforce-secure-profile=false\n'
    'level-type=minecraft\\:flat\ngamemode=survival\ndifficulty=peaceful\nspawn-monsters=false\n'
    'view-distance=4\nsimulation-distance=4\nspawn-protection=0\nallow-flight=true\n'
    'pause-when-empty-seconds=0\nwhite-list=false\nmotd=Hotkey Wheel QA\nsync-chunk-writes=false\n')
(RUN / 'ops.json').write_text(json.dumps([{'uuid': offline_uuid(PLAYER), 'name': PLAYER, 'level': 4,
                                           'bypassesPlayerLimit': True}], indent=2) + '\n')

command = [str(JAVA), '-Xms512M', '-Xmx1536M', '-XX:ActiveProcessorCount=2', '-cp', os.pathsep.join(map(str, classpath)),
           'net.fabricmc.loader.impl.launch.knot.KnotServer', 'nogui']
(RUN / 'launch.json').write_text(json.dumps({
    'port': PORT, 'command': command,
    'mods': {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted((RUN / 'mods').glob('*.jar'))}},
    indent=2) + '\n')

with (RUN / 'console.log').open('w') as log:
    process = subprocess.Popen(command, cwd=RUN, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
    (RUN / 'server.pid').write_text(f'{process.pid}\n')

    def stop(*_):
        if process.poll() is None:
            process.stdin.write('stop\n')
            process.stdin.flush()

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    try:
        while process.poll() is None:
            request = RUN / 'console-command.txt'
            if request.exists():
                text = request.read_text()
                request.unlink()
                process.stdin.write(text.rstrip('\n') + '\n')
                process.stdin.flush()
            time.sleep(0.25)
    finally:
        if process.poll() is None:
            try:
                process.wait(timeout=60)
            except subprocess.TimeoutExpired:
                process.kill()
        (RUN / 'server.pid').unlink(missing_ok=True)
(RUN / 'exit-code.txt').write_text(f'{process.returncode}\n')
raise SystemExit(process.returncode)
