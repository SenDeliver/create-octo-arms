"""Pole suite checks: a 1x1 jungle log pole (x 5000, z 0, y -60..-20) climbed from a face and from a corner, and gone round
at height. usage: pole_metrics.py tools/logs/<tag>-pole"""
import math, re, sys
pre = sys.argv[1]
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
c = open(f'{pre}-client.log', errors='replace').read()
tr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num + r' d=\S+ cmd=\S+ held=(\d) state=(\w+)', c):
    tr[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), held=int(m[5]), state=m[6])
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
PX, PZ = 5000.5, 0.5
fails = []

def check(ok, name, text):
    if not ok: fails.append(name)
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")

def span(a, b):
    return [tr[g] for g in sorted(tr) if mk[a] <= g <= mk[b]]

def states(w):
    out = []
    for x in w:
        if not out or out[-1] != x['state']: out.append(x['state'])
    return ' -> '.join(out)

def angle(p):
    return math.degrees(math.atan2(p[2] - PZ, p[0] - PX))

for tag, a, b in (('F face copper', 'fa', 'fb'), ('C corner copper', 'ca', 'cb'), ('N corner netherite', 'na', 'nb')):
    if a not in mk or b not in mk:
        check(False, tag, 'missing marks'); continue
    w = span(a, b)
    top = max(x['p'][1] for x in w) + 60
    check(top >= 40 and states(w) == 'ACTIVE', tag, f"start feet {w[0]['p'][1] + 60:.2f}, max feet {top:.2f} above floor (>= 40; pole top 41), end feet {w[-1]['p'][1] + 60:.2f}, held min {min(x['held'] for x in w)}, states {states(w)}")
for tag, a, b in (('S round copper', 'sa', 'sb'), ('T round netherite', 'ta', 'tb')):
    if a not in mk or b not in mk:
        check(False, tag, 'missing marks'); continue
    w = span(a, b)
    a0, a1 = angle(w[0]['p']), angle(w[-1]['p'])
    far = max(abs((angle(x['p']) - a0 + 540) % 360 - 180) for x in w)
    check(far >= 80 and states(w) == 'ACTIVE', tag, f"furthest {far:.0f} deg round the pole (>= 80: past the first corner onto the next face; 32 before the fix), feet y {w[0]['p'][1] + 60:.2f} -> {w[-1]['p'][1] + 60:.2f}, pos {w[0]['p'][0]:.2f},{w[0]['p'][2]:.2f} -> {w[-1]['p'][0]:.2f},{w[-1]['p'][2]:.2f}, held min {min(x['held'] for x in w)}, states {states(w)}")
exc = [l for l in c.splitlines() if 'Exception' in l and 'ApiStatus' not in l and 'neoforge-client.new.tmp.toml' not in l]
check(not exc, 'E exceptions', f'{len(exc)} exception lines')
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
