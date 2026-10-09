"""Phase-9 (v1.1) numbers and pass/fail from a p9 suite log set: Rig Grips on Sable Ships (parked turned, moving, rotating,
world <-> ship, ship removed mid-grip, deck ride). Log contract (dev source set, trace on): see tools/README.md "p9 log contract".
usage: p9_metrics.py tools/logs/<tag>-p9"""
import math
import re
import sys

pre = sys.argv[1]
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
q4 = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
r = open(f'{pre}-rcon.txt', errors='replace').read()
fails = []

HOLD_SETTLE = 20          # ticks skipped after a Grip/velocity change before Held is required (p6/p6fast)
MIN_GRIPS = 2             # Held = at least 2 valid Grips (p6 G3, p6fast F)
RIDE_MAX = 0.75           # ship-frame displacement of an idle rider over a window: RigServer.GRIP_SLACK
DRIFT_MAX = 0.5           # client vs server, lag-allowed (analyze.py "ticks>0.5")
DRIFT_SKIP = 10           # ticks around a SHIPDRIVE velocity step skipped for drift only
SPEED_TOL = 0.15          # ship really moved at the asked speed
AIR_IDLE = 80.0           # Copper idle ticks per Air (config air.idle, p2 D copper idle)
AIR_MOVE = 20.0           # Copper move ticks per Air (what an uncorrected carry would cost)
LET_GO_TICKS = 10         # ship removed: Grips gone / state left ACTIVE within this many ticks
FALL_MIN = 5.0            # ship removed: feet drop at least this much
FLOOR = -60.0             # flat world: feet on the floor


def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok:
        fails.append(name)


def info(text):
    print(f'INFO {text}')


# --- parse -------------------------------------------------------------------------------------------------------------
tr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num + r' d=\S+ cmd=\S+ held=(\d+) state=(\w+) .*?ground=(\w+) hp=(\S+) air=(\d+) ff=\w+'
                     r'(?: sheld=(\d+) carry=(\S+))?', c):
    tr[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), held=int(m[5]), state=m[6], ground=m[7] == 'true', hp=float(m[8]),
                         air=int(m[9]), sheld=None if m[10] is None else int(m[10]), carry=m[11])
st = {}
for m in re.finditer(r'STRACE gt=(\d+) Dev pos=' + num + r' v=\S+ state=(\w+) grips=(\d+) cf=\S+ flying=\w+ fall=\S+ hp=(\S+) ground=(\w+) air=(\d+) mv=\S+'
                     r'(?: sgrips=(\d+) carry=(\S+))?', s):
    st[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), state=m[5], grips=int(m[6]), hp=float(m[7]), ground=m[8] == 'true',
                         air=int(m[9]), sgrips=None if m[10] is None else int(m[10]), carry=m[11])
sship = {}   # name -> {gt: (pos, q, v, w)}
ids = {}     # id -> name
for m in re.finditer(r'SSHIP gt=(\d+) name=(\S+) id=(\S+) pos=' + num + r' q=' + q4 + r' v=' + num + r' w=' + num, s):
    sship.setdefault(m[2], {})[int(m[1])] = (tuple(map(float, m.group(4, 5, 6))), tuple(map(float, m.group(7, 8, 9, 10))),
                                             tuple(map(float, m.group(11, 12, 13))), tuple(map(float, m.group(14, 15, 16))))
    ids[m[3]] = m[2]
cship = {}   # name -> {gt: (pos, q)}
for m in re.finditer(r'CSHIP gt=(\d+) id=(\S+) pos=' + num + r' q=' + q4, c):
    cship.setdefault(ids.get(m[2], m[2]), {})[int(m[1])] = (tuple(map(float, m.group(3, 4, 5))), tuple(map(float, m.group(6, 7, 8, 9))))
drives = [(int(m[1]), m[2]) for m in re.finditer(r'SHIPDRIVE gt=(\d+) name=(\S+)', s)]
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
for m in re.findall(r'SPOS (\S+) gt=(\d+) Dev', s):
    mk.setdefault(m[0], int(m[1]))


