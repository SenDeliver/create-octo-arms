"""Actuator look pass/fail: shots taken and not blank, limbOpacity visible in first person, HUD lines, Back Slot with Curios.
usage: look_metrics.py tools/logs/<tag>-look [screenshot dir, default run/screenshots]. The shots themselves still want a human look."""
import re, sys, os
from PIL import Image, ImageChops, ImageStat
pre = sys.argv[1]
shots = sys.argv[2] if len(sys.argv) > 2 else 'run/screenshots'
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
fails = []

def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok: fails.append(name)

def img(n):
    p = os.path.join(shots, f'{n}.png')
    return Image.open(p).convert('RGB') if os.path.exists(p) else None

def changed_px(a, b):
    return ImageChops.difference(a, b).convert('L').point(lambda v: 255 if v > 24 else 0).histogram()[255]

curios = bool(re.search(r'\(curios\)', s))
names = ['look_rest_third', 'look_rest_front', 'look_active_third', 'look_fp_idle', 'look_fp_far', 'look_fp_far_03', 'look_fp_hold_10',
         'look_fp_hold_03', 'look_fp_hold_10b', 'look_hold_light_third', 'look_hud_limp', 'look_hold_heavy_third',
         'look_fp_far_carry', 'look_far_carry_third', 'look_carry_pick_third', 'look_carry_turn_third', 'look_carry_pick_front', 'look_carry_down_third', 'look_carry_up_third', 'look_carry_swing_third',
         'look_carry_pick_fp', 'look_carry_swing_fp', 'look_carry_block_fp', 'look_carry_block_front', 'look_carry_bow_fp']
cact = {m[0]: dict(held=int(m[1]), heavy=m[2] == 'true', hud=m[3], fitted=m[4] == 'true', limp=m[5] == 'true', speed=float(m[6]))
        for m in re.findall(r'CACT (\S+) gt=\d+ held=(-?\d+) heavy=(\w+) .*? hud="([^"]*)" fitted=(\w+) limp=(\w+) .*? speed=(\S+)', c)}
grab = re.findall(r'SGRAB gt=\d+ Dev kind=(\w+) target=\S+ type=(\S+) why=(\S+) actuators=(\d+)', s)
far = re.findall(r'SFAR gt=\d+ Dev kind=attack at=\S+ type=minecraft:husk .*? far=(\w+) .*? canceled=(\w+)', s)
opa = re.findall(r'TRACE t=\d+ gt=\d+ .*? far=(\d+) xh=.*? opa=(\S+)', c)

print('== Shots')
for n in names + (['look_backslot_third'] if curios else []):
    im = img(n)
    sd = ImageStat.Stat(im.convert('L')).stddev[0] if im else 0
    floor = 10 if n in ('look_carry_down_third', 'look_carry_up_third') else 20
    check(f'S {n}', im is not None and f'Saved screenshot as {n}.png' in c and sd > floor, f"{'present' if im else 'missing'}, stddev {sd:.1f}")
a, b, b2 = img('look_fp_hold_10'), img('look_fp_hold_03'), img('look_fp_hold_10b')
if a and b and b2:
    d, ctl = changed_px(a, b), changed_px(a, b2)
    check('F limbOpacity 0.3 shows in first person (held pig, same pose: 1.0 vs 0.3 against 1.0 vs 1.0)', d > 5000 and d > 10 * max(ctl, 1), f'{d} px changed, control {ctl} px')
check('F Far Use reach drawn in first person at 1.0 and 0.3, then carrying a pickaxe (first and third person)', len(far) == 4 and all(f == ('FAR', 'false') for f in far)
      and any(int(t) > 0 and o == '1.00' for t, o in opa) and any(int(t) > 0 and o == '0.30' for t, o in opa), f'SFAR {far}')

print('== HUD and holds')
check('H Rest: fitted, nothing held, no HUD line', cact.get('rest', {}).get('fitted') and cact['rest']['held'] == -1 and cact['rest']['hud'] == '-', f"{cact.get('rest')}")
check('H light hold: "Holding Pig", one Actuator', cact.get('hold', {}).get('hud') == 'Holding Pig' and ('start', 'minecraft:pig', 'light', '1') in grab, f"{cact.get('hold')}")
check('H no Air: limp HUD line, the pig let go', cact.get('limp', {}).get('limp') and cact['limp']['hud'] == 'No Air: Actuators limp' and ('release', 'minecraft:pig', 'air', '1') in grab,
      f"{cact.get('limp')}")
check('H heavy hold: "Holding Iron Golem heavy", both Actuators, speed x0.6', cact.get('heavy', {}).get('hud') == 'Holding Iron Golem heavy' and abs(cact['heavy']['speed'] - 0.6) < 1e-3
      and ('start', 'minecraft:iron_golem', 'heavy', '2') in grab, f"{cact.get('heavy')}")

print('== Back Slot')
if curios:
    check('B Back Slot Rig fitted over an iron chestplate', cact.get('back', {}).get('fitted'), f"{cact.get('back')}")
else:
    print('SKIP B Back Slot: run with GRADLE_ARGS=-PwithCurios')

exc = [l for l in (c + s).splitlines() if 'Exception' in l and 'ApiStatus' not in l and 'authlib' not in l and 'neoforge-client.new.tmp.toml' not in l
       and 'Error loading class: net/irisshaders/' not in l and 'org.gradle.' not in l and 'MessageIOException' not in l]
check('E exceptions', not exc, f'{len(exc)} exception lines' + (f': {exc[0][:200]}' if exc else ''))
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
