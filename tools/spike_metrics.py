"""Per-scenario numbers from a run_all.sh log set. usage: spike_metrics.py tools/logs/<tag>"""
import re, sys
pre = sys.argv[1]
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'

def load(run):
    c = open(f'{pre}-{run}-client.log', errors='replace').read()
    s = open(f'{pre}-{run}-server.log', errors='replace').read()
    r = open(f'{pre}-{run}-rcon.txt', errors='replace').read()
    tr = {}
    for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num + r' d=\S+ cmd=\S+ held=(\d) state=(\w+) fly=(\w+) mayfly=(\w+) press=(\w+)', c):
        tr[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), held=int(m[5]), state=m[6], fly=m[7] == 'true', press=m[9] == 'true')
    st = {}
    for m in re.finditer(r'STRACE gt=(\d+) Dev pos=' + num + r' v=\S+ state=(\w+) grips=(\d) cf=(\S+) flying=(\w+) fall=(\S+) hp=(\S+)', s):
        st[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), state=m[5], cf=float(m[7]), flying=m[8] == 'true', hp=float(m[10]))
    marks = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
    return tr, st, marks, r

def span(d, a, b):
    return [d[g] for g in sorted(d) if a <= g <= b]

tr, st, mk, rc = load('main')
out = []
w = span(tr, mk['s1a'], mk['s1b'])
moving = [x for x in w]
dx = w[-1]['p'][0] - w[0]['p'][0]
out.append(f"S1 flat walk: {dx:.2f} blocks in 60 ticks W = {dx / 3:.2f} b/s, y change {w[-1]['p'][1] - w[0]['p'][1]:+.3f}, min held {min(x['held'] for x in w)}")
w = [(g, tr[g]) for g in sorted(tr) if mk['s2a'] <= g <= mk['s2b']]
y0 = w[0][1]['p'][1]
hit = next((g for g, x in w if x['p'][1] >= -50.0), None)
out.append(f"S2 wall climb: start y {y0:.2f}, target y>=-50 reached after {(hit - mk['s2a']) / 20 if hit else float('nan'):.2f} s, top y {max(x['p'][1] for g, x in w):.2f} after 3 s, min held {min(x['held'] for g, x in w)}")
w = span(tr, mk['s3a'], mk['s3b']); sw = span(st, mk['s3a'], mk['s3b'])
ys = [x['p'][1] for x in w]
out.append(f"S3 ceiling hang idle: {(mk['s3b'] - mk['s3a']) / 20:.1f} s, client y range {max(ys) - min(ys):.4f}, held min {min(x['held'] for x in w)}, server cf min {min(x['cf'] for x in sw):.0f}, server state {set(x['state'] for x in sw)}, feet {ys[0]:.3f} (ceiling -48, floor -60)")
w = span(tr, mk['s4a'], mk['s4b'])
wall = [x['p'] for x in w if x['p'][0] > 141 and x['p'][1] < -50.5]
pinned = [p for p in wall if abs(p[0] - 145.0) < 0.01]
out.append(f"S4 cliff: over edge x>141 and below top: {len(wall)} ticks, x max over wall before floor {max(p[0] for p in wall if p[1] > -57):.3f}, ticks pinned at x=145.0 (Reach from wall) descending: {len(pinned)} (y {max(p[1] for p in pinned):.2f} -> {min(p[1] for p in pinned):.2f}), states {set(x['state'] for x in w)}, end {w[-1]['p'][0]:.2f},{w[-1]['p'][1]:.2f}")
w = span(tr, mk['s5a'], mk['s5b'])
body_y = w[-1]['p'][1] + 0.9
pred = 171 + (16 - (body_y + 50) ** 2) ** 0.5
out.append(f"S5 gap: stop x {w[-1]['p'][0]:.3f}, predicted {pred:.3f} (edge x=171, top -50, body y {body_y:.2f}, Reach 4), y kept {min(x['p'][1] for x in w):.3f}, states {set(x['state'] for x in w)}")
w = span(tr, mk['s6a'], mk['s6b']); sw = span(st, mk['s6a'], mk['s6b'])
states = []
for x in w:
    if not states or states[-1] != x['state']: states.append(x['state'])
