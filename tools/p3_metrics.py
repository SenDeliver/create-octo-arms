"""Phase-3 numbers and pass/fail from a p3 suite log set. usage: p3_metrics.py tools/logs/<tag>-p3"""
import re, sys
pre = sys.argv[1]
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
tr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num + r' d=' + num + r' .*?held=(\d) state=(\w+) fly=\w+ mayfly=\w+ .*?ground=(\w+) hp=(\S+) air=(\d+)', c):
    tr[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), d=tuple(map(float, m.group(5, 6, 7))), held=int(m[8]), state=m[9], ground=m[10] == 'true', hp=float(m[11]), air=int(m[12]))
st = {}
for m in re.finditer(r'STRACE gt=(\d+) Dev pos=' + num + r' v=\S+ state=(\w+) grips=(\d) cf=(\S+) flying=(\w+) fall=(\S+) hp=(\S+) ground=(\w+) air=(\d+) mv=' + num, s):
    st[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), state=m[5], grips=int(m[6]), cf=float(m[7]), fall=float(m[9]), hp=float(m[10]),
                         ground=m[11] == 'true', air=int(m[12]), mv=tuple(map(float, m.group(13, 14, 15))))
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
hurts = [(int(m[0]), float(m[1])) for m in re.findall(r'HURT gt=(\d+) Dev source=fall amount=(\S+)', s)]
falls = [(int(m[0]), float(m[1])) for m in re.findall(r'FALL gt=(\d+) Dev distance=(\S+)', s)]
SET = r'RigSettings\[standingHeight=(\S+), speed=(\S+), activation=(\w+)\]'
crig = {}
for m in re.finditer(r'CRIG (\S+) (?:screen settings=' + SET + r'|slot=(\w+) item=(\S+) settings=(?:' + SET + r'|null))', c):
    key = (m[1], 'screen' if m[2] else m[5])
    crig[key] = (float(m[2]), float(m[3]), m[4]) if m[2] else ((float(m[7]), float(m[8]), m[9]) if m[7] else None)
srig = {}
for m in re.finditer(r'SRIG (\S+) Dev slot=(\w+) item=(\S+) settings=(?:' + SET + r'|null)', s):
    srig[(m[1], m[2])] = ((float(m[4]), float(m[5]), m[6]) if m[4] else None, m[3])
screens = re.findall(r'SCREEN (\S+)', c)
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

SCREEN = 'dev.tentaworks.client.RigSettingsScreen'
edited = (1.0, 6.6, 'DOUBLE_JUMP')
print('== Rig Settings screen and the component')
check('B1 sneak + use in the air opens Rig Settings (defaults shown)', len(screens) >= 1 and screens[0] == SCREEN and crig.get(('b1open', 'screen')) == (1.5, 5.6, 'DOUBLE_JUMP'),
      f"screen {screens[:1]}, values {crig.get(('b1open', 'screen'))}")
check('B1 scrolled values written to the Rig in hand (server)', crig.get(('b1edit', 'screen')) == edited and srig.get(('b1', 'mainhand'), (None,))[0] == edited
      and ('b1', 'chest') not in srig and len(screens) >= 2 and screens[1] == 'none',
      f"screen {crig.get(('b1edit', 'screen'))}, server mainhand {srig.get(('b1', 'mainhand'))}, chest {srig.get(('b1', 'chest'), 'empty (not equipped)')}, after close {screens[1:2]}")
check('B3 drop + pick up keeps the settings', srig.get(('b3drop', 'mainhand'), (None,))[0] == edited and crig.get(('b3reopen', 'screen')) == edited,
      f"server {srig.get(('b3drop', 'mainhand'))}, reopened screen {crig.get(('b3reopen', 'screen'))}")
check('B3 sneak-place as a Rig Block, pick up (worn): settings kept', srig.get(('b3block', 'chest'), (None,))[0] == edited and ('b3block', 'mainhand') not in srig,
      f"server chest {srig.get(('b3block', 'chest'))}, mainhand {srig.get(('b3block', 'mainhand'), 'empty')}")