# --- math --------------------------------------------------------------------------------------------------------------
def qmul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return (aw * bx + ax * bw + ay * bz - az * by, aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw, aw * bw - ax * bx - ay * by - az * bz)


def rot_inv(q, v):
    """conj(q) * v * q: world vector into the ship frame (unit q)."""
    qc = (-q[0], -q[1], -q[2], q[3])
    x, y, z, _ = qmul(qmul(qc, (v[0], v[1], v[2], 0.0)), q)
    return x, y, z


def angle_deg(q0, q1):
    x, y, z, w = qmul((-q0[0], -q0[1], -q0[2], q0[3]), q1)
    return math.degrees(2 * math.atan2(math.sqrt(x * x + y * y + z * z), abs(w)))


def local(p, pose):
    pos, q = pose
    return rot_inv(q, tuple(a - b for a, b in zip(p, pos)))


def span(d, a, b):
    return [(g, d[g]) for g in sorted(d) if a <= g <= b]


def states(w):
    out = []
    for _, x in w:
        if not out or out[-1] != x['state']:
            out.append(x['state'])
    return ' -> '.join(out)


def ship_pose(name, g, side='s'):
    d = sship.get(name, {}) if side == 's' else cship.get(name, {})
    x = d.get(g)
    return None if x is None else (x[0], x[1])


def need(*tags):
    miss = [t for t in tags if t not in mk]
    return miss


# --- setup / contract --------------------------------------------------------------------------------------------------
print('== Setup and log contract')
NAMES = ['p9a', 'p9b', 'p9c', 'p9d', 'p9e', 'p9f']
assembled = r.count('Assembled a region')
named = r.count('Set name of sub-level')
bad_cmd = [l for l in r.splitlines() if re.search(r'Unknown or incomplete command|Incorrect argument|Unknown command|Invalid|Expected', l)]
no_ship = [l for l in r.splitlines() if re.search(r'=> no ship', l)]
check('S0 Sable commands (needs -PwithSable)', assembled == 6 and named == 6 and not bad_cmd,
      f'{assembled}/6 "Assembled a region", {named}/6 named, {len(bad_cmd)} command errors' + (f': {bad_cmd[0][:160]}' if bad_cmd else ''))
seen = [n for n in NAMES if n in sship]
check('S1 SSHIP lines per ship', len(seen) == 6, f'ships traced {seen} (want {NAMES})')
check('S2 STRACE sgrips/carry + TRACE sheld/carry fields', any(x['sgrips'] is not None for x in st.values()) and any(x['sheld'] is not None for x in tr.values()),
      f"STRACE with sgrips {sum(1 for x in st.values() if x['sgrips'] is not None)}/{len(st)}, TRACE with sheld {sum(1 for x in tr.values() if x['sheld'] is not None)}/{len(tr)}")
check('S3 /tentaworks ship drive', len(drives) >= 16 and not no_ship, f'{len(drives)} SHIPDRIVE lines (>= 16), {len(no_ship)} "no ship" replies')
CENTRES = {'p9a': (6063.5, -57.5, 0.5), 'p9b': (6103.5, -47.5, 0.5), 'p9c': (6403.5, -47.5, 0.5), 'p9d': (6503.5, -57.5, 3.5),
           'p9e': (6603.5, -47.5, 0.5), 'p9f': (6703.5, -47.5, 0.5)}
first = {n: sship[n][min(sship[n])][0] for n in CENTRES if sship.get(n)}
off = {n: math.dist(first[n], CENTRES[n]) for n in first}
check('S4 named ships start at their box centres', len(off) == 6 and max(off.values()) <= 0.6,
      ', '.join(f'{n} {d:.2f}' for n, d in off.items()) + ' (<= 0.6; checks names and that pose position = box centre = rotation point)')
if not cship:
    info('no CSHIP lines: client/server drift falls back to world frame with one tick of ship motion allowed')


def sg(x):
    return -1 if x['sgrips'] is None else x['sgrips']


