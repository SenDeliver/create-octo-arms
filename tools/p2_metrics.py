"""Phase-2 numbers and pass/fail from a p2 suite log set. usage: p2_metrics.py tools/logs/<tag>-p2"""
import math, re, sys
pre = sys.argv[1]
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
r = open(f'{pre}-rcon.txt', errors='replace').read()
tr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num + r' .*?held=(\d) state=(\w+) .*?ground=(\w+) hp=(\S+) air=(\d+)', c):
    tr[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), held=int(m[5]), state=m[6], ground=m[7] == 'true', hp=float(m[8]), air=int(m[9]))
st = {}
for m in re.finditer(r'STRACE gt=(\d+) Dev pos=' + num + r' v=\S+ state=(\w+) grips=(\d) cf=(\S+) flying=(\w+) fall=(\S+) hp=(\S+) ground=(\w+) air=(\d+) mv=' + num, s):
    st[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), state=m[5], grips=int(m[6]), cf=float(m[7]), fall=float(m[9]), hp=float(m[10]),
                         ground=m[11] == 'true', air=int(m[12]), mv=tuple(map(float, m.group(13, 14, 15))))
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
for m in re.findall(r'SPOS (\S+) gt=(\d+) Dev', s):
    mk.setdefault(m[0], int(m[1]))
hurts = [(int(m[0]), float(m[1])) for m in re.findall(r'HURT gt=(\d+) Dev source=fall amount=(\S+)', s)]
falls = [(int(m[0]), float(m[1])) for m in re.findall(r'FALL gt=(\d+) Dev distance=(\S+)', s)]
titles = re.findall(r'TITLE gt=(\d+) subtitle="([^"]*)"', c)
fails = []

def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok: fails.append(name)

def span(d, a, b):
    return [(g, d[g]) for g in sorted(d) if a <= g <= b]

def states(w):
    out = []
    for _, x in w:
        if not out or out[-1] != x['state']: out.append(x['state'])
    return ' -> '.join(out)

def first(d, a, b, pred):
    return next((g for g, x in span(d, a, b) if pred(x)), None)

def drain(name, a, b, expect):
    w = span(st, a, b)
    dec = [g for (g0, x0), (g, x) in zip(w, w[1:]) if x['air'] < x0['air']]
    used = w[0][1]['air'] - w[-1][1]['air']
    if len(dec) < 3:
        check(name, False, f'only {len(dec)} Air decrements in {b - a} ticks'); return
    per = (dec[-1] - dec[0]) / (len(dec) - 1)
    check(name, abs(per - expect) <= 0.1 * expect,
          f'{per:.1f} ticks per Air (config {expect:.0f}, {100 * (per - expect) / expect:+.1f} %), {len(dec)} decrements, {used} Air in {b - a} ticks, server states {sorted({x["state"] for _, x in w})}')

print('== Drain (server STRACE air, ticks between decrements)')
drain('D copper idle', mk['dci'] + 5, mk['dcm'], 80)
drain('D copper move', mk['dcm'] + 5, mk['dcw'], 20)
drain('D copper climb', mk['dcc'] + 5, mk['dcd'], 10)
drain('D netherite idle', mk['dni'] + 5, mk['dnm'], 160)
drain('D netherite move', mk['dnm'] + 5, mk['dnw'], 40)
drain('D netherite climb', mk['dnc'] + 5, mk['dnd'], 20)
for tag, a, b in (('copper', 'dcd', 'dcs'), ('netherite', 'dnd', 'dns')):
    w = span(tr, mk[a], mk[b])
    check(f'D {tag} Setting Down along the wall', w[-1][1]['state'] == 'STOWED' and w[-1][1]['ground'] and min(x['hp'] for _, x in w) == 20,
          f"client {states(w)}, end feet {w[-1][1]['p'][1]:.2f} ground {w[-1][1]['ground']}, hp min {min(x['hp'] for _, x in w)}")

