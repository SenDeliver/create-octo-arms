"""Phase-6 numbers and pass/fail from a p6 suite log set (gait spread, steppers, planted while moving, frame cost, plant sounds).
usage: p6_metrics.py tools/logs/<tag>-p6 [--walk]   (--walk: run made with TW_CLIENT_WALK=true, the vanilla walk animation is expected while Held)"""
import math
import re
import sys

pre = sys.argv[1]
walk_cfg = '--walk' in sys.argv[2:]
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
c = open(f'{pre}-client.log', errors='replace').read()
c2 = open(f'{pre}-client2.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
fails = []

SPREAD_MIN = 1.5
PERF_MAX_US = 2000.0


def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok:
        fails.append(name)


gait = {}
for m in re.finditer(r'GAIT gt=(\d+) state=(\w+) pos=' + num + r' planted=(\d) stepping=(\d) minpair=(\S+) slots=(\S+)', c):
    gait[int(m[1])] = dict(state=m[2], p=tuple(map(float, m.group(3, 4, 5))), planted=int(m[6]), stepping=int(m[7]),
                           minpair=None if m[8] == '-' else float(m[8]), slots=m[9].split(';'))
st = {}
for m in re.finditer(r'STRACE gt=(\d+) Dev pos=' + num + r' v=\S+ state=(\w+) grips=(\d)', s):
    st[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), state=m[5], grips=int(m[6]))
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}


def span(d, a, b):
    return [(g, d[g]) for g in sorted(d) if a <= g <= b]


def moving(w):
    out = []
    for (g0, x0), (g1, x1) in zip(w, w[1:]):
        if math.dist(x0['p'], x1['p']) > 0.01 and x1['state'] == 'ACTIVE':
            out.append((g1, x1))
    return out


def plants(w):
    n = 0
    for (_, x0), (_, x1) in zip(w, w[1:]):
        for a, b in zip(x0['slots'], x1['slots']):
            if a != '-' and b != '-' and not a.endswith('/1.00') and b.endswith('/1.00'):
                n += 1
    return n


for tier, a, b, ceil in (('Copper', 'hanga', 'hangb', -48), ('Netherite', 'nhanga', 'nhangb', -40)):
    w = span(gait, mk[a], mk[b])
    pairs = [x['minpair'] for _, x in w if x['minpair'] is not None]
    full = all(len([q for q in x['slots'] if q != '-']) == 4 for _, x in w)
    ys = [x['p'][1] for _, x in w]
    check(f'G1 {tier} spread under the ceiling at max depth', bool(pairs) and min(pairs) >= SPREAD_MIN and full,
          f'{len(w)} ticks, feet y {ys[0]:.3f} (body {ceil - ys[0] - 0.9:.2f} below the ceiling), min pairwise Grip distance {min(pairs):.3f} (>= {SPREAD_MIN}), 4 Grips every tick {full}')

allsteps = max(x['stepping'] for x in gait.values())
check('G2 steppers', allsteps <= 2, f'max simultaneous steppers {allsteps} over {len(gait)} GAIT ticks (<= 2)')

for name, a, b, need in (('flat walk', 'walka', 'walkb', 6), ('wall climb', 'climba', 'climbb', 2), ('ceiling walk', 'ceila', 'ceilb', 2), ('cliff edge', 'cliffa', 'cliffb', 4)):
    w = span(gait, mk[a], mk[b])
    mv = moving(w)
    minpl = min(x['planted'] for _, x in mv) if mv else -1
    n = plants(w)
    sw = [x for _, x in span(st, mk[a], mk[b]) if x['state'] == 'ACTIVE']
    smin = min(x['grips'] for x in sw) if sw else -1
    states = sorted(set(x['state'] for _, x in w))
    check(f'G3 Held while stepping ({name})', mv and minpl >= 2 and smin >= 2 and n >= need and states == ['ACTIVE'],
          f'{len(mv)} moving ticks, min planted {minpl} (>= 2), {n} plants (>= {need}), server min valid Grips {smin} (>= 2), states {states}')

