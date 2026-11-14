"""Ship Grab pass/fail from a shipgrab suite log set (-PwithSable). usage: shipgrab_metrics.py tools/logs/<tag>-shipgrab"""
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

ships = {}
for m in re.finditer(r'SSHIP gt=(\d+) name=(\S+) id=\S+ pos=(\S+) q=(\S+) v=(\S+)', s):
    ships.setdefault(m[2], {})[int(m[1])] = dict(pos=vec(m[3]), q=vec(m[4]), v=vec(m[5]))
if not ships:
    print('SKIP no SSHIP lines: run with GRADLE_ARGS=-PwithSable')
    print('RESULT SKIP'); sys.exit(0)
spos = {}
for m in re.finditer(r'SPOS (\S+) gt=(\d+) Dev pos=(\S+) .*? air=(\d+) .*? gheld=(-?\d+) gheavy=(\w+) gship=(\S+) gcentre=(\S+) gmass=(\S+) adebt=\S+ mspd=(\S+) xp=\d+ fitted=(\w+)', s):
    spos[m[1]] = dict(gt=int(m[2]), pos=vec(m[3]), air=int(m[4]), held=int(m[5]), heavy=m[6] == 'true', ship=m[7], centre=vec(m[8]), mass=float(m[9]),
                      mspd=float(m[10]), fitted=m[11] == 'true')
dev = {int(g): vec(p) for g, p in re.findall(r'STRACE gt=(\d+) Dev pos=(\S+)', s)}
grab = [dict(gt=int(m[0]), kind=m[1], why=m[2], arms=int(m[3]), speed=float(m[4]), mass=float(m[5]))
        for m in re.findall(r'SGRAB gt=(\d+) Dev kind=(\w+) target=-1 type=ship why=(\S+) actuators=(\d+) speed=(\S+) mass=(\S+)', s)]
sg = {m[0]: float(m[1]) for m in re.findall(r'SPOS (\S+) gt=\d+ Dev .*? gdist=(\S+) gplace=\w+ dual=\w+', s)}
wheel = [dict(gt=int(m[0]), n=int(m[1]), canceled=m[2] == 'true') for m in re.findall(r'CWHEEL gt=(\d+) n=(-?\d+) canceled=(\w+) slot=\d+', c)]
conf = re.findall(r'SCONFIG gt=\d+ path=grab\.massCap\.netherite value=(\S+)', s)
cact = {m[0]: dict(heavy=m[1] == 'true', ship=m[2], hud=m[3], speed=float(m[4])) for m in re.findall(r'CACT (\S+) gt=\d+ held=-?\d+ heavy=(\w+) ship=(\S+) .*? hud="([^"]*)" .*? speed=(\S+)', c)}
overlay = [(int(g), t) for g, t in re.findall(r'OVERLAY gt=(\d+) text="([^"]*)"', c)]
wearer = {int(g): sh for g, sh in re.findall(r'TRACE t=\d+ gt=(\d+) .*? gship=(\S+) far=', c)}
seen = {int(g): sh for g, sh in re.findall(r'RWALK gt=(\d+) name=Dev .*? gship=(\S+) hmob=', c2)}

def at(name, gt):
    t = ships.get(name, {})
    g = min(t, key=lambda k: abs(k - gt), default=None)
    return t[g] if g is not None and abs(g - gt) <= 2 else None

def kind(k, why=None):
    return [g for g in grab if g['kind'] == k and (why is None or g['why'] == why)]

def noted(text, gt):
    return any(t == text and 0 <= g - gt <= 5 for g, t in overlay)

print('== Setup')
for n, x in (('sg', 11407.5), ('sh', 11428.5), ('sc', 11447.5)):
    p = at(n, min(ships.get(n, {0: 0})))
    check(f'[setup] Ship {n} assembled', p and abs(p['pos'][0] - x) < 0.2, f"{p and p['pos']}")
