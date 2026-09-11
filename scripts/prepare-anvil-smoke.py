#!/usr/bin/env python3
"""Fetch pinned upstream artwork into ignored local fixtures; requires authenticated gh.
Run from the repository root. These resources are never distributed in Postmark.
"""
from pathlib import Path
import subprocess,json,base64
out=Path('local/anvil-stamp-fixture');out.mkdir(parents=True,exist_ok=True)
ref='4111a03b01ab694aafbd542dc065cba76f5d0e98'
seen=set()
def get(path):
 data=json.loads(subprocess.check_output(['gh','api',f'repos/Anvil-Dev/AnvilCraft/contents/{path}?ref={ref}']))
 return base64.b64decode(data['content'])
def model(name):
 if not name.startswith('anvilcraft:') or name in seen:return
 seen.add(name);path='assets/anvilcraft/models/'+name.split(':',1)[1]+'.json'
 blob=get('src/main/resources/'+path);p=out/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(blob)
 data=json.loads(blob)
 if 'parent' in data:model(data['parent'])
 for texture in data.get('textures',{}).values():
  if not texture.startswith('anvilcraft:'):continue
  path='assets/anvilcraft/textures/'+texture.split(':',1)[1]+'.png';p=out/path
  if p.exists():continue
  p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(get('src/main/resources/'+path))
for actual,alias in [('celestial_forging_anvil','nether_star'),('spacetime_supercomputer','recovery_compass')]:
 blob=get('src/generated/resources/assets/anvilcraft/items/'+actual+'.json')
 p=out/('assets/minecraft/items/'+alias+'.json');p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(blob)
 model(json.loads(blob)['model']['model'])
(out/'SOURCE.txt').write_text('Anvil-Dev/AnvilCraft '+ref+'\nTest-only original item model resources; nether_star = celestial_forging_anvil, recovery_compass = spacetime_supercomputer.\n')
print(ref, 'models:',len(seen),'files:',len(list(out.rglob('*.*'))))
