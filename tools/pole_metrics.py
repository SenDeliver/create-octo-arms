"""Pole suite checks: a 1x1 jungle log pole (x 5000, z 0, y -60..-20) and a 2x2 one (x 5100..5101, z 0..1, y -60..-41) climbed
from a face and from a corner, and gone round at height. usage: pole_metrics.py tools/logs/<tag>-pole"""
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

for tag, a, b in (('F face copper', 'fa', 'fb'), ('C corner copper walk-in, jump only', 'ca', 'cb'), ('N corner netherite walk-in, jump only', 'na', 'nb'),
                  ('C2 corner copper forward + jump', 'c2a', 'c2b'), ('N2 corner netherite forward + jump', 'n2a', 'n2b')):
    if a not in mk or b not in mk:
        check(False, tag, 'missing marks'); continue
    w = span(a, b)
    top = max(x['p'][1] for x in w) + 60
    hi = max(w, key=lambda x: x['p'][1])
    check(top >= 40 and states(w) == 'ACTIVE', tag, f"start feet {w[0]['p'][1] + 60:.2f}, max feet {top:.2f} above floor (>= 40; pole top 41), end feet {w[-1]['p'][1] + 60:.2f}, body at start {w[0]['p'][0] - 5001:.2f} east / {w[0]['p'][2] - 1:.2f} south of the corner (5001,1), at the top {hi['p'][0] - 5001:.2f} / {hi['p'][2] - 1:.2f}, held min {min(x['held'] for x in w)}, states {states(w)}")
for tag, a, b in (('S round copper', 'sa', 'sb'), ('T round netherite', 'ta', 'tb')):
    if a not in mk or b not in mk:
        check(False, tag, 'missing marks'); continue
    w = span(a, b)
    a0, a1 = angle(w[0]['p']), angle(w[-1]['p'])
    far = max(abs((angle(x['p']) - a0 + 540) % 360 - 180) for x in w)
    check(far >= 80 and states(w) == 'ACTIVE', tag, f"furthest {far:.0f} deg round the pole (>= 80: past the first corner onto the next face; 32 before the fix), feet y {w[0]['p'][1] + 60:.2f} -> {w[-1]['p'][1] + 60:.2f}, pos {w[0]['p'][0]:.2f},{w[0]['p'][2]:.2f} -> {w[-1]['p'][0]:.2f},{w[-1]['p'][2]:.2f}, held min {min(x['held'] for x in w)}, states {states(w)}")
def offsets(p):
    return f"{5100 - p[0]:.2f} west / {p[2] - 2:.2f} south of the corner"

for tag, a, b in (('D 2x2 corner copper hugging', 'da', 'db'), ('E 2x2 corner copper owner offsets forward + jump', 'ea', 'eb'),
                  ('G 2x2 corner netherite owner offsets forward + jump', 'ga', 'gb'), ('H 2x2 west face netherite 0.7 off', 'ha', 'hb'),
                  ('J 2x2 corner copper far offsets', 'ja', 'jb')):
    if a not in mk or b not in mk:
        check(False, tag, 'missing marks'); continue
    w = span(a, b)
    hi = max(w, key=lambda x: x['p'][1])
    top = hi['p'][1] + 60
    check(top >= 19 and states(w) == 'ACTIVE', tag, f"max feet {top:.2f} above floor (>= 19; pole top 20), body at start {offsets(w[0]['p'])}, at the top {offsets(hi['p'])}, end feet {w[-1]['p'][1] + 60:.2f}, held min {min(x['held'] for x in w)}, states {states(w)}")
for tag, a, b in (('R 2x2 round copper', 'ra', 'rb'), ('Q 2x2 round netherite', 'qa', 'qb')):
    if a not in mk or b not in mk:
        check(False, tag, 'missing marks'); continue
    w = span(a, b)
    ang = lambda p: math.degrees(math.atan2(p[2] - 1, p[0] - 5101))
    a0 = ang(w[0]['p'])
    far = max(abs((ang(x['p']) - a0 + 540) % 360 - 180) for x in w)
    check(far >= 80 and states(w) == 'ACTIVE', tag, f"furthest {far:.0f} deg round the pole (>= 80), feet y {w[0]['p'][1] + 60:.2f} -> {w[-1]['p'][1] + 60:.2f}, pos {w[0]['p'][0]:.2f},{w[0]['p'][2]:.2f} -> {w[-1]['p'][0]:.2f},{w[-1]['p'][2]:.2f}, held min {min(x['held'] for x in w)}, states {states(w)}")
exc = [l for l in c.splitlines() if 'Exception' in l and 'ApiStatus' not in l and 'neoforge-client.new.tmp.toml' not in l]
check(not exc, 'E exceptions', f'{len(exc)} exception lines')
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
