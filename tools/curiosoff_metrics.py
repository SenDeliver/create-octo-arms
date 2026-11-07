"""curiosoff suite (adr/0006, server config curios.backSlot = false): pass/fail from a curiosoff log set. usage: curiosoff_metrics.py tools/logs/<tag>-curiosoff"""
import re, sys

pre = sys.argv[1]
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
CHEST, RIG = 'minecraft:iron_chestplate', 'tentaworks:copper_tentacle_rig'
fails = []


def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok:
        fails.append(name)


back = {m[0]: dict(worn=m[1], item=m[2], air=int(m[3]), armor=int(m[4]), chest=m[5])
        for m in re.findall(r'SBACK (\S+) Dev worn=(\S+) backitem=(\S+) backair=(-?\d+) backdmg=\d+ backmax=\d+ armor=(\d+) chest=(\S+)', s)}
tries = {m[0]: dict(back=m[1] == 'true', chest=m[2] == 'true') for m in re.findall(r'SBACKTRY (\S+) Dev backok=(\w+) chestok=(\w+)', s)}
st = {int(m[1]): dict(state=m[2], slot=m[3]) for m in re.finditer(r'STRACE gt=(\d+) Dev .*? state=(\w+) .* rigslot=(\S+)', s)}
tr = {int(m[1]): dict(state=m[2], slot=m[3]) for m in re.finditer(r'TRACE t=\d+ gt=(\d+) .*? state=(\w+) .* rigslot=(\S+)', c)}
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
tips = ' | '.join(m[1] for m in re.findall(r'CTIP (\S+) (.*)', c) if m[0] == 'c0')
overlays = re.findall(r'OVERLAY gt=\d+ text="(.*?)"', c)
inv = {(m[0], m[1]): m[2] for m in re.findall(r'SINV (\S+) Dev slot=(\S+) item=(\S+)', s)}
missing = [k for k in ('c0a', 'c0', 'c3a', 'c3', 'end') if k not in mk]
tags = [k for k in ('c0', 'c0b', 'c2', 'c3') if k not in back]
if missing or tags or not st or not tr:
    print(f'FAIL marks or back lines missing: marks {missing}, SBACK {tags}, STRACE {len(st)}, TRACE {len(tr)}')
    sys.exit(1)


def span(d, a, b):
    return [x for g, x in sorted(d.items()) if a <= g <= b]


c0 = back['c0']
check('C0 a Rig in the back slot is inert with the config off', c0['item'] == RIG and c0['worn'] == '-' and c0['chest'] == CHEST, f'{c0}')
check('C0 Double Jump does nothing', all(x['state'] == 'STOWED' and x['slot'] == '-' for x in span(st, mk['c0a'], mk['c0'])) and all(x['state'] == 'STOWED' for x in span(tr, mk['c0a'], mk['c0'])),
      f"server states {sorted(set(x['state'] for x in span(st, mk['c0a'], mk['c0'])))}, client {sorted(set(x['state'] for x in span(tr, mk['c0a'], mk['c0'])))}")
check('C0 tooltip says the back slot is disabled', 'disabled on this server' in tips, tips[-240:])
t = tries.get('c0')
check('C1 the back slot refuses a Rig, the chest slot takes one', t is not None and not t['back'] and t['chest'], f'{t}')
check('C1 the action bar says the back slot takes no Rig', any('takes no Rig' in o for o in overlays), f'overlays {overlays}')
c2 = back['c2']
check('C2 use with a chestplate on swaps into the chest as vanilla does', c2['worn'] == 'chest' and c2['chest'] == RIG and inv.get(('c2', '0')) == CHEST, f"{c2}, hotbar 0 {inv.get(('c2', '0'))}")
c3 = span(st, mk['c3a'], mk['c3'])
check('C3 the chest Rig works', c3 and sum(x['state'] == 'ACTIVE' and x['slot'] == 'chest' for x in c3) >= 0.9 * len(c3), f"{sum(x['state'] == 'ACTIVE' for x in c3)}/{len(c3)} Active")
bad = [l for l in s.splitlines() if re.search(r'was kicked|moves faster than|moves against|forcing Falling', l)]
check('N no kick or Rig warning', not bad, f'{len(bad)} lines' + (f': {bad[0][-160:]}' if bad else ''))
exc = [l for l in (c + s).splitlines() if re.match(r'\[[\d:]+\] \[[^\]]*/(ERROR|FATAL)\]', l) or re.match(r'\s+at dev\.tentaworks\.', l)
       or re.match(r'\[[\d:]+\] \[', l) and 'Exception' in l and 'Error loading class' not in l]
exc = [l for l in exc if 'No key layers' not in l and 'MessageIOException' not in l and 'Unexpected type tag' not in l]
check('N no exceptions', not exc, f'{len(exc)} lines' + (f': {exc[0][-200:]}' if exc else ''))
print(f'curiosoff: {len(fails)} FAIL' if fails else 'curiosoff: all PASS')
