"""Grab pass/fail from a grab suite log set. usage: grab_metrics.py tools/logs/<tag>-grab"""
import re, sys, math
pre = sys.argv[1]
c = open(f'{pre}-client.log', errors='replace').read()
c2 = open(f'{pre}-client2.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
fails = []

def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok: fails.append(name)

def vec(t):
    return None if t == '-' else tuple(float(x) for x in t.split(','))

def dist(a, b):
    return math.dist(a, b) if a and b else 99.0

spos = {}
for m in re.finditer(r'SPOS (\S+) gt=(\d+) Dev pos=(\S+) .*? state=(\w+) .*? air=(\d+) .*? gheld=(-?\d+) gheavy=(\w+) gship=\S+ gcentre=(\S+) gmass=\S+ adebt=(\S+) mspd=(\S+) xp=\d+ fitted=(\w+)', s):
    spos[m[1]] = dict(gt=int(m[2]), pos=vec(m[3]), state=m[4], air=int(m[5]), held=int(m[6]), heavy=m[7] == 'true', centre=vec(m[8]),
                      debt=float(m[9]), mspd=float(m[10]), fitted=m[11] == 'true')
strace = {}
for m in re.finditer(r'STRACE gt=(\d+) Dev pos=\S+ .*? air=(\d+) .*? gheld=(-?\d+) gheavy=\w+ gship=\S+ gcentre=(\S+) gmass=\S+ adebt=(\S+)', s):
    strace[int(m[1])] = dict(air=int(m[2]), held=int(m[3]), centre=vec(m[4]), debt=float(m[5]))
smob = {}
first = {}
for m in re.finditer(r'SMOB gt=(\d+) id=(\d+) type=(\S+) pos=(\S+) v=(\S+) nograv=\w+ ground=(\w+) hp=(\S+) fall=\S+ box=(\S+) veh=(\S+) pinned=(\S+) thrown=(\w+) swell=(\S+) target=(\S+)', s):
    d = dict(gt=int(m[1]), type=m[3], pos=vec(m[4]), v=vec(m[5]), ground=m[6] == 'true', hp=float(m[7]), box=vec(m[8]), veh=m[9], pinned=m[10],
             thrown=m[11] == 'true', swell=None if m[12] == '-' else float(m[12]), target=m[13])
    smob[(d['gt'], m[2])] = d
    first.setdefault(m[2], d)
grab = [dict(gt=int(m[0]), kind=m[1], target=m[2], type=m[3], why=m[4], arms=int(m[5]), speed=float(m[6]))
        for m in re.findall(r'SGRAB gt=(\d+) Dev kind=(\w+) target=(\S+) type=(\S+) why=(\S+) actuators=(\d+) speed=(\S+)', s)]
garm = {(int(m[0]), m[1], m[2]): m[3] for m in re.findall(r'SGRAB gt=(\d+) Dev kind=(\w+) target=(\S+) .*? arm=(\S+)', s)}
sg = {m[0]: dict(arm=m[1], dist=float(m[2]), place=m[3] == 'true') for m in re.findall(r'SPOS (\S+) gt=\d+ Dev .*? garm=(\S+) gdist=(\S+) gplace=(\w+) dual=\w+', s)}
cspot = {int(m[0]): (None if m[1] == '-' else vec(m[1]), m[2] == 'true', m[3]) for m in re.findall(r'TRACE t=\d+ gt=(\d+) .*? spot=(\S+) spotok=(\w+) aff=(\w+)', c)}
wheel = [dict(gt=int(m[0]), n=int(m[1]), canceled=m[2] == 'true', slot=int(m[3])) for m in re.findall(r'CWHEEL gt=(\d+) n=(-?\d+) canceled=(\w+) slot=(\d+)', c)]
event = [(int(g), e, float(n)) for g, e, n in re.findall(r'SRIGEVENT gt=(\d+) Dev event=(\w+) number=(\S+)', s)]
hurt = [dict(gt=int(m[0]), id=m[1], source=m[2], amount=float(m[3]), by=m[4]) for m in re.findall(r'MHURT gt=(\d+) id=(\d+) type=\S+ source=(\S+) amount=(\S+) by=(\S+)', s)]
far = [dict(gt=int(m[0]), at=m[1], far=m[2], asking=m[3] == 'true') for m in re.findall(r'SFAR gt=(\d+) Dev kind=interact at=(\S+) .*? far=(\w+) sight=\w+ asking=(\w+)', s)]
ctr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) .*? gheld=(-?\d+) gheavy=\w+ gship=\S+ far=(\d+) xh=(\S+) hmob=(\S+) hpt=(\S+) xd=(\S+) limp=(\w+)', c):
    ctr[int(m[1])] = dict(held=int(m[2]), far=int(m[3]), xh=m[4], hmob=vec(m[5]), hpt=vec(m[6]), xd=None if m[7] == '-' else float(m[7]))
