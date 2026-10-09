"""Phase-4 numbers and pass/fail from a p4 suite log set. usage: p4_metrics.py tools/logs/<tag>-p4"""
import re, sys
pre = sys.argv[1]
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
r = open(f'{pre}-rcon.txt', errors='replace').read()
tr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num + r' d=' + num + r' .*?held=(\d) state=(\w+) fly=\w+ mayfly=\w+ .*?ground=(\w+) hp=(\S+) air=(\d+)', c):
    tr[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), held=int(m[8]), state=m[9], air=int(m[12]))
st = {}
for m in re.finditer(r'STRACE gt=(\d+) Dev pos=' + num + r' v=\S+ state=(\w+) grips=(\d) cf=(\S+) flying=(\w+) fall=(\S+) hp=(\S+) ground=(\w+) air=(\d+) mv=' + num, s):
    st[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), state=m[5], grips=int(m[6]), cf=float(m[7]), air=int(m[12]),
                         mv=tuple(map(float, m.group(13, 14, 15))))
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
smk = {m[0]: int(m[1]) for m in re.findall(r'SPOS (\S+) gt=(\d+)', s)}
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

def enchs(text):
    return dict((k, int(v)) for k, v in re.findall(r'([\w:]+):(\d+)', text))

sinv = {}
for m in re.finditer(r'SINV (\S+) Dev slot=(\S+) item=(\S+) ench=\[([^\]]*)\] stored=\[([^\]]*)\] damage=(\d+)/(\d+) air=(\d+) settings=(.*)', s):
    sinv.setdefault(m[1], []).append(dict(slot=m[2], item=m[3], ench=enchs(m[4]), stored=enchs(m[5]), damage=int(m[6]), max=int(m[7]), air=int(m[8])))
cmenu = {}
for m in re.finditer(r'CMENU (\S+) (.*)', c):
    cmenu.setdefault(m[1], []).append(m[2])

def reach_line(kind, tag):
    m = re.search(kind + r' ' + tag + r' (?:Dev )?reach=(\S+) top=(\S+) thrift=(\S+) swift=(\S+)', c if kind == 'CREACH' else s)
    return tuple(map(float, m.groups())) if m else None

print('== Enchanting table (vanilla EnchantmentMenu at a real table, 32 bookshelves)')
avail = {m[0]: dict((k, int(v)) for k, v in re.findall(r'([\w:]+)=(\d+)', m[1])) for m in re.findall(r'ENCHAVAIL item=(\S+) \{(.*)\}', s)}
ours = {'tentaworks:far_reach': 3, 'tentaworks:thrift': 3, 'tentaworks:swift': 2}
vanilla = {'minecraft:protection': 4, 'minecraft:fire_protection': 4, 'minecraft:blast_protection': 4, 'minecraft:projectile_protection': 4,
           'minecraft:thorns': 3, 'minecraft:unbreaking': 3, 'create:capacity': 3}
never = ['minecraft:mending', 'minecraft:binding_curse', 'minecraft:vanishing_curse', 'minecraft:sharpness', 'minecraft:swift_sneak']
for item in ('tentaworks:copper_tentacle_rig', 'tentaworks:netherite_tentacle_rig'):
    a = avail.get(item, {})
    miss = {k: (v, a.get(k)) for k, v in {**ours, **vanilla}.items() if a.get(k) is None}
    check(f'E table can offer ours + chest + Capacity + Unbreaking ({item.split(":")[1]})', not miss and not any(k in a for k in never),
          f"max level offered over power 1..50: {a}; missing {miss}; forbidden present {[k for k in never if k in a]}")