print('== Empty Rig')
w = span(tr, mk['e1a'], mk['e1b']); sw = span(st, mk['e1a'], mk['e1b'])
dy = max(x['p'][1] for _, x in w) - w[0][1]['p'][1]
check('E1 empty Rig cannot activate', all(x['state'] == 'STOWED' for _, x in w) and all(x['state'] == 'STOWED' for _, x in sw) and dy < 1.26
      and max(x['cf'] for _, x in sw) == 0 and w[-1][1]['ground'] and w[0][1]['air'] == 0,
      f"air {w[0][1]['air']}, client {states(w)}, server {states(sw)}, max rise {dy:.3f} (plain vanilla jumps, 1.252), cf max {max(x['cf'] for _, x in sw):.0f}, back on the floor {w[-1][1]['ground']}")

print('== C1 Air runs out mid-climb')
w = span(tr, mk['c1a'], mk['c1end']); sw = span(st, mk['c1a'], mk['c1end'])
cc = first(tr, mk['c1a'], mk['c1end'], lambda x: x['state'] == 'CRAWL'); sc = first(st, mk['c1a'], mk['c1end'], lambda x: x['state'] == 'CRAWL')
sa = first(st, mk['c1a'], mk['c1end'], lambda x: x['air'] == 0)
crawl = [(g, x) for g, x in w if x['state'] == 'CRAWL']
cr = crawl[5:-2]
speed = (cr[0][1]['p'][1] - cr[-1][1]['p'][1]) / (cr[-1][0] - cr[0][0]) * 20 if len(cr) > 10 else float('nan')
top = max(x['p'][1] for _, x in w)
check('C1 Crawl speed', abs(speed - 1.0) <= 0.1, f'{speed:.3f} b/s down (config 1.0) over {len(cr)} ticks, from feet {top:.2f} (climbed {top + 60:.1f})')
check('C1 Crawl ends Stowed on the floor', w[-1][1]['state'] == 'STOWED' and w[-1][1]['ground'] and min(x['hp'] for _, x in sw) == 20,
      f"client {states(w)}, server {states(sw)}, end feet {w[-1][1]['p'][1]:.3f}, hp min {min(x['hp'] for _, x in sw)}")
check('C1 client/server agree on Crawl', None not in (cc, sc, sa) and abs(cc - sc) <= 3, f'server Air 0 at gt {sa}, server CRAWL {sc}, client CRAWL {cc} ({(cc or 0) - (sc or 0):+d} ticks)')
check('C1 x stays at the wall while crawling', max(abs(x['p'][0] - crawl[0][1]['p'][0]) for _, x in crawl) < 0.05, f"x {crawl[0][1]['p'][0]:.3f}, max shift {max(abs(x['p'][0] - crawl[0][1]['p'][0]) for _, x in crawl):.3f}")

print('== Warnings')
low = [t for t in titles if 'low' in t[1].lower()]; dep = [t for t in titles if 'depleted' in t[1].lower()]
print('TITLE lines:', titles)
check('Create low-air title', bool(low), f'{low[:1]}')
check('Create depleted title', bool(dep), f'{dep[:1]}')

print('== C2 Air runs out hanging under a ceiling')
w = span(tr, mk['c2a'], mk['c2h2']); sw = span(st, mk['c2a'], mk['c2h2'])
hold = span(tr, mk['c2h1'], mk['c2h2']); shold = span(st, mk['c2h1'], mk['c2h2'])
ys = [x['p'][1] for _, x in hold]
check('C2 Crawl holds under the ceiling', max(ys) - min(ys) < 0.001 and all(x['state'] == 'CRAWL' for _, x in shold) and min(x['cf'] for _, x in shold) == 1,
      f"client {states(w)}, server {states(sw)}, hold {mk['c2h2'] - mk['c2h1']} ticks at feet {ys[-1]:.3f} (body {ys[-1] + 0.9:.2f}, ceiling -45, floor -60), y range {max(ys) - min(ys):.4f}, cf min {min(x['cf'] for _, x in shold):.0f}, grips min {min(x['grips'] for _, x in shold)}")
w = span(tr, mk['c2h2'], mk['c2h3']); sw = span(st, mk['c2h2'], mk['c2h3'])
ys = [x['p'][1] for _, x in w]
check('C2 client hitch (0.8 s, no packets): Crawl resumes', 'FALLING' in states(sw) and sw[-1][1]['state'] == 'CRAWL' and sw[-1][1]['cf'] == 1 and max(ys) - min(ys) < 0.01,
      f"server {states(sw)}, client {states(w)}, y range {max(ys) - min(ys):.4f}, cf end {sw[-1][1]['cf']:.0f}")