keyset = (1.0, 6.6, 'KEY')
check('B2 Rig Settings key while worn opens the screen; mode change saved', len(screens) >= 3 and screens[2] == SCREEN and crig.get(('b2open', 'screen')) == edited
      and srig.get(('b2', 'chest'), (None,))[0] == keyset, f"screen {screens[2:3]}, opened with {crig.get(('b2open', 'screen'))}, server chest {srig.get(('b2', 'chest'))}")
check('R relog keeps the settings', srig.get(('relog', 'chest'), (None,))[0] == keyset and crig.get(('relog', 'chest')) == keyset,
      f"server {srig.get(('relog', 'chest'))}, client {crig.get(('relog', 'chest'))}")
check('B4 forged packet Height 50 / Speed 100 clamped (Copper)', srig.get(('b4a', 'chest'), (None,))[0] == (4.0, 8.0, 'KEY'), f"{srig.get(('b4a', 'chest'))} (Reach 4, speed.max 8)")
check('B4 forged NaN / -3 clamped', srig.get(('b4b', 'chest'), (None,))[0] == (1.5, 4.3, 'DOUBLE_JUMP'), f"{srig.get(('b4b', 'chest'))} (NaN -> default 1.5, min speed 4.3)")
check('B4 forged Height 50 clamped to Netherite Reach', srig.get(('b4c', 'mainhand'), (None,))[0] == (7.0, 8.0, 'KEY'), f"{srig.get(('b4c', 'mainhand'))} (Reach 7)")

print('== Standing Height and Speed')
h10 = tr[mk['h10']]['p'][1] + 60; h25 = tr[mk['h25']]['p'][1] + 60
check('B5 Standing Height 1.0 vs 2.5', abs(h10 - 1.0) <= 0.05 and abs(h25 - 2.5) <= 0.05 and tr[mk['h10']]['state'] == 'ACTIVE' and tr[mk['h25']]['state'] == 'ACTIVE',
      f'idle feet above the floor {h10:.3f} and {h25:.3f} (difference {h25 - h10:.3f})')
for tag, want in (('s43', 4.3), ('s80', 8.0)):
    a, b = tr[mk[tag + 'a']], tr[mk[tag + 'b']]
    dist = ((b['p'][0] - a['p'][0]) ** 2 + (b['p'][2] - a['p'][2]) ** 2) ** 0.5
    v = dist / ((mk[tag + 'b'] - mk[tag + 'a']) / 20)
    sv = [x['mv'][0] * 20 for _, x in span(st, mk[tag + 'a'] + 2, mk[tag + 'b'] - 2)]
    check(f'B6 Speed {want}', abs(v - want) <= 0.1 * want and all(x['state'] == 'ACTIVE' for _, x in span(tr, mk[tag + 'a'], mk[tag + 'b'])),
          f'{v:.2f} b/s ({100 * (v - want) / want:+.1f} %), server mirrored {min(sv):.2f}..{max(sv):.2f} b/s, y drift {b["p"][1] - a["p"][1]:+.3f}')

print('== Key-only Activation Mode')
w = span(tr, mk['k1a'], mk['k1b']); sw = span(st, mk['k1a'], mk['k1b'])
rise = max(x['p'][1] for _, x in w) - w[0][1]['p'][1]
check('K1 Double Jump does not activate in key-only mode', all(x['state'] == 'STOWED' for _, x in w) and all(x['state'] == 'STOWED' for _, x in sw)
      and max(x['cf'] for _, x in sw) == 0 and rise < 1.26, f'client {states(w)}, server {states(sw)}, rise {rise:.3f} (plain vanilla jumps 1.252), cf max {max(x["cf"] for _, x in sw):.0f}')
