"""movemode suite: the Rig Settings row Movement mode (Default / Creative flight) writes the client config movement.forward.
usage: movemode_metrics.py <log prefix> [run dir, default run]"""
import re, sys, os
pre = sys.argv[1]
run = sys.argv[2] if len(sys.argv) > 2 else 'run'
client = open(pre + '-client.log', errors='replace').read()
checks = []
def check(name, ok, detail):
    checks.append(ok)
    print(('PASS ' if ok else 'FAIL ') + name + ': ' + detail)
mv = {m[0]: (m[1], m[2]) for m in re.findall(r'CMOVE (\S+) screen=(\S+) config=(\S+)', client)}
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
mk = {m[0]: (tuple(map(float, m[1:4])), m[4]) for m in re.findall(r'CPOS (\S+) gt=\d+ pos=' + num + r' state=(\S+)', client)}
def want(tag, screen, config):
    got = mv.get(tag)
    check(f'{tag}: screen {screen}, config {config}', got == (screen, config), f'got {got}')
want('m0', '-', 'LOOK')
want('m1open', 'LOOK', 'LOOK')
want('m1edit', 'FLAT', 'LOOK')
want('m1closed', '-', 'FLAT')
want('m2open', 'FLAT', 'FLAT')
want('m2closed', '-', 'LOOK')
want('m3closed', '-', 'FLAT')
def walk(name, a, b, cond, rule):
    if a not in mk or b not in mk:
        check(name, False, f'marks {a}/{b} missing'); return
    (x0, y0, z0), s0 = mk[a]; (x1, y1, z1), s1 = mk[b]
    check(name, cond(y1 - y0, z1 - z0) and s0 == s1 == 'ACTIVE', f'feet {y0:.2f} -> {y1:.2f}, z {z0:.2f} -> {z1:.2f}, {s0}/{s1} ({rule})')
walk('F Creative flight: 60 down + W stays level', 'fa', 'fb', lambda dy, dz: abs(dy) < 0.05 and dz > 3.0, '|dy| < 0.05, dz > 3, Active')
walk('L Default: 60 down + W goes down', 'la', 'lb', lambda dy, dz: dy < -1.0 and dz > 1.0, 'dy < -1, dz > 1, Active')
toml = os.path.join(run, 'config', 'tentaworks-client.toml')
saved = re.search(r'forward\s*=\s*"(\w+)"', open(toml).read()) if os.path.exists(toml) else None
check('S the client config file was saved', bool(saved) and saved[1] == 'FLAT', f'{toml}: forward = {saved[1] if saved else None} (want FLAT)')
print('PASS' if all(checks) else 'FAIL')
