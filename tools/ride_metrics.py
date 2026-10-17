"""Ride checks (adr/0003): starting a Ride Stows the Rig on both sides, nothing Activates it during the Ride, Shift/jump/WASD go to the Ride,
the server decides every dismount. Kinds: setup (the scene did what the check needs), guard (passes before and after the fix), bug (fails before the fix).
usage: ride_metrics.py <log prefix>"""
import math, re, sys
pre = sys.argv[1]
client = open(pre + '-client.log', errors='replace').read()
server = open(pre + '-server.log', errors='replace').read()
ct = {int(m[0]): dict(pos=tuple(map(float, m[1].split(','))), state=m[2], air=int(m[3]), veh=m[4], ride=m[5], hold=m[6], vrot=m[7])
      for m in re.findall(r'TRACE t=\d+ gt=(\d+) pos=(\S+) .*? state=(\w+) .*? air=(-?\d+) .*? veh=(\S+) ride=(\S+) hold=(\S+) vrot=(\S+)', client)}
st = {int(m[0]): dict(pos=tuple(map(float, m[1].split(','))), state=m[2], air=int(m[3]), veh=m[4], ride=m[5], vrot=m[6])
      for m in re.findall(r'STRACE gt=(\d+) Dev pos=(\S+) .*? state=(\w+) .*? air=(-?\d+) .*? veh=(\S+) ride=(\S+) vrot=(\S+)', server)}
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', client)}
fails = []
STOW_TICKS = 5


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


def mounted(a, b, veh=None):
    """First client and server tick in [a, b] with a vehicle (of that type, when given)."""
    pred = (lambda x: x['veh'] != '-') if veh is None else (lambda x: x['veh'] == veh)
    return first(ct, a, b, pred), first(st, a, b, pred)


def before(gt):
    x = ct.get(gt - 1) if gt is not None else None
    return x['state'] if x else None


def full(w, a, b):
    """A trace window over [a, b] with at least 90% of its ticks (a window with lines missing tests nothing)."""
    return len(w) >= 0.9 * (b - a)


def stowed_span(a, b):
    """Client and server ticks in [a, b] not Stowed, and whether both windows are full."""
    wc, ws = span(ct, a, b), span(st, a, b)
    return [g for g, y in wc + ws if y['state'] != 'STOWED'], full(wc, a, b) and full(ws, a, b), f'{len(wc)}/{len(ws)} client/server ticks'


def stowed_soon(d, g, end):
    """Stowed within STOW_TICKS of g and on every tick after that up to end."""
    if g is None: return False, 'no mount tick'
    s = first(d, g, g + STOW_TICKS, lambda x: x['state'] == 'STOWED')
    if s is None: return False, f'states {[x["state"] for _, x in span(d, g, g + STOW_TICKS)]}'
    after = span(d, s, end)
    bad = [gg for gg, x in after if x['state'] != 'STOWED']
    return not bad and len(after) > 0, f'stowed at +{s - g}, not Stowed on {len(bad)}/{len(after)} ticks after {bad[:4]}'


def ride_stows(tag, a, b, end, veh=None):
    """Mount between marks a and b; Stowed on both sides within STOW_TICKS, through end."""
    cg, sg = mounted(mk[a], mk[b], veh)
    okc, tc = stowed_soon(ct, cg, mk[end])
    oks, ts = stowed_soon(st, sg, mk[end])
    check(okc and oks, 'bug', f'{tag} Ride Stows the Rig (client and server, within {STOW_TICKS} ticks)', f'client mount gt {cg}: {tc}; server mount gt {sg}: {ts}')
    return cg, sg


def mounted_from(tag, a, b, want, veh=None):
    cg, sg = mounted(mk[a], mk[b], veh)
    # the server's Stow can reach the client a tick before the client sees the mount: read the state before the earlier side
    st0 = before(min((g for g in (cg, sg) if g is not None), default=None))
    check(cg is not None and sg is not None and st0 == want, 'setup', f'{tag} mounted from {want}',
          f'client mount gt {cg} ({ct[cg]["veh"] if cg else "-"}), server mount gt {sg}, client state before {st0}')
    return cg, sg


def dismounts(tag, a, b, kind='bug'):
    seated = [g for g, x in span(st, mk[a] - 2, mk[a]) if x['veh'] != '-']
    off = first(st, mk[a], mk[b], lambda x: x['veh'] == '-')
    check(seated and off is not None, kind, f'{tag} Shift dismounts (server)', f'seated at {a}: {bool(seated)}, server veh cleared at gt {off}')


def flat(p, q):
    return math.hypot(q[0] - p[0], q[2] - p[2])