k1c = tr[mk['k1c']]
check('K1 the toggle key activates (lifts to Standing Height 1.5)', k1c['state'] == 'ACTIVE' and abs(k1c['p'][1] + 60 - 1.5) <= 0.05 and st.get(mk['k1c'], {}).get('state') == 'ACTIVE',
      f"client {states(span(tr, mk['k1b'], mk['k1c']))}, feet {k1c['p'][1] + 60:.3f} above the floor, server {st.get(mk['k1c'], {}).get('state')}")
w = span(tr, mk['k1c'], mk['k1d'])
check('K1 Double Jump while Active in key-only mode does not Set Down', all(x['state'] == 'ACTIVE' for _, x in w), f'client {states(w)}')
w = span(tr, mk['k1d'], mk['k1e'])
check('K1 the toggle key sets down', states(w) == 'ACTIVE -> SETTING_DOWN -> STOWED' and w[-1][1]['ground'], f"client {states(w)}, end feet {w[-1][1]['p'][1]:.3f}")
w = span(tr, mk['k2a'], mk['k2b']); hk = [h for g, h in hurts if mk['k2a'] <= g <= mk['k2c']]
check('K2 mid-air key activation catches at the wall', 'FALLING -> ACTIVE' in states(w) and not hk and w[-1][1]['state'] == 'ACTIVE',
      f"client {states(w)}, held at feet {w[-1][1]['p'][1]:.2f} (wall top -50), fall damage {hk}")
w = span(tr, mk['k2b'], mk['k2c'])
check('K2 key sets down along the wall to the floor', w[-1][1]['state'] == 'STOWED' and w[-1][1]['ground'] and abs(w[-1][1]['p'][1] + 60) < 0.01, f"client {states(w)}, end feet {w[-1][1]['p'][1]:.3f}")

