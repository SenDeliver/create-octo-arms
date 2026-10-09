"""Phase-5 numbers and pass/fail from a p5 suite log set. usage: p5_metrics.py tools/logs/<tag>-p5"""
import re, sys
pre = sys.argv[1]
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
r = open(f'{pre}-rcon.txt', errors='replace').read()
tr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num + r' .*?held=(\d) state=(\w+) .*?ground=(\w+) hp=(\S+) air=(\d+)', c):
    tr[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), state=m[6], ground=m[7] == 'true', air=int(m[9]))
st = {}
for m in re.finditer(r'STRACE gt=(\d+) Dev pos=' + num + r' v=\S+ state=(\w+) grips=(\d) cf=(\S+) flying=(\w+) fall=(\S+) hp=(\S+) ground=(\w+) air=(\d+)', s):
    st[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), state=m[5], grips=int(m[6]), ground=m[11] == 'true', air=int(m[12]))
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
smk = {m[0]: int(m[1]) for m in re.findall(r'SPOS (\S+) gt=(\d+) Dev', s)}
titles = [(int(g), t) for g, t in re.findall(r'TITLE gt=(\d+) subtitle="([^"]*)"', c)]
hurts = [(int(g), src, float(a)) for g, src, a in re.findall(r'HURT gt=(\d+) Dev source=(\S+) amount=(\S+)', s)]
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


sinv = {}
for m in re.finditer(r'SINV (\S+) Dev slot=(\S+) item=(\S+) ench=\[([^\]]*)\] stored=\[[^\]]*\] damage=(\d+)/(\d+) air=(\d+)', s):
    sinv.setdefault(m[1], []).append(dict(slot=m[2], item=m[3], ench=m[4], damage=int(m[5]), max=int(m[6]), air=int(m[7])))
sname = {}
for m in re.finditer(r'SNAME (\S+) Dev slot=(\S+) item=(\S+) count=(\d+) air=(\S+) damage=(\d+)/(\d+) ench=(\d+) settings=(.*?) name=(.*)', s):
    sname.setdefault(m[1], []).append(dict(slot=m[2], item=m[3], air=m[5], damage=int(m[6]), max=int(m[7]), ench=int(m[8]), settings=m[9], name=m[10].strip()))
cmenu = {}
for m in re.finditer(r'CMENU (\S+) (.*)', c):
    cmenu.setdefault(m[1], []).append(m[2])
ctip = {}
for m in re.finditer(r'CTIP (\S+) (.*)', c):
    ctip.setdefault(m[1], []).append(m[2].strip())


def rig(tag, item='tentacle_rig'):
    xs = [x for x in sinv.get(tag, []) + sname.get(tag, []) if item in x['item'] and x['slot'] not in ('carried',)]
    return xs[0] if xs else None


print('== Recipes (recipe manager after /reload)')
rl = re.search(r'> reload => (.*)', r)
errs = [l for l in s.splitlines() if re.search(r'/ERROR\]|Exception', l) and 'No key layers' not in l and 'ScheduledForRemoval' not in l]
check('R /reload with no errors', rl is not None and not errs, f"reply {rl[1] if rl else None!r}; server ERROR/Exception lines: {len(errs)} {errs[:2]}")
want = ['copper_rig', 'netherite_rig', 'copper_rig_flipped_none', 'rig_missing_tentacle_none', 'tentacle_sequence', 'smithing', 'smithing_netherite_base_none']
got = dict((n, ok) for ok, n in re.findall(r'RECIPE (PASS|FAIL) (\S+)', s))
check('R lookups resolve (7 cases)', all(got.get(n) == 'PASS' for n in want), f"{got}")
cnt = re.search(r'RECIPE count tentaworks=(\d+) (.*)', s)
check('R exactly 4 Tentaworks recipes', cnt and cnt[1] == '4', cnt[2] if cnt else 'none')
for line in re.findall(r'RECIPE \w+ (?:copper_rig|netherite_rig|tentacle_sequence|smithing) .*', s): print('   ', line[:220])
adv = re.search(r'RECIPE advancement doc_rig present=(\w+) parent=(\S+)', s)
check('R advancement tentaworks:doc_rig loaded under create:backtank', adv and adv[1] == 'true' and adv[2] == 'create:backtank', adv.group(0) if adv else 'none')
items = dict(re.findall(r'ITEM (\S+) maxStack=(\d+)', s))
check('R Tentacle stacks to 64, Incomplete Tentacle to 1', items.get('tentaworks:tentacle') == '64' and items.get('tentaworks:incomplete_tentacle') == '1', f'{items}')
granted = re.findall(r'Dev has (?:made|reached|completed) the (?:advancement|goal|challenge) \[Doc\'s Rig\]', s)
check("R Doc's Rig granted once on the first Rig", len(granted) == 1, f'{len(granted)} grant lines')

