"""First-person view suite (#33, #41): own Actuators at rest stay off screen; none in the world pass while scoping. usage: fpview_metrics.py tools/logs/<tag>-fpview"""
import re, sys
pre = sys.argv[1]
c = open(f'{pre}-client.log', errors='replace').read()
fails = []

def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok: fails.append(name)

mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
look = [dict(gt=int(m[0]), first=m[1] == 'true', frames=int(m[2]), amain=int(m[3]), aoff=int(m[4]), wmain=int(m[5]), woff=int(m[6]), rmain=int(m[7]), roff=int(m[8]))
        for m in re.findall(r'CLOOK gt=(\d+) first=(\w+) frames=(\d+) vhand=\d+,\d+ ahand=(\d+),(\d+) .*? wpass=(\d+),(\d+) rview=(\d+),(\d+)', c)]

def window(tag):
    a, b = mk.get(tag + 'a'), mk.get(tag + 'b')
    return [] if a is None or b is None else [l for l in look if a <= l['gt'] <= b and l['first'] and l['frames'] > 0]

def total(w, *keys):
    return sum(l[k] for l in w for k in keys)

for tag, what in [('v1', 'FOV 70, standing'), ('v2', 'FOV 90, looking up, strafing right'), ('v3', 'FOV 90, fast turn left, looking up'),
                  ('v4', 'FOV 90, fast turn right, looking up'), ('v5', 'FOV 90, Active, looking up, strafing right'),
                  ('v6', 'FOV 90, crouched, looking up, strafing right'), ('v7', 'FOV 90, crawling, looking ahead, up, down'),
                  ('v8', 'FOV 90, left-handed, looking up, strafing left')]:
    w = window(tag)
    r = (total(w, 'rmain'), total(w, 'roff'))
    check(tag.upper(), len(w) >= 20 and r == (0, 0), f'{what}: {len(w)} ticks, frames with a resting Actuator on screen (main, off) {r}')
for tag, what in [('s1', 'spyglass in the main hand'), ('s2', 'spyglass in the off hand')]:
    w = window(tag)
    wp, r = (total(w, 'wmain'), total(w, 'woff')), (total(w, 'rmain'), total(w, 'roff'))
    check(tag.upper(), len(w) >= 20 and wp == (0, 0) and r == (0, 0), f'{what}, scoping: {len(w)} ticks, world-pass frames (main, off) {wp}, resting on screen {r}')
bad = re.findall(r'---- Minecraft Crash Report ----|Exception in thread', c)
check('E client: no crash', not bad, f'{len(bad)} hits')
print('RESULT ' + ('PASS' if not fails else 'FAIL ' + ' '.join(fails)))