lmob = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) .*? lmob=(\S+)', c):
    if m[2] != '-':
        a, b = m[2].split(';')
        lmob[int(m[1])] = (vec(a), vec(b))
rwalk = {int(g): dict(held=int(h), hmob=vec(p)) for g, h, p in re.findall(r'RWALK gt=(\d+) name=Dev .*? gheld=(-?\d+) gship=\S+ hmob=(\S+)', c2)}
cpos = {m[0]: dict(gt=int(m[1]), pos=vec(m[2])) for m in re.findall(r"CPOS (\S+) gt=(\d+) pos=(\S+)", c)}
cact = {m[0]: dict(held=int(m[1]), heavy=m[2] == 'true', hud=m[3], limp=m[4] == 'true', speed=float(m[5]))
        for m in re.findall(r'CACT (\S+) gt=\d+ held=(-?\d+) heavy=(\w+) .*? hud="([^"]*)" fitted=\w+ limp=(\w+) .*? speed=(\S+)', c)}
overlay = [(int(g), t) for g, t in re.findall(r'OVERLAY gt=(\d+) text="([^"]*)"', c)]

def mob(x, z):
    return next((i for i, d in first.items() if abs(d['pos'][0] - x) < 0.05 and abs(d['pos'][2] - z) < 0.05), None)

def track(i, a, b):
    return [smob[(g, i)] for g in range(a, b + 1) if (g, i) in smob]

def grabs(kind, i):
    return [g for g in grab if g['kind'] == kind and g['target'] == i]

def paid(a, b):
    return (spos[a]['air'] - spos[b]['air']) + (spos[b]['debt'] - spos[a]['debt'])

print('== Setup')
want = {'s1': (11204.5, 0.5), 'p1': (11202.5, 12.5), 'h1': (11203.5, 36.5), 'w1': (11203.5, 40.5), 'g1': (11203.5, 48.5),
        'boss': (11205.5, 60.5), 'wolf': (11203.5, 68.5), 's2': (11203.5, 72.5), 'p4': (11203.5, 84.5),
        'p5': (11203.5, 96.5), 'w2': (11203.5, 100.5), 'h2': (11203.5, 104.5), 'p6': (11203.5, 108.5), 'p7': (11203.5, 112.5), 'p8': (11203.5, 116.5),
        'p9': (11203.5, 120.5), 'w9': (11203.5, 124.5), 'p10': (11202.5, 132.5), 'p11': (11202.5, 134.5), 'h3': (11199.5, 142.0)}
ids = {k: mob(*v) for k, v in want.items()}
for k, i in ids.items():
    check(f'[setup] {k} spawned', i is not None, f"id {i} {first[i]['type'] if i else '-'}")
g1a = spos.get('g1a', {})
check('[setup] Netherite Rig worn and fitted, Air 900', g1a.get('fitted') and g1a.get('air') == 900, f'{g1a}')
if not all(ids.values()) or not g1a:
    print('RESULT FAIL setup'); sys.exit(0)

