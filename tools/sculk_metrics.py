"""Sculk suite: Silent Step. A Sculk Sensor hears a Stowed walk and a plain carried Rig, not a carried Rig with Silent Step.
usage: sculk_metrics.py <log prefix>"""
import re, sys
pre = sys.argv[1]
client = open(pre + '-client.log', errors='replace').read()
server = open(pre + '-server.log', errors='replace').read()
freq = {}
for m in re.finditer(r'SNBT (\S+) gt=\d+ \S+ (.*)', server):
    f = re.search(r'last_vibration_frequency:\s*(\d+)', m[2])
    freq[m[1]] = int(f[1]) if f else None
digs = {m[0]: (m[1] == 'true', m[2]) for m in re.findall(r'CDIG (\S+) gt0=\d+ gt1=\d+ ticks=\d+ broken=(\w+) ground=\w+ state=(\w+)', client)}
events = re.findall(r'SGEVENT gt=\d+ Dev event=(\S+) pos=\(.*?\) dampens=(\w+) state=(\w+)', server)
checks = []
def check(name, ok, detail):
    checks.append(ok)
    print(('PASS ' if ok else 'FAIL ') + name + ': ' + detail)
def window(name, a, b, heard):
    fa, fb = freq.get(a), freq.get(b)
    ok = fa == 0 and fb is not None and ((fb > 0) if heard else (fb == 0))
    check(name, ok, f'frequency {fa} -> {fb} (' + ('heard' if heard else 'silent') + ' expected)')
window('P control: Stowed walk is heard', 'p0', 'p1', True)
window('A plain Rig carried: walk + block break is heard', 'a0', 'a1', True)
window('E Silent Step carried: walk + block break is not heard', 'e0', 'e1', False)
window('E2 Silent Step Stowed: walk is heard again', 'f0', 'f1', True)
check('D setup: both blocks broken while carried', digs.get('a', (False,))[0] and digs.get('e', (False,))[0] and digs['a'][1] == 'ACTIVE' and digs['e'][1] == 'ACTIVE', str(digs))
damp = [e for e in events if e[1] == 'true']
loud = [e for e in events if e[1] == 'false' and e[2] != 'STOWED']
check('G server: carried game events dampened only with Silent Step', bool(damp) and all(e[2] != 'STOWED' for e in damp) and bool(loud),
      f'{len(damp)} dampened ({sorted(set(e[0] for e in damp))}), {len(loud)} carried plain')
kicks = len(re.findall(r'moved too quickly|moved wrongly|moves faster than|kicked', server[server.find('SNBT p0'):]))
check('S server: no kick, no speed warning after the first tp', kicks == 0, f'{kicks} hits')
print('PASS' if all(checks) else 'FAIL')