def held(name, a, b, need_ship=True, ship_share=1.0):
    """Server: ACTIVE every tick, >= 2 valid Grips; ship Grips >= 2 on ship_share of the ticks."""
    w = span(st, a, b)
    if not w:
        check(name, False, f'no STRACE ticks in {a}..{b}')
        return
    act = all(x['state'] == 'ACTIVE' for _, x in w)
    mg = min(x['grips'] for _, x in w)
    ok_ship = sum(1 for _, x in w if sg(x) >= MIN_GRIPS)
    share = ok_ship / len(w)
    ok = act and mg >= MIN_GRIPS and (not need_ship or share >= ship_share)
    check(name, ok, f'{len(w)} ticks, server {states(w)}, min Grips {mg} (>= {MIN_GRIPS}), ship Grips >= {MIN_GRIPS} on {ok_ship} ticks '
                    f'({100 * share:.0f} %, want >= {100 * ship_share:.0f} %), min ship Grips {min(sg(x) for _, x in w)}')


def ride(name, ship, a, b, limit=RIDE_MAX):
    """Server ship-frame displacement of the wearer over the window (rider stays put on the hull)."""
    loc = [(g, local(x['p'], ship_pose(ship, g))) for g, x in span(st, a, b) if ship_pose(ship, g)]
    if len(loc) < 2:
        check(name, False, f'no SSHIP {ship} / STRACE overlap in {a}..{b}')
        return None
    ref = loc[0][1]

    def lagfree(g, x):
        # server position follows packet arrival: allow the ship pose of the tick before or after (analyze.py's 1-tick lag)
        return min(math.dist(ref, local(x['p'], pz)) for pz in (ship_pose(ship, k) for k in (g - 1, g, g + 1)) if pz)
    dl = [lagfree(g, st[g]) for g, _ in loc]
    d = max(dl)
    end = dl[-1]
    pos0 = ship_pose(ship, loc[-1][0])[0]
    far = math.dist(st[loc[-1][0]]['p'], pos0)
    check(name, d < limit, f'ship-frame displacement max {d:.3f} end {end:.3f} (< {limit}), end distance to ship rotation point {far:.2f}, {len(loc)} ticks')
    return d


def ship_speed(ship, a, b):
    w = span(sship.get(ship, {}), a, b)
    if len(w) < 2:
        return None, None
    (g0, x0), (g1, x1) = w[0], w[-1]
    turned = sum(angle_deg(p0[1], p1[1]) for (_, p0), (_, p1) in zip(w, w[1:]))
    return math.dist(x0[0], x1[0]) / ((g1 - g0) / 20), turned / ((g1 - g0) / 20)


def moved(name, ship, a, b, want, rot=False):
    v, wdeg = ship_speed(ship, a, b)
    got = wdeg if rot else v
    unit = 'deg/s' if rot else 'b/s'
    check(name, got is not None and abs(got - want) <= SPEED_TOL * want, f'ship {ship} {got if got is None else round(got, 2)} {unit} (asked {want}, +-{100 * SPEED_TOL:.0f} %)')


def air_used(a, b):
    w = span(st, a, b)
    return (w[0][1]['air'] - w[-1][1]['air'], w[-1][0] - w[0][0]) if len(w) > 1 else (None, None)


base_rate = None


def air(name, a, b):
    used, n = air_used(a, b)
    if used is None:
        check(name, False, f'no STRACE ticks in {a}..{b}')
        return
    rate = base_rate if base_rate else 1 / AIR_IDLE
    allow = math.ceil(n * rate) + 1
    check(name, used <= allow, f'{used} Air in {n} ticks (world idle baseline allows {allow}; idle rate {n / AIR_IDLE:.1f}, move rate would be {n / AIR_MOVE:.1f})')


def drives_near(g):
    return any(abs(g - d) <= DRIFT_SKIP for d, _ in drives)


