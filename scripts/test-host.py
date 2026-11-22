"""Run bounded protocol tests on the host; does not connect to a phone."""
from pathlib import Path
import hashlib
import os
import subprocess

root = Path(__file__).resolve().parents[1]
java = Path(os.environ['JAVA_HOME']) / 'bin'
sdk_root = os.environ.get('ANDROID_SDK_ROOT')
if not sdk_root:
    raise SystemExit('Set ANDROID_SDK_ROOT to an Android SDK with platform 35')
android_jar = Path(sdk_root) / 'platforms/android-35/android.jar'
if not android_jar.is_file():
    raise SystemExit(f'Install Android SDK Platform 35: {android_jar} was not found')
output = root / 'test-results/classes'
output.mkdir(parents=True, exist_ok=True)
jar = root / 'native-auth/deps/json-20240303.jar'
jar.parent.mkdir(parents=True, exist_ok=True)
if not jar.exists():
    subprocess.run(['curl', '-fL', '--retry', '2', '--max-time', '120', 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar', '-o', str(jar)], check=True)
if hashlib.sha256(jar.read_bytes()).hexdigest() != '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed':
    raise SystemExit('JSON dependency SHA-256 mismatch')
test_sources = root / 'app/tests'
# Use the pinned host JSON implementation ahead of the Android SDK stubs.
dependencies = os.pathsep.join(map(str, (jar, android_jar)))
sourcepath = os.pathsep.join(map(str, (root / 'app/src', test_sources)))
subprocess.run([
    str(java / 'javac'), '-encoding', 'UTF-8', '-cp', dependencies,
    '-sourcepath', sourcepath, '-d', str(output),
    *map(str, sorted(test_sources.glob('*.java'))),
], check=True)
tests = [
    ('BeaconTest', []), ('PortTest', []), ('WireIdentityTest', []),
    ('WireTest', [str(root / 'native-auth/wire-vectors.txt'), str(root / 'test-results/wire.bin')]),
    ('NetworkTest', [str(root / 'native-auth/network-vectors.txt')]), ('NetworkFrequencyTest', []),
    ('DmsdpTest', [str(root / 'native-auth/dmsdp-vectors.txt')]), ('CipherTest', []),
    ('ControlTest', []), ('DeviceBusinessTest', []), ('DisplayGateTest', []),
    ('HiSightTest', []), ('HiSightTcpTest', ['--media-idle']), ('UiInputTest', []),
    ('AudioTest', []), ('MicrophoneTest', []), ('MicrophoneCapabilityTest', []),
    ('ReceiverStatusTest', []), ('ApScanTest', [])
]
runtime_classpath = os.pathsep.join((str(output), dependencies))
for name, args in tests:
    print('Running ' + name, flush=True)
    subprocess.run([str(java / 'java'), '-cp', runtime_classpath, 'local.hicarlab.' + name, *args], check=True, timeout=90)
print('All host tests passed. This does not prove device or car compatibility.')