print('== G1 light sheep')
s1 = ids['s1']
st = grabs('start', s1)
check('G1 start: light, one Actuator', st and st[0]['why'] == 'light' and st[0]['arms'] == 1, f'{st[:1]}')
a, b = spos['g1held']['gt'], spos['g1still']['gt']
off = [dist(smob[(g, s1)]['box'], strace[g]['centre']) for g in range(a, b + 1) if (g, s1) in smob and g in strace]
check('G1 server: held 40 ticks at the hold point (box within 0.6)', len(off) >= 38 and max(off) <= 0.6, f'{len(off)} ticks, max {max(off, default=99):.3f}')
ct = [ctr[g] for g in range(a + 5, b + 1) if g in ctr and ctr[g]['held'] == int(s1)]
coff = [dist(t['hmob'], t['hpt']) for t in ct]
check('G1 wearer client: mob at the hold point (after 5 ticks of entity lerp)', len(ct) >= 30 and max(coff) <= 0.6, f'{len(ct)} ticks, max {max(coff, default=99):.3f}')
check('G1 off the crosshair', ct and all(t['xh'] != s1 and (t['xd'] or 0) > 0.3 for t in ct),
      f"xh {sorted({t['xh'] for t in ct})} xd min {min((t['xd'] or 0 for t in ct), default=0):.3f}")
ob = [dist(rwalk[g]['hmob'], smob[(g, s1)]['box']) for g in range(a + 5, b + 1) if g in rwalk and (g, s1) in smob and rwalk[g]['held'] == int(s1)]
check('G1 observer (Dev2) sees the held mob at the server spot', len(ob) >= 25 and max(ob) <= 0.6, f'{len(ob)} ticks, max {max(ob, default=99):.3f}')
w0, w1 = smob.get((b, s1)), smob.get((spos['g1walk']['gt'], s1))
moved = (w1['box'][0] - w0['box'][0]) if w0 and w1 else 0
check('G1 walked 8+ blocks holding, the mob came along', spos['g1walk']['held'] == int(s1) and moved >= 8 and dist(w1['box'], strace[spos['g1walk']['gt']]['centre']) <= 0.6,
      f"mob moved {moved:.2f}, held {spos['g1walk']['held']}")
check('G1 Active and back Stowed keep the hold', spos['g1active']['state'] == 'ACTIVE' and spos['g1active']['held'] == int(s1)
      and spos['g1stowed']['state'] == 'STOWED' and spos['g1stowed']['held'] == int(s1), f"{spos['g1active']['state']} {spos['g1active']['held']} / {spos['g1stowed']['state']} {spos['g1stowed']['held']}")
rl = grabs('release', s1)
after = track(s1, rl[0]['gt'] + 1, rl[0]['gt'] + 40) if rl else []
calm = [d for d in after if d['pinned'] == 'calm']
check('G1 set down: why=release, then calm, then free on the ground', rl and rl[0]['why'] == 'release' and 15 <= len(calm) <= 25
      and after and after[-1]['pinned'] == '-' and after[-1]['ground'], f"{rl[:1]} calm {len(calm)} ticks, end {after[-1]['pinned'] if after else '-'}")
hs = re.search(rf'SGRAB gt=\d+ Dev kind=release target={s1} .*? ticks=(\d+) air=(\S+)', s)
check('G1 hold summary on the release line: ticks held, Air paid', hs and rl and st and int(hs[1]) == rl[0]['gt'] - st[0]['gt'] and float(hs[2]) > 0, f'{hs and hs.groups()}')
pr = far[0] if far else None
p0 = strace.get(st[0]['gt'] - 1) if st else None
p1 = strace.get(st[0]['gt'] + 1) if st else None
probe_paid = (p0['air'] - p1['air']) + (p1['debt'] - p0['debt']) if p0 and p1 else 99
check('G1 claim probe (Far, 3.56 away) asks, charges no Far Use Air', pr and pr['asking'] and pr['far'] == 'FAR' and probe_paid < 0.1 and all(f['asking'] for f in far)
      and not any(t['far'] for t in ctr.values()), f"{pr} paid over the claim {probe_paid:.4f}, client far ttl max {max(t['far'] for t in ctr.values())}")