print('== In-world machines')
asm = re.findall(r'ASSEMBLY (PASS|WAIT) items=\[(.*?)\] deployerHeld=\[(.*?)\]', s)
held = dict(x.split(':') for x in asm[-1][2].split(', ')) if asm else {}
check('SA Piston Extension Pole -> Tentacle on the Depot (2 loops, 6 stations)', asm and asm[-1][0] == 'PASS' and asm[-1][1].count('tentaworks:tentacle') == 1 and 'incomplete' not in asm[-1][1],
      f"last check {asm[-1][:2] if asm else None}")
used = [64 - int(v) for v in held.values()]
check('SA each Deployer used exactly once (2 Stickers, 2 Andesite Casings)', used == [1, 1, 1, 1], f'held after {held}')
chains = re.findall(r'CRAFTER chain=(-?\d+) speeds=\[(.*?)\]', s)
check('MC two 9-crafter chains, all spinning', len(chains) == 2 and all(ch == '9' and all(abs(float(v)) == 128 for v in sp.split(', ')) for ch, sp in chains), f'{chains}')
filled = re.findall(r'CRAFTER filled 9 slots, refused=\[(.*?)\]', s)
check('MC all 9 inputs accepted (both grids)', filled == ['', ''], f'{filled}')
outs = re.findall(r'CRAFTER out chest item=(\S+) count=(\d+) air=(\S+) damage=(\d+)/(\d+)', s)
first = {}
for o in outs: first.setdefault(o[0], o)
cu, ne = first.get('tentaworks:copper_tentacle_rig'), first.get('tentaworks:netherite_tentacle_rig')
check('MC Copper grid -> one Copper Rig, starts empty (backtank had 900 Air)', cu and cu[1] == '1' and cu[2] in ('none', '0') and cu[3] == '0', f'{cu}')
check('MC Netherite grid -> one Netherite Rig, starts empty (backtank had 500 Air)', ne and ne[1] == '1' and ne[2] in ('none', '0') and ne[3] == '0', f'{ne}')

print('== Rig never breaks')
w = [rig(t) for t in ('w1a', 'w1b', 'w1c', 'w1d', 'w1e')]
check('W1 two 8-point hits on 109/112 stop at 111 (never 112, never broken)', all(w[:3]) and [x['damage'] for x in w[:3]] == [109, 111, 111] and all(x['max'] == 112 for x in w[:3]),
      f"damage {[x and x['damage'] for x in w[:3]]}")
check('W1 a Rig given at damage 500 reads 111/112 and a hit keeps it', w[3] and w[4] and w[3]['damage'] == 111 and w[4]['damage'] == 111 and w[4]['air'] == 900,
      f"{w[3] and w[3]['damage']} -> {w[4] and w[4]['damage']}, air {w[4] and w[4]['air']}")
