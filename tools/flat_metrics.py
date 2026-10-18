"""Flat suite (issue #18): client config movement.forward. look: W follows the camera pitch. flat: W/S level only, Space/Shift change the height.
usage: flat_metrics.py <log prefix>"""
import re, sys
pre = sys.argv[1]
client = open(pre + '-client.log', errors='replace').read()
server = open(pre + '-server.log', errors='replace').read()
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
states = dict(re.findall(r'CPOS (\S+) .*? state=(\S+)', client))
mk = {m[0]: (int(m[1]), tuple(map(float, m[2:5]))) for m in re.findall(r'CPOS (\S+) gt=(\d+) pos=' + num, client)}
checks = []
def check(name, ok, detail):
    checks.append(ok)
    print(('PASS ' if ok else 'FAIL ') + name + ': ' + detail)
def pair(a, b):
    if a not in mk or b not in mk: return None
    (x0, y0, z0), (x1, y1, z1) = mk[a][1], mk[b][1]
    return y1 - y0, z1 - z0, f'feet {y0:.2f} -> {y1:.2f}, z {z0:.2f} -> {z1:.2f}'
def run(name, a, b, cond, want):
    p = pair(a, b)
    if p is None: check(name, False, f'marks {a}/{b} missing'); return
    dy, dz, d = p
    check(name, cond(dy, dz), d + ' (' + want + ')')
run('L look: 60 down + W goes down to the floor', 'la', 'lb', lambda dy, dz: dy < -1.0 and dz > 1.0, 'dy < -1, dz > 1')
run('F1 flat: 60 down + W stays level', 'fa', 'fb', lambda dy, dz: abs(dy) < 0.05 and dz > 3.0 and mk['fa'][1][1] > -59.5, '|dy| < 0.05, dz > 3, lifted start')
run('F2 flat: 60 up + W stays level', 'fc', 'fd', lambda dy, dz: abs(dy) < 0.05 and dz > 3.0, '|dy| < 0.05, dz > 3')
run('F3 flat: Space still climbs', 'fe', 'ff', lambda dy, dz: dy > 0.5 and abs(dz) < 0.05, 'dy > 0.5, |dz| < 0.05')
run('F4 flat: Shift still descends', 'ff', 'fg', lambda dy, dz: dy < -0.5 and abs(dz) < 0.05, 'dy < -0.5, |dz| < 0.05')
run('F5 flat: 60 down + S backs up level', 'fh', 'fi', lambda dy, dz: abs(dy) < 0.05 and dz < -3.0, '|dy| < 0.05, dz < -3')
active = [k for k in ('la', 'fa', 'fb', 'fc', 'fd', 'fe', 'ff', 'fg', 'fh', 'fi') if states.get(k) != 'ACTIVE']
check('A Rig Active at every mark', not active, 'not Active at ' + ', '.join(active) if active else 'all Active')
kicks = len(re.findall(r'moved too quickly|moved wrongly|moves faster than|kicked', server))
check('S server: no kick, no speed warning', kicks == 0, f'{kicks} hits')
print('PASS' if all(checks) else 'FAIL')