print('== G2 Throw')
p1i = ids['p1']
th = grabs('throw', p1i)
fl = [d for d in track(p1i, th[0]['gt'], th[0]['gt'] + 5) if d['thrown']] if th else []
v0 = math.hypot(*fl[0]['v']) if fl else 0
check('G2 Throw 1.2 b/t', th and abs(th[0]['speed'] - 1.2) < 1e-3 and v0 >= 0.96, f"{th[:1]} first flight speed {v0:.3f}")
imp = [h for h in hurt if h['id'] == p1i and h['source'] == 'tentaworks.impact']
ev = [e for e in event if e[1] == 'impact' and e[0] <= spos['g2rest']['gt']]
check('G2 Impact damage once, matches the event', len(imp) == 1 and len(ev) == 1 and abs(imp[0]['amount'] - ev[0][2]) < 0.01 and imp[0]['by'] == 'Dev', f'{imp} {ev}')
td = [e for e in event if e[1] == 'throw_distance']
rest = smob.get((spos['g2rest']['gt'], p1i))
check('G2 flew 4+ blocks, thrown cleared at rest', td and td[0][2] > 4 and rest and not rest['thrown'] and rest['ground'], f"{td} rest {rest and (rest['thrown'], rest['ground'])}")

print('== G3 creeper pinned')
cr = next((g for g in grab if g['type'] == 'minecraft:creeper' and g['kind'] == 'start'), None)
ci = cr['target'] if cr else None
crl = grabs('release', ci) if ci else []
if cr and crl:
    at = smob.get((cr['gt'], ci), {})
    held = track(ci, cr['gt'] + 2, crl[0]['gt'] - 1)
    calm = [d for d in track(ci, crl[0]['gt'] + 1, crl[0]['gt'] + 25) if d['pinned'] == 'calm']
    later = track(ci, crl[0]['gt'] + 1, crl[0]['gt'] + 60)
    sw = [d['swell'] or 0 for d in held]
    check('G3 creeper swelling at the Grab (setup, timing)', (at.get('swell') or 0) > 0, f"swell {at.get('swell')} target {at.get('target')}")
    check('G3 held: pinned, the swell never grows, no blast', held and all(d['pinned'] == 'held' for d in held) and all(y <= x + 1e-6 for x, y in zip(sw, sw[1:]))
          and not [x for x in hurt if x['id'] == ci and x['source'] != 'genericKill'], f"{len(held)} ticks, swell {sw[0] if sw else '-'} -> {sw[-1] if sw else '-'}")
    cs = [d['swell'] or 0 for d in calm]
    free = [d for d in later if d['pinned'] == '-']
    check('G3 calm 20 ticks without swelling, swells again once free', 15 <= len(calm) <= 25 and all(y <= x + 1e-6 for x, y in zip(cs, cs[1:]))
          and free and max(d['swell'] or 0 for d in free) > (cs[-1] if cs else 0), f"calm {len(calm)} ticks, swell after {max((d['swell'] or 0 for d in free), default=0):.3f}")
else:
    check('G3 creeper grabbed and set down', False, f'{cr} {crl}')

print('== G4 heavy vs light')
h = grabs('start', ids['h1'])
check('G4 horse heavy: both Actuators, speed x0.6', h and h[0]['why'] == 'heavy' and h[0]['arms'] == 2 and spos['g4h']['heavy'] and abs(spos['g4h']['mspd'] - 0.06) < 1e-4
      and cact.get('g4h', {}).get('heavy') and abs(cact['g4h']['speed'] - 0.6) < 1e-3 and 'heavy' in cact['g4h']['hud'], f"{h[:1]} mspd {spos['g4h']['mspd']} {cact.get('g4h')}")
w = grabs('start', ids['w1'])
check('G4 cow light: one Actuator, full speed', w and w[0]['why'] == 'light' and w[0]['arms'] == 1 and not spos['g4c']['heavy'] and abs(spos['g4c']['mspd'] - 0.1) < 1e-4, f"{w[:1]} mspd {spos['g4c']['mspd']}")
bare = dist(spos['g4w1']['pos'], spos['g4w0']['pos'])
heavy = dist(spos['g4g1']['pos'], spos['g4g0']['pos'])
check('G4 iron golem heavy: walks at about 0.6 of bare speed (timing)', spos['g4g0']['heavy'] and spos['g4g1']['held'] == int(ids['g1']) and bare > 4 and 0.5 <= heavy / bare <= 0.72,
      f'bare {bare:.2f}, heavy {heavy:.2f}, ratio {heavy / bare if bare else 0:.3f}')

