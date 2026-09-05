#!/usr/bin/env python3
"""Read public model metadata/config only. Never download model weights."""
import urllib.request, json, hashlib
repo='taobao-mnn/gemma-4-E4B-it-MNN'
def read(url):
    with urllib.request.urlopen(url, timeout=60) as r: return r.read()
info=json.loads(read('https://huggingface.co/api/models/'+repo))
rev=info['sha']
assets=json.loads(read(f'https://huggingface.co/api/models/{repo}/tree/{rev}'))
required=['config.json','llm.mnn','llm.mnn.weight','llm_config.json','ple_embeddings_int4.bin','tokenizer.mtok','visual.mnn','visual.mnn.weight']
result=[]
for a in assets:
    if a['path'] not in required: continue
    if 'lfs' in a: sha=a['lfs']['oid']
    else:
        assert a['size'] < 65536
        data=read(f'https://huggingface.co/{repo}/resolve/{rev}/{a["path"]}')
        sha=hashlib.sha256(data).hexdigest()
        print(a['path'], data.decode())
    result.append(dict(name=a['path'],sizeBytes=a['size'],sha256=sha))
print('E4B_REGISTRY='+json.dumps(dict(source=repo,revision=rev,files=result)))
for source, revision in [('gemma-4-E2B-it-MNN','ce18884f154ce405545f1acda5c5c8fdd9c1280c'),('gemma-4-E4B-it-MNN',rev)]:
    print('TEMPLATE',source,read(f'https://huggingface.co/taobao-mnn/{source}/resolve/{revision}/llm_config.json').decode())