print('== Crawl let-go and re-grab (part A 1, 2)')
for tag, how in (('a1', 'Double Jump'), ('a1k', 'toggle key')):
    cr = tr[mk[tag + 'crawl']]
    w = span(tr, mk[tag + 'crawl'], mk[tag + 'end'])
    lg = first(tr, mk[tag + 'crawl'], mk[tag + 'end'], lambda x: x['state'] == 'STOWED')
    fall = next((d for g, d in falls if lg and lg <= g <= mk[tag + 'end'] and d > 3), float('nan'))
    dmg = sum(h for g, h in hurts if lg and lg <= g <= mk[tag + 'end'])
    cf_after = [x['cf'] for g, x in span(st, (lg or 0) + 3, mk[tag + 'end'])]
    check(f'{tag.upper()} {how} in Crawl lets go, vanilla fall damage', cr['state'] == 'CRAWL' and lg is not None and w[-1][1]['ground'] and dmg == max(0, -(-(fall - 3) // 1)) and dmg > 0 and max(cf_after) == 0,
          f"Crawl hold at feet {cr['p'][1]:.3f} (body {cr['p'][1] + 0.9:.2f}, ceiling -45 - Reach 4), client {states(w)}, fall {fall:.2f}, damage {dmg:.0f}, cf after let-go {sorted(set(cf_after))}")
w = span(tr, mk['a2crawl'], mk['a2end']); sw = span(st, mk['a2crawl'], mk['a2end'])
rg = first(tr, mk['a2cut'], mk['a2end'], lambda x: x['state'] == 'FALLING')
rc = first(tr, rg or mk['a2cut'], mk['a2end'], lambda x: x['state'] == 'CRAWL') if rg else None
dmg = [h for g, h in hurts if mk['a2cut'] <= g <= mk['a2end']]
crawl = [x for g, x in w if rc and g > rc + 3 and x['state'] == 'CRAWL']
cspeed = (crawl[0]['p'][1] - crawl[-1]['p'][1]) / (len(crawl) - 1) * 20 if len(crawl) > 10 else float('nan')
check('A2 Crawl loses its Grips: Falling, re-grab into Crawl, Stowed', states(w) == 'CRAWL -> FALLING -> CRAWL -> STOWED' and 'CRAWL -> FALLING -> CRAWL' in states(sw) and not dmg and w[-1][1]['ground'],
      f"client {states(w)}, server {states(sw)}, re-grab at feet {tr[rc]['p'][1] if rc else float('nan'):.2f} (floor -60, Reach 4), Crawl {cspeed:.2f} b/s, fall damage {dmg}")
forced = len(re.findall(r'moves against Crawl|forcing Falling', s))
check('A2 server accepts the re-grab (no forced Falling)', forced == 0 and min(x['cf'] for g, x in sw if x['state'] == 'CRAWL' and rc and g > rc + 2) == 1, f'{forced} forced Falling warns')

print('== Creative (part A 3)')
w = span(tr, mk['cr1a'], mk['cr1c']); sw = span(st, mk['cr1a'], mk['cr1c'])
act = first(st, mk['cr1a'], mk['cr1c'], lambda x: x['state'] == 'ACTIVE')
check('CR1 creative with 0 Air activates, moves, never Crawls', 'CRAWL' not in states(w) + states(sw) and act is not None and max(x['air'] for _, x in sw) == 0
      and sw[-1][1]['state'] == 'ACTIVE' and min(x['cf'] for g, x in sw if g > act + 2) == 1,
      f"client {states(w)}, server {states(sw)}, Air {max(x['air'] for _, x in sw)}, climbed {max(x['p'][1] for _, x in w) + 60:.2f}")
sw = span(st, mk['cr2a'], mk['cr2c'])
check('CR2 creative uses no Air', min(x['air'] for _, x in sw) == 900 and max(x['air'] for _, x in sw) == 900 and 'ACTIVE' in states(sw),
      f"Air {min(x['air'] for _, x in sw)}..{max(x['air'] for _, x in sw)} over {mk['cr2c'] - mk['cr2a']} ticks (moved, climbed, Set Down), server {states(sw)}")
check('Creative inventory opens', 'screenshot p3_creative_inventory.png' in c and 'Exception' not in c[c.find('script: key key.keyboard.e'):c.find('p3_creative_inventory')],
      'shot p3_creative_inventory.png')

print('== Long fall (part A 4)')
a, dj = mk['a4a'], mk['a4dj']
catch = first(tr, dj, mk['a4b'], lambda x: x['state'] == 'ACTIVE' and x['held'] >= 2)
kicks = [m.start() for m in re.finditer(r'kicked for floating too long', s)]
a4n_at = s.find('fallfix false')
before = [k for k in kicks if a4n_at < 0 or k < a4n_at]
dmg = [h for g, h in hurts if a <= g <= mk['a4end']]
check('A4 120+ tick Stowed fall, mid-air Double Jump, caught at the tall wall, not kicked', catch is not None and dj - a >= 120 and not before and not dmg,
      f"Stowed {dj - a} ticks from y 600 (Double Jump at feet {tr[dj]['p'][1]:.1f}), caught after {catch - a if catch else -1} ticks in the air at feet {tr[catch]['p'][1] if catch else float('nan'):.2f} (wall top 100), kicks {len(before)}, damage {dmg}")
after = [k for k in kicks if a4n_at >= 0 and k > a4n_at]
na = mk.get('a4na')
ntr = span(tr, na, na + 400) if na else []
check('A4 negative control: fall guard off, plain fall is kicked (vanilla zeroes the Y move)', bool(after),
      f"kick lines after 'fallfix false': {len(after)}, last traced tick {ntr[-1][0] - na if ntr else -1} after the tp, feet {ntr[-1][1]['p'][1] if ntr else float('nan'):.1f}")

print('== Log')
exc = sum(1 for t in (s, c) for line in t.splitlines() if 'Exception' in line and 'ScheduledForRemoval' not in line)
check('No exceptions', exc == 0, f'{exc}')
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
