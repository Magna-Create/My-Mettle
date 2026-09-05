#!/usr/bin/env python3
"""Export audited runtime/JNI libraries for Termux; import only a hash-pinned bundle matching source."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile
ROOT = Path(__file__).resolve().parents[1]
NAMES = {'libMNN.so', 'libMNN_Express.so', 'libMNN_CL.so', 'libMNN_Vulkan.so', 'libMNNOpenCV.so', 'libMNNAudio.so', 'libllm.so', 'libc++_shared.so', 'liblab2b_mnn.so'}
SOURCES = ['app/src/main/cpp/mnn_bridge.cpp', 'app/src/main/cpp/CMakeLists.txt', 'tools/prepare_mnn.py', 'app/src/main/java/dev/kian/lab2b/vlm/LocalInferenceEngine.kt']
def digest(data): return hashlib.sha256(data).hexdigest()
def source_hashes(): return {name: digest((ROOT / name).read_bytes()) for name in SOURCES}
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['export', 'import', 'reuse'])
    parser.add_argument('bundle', type=Path, nargs='?', default=ROOT / 'lab2b-native.zip')
    parser.add_argument('--apk', type=Path, default=ROOT / 'app/build/outputs/apk/debug/app-debug.apk')
    parser.add_argument('--sha256')
    args = parser.parse_args()
    if args.action == 'reuse':
        destination = ROOT / '.deps/prebuilt'
        manifest_path = destination / 'manifest.json'
        if not manifest_path.is_file():
            raise SystemExit('No previously imported native bundle. Import a verified bundle first.')
        manifest = json.loads(manifest_path.read_text())
        expected = source_hashes()
        actual = dict(manifest['sources'])
        adapter = 'app/src/main/java/dev/kian/lab2b/vlm/LocalInferenceEngine.kt'
        # Explicit compatibility migration: C++ and build inputs must remain identical.
        # The old adapter's complete hash and unchanged JVM native declarations are pinned.
        if actual != expected:
            if actual.get(adapter) != 'e731aadbc709ce129e7ff2261c646ecceb3a079b589e20f3fd841e130e8bc480': raise SystemExit('Unknown adapter revision; obtain matching native bundle')
            abi = (ROOT / adapter).read_text().split('interface OutputCallback')[1].split('class MnnEngine')[0]
            if digest(abi.encode()) != 'e4e9efed49ff7df2e62a2c81c683fd063c228a1eda237e630600231fa41001f9': raise SystemExit('Native declarations changed; rebuild JNI')
            actual[adapter] = expected[adapter]
            if actual != expected: raise SystemExit('Native build inputs changed; rebuild JNI')
        if set(manifest['files']) != NAMES: raise SystemExit('Incomplete native bundle')
        for name, sha in manifest['files'].items():
            if digest((destination / 'arm64-v8a' / name).read_bytes()) != sha: raise SystemExit('Native file changed: ' + name)
        manifest['sources'] = expected
        temporary = manifest_path.with_suffix('.partial')
        temporary.write_text(json.dumps(manifest))
        temporary.replace(manifest_path)
        print('Existing verified native libraries reused; JNI declarations and all native build inputs unchanged.')
        return
    if args.action == 'export':
        with zipfile.ZipFile(args.apk) as apk:
            files = {name: apk.read('lib/arm64-v8a/' + name) for name in sorted(NAMES)}
        manifest = dict(sources=source_hashes(), files={name: digest(data) for name, data in files.items()})
        args.bundle.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.bundle, 'w', zipfile.ZIP_DEFLATED) as bundle:
            bundle.writestr('manifest.json', json.dumps(manifest, sort_keys=True))
            for name, data in files.items(): bundle.writestr(name, data)
        print(digest(args.bundle.read_bytes()), args.bundle.name)
    else:
        if not args.sha256 or digest(args.bundle.read_bytes()) != args.sha256:
            raise SystemExit('Supply the published matching --sha256; bundle hash mismatch')
        with zipfile.ZipFile(args.bundle) as bundle:
            if set(bundle.namelist()) != NAMES | {'manifest.json'}: raise SystemExit('Unexpected native files')
            manifest = json.loads(bundle.read('manifest.json'))
            if manifest['sources'] != source_hashes(): raise SystemExit('Native bundle does not match this checkout; rebuild on desktop/CI')
            files = {name: bundle.read(name) for name in NAMES}
            if {name: digest(data) for name, data in files.items()} != manifest['files']: raise SystemExit('Native file hash mismatch')
        destination = ROOT / '.deps/prebuilt/arm64-v8a'
        destination.mkdir(parents=True, exist_ok=True)
        for old in destination.iterdir(): old.unlink()
        for name, data in files.items(): (destination / name).write_bytes(data)
        (ROOT / '.deps/prebuilt/manifest.json').write_text(json.dumps(manifest))
        print('Verified native bundle installed. Build with -Plab2bPrebuiltNative=true')
if __name__ == '__main__': main()