bt = avail.get('create:copper_backtank', {})
check('E control: Create backtank gets no Rig enchants, no Unbreaking', bt and not any(k in bt for k in ours) and 'minecraft:unbreaking' not in bt and 'create:capacity' in bt, f'{bt}')
stat = {(m[0], int(m[1])): dict((k, int(v)) for k, v in re.findall(r'([\w:]+)=(\d+)', m[2])) for m in re.findall(r'ENCHSTAT item=(\S+) seeds=\d+ slot=(\d) avgcost=\S+ clues=\{(.*)\}', s)}
for item in ('tentaworks:copper_tentacle_rig', 'tentaworks:netherite_tentacle_rig'):
    tot = {}
    for sl in (1, 2, 3):
        for k, v in stat.get((item, sl), {}).items(): tot[k] = tot.get(k, 0) + v
    check(f'E our enchantments shown as table clues over 300 seeds ({item.split(":")[1]})', all(tot.get(k, 0) > 0 for k in ours),
          f"clue counts slots 1-3: { {k: tot.get(k, 0) for k in ours} }, slot 3 {stat.get((item, 3))}")
seed = re.search(r'ENCHSEED item=\S+ seed=(\d+) costs=(\S+) clues=(\S+)', s)
offers = [x for x in cmenu.get('e1', []) if x.startswith('offer=')]
after = [x for x in cmenu.get('e2', []) if x.startswith('slot=0 ')]
rig = [x for x in sinv.get('e1', []) if x['item'] == 'tentaworks:copper_tentacle_rig']
check('E real client: third offer Far Reach, enchanting gives it', seed and any(o.startswith('offer=3') and 'tentaworks:far_reach' in o for o in offers)
      and after and 'tentaworks:far_reach' in after[0] and rig and 'tentaworks:far_reach' in rig[0]['ench'],
      f"seed {seed.groups() if seed else None}; client offers {offers}; table after {after}; server inventory {rig[0]['ench'] if rig else None}")

print('== Anvil + enchanted book (real client clicks)')
want = {'a1': {'minecraft:mending': 1}, 'a2': {'minecraft:unbreaking': 3}, 'a3': {'minecraft:protection': 4}, 'a4': {'create:capacity': 3},
        'a5': {'tentaworks:far_reach': 3}, 'a6': {'tentaworks:thrift': 3}, 'a7': {'tentaworks:swift': 2},
        'a8': {'minecraft:thorns': 3, 'minecraft:binding_curse': 1, 'minecraft:vanishing_curse': 1},
        'a9': {'tentaworks:far_reach': 3, 'tentaworks:thrift': 3, 'tentaworks:swift': 2, 'minecraft:mending': 1, 'minecraft:unbreaking': 3,
               'minecraft:protection': 4, 'create:capacity': 3}}
for case, w in want.items():
    items = [x for x in sinv.get(case, []) if x['item'] != 'minecraft:enchanted_book']
    books = [x for x in sinv.get(case, []) if x['item'] == 'minecraft:enchanted_book']
    cost = re.search(r'anvil cost=(\d+)', ' '.join(cmenu.get(case, [])))
    check(f'A {case} applies {w}', len(items) == 1 and items[0]['ench'] == w and not books and cost and int(cost[1]) > 0,
          f"result {[(x['item'], x['ench']) for x in items]}, book left {len(books)}, cost {cost[1] if cost else None}")
for case, why in (('r1', 'Blast Protection vs Protection IV'), ('r2', 'Sharpness'), ('r3', 'Far Reach on a Create backtank'),
                  ('r4', 'Mending on a Create backtank (Create still refuses)'), ('r5', 'Swift Sneak (legs only)')):
    items = [x for x in sinv.get(case, []) if x['item'] != 'minecraft:enchanted_book']
    books = [x for x in sinv.get(case, []) if x['item'] == 'minecraft:enchanted_book']
    cost = re.search(r'anvil cost=(\d+)', ' '.join(cmenu.get(case, [])))
    result = [x for x in cmenu.get(case, []) if x.startswith('slot=2 ')]
    base = {'minecraft:protection': 4} if case == 'r1' else {}
    check(f'A {case} refused: {why}', len(items) == 1 and items[0]['ench'] == base and len(books) == 1 and not result and cost and int(cost[1]) == 0,
          f"left item {[(x['item'], x['ench']) for x in items]}, book kept {len(books)}, result slot {result}, cost {cost[1] if cost else None}")