w = span(tr, mk['c2h3'], mk['c2end'])
check('C2 pillar brings a floor into Reach: Crawl down, Stowed', w[-1][1]['state'] == 'STOWED' and w[-1][1]['ground'] and abs(w[-1][1]['p'][1] + 52) < 0.01,
      f"client {states(w)}, end feet {w[-1][1]['p'][1]:.3f} (pillar top -52)")
kick = s.count('Flying is not enabled')
check('No float kick in p2', kick == 0, f'{kick}')

print('== Setting Down')
w = span(tr, mk['sd1a'], mk['sd1b']); sw = span(st, mk['sd1a'] - 30, mk['sd1b'])
check('SD1 on a floor: on feet, Stowed, no damage', w[-1][1]['state'] == 'STOWED' and w[-1][1]['ground'] and min(x['hp'] for _, x in sw) == 20,
      f"client {states(w)}, end feet {w[-1][1]['p'][1]:.3f}, server hp min {min(x['hp'] for _, x in sw)}")
w = span(tr, mk['sd2a'], mk['sd2b']); sw = span(st, mk['sd2a'], mk['sd2b'])
lg = first(tr, mk['sd2a'], mk['sd2b'], lambda x: x['state'] == 'STOWED')
letgo_y = tr[lg - 1]['p'][1]
land = first(st, lg, mk['sd2b'], lambda x: x['hp'] < 20)
fall = next((d for g, d in falls if lg <= g <= mk['sd2b'] and d > 3), float('nan'))
hp = 20 - sum(d for g, d in hurts if lg <= g <= mk['sd2b'])
exp = math.ceil(fall - 3) if fall == fall else -1
cf_after = [x['cf'] for g, x in span(st, lg + 3, lg + 10)]
check('SD2 under a ceiling: lets go at the lowest Held point', abs(letgo_y + 0.9 - (-49)) < 0.05 and states(w).startswith('ACTIVE -> SETTING_DOWN -> STOWED'),
      f"client {states(w)}, let go at feet {letgo_y:.3f} (body {letgo_y + 0.9:.3f}; ceiling -45 - Reach 4 = -49), server fall at let-go {st.get(lg, {}).get('fall')}, cf after {sorted(set(cf_after))}")
check('SD2 vanilla fall damage from the let-go point', land and abs(fall - (letgo_y + 60)) < 1.2 and 20 - hp == exp and max(cf_after) == 0,
      f'server fall distance at landing {fall:.2f} (let-go feet - floor = {letgo_y + 60:.2f}; vanilla skips the landing step), damage {20 - hp:.0f}, expected ceil(fall - 3) = {exp}')

print('== Mid-air Double Jump')
w = span(tr, mk['mj1a'], mk['mj1b']); sw = span(st, mk['mj1a'], mk['mj1b'])
caught = first(tr, mk['mj1a'] + 8, mk['mj1b'], lambda x: x['state'] == 'ACTIVE')
check('MJ1 caught next to the wall, no damage', caught is not None and min(x['hp'] for _, x in sw) == 20 and not any(mk['mj1a'] <= g <= mk['mj1b'] for g, _ in hurts) and not w[-1][1]['ground'],
      f"client {states(w)}, caught at feet {tr[caught]['p'][1] if caught else float('nan'):.2f} (wall top -50, start -40), held at {w[-1][1]['p'][1]:.2f}, server max fall {max(x['fall'] for _, x in sw):.2f}, hp min {min(x['hp'] for _, x in sw)}, fall damage events {[d for g, d in hurts if mk['mj1a'] <= g <= mk['mj1b']]}")
w = span(tr, mk['mj1b'], mk['mj1c'])
check('MJ1 then Setting Down to the floor', w[-1][1]['state'] == 'STOWED' and w[-1][1]['ground'], f"client {states(w)}, end feet {w[-1][1]['p'][1]:.3f}")
w = span(tr, mk['mj2a'], mk['mj2b']); sw = span(st, mk['mj2a'], mk['mj2b'])
land = first(st, mk['mj2a'], mk['mj2b'], lambda x: x['hp'] < 20)
fall = next((d for g, d in falls if mk['mj2a'] <= g <= mk['mj2b'] and d > 3), float('nan'))
hp = 20 - sum(d for g, d in hurts if mk['mj2a'] <= g <= mk['mj2b'])
check('MJ2 empty Rig: no catch, normal damage', all(x['state'] == 'STOWED' for _, x in w) and land and fall == fall and 20 - hp == math.ceil(fall - 3),
      f"air {w[0][1]['air']}, client {states(w)}, server {states(sw)}, fall {fall:.2f}, damage {20 - hp:.0f}, expected {math.ceil(fall - 3) if fall == fall else None}")

