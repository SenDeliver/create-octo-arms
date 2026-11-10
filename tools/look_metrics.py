"""Actuator look pass/fail: shots taken and not blank, limbOpacity visible in first person, HUD lines, no hand drawn twice in first
person, retarget time, crosshair marks, Grab cues, the holding brackets, set-down spot, hold distance gauge, the hint and refusal
lines on the action bar (state and pixels, both tier tints), Back Slot with Curios.
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
         'look_m_cue_grab_late', 'look_m_cue_full', 'look_m_cue_two', 'look_m_cue_refused', 'look_m_press_far', 'look_m_hold_both', 'look_m_hold',
         'look_m_hold_quiet', 'look_m_spot_ok', 'look_m_spot_bad', 'look_m_spot_bad_g3', 'look_m_miss', 'look_m_gauge', 'look_m_wheel', 'look_m_wheel_g3', 'look_m_gauge_sky',
         'look_m_hold_tool_fp', 'look_m_hold_tool_third', 'look_m_hold_main_fp', 'look_m_hold_main_third',
         'look_m_copper_g2', 'look_m_copper_dual_g2', 'look_m_copper_dual_g3', 'look_m_copper_hold_g2', 'look_m_copper_hold_g3',
         'look_k_dig_front', 'look_k_ceiling_rest_third']
bursts = [f'{n}_{i:02d}' for n, k in (('look_w_mine_fp', 8), ('look_w_retarget_fp', 8), ('look_w_mine_third', 4), ('look_w_retarget_third', 6),
                                      ('look_w_dual_fp', 8), ('look_w_dual_third', 6), ('look_w_eat_fp', 4), ('look_m_grab', 14), ('look_m_setdown', 8),
                                      ('look_k_dig_fp', 8), ('look_k_dig_third', 4), ('look_k_feet_fp', 4), ('look_k_feet_crouch_fp', 4), ('look_k_feet_third', 3),
                                      ('look_k_feet_active_fp', 4), ('look_k_ceiling_floor_fp', 4), ('look_k_ceiling_up_fp', 4), ('look_k_ceiling_up_third', 3),
                                      ('look_k_step_fp', 4), ('look_k_step_retarget_fp', 4), ('look_k_step_retarget_third', 4)) for i in range(k)]
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
blank = [n for n in bursts if img(n) is None or ImageStat.Stat(img(n).convert('L')).stddev[0] <= (12 if n.startswith('look_k_') else 20)]
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
look = [dict(gt=int(m[0]), first=m[1] == 'true', frames=int(m[2]), v=(int(m[3]), int(m[4])), a=(int(m[5]), int(m[6])), dbl=int(m[7]) + int(m[8]),
             lag=float(m[9]) if m[9] else -1)
        for m in re.findall(r'CLOOK gt=(\d+) first=(\w+) frames=(\d+) vhand=(\d+),(\d+) ahand=(\d+),(\d+) dbl=(\d+),(\d+)(?: mark=\w+ cue=\w+ lag=(\S+))?', c)]
fp = [l for l in look if l['first']]
tot = lambda k, h: sum(l[k][h] for l in fp)
text = (f"{len(fp)} first-person ticks, {sum(l['frames'] for l in fp)} frames, vanilla main {tot('v', 0)} off {tot('v', 1)}, "
        f"Actuator main {tot('a', 0)} off {tot('a', 1)}, doubled {sum(l['dbl'] for l in fp)}")
check('D no hand or item drawn twice in first person (vanilla hand and Actuator in one frame)', len(fp) > 100 and sum(l['dbl'] for l in fp) == 0
      and tot('a', 0) > 100, text)
check('D the Actuators stand in for both hands: vanilla draws no hand, arm or item in any state of the work section (idle, far mining, '
      'retarget, Dual Wield, eating, off hand alone, left-handed)', len(fp) > 100 and tot('v', 0) == 0 and tot('v', 1) == 0 and tot('a', 1) > 20, text)
marks = {m[0]: int(m[1]) for m in re.findall(r'CPOS (k_\w+) gt=(\d+)', c)}
order = sorted(marks.items(), key=lambda kv: kv[1])
def part(gt):
    name = 'w'
    for k, g in order:
        if gt >= g: name = k
    return name
work = [l for l in fp if part(l['gt']) not in ('k_feet_third', 'k_active')]
run = worst = 0
for l in work:
    run = run + 1 if l['lag'] > 0.1 else 0
    worst = max(worst, run)
acting = [l['lag'] for l in work if l['lag'] >= 0]
check('R retarget (work section and K, but the Rig rising to Active at the feet, which the target chase trails): the acting Actuator is on its new target within 3 ticks (ticks in a row more than 0.1 block off)', len(acting) > 30 and worst <= 3
      and max(acting, default=0) > 0.1, f"{len(acting)} acting ticks, worst run {worst}, largest lag {max(acting, default=0):.2f}")

print('== Clearance')
clip = [(int(m[0]), m[1] == 'true', int(m[2]), int(m[3]), float(m[4]))
        for m in re.findall(r'CLOOK gt=(\d+) first=(\w+) .*? clip=(\d+),(\d+) cdepth=(\S+)', c)]
bad = [(part(g), g, 'fp' if f else 'third', a, b, d) for g, f, a, b, d in clip if a or b]
parts = sorted({part(g) for g, *_ in clip})
check('K no Actuator point (segments, gripper, carried item) inside a block on any traced tick: the work section (wall), Dual Wield shovels on the '
      'grass 3 ahead, the block 1 ahead at the feet standing, crouched and Active, under a low ceiling (floor, ceiling, Rest Pose Stowed and Active), '
      'a step (its face, then retargeted over its edge to its top, first and third person)', len(clip) > 200 and 'k_end' in marks and not bad,
      f'{len(clip)} ticks over {parts}, {len(bad)} with points inside' + (f', first {bad[:4]}' if bad else ''))

print('== Crosshair marks, Grab cues, holding brackets, gauge, action-bar lines')
chud = {m[0]: dict(shown=m[1] == 'true', mark=m[2], cue=m[3], hold=m[4], spot=m[5], throwkey=m[6] == 'true', cuearm=m[7], dist=float(m[8]), miss=m[9] == 'true',
                   grip=m[10], gauge=m[11], cap=m[12] == 'true', hints=int(m[13]), bar=m[14])
        for m in re.findall(r'CHUD (\S+) gt=\d+ shown=(\w+) mark=(\w+) cue=(\w+) hold=(\w+) spot=(\S+) throwkey=(\w+) cuearm=(\w+) dist=(\S+) miss=(\w+)'
                            r' grip=(\S+) gauge=(\S+) cap=(\w+) hints=(\d+) bar="([^"]*)"', c)}
HINT, HINT_TOOL = 'V Set Down   Left Button Throw   V + ~ Distance', 'V Set Down   V + Left Button Throw   V + ~ Distance'
want = dict(reach=dict(mark='REACH', cue='NONE', grip='-'), dual=dict(mark='DUAL'), out=dict(mark='OUT', miss=False), far_idle=dict(mark='NONE', miss=False),
            miss=dict(mark='NONE', miss=True, bar=''), miss_gone=dict(mark='NONE', miss=False), near=dict(mark='NONE'), limp=dict(mark='LIMP'),
            cue_grab=dict(cue='GRAB', cuearm='OFF', cap=True), cue_grab_late=dict(cue='GRAB', cuearm='OFF', cap=False),
            press_limp=dict(hold='NONE', bar='No Air: the Actuators hang limp'),
            cue_full=dict(cue='HANDS_FULL', cap=False), press_full=dict(hold='NONE', bar='Hands full: empty a hand to Grab'),
            hold_refused=dict(hold='OFF', grip='L', hints=0, bar='Hands full: empty a hand to Grab'),
            cue_two=dict(cue='TWO_HANDS'), press_two=dict(hold='NONE', bar='Needs both Actuators: empty both hands'),
            cue_both=dict(cue='GRAB', cuearm='BOTH'), cue_refused=dict(cue='REFUSED'),
            press_refused=dict(hold='NONE', bar="Can't Grab something riding or ridden"),
            far_mob=dict(cue='NONE', miss=False), press_far=dict(hold='NONE', miss=True, bar='Out of Reach'),
            hold_both=dict(hold='BOTH', grip='LR', hints=1, bar=HINT, cue='NONE'),
            hold=dict(hold='OFF', cue='NONE', spot='-', throwkey=False, grip='L', hints=2, bar=HINT, gauge='-'),
            hold_quiet=dict(hold='OFF', grip='L', bar='', gauge='-'), spot_ok=dict(hold='OFF', spot='ok', grip='L'),
            spot_bad=dict(hold='OFF', spot='bad', grip='L'), wheel_kept=dict(hold='OFF', grip='L'), gauge_gone=dict(hold='OFF', gauge='-'),
            cue_tool=dict(cue='GRAB', cuearm='OFF', cap=False), hold_tool=dict(hold='OFF', throwkey=True, grip='L', hints=2, bar=''),
            cue_main=dict(cue='GRAB', cuearm='MAIN', cap=False), hold_main=dict(hold='MAIN', throwkey=False, grip='R', bar=''),
            copper_reach=dict(mark='REACH'), copper_dual=dict(mark='DUAL'), copper_hold=dict(hold='OFF', grip='L', hints=1, bar=HINT_TOOL))
for tag, w in want.items():
    got = chud.get(tag)
    check(f'M {tag}', got is not None and got['shown'] and all(got[k] == v for k, v in w.items()), f'{got}')
w0, w1, wk = chud.get('wheel0'), chud.get('wheel'), chud.get('wheel_kept')
check('M wheel: the Grab key held + two notches move the hold 1.0 farther', w0 is not None and w1 is not None and w0['dist'] > 0
      and abs(w1['dist'] - w0['dist'] - 1.0) < 0.01, f"{w0 and w0['dist']} -> {w1 and w1['dist']}")
g0, g1 = [tuple(int(v) for v in w['gauge'].split('/')) if w is not None and '/' in w['gauge'] else None for w in (w0, w1)]
check('M gauge: shows with the Grab key down, a tick per notch from the nearest hold to Reach 7 (13), the lit one two up after two notches; it lingers after the key and is gone 22 ticks later',
      g0 is not None and g1 is not None and g0[1] == g1[1] == 13 and g0[0] >= 1 and g1[0] == g0[0] + 2 and wk is not None and '/' in wk['gauge'],
      f"{w0 and w0['gauge']} -> {w1 and w1['gauge']}, after the key {wk and wk['gauge']}, later {chud.get('gauge_gone', {}).get('gauge')}")
pig = [(k, a) for k, t, a in garm if t == 'minecraft:pig']
check('M the server holds with the same Actuator: off-hand (H, after a refusal, empty hands, tool in the main hand, Copper), main-hand (item in the off hand); five set down at the looked spot',
      [a for k, a in pig if k == 'start'] == ['OFF', 'OFF', 'OFF', 'OFF', 'MAIN', 'OFF'] and sum(1 for k, a in pig if k == 'place') == 5, f'{pig}')
hidden = {t: chud.get(t) for t in ('third', 'f1', 'screen')}
check('M hidden in third person, with F1 and under a screen', all(v is not None and not v['shown'] and v['grip'] == '-' for v in hidden.values()), f'{hidden}')

def tinted(name, colour, x0, x1, y0=-24, y1=24, tol=36, where=False):
    """Pixels near a colour in a box round the crosshair (offsets in screen pixels from the centre); where: their mean y offset too."""
    im = img(name)
    if im is None: return (-1, 0) if where else -1
    cx, cy = im.width // 2, im.height // 2
    w = x1 - x0
    d = im.crop((cx + x0, cy + y0, cx + x1, cy + y1)).tobytes()
    hit = [i // 3 for i in range(0, len(d), 3) if all(abs(d[i + k] - colour[k]) <= tol for k in range(3))]
    return (len(hit), sum(i // w for i in hit) / len(hit) + y0 if hit else 0) if where else len(hit)

NETHERITE, COPPER, RED, PALE = (201, 162, 245), (247, 163, 92), (255, 74, 74), (170, 158, 188)
PAD_L, PAD_R, GAUGE_L, QUIET = (-26, -10, -12, 10), (10, 26, -12, 10), (-40, -26, -30, 28), (-80, 80, -80, 80)
px = {n: (tinted(n, NETHERITE, -44, -8), tinted(n, NETHERITE, 8, 44)) for n in ('look_m_reach', 'look_m_dual', 'look_m_limp')}
red = (tinted('look_m_limp', RED, -44, -8), tinted('look_m_limp', RED, 8, 44))
check('M pixels: in Reach only the acting side is lit, Dual Wield lights both, limp marks are red', px['look_m_reach'][0] == 0 and px['look_m_reach'][1] > 20
      and min(px['look_m_dual']) > 20 and min(red) > 20 and max(px['look_m_limp']) == 0, f'tier-tinted pixels left, right {px}, red when limp {red}')
pad = {n: (tinted(n, NETHERITE, *PAD_L), tinted(n, NETHERITE, *PAD_R)) for n in ('look_m_hold_quiet', 'look_m_hold_both', 'look_m_hold_main_fp', 'look_m_hold_tool_fp')}
check('M pixels: the holding Actuator\'s bracket is a tier-tinted pad tight on the crosshair: left for the off-hand Actuator, right for the main-hand one, both for a two-Actuator hold',
      all(pad[n][0] > 20 and pad[n][1] == 0 for n in ('look_m_hold_quiet', 'look_m_hold_tool_fp')) and pad['look_m_hold_main_fp'][0] == 0
      and pad['look_m_hold_main_fp'][1] > 20 and min(pad['look_m_hold_both']) > 20, f'tier-tinted pixels left, right {pad}')
f1 = tinted('look_m_f1', NETHERITE, *QUIET)
quiet = tinted('look_m_hold_quiet', NETHERITE, *QUIET) - tinted('look_m_hold_quiet', NETHERITE, -10, 10, -10, 10)
check('M pixels: holding, the centre shows the pad and nothing else in the tier tint (160 px round the crosshair, but for the crosshair itself, lilac over grass); nothing with F1',
      quiet == pad['look_m_hold_quiet'][0] and f1 == 0, f"holding {quiet} px, in the pad {pad['look_m_hold_quiet'][0]}, F1 {f1} px")
ok, bad = tinted('look_m_spot_ok', NETHERITE, *PAD_L), tinted('look_m_spot_bad', NETHERITE, *PAD_L)
pale, pale3 = tinted('look_m_spot_bad', PALE, *PAD_L, tol=24), tinted('look_m_spot_bad_g3', PALE, -39, -15, -18, 15, tol=24)
check('M pixels: the pad is tier-tinted over a valid set-down spot, pale (not red) over one that is not (GUI scale 2 and 3)',
      ok > 20 and bad == 0 and pale > 20 and pale3 > 1.5 * pale and tinted('look_m_spot_bad', RED, *PAD_L) == 0,
      f'valid {ok} px, not valid: tier-tinted {bad}, pale {pale} px (scale 3: {pale3})')
(n0, y0), (n1, y1) = tinted('look_m_gauge', NETHERITE, *GAUGE_L, where=True), tinted('look_m_wheel', NETHERITE, *GAUGE_L, where=True)
n3, sky = tinted('look_m_wheel_g3', NETHERITE, -60, -39, -45, 42), tinted('look_m_gauge_sky', NETHERITE, *GAUGE_L)
check('M pixels: the gauge\'s lit tick sits beside the pad and moves up two notches (8 px at GUI scale 2) with the wheel; no gauge without the key; scaled at GUI scale 3; as lit against the sky',
      n0 >= 12 and n1 >= 12 and abs(y0 - y1 - 8) <= 1 and tinted('look_m_hold_quiet', NETHERITE, *GAUGE_L) == 0 and n3 > 1.5 * n1 and sky >= 12,
      f'lit pixels {n0} at y {y0:.1f}, after two notches {n1} at y {y1:.1f}, scale 3: {n3}, against the sky {sky}')
cu = {n: (tinted(n, COPPER, -44, -8), tinted(n, COPPER, 8, 44)) for n in ('look_m_copper_g2', 'look_m_copper_dual_g2', 'look_m_copper_dual_g3')}
check('M pixels: a Copper Rig tints the marks copper at GUI scale 2 and 3 (acting side only in Reach, both for Dual Wield)',
      cu['look_m_copper_g2'][0] == 0 and cu['look_m_copper_g2'][1] > 20 and min(cu['look_m_copper_dual_g2']) > 20
      and min(cu['look_m_copper_dual_g3']) > 1.5 * min(cu['look_m_copper_dual_g2']), f'copper-tinted pixels left, right {cu}')
ch2, ch3 = tinted('look_m_copper_hold_g2', COPPER, *PAD_L), tinted('look_m_copper_hold_g3', COPPER, -39, -15, -18, 15)
check('M pixels: a Copper Rig\'s holding pad is copper at GUI scale 2 and 3', ch2 > 20 and ch3 > 1.5 * ch2
      and tinted('look_m_copper_hold_g2', NETHERITE, *QUIET) == 0, f'copper-tinted pixels {ch2}, scale 3: {ch3}')

print('== Back Slot')
if curios:
    check('B Back Slot Rig fitted over an iron chestplate', cact.get('back', {}).get('fitted'), f"{cact.get('back')}")
else:
    print('SKIP B Back Slot: run with GRADLE_ARGS=-PwithCurios')

exc = [l for l in (c + s).splitlines() if 'Exception' in l and 'ApiStatus' not in l and 'authlib' not in l and 'neoforge-client.new.tmp.toml' not in l
       and 'Error loading class: net/irisshaders/' not in l and 'org.gradle.' not in l and 'MessageIOException' not in l]
check('E exceptions', not exc, f'{len(exc)} exception lines' + (f': {exc[0][:200]}' if exc else ''))
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