k0 = spos.get('k0', {})
check('[setup] Netherite Rig worn and fitted', k0.get('fitted') and k0.get('air') == 900, f'{k0}')
if not k0 or 'sg' not in ships:
    print('RESULT FAIL setup'); sys.exit(0)

print('== K small Ship')
st = kind('start')
k1 = spos['k1']
check('K1 Grab: a Ship, both Actuators, mass 54', st and st[0]['why'] == 'ship' and st[0]['arms'] == 2 and abs(st[0]['mass'] - 54) < 0.5 and k1['ship'] != '-',
      f"{st[:1]} gship {k1['ship']}")
pf = re.findall(r'SFAR gt=(\d+) Dev kind=rclick at=\S+ .*? far=(\w+) sight=\w+ asking=(\w+) canceled=(\w+)', s)
pf = [x for x in pf if st and int(x[0]) == st[0]['gt']]
check('K1 the Ship block click is left to Sable (PLOT, not cancelled), claim probe asks', pf and pf[0][1] == 'PLOT' and pf[0][2] == 'true' and pf[0][3] == 'false', f'{pf}')
ca = cact.get('k1', {})
check('K1 heavy hold: speed x0.6, HUD line', k1['heavy'] and abs(k1['mspd'] - 0.06) < 1e-4 and ca.get('heavy') and abs(ca.get('speed', 0) - 0.6) < 1e-3
      and ca.get('hud') == 'Holding Ship heavy', f"mspd {k1['mspd']} {ca}")
def rot(q, v):
    x, y, z, w = q
    t = (2 * (y * v[2] - z * v[1]), 2 * (z * v[0] - x * v[2]), 2 * (x * v[1] - y * v[0]))
    return (v[0] + w * t[0] + y * t[2] - z * t[1], v[1] + w * t[1] + z * t[0] - x * t[2], v[2] + w * t[2] + x * t[1] - y * t[0])

def heading(v):
    return math.degrees(math.atan2(v[2], v[0]))

def turn(a, b):
    return (b - a + 180) % 360 - 180

q0, q1 = spos['kq0'], spos['kq1']
s0, s1 = at('sg', q0['gt']), at('sg', q1['gt'])
e0, e1 = (q0['pos'][0], q0['pos'][1] + 1.62, q0['pos'][2]), (q1['pos'][0], q1['pos'][1] + 1.62, q1['pos'][2])
r0, r1 = [tuple(p - e for p, e in zip(sh['pos'], eye)) for sh, eye in ((s0, e0), (s1, e1))]
swing = turn(heading(r0), heading(r1))
spin = turn(heading(rot(s0['q'], (1, 0, 0))), heading(rot(s1['q'], (1, 0, 0))))
dist = math.hypot(r1[0], r1[2]) - math.hypot(r0[0], r0[2])
tilt = abs(rot(s1['q'], (0, 1, 0))[1] - rot(s0['q'], (0, 1, 0))[1])
check('K2 a quarter turn north swings the Ship round the wearer, its yaw turns with them', abs(swing + 90) <= 10 and abs(spin + 90) <= 10 and abs(dist) <= 0.5
      and abs(r1[1] - r0[1]) <= 0.5 and tilt <= 0.05, f'swing {swing:.1f} deg, ship yaw {spin:.1f} deg, distance change {dist:.3f}, height change {r1[1] - r0[1]:.3f}, tilt {tilt:.3f}')
a, b = spos['kw0']['gt'], spos['kw1']['gt']
off = [(at('sg', g)['pos'][0] - dev[g][0], at('sg', g)['pos'][2] - dev[g][2]) for g in range(a, b + 1) if g in dev and at('sg', g)]
dx = [math.dist(o, off[0]) for o in off]
walk = math.dist(spos['kw1']['pos'], spos['kw0']['pos'])
moved = math.dist(at('sg', b)['pos'], at('sg', a)['pos'])
check('K2 the Ship follows the wearer (offset within 0.5)', walk > 3 and moved > 3 and max(dx) <= 0.5, f'walk {walk:.2f}, ship {moved:.2f}, offset drift max {max(dx):.3f}')
rl = kind('release')
r0, r1 = at('sg', spos['kr0']['gt']), at('sg', spos['kr1']['gt'])
slide = math.dist(r0['pos'], r1['pos'])
check('K3 Set Down: why=release, the Ship stays', rl and rl[0]['why'] == 'release' and spos['kr0']['ship'] == '-' and abs(spos['kr0']['mspd'] - 0.1) < 1e-4 and slide < 0.3,
      f"{rl[:1]} slide {slide:.3f} over 40 ticks")