# W walk animation (v1.1.2): wa = LivingEntity.walkAnimation.speed() each tick (TRACE: own body, RWALK: Dev seen by Dev2)
WA_STILL = 0.02
WA_WALK = 0.1
WA_SETTLE = 10
wa = {int(m[1]): (m[2], float(m[3])) for m in re.finditer(r'TRACE t=\d+ gt=(\d+) .*? state=(\w+) .*? wa=(\S+)', c)}
rwa = {int(m[1]): (m[2], float(m[3])) for m in re.finditer(r'RWALK gt=(\d+) name=Dev state=(\S+) wa=(\S+)', c2)}
w = span(wa, mk['swalka'], mk['swalkb'])
peak = max((x[1] for _, x in w), default=-1)
check('W1 stowed walk animates', w and peak >= WA_WALK and all(x[0] == 'STOWED' for _, x in w), f'{len(w)} ticks, peak walk speed {peak:.3f} (>= {WA_WALK}), states {sorted(set(x[0] for _, x in w))}')
for name, a, b in (('flat walk', 'walka', 'walkb'), ('wall climb', 'climba', 'climbb'), ('ceiling walk', 'ceila', 'ceilb'), ('cliff edge', 'cliffa', 'cliffb')):
    w = [(g, x) for g, x in span(wa, mk[a], mk[b]) if g >= mk[a] + WA_SETTLE]
    peak = max((x[1] for _, x in w), default=-1)
    active = w and all(x[0] == 'ACTIVE' for _, x in w)
    if walk_cfg:
        check(f'W2 walkWhileHeld=true: body walks while Held ({name})', active and peak >= WA_WALK, f'{len(w)} ticks, peak walk speed {peak:.3f} (>= {WA_WALK})')
    else:
        check(f'W2 body still while Held ({name})', active and peak <= WA_STILL, f'{len(w)} ticks, peak walk speed {peak:.3f} (<= {WA_STILL})')
w = [(g, x) for g, x in span(rwa, mk['walka'], mk['walkb']) if g >= mk['walka'] + WA_SETTLE]
peak = max((x[1] for _, x in w), default=-1)
active = w and all(x[0] == 'ACTIVE' for _, x in w)
if walk_cfg:
    check('W3 walkWhileHeld=true: Dev2 sees Dev walk while Held', active and peak >= WA_WALK, f'{len(w)} RWALK ticks, peak {peak:.3f} (>= {WA_WALK})')
else:
    check('W3 Dev2 sees Dev still while Held (flat walk)', active and peak <= WA_STILL, f'{len(w)} RWALK ticks, peak {peak:.3f} (<= {WA_STILL})')

for who, log in (('Dev', c), ('Dev2', c2)):
    m = re.search(r'CPERF p6_\S*two_rigs frames=(\d+) avg_us_per_frame=(\S+) max_us_per_frame=(\S+) fps=(\d+)', log)
    if not m:
        check(f'P1 frame cost ({who})', False, 'no CPERF line')
        continue
    check(f'P1 frame cost ({who}, two Rigs in view)', float(m[2]) < PERF_MAX_US,
          f'{m[1]} frames, Tentacle drawing avg {m[2]} us/frame, max {m[3]} us, fps {m[4]} (avg < {PERF_MAX_US:.0f} us)')

m = re.search(r'CSOUND p6 own_gait plants=(\d+) steps=(\d+) own_plants_near=(\d+) own_plant_sounds=(\d+)', c)
heard = re.search(r'CSOUND p6 heard=\{([^}]*)\}', c)
check('S1 own plant sounds', m and int(m[3]) > 0 and m[3] == m[4] and abs(int(m[1]) - int(m[3])) <= 2,
      f'gait plants {m[1]}, drawn plants {m[3]}, plant sounds {m[4]}; heard {heard[1] if heard else "-"}' if m else 'no CSOUND line')
m2 = re.search(r'CSOUND p6_d2_end own_gait plants=\d+ steps=\d+ own_plants_near=\d+ own_plant_sounds=\d+ other_plants_near=(\d+) other_plant_sounds=(\d+)', c2)
heard2 = re.search(r'CSOUND p6_d2_end heard=\{([^}]*)\}', c2)
slime2 = int(re.search(r'create:slime_added=(\d+)', heard2[1])[1]) if heard2 and 'slime_added' in heard2[1] else 0
check('S2 Dev2 hears Dev', m2 and int(m2[1]) > 0 and m2[1] == m2[2] and slime2 >= int(m2[2]),
      f"Dev's plants seen near Dev2 {m2[1]}, played {m2[2]}, slime sounds reaching Dev2's engine {slime2}" if m2 else 'no CSOUND line')
h = heard[1] if heard else ''
check('S3 piston + steam', 'piston.extend' in h and 'piston.contract' in h and 'create:steam' in h, f'Dev heard {h}')

exc = [l for l in (c + c2 + s).splitlines() if 'Exception' in l and 'ApiStatus' not in l and 'authlib' not in l and 'Error loading class: xaero/' not in l]
check('E exceptions', not exc, f'{len(exc)} exception lines' + (f': {exc[0][:200]}' if exc else ''))
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
