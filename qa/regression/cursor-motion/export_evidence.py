#!/usr/bin/env python3
"""Export only synthetic QA observations, packet traces, and artifact hashes."""
import json,re
from pathlib import Path
HERE=Path(__file__).resolve().parent
cases={
 'baseline-moving-1.0.0':{'motion':True,'corrupted':False,'expected_pass':True},
 'baseline-moving-1.0.1':{'motion':True,'corrupted':False,'expected_pass':True},
 'corrupted-moving-1.0.0':{'motion':True,'corrupted':True,'expected_pass':False},
 'corrupted-moving-1.0.1':{'motion':True,'corrupted':True,'expected_pass':True},
 'corrupted-stationary-1.0.0':{'motion':False,'corrupted':True,'expected_pass':True},
 'corrupted-held-delay-1.0.1':{'motion':True,'corrupted':True,'hold_ticks':3,'proxy_delay_ms_each_direction':100,'expected_pass':True},
 'corrupted-moving-original-only':{'motion':True,'corrupted':True,'original_mods_only':True,'expected_pass':True},
}
export={'scope':'Fresh native client processes; generated Survival inventory; synthetic stacks only; direct screen mouse events; no actual player or world data.','cases':[]}
for name,settings in cases.items():
 p=HERE/'runs'/name/'result.json'
 if not p.exists():raise SystemExit('Missing result '+name)
 r=json.loads(p.read_text())
 if r['pass']!=settings['expected_pass']:raise SystemExit('Unexpected result '+name)
 item={'suite':name,**settings,'pass':r['pass'],'client_result':r.get('client-result'),'server_result':r.get('server-result'),'mods':{side:[x for x in entries if 'creative-qa' not in x['file']] for side,entries in r['mods'].items()}}
 for key in ['client-observations','cursor-trace-client','cursor-trace-server']:
  item[key]=[re.sub(r'^\d+ ','',line) for line in r.get(key,'').splitlines()]
 item['quick_craft_packets']=sum('BEFORE ' in x and 'input=QUICK_CRAFT ' in x for x in item['cursor-trace-server']); item['cursor_corrections']=sum('CURSOR BEFORE ' in x for x in item['cursor-trace-client']); export['cases'].append(item)
out=HERE.parents[1]/'evidence/cursor-motion-1.0.1.json';out.write_text(json.dumps(export,indent=2)+'\n');print(out.relative_to(HERE.parents[2]))