th = kind('throw')
t0, t1 = at('sg', spos['kt0']['gt']), at('sg', spos['kt1']['gt'])
fly = [at('sg', g) for g in range(th[0]['gt'] + 1, th[0]['gt'] + 6)] if th else []
v0 = max((math.hypot(*f['v']) / 20 for f in fly if f), default=0)
check('K4 Throw: 1.2 b/t, the Ship flies', th and abs(th[0]['speed'] - 1.2) < 1e-3 and v0 >= 0.96 and math.dist(t0['pos'], t1['pos']) > 2,
      f"{th[:1]} peak {v0:.3f} b/t, moved {math.dist(t0['pos'], t1['pos']):.2f}")
held = [g for g, sh in wearer.items() if sh == k1['ship']]
same = [g for g in held if seen.get(g) == k1['ship']]
check('K5 observer (Dev2) sees the same Ship held', len(held) >= 40 and len(same) >= 0.8 * len(held), f'wearer {len(held)} ticks, observer {len(same)}')

print('== M over the mass cap')
m = kind('refuse', 'mass')
check('M refused over grab.massCap.netherite 100 (mass 196), cap restored', m and abs(m[0]['mass'] - 196) < 0.5 and noted('Too heavy for this Rig', m[0]['gt'])
      and conf[:2] == ['100.0', '6000.0'] and not [g for g in grab if g['kind'] == 'start' and abs(g['mass'] - 196) < 0.5], f'{m[:1]} config {conf}')

print('== C crewed')
cr = kind('refuse', 'crewed')
check('C refused with Dev2 on board', cr and noted('Someone is on board', cr[0]['gt']), f'{cr[:1]}')
c2s = spos.get('c2', {})
sc = at('sc', c2s.get('gt', 0))
ctl = [g for g in st if cr and g['gt'] > cr[0]['gt']]
check('C control: Grabbed once Dev2 is off', ctl and c2s.get('ship', '-') not in ('-', k1['ship']) and sc and math.dist(c2s['centre'], sc['pos']) < 2.5,
      f"{ctl[:1]} gship {c2s.get('ship')}")


print('== W wheel, tap, two hands')
w = [x for x in wheel if x['n'] == -2]
d0, d1 = sg.get('c2'), sg.get('c2w')
check('W the Grab key + 2 notches closer: the hold 1.0 nearer along the look (or at the 1.0 floor), the hotbar does not scroll',
      d0 is not None and d1 is not None and (abs(d0 - d1 - 1.0) < 0.02 or (d1 == 1.0 and d0 > d1)) and w and w[0]['canceled'], f"gdist {d0} -> {d1}, wheel {w[:1]}")
rl = [g for g in kind('release') if spos.get('c2k') and g['gt'] >= spos['c2k']['gt'] and g['gt'] > spos['c2w']['gt'] + 5]
check('W releasing the key after the wheel keeps the Ship; a tap releases it (why=release)', spos.get('c2k', {}).get('ship', '-') not in ('-',) and rl and rl[0]['why'] == 'release',
      f"held {spos.get('c2k', {}).get('ship')} release {rl[:1]}")
t = kind('refuse', 'two_hands')
check('T an item in the main hand: a Ship refused (two_hands)', t and noted('Needs both Actuators: empty both hands', t[0]['gt']), f'{t[:1]}')

