"""Actuator look pass/fail: shots taken and not blank, limbOpacity visible in first person, HUD lines, no hand drawn twice in first
person, retarget time, crosshair marks, Grab cues, the holding arm, set-down spot and hold distance (state and pixels, both tier tints),
Back Slot with Curios.
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
         'look_carry_pick_fp', 'look_carry_swing_fp', 'look_carry_block_fp', 'look_carry_block_front', 'look_carry_bow_fp',
         'look_w_offhand_fp', 'look_w_lefthanded_fp', 'look_m_reach', 'look_m_dual', 'look_m_out', 'look_m_limp', 'look_m_cue_grab',
         'look_m_cue_full', 'look_m_cue_two', 'look_m_cue_refused', 'look_m_hold', 'look_m_spot_ok', 'look_m_spot_bad', 'look_m_spot_bad_g3',
         'look_m_miss', 'look_m_wheel', 'look_m_hold_tool_fp', 'look_m_hold_tool_third', 'look_m_hold_main_fp', 'look_m_hold_main_third',
         'look_m_copper_g2', 'look_m_copper_dual_g2', 'look_m_copper_dual_g3']
bursts = [f'{n}_{i:02d}' for n, k in (('look_w_mine_fp', 8), ('look_w_retarget_fp', 8), ('look_w_mine_third', 4), ('look_w_retarget_third', 6),
                                      ('look_w_dual_fp', 8), ('look_w_dual_third', 6), ('look_w_eat_fp', 4), ('look_m_setdown', 8)) for i in range(k)]
cact = {m[0]: dict(held=int(m[1]), heavy=m[2] == 'true', hud=m[3], fitted=m[4] == 'true', limp=m[5] == 'true', speed=float(m[6]))
        for m in re.findall(r'CACT (\S+) gt=\d+ held=(-?\d+) heavy=(\w+) .*? hud="([^"]*)" fitted=(\w+) limp=(\w+) .*? speed=(\S+)', c)}
grab = re.findall(r'SGRAB gt=\d+ Dev kind=(\w+) target=\S+ type=(\S+) why=(\S+) actuators=(\d+)', s)
garm = re.findall(r'SGRAB gt=\d+ Dev kind=(\w+) target=\S+ type=(\S+) .*? arm=(\S+)', s)
far = re.findall(r'SFAR gt=\d+ Dev kind=attack at=\S+ type=minecraft:husk .*? far=(\w+) .*? canceled=(\w+)', s)
opa = re.findall(r'TRACE t=\d+ gt=\d+ .*? far=(\d+) xh=.*? opa=(\S+)', c)

print('== Shots')
for n in names + (['look_backslot_third'] if curios else []):
    im = img(n)
    sd = ImageStat.Stat(im.convert('L')).stddev[0] if im else 0
    floor = 10 if n in ('look_carry_down_third', 'look_carry_up_third') else 20
    check(f'S {n}', im is not None and f'Saved screenshot as {n}.png' in c and sd > floor, f"{'present' if im else 'missing'}, stddev {sd:.1f}")
blank = [n for n in bursts if img(n) is None or ImageStat.Stat(img(n).convert('L')).stddev[0] <= 20]
check('S burst frames (mining, retarget, Dual Wield, eating, set-down carry; first and third person)', not blank, f'{len(bursts) - len(blank)} of {len(bursts)} present' + (f', missing or blank {blank[:4]}' if blank else ''))
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

print('== First-person hands')
look = [dict(first=m[0] == 'true', frames=int(m[1]), v=(int(m[2]), int(m[3])), a=(int(m[4]), int(m[5])), dbl=int(m[6]) + int(m[7]),
             lag=float(m[8]) if m[8] else -1)
        for m in re.findall(r'CLOOK gt=\d+ first=(\w+) frames=(\d+) vhand=(\d+),(\d+) ahand=(\d+),(\d+) dbl=(\d+),(\d+)(?: mark=\w+ cue=\w+ lag=(\S+))?', c)]
fp = [l for l in look if l['first']]
tot = lambda k, h: sum(l[k][h] for l in fp)
text = (f"{len(fp)} first-person ticks, {sum(l['frames'] for l in fp)} frames, vanilla main {tot('v', 0)} off {tot('v', 1)}, "
        f"Actuator main {tot('a', 0)} off {tot('a', 1)}, doubled {sum(l['dbl'] for l in fp)}")
check('D no hand or item drawn twice in first person (vanilla hand and Actuator in one frame)', len(fp) > 100 and sum(l['dbl'] for l in fp) == 0
      and tot('a', 0) > 100, text)
check('D the Actuators stand in for both hands: vanilla draws no hand, arm or item in any state of the work section (idle, far mining, '
      'retarget, Dual Wield, eating, off hand alone, left-handed)', len(fp) > 100 and tot('v', 0) == 0 and tot('v', 1) == 0 and tot('a', 1) > 20, text)
run = worst = 0
for l in fp:
    run = run + 1 if l['lag'] > 0.1 else 0
    worst = max(worst, run)
acting = [l['lag'] for l in fp if l['lag'] >= 0]
check('R retarget: the acting Actuator is on its new target within 3 ticks (ticks in a row more than 0.1 block off)', len(acting) > 30 and worst <= 3
      and max(acting, default=0) > 0.1, f"{len(acting)} acting ticks, worst run {worst}, largest lag {max(acting, default=0):.2f}")

print('== Crosshair marks and Grab cues')
chud = {m[0]: dict(shown=m[1] == 'true', mark=m[2], cue=m[3], hold=m[4], spot=m[5], throwkey=m[6] == 'true', cuearm=m[7], dist=float(m[8]), miss=m[9] == 'true')
        for m in re.findall(r'CHUD (\S+) gt=\d+ shown=(\w+) mark=(\w+) cue=(\w+) hold=(\w+) spot=(\S+) throwkey=(\w+) cuearm=(\w+) dist=(\S+) miss=(\w+)', c)}
want = dict(reach=dict(mark='REACH', cue='NONE'), dual=dict(mark='DUAL'), out=dict(mark='OUT', miss=False), far_idle=dict(mark='NONE', miss=False),
            miss=dict(mark='NONE', miss=True), miss_gone=dict(mark='NONE', miss=False), near=dict(mark='NONE'), limp=dict(mark='LIMP'),
            cue_grab=dict(cue='GRAB', cuearm='OFF'), cue_full=dict(cue='HANDS_FULL'), cue_two=dict(cue='TWO_HANDS'), cue_both=dict(cue='GRAB', cuearm='BOTH'),
            cue_refused=dict(cue='REFUSED'), hold=dict(hold='OFF', cue='NONE', spot='-', throwkey=False), spot_ok=dict(hold='OFF', spot='ok'),
            spot_bad=dict(hold='OFF', spot='bad'), wheel_kept=dict(hold='OFF'), cue_tool=dict(cue='GRAB', cuearm='OFF'),
            hold_tool=dict(hold='OFF', throwkey=True), cue_main=dict(cue='GRAB', cuearm='MAIN'), hold_main=dict(hold='MAIN', throwkey=False),
            copper_reach=dict(mark='REACH'), copper_dual=dict(mark='DUAL'))
for tag, w in want.items():
    got = chud.get(tag)
    check(f'M {tag}', got is not None and got['shown'] and all(got[k] == v for k, v in w.items()), f'{got}')
w0, w1 = chud.get('wheel0'), chud.get('wheel')
check('M wheel: the Grab key held + two notches move the hold 1.0 farther', w0 is not None and w1 is not None and w0['dist'] > 0
      and abs(w1['dist'] - w0['dist'] - 1.0) < 0.01, f"{w0 and w0['dist']} -> {w1 and w1['dist']}")
pig = [(k, a) for k, t, a in garm if t == 'minecraft:pig']
check('M the server holds with the same Actuator: off-hand (H, empty hands, tool in the main hand), main-hand (item in the off hand); three set down at the looked spot',
      [a for k, a in pig if k == 'start'] == ['OFF', 'OFF', 'OFF', 'MAIN'] and sum(1 for k, a in pig if k == 'place') == 3, f'{pig}')
hidden = {t: chud.get(t) for t in ('third', 'f1', 'screen')}
check('M hidden in third person, with F1 and under a screen', all(v is not None and not v['shown'] for v in hidden.values()), f'{hidden}')

def tinted(name, colour, x0, x1, y0=-24, y1=24, tol=36):
    """Pixels near a colour in a box round the crosshair (offsets in screen pixels from the centre)."""
    im = img(name)
    if im is None: return -1
    cx, cy = im.width // 2, im.height // 2
    d = im.crop((cx + x0, cy + y0, cx + x1, cy + y1)).tobytes()
    return sum(1 for i in range(0, len(d), 3) if all(abs(d[i + k] - colour[k]) <= tol for k in range(3)))

NETHERITE, COPPER, RED = (201, 162, 245), (247, 163, 92), (255, 74, 74)
px = {n: (tinted(n, NETHERITE, -44, -8), tinted(n, NETHERITE, 8, 44)) for n in ('look_m_reach', 'look_m_dual', 'look_m_limp')}
red = (tinted('look_m_limp', RED, -44, -8), tinted('look_m_limp', RED, 8, 44))
check('M pixels: in Reach only the acting side is lit, Dual Wield lights both, limp marks are red', px['look_m_reach'][0] == 0 and px['look_m_reach'][1] > 20
      and min(px['look_m_dual']) > 20 and min(red) > 20 and max(px['look_m_limp']) == 0, f'tier-tinted pixels left, right {px}, red when limp {red}')
f1 = tinted('look_m_f1', NETHERITE, -80, 80, -80, 80)
hold = tinted('look_m_hold', NETHERITE, -24, 24, -48, -8)
check('M pixels: the closed claw shows above the crosshair while holding, nothing with F1', hold > 20 and f1 == 0, f'holding {hold} px, F1 {f1} px')
ok, bad = tinted('look_m_spot_ok', NETHERITE, 14, 34, -44, -18), tinted('look_m_spot_bad', RED, 14, 34, -44, -18)
bad3, badok = tinted('look_m_spot_bad_g3', RED, 20, 50, -66, -27), tinted('look_m_spot_bad', NETHERITE, 14, 34, -44, -18)
check('M pixels: the set-down marker is tier-tinted on a valid spot, red on a spot that is not (GUI scale 2 and 3)',
      ok > 8 and bad > 8 and bad3 > bad and badok == 0, f'valid {ok} px, not valid red {bad} px (scale 3: {bad3}), tier-tinted when not valid {badok}')
cu = {n: (tinted(n, COPPER, -44, -8), tinted(n, COPPER, 8, 44)) for n in ('look_m_copper_g2', 'look_m_copper_dual_g2', 'look_m_copper_dual_g3')}
check('M pixels: a Copper Rig tints the marks copper at GUI scale 2 and 3 (acting side only in Reach, both for Dual Wield)',
      cu['look_m_copper_g2'][0] == 0 and cu['look_m_copper_g2'][1] > 20 and min(cu['look_m_copper_dual_g2']) > 20
      and min(cu['look_m_copper_dual_g3']) > 1.5 * min(cu['look_m_copper_dual_g2']), f'copper-tinted pixels left, right {cu}')

print('== Back Slot')
if curios:
    check('B Back Slot Rig fitted over an iron chestplate', cact.get('back', {}).get('fitted'), f"{cact.get('back')}")
else:
    print('SKIP B Back Slot: run with GRADLE_ARGS=-PwithCurios')

exc = [l for l in (c + s).splitlines() if 'Exception' in l and 'ApiStatus' not in l and 'authlib' not in l and 'neoforge-client.new.tmp.toml' not in l
       and 'Error loading class: net/irisshaders/' not in l and 'org.gradle.' not in l and 'MessageIOException' not in l]
check('E exceptions', not exc, f'{len(exc)} exception lines' + (f': {exc[0][:200]}' if exc else ''))
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
