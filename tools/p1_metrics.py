"""Phase-1 numbers from a p1 suite log set. usage: p1_metrics.py tools/logs/<tag>-p1"""
import re, sys
pre = sys.argv[1]
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
r = open(f'{pre}-rcon.txt', errors='replace').read()
tr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num + r' d=\S+ cmd=\S+ held=(\d) state=(\w+)', c):
    tr[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), held=int(m[5]), state=m[6])
st = {}
for m in re.finditer(r'STRACE gt=(\d+) Dev pos=' + num + r' v=\S+ state=(\w+) grips=(\d) cf=(\S+) flying=(\w+) fall=(\S+) hp=(\S+)', s):
    st[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), state=m[5], grips=int(m[6]), cf=float(m[7]), hp=float(m[10]))
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}

def span(d, a, b):
    return [d[g] for g in sorted(d) if a <= g <= b]

def states(w):
    out = []
    for x in w:
        if not out or out[-1] != x['state']: out.append(x['state'])
    return ' -> '.join(out)

w = span(tr, mk['n1a'], mk['n1idle']); sw = span(st, mk['n1top'], mk['n1idle'])
top = max(x['p'][1] for x in w)
print(f"N1 netherite Space to max: max feet {top + 60:.2f} above floor (body {top + 60.9:.2f}; copper S7 = 3.10), idle {(mk['n1idle'] - mk['n1top']) / 20:.1f} s server states {sorted(set(x['state'] for x in sw))} cf min {min(x['cf'] for x in sw):.0f}")
w = span(tr, mk['n2a'] + 30, mk['n2hang']); sw = span(st, mk['n2a'] + 30, mk['n2hang'])
ys = [x['p'][1] for x in w]
print(f"N2 netherite ceiling-only hang: feet {ys[-1]:.3f} body {ys[-1] + 0.9:.2f} (ceiling face -41 = {-41 - ys[-1] - 0.9:.2f} above body, ground -60), y range {max(ys) - min(ys):.4f}, client {states(w)}, server {states(sw)}, grips min {min(x['grips'] for x in sw)}, cf min {min(x['cf'] for x in sw):.0f}")
w = span(tr, mk['n2hang'] + 1, mk['n2fell']); sw = span(st, mk['n2hang'] + 1, mk['n2fell'])
print(f"N2 copper control (Reach 4): client {states(w)}, fell to feet {w[-1]['p'][1]:.2f}, server {states(sw)}")
for name, key in (('placed rig BE', r'data get block 471 -60 0 => .*?has the following block data: (\{.*)'),):
    for m in re.findall(key, r)[:3]:
        print(f"{name}: " + re.sub(r'\s+', ' ', m)[:300])
print('Air samples (rig 471,-60,0 / Create copper backtank 471,-60,-4):', re.findall(r'data get block 471 -60 (-?\d) Air => .*?data: (\d+)', r))
print('\n'.join(re.findall(r'BACKTANK .*', c)))
print('motor ScrollValue:', re.findall(r'ScrollValue => .*?data: (\d+)', r))
inv = re.findall(r'data get entity Dev Inventory => (.*)', r)
for i, line in enumerate(inv):
    print(f"Inventory sample {i}: {line[:400]}")
item = re.findall(r'data get entity @e\[type=item.*? => (.*)', r)
print('dropped item:', item[:1])
print('Dev Air under water:', re.findall(r'data get entity Dev Air => .*?data: (\d+)', r))