print('== O own Ship (Copper Rig on the deck sd)')
OWN = 'You stand on that Ship and nothing else holds you'
strace = [(mm.start(), int(mm[1]), vec(mm[2]), mm[3], int(mm[4]), int(mm[5]), mm[6])
          for mm in re.finditer(r'STRACE gt=(\d+) Dev pos=(\S+) v=\S+ state=(\w+) grips=(\d+) .*? sgrips=(\d+) carry=(\S+)', s)]
srv = {x[1]: dict(pos=x[2], state=x[3], grips=x[4], sgrips=x[5], carry=x[6]) for x in strace}
mk = {mm[1]: dict(gt=int(mm[2]), state=mm[3], grips=int(mm[4]), sgrips=int(mm[5]), carry=mm[6])
      for mm in re.finditer(r'SPOS (\S+) gt=(\d+) Dev pos=\S+ v=\S+ state=(\w+) grips=(\d+) .*? sgrips=(\d+) carry=(\S+)', s)}
ctr = {int(mm[1]): dict(pos=vec(mm[2]), d=vec(mm[3]), state=mm[4], sheld=int(mm[5]), carry=mm[6], aff=mm[7])
       for mm in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=(\S+) d=(\S+) cmd=\S+ held=\d+ state=(\w+) .*? sheld=(\d+) carry=(\S+) .*? aff=(\w+)', c)}
cmk = {mm[1]: int(mm[2]) for mm in re.finditer(r'CPOS (\S+) gt=(\d+)', c)}
obs = {int(g): int(n) for g, n in re.findall(r'RWALK gt=(\d+) name=Dev .*? rsgrips=(\d+)', c2)}
sgl = [(mm.start(), int(mm[1]), mm[2], mm[3], float(mm[4])) for mm in re.finditer(r'SGRAB gt=(\d+) Dev kind=(\w+) target=-1 type=ship why=(\S+) actuators=\d+ speed=\S+ mass=(\S+)', s)]
def aff(tag):
    g = cmk.get(tag)
    return ctr.get(g, {}).get('aff') if g is not None else None
def after(tag, k, why=None):
    g = mk.get(tag, {}).get('gt', 1 << 60)
    return [x for x in sgl if x[1] >= g and x[2] == k and (why is None or x[3] == why)]
if 'o1' not in mk or 'sd' not in ships:
    check('O section ran', False, f"marks {sorted(mk)} ships {sorted(ships)}")
else:
    o1, o2, o3 = mk['o1'], mk.get('o2', {}), mk.get('o3', {})
    r1 = after('o1', 'refuse')
    check('O1 Stowed, feet on the deck: refused (own), the crosshair cue said so first', o1['state'] == 'STOWED' and r1 and r1[0][3] == 'own'
          and noted(OWN, r1[0][1]) and aff('o1') == 'REFUSED', f"state {o1['state']} {r1[:1]} cue {aff('o1')}")
    r2 = after('o2', 'refuse')
    check('O2 Active, every Grip on the deck: refused (own), cue REFUSED', o2.get('state') == 'ACTIVE' and o2.get('grips', 0) >= 2
          and o2.get('sgrips') == o2.get('grips') and r2 and r2[0][3] == 'own' and noted(OWN, r2[0][1]) and aff('o2') == 'REFUSED',
          f"{o2} {r2[:1]} cue {aff('o2')}")
    st3 = after('o3', 'start')
    check('O3 Active, Grips on the wall and on the deck: Grabbed (mass 162), cue GRAB', o3.get('state') == 'ACTIVE' and o3.get('sgrips', 0) >= 1
          and o3.get('grips', 0) - o3.get('sgrips', 0) >= 1 and st3 and abs(st3[0][4] - 162) < 0.5 and aff('o3') == 'GRAB',
          f"{o3} start {st3[:1]} cue {aff('o3')}")
    if st3:
        g0, at0 = st3[0][1], st3[0][0]
        nxt = next((x for x in strace if x[0] > at0), None)
        check('O3 the deck Grips let go on the Grab tick (server): next STRACE sgrips=0, carry -, off-deck Grips kept',
              nxt and nxt[5] == 0 and nxt[6] == '-' and nxt[4] >= o3['grips'] - o3['sgrips'], f"{nxt and nxt[1:]}")
        g1 = mk.get('o4b', {}).get('gt', g0)
        sw = [srv[g] for g in range(g0 + 1, g1 + 1) if g in srv]
        cw = [ctr[g] for g in range(g0 + 3, g1 + 1) if g in ctr]
        ow = [obs[g] for g in range(g0 + 3, g1 + 1) if g in obs]
        check('O4 while held: no Grip on the held deck, no Carry Ship, still Active (server, wearer, observer)',
              len(sw) > 30 and all(x['sgrips'] == 0 and x['carry'] == '-' and x['state'] == 'ACTIVE' for x in sw)
              and len(cw) > 30 and all(x['sheld'] == 0 and x['carry'] == '-' and x['state'] == 'ACTIVE' for x in cw)
              and len(ow) > 20 and max(ow) == 0 and obs.get(cmk.get('o3'), 0) >= 1,
              f"server {len(sw)} ticks states {sorted({x['state'] for x in sw})} sgrips max {max((x['sgrips'] for x in sw), default=-1)}; "
              f"wearer {len(cw)} states {sorted({x['state'] for x in cw})} sheld max {max((x['sheld'] for x in cw), default=-1)}; "
              f"observer before {obs.get(cmk.get('o3'))} during max {max(ow, default=-1)}")
        jump = max((math.hypot(*ctr[g]['d']) for g in range(g0 - 1, g1 + 1) if g in ctr), default=9)
        agree = max((math.dist(ctr[g]['pos'], srv[g]['pos']) for g in range(g0, g0 + 6) if g in ctr and g in srv), default=9)
        check('O3 client and server agree on the wearer through the Grab (g0..g0+5 within 0.1)', agree < 0.1, f"max gap {agree:.3f}")
        warn = re.findall(r'Tentacle Rig of Dev [^\n]*|[^\n]*(?:moved too quickly|moved wrongly|moves faster than|kicked)[^\n]*', s[at0:next((x[0] for x in strace if x[1] > g1 + 20), len(s))])
        check('O4 a clean moment: no step over 0.25 on the wearer, no forced Falling, Stow or moved wrongly', jump < 0.25 and not warn,
              f"max step {jump:.3f}, warnings {warn[:2]}")
        a4, b4 = mk.get('o4a', {}).get('gt'), mk.get('o4b', {}).get('gt')
        sa, sb = at('sd', a4 or 0), at('sd', b4 or 0)
        rise = sb['pos'][1] - sa['pos'][1] if sa and sb else 0
        dy = srv[b4]['pos'][1] - srv[a4]['pos'][1] if a4 in srv and b4 in srv else 9
        check('O4 2 notches nearer lift the held deck, the wearer is not carried', rise >= 0.6 and abs(dy) < 0.1, f"deck rise {rise:.3f}, wearer dy {dy:.3f}")
    rl5 = after('o4b', 'release')
    rel = rl5[0] if rl5 else None
    stow = next((g for g in sorted(srv) if b4 and g > b4 and srv[g]['state'] == 'STOWED'), None) if st3 else None
    check('O5 Set Down onto the held deck: let go (underfoot) once the feet rest on it', rel and rel[3] == 'underfoot' and stow is not None and 0 <= rel[1] - stow <= 10,
          f"release {rel and rel[1:4]} Stowed at {stow}")
    if rel:
        dk = [at('sd', g)['pos'][1] for g in range(rel[1] + 5, rel[1] + 21) if at('sd', g)]
        dw = [srv[g]['pos'][1] for g in range(rel[1] + 5, rel[1] + 21) if g in srv]
        on = dk and dw and all(abs(w - k - 0.5) < 0.05 for w, k in zip(dw, dk))
        check('O5 after it the deck settles and the wearer stands on it, nothing lifts (rel+5..+20)', len(dk) > 10 and len(dw) > 10
              and max(dk) - min(dk) < 0.1 and max(dw) - min(dw) < 0.1 and on,
              f"deck y {min(dk, default=0):.3f}..{max(dk, default=0):.3f} wearer y {min(dw, default=0):.3f}..{max(dw, default=0):.3f} on deck {on}")
    o6 = mk.get('o6', {})
    g6 = cmk.get('o6')
    check('O6 after the release the deck is a place to stand again: Grips on it (server, observer)', o6.get('state') == 'ACTIVE' and o6.get('sgrips', 0) >= 1
          and obs.get(g6, 0) >= 1, f"{o6} observer {obs.get(g6)}")

    o7 = mk.get('o7', {})
    st7 = after('o7', 'start')
    check('O7 Active 4 from the wall, one Grip on it and three on the deck: Grabbed, cue GRAB', o7.get('state') == 'ACTIVE' and o7.get('grips') == 4
          and o7.get('sgrips') == 3 and st7 and abs(st7[0][4] - 162) < 0.5 and aff('o7') == 'GRAB', f"{o7} start {st7[:1]} cue {aff('o7')}")
    if st7:
        g7, at7 = st7[0][1], st7[0][0]
        nxt = next((x for x in strace if x[0] > at7), None)
        check('O7 the deck Grips let go on the Grab tick: next STRACE sgrips=0, carry -', nxt and nxt[5] == 0 and nxt[6] == '-', f"{nxt and nxt[1:]}")
        rl7 = [x for x in sgl if x[1] >= g7 and x[2] == 'release']
        e7 = rl7[0][1] if rl7 else g7 + 60
        sw = [srv[g] for g in range(g7 + 2, e7) if g in srv]
        cw = [ctr[g] for g in range(g7 + 3, e7) if g in ctr]
        check('O7 re-Gripped off the deck at once (server from the next packet): Active, 2+ Grips, none on the deck, no Carry Ship (server, wearer)',
              len(sw) > 30 and all(x['state'] == 'ACTIVE' and x['grips'] >= 2 and x['sgrips'] == 0 and x['carry'] == '-' for x in sw)
              and len(cw) > 30 and all(x['state'] == 'ACTIVE' and x['sheld'] == 0 and x['carry'] == '-' for x in cw),
              f"server {len(sw)} ticks states {sorted({x['state'] for x in sw})} grips min {min((x['grips'] for x in sw), default=-1)}; wearer {len(cw)} states {sorted({x['state'] for x in cw})}")
        jump = max((math.hypot(*ctr[g]['d']) for g in range(g7 - 1, e7 + 1) if g in ctr), default=9)
        agree = max((math.dist(ctr[g]['pos'], srv[g]['pos']) for g in range(g7, g7 + 6) if g in ctr and g in srv), default=9)
        warn = re.findall(r'Tentacle Rig of Dev [^\n]*|[^\n]*(?:moved too quickly|moved wrongly|moves faster than|kicked)[^\n]*',
                          s[at7:next((x[0] for x in strace if x[1] > e7 + 20), len(s))])
        check('O7 a clean moment: no client step over 0.25, client and server within 0.1, no Rig WARN or kick', jump < 0.25 and agree < 0.1 and not warn,
              f"max step {jump:.3f}, gap {agree:.3f}, warnings {warn[:2]}")
        check('O7 a tap lets the deck go (why=release)', rl7 and rl7[0][3] == 'release', f"{rl7[:1]}")

exc = [l for l in (c + c2 + s).splitlines() if 'Exception' in l and 'ApiStatus' not in l and 'authlib' not in l and 'neoforge-client.new.tmp.toml' not in l
       and 'Error loading class: net/irisshaders/' not in l and 'org.gradle.' not in l and 'MessageIOException' not in l
       and 'sun.nio.fs.UnixException' not in l]
big = c.count('abnormally large AABB') + c2.count('abnormally large AABB')
check('E no Sable "abnormally large AABB" (#38: a Ship hit measured in plot space)', not big, f'{big} hits')
check('E exceptions', not exc, f'{len(exc)} exception lines' + (f': {exc[0][:200]}' if exc else ''))
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
