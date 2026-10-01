#!/usr/bin/env python3
import subprocess,sys,os,json
from pathlib import Path
root=Path(__file__).resolve().parent
candidate=str(Path(sys.argv[1]).resolve())
cases=[('whetstone-qa','developer-inventory',{'WHETSTONE_QA_SCENARIO':'inventory'}),('whetstone-qa','developer-table',{'WHETSTONE_QA_SCENARIO':'table'}),('whetstone-qa','ported-inventory',{'SSO_QA_TRACK':'port'}),('whetstone-creative-qa','creative-enchanted',{'WHETSTONE_ENCHANTED':'true'})]
results=[]
for runner,suite,extra in cases:
 env=os.environ.copy();env.update(extra)
 subprocess.run([sys.executable,str(root/runner/'run.py'),'combined-four-'+suite,candidate],env=env,check=True)
 result=json.loads((root/runner/'runs'/('combined-four-'+suite)/'result.json').read_text())
 results.append({'suite':suite,'pass':result['pass']})
 print('REPAIR_SUITE',suite,result['pass'],flush=True)
(root/'RESULTS.json').write_text(json.dumps(results,indent=2)+'\n')
if not all(r['pass'] for r in results):raise SystemExit(1)