fall_ticks = sum(1 for x in w if x['state'] == 'FALLING')
out.append(f"S6 grips broken: states {' -> '.join(states)}, Falling {fall_ticks} ticks, fell {w[0]['p'][1] - w[-1]['p'][1]:.2f} blocks, re-grab feet y {w[-1]['p'][1]:.3f} (floor -60), server hp min {min(x['hp'] for x in sw):.1f}, cf during Falling {sorted(set(x['cf'] for x in sw if x['state'] == 'FALLING'))}")
w = span(tr, mk['s7a'], mk['s7b']); sw = span(st, mk['s7top'], mk['s7idle'])
top = max(x['p'][1] for x in w)
out.append(f"S7 Space/Shift: max feet y {top:.3f} = {top + 60:.2f} above floor (body {top + 60.9:.2f} = Reach), idle at top {(mk['s7idle'] - mk['s7top']) / 20:.1f} s with server cf {min(x['cf'] for x in sw):.0f}, Shift end y {w[-1]['p'][1]:.3f}")
w = span(tr, mk['s8on'], mk['s8off'])
st8 = []
for x in w:
    if not st8 or st8[-1] != x['state']: st8.append(x['state'])
fl = re.findall(r'abilities.flying => Dev has the following entity data: (\w+)', rc)
out.append(f"S8 Double Jump on/off: client states {' -> '.join(st8)}, client fly ever true: {any(x['fly'] for x in w)}, server abilities.flying samples {fl}")
w = span(tr, mk['swb'], mk['swc'])
ys = [x['p'][1] for x in w]
out.append(f"W underwater: climbed to feet {mk and w[0]['p'][1]:.3f} (water top -51), idle {(mk['swc'] - mk['swb']) / 20:.1f} s y range {max(ys) - min(ys):.4f}, held min {min(x['held'] for x in w)}")
print('\n'.join(out))

tr, st, mk, rc = load('life')
print('LIFE mayfly samples in order:', re.findall(r'abilities.mayfly => Dev has the following entity data: (\w+)', rc))
for a, b, name in (('l1hang', 'l1after', 'L1 logout/rejoin'), ('l2hang', 'l2after', 'L2 unequip'), ('l3hang', 'l3after', 'L3 death')):
    sw = span(st, mk[a], mk[b])
    print(f"{name}: server hp min {min(x['hp'] for x in sw):.1f}, end pos {sw[-1]['p']}, cf end {sw[-1]['cf']:.0f}, flying ever {any(x['flying'] for x in sw)}")
sw = span(st, mk['l4after'] - 60, mk['l4after'] + 40)
s5 = span(st, mk['l4after'] + 1, mk['l5after'])
print(f"L5 forged Active, no Grips: server states {sorted(set(x['state'] for x in s5))}, cf max while not client-carried {max(x['cf'] for g, x in zip(sorted(st), st.values()) if mk['l4after'] < g <= mk['l5after'] and tr.get(g, {}).get('state') == 'STOWED'):.0f}")
g6 = [g for g in sorted(st) if mk['l5after'] < g <= mk['l6after']]
on = next(g for a, g in zip(g6, g6[1:]) if st[g]['cf'] and not st[a]['cf'])
off = next(g for g in g6 if g > on and not st[g]['cf'])
print(f"L6b one forged Active with Grips, then silence: float on at gt {on}, off after {off - on} ticks")
srv = open(f'{pre}-life-server.log', errors='replace').read()
kick = int(re.findall(r'STRACE gt=(\d+)(?:(?!STRACE).)*?Flying is not enabled', srv, re.S)[-1])
up = next(g for g in sorted(st) if g > mk['l6after'] and st[g]['state'] != 'FALLING')
print(f"L7 float fix off: kicked {kick - up} ticks after the carry started, cf max {max(st[g]['cf'] for g in sorted(st) if up <= g <= kick):.0f}")
tr, st, mk, rc = load('creative')
print('CREATIVE flying samples:', re.findall(r'abilities.flying => Dev has the following entity data: (\w+)', rc), 'mayfly samples:', re.findall(r'abilities.mayfly => Dev has the following entity data: (\w+)', rc))
