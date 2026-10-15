"""Sneak suite: while the Rig is out with Shift held, client and server see a sneak but no crouch, and sneak + use works.
usage: sneak_metrics.py <log prefix>"""
import re, sys
pre = sys.argv[1]
client = open(pre + '-client.log', errors='replace').read()
server = open(pre + '-server.log', errors='replace').read()
out = re.compile(r'state=(?:ACTIVE|SETTING_DOWN|FALLING|CRAWL)')
c = [l for l in client.splitlines() if 'TRACE t=' in l and out.search(l) and 'sneak=true' in l]
s = [l for l in server.splitlines() if 'STRACE' in l and ' Dev ' in l and out.search(l) and 'sneak=true' in l]
crouch = [l for l in c if 'crouch=true' in l or 'pose=CROUCHING' in l]
eyes = sorted({m for l in c for m in re.findall(r'eye=([\d.]+)', l)})
scrouch = [l for l in s if 'pose=CROUCHING' in l]
ys = [float(m) for l in c for m in re.findall(r'pos=-?[\d.]+,(-?[\d.]+),', l)]
print(f'client ticks sneaking with the Rig out: {len(c)}, crouching: {len(crouch)}, eye heights: {eyes}')
print(f'server ticks sneaking with the Rig out: {len(s)}, crouching: {len(scrouch)}')
if ys: print(f'feet while sneaking: {max(ys):.3f} -> {min(ys):.3f} (Shift moves down)')
for l in re.findall(r'SNEAKTEST .*', server): print(l)
for l in re.findall(r'SCREEN \S+', client): print(l)
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
mk = {m[0]: (int(m[1]), tuple(map(float, m[2:5]))) for m in re.findall(r'CPOS (\S+) gt=(\d+) pos=' + num, client)}
tr = {int(m[0]): tuple(map(float, m[1:4])) for m in re.findall(r'TRACE t=\d+ gt=(\d+) pos=' + num, client)}
checks = []
def check(name, ok, detail):
    checks.append(ok)
    print(('PASS ' if ok else 'FAIL ') + name + ': ' + detail)
if 'ea' in mk and 'eb' in mk:
    x0, x1 = mk['ea'][1][0], mk['eb'][1][0]
    check('E no edge hold: forward + Shift carries off the platform edge (x 6013)', x1 > 6013.5, f'x {x0:.2f} -> {x1:.2f}, feet {mk["eb"][1][1]:.2f}')
else:
    check('E marks', False, 'missing')
if 'la' in mk and 'lb' in mk:
    y0, y1 = mk['la'][1][1], mk['lb'][1][1]
    check('L no ladder stop: Shift moves down the ladder', y0 - y1 > 2, f'feet {y0:.2f} -> {y1:.2f}')
else:
    check('L marks', False, 'missing')
if all(k in mk for k in ('ua', 'ub', 'uc')):
    g0, g1, g2 = mk['ua'][0], mk['ub'][0], mk['uc'][0]
    down = [tr[g - 1][1] - tr[g][1] for g in range(g0 + 1, g1 + 1) if g in tr and g - 1 in tr]
    idle = [abs(tr[g][1] - tr[g - 1][1]) for g in range(g1 + 5, g2 + 1) if g in tr and g - 1 in tr]
    check('U no extra sinking with Shift under water (<= Rig step 0.28 + 0.01)', down and max(down) <= 0.29 and mk['ua'][1][1] - mk['ub'][1][1] > 1,
          f'feet {mk["ua"][1][1]:.2f} -> {mk["ub"][1][1]:.2f}, max per tick {max(down, default=0):.3f}')
    check('U no sinking at idle under water', idle and max(idle) < 0.01, f'max per tick {max(idle, default=0):.4f} over {len(idle)} ticks')
else:
    check('U marks', False, 'missing')
ok = all(checks) and c and s and not crouch and not scrouch and 'did not' not in server and 'not placed' not in server and ys and min(ys) < max(ys) - 1
print('PASS' if ok else 'FAIL')
