#!/usr/bin/env python3
"""Fetch pinned upstream prebuilts and headers. Never downloads model weights."""
import hashlib
import io
from pathlib import Path
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEPS = ROOT / '.deps'
ARCHIVES = [
    ('mnn-android.zip', 'https://github.com/alibaba/MNN/releases/download/3.6.1/mnn_3.6.1_android_armv7_armv8_cpu_opencl_vulkan.zip',
     '46dc7e86d45b8d4e957db81d2603e0b7f6c9ce9b84092ffdcee1b843cbfc9d71'),
    ('mnn-source.tar.gz', 'https://github.com/alibaba/MNN/archive/refs/tags/3.6.1.tar.gz',
     '4b6065c4e2674318f5bf1dc75836ce4d30c17bfe598c4a1b11b7d0b2092b06e6'),
]

def main():
    DEPS.mkdir(exist_ok=True)
    for name, url, digest in ARCHIVES:
        path = DEPS / name
        if not path.exists():
            print('Downloading', name, flush=True)
            temporary = path.with_suffix('.partial')
            with urllib.request.urlopen(url, timeout=120) as response, temporary.open('wb') as target:
                while chunk := response.read(1024 * 1024):
                    target.write(chunk)
            temporary.replace(path)
        if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise RuntimeError(f'{name}: SHA-256 mismatch; remove it and retry')
    libs = DEPS / 'jniLibs/arm64-v8a'
    libs.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(DEPS / 'mnn-android.zip') as archive:
        for name in archive.namelist():
            if '/arm64-v8a/' in name and name.endswith('.so') and not name.endswith('/libmnncore.so'):
                (libs / Path(name).name).write_bytes(archive.read(name))
    with tarfile.open(DEPS / 'mnn-source.tar.gz') as archive:
        for entry in archive:
            parts = Path(entry.name).parts[1:]
            relative = Path(*parts)
            if not entry.isfile() or '..' in parts:
                continue
            if str(relative).startswith(('include/', 'transformers/llm/engine/include/')):
                target = DEPS / 'headers' / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.extractfile(entry).read())
    print('MNN 3.6.1 prebuilts and matching headers verified; no model weights fetched.')

if __name__ == '__main__':
    main()