a, b = mk.get('w2a'), mk.get('w2c')
win = span(tr, a, b) if a and b else []
swin = span(st, a, b) if a and b else []
warn = [t for g, t in titles if a and b and a <= g <= b + 5 and 'worn out' in t]
jumped = max((x['p'][1] for _, x in win), default=0) - (win[0][1]['p'][1] if win else 0)
check('W2 worn-out Rig: two Double Jumps refused (client and server Stowed), warning subtitle', win and all(x['state'] == 'STOWED' for _, x in win) and all(x['state'] == 'STOWED' for _, x in swin) and warn,
      f"client {states(win)}, server {states(swin)}, plain jump rise {jumped:.3f}, subtitle {warn[:1]}")
a, b = smk.get('w3a'), smk.get('w3b')
f1 = span(st, a, b) if a and b else []
a2, b2 = smk.get('w3c'), smk.get('w3d')
f2 = span(st, a2, b2) if a2 and b2 else []
check('W3 forged Active packets on the worn Rig refused; control (damage 50) accepted', f1 and all(x['state'] != 'ACTIVE' for _, x in f1) and any(x['state'] == 'ACTIVE' for _, x in f2),
      f"worn: {states(f1)}; control: {states(f2)}")
up, hit, end = mk.get('w4up'), smk.get('w4hit'), mk.get('w4end')
w4 = span(tr, up, end) if up and end else []
sd = next((g for g, x in w4 if x['state'] == 'SETTING_DOWN'), None)
stow = next((g for g, x in w4 if x['state'] == 'STOWED' and sd and g > sd), None)
y_end = tr[end]['p'][1] if end in tr else None
y_up = tr[up]['p'][1] if up in tr else None
check('W4 Active Rig worn out by a hit: Sets Down within 3 ticks, Stowed on the floor', sd is not None and hit is not None and sd - hit <= 3 and stow and y_end is not None and abs(y_end + 60) < 0.01,
      f"client {states(w4)}; lifted to {y_up}, hit gt {hit}, Setting Down after {None if sd is None or hit is None else sd - hit} ticks, Stowed after {None if stow is None or sd is None else stow - sd}, end y {y_end}; server {states(span(st, up, end) if up and end else [])}; damage {rig('w4end') and rig('w4end')['damage']}")
up, hit, end = mk.get('w5up'), smk.get('w5hit'), mk.get('w5end')
w5 = span(tr, up, end) if up and end else []
fall = [h for h in hurts if h[1] == 'fall' and hit and end and hit <= h[0] <= end + 5]
check('W5 worn out while hanging (no floor in Reach): Sets Down, lets go, falls with fall damage', w5 and 'SETTING_DOWN' in states(w5) and w5[-1][1]['state'] == 'STOWED' and w5[-1][1]['ground'] and fall,
      f"client {states(w5)}; hang y {tr.get(up, {}).get('p')}, fall damage {fall}")
up, hit, end = mk.get('w6up'), smk.get('w6hit'), mk.get('w6end')
w6 = span(tr, up, end) if up and end else []
pre_hit = span(tr, hit - 10, hit - 1) if hit else []
fall = [h for h in hurts if h[1] == 'fall' and hit and end and hit <= h[0] <= end + 5]
check('W6 Crawl holding under a ceiling, worn out by a hit: lets go, falls', pre_hit and all(x['state'] == 'CRAWL' for _, x in pre_hit) and w6[-1][1]['state'] == 'STOWED' and w6[-1][1]['ground'] and fall,
      f"client {states(w6)}; Crawl before the hit {len(pre_hit)} ticks, fall damage {fall}")

print('== Repair')
a0, a1 = rig('an1a'), rig('an1')
cost = [x for x in cmenu.get('an1', []) if 'cost=' in x]
check('A1 anvil: worn Copper (111) + 2 copper ingots -> 55/112, Air/enchants/settings/name kept', a0 and a1 and a1['damage'] == 55 and a1['air'] == a0['air'] == '600' and a1['ench'] == a0['ench'] == 2
      and a1['settings'] == a0['settings'] and a1['name'] == a0['name'] == 'Octo', f"before {a0}; after {a1}; {cost}")
