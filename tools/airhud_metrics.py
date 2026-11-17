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
at = {int(m[0]): (int(m[1]), float(m[2])) for m in re.findall(r'TRACE t=\d+ gt=(\d+) .*? airt=(-?\d+) airr=(\S+)', c)}
idle = 0.5 / 80
w = [g for g in sorted(at) if 'a2e' in mk and 'a2r' in mk and mk['a2e'] <= g <= mk['a2r']]
last = at[w[-1]] if w else None
check('A2r Active Idle: the time is the Air left at the Netherite Idle rate (air.idle 80, factor 0.5), not 1 Air a second',
      last and abs(last[1] - idle) <= 0.1 * idle and 1500 <= last[0] * last[1] <= 1801, f'airt {last and last[0]} airr {last and last[1]} (want {idle:.6f}), Air {last and last[0] * last[1]:.0f}')
on = {t[0] for t in tr if t[2]}
hid = [g for g in at if at[g][0] >= 0 and not on & {g - 1, g, g + 1}]
check('A8 the time is set only while the timer shows (a tick of slack: the trace and the HUD tick run apart)', not hid, f'{len(hid)} ticks with airt but no airhud')
bad = re.findall(r'---- Minecraft Crash Report ----|Exception in thread', c)
check('E client: no crash', not bad, f'{len(bad)} hits')
print('RESULT ' + ('PASS' if not fails else 'FAIL ' + ' '.join(fails)))
