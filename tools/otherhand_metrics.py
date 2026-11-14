"""otherhand suite: a hand another mod draws itself (selfdraw) is left to it. usage: otherhand_metrics.py tools/logs/<tag>-otherhand"""
import re, sys
pre = sys.argv[1]
c = open(f'{pre}-client.log', errors='replace').read()
fails = []

def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok: fails.append(name)

mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
look = [dict(gt=int(m[0]), first=m[1] == 'true', frames=int(m[2]), vmain=int(m[3]), voff=int(m[4]), amain=int(m[5]), aoff=int(m[6]), dmain=int(m[7]), doff=int(m[8]), omain=int(m[9]), ooff=int(m[10]))
        for m in re.findall(r'CLOOK gt=(\d+) first=(\w+) frames=(\d+) vhand=(\d+),(\d+) ahand=(\d+),(\d+) dbl=(\d+),(\d+) .*? ohand=(\d+),(\d+)', c)]

def window(tag):
    a, b = mk.get(tag + 'a'), mk.get(tag + 'b')
    return None if a is None or b is None else [l for l in look if a <= l['gt'] <= b and l['first'] and l['frames'] > 0]

def case(name, tag, what, vanilla, actuator):
    w = window(tag)
    if not w:
        check(name, False, f'{what}: no first-person CLOOK lines')
        return
    v = sum(l[vanilla] for l in w) if vanilla else 0
    a = sum(l[actuator] for l in w) if actuator else 0
    dbl = sum(l['dmain'] + l['doff'] for l in w)
    ok = dbl == 0 and (v > 0 if vanilla else True) and (a > 0 if actuator else True)
    others = {k: sum(l[k] for l in w) for k in ('vmain', 'voff', 'amain', 'aoff', 'omain', 'ooff')}
    check(name, ok, f'{what}: {len(w)} ticks, frames drawn {others}, both {dbl}')
    return others

o1 = case('O1', 'o1', 'iron pickaxe (no other mod draws it): main-hand Actuator carries it', None, 'amain')
check('O1v', o1 is not None and o1['vmain'] == 0 and o1['omain'] == 0, 'neither vanilla nor the other mod draws the main hand')
o2 = case('O2', 'o2', 'stick (the other mod draws it): the other mod draws the main hand', 'omain', None)
check('O2a', o2 is not None and o2['amain'] == 0 and o2['vmain'] == 0, 'the main-hand Actuator does not carry it, vanilla does not draw it')
o3 = case('O3', 'o3', 'stick in the off hand: the other mod draws the off hand', 'ooff', None)
check('O3a', o3 is not None and o3['aoff'] == 0 and o3['voff'] == 0, 'the off-hand Actuator does not carry it, vanilla does not draw it')
o4 = case('O4', 'o4', 'iron pickaxe again: the main-hand Actuator carries it again', None, 'amain')
check('O4v', o4 is not None and o4['vmain'] == 0 and o4['omain'] == 0, 'neither vanilla nor the other mod draws the main hand')
bad = re.findall(r'---- Minecraft Crash Report ----|Exception in thread', c)
check('E client: no crash', not bad, f'{len(bad)} hits')
print('RESULT ' + ('PASS' if not fails else 'FAIL ' + ' '.join(fails)))
