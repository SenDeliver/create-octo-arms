"""slider suite: the Rig Settings Speed row shows the Speed with Swift; a full slider reads the top Speed of the limits line.
usage: slider_metrics.py <log prefix> [swiftPerLevel] [speed.max]   (defaults 0.15 and 8.0, the mod defaults)"""
import re, sys
pre = sys.argv[1]
per = float(sys.argv[2]) if len(sys.argv) > 2 else 0.15
top = float(sys.argv[3]) if len(sys.argv) > 3 else 8.0
client = open(pre + '-client.log', errors='replace').read()
checks = []
def check(name, ok, detail):
    checks.append(ok)
    print(('PASS ' if ok else 'FAIL ') + name + ': ' + detail)
rows = {m[0]: (float(m[1]), float(m[2])) for m in re.findall(r'CSPEED (\S+) row=([\d.]+) b/s limits=Reach [\d.]+, top Speed ([\d.]+) b/s', client)}
setting = {m[0]: float(m[1]) for m in re.findall(r'CRIG (\S+) screen settings=RigSettings\[standingHeight=\S+, speed=([\d.]+),', client)}
walk = min((v for k, v in setting.items() if k.endswith('min')), default=None)
for case, swift in (('c0', 0), ('c1', 1), ('c2', 2), ('n0', 0), ('n2', 2)):
    f = 1 + per * swift
    want_top = round(top * f, 1)
    mx, mn = rows.get(case + 'max'), rows.get(case + 'min')
    if not mx or not mn:
        check(case + ' screen read', False, 'CSPEED lines missing'); continue
    check(case + ' full slider row = top Speed line', mx[0] == mx[1] == want_top and setting.get(case + 'max') == top,
          f'row {mx[0]} b/s, line {mx[1]} b/s, want {want_top} (Swift {swift}, x{f:.3f}); stored setting {setting.get(case + "max")} (want {top}, no Swift)')
    want_min = round(walk * f, 1) if walk is not None else None
    check(case + ' empty slider row = walk Speed with Swift', want_min is not None and abs(mn[0] - want_min) <= 0.1,
          f'row {mn[0]} b/s, stored {setting.get(case + "min")}, want {want_min}')
saved = re.findall(r'CRIG (c0saved|c2saved) slot=mainhand item=\S+ settings=RigSettings\[standingHeight=\S+, speed=([\d.]+),', client)
check('S the saved setting stays without Swift', len(saved) == 2 and all(float(s) == top for _, s in saved), f'{saved} (want {top})')
print('PASS' if all(checks) else 'FAIL')