print('== Durability and Mending')
d = {t: next((x for x in sinv.get(t, []) if x['slot'] == '38'), None) for t in ('d0', 'd1', 'd2')}
check('D worn Rig wears from a mob hit, Mending repairs it from XP', all(d.values()) and d['d0']['damage'] == 0 and d['d0']['max'] == 112
      and d['d1']['damage'] > 0 and d['d2']['damage'] < d['d1']['damage'],
      f"damage {d['d0'] and d['d0']['damage']} -> {d['d1'] and d['d1']['damage']} (hit 8) -> {d['d2'] and d['d2']['damage']} (XP orb 10) of {d['d0'] and d['d0']['max']}")

print('== Rig Block place / pick up')
b0 = next((x for x in sinv.get('b0', []) if x['slot'] == '0'), None)
b1 = [x for x in sinv.get('b1', [])]
b2 = next((x for x in sinv.get('b2', []) if x['slot'] == '38'), None)
be = re.search(r'data get block 1521 -60 0 => (.*)', r)
check('B enchantments, damage, Air kept through Rig Block place + pick up', b0 and b2 and b0['ench'] == b2['ench'] and b2['damage'] == b0['damage'] == 20
      and b2['air'] == 300 and not b1 and be and 'tentaworks:far_reach' in be[1] and 'CapacityEnchantment: 2' in be[1],
      f"before {b0 and (b0['ench'], b0['damage'], b0['air'])}, placed BE has ours {bool(be and 'tentaworks:far_reach' in be[1])}, after pick up (chest) {b2 and (b2['ench'], b2['damage'], b2['air'])}")

print('== Far Reach')
for tag, exp in (('fx', (7.0, 8.0, 0.55, 1.0)), ('fn', (10.0, 8.0, 1.0, 1.0)), ('f1', (7.0, 10.4, 1.0, 1.3)), ('f1c', (4.0, 8.0, 1.0, 1.0))):
    sv, cv = reach_line('SREACH', tag), reach_line('CREACH', tag)
    ok = sv and cv and all(abs(a - b) < 0.01 for a, b in zip(sv, exp)) and all(abs(a - b) < 0.01 for a, b in zip(cv, exp))
    check(f'{tag.upper()} effective reach/top/thrift/swift', ok, f'server {sv}, client {cv}, expected {exp}')
clamp = re.search(r'SRIG f1clamp Dev slot=chest item=\S+ settings=RigSettings\[standingHeight=(\S+), speed=(\S+),', s)
check('F1 forged Standing Height 50 clamped to the enchanted Reach', clamp and float(clamp[1]) == 7.0 and float(clamp[2]) == 8.0, f'{clamp.groups() if clamp else None} (Reach 4+3, speed.max 8)')
w = span(tr, mk['f1a'], mk['f1idle']); wc = span(tr, mk['f1ca'], mk['f1cidle'])
top = max(x['p'][1] for _, x in w) + 60; topc = max(x['p'][1] for _, x in wc) + 60
sw = span(st, mk['f1top'] + 2, mk['f1idle'])
check('F1 max height grows by the Far Reach (III Copper vs plain Copper)', 5.9 <= top <= 6.2 and 2.9 <= topc <= 3.2 and all(x['state'] == 'ACTIVE' for _, x in sw),
      f'max feet {top:.2f} vs {topc:.2f} above the floor (+{top - topc:.2f}; Netherite N1 = 6.10), server at the top {states(sw)}, grips min {min(x["grips"] for _, x in sw)}')
w = span(tr, mk['f2a'] + 30, mk['f2hang']); sw = span(st, mk['f2a'] + 30, mk['f2hang'])
ys = [x['p'][1] for _, x in w]
check('F2 Far Reach III Copper hangs from the ceiling only', w and max(ys) - min(ys) < 0.01 and all(x['state'] == 'ACTIVE' for _, x in w)
      and all(x['state'] == 'ACTIVE' and x['grips'] >= 2 and x['cf'] == 1 for _, x in sw),
      f'feet {ys[-1]:.3f}, ceiling face -41 is {-41 - ys[-1] - 0.9:.2f} above the body, y range {max(ys) - min(ys):.4f}, client {states(w)}, server {states(sw)}, grips min {min(x["grips"] for _, x in sw)}')
