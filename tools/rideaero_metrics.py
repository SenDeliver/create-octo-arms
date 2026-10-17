"""Rideaero checks (adr/0003, -PwithSable): seat on a Sable Ship parked and moving, Control Hold on a Simulated steering wheel,
Simulated handle (sets the wearer's motion: a Ride; grabbed sneaking it drags its Ship: neither, the Rig carries on). Kinds: setup, guard (passes before and after the fix), bug (fails before the fix).
usage: rideaero_metrics.py <log prefix>"""
import math, re, sys
pre = sys.argv[1]
client = open(pre + '-client.log', errors='replace').read()
server = open(pre + '-server.log', errors='replace').read()
ct = {int(m[0]): dict(pos=tuple(map(float, m[1].split(','))), state=m[2], eye=float(m[3]), veh=m[4], ride=m[5], hold=m[6])
      for m in re.findall(r'TRACE t=\d+ gt=(\d+) pos=(\S+) .*? state=(\w+) .*? eye=([\d.]+) veh=(\S+) ride=(\S+) hold=(\S+) vrot=\S+', client)}
st = {int(m[0]): dict(pos=tuple(map(float, m[1].split(','))), state=m[2], veh=m[3])
      for m in re.findall(r'STRACE gt=(\d+) Dev pos=(\S+) .*? state=(\w+) .*? veh=(\S+) ride=\S+ vrot=\S+', server)}
ship = {int(m[0]): tuple(map(float, m[1].split(','))) for m in re.findall(r'SSHIP gt=(\d+) name=rw id=\S+ pos=(\S+)', server)}
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', client)}
nbt = {m[0]: m[1] for m in re.findall(r'SNBT (\S+) gt=\d+ \S+ \S+ \S+ (.*)', server)}
WHEEL = 'SteeringWheelHandler'
HANDLE = 'ClientHandleHandler'
GRAB = {'base': (6069.6875, -56.5, 6.5), 'rig': (6069.6875, -56.5, 0.5)}
STOW_TICKS = 5
fails = []


def check(ok, kind, name, text):
    ok = bool(ok)
    if not ok: fails.append(name)
    print(f"{'PASS' if ok else 'FAIL'} [{kind}] {name}: {text}")


def span(d, a, b):
    return [(g, d[g]) for g in sorted(d) if a <= g <= b]


def first(d, a, b, pred):
    return next((g for g, x in span(d, a, b) if pred(x)), None)


def marks(*names):
    missing = [n for n in names if n not in mk]
    if missing: check(False, 'setup', f'marks {",".join(names)}', f'missing marks {missing}')
    return not missing


def before(gt):
    x = ct.get(gt - 1) if gt is not None else None
    return x['state'] if x else None


def full(w, a, b):
    """A trace window over [a, b] with at least 90% of its ticks (a window with lines missing tests nothing)."""
    return len(w) >= 0.9 * (b - a)


def stowed_soon(d, g, end):
    if g is None: return False, 'no start tick'
    s = first(d, g, g + STOW_TICKS, lambda x: x['state'] == 'STOWED')
    if s is None: return False, f'states {[x["state"] for _, x in span(d, g, g + STOW_TICKS)]}'
    after = span(d, s, end)
    bad = [gg for gg, x in after if x['state'] != 'STOWED']
    return not bad and len(after) > 0, f'stowed at +{s - g}, not Stowed on {len(bad)}/{len(after)} ticks after {bad[:4]}'


def target(tag):
    """Wheel target angle the server got: TargetAngleToUpdate (the last SteeringWheelPacket), else TargetAngle."""
    s = nbt.get(tag)
    if s is None: return None
    m = re.search(r'TargetAngleToUpdate:(-?[\d.]+)f', s) or re.search(r'TargetAngle:(-?[\d.]+)f', s)
    return float(m[1]) if m else None


def grab_dist(gt, which):
    x = ct.get(gt)
    if x is None: return None
    e = (x['pos'][0], x['pos'][1] + x['eye'], x['pos'][2])
    return math.dist(e, GRAB[which])


def seat_section(tag, a, b, c):
    cg = first(ct, mk[a], mk[b], lambda x: x['veh'] == 'create:seat')
    sg = first(st, mk[a], mk[b], lambda x: x['veh'] == 'create:seat')
    # the server's Stow can reach the client a tick before the client sees the mount: read the state before the earlier side
    st0 = before(min((g for g in (cg, sg) if g is not None), default=None))
    check(cg and sg and st0 == 'ACTIVE', 'setup', f'{tag} sits on the Ship seat with the Rig Active', f'client gt {cg}, server gt {sg}, state before {st0}')
    okc, tc = stowed_soon(ct, cg, mk[b])
    oks, ts = stowed_soon(st, sg, mk[b])
    check(okc and oks, 'bug', f'{tag} sitting Stows the Rig (client and server, within {STOW_TICKS} ticks)', f'client: {tc}; server: {ts}')
    return cg, sg


