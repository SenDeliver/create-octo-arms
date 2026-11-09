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

exc = [l for l in (c + c2 + s).splitlines() if 'Exception' in l and 'ApiStatus' not in l and 'authlib' not in l and 'neoforge-client.new.tmp.toml' not in l
       and 'Error loading class: net/irisshaders/' not in l and 'org.gradle.' not in l and 'MessageIOException' not in l
       and 'sun.nio.fs.UnixException' not in l]
check('E exceptions', not exc, f'{len(exc)} exception lines' + (f': {exc[0][:200]}' if exc else ''))
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
