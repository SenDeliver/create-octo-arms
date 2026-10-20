"""Cave suite checks (the 2026-10-20 clipping report): on an 11x11 platform (top y -49) with a one-high cave under it, a low body
(Standing Height 0) keeps every Grip on or above the platform top: the cave floor (top -51) and cave walls (y -51..-50) under
it are out of sight. Idle, then walking while looking down (the body stays at its lowest). Netherite (N) and Copper (C).
usage: cave_metrics.py tools/logs/<tag>-cave"""
import re, sys
pre = sys.argv[1]
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
FLOOR = -49.0
tr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num + r' d=\S+ cmd=\S+ held=(\d) state=(\w+)', c):
    tr[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), held=int(m[5]), state=m[6])
gait = {}
for m in re.finditer(r'GAIT gt=(\d+) state=(\w+) pos=' + num + r' planted=(\d) stepping=(\d) minpair=(\S+) slots=(\S+)', c):
    slots = []
    for part in m[9].split(';'):
        g = re.match(r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)@(\w)/([\d.]+)', part)
        slots.append(None if g is None else dict(p=tuple(map(float, g.group(1, 2, 3))), face=g[4], phase=float(g[5])))
    gait[int(m[1])] = dict(state=m[2], slots=slots)
st = {}
for m in re.finditer(r'STRACE gt=(\d+) Dev pos=' + num + r' v=\S+ state=(\w+) grips=(\d)', s):
    st[int(m[1])] = dict(state=m[5], grips=int(m[6]))
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
fails = []


def check(ok, name, text):
    if not ok: fails.append(name)
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")


def span(d, a, b):
    return [d[g] for g in sorted(d) if mk[a] <= g <= mk[b]]


def states(w):
    out = []
    for x in w:
        if not out or out[-1] != x['state']: out.append(x['state'])
    return ' -> '.join(out)


for tag, a, b in (('N netherite low on the cave roof', 'na', 'nb'), ('C copper low on the cave roof', 'ca', 'cb')):
    if a not in mk or b not in mk:
        check(False, tag, 'missing marks'); continue
    w = span(tr, a, b)
    g = span(gait, a, b)
    pts = [sl for x in g for sl in x['slots'] if sl]
    low = min(p['p'][1] for p in pts) if pts else float('nan')
    under = sum(1 for p in pts if p['p'][1] < FLOOR - 0.01)
    two = sum(1 for x in g if sum(1 for sl in x['slots'] if sl) >= 2)
    steps = sum(1 for x in g if any(sl and sl['phase'] < 1 for sl in x['slots']))
    srv = span(st, a, b)
    srv2 = sum(1 for x in srv if x['grips'] >= 2)
    feet = max(x['p'][1] for x in w)
    ok = pts and under == 0 and two >= 0.95 * len(g) and steps > 0 and states(w) == 'ACTIVE' and srv and srv2 >= 0.95 * len(srv) and feet <= FLOOR + 0.3
    check(ok, tag, f"lowest Grip y {low:.2f} (floor top {FLOOR}; the cave floor top is -51, its walls -51..-50), {under} of {len(pts)} Grip points under the floor (want 0), "
                   f">= 2 Grips on {two}/{len(g)} gait ticks (>= 95 %), stepping ticks {steps} (> 0), server >= 2 valid Grips on {srv2}/{len(srv)} ticks (>= 95 %), "
                   f"max feet {feet:.2f} (<= {FLOOR + 0.3}: body low), states {states(w)}")
forced = s.count('forcing Falling')
check(forced == 0, 'S server never forces Falling', f'{forced} forcing lines (want 0: client and server agree on sight)')
exc = [l for l in c.splitlines() if 'Exception' in l and 'ApiStatus' not in l and 'neoforge-client.new.tmp.toml' not in l]
check(not exc, 'E exceptions', f'{len(exc)} exception lines')
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