an2 = [x for x in cmenu.get('an2', []) if x.startswith('slot=2 ')]
r2 = rig('an2')
check('A2 control: Copper Rig + netherite ingot gives no result', not an2 and r2 and r2['damage'] == 111, f"result slot {an2}, rig damage {r2 and r2['damage']}")
r3 = rig('an3')
check('A3 anvil: worn Netherite (591) + 1 netherite ingot -> 443/592, Thrift kept', r3 and r3['item'].endswith('netherite_tentacle_rig') and r3['damage'] == 443 and r3['ench'] == 1, f'{r3}')
m0, m2 = rig('m0'), rig('m2')
a, b = mk.get('m1a'), mk.get('m1b')
m1 = span(tr, a, b) if a and b else []
up, end = mk.get('m2up'), mk.get('m2end')
check('M Mending: worn Rig refused, a 10-XP orb mends 20, then it activates and Sets Down', m0 and m2 and m0['damage'] == 111 and m2['damage'] == 91 and m1 and all(x['state'] == 'STOWED' for _, x in m1)
      and up in tr and tr[up]['state'] == 'ACTIVE' and end in tr and tr[end]['state'] == 'STOWED',
      f"damage {m0 and m0['damage']} -> {m2 and m2['damage']}; worn: {states(m1)}; mended: {tr.get(up, {}).get('state')} then {tr.get(end, {}).get('state')}")

print('== Tooltips (real hover text)')
dur = lambda t: [x for x in ctip.get(t, []) if x.startswith('Durability')]
worn = lambda t: [x for x in ctip.get(t, []) if x.startswith('Worn out')]
check('T undamaged: no durability or worn-out line', ctip.get('t0') and not dur('t0') and not worn('t0'), f"{len(ctip.get('t0', []))} lines")
check('T damaged 50: "Durability: 62 / 112", not worn out', dur('t1') == ['Durability: 62 / 112'] and not worn('t1'), f"{dur('t1')} {worn('t1')}")
check('T worn Copper: durability 1 / 112 + worn-out line naming the Copper Ingot', dur('t2') == ['Durability: 1 / 112'] and worn('t2') and 'Copper Ingot' in worn('t2')[0], f"{dur('t2')} {worn('t2')}")
check('T worn Netherite: worn-out line naming the Netherite Ingot', worn('t3') and 'Netherite Ingot' in worn('t3')[0], f"{dur('t3')} {worn('t3')}")

print('== Smithing (real client clicks)')
s0 = rig('sm0')
s1 = rig('sm', 'netherite_tentacle_rig')
check('SM Copper Rig -> Netherite Rig keeps Air, enchants, settings, name, damage', s0 and s1 and s1['air'] == s0['air'] == '500' and s1['damage'] == 40 and s1['max'] == 592 and s1['ench'] == 1
      and s1['settings'] == s0['settings'] and s1['name'] == 'Smithy' and not any('copper_tentacle_rig' in x['item'] for x in sname.get('sm', [])),
      f"before {s0}; after {s1}")

print('== JEI and creative tab')
jei = re.findall(r'JEI (\S+) (\w+) \d+ (\S+) -> (\d+) recipes', c)
check('J JEI shows each recipe (Sequenced Assembly, 2x Mechanical Crafting, Smithing)', len(jei) == 4 and all(int(x[3]) >= 1 for x in jei), f'{jei}')
check('C creative inventory opened on the Tentaworks tab', 'tab tentaworks:main selected' in c, '')
shots = re.findall(r'screenshot (p5_\S+)\.png', c)
check('S screenshots taken (12)', len(set(shots)) == 12, f'{sorted(set(shots))}')
cex = [l for l in c.splitlines() if 'Exception' in l and 'ScheduledForRemoval' not in l]
check('client log has no exceptions', not cex, f'{cex[:2]}')

print(f"RESULT {'PASS' if not fails else 'FAIL'} ({len(fails)} failed: {fails})")
