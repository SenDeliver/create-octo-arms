"""relog suite: a Rig that carries at logout or at a dimension change is Active again after it, without fall damage.
usage: relog_metrics.py tools/logs/<tag>-relog"""
import re, sys
pre = sys.argv[1]
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
tr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num + r' .*?held=(\d) state=(\w+) .*?ground=(\w+) hp=(\S+)', c):
    tr[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), held=int(m[5]), state=m[6], ground=m[7] == 'true', hp=float(m[8]))
st = {}
for m in re.finditer(r'STRACE gt=(\d+) Dev pos=' + num + r' v=\S+ state=(\w+) grips=(\d) cf=\S+ flying=\w+ fall=(\S+) hp=(\S+)', s):
    st[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), state=m[5], grips=int(m[6]), fall=float(m[7]), hp=float(m[8]))
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
hurts = [(int(m[0]), m[1], float(m[2])) for m in re.findall(r'HURT gt=(\d+) Dev source=(\S+) amount=(\S+)', s)]
fails = []

def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok: fails.append(name)

def span(d, a, b):
    return [(g, d[g]) for g in sorted(d) if a <= g <= b]

def states(w):
    out = []
    for _, x in w:
        if not out or out[-1] != x['state']: out.append(x['state'])
    return ' -> '.join(out)

def at(d, g):
    keys = [k for k in d if k <= g]
    return d[max(keys)] if keys else None

def hurt(a, b):
    return [(g, src, amt) for g, src, amt in hurts if a <= g <= b]

need = ['r1a', 'r1b', 'r1c', 'r2a', 'r2b', 'r2c', 'r3a', 'r3b', 'na', 'nb']
missing = [k for k in need if k not in mk]
if missing:
    print(f'FAIL marks missing: {missing}')
    print('RESULT FAIL')
    sys.exit(1)

print('== R1 logout while Active beside a wall')
a, b = at(tr, mk['r1a']), at(tr, mk['r1b'])
w = span(tr, mk['r1a'] + 1, mk['r1b'])
check('R1 back Active where it was', a and b and a['state'] == 'ACTIVE' and b['state'] == 'ACTIVE' and not b['ground'] and abs(b['p'][1] - a['p'][1]) < 0.5 and a['p'][1] > -55,
      f"before feet {a and a['p'][1]:.3f} {a and a['state']}, after feet {b and b['p'][1]:.3f} {b and b['state']}, client after login {states(w)}")
check('R1 no damage', not hurt(mk['r1a'], mk['r1b']) and min(x['hp'] for _, x in span(st, mk['r1a'], mk['r1b'])) == 20, f"{hurt(mk['r1a'], mk['r1b'])}")
check('R1 Setting Down after it reaches the floor', at(tr, mk['r1c'])['state'] == 'STOWED' and at(tr, mk['r1c'])['ground'], f"client {states(span(tr, mk['r1b'], mk['r1c']))}")

print('== R2 logout while Active, the Grips gone meanwhile')
a, b = at(tr, mk['r2a']), at(tr, mk['r2b'])
w = span(tr, mk['r2a'] + 1, mk['r2b'])
sw = span(st, mk['r2a'] + 1, mk['r2b'])
check('R2 Falling on login, caught on the rest of the wall', a and b and a['state'] == 'ACTIVE' and b['state'] == 'ACTIVE' and 'FALLING -> ACTIVE' in states(w)
      and not b['ground'] and a['p'][1] - b['p'][1] > 0.5 and b['p'][1] > -59,
      f"before feet {a and a['p'][1]:.3f}, after feet {b and b['p'][1]:.3f} (wall top now y -52, floor -60), client {states(w)}, server {states(sw)}")
check('R2 no damage', not hurt(mk['r2a'], mk['r2b']), f"{hurt(mk['r2a'], mk['r2b'])}, server fall max {max((x['fall'] for _, x in sw), default=0):.2f}")

print('== R3 logout while Stowed')
w = span(tr, mk['r3a'], mk['r3b']); sw = span(st, mk['r3a'], mk['r3b'])
check('R3 still Stowed after login', states(w) == 'STOWED' and states(sw) == 'STOWED', f"client {states(w)}, server {states(sw)}")

print('== N dimension change while Active')
a, b = at(tr, mk['na']), at(tr, mk['nb'])
w = span(tr, mk['na'] + 1, mk['nb'])
check('N Active again in the Nether, caught at the wall', a and b and a['state'] == 'ACTIVE' and b['state'] == 'ACTIVE' and abs(b['p'][1] - 150) < 2 and not b['ground'],
      f"before {a and a['p']}, after {b and b['p']} (wall x 13540, y 140..160), client {states(w)}")
check('N no damage', not hurt(mk['na'], mk['nb']), f"{hurt(mk['na'], mk['nb'])}")

resent = s.count('forcing Falling')
check('No forced Falling or speed violation', resent == 0 and 'faster than its top Speed' not in s, f'{resent} forcing lines')
print('RESULT ' + ('PASS' if not fails else 'FAIL ' + ', '.join(fails)))
