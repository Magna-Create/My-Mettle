#!/usr/bin/env python3
"""Reject tracked model/native/build outputs and unignored experimental binaries."""
from pathlib import Path
import subprocess
root = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip())
paths = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=root).decode().split('\0')
bad = []
for name in filter(None, paths):
    p = Path(name)
    if p.suffix.lower() in {'.gguf', '.litertlm', '.mnn', '.weight', '.mtok', '.safetensors', '.onnx', '.pte', '.dlc', '.apk', '.aab', '.so', '.aar'}:
        bad.append(name)
    if name.startswith('experiments/lab2b-vlm-harness/') and (p.suffix in {'.bin', '.zip', '.gz'} or '/.deps/' in name):
        bad.append(name)
if bad:
    raise SystemExit('LAB2B_BINARY_SAFETY=FAIL\n' + '\n'.join(bad))
print('LAB2B_BINARY_SAFETY=PASS')