w = span(tr, mk['mj3a'], mk['mj3b'])
caught = first(tr, mk['mj3a'] + 8, mk['mj3b'], lambda x: x['state'] == 'ACTIVE')
dmg = [d for g, d in hurts if mk['mj3a'] <= g <= mk['mj3b']]
vmax = max((w[i - 1][1]['p'][1] - w[i][1]['p'][1] for i in range(1, len(w))), default=0)
cy = tr[caught]['p'][1] + 0.9 if caught else float('nan')
band = (4 ** 2 - (854.94 - 851) ** 2) ** 0.5
check('MJ3 fast fall past a thin bar: caught inside its Held band, no damage', caught is not None and not dmg and w[-1][1]['state'] == 'ACTIVE' and -20 - band - 0.01 <= cy <= -19 + band + 0.01,
      f"client {states(w)}, fastest fall step {vmax:.2f} b/tick, catch step {tr[caught - 1]['p'][1] - tr[caught - 2]['p'][1] if caught else 0:.3f} (swept: cut short inside the band), caught with body at {cy:.2f} (Held band {-20 - band:.2f} .. {-19 + band:.2f}, {2 * band + 1:.2f} high), damage {dmg}")

print('== Negative controls (forged Active climb packets with real Grips)')
sw = span(st, mk['nc1a'], mk['nc1b'])
check('NC1 Air 0, Dev on the floor: never Active, no lift', all(x['state'] == 'STOWED' for _, x in sw) and max(x['mv'][1] for _, x in sw) <= 0 and max(x['cf'] for _, x in sw) == 0,
      f"server {states(sw)}, mv.y max {max(x['mv'][1] for _, x in sw):.3f}, cf max {max(x['cf'] for _, x in sw):.0f}")
sw = span(st, mk['nc1pa'], mk['nc1pb'])
check('NC1 positive control, Air 900: Active, mv up', 'ACTIVE' in states(sw) and max(x['mv'][1] for _, x in sw) > 0.2 and max(x['cf'] for _, x in sw) == 1 and sw[-1][1]['cf'] == 0,
      f"server {states(sw)}, mv.y max {max(x['mv'][1] for _, x in sw):.3f}, cf max {max(x['cf'] for _, x in sw):.0f}, cf end {sw[-1][1]['cf']:.0f}")
sw = span(st, mk['nc2a'], mk['nc2b'])
ff = first(st, mk['nc2a'], mk['nc2b'], lambda x: x['state'] == 'FALLING')
warn = s.count('moves against Crawl')
w = span(tr, mk['nc2a'], mk['nc2b'])
check('NC2 Crawl + forged climb, Air 0: never Active, forced Falling', 'ACTIVE' not in states(sw) and max(x['mv'][1] for _, x in sw) <= 0 and ff is not None and ff - mk['nc2a'] <= 15 and warn == 1,
      f"server {states(sw)}, Falling after {ff - mk['nc2a'] if ff else None} ticks, mv.y max {max(x['mv'][1] for _, x in sw):.3f}, warn lines {warn}, client {states(w)}, cf end {sw[-1][1]['cf']:.0f}")

print('== Air agreement (client TRACE vs server STRACE)')
both = [g for g in tr if g in st]
bad = [g for g in both if not any((tr[g]['air'] > 0) == (st[k]['air'] > 0) for k in range(g - 2, g + 3) if k in st)]
check('Client and server agree on Air > 0 (+-2 ticks)', not bad, f'{len(both)} ticks compared, {len(bad)} disagree {bad[:5]}')
diff = [min(abs(tr[g]['air'] - st[k]['air']) for k in range(g - 2, g + 3) if k in st) for g in both]
print(f'max |client air - server air| (+-2 ticks) = {max(diff)}')
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
