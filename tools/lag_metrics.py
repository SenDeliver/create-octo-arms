"""lag suite: a wearer at top Speed on a lagging server keeps the Rig; forged speed is still stowed (speed.check on).
usage: lag_metrics.py <log prefix> [check-off]   check-off: the run had TW_SPEED_CHECK=false, the forged slide must NOT stow"""
import re, sys
pre = sys.argv[1]
off = len(sys.argv) > 2 and sys.argv[2] == 'check-off'
client = open(pre + '-client.log', errors='replace').read()
server = open(pre + '-server.log', errors='replace').read()
checks = []
def check(name, ok, detail):
    checks.append(ok)
    print(('PASS ' if ok else 'FAIL ') + name + ': ' + detail)
marks = {m[0]: (int(m[1]), m[2]) for m in re.findall(r'CPOS (\S+) gt=(\d+) pos=\S+ state=(\S+)', client)}
zs = {m[0]: float(m[1]) for m in re.findall(r'CPOS (\S+) gt=\d+ pos=-?[\d.]+,-?[\d.]+,(-?[\d.]+) ', client)}
cr = re.search(r'CREACH rig reach=(\S+) top=(\S+) thrift=\S+ swift=(\S+)', client)
if cr and 'w0a' in marks and 'w0b' in marks:
    top = float(cr[2])
    secs = (marks['w0b'][0] - marks['w0a'][0]) / 20.0
    v = (zs['w0b'] - zs['w0a']) / secs
    check('R the worn Rig really runs at its top Speed (W0, no lag)', 0.9 * top <= v <= 1.1 * top,
          f'Reach {cr[1]}, top Speed {top} b/s (Swift x{cr[3]}), measured {v:.2f} b/s over {secs:.1f} s')
else:
    check('R the worn Rig really runs at its top Speed (W0, no lag)', False, 'CREACH rig line or W0 marks missing')
lag_on = server.find('LAG on ms=80')
burst = server.find('burst 5')
walk = server[lag_on:burst] if lag_on >= 0 and burst >= 0 else ''
forged = server[burst:] if burst >= 0 else ''
check('setup: lag and forged phases ran', lag_on >= 0 and burst >= 0 and server.count('LAG off') >= 4,
      f"LAG on at {lag_on}, burst at {burst}, {server.count('LAG off')} LAG off (>= 4)")
for a, b, what in (('w0a', 'w0b', 'W0 no lag'), ('w1a', 'w1b', 'W1 sustained lag'), ('w2a', 'w2b', 'W2 1.5 s stalls')):
    ok = a in marks and b in marks and marks[a][1] == 'ACTIVE' and marks[b][1] == 'ACTIVE'
    if ok:
        states = [m[1] for m in re.findall(r'TRACE t=\d+ gt=(\d+) .*? state=(\S+)', client) if marks[a][0] <= int(m[0]) <= marks[b][0]]
        stowed = sum(s == 'STOWED' for s in states)
        ok = stowed == 0 and len(states) > 0
        detail = f'{len(states)} client ticks, {stowed} Stowed'
    else:
        detail = f"marks {a}/{b}: {marks.get(a)} {marks.get(b)}"
    check(what + ': Rig stays Active walking at top Speed', ok, detail)
    dz = zs.get(b, 0) - zs.get(a, 0)
    check(what + ': really walked (a moved mouse turns the camera)', dz >= 15, f'z {zs.get(a)} -> {zs.get(b)}, {dz:.1f} blocks (>= 15)')
n_walk = walk.count('faster than its top Speed')
check('S no speed stow while walking (W1, W2)', n_walk == 0 and 'faster than its top Speed' not in server[:max(lag_on, 0)], f'{n_walk} "faster than" lines during the lag phases')
n_forged = forged.count('faster than its top Speed')
if off:
    check('A speed.check=false: forged 20 b/s slide (5 packets per tick) is not stowed', n_forged == 0, f'{n_forged} "faster than" lines')
else:
    check('A forged 20 b/s slide with 5 packets per tick is still stowed', n_forged >= 1, f'{n_forged} "faster than" lines (>= 1)')
print('INFO vanilla: ' + str(len(re.findall(r'moved too quickly', server))) + ' "moved too quickly", ' + str(len(re.findall(r'moved wrongly', server))) + ' "moved wrongly", ' + str(len(re.findall(r'kicked|lost connection', server, re.I))) + ' kick/lost connection')
print('PASS' if all(checks) else 'FAIL')