def dismounts(tag, a, b):
    seated = [g for g, x in span(st, mk[a] - 2, mk[a]) if x['veh'] != '-']
    off = first(st, mk[a], mk[b], lambda x: x['veh'] == '-')
    check(seated and off is not None, 'bug', f'{tag} Shift dismounts (server)', f'seated at {a}: {bool(seated)}, server veh cleared at gt {off}')


def kicks(server):
    """Kicks and moved-wrongly lines, except moved wrongly within 3 ticks after a forced `ride dismount` (a section reset, logged as command feedback)."""
    out, skipped, gt, forced = [], 0, -1, -100
    for line in server.splitlines():
        m = re.search(r'STRACE gt=(\d+) ', line)
        if m: gt = int(m[1])
        elif 'stopped riding' in line: forced = gt
        elif 'moved wrongly' in line:
            if gt - forced <= 3: skipped += 1
            else: out.append(f'gt~{gt} {line}')
        elif re.search(r'kicked|Kicking|lost connection', line) and not line.endswith('lost connection: Disconnected'): out.append(line)
    return out, skipped


if not ct or not st:
    check(False, 'setup', 'traces', f'client ticks {len(ct)}, server ticks {len(st)}')
else:
    if marks('w1a', 'w1b', 'w1c'):
        seat_section('W1 parked', 'w1a', 'w1b', 'w1c')
        dismounts('W1 parked', 'w1b', 'w1c')
    if marks('w2a', 'w2b', 'w2c', 'w2d'):
        seat_section('W2', 'w2a', 'w2b', 'w2c')
        p, q = ship.get(mk['w2b']), ship.get(mk['w2c'])
        dx = q[0] - p[0] if p and q else None
        check(dx is not None and dx >= 15, 'setup', 'W2 Ship moves +x (>= 15 blocks over 100 ticks)', f'dx {dx}')
        win = span(st, mk['w2b'], mk['w2c'])
        seated = sum(x['veh'] == 'create:seat' for _, x in win)
        check(win and seated == len(win), 'guard', 'W2 stays seated on the moving Ship', f'seated {seated}/{len(win)} server ticks')
        wc = span(ct, mk['w2b'], mk['w2c'])
        bad = [g for g, x in wc if x['state'] != 'STOWED']
        check(full(wc, mk['w2b'], mk['w2c']) and not bad, 'bug', 'W2 Rig Stowed while seated on the moving Ship', f'not Stowed on {len(bad)}/{len(wc)} client ticks')
        dismounts('W2 moving', 'w2c', 'w2d')
    # W3 steering wheel
    if marks('w3a', 'w3b'):
        x = ct.get(mk['w3a'])
        check(x and x['hold'] == WHEEL and x['state'] == 'STOWED', 'setup', 'W3 baseline, hold on the wheel with the Rig Stowed', f'{x and (x["hold"], x["state"])}')
        t0, t1 = target('w3base0'), target('w3base')
        check(t0 is not None and abs(t0 - 30) < 1 and t1 is not None and abs(t1 - 45) < 0.01, 'setup', 'W3 baseline, Shift snaps the wheel from 30 to 45 deg', f'target {t0} -> {t1}')
        y = ct.get(mk['w3b'])
        check(y and y['hold'] == '-', 'setup', 'W3 baseline, letting go of use ends the hold', f'hold {y and y["hold"]}')
    if marks('w3c', 'w3d', 'w3e', 'w3f', 'w3g'):
        x, y = ct.get(mk['w3c']), ct.get(mk['w3d'])
        check(x and x['state'] == 'ACTIVE' and y and y['hold'] == WHEEL and y['state'] == 'ACTIVE', 'setup', 'W3 Control Hold starts with the Rig Active',
              f'w3c {x and x["state"]}, w3d {y and (y["hold"], y["state"])}')
        win = span(ct, mk['w3d'], mk['w3e'])
        held = all(z['hold'] == WHEEL for _, z in win)
        check(win and held, 'setup', 'W3 hold lasts while Shift is held', f'hold on {sum(z["hold"] == WHEEL for _, z in win)}/{len(win)} ticks')
        t0, t1 = target('w3rig0'), target('w3rig')
        check(t0 is not None and abs(t0 - 30) < 1, 'setup', 'W3 Control Hold, wheel at 30 before Shift', f'target {t0}')
        check(t1 is not None and abs(t1 - 45) < 0.01, 'bug', 'W3 Control Hold, Shift goes to the wheel (snaps to 45 deg)', f'target {t1}')
        p0 = ct[mk['w3d']]['pos'] if mk['w3d'] in ct else None
        drift = max(math.dist(p0, z['pos']) for _, z in win) if win and p0 else None
        check(drift is not None and drift < 0.05, 'bug', 'W3 Control Hold, the Rig holds the wearer still while Shift is held (< 0.05)', f'max drift {drift}')
        act = [g for g, z in win if z['state'] != 'ACTIVE']
        check(win and not act, 'guard', 'W3 Control Hold, the Rig stays Active (keeps its Grips)', f'not Active on {len(act)}/{len(win)} ticks')
        f = ct.get(mk['w3f'])
        check(f and f['state'] in ('SETTING_DOWN', 'STOWED'), 'guard', 'W3 Double Jump during the Control Hold sets the Rig down', f'state {f and f["state"]}, hold {f and f["hold"]}')
        g = ct.get(mk['w3g'])
        check(g and g['hold'] == '-', 'setup', 'W3 letting go of use ends the hold', f'hold {g and g["hold"]}')
    if marks('w3h', 'w3i', 'w3j'):
        h, i, j = ct.get(mk['w3h']), ct.get(mk['w3i']), ct.get(mk['w3j'])
        check(h and h['hold'] == WHEEL and i and i['hold'] == '-', 'setup', 'W3 hand-back, held, then let go', f'w3h {h and h["hold"]}, w3i {i and i["hold"]}')
        drop = i['pos'][1] - j['pos'][1] if i and j else None
        check(drop is not None and drop >= 0.3 and j['state'] == 'ACTIVE', 'guard', 'W3 hand-back, after letting go Shift moves the Rig down again (>= 0.3)', f'drop {drop}, state {j and j["state"]}')
    # W4 handle
    if marks('w4a', 'w4b', 'w4c'):
        x = ct.get(mk['w4a'])
        check(x and x['hold'] == HANDLE, 'setup', 'W4 baseline, hold on the handle with the Rig Stowed', f'hold {x and x["hold"]}, state {x and x["state"]}')
        da, db, dc = grab_dist(mk['w4a'], 'base'), grab_dist(mk['w4b'], 'base'), grab_dist(mk['w4c'], 'base')
        check(None not in (da, db, dc) and da - db >= 0.3 and dc - db >= 0.3, 'setup', 'W4 baseline, W pulls closer, S lets out (>= 0.3 each)',
              f'eye to handle {da and round(da, 2)} -> W {db and round(db, 2)} -> S {dc and round(dc, 2)}')
    if marks('w4e', 'w4f', 'w4g', 'w4h'):
        g = first(ct, mk['w4e'], mk['w4f'], lambda x: x['hold'] == HANDLE)
        check(g is not None and before(g) == 'ACTIVE', 'setup', 'W4 handle hold starts with the Rig Active', f'hold start gt {g}, state before {before(g)}')
        ok, t = stowed_soon(ct, g, mk['w4h'])
        check(ok, 'bug', f'W4 the handle moves the wearer (a Ride), the Rig Stows (client, within {STOW_TICKS} ticks)', t)
        da, db, dc = grab_dist(mk['w4f'], 'rig'), grab_dist(mk['w4g'], 'rig'), grab_dist(mk['w4h'], 'rig')
        check(da is not None and da <= 2.0, 'bug', 'W4 the handle pulls the wearer in (eye to handle <= 2.0 after 15 ticks, baseline ~1.5)', f'eye to handle {da and round(da, 2)}')
        win = span(ct, mk['w4f'], mk['w4h'])
        held = win and all(x['hold'] == HANDLE for _, x in win)
        stowed = win and all(x['state'] == 'STOWED' for _, x in win)
        # an Active Rig also moves the body toward the wall on W: count the distance change only with the Rig Stowed
        check(held and stowed and None not in (da, db, dc) and da - db >= 0.3 and dc - db >= 0.3, 'bug', 'W4 W/S work the handle (Rig Stowed, closer then out >= 0.3)',
              f'hold throughout {held}, Stowed throughout {stowed}, eye to handle {da and round(da, 2)} -> W {db and round(db, 2)} -> S {dc and round(dc, 2)}')
    # W5 handle grabbed sneaking: it drags its Ship (here a wall: nothing), moves no one and reads no keys, so it is no Control Hold: the Rig keeps moving
    if marks('w5a', 'w5b', 'w5c'):
        a = ct.get(mk['w5a'])
        check(a and a['hold'] == HANDLE and a['state'] == 'ACTIVE', 'setup', 'W5 handle grabbed sneaking with the Rig Active: hold on, Rig still Active (no pull, no Ride)',
              f'{a and (a["hold"], a["state"])}')
        win = span(ct, mk['w5b'], mk['w5c'])
        held = win and all(x['hold'] == HANDLE and x['state'] == 'ACTIVE' for _, x in win)
        b, c = ct.get(mk['w5b']), ct.get(mk['w5c'])
        dx = c['pos'][0] - b['pos'][0] if b and c else None
        check(held and dx is not None and dx >= 0.3, 'bug', 'W5 drag hold, W still moves the Rig (>= 0.3 toward the wall, hold on, Rig Active)',
              f'hold and Active throughout {held}, dx {dx and round(dx, 2)}')
    bad, skipped = kicks(server)
    check(not bad, 'guard', 'no kick, no moved wrongly', f'{bad[:2]}, {skipped} moved wrongly right after a forced dismount (reset) not counted')
    exc = re.findall(r'.*Exception.*', client + server)
    print(f"INFO exceptions in the logs: {len(exc)} {exc[:2]}")
print('PASS' if not fails else 'FAIL ' + ', '.join(fails))
