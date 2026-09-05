#!/usr/bin/env python3
"""Read every packaged ELF header, then use Android zipalign -P 16 on the APK."""
import hashlib
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import zipfile
apk = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / 'app/build/outputs/apk/debug/app-debug.apk'
failures = []
count = total = 0
with zipfile.ZipFile(apk) as archive:
    for name in archive.namelist():
        if not name.endswith('.so'):
            continue
        count += 1
        data = archive.read(name); total += len(data)
        if not name.startswith('lib/arm64-v8a/') or data[:6] != b'\x7fELF\x02\x01':
            failures.append(name + ': expected arm64 little-endian ELF64'); continue
        machine = struct.unpack_from('<H', data, 18)[0]
        offset = struct.unpack_from('<Q', data, 32)[0]
        entry_size, entries = struct.unpack_from('<HH', data, 54)
        aligns = []
        for index in range(entries):
            ptype, flags, poffset, vaddr, paddr, filesz, memsz, align = struct.unpack_from('<IIQQQQQQ', data, offset + index * entry_size)
            if ptype == 1:
                aligns.append(align)
                if align < 16384 or vaddr % 16384 != poffset % 16384:
                    failures.append(name + ': unaligned LOAD')
        if machine != 183 or not aligns:
            failures.append(name + ': wrong machine/no LOAD')
        print(f'{name}: bytes={len(data)} machine={machine} LOAD={aligns} sha256={hashlib.sha256(data).hexdigest()}')
if not count:
    failures.append('No native libraries found')
print(f'APK_BYTES={apk.stat().st_size}\nNATIVE_BYTES={total}\nSO_COUNT={count}\nAPK_SHA256={hashlib.sha256(apk.read_bytes()).hexdigest()}')
zipalign = os.environ.get('ZIPALIGN_BIN') or shutil.which('zipalign')
if not zipalign and os.environ.get('ANDROID_HOME'):
    candidates = sorted(Path(os.environ['ANDROID_HOME']).glob('build-tools/*/zipalign'))
    if candidates: zipalign = str(candidates[-1])
if not zipalign:
    failures.append('Android build-tools zipalign missing')
elif subprocess.run([zipalign, '-c', '-P', '16', '4', str(apk)]).returncode != 0:
    failures.append('APK ZIP 16 KB alignment failed')
if failures:
    raise SystemExit('LAB2B_NATIVE_16K=FAIL\n' + '\n'.join(failures))
print('LAB2B_NATIVE_16K=PASS_STATIC (device runtime acceptance remains pending)')
