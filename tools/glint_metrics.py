"""Glint suite: Far Use with glinting items in the Actuators, first person (#32). usage: glint_metrics.py tools/logs/<tag>-glint"""
import re, sys
pre = sys.argv[1]
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
fails = []

def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok: fails.append(name)

mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
trace = [(int(m[0]), int(m[1]), m[2]) for m in re.findall(r'TRACE t=\d+ gt=(\d+) .*? far=(-?\d+) .*? farhand=(\S+)', c)]
far = [dict(gt=int(m[0]), kind=m[1], at=m[2], far=m[3], canceled=m[4] == 'true')
       for m in re.findall(r'SFAR gt=(\d+) Dev kind=(\w+) at=(\S+) type=\S+ dist=\S+ range=\S+ lift=\S+ far=(\w+) sight=\w+ asking=\w+ canceled=(\w+)', s)]
probe = {}
for m in re.finditer(r'PROBE (-?\d+), (-?\d+), (-?\d+) Block\{([^}]+)\}', s):
    probe.setdefault(f'{m[1]},{m[2]},{m[3]}', []).append(m[4])

cases = [('G1', 'g1', 'glinting Create wrench, main hand, mines dirt', ('lclick', 'break'), 'main', '11707,-59,0', 'minecraft:air'),
         ('G2', 'g2', 'glinting Create wrench, main hand, used on a shaft', ('rclick',), 'main', None, None),
         ('G3', 'g3', 'glinting Create wrench, off hand, used on a shaft', ('rclick',), 'off', None, None),
         ('G4', 'g4', 'glinting diamond pickaxe, main hand, mines dirt', ('lclick', 'break'), 'main', '11707,-59,12', 'minecraft:air')]
for name, tag, what, kinds, hand, pos, after in cases:
    a, b = mk.get(tag + 'a'), mk.get(tag + 'b')
    if a is None or b is None:
        check(name, False, f'{what}: client never reached mark {tag}b (crashed or stuck)')
        continue
    acts = [f for f in far if a <= f['gt'] <= b + 2 and f['kind'] in kinds]
    farok = [f for f in acts if f['far'] == 'FAR' and not f['canceled']]
    drawn = [t for t in trace if a <= t[0] <= b and t[1] > 0 and t[2] == hand]
    detail = f'{what}: {len(farok)}/{len(acts)} Far Use actions FAR and not cancelled, {len(drawn)} ticks reaching with the {hand} Actuator'
    ok = bool(farok) and bool(drawn)
    if pos:
        got = probe.get(pos, ['?'])[-1]
        ok = ok and got == after
        detail += f', block after {got}'
    check(name, ok, detail)
bad = re.findall(r'Not building!|Exception in thread|---- Minecraft Crash Report ----', c)
check('E client: no crash, no "Not building!"', not bad and 'quit' in c[-20000:].lower(), f'{len(bad)} hits')
print('RESULT ' + ('PASS' if not fails else 'FAIL ' + ' '.join(fails)))