def drift(name, ship, a, b, speed=0.0):
    """analyze.py lag-allowed drift, in the ship frame when the client logs CSHIP, else world frame + one tick of ship motion."""
    keep = []
    shipframe = ship in cship
    for g in sorted(st):
        if not (a <= g <= b) or g not in tr or g - 1 not in tr or drives_near(g):
            continue
        if tr[g]['state'] not in ('ACTIVE', 'SETTING_DOWN', 'FALLING', 'CRAWL'):
            continue
        if shipframe:
            ps, pc, pc1 = ship_pose(ship, g), ship_pose(ship, g, 'c'), ship_pose(ship, g - 1, 'c')
            if not (ps and pc and pc1):
                continue
            sv = local(st[g]['p'], ps)
            same = math.dist(local(tr[g]['p'], pc), sv)
            lag = min(same, math.dist(local(tr[g - 1]['p'], pc1), sv))
        else:
            same = math.dist(tr[g]['p'], st[g]['p'])
            lag = min(same, math.dist(tr[g - 1]['p'], st[g]['p']))
        keep.append((g, same, lag))
    if not keep:
        check(name, False, f'no comparable ticks in {a}..{b}')
        return
    limit = DRIFT_MAX if shipframe else DRIFT_MAX + speed / 20
    mx = max(k[2] for k in keep)
    check(name, mx <= limit, f"{'ship' if shipframe else 'world'} frame, {len(keep)} ticks, max drift same gt {max(k[1] for k in keep):.3f}, "
                             f"1 tick lag allowed {mx:.3f} (<= {limit:.2f}), ticks > {limit:.2f}: {sum(1 for k in keep if k[2] > limit)}")


def section(title, *tags):
    print('==', title)
    miss = need(*tags)
    if miss:
        check(f'{title} marks', False, f'missing marks {miss} (client or timeline stopped early)')
    return not miss


# --- W world baseline --------------------------------------------------------------------------------------------------
if section('W world wall baseline', 'wa', 'wb'):
    held('W held on the world wall', mk['wa'], mk['wb'], need_ship=False)
    used, n = air_used(mk['wa'], mk['wb'])
    if used is not None and n:
        base_rate = max(used, 1) / n
        info(f'world idle hang: {used} Air in {n} ticks ({n / max(used, 1):.0f} ticks per Air, config idle {AIR_IDLE:.0f})')

# --- A parked ship, turned 30 degrees ------------------------------------------------------------------------------------
if section('A parked ship turned 30 deg', 'aclimb0', 'aclimb1', 'aa', 'ab'):
    p = ship_pose('p9a', mk['aa'])
    check('A1 ship turned', p is not None and abs(angle_deg((0, 0, 0, 1), p[1]) - 30) <= 3,
          f'p9a turned {angle_deg((0, 0, 0, 1), p[1]):.1f} deg (want 30 +-3)' if p else 'no SSHIP p9a')
    w = span(tr, mk['aclimb0'], mk['aa'])
    rise = max(x['p'][1] for _, x in w) - w[0][1]['p'][1] if w else 0
    smax = max((sg(x) for _, x in span(st, mk['aclimb0'], mk['ab'])), default=-1)
    check('A2 climbs the ship wall', rise >= 1.0 and smax >= MIN_GRIPS, f'feet rose {rise:.2f} (>= 1.0), max ship Grips {smax} (>= {MIN_GRIPS})')
    held('A3 held on the parked turned ship', mk['aa'] + HOLD_SETTLE, mk['ab'], ship_share=0.5)
    a0, a1 = ship_pose('p9a', mk['aa']), ship_pose('p9a', mk['ab'])
    check('A4 parked ship stays put', a0 and a1 and math.dist(a0[0], a1[0]) < 0.5, f'p9a moved {math.dist(a0[0], a1[0]):.3f} (< 0.5)' if a0 and a1 else 'no SSHIP p9a')
    air('A5 Air on the parked ship', mk['aa'], mk['ab'])
    drift('A6 client/server drift', 'p9a', mk['aa'], mk['ab'])

