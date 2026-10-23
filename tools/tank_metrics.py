"""Tank suite checks (issue #19): Create Fluid Tanks are Grips. A 1x1 Fluid Tank column (x 5200, z 0, y -60..-41) and a 2x2 Creative
Fluid Tank column (x 5300..5301, z 0..1) climbed from a face and from a corner; a Fluid Tank roof (y -56 over x 5398..5400) hung under.
Slab report: on a floating 3x3 bottom-slab platform (x 5499..5501, top -49.5) the body walks east past the edge and stays held there.
usage: tank_metrics.py tools/logs/<tag>-tank"""
import re, sys
pre = sys.argv[1]
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
c = open(f'{pre}-client.log', errors='replace').read()
tr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num + r' d=\S+ cmd=\S+ held=(\d) state=(\w+)', c):
    tr[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), held=int(m[5]), state=m[6])
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
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

for tag, a, b in (('F tank face copper', 'fa', 'fb'), ('N tank face netherite', 'na', 'nb'), ('D creative tank 2x2 corner copper', 'da', 'db')):
    if a not in mk or b not in mk:
        check(False, tag, 'missing marks'); continue
    w = span(a, b)
    hi = max(w, key=lambda x: x['p'][1])
    top = hi['p'][1] + 60
    check(top >= 19 and states(w) == 'ACTIVE', tag, f"max feet {top:.2f} above floor (>= 19; column top 20; 0 before the fix: tanks were no Grips), start feet {w[0]['p'][1] + 60:.2f}, end feet {w[-1]['p'][1] + 60:.2f}, held min {min(x['held'] for x in w)}, states {states(w)}")
tag, a, b = 'R hang under the tank roof copper', 'ra', 'rb'
if a not in mk or b not in mk:
    check(False, tag, 'missing marks')
else:
    w = span(a, b)
    top = max(x['p'][1] for x in w) + 60
    end = w[-1]['p'][1] + 60
    check(top >= 1.0 and end >= 1.0 and states(w) == 'ACTIVE', tag, f"max feet {top:.2f} above floor (>= 1.0; roof underside at 4), end feet {end:.2f} (>= 1.0: still hanging 40 ticks after jump released), held min {min(x['held'] for x in w)}, states {states(w)}")
tag, a, b = 'S slab platform edge copper', 'sa', 'sb'
if a not in mk or b not in mk:
    check(False, tag, 'missing marks')
else:
    w = span(a, b)
    low = min(x['p'][1] for x in w) + 60
    end = w[-1]
    check(low >= 8.0 and end['p'][0] >= 5501.5 and end['held'] >= 2 and states(w) == 'ACTIVE', tag,
          f"min feet {low:.2f} above ground (>= 8.0; platform top 10.5; the body fell to 0 before the fix: a bottom slab's top was no Grip), end x {end['p'][0]:.2f} (>= 5501.5: past the east edge), end held {end['held']}, states {states(w)}")
exc = [l for l in c.splitlines() if 'Exception' in l and 'ApiStatus' not in l and 'neoforge-client.new.tmp.toml' not in l and 'Error loading class: xaero/' not in l]
check(not exc, 'E exceptions', f'{len(exc)} exception lines')
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