print('== G5 refusals')
for name, key, why, text in (('boss', 'boss', 'boss', 'Too mighty to Grab: a boss'), ('player', None, 'player', "Players can't be Grabbed"),
                             ("someone else's wolf", 'wolf', 'owned', "That's someone else's animal")):
    r = [g for g in grab if g['kind'] == 'refuse' and g['why'] == why and (key is None or g['target'] == ids[key])]
    note = r and any(t == text and 0 <= g - r[0]['gt'] <= 5 for g, t in overlay)
    check(f'G5 {name} refused', r and not [g for g in grab if g['kind'] == 'start' and g['target'] == r[0]['target']] and note, f'{r[:1]} note {bool(note)}')

print('== G6 seat')
d = smob.get((spos['g6']['gt'], ids['s2']))
check('G6 set down onto a Create seat', d and d['veh'] == 'create:seat', f"veh {d and d['veh']}")

print('== G7 release reasons')
rs = [g['why'] for g in grabs('release', ids['p4'])]
check('G7 releases: no Air, Crawl, Rig off, out of Reach, logout', rs == ['air', 'crawl', 'rig_off', 'far', 'logout'], f'{rs}')
check('G7 no Air Stowed: Actuators limp', cact.get('g7limp', {}).get('limp') and cact['g7limp']['hud'] == 'No Air: Actuators limp', f"{cact.get('g7limp')}")
check('G7 Crawl case was Active', spos['g7c']['state'] == 'ACTIVE' and spos['g7c']['held'] == int(ids['p4']), f"{spos['g7c']['state']} held {spos['g7c']['held']}")
d = smob.get((spos['g7lback']['gt'], ids['p4']))
check('G7 after logout and rejoin: nothing held, the pig free', cact.get('g7lback', {}).get('held') == -1 and d and d['pinned'] == '-', f"{cact.get('g7lback')} pig {d and d['pinned']}")


print('== G8 hands, set-down spot, wheel')
p5 = ids['p5']
st = grabs('start', p5)
th = grabs('throw', p5)
check('G8a item in the main hand: the off-hand Actuator Grabs', st and garm.get((st[0]['gt'], 'start', p5)) == 'OFF' and sg.get('g8aheld', {}).get('arm') == 'OFF', f"{st[:1]} arm {st and garm.get((st[0]['gt'], 'start', p5))} {sg.get('g8aheld')}")
check('G8a plain attack with the item: no Throw; the Grab key held + attack Throws', th and spos['g8aswing']['held'] == int(p5) and th[0]['gt'] > spos['g8aswing']['gt'] and spos['g8athrown']['held'] == -1,
      f"held at swing {spos['g8aswing']['held']}, throw {th[:1]}, after {spos['g8athrown']['held']}")
for tag, key, why, text in (('G8b both hands full: a light cow refused', 'w2', 'hands_full', 'Hands full: empty a hand to Grab'),
                            ('G8c one hand full: a heavy horse refused', 'h2', 'two_hands', 'Needs both Actuators: empty both hands')):
    r = [g for g in grab if g['kind'] == 'refuse' and g['target'] == ids[key]]
    note = r and any(t == text and 0 <= g - r[0]['gt'] <= 5 for g, t in overlay)
    check(tag, r and r[0]['why'] == why and not grabs('start', ids[key]) and note, f"{r[:1]} note {bool(note)}")
p6 = ids['p6']
st = grabs('start', p6)
rl = grabs('release', p6)
check('G8d off hand full: the main-hand Actuator Grabs; an item in its hand sets the pig down (why=hand)',
      st and garm.get((st[0]['gt'], 'start', p6)) == 'MAIN' and rl and rl[0]['why'] == 'hand', f"{st[:1]} arm {st and garm.get((st[0]['gt'], 'start', p6))} release {rl[:1]}")