# --- B moving ship ------------------------------------------------------------------------------------------------------
STEPS = [('b4', (4, 0, 0)), ('b15', (15, 0, 0)), ('bz', (0, 0, 4)), ('bup', (0, 3, 0))]
if section('B moving ship', 'b0', 'bend', *[k + x for k, _ in STEPS for x in ('a', 'b')]):
    w = span(st, mk['b0'], mk['bend'])
    stow = [g for g, x in w if x['state'] != 'ACTIVE']
    check('B0 no stow across all velocity steps', w and not stow, f'server {states(w)} over {len(w)} ticks' + (f', first non-ACTIVE gt {stow[0]}' if stow else ''))
    for k, v in STEPS:
        a, b = mk[k + 'a'], mk[k + 'b']
        sp = math.hypot(*v)
        moved(f'B {k} ship speed', 'p9b', a, b, sp)
        held(f'B {k} held on the moving ship', a, b)
        ride(f'B {k} rides along (stays on the hull)', 'p9b', a, b)
        air(f'B {k} Air (carry not counted as movement)', a, b)
        drift(f'B {k} client/server drift', 'p9b', a, b, sp)

# --- C rotating ship ----------------------------------------------------------------------------------------------------
if section('C rotating ship', 'c0', 'cya', 'cyb', 'cra', 'crb', 'cend'):
    w = span(st, mk['c0'], mk['cend'])
    stow = [g for g, x in w if x['state'] != 'ACTIVE']
    check('C0 no stow while rotating', w and not stow, f'server {states(w)} over {len(w)} ticks')
    for k, deg, a, b in (('yaw', 20, mk['cya'], mk['cyb']), ('roll', 10, mk['cra'], mk['crb'])):
        moved(f'C {k} ship rate', 'p9c', a, b, deg, rot=True)
        held(f'C {k} held on the rotating ship', a, b)
        ride(f'C {k} rides along', 'p9c', a, b)
        drift(f'C {k} client/server drift', 'p9c', a, b, math.radians(deg) * 5)
    air('C Air while rotating', mk['cya'], mk['cyb'])

# --- D world <-> ship ---------------------------------------------------------------------------------------------------
if section('D world <-> ship', 'dw0', 'ds0', 'ds1', 'dw1', 'dstr', 'dend'):
    def avg_sg(a, b):
        w = span(st, a, b)
        return sum(sg(x) for _, x in w) / len(w) if w else -1
    s0, s1, s2 = avg_sg(mk['dw0'] - 10, mk['dw0']), avg_sg(mk['ds0'], mk['ds1']), avg_sg(mk['dw1'] - 10, mk['dw1'])
    zs = [tr[g]['p'][2] for g in (mk['dw0'], mk['ds0'], mk['dw1']) if g in tr]
    check('D1 world -> ship -> world', s0 < 1 and s1 >= MIN_GRIPS and s2 < 1,
          f'mean ship Grips on the world wall {s0:.2f}, on the hull {s1:.2f}, back on the world wall {s2:.2f}; z {", ".join(f"{z:.2f}" for z in zs)}')
    held('D2 held across the seam', mk['dw0'], mk['dw1'], need_ship=False)
    p0, p1 = ship_pose('p9d', mk['dw0']), ship_pose('p9d', mk['dw1'])
    if p0 and p1:
        info(f'parked p9d moved {math.dist(p0[0], p1[0]):.3f} while the wearer climbed over it')
    w = span(st, mk['dstr'], mk['dend'])
    e0, e1 = ship_pose('p9d', mk['dstr']), ship_pose('p9d', mk['dend'])
    left = math.dist(e0[0], e1[0]) if e0 and e1 else 0
    last = w[-1][1] if w else None
    check('D3 ship leaves a straddling wearer', w and left >= 3 and all(x['state'] != 'STOWED' for _, x in w) and last['state'] == 'ACTIVE'
          and last['grips'] >= MIN_GRIPS and sg(last) == 0,
          f'ship left {left:.2f} (>= 3), server {states(w)}, end Grips {last["grips"] if last else "-"} ship Grips {sg(last) if last else "-"} (want world only)')

