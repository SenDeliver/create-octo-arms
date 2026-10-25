"""Spectator suite: a spectator wearing a Rig keeps flying and the Rig stays Stowed whatever jump is pressed; survival gets the Rig back.
usage: spec_metrics.py <log prefix>"""
import re, sys
pre = sys.argv[1]
client = open(pre + '-client.log', errors='replace').read()
server = open(pre + '-server.log', errors='replace').read()
rcon = open(pre + '-rcon.txt', errors='replace').read()
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', client)}
states = dict(re.findall(r'CPOS (\S+) .*? state=(\S+)', client))
tr = {int(m[0]): (m[1], m[2], m[3]) for m in re.findall(r'TRACE t=\d+ gt=(\d+) .*? state=(\w+) fly=(\w+) mayfly=(\w+)', client)}
st = {int(m[0]): (m[1], m[2]) for m in re.findall(r'STRACE gt=(\d+) Dev pos=\S+ v=\S+ state=(\w+) grips=\d cf=\S+ flying=(\w+)', server)}
flying = re.findall(r'abilities\.flying => Dev has the following entity data: (\w+)', rcon)
modes = re.findall(r'playerGameType => Dev has the following entity data: (\w+)', rcon)
checks = []
def check(name, ok, detail):
    checks.append(ok)
    print(('PASS ' if ok else 'FAIL ') + name + ': ' + detail)
def window(a, b, src):
    if a not in mk or b not in mk: return None
    return [v for gt, v in src.items() if mk[a] <= gt <= mk[b]]
check('S0 setup: Rig Active before spectator', states.get('s0') == 'ACTIVE', 'state at s0 ' + str(states.get('s0')))
w = window('s1', 's4', tr)
bad = [v for v in w] if w is None else [v for v in w if v != ('STOWED', 'true', 'true')]
check('S1 client: spectator stays Stowed and flying through Double Jump, three taps, held jump', w is not None and len(w) >= 60 and not bad,
      f'{len(w or [])} ticks, {len(bad)} not (STOWED, fly, mayfly)' + (f', first {bad[0]}' if bad else ''))
w = window('s1', 's4', st)
bad = [v for v in w] if w is None else [v for v in w if v != ('STOWED', 'true')]
check('S2 server: spectator Stowed and flying', w is not None and len(w) >= 60 and not bad, f'{len(w or [])} ticks, {len(bad)} not (STOWED, flying)')
check('S3 rcon: spectator (3) and flying at s1 and s4', flying[:2] == ['1b', '1b'] and modes[:2] == ['3', '3'], f'flying {flying[:2]}, game type {modes[:2]}')
check('S4 survival: Double Jump activates again', states.get('s6') == 'ACTIVE' and len(flying) >= 3 and flying[2] == '0b',
      f'state at s6 {states.get("s6")}, flying {flying[2:3]}, game type {modes[2:3]}')
kicks = len(re.findall(r'moved too quickly|moved wrongly|moves faster than|kicked', server))
check('S server: no kick, no speed warning', kicks == 0, f'{kicks} hits')
print('PASS' if all(checks) else 'FAIL')