p7 = ids['p7']
rl = grabs('release', p7)
pl = [g['gt'] for g in grabs('place', p7)]
mk = cpos.get('g8espot', {}).get('gt')
sp = next((cspot[g] for g in range(mk, mk - 6, -1) if g in cspot), None) if mk else None
down = smob.get((spos['g8edown']['gt'], p7))
gap = dist(down['pos'], sp[0]) if down and sp and sp[0] else 99
check('G8e set down at the looked spot: valid spot, place then release, the pig stands there',
      sp and sp[1] and pl and rl and rl[0]['why'] == 'release' and 0 < rl[0]['gt'] - pl[0] <= 8 and gap <= 0.3,
      f"spot {sp} place {pl[-1:] if pl else '-'} release {rl[:1]} pig {down and down['pos']} gap {gap:.3f}")
p8 = ids['p8']
f0, f1, f2 = sg.get('g8f0', {}), sg.get('g8f1', {}), sg.get('g8f2', {})
w0 = [w for w in wheel if w['n'] == 2]
w1 = [w for w in wheel if w['n'] == 1]
check('G8f the Grab key held + 2 notches: the hold 1.0 farther, the hotbar does not scroll', f0 and f1 and abs(f1['dist'] - f0['dist'] - 1.0) < 0.02
      and w0 and w0[0]['canceled'], f"gdist {f0.get('dist')} -> {f1.get('dist')}, wheel {w0[:1]}")
check('G8f releasing the key after the wheel sets nothing down; the wheel alone scrolls the hotbar; a tap sets down',
      spos['g8f2']['held'] == int(p8) and w1 and not w1[0]['canceled'] and w0 and w1[0]['slot'] != w0[0]['slot'] and grabs('release', p8),
      f"held {spos['g8f2']['held']} wheel {w1[:1]} release {grabs('release', p8)[:1]}")

print('== G8g Set Down: the mob never shows past the spot')

def setdown(i, tap):
    """The client's spot at the tap, the server feet's gap to it at the tap, and the wearer's client view of the mob (feet and lerp target) to rest."""
    sp = next((cspot[g][0] for g in range(tap, tap - 4, -1) if g in cspot and cspot[g][1] and cspot[g][0]), None)
    rl = [g['gt'] for g in grab if g['kind'] == 'release' and g['target'] == i and g['gt'] >= tap]
    m0 = smob.get((tap, i))
    if not sp or not rl or not m0:
        return None
    half = m0['box'][1] - m0['pos'][1]
    path = [(g, (lmob[g][0][0], lmob[g][0][1] - half, lmob[g][0][2]), lmob[g][1]) for g in range(tap + 1, rl[0] + 12) if g in lmob]
    return sp, dist(m0['pos'], sp), path

def away(path, sp):
    """The most the wearer's client view of the mob moves away from the spot in one tick (feet, lerp target), and where it ends."""
    worst = 0.0
    for k in (1, 2):
        d = [dist(p[k], sp) for p in path]
        worst = max([worst] + [b - a for a, b in zip(d, d[1:])])
    return worst, dist(path[-1][1], sp) if path else 99

for tag, key in (('G3 creeper', 'c1'), ('G4 horse', 'h1'), ('G4 cow', 'w1'), ('G8e pig, floor 3.5 ahead', 'p7'),
                 ('G8g1 pig, wheel +3, floor 2 ahead', 'p10'), ('G8g2 pig, wheel +2, slab top near eye level', 'p11'),
                 ('G8g3 heavy horse, slab top 1.5 ahead', 'h3')):
    i = ids.get(key) if key != 'c1' else next((g['target'] for g in grab if g['kind'] == 'place' and g['type'] == 'minecraft:creeper'), None)
    pl = grabs('place', i) if i else []
    r = setdown(i, pl[0]['gt']) if pl else None
    if not r:
        check(f'[bug] {tag}: Set Down at the looked spot', False, f'no place/spot/trace ({pl[:1]})')
        continue
    sp, gap0, path = r
    worst, end = away(path, sp)
    check(f'[bug] {tag}: on the wearer\'s client the mob never moves away from the spot after the tap, ends on it', len(path) >= 8 and worst <= 0.05 and end <= 0.1,
          f'{len(path)} ticks, worst step away {worst:.3f}, end {end:.3f}, tap gap {gap0:.3f}')