def path(d, a, b):
    """Horizontal path length over [a, b] (the chain ride goes round a loop and back)."""
    w = span(d, a, b)
    return sum(flat(w[i][1]['pos'], w[i + 1][1]['pos']) for i in range(len(w) - 1)) if len(w) > 1 else None


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
    # A Stowed baseline
    if marks('a0', 'a1', 'a2'):
        cg, sg = mounted(mk['a0'], mk['a1'], 'create:seat')
        check(cg and sg and ct[cg]['state'] == 'STOWED', 'setup', 'A sits on the seat Stowed (client and server veh create:seat)', f'client gt {cg}, server gt {sg}')
        dismounts('A Stowed', 'a1', 'a2', 'guard')
    # J Double Jump after a dismount
    if marks('j0', 'j1', 'j2'):
        j0, j1 = ct.get(mk['j0']), ct.get(mk['j1'])
        sd = first(ct, mk['j1'], mk['j2'], lambda x: x['state'] in ('SETTING_DOWN', 'STOWED'))
        check(j0 and j0['state'] == 'STOWED' and j0['veh'] == '-' and j1 and j1['state'] == 'ACTIVE' and sd is not None and ct[mk['j2']]['state'] == 'STOWED',
              'guard', 'J after a dismount Double Jump activates, Double Jump again sets down',
              f'j0 {j0 and (j0["state"], j0["veh"])}, j1 {j1 and j1["state"]}, set down at gt {sd}, j2 {ct.get(mk["j2"], {}).get("state")}')
    # S1 owner repro
    if marks('s1a', 's1b', 's1c'):
        mounted_from('S1', 's1a', 's1b', 'ACTIVE', 'create:seat')
        ride_stows('S1', 's1a', 's1b', 's1b', 'create:seat')
        dismounts('S1', 's1b', 's1c')
    # S2 seated Stowed, Double Jump
    if marks('s2a', 's2b', 's2c'):
        x = st.get(mk['s2a'])
        check(x and x['veh'] == 'create:seat' and ct[mk['s2a']]['state'] == 'STOWED', 'setup', 'S2 seated Stowed', f'server {x and (x["veh"], x["state"])}')
        bad, ok, n = stowed_span(mk['s2a'], mk['s2b'])
        check(ok and not bad, 'bug', 'S2 seated, Double Jump does not Activate the Rig', f'not Stowed on {len(bad)} ticks (client+server) {bad[:4]}, {n}')
        dismounts('S2', 's2b', 's2c')
    # S3 key-only mode, toggle key
    if marks('s3a', 's3b', 's3c'):
        x = st.get(mk['s3a'])
        check(x and x['veh'] == 'create:seat' and ct[mk['s3a']]['state'] == 'STOWED' and 'raw settings chest RigSettings[standingHeight=1.5, speed=5.6, activation=KEY]' in client,
              'setup', 'S3 seated Stowed, Activation Mode key only', f'server {x and (x["veh"], x["state"])}')
        bad, ok, n = stowed_span(mk['s3a'], mk['s3b'])
        check(ok and not bad, 'bug', 'S3 seated, the toggle key does not Activate the Rig', f'not Stowed on {len(bad)} ticks {bad[:4]}, {n}')
        dismounts('S3', 's3b', 's3c')
    # S4 Air while seated
    if marks('s4a', 's4b', 's4c'):
        mounted_from('S4', 's4a', 's4b', 'ACTIVE', 'create:seat')
        b, c = st.get(mk['s4b']), st.get(mk['s4c'])
        seated = all(y['veh'] == 'create:seat' for _, y in span(st, mk['s4b'], mk['s4c']))
        check(b and c and seated and c['air'] == b['air'], 'bug', 'S4 200 ticks seated, no Air used', f'server Air {b and b["air"]} -> {c and c["air"]}, seated throughout {seated}')
        bad, ok, n = stowed_span(mk['s4b'], mk['s4c'])
        check(ok and not bad, 'bug', 'S4 200 ticks seated, Rig Stowed', f'not Stowed on {len(bad)} ticks {bad[:4]}, {n}')
    # S5 old client: forged Active packets with real Grips while seated
    if marks('s5a', 's5b'):
        x = st.get(mk['s5a'])
        faked = 'fake held for 60 ticks' in server
        check(x and x['veh'] == 'create:seat' and x['state'] == 'STOWED' and faked, 'setup', 'S5 seated Stowed, forged Active packets sent', f'server {x and (x["veh"], x["state"])}, fake {faked}')
        win = span(st, mk['s5a'] + 3, mk['s5b'])
        bad = [g for g, y in win if y['state'] != 'STOWED']
        check(full(win, mk['s5a'] + 3, mk['s5b']) and len(bad) <= 2 and all(y['state'] == 'STOWED' for _, y in win[-20:]), 'bug', 'S5 server keeps the Rig Stowed for a passenger (old client)',
              f'server not Stowed on {len(bad)}/{len(win)} ticks {bad[:4]}')
    # S6..S8 mount from Setting Down, Crawl, Falling
    for tag, want in (('s6', 'SETTING_DOWN'), ('s7', 'CRAWL'), ('s8', 'FALLING')):
        a, b, c = tag + 'a', tag + 'b', tag + 'c'
        if marks(a, b, c):
            mounted_from(tag.upper(), a, b, want, 'minecraft:minecart')
            ride_stows(tag.upper(), a, b, b, 'minecraft:minecart')
            dismounts(tag.upper(), b, c)
    # B0 / H0 baselines, Rig Stowed: the vehicle itself works in this scene
    if marks('bb1', 'bb2', 'bb3', 'bb4'):
        p, q = st.get(mk['bb1']), st.get(mk['bb2'])
        d = flat(p['pos'], q['pos']) if p and q and p['veh'] == q['veh'] == 'minecraft:boat' else None
        r2, r3 = ct.get(mk['bb2']), ct.get(mk['bb3'])
        dr = abs(float(r3['vrot']) - float(r2['vrot'])) if r2 and r3 and r2['vrot'] != '-' and r3['vrot'] != '-' else None
        check(d is not None and d >= 1.0 and dr is not None and dr >= 10, 'setup', 'B0 Stowed, W moves the boat, A turns it', f'moved {d}, yaw change {dr}')
        dismounts('B0 Stowed', 'bb3', 'bb4', 'setup')
    if marks('hb1', 'hb2', 'hb3', 'hb4'):
        p, q = st.get(mk['hb1']), st.get(mk['hb2'])
        d = flat(p['pos'], q['pos']) if p and q and p['veh'] == q['veh'] == 'minecraft:horse' else None
        win = span(st, mk['hb2'], mk['hb3'])
        rise = max(y['pos'][1] for _, y in win) - st[mk['hb2']]['pos'][1] if win and mk['hb2'] in st and all(y['veh'] == 'minecraft:horse' for _, y in win) else None
        check(d is not None and d >= 1.0 and rise is not None and rise >= 0.5, 'setup', 'H0 Stowed, W moves the horse, jump makes it jump', f'moved {d}, rise {rise}')
        dismounts('H0 Stowed', 'hb3', 'hb4', 'setup')
    # B boat
    if marks('b0', 'b1', 'b2', 'b3', 'b4'):
        mounted_from('B', 'b0', 'b1', 'ACTIVE', 'minecraft:boat')
        ride_stows('B', 'b0', 'b1', 'b3', 'minecraft:boat')
        p, q = st.get(mk['b1']), st.get(mk['b2'])
        d = flat(p['pos'], q['pos']) if p and q and p['veh'] == q['veh'] == 'minecraft:boat' else None
        check(d is not None and d >= 1.0, 'bug', 'B W moves the boat (>= 1 block, server)', f'moved {d}')
        r2, r3 = ct.get(mk['b2']), ct.get(mk['b3'])
        dr = abs(float(r3['vrot']) - float(r2['vrot'])) if r2 and r3 and r2['vrot'] != '-' and r3['vrot'] != '-' else None
        check(dr is not None and dr >= 10, 'bug', 'B A turns the boat (>= 10 deg, client)', f'yaw {r2 and r2["vrot"]} -> {r3 and r3["vrot"]}')
        dismounts('B', 'b3', 'b4')
    # H horse
    if marks('h0', 'h1', 'h2', 'h3', 'h4'):
        mounted_from('H', 'h0', 'h1', 'ACTIVE', 'minecraft:horse')
        ride_stows('H', 'h0', 'h1', 'h3', 'minecraft:horse')
        p, q = st.get(mk['h1']), st.get(mk['h2'])
        d = flat(p['pos'], q['pos']) if p and q and p['veh'] == q['veh'] == 'minecraft:horse' else None
        check(d is not None and d >= 1.0, 'bug', 'H W moves the horse (>= 1 block, server)', f'moved {d}')
        win = span(st, mk['h2'], mk['h3'])
        rise = max(y['pos'][1] for _, y in win) - st[mk['h2']]['pos'][1] if win and mk['h2'] in st and all(y['veh'] == 'minecraft:horse' for _, y in win) else None
        check(rise is not None and rise >= 0.5, 'bug', 'H jump (held, released) makes the horse jump (server y rises >= 0.5)', f'rise {rise}')
        dismounts('H', 'h3', 'h4')
    if marks('h5a', 'h5b'):
        x = st.get(mk['h5a'])
        check(x and x['veh'] == 'minecraft:horse' and ct[mk['h5a']]['state'] == 'STOWED', 'setup', 'H5 on the horse Stowed', f'server {x and (x["veh"], x["state"])}')
        bad, ok, n = stowed_span(mk['h5a'], mk['h5b'])
        check(ok and not bad, 'bug', 'H5 on the horse, Double Jump does not Activate the Rig', f'not Stowed on {len(bad)} ticks {bad[:4]}, {n}')
    # M minecart
    if marks('m0', 'm1', 'm2', 'm3'):
        mounted_from('M', 'm0', 'm1', 'ACTIVE', 'minecraft:minecart')
        ride_stows('M', 'm0', 'm1', 'm1', 'minecraft:minecart')
        dismounts('M', 'm1', 'm2')
        off = first(st, mk['m1'], mk['m2'], lambda x: x['veh'] == '-')
        after = span(ct, off, mk['m3']) if off else []
        check(after and all(y['state'] == 'STOWED' for _, y in after), 'bug', 'M after the Ride the Rig stays Stowed (no re-grab)', f'{len(after)} ticks after the dismount, states {sorted({y["state"] for _, y in after})}')
    if marks('j3a', 'j3b'):
        a, b = ct.get(mk['j3a']), ct.get(mk['j3b'])
        check(a and a['state'] == 'STOWED' and a['veh'] == '-' and b and b['state'] == 'ACTIVE', 'bug', 'J3 after the minecart Ride, Stowed, Double Jump activates again',
              f'j3a {a and (a["state"], a["veh"])}, j3b {b and b["state"]}')
    # C chain conveyor
    if marks('c0a', 'c0b'):
        win = span(ct, mk['c0a'], mk['c0b'])
        riding = sum(y['ride'] == 'chain' for _, y in win)
        d = path(ct, mk['c0a'], mk['c0b'])
        check(win and riding == len(win) and d is not None and d >= 3 and all(y['state'] == 'STOWED' for _, y in win), 'setup',
              'C0 Stowed: the chain conveyor ride carries the wearer (path >= 3 blocks)', f'riding {riding}/{len(win)} ticks, path {d}')
        sr = first(st, mk['c0a'] - 5, mk['c0b'], lambda x: x['ride'] == 'chain')
        check(sr is not None, 'setup', 'C0 server sees the chain ride', f'first server ride=chain gt {sr}')
    if marks('c1a', 'c1b', 'c1c'):
        g = first(ct, mk['c1a'], mk['c1b'], lambda x: x['ride'] == 'chain')
        check(g is not None and before(g) == 'ACTIVE', 'setup', 'C1 chain ride started with the Rig Active', f'ride start gt {g}, state before {before(g)}')
        okc, tc = stowed_soon(ct, g, mk['c1c'])
        check(okc, 'bug', f'C1 chain ride Stows the Rig (client, within {STOW_TICKS} ticks)', tc)
        sg = first(st, mk['c1a'], mk['c1c'], lambda x: x['ride'] == 'chain')
        oks, ts = stowed_soon(st, sg, mk['c1c'])
        check(oks, 'bug', f'C1 chain ride Stows the Rig (server, within {STOW_TICKS} ticks of its ride=chain)', f'server ride gt {sg}: {ts}')
        win = span(ct, mk['c1b'], mk['c1c'])
        riding = sum(y['ride'] == 'chain' for _, y in win)
        d = path(ct, mk['c1b'], mk['c1c'])
        check(win and riding >= 0.9 * len(win) and d is not None and d >= 3, 'bug', 'C1 chain ride carries the wearer (path >= 3 blocks, still riding)', f'riding {riding}/{len(win)} ticks, path {d}')
    # C2 old client on the chain: the server reads Create's hanging-player list (also Simulated's zipline), not a vehicle
    if marks('c2a', 'c2b'):
        x = st.get(mk['c2a'])
        faked = 'fake held for 40 ticks' in server
        check(x and x['ride'] == 'chain' and x['state'] == 'STOWED' and faked, 'setup', 'C2 on the chain Stowed (server ride=chain), forged Active packets sent',
              f'server {x and (x["ride"], x["state"])}, fake {faked}')
        win = span(st, mk['c2a'] + 3, mk['c2b'])
        bad = [g for g, y in win if y['state'] != 'STOWED']
        riding = sum(y['ride'] == 'chain' for _, y in win)
        check(full(win, mk['c2a'] + 3, mk['c2b']) and riding == len(win) and len(bad) <= 2 and all(y['state'] == 'STOWED' for _, y in win[-20:]), 'bug',
              'C2 server keeps the Rig Stowed for a chain rider (old client)', f'server not Stowed on {len(bad)}/{len(win)} ticks {bad[:4]}, ride=chain on {riding}')
    bad, skipped = kicks(server)
    check(not bad, 'guard', 'no kick, no moved wrongly', f'{bad[:2]}, {skipped} moved wrongly right after a forced dismount (reset) not counted')
    print(f"INFO moved too quickly lines: {len(re.findall(r'moved too quickly', server))} (a tp from spawn logs some)")
print('PASS' if not fails else 'FAIL ' + ', '.join(fails))
