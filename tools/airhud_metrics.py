"""Air timer suite: Create's remaining-Air timer for the Rig while it uses Air. usage: airhud_metrics.py tools/logs/<tag>-airhud"""
import re, sys
pre = sys.argv[1]
c = open(f'{pre}-client.log', errors='replace').read()
fails = []

def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok: fails.append(name)

mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
tr = [(int(m[0]), m[1], m[2] == 'true') for m in re.findall(r'TRACE t=\d+ gt=(\d+) .*? state=(\w+) .*? airhud=(\w+)', c)]

def window(a, b):
    if a not in mk or b not in mk: return None
    return [t for t in tr if mk[a] <= t[0] <= mk[b]]

def case(name, a, b, want, what, state=None, any_=False):
    w = window(a, b)
    if not w:
        check(name, False, f'{what}: no TRACE between {a} and {b}')
        return
    hits = [t for t in w if t[2] == want]
    ok = bool(hits) if any_ else len(hits) == len(w)
    if state: ok = ok and all(t[1] == state for t in w)
    states = sorted({t[1] for t in w})
    check(name, ok, f'{what}: {len(hits)}/{len(w)} ticks airhud={str(want).lower()}, states {states}')

case('A1', 'a1', 'a1e', False, 'Stowed, standing')
case('A2', 'a2', 'a2e', True, 'Active', state='ACTIVE')
case('A3', 'a3', 'a3e', False, 'Stowed again after Setting Down', state='STOWED')
case('A4', 'a4', 'a4e', True, 'Stowed Far Use at the default cost', any_=True)
case('A4b', 'a4f', 'a4g', False, 'after the sips')
case('A5', 'a5', 'a5e', False, 'creative, Active', state='ACTIVE')
case('A6', 'a6', 'a6e', False, 'Diving Helmet under water: Create shows its own')
case('A6b', 'a6b', 'a6be', False, 'Active in the water with the helmet: Create shows its own', state='ACTIVE')
case('A7', 'a7', 'a7e', False, 'out of the water: helmet breaths are not Rig drain')
bad = re.findall(r'---- Minecraft Crash Report ----|Exception in thread', c)
check('E client: no crash', not bad, f'{len(bad)} hits')
print('RESULT ' + ('PASS' if not fails else 'FAIL ' + ' '.join(fails)))
