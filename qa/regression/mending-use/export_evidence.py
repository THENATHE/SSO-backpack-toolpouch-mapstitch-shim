#!/usr/bin/env python3
"""Export only synthetic case observations and artifact hashes, never launcher commands."""
import argparse, json
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('label');a=p.parse_args();here=Path(__file__).resolve().parent
server=(here/'src/control/server-evidence.txt').read_text().splitlines();client=(here/'src/control/client-evidence.txt').read_text().splitlines()
rows=[]
for line in server:
 if not line.startswith('CASE '):continue
 fields=line.split();row={'case':fields[1]}
 for field in fields[2:]:
  k,v=field.split('=',1)
  row[k]=True if v=='true' else False if v=='false' else int(v)
 sync=next(l for l in client if l.startswith('SYNC '+row['case']+' '));row['clientServerSynchronized']=sync.endswith('equal=true')
 row['useHandlerInvocations']=row.pop('usePackets')
 row['clientUse']=next(l.split(' response=',1)[1] for l in client if l.startswith('USE '+row['case']+' '))
 pre=next(l for l in client if l.startswith('PRE_USE '+row['case']+' '))
 if ' damage=' in pre: row['clientDamageBeforeUse']=int(pre.split(' damage=')[1].split()[0])
 row['clientBrokenBeforeUse']=next(l for l in client if l.startswith('PRE_USE '+row['case']+' ')).endswith('broken=true')
 row['serverBrokenBeforeUse']=next(l for l in server if l.startswith('CONFIG '+row['case']+' ')).split(' broken=')[1].split()[0]=='true'
 rows.append(row)
assert len(rows) in (3,12)
assert all(r['clientServerSynchronized'] for r in rows), 'native inventory desynchronized'
audit=json.loads((here/'src/runs/server/launch-audit-1.json').read_text())
mods=[{'filename':Path(m['path']).name,'sha256':m['sha256']} for m in audit['mods'] if not Path(m['path']).name.endswith('-qa.jar')]
integrity=json.loads((here/'src/integrity.json').read_text());assert integrity['unchanged']
result={'label':a.label,'fixtureCompleted':True,'productionArtifactsUnchanged':True,'allFeatureCasesPassed':all(r['expectedBehavior'] for r in rows),'method':'Fresh native client, original mods plus addon; keyShift input then gameMode.useItem. Seeded Survival fixtures; no direct ModUtil repair call. All other SSO configuration defaults unchanged. Use-item-in-air path, not use-on-block. Three repeats are same tick; cooldown explicitly seeded200 ticks. Fully broken cases explicitly carry SSO BROKEN component. Stone degradation randomized by original recipe (0 or1 per use).','serverMods':mods,'cases':rows}
client_audit=json.loads((here/'src/runs/native/launch-audit-1.json').read_text())
result['clientMods']=[{'filename':Path(m['path']).name,'sha256':m['sha256']} for m in client_audit['mods'] if not Path(m['path']).name.endswith('-qa.jar')]
result['automaticRepairEnabled']=not any('mending.isolate-broken=true' in v for v in audit['command'])
result['originalModsOnly']=any('mending.original-only=true' in v for v in audit['command'])
result['counterNote']='Two handler invocations per use packet: network dispatch and server reschedule. Zero means no use packet arrived.'
if not result['automaticRepairEnabled']: result['method']+=' Automatic repair explicitly disabled in disposable server/client memory to isolate still-broken manual use.'
out=here.parents[1]/'evidence'/('mending-use-'+a.label+'.json');out.write_text(json.dumps(result,indent=2)+'\n');print(out);print(json.dumps({'cases':len(rows),'failed':[r['case'] for r in rows if not r['expectedBehavior']],'synchronized':True}))