# --- E ship removed mid-grip --------------------------------------------------------------------------------------------
if section('E ship removed mid-grip', 'e0', 'erm', 'e1'):
    g0 = mk['erm']
    held('E1 held on the ship before removal', mk['e0'] - 20, g0)
    sw = span(st, g0, g0 + 200)
    let_s = next((g for g, x in sw if x['state'] != 'ACTIVE' or x['grips'] < MIN_GRIPS), None)
    let_c = next((g for g, x in span(tr, g0, g0 + 200) if x['state'] != 'ACTIVE'), None)
    check('E2 lets go', let_s is not None and let_s - g0 <= LET_GO_TICKS and let_c is not None and let_c - g0 <= LET_GO_TICKS,
          f'server let go after {None if let_s is None else let_s - g0} ticks, client after {None if let_c is None else let_c - g0} (<= {LET_GO_TICKS}), server {states(sw[:60])}')
    cw = span(tr, g0, mk['e1'])
    y0 = cw[0][1]['p'][1] if cw else 0
    drop = y0 - min(x['p'][1] for _, x in cw) if cw else 0
    end = cw[-1][1] if cw else None
    # a Rig with Air may re-grab the floor and hover at Standing Height (ground=false): near the floor counts
    check('E3 falls to the floor', cw and drop >= FALL_MIN and end['p'][1] <= FLOOR + 2.0 and end['hp'] > 0,
          f"feet {y0:.2f} -> min {y0 - drop:.2f} (drop {drop:.2f} >= {FALL_MIN}), end feet {end['p'][1] if end else '-'} (<= {FLOOR + 2.0}), "
          f"ground {end['ground'] if end else '-'}, state {end['state'] if end else '-'}, hp {end['hp'] if end else '-'}, client {states(cw)}")
    gone = [g for g in sship.get('p9e', {}) if g > g0 + 2]
    check('E4 ship really removed', 'Removed' in r and not gone, f'"Removed" in rcon {"Removed" in r}, SSHIP p9e ticks after removal {len(gone)}')

# --- F deck ride --------------------------------------------------------------------------------------------------------
if section('F deck ride', 'f0', 'fa', 'fb', 'f1'):
    moved('F ship speed', 'p9f', mk['fa'], mk['fb'], 4)
    w = span(st, mk['f0'], mk['f1'])
    check('F1 stays Active on the deck', w and all(x['state'] == 'ACTIVE' for _, x in w), f'server {states(w)}')
    ride('F2 rides the deck (no double carry, not left behind)', 'p9f', mk['fa'], mk['fb'])
    drift('F3 client/server drift', 'p9f', mk['fa'], mk['fb'], 4)

# --- whole run ----------------------------------------------------------------------------------------------------------
print('== Whole run')
bad = ['moved wrongly', 'Flying is not enabled', 'multiplayer.disconnect.flying', 'lost connection', 'forcing Falling', 'non-finite', 'kicked', 'moves against Crawl']
counts = {b: s.count(b) for b in bad}
check('K no kick / forced Falling / wrong moves', not any(counts.values()), ', '.join(f'{k}={v}' for k, v in counts.items()))


def secs(t):
    h, m_, s_ = map(int, t.split(':'))
    return h * 3600 + m_ * 60 + s_


tps = [secs(t) for t in re.findall(r'^(\d\d:\d\d:\d\d) > .*\b(?:tp|kill)\b', r, re.M)]
tps += [secs(t) for t in re.findall(r'\[(\d\d:\d\d:\d\d)\].*script: (?:cmd tp|rejoin)', c)]
quick = re.findall(r'\[(\d\d:\d\d:\d\d)\] \[Server thread/WARN\].*?: (\w+) moved too quickly', s)
unexplained = [(t, n) for t, n in quick if not any(0 <= secs(t) - pt <= 2 for pt in tps)]
check('K moved too quickly', not unexplained, f'{len(quick)} total, unexplained (not within 2 s after a tp) {len(unexplained)} {unexplained[:3]}')
exc = [l for l in (c + s).splitlines() if 'Exception' in l and 'ApiStatus' not in l and 'authlib' not in l and 'neoforge-client.new.tmp.toml' not in l]
check('E exceptions', not exc, f'{len(exc)} exception lines' + (f': {exc[0][:200]}' if exc else ''))
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
