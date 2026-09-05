#!/usr/bin/env python3
"""Exercise reuse on the real audited CI native bundle; never change user model data."""
from pathlib import Path
import json, subprocess
from native_bundle import ROOT, digest
manifest=ROOT/'.deps/prebuilt/manifest.json'
data=json.loads(manifest.read_text())
subprocess.run(['python3',str(ROOT/'tools/native_bundle.py'),'reuse'],check=True)
legacy=dict(data);legacy['sources']=dict(data['sources'])
legacy['sources']['app/src/main/java/dev/kian/lab2b/vlm/LocalInferenceEngine.kt']='e731aadbc709ce129e7ff2261c646ecceb3a079b589e20f3fd841e130e8bc480'
manifest.write_text(json.dumps(legacy))
subprocess.run(['python3',str(ROOT/'tools/native_bundle.py'),'reuse'],check=True)
assert json.loads(manifest.read_text()) == data
bad=dict(legacy);bad['sources']=dict(legacy['sources']);bad['sources']['app/src/main/cpp/mnn_bridge.cpp']='0'*64
manifest.write_text(json.dumps(bad))
assert subprocess.run(['python3',str(ROOT/'tools/native_bundle.py'),'reuse']).returncode != 0
manifest.write_text(json.dumps(data))
print('NATIVE_REUSE_TESTS=PASS: current, known legacy, rejected changed C++')