w = span(tr, mk['f2hang'] + 1, mk['f2fell']); sw = span(st, mk['f2hang'] + 1, mk['f2fell'])
check('F2 control: plain Copper in the same spot falls', 'FALLING' in states(w) and w[-1][1]['p'][1] < ys[-1] - 5,
      f'client {states(w)}, feet {ys[-1]:.2f} -> {w[-1][1]["p"][1]:.2f}, server {states(sw)}')
warn = s.count('forcing Falling') + s.count('moves against Crawl')
check('Server never forced Falling (Far Reach Grips accepted)', warn == 0, f'{warn} warn lines')

print('== Thrift (server STRACE air, ticks between decrements)')
def drain(name, a, b, expect):
    w = span(st, a, b)
    dec = [g for (g0, x0), (g, x) in zip(w, w[1:]) if x['air'] < x0['air']]
    used = w[0][1]['air'] - w[-1][1]['air']
    if len(dec) < 3:
        check(name, False, f'only {len(dec)} Air decrements in {b - a} ticks'); return
    per = (dec[-1] - dec[0]) / (len(dec) - 1)
    check(name, abs(per - expect) <= 0.1 * expect,
          f'{per:.1f} ticks per Air (expected {expect:.1f}, {100 * (per - expect) / expect:+.1f} %), {len(dec)} decrements, {used} Air in {b - a} ticks, server states {sorted({x["state"] for _, x in w})}')
for lv, cut in ((1, 0.85), (3, 0.55)):
    t = f't{lv}'
    drain(f'T Thrift {"I" if lv == 1 else "III"} idle', mk[t + 'i'] + 5, mk[t + 'm'], 80 / cut)
    drain(f'T Thrift {"I" if lv == 1 else "III"} move', mk[t + 'm'] + 5, mk[t + 'w'], 20 / cut)
    drain(f'T Thrift {"I" if lv == 1 else "III"} climb', mk[t + 'c'] + 5, mk[t + 'd'], 10 / cut)

print('== Swift')
for tag, want in (('s1', 5.6 * 1.15), ('s2', 8.0 * 1.3)):
    a, b = tr[mk[tag + 'a']], tr[mk[tag + 'b']]
    dist = ((b['p'][0] - a['p'][0]) ** 2 + (b['p'][2] - a['p'][2]) ** 2) ** 0.5
    v = dist / ((mk[tag + 'b'] - mk[tag + 'a']) / 20)
    sv = [(x['mv'][0] ** 2 + x['mv'][2] ** 2) ** 0.5 * 20 for _, x in span(st, mk[tag + 'a'] + 2, mk[tag + 'b'] - 2)]
    check(f'S {tag} Swift {"I at 5.6" if tag == "s1" else "II at 8.0"}', abs(v - want) <= 0.1 * want and abs(max(sv) - want) <= 0.1 * want
          and all(x['state'] == 'ACTIVE' for _, x in span(tr, mk[tag + 'a'], mk[tag + 'b'])),
          f'{v:.2f} b/s (expected {want:.2f}, {100 * (v - want) / want:+.1f} %), server mirrored {min(sv):.2f}..{max(sv):.2f} b/s, y drift {b["p"][1] - a["p"][1]:+.3f}')
for tag, sent, want, what in (('fk1', 10.4, 10.4, 'Swift II accepts 10.4 b/s'), ('fk2', 20.0, 10.92, 'Swift II clamps 20 b/s to 10.4 x 1.05'),
                              ('fk3', 10.4, 8.4, 'no Swift clamps 10.4 b/s to 8 x 1.05')):
    w = span(st, smk[tag + 'a'], smk[tag + 'b'])
    mv = max((x['mv'][0] ** 2 + x['mv'][2] ** 2) ** 0.5 * 20 for _, x in w)
    check(f'S3 forged packet: {what}', abs(mv - want) <= 0.05, f'sent {sent} b/s, server mirrored max {mv:.2f} b/s (expected {want:.2f}), server states {states(w)}')

print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
