#!/usr/bin/env python3
"""Export passing, sanitized matrix/SSO wire evidence without launch credentials or game directories."""
from pathlib import Path
import hashlib,json,shutil,zipfile
HERE=Path(__file__).resolve().parent;PROJECT=HERE.parents[1];ROOT=PROJECT.parents[1]
OUT=PROJECT/'qa/evidence';OUT.mkdir(exist_ok=True)
EXPECTED='2ab08d31dc335b03e2e4c16bfd2b1538de8e7e5a6e1760aac4164ed807e49fe3'
matrix=OUT/'matrix';matrix.mkdir(exist_ok=True)
for name,suite,count in [('developer','unified-developer-v2',16),('sso-port','unified-sso-port-v1',2),('standalone-chalk-coexistence','unified-chalk-coexist-v1',1)]:
    run=HERE/'runs'/suite;results=json.loads((run/'RESULTS.json').read_text());assert len(results)==count
    excerpts=[]
    for r in results:
        assert r['pass'] and r['candidate_sha256']==EXPECTED and not r['errors']
        path=run/r['profile']/'console.log'
        excerpts.append(r['profile']+'\n'+'\n'.join(line for line in path.read_text(errors='replace').splitlines() if 'SSO_STACK_MODULES' in line or 'Done (' in line or 'Reloading!' in line or 'Stopping server' in line or 'Saving worlds' in line))
    (matrix/(name+'-results.json')).write_text(json.dumps(results,indent=2)+'\n')
    (matrix/(name+'-log-excerpts.txt')).write_text('\n\n'.join(excerpts).replace(str(ROOT),'<WORKSPACE>')+'\n')
shutil.copy2(HERE/'TARGET-ARTIFACTS.json',matrix/'TARGET-ARTIFACTS.json')
network=OUT/'sso-vanilla-wire';network.mkdir(exist_ok=True)
for track,suite in [('developer','unified-developer-v1'),('sso-port','unified-sso-port-v1')]:
    run=PROJECT/'qa/sso-vanilla/runs'/suite;r=json.loads((run/'result.json').read_text())
    assert r['pass'] and r['candidate_sha256']==EXPECTED
    assert {m['id'] for m in r['mods']['client']}=={'fabric-api','sso_network_client_qa'}
    serverids={m['id'] for m in r['mods']['server']}
    assert ('defaulted' in serverids)==(track=='developer') and ('codecui' in serverids)==(track=='developer')
    pack=run/'server/polymer/resource_pack.zip'
    with zipfile.ZipFile(pack) as z:assert z.testzip() is None
    clientlog=(run/'client/console.log').read_text(errors='replace')
    loaded=[line for line in clientlog.splitlines() if 'Reloading ResourceManager' in line and 'file/sso-qa.zip' in line]
    assert loaded,'Generated pack was not loaded'
    r['resource_pack']={'integrity':True,'sha256':hashlib.sha256(pack.read_bytes()).hexdigest(),'loaded_evidence':loaded}
    (network/(track+'-result.json')).write_text(json.dumps(r,indent=2).replace(str(ROOT),'<WORKSPACE>')+'\n')
print('Exported 19 matrix cases and 8 menu transactions; candidate',EXPECTED)