print('== G9 inventory screen (creative, survival)')
srig = {int(g): dict(state=st_, air=int(a), dmg=int(d), held=int(h)) for g, st_, a, d, h in
        re.findall(r'STRACE gt=(\d+) Dev pos=\S+ v=\S+ state=(\w+) .*? air=(\d+) .*? rigdmg=(\d+) .*? gheld=(-?\d+)', s)}
cstate = {int(g): st_ for g, st_ in re.findall(r'TRACE t=\d+ gt=(\d+) pos=\S+ d=\S+ cmd=\S+ held=\d+ state=(\w+)', c)}
screens = re.findall(r'SCREEN (\S+)', c)
p9, w9 = ids['p9'], ids['w9']

def window(a, b):
    ga, gb = spos.get(a, {}).get('gt'), spos.get(b, {}).get('gt')
    if ga is None or gb is None: return [], []
    return [srig[g] for g in range(ga, gb + 1) if g in srig], [cstate[g] for g in range(ga, gb + 1) if g in cstate]

sw, cw = window('g9held', 'g9closed')
check('[bug] G9 creative: the inventory key opens the creative inventory and the Rig stays Active on the server and the client',
      any(x.endswith('CreativeModeInventoryScreen') for x in screens) and len(sw) > 30 and all(x['state'] == 'ACTIVE' for x in sw)
      and cw and all(x == 'ACTIVE' for x in cw),
      f"screens {screens}, server {sorted(set(x['state'] for x in sw))} over {len(sw)}, client {sorted(set(cw))} over {len(cw)}")
rl = grabs('release', w9)
check('[guard] G9 creative: Throw, hit and Grab done; the cow stays held through the screen',
      grabs('throw', p9) and any(h['id'] == w9 and h['by'] == 'Dev' for h in hurt) and sw and all(x['held'] == int(w9) for x in sw),
      f"throw {grabs('throw', p9)[:1]} hits {[h['amount'] for h in hurt if h['id'] == w9]} held {sorted(set(x['held'] for x in sw))} release {rl[:1]}")
a0, a1 = spos.get('g9a', {}), spos.get('g9closed', {})
d0, d1 = srig.get(a0.get('gt'), {}), srig.get(a1.get('gt'), {})
check('[guard] G9 creative: no Air paid and no wear on the Rig (Throw, hit, hold)',
      a0 and a1 and a0['air'] == a1['air'] and a0['debt'] == a1['debt'] and d0 and d1 and d0['dmg'] == d1['dmg'],
      f"air {a0.get('air')} -> {a1.get('air')}, debt {a0.get('debt')} -> {a1.get('debt')}, damage {d0.get('dmg')} -> {d1.get('dmg')}")
sw, cw = window('g9held', 'g9sclosed')
end = spos.get('g9sclosed', {}).get('gt', 0)
check('[guard] G9s survival: the inventory key opens the survival inventory; Active and the hold go on until the tap',
      any(x.endswith('.InventoryScreen') for x in screens) and sw and all(x['state'] == 'ACTIVE' and x['held'] == int(w9) for x in sw)
      and cw and all(x == 'ACTIVE' for x in cw) and rl and rl[0]['gt'] > end and rl[0]['why'] == 'release',
      f"screens {screens}, server {sorted(set(x['state'] for x in sw))}, client {sorted(set(cw))}, release {rl[:1]} after {end}")

exc = [l for l in (c + c2 + s).splitlines() if 'Exception' in l and 'ApiStatus' not in l and 'authlib' not in l and 'neoforge-client.new.tmp.toml' not in l
       and 'Error loading class: net/irisshaders/' not in l and 'org.gradle.' not in l and 'MessageIOException' not in l
       and 'sun.nio.fs.UnixException' not in l]
check('E exceptions', not exc, f'{len(exc)} exception lines' + (f': {exc[0][:200]}' if exc else ''))
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
