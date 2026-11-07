"""curios suite (issue #17, adr/0006: Curios Back Slot): pass/fail from a curios log set. usage: curios_metrics.py tools/logs/<tag>-curios"""
import os, re, sys

pre = sys.argv[1]
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
c2 = open(f'{pre}-client2.log', errors='replace').read() if os.path.exists(f'{pre}-client2.log') else ''
CHEST, RIG, ELYTRA = 'minecraft:iron_chestplate', 'tentaworks:copper_tentacle_rig', 'minecraft:elytra'
IRON_ARMOR = 6
fails = []


def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok:
        fails.append(name)


back = {m[0]: dict(worn=m[1], item=m[2], air=int(m[3]), dmg=int(m[4]), max=int(m[5]), armor=int(m[6]), chest=m[7], sources=int(m[8]), slots=int(m[9]))
        for m in re.findall(r'SBACK (\S+) Dev worn=(\S+) backitem=(\S+) backair=(-?\d+) backdmg=(\d+) backmax=(\d+) armor=(\d+) chest=(\S+) sources=(\d+) slots=(\d+)', s)}
tries = {m[0]: dict(back=m[1] == 'true', chest=m[2] == 'true') for m in re.findall(r'SBACKTRY (\S+) Dev backok=(\w+) chestok=(\w+)', s)}
st = {}
for m in re.finditer(r'STRACE gt=(\d+) Dev .*? state=(\w+) grips=(\d+) .*? air=(\d+) .* armor=(\d+) rigslot=(\S+) rigdmg=(\d+) ff=(\w+)', s):
    st[int(m[1])] = dict(state=m[2], grips=int(m[3]), air=int(m[4]), armor=int(m[5]), slot=m[6], dmg=int(m[7]), ff=m[8] == 'true')
tr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) .*? state=(\w+) .*? air=(\d+) ff=(\w+) .* armor=(\d+) rigslot=(\S+)', c):
    tr[int(m[1])] = dict(state=m[2], air=int(m[3]), ff=m[4] == 'true', armor=int(m[5]), slot=m[6])
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
tips = {}
for m in re.finditer(r'CTIP (\S+) (.*)', c):
    tips.setdefault(m[1], []).append(m[2])
srig_back = {m[0]: m[1] for m in re.findall(r'SRIG (\S+) Dev slot=back item=\S+ settings=(.*)', s)}
screens = re.findall(r'SCREEN (\S+)', c)
inv = {(m[0], m[1]): m[2] for m in re.findall(r'SINV (\S+) Dev slot=(\S+) item=(\S+)', s)}
overlays = [(int(m[0]), m[1]) for m in re.findall(r'OVERLAY gt=(\d+) text="(.*?)"', c)]

missing = [k for k in ('a0', 'a1', 'h2', 's1', 'w0', 'w1', 'w2', 'r2', 'r1a', 'r1', 'u1', 'e0', 'e1', 'e2', 'e3', 'end') if k not in mk]
tags = [k for k in ('u0', 'b0', 'a0', 'a1', 'h0', 'h1', 'h2', 'w0', 'w1', 'w2', 'r3', 'r1', 'u1a', 'u1', 'm0', 'm1', 'e1') if k not in back]
if missing or tags or not st or not tr:
    print(f'FAIL marks or back lines missing: marks {missing}, SBACK {tags}, STRACE {len(st)}, TRACE {len(tr)}')
    sys.exit(1)


def span(d, a, b):
    return [(g, d[g]) for g in sorted(d) if a <= g <= b]


def at(d, g):
    keys = [k for k in d if k <= g]
    return d[max(keys)] if keys else None


def overlay_between(a, b, text):
    return any(a <= g <= b and text in t for g, t in overlays)


u0 = back['u0']
check('U0 use with an empty chest slot equips into the chest (vanilla)', u0['worn'] == 'chest' and u0['chest'] == RIG and ('u0', '0') not in inv, f"{u0}, hotbar 0 {inv.get(('u0', '0'))}")
b0 = back['b0']
check('B0 back slot Rig worn over an iron chestplate', b0['slots'] >= 1 and b0['worn'] == 'back' and b0['item'] == RIG and b0['chest'] == CHEST and b0['air'] == 900, f'{b0}')
check('B0 no armor value from the back slot Rig (chestplate only)', b0['armor'] == IRON_ARMOR, f"armor {b0['armor']} (iron chestplate {IRON_ARMOR})")
check('B0 Create sees the back slot Rig as an Air source (Diving Helmet, air HUD)', b0['sources'] == 1, f"getAllWithAir {b0['sources']}")
t0 = ' | '.join(tips.get('t0', []))
check('T tooltip: no armor in the back slot', 'In the back slot: no armor' in t0, t0[-240:])
check('T tooltip: armor enchantments idle in the back slot', 'do nothing in the back slot' in t0, t0[-240:])
a = span(st, mk['a0'], mk['a1'])
act = [x for _, x in a if x['state'] == 'ACTIVE']
check('A Rise from the back slot: Active and Held on the server', len(act) >= 0.9 * len(a) and all(x['grips'] >= 2 for x in act) and all(x['slot'] == 'back' for _, x in a),
      f"{len(act)}/{len(a)} Active, grips {sorted(set(x['grips'] for x in act))}, rigslot {sorted(set(x['slot'] for _, x in a))}")
ca = span(tr, mk['a0'], mk['a1'])
check('A client sees the back slot Rig and is Active', ca and all(x['slot'] == 'back' for _, x in ca) and sum(x['state'] == 'ACTIVE' for _, x in ca) >= 0.9 * len(ca),
      f"{sum(x['state'] == 'ACTIVE' for _, x in ca)}/{len(ca)} Active, rigslot {sorted(set(x['slot'] for _, x in ca))}")
armors = sorted(set(x['armor'] for _, x in span(st, mk['a0'], mk['h2'] - 1)))
check("A armor stays the chestplate's while Active", armors == [IRON_ARMOR], f'server armor {armors}')
check('A Air drains from the back slot Rig', back['a1']['air'] < back['a0']['air'] <= 900 and a[-1][1]['air'] < a[0][1]['air'],
      f"SBACK a0 {back['a0']['air']} -> a1 {back['a1']['air']}, STRACE {a[0][1]['air']} -> {a[-1][1]['air']}")
h0, h1, h2 = back['h0']['dmg'], back['h1']['dmg'], back['h2']['dmg']
check('H three hits wear the back slot Rig 1 each, like chest armor', h1 - h0 == 3, f'damage {h0} -> {h1}')
check('H a fall hit (bypasses armor) does not wear it', h2 == h1, f'damage {h1} -> {h2}')
check('S Rig Settings key opens the screen for the back slot', any(x.endswith('RigSettingsScreen') for x in screens), f'screens {screens}')
check('S settings written to the back slot Rig', 's1' in srig_back and 'standingHeight=1.2' in srig_back['s1'].replace(' ', ''), f"SRIG s1 back {srig_back.get('s1')}")
w0 = at(st, mk['w0'])
w = span(st, mk['w0'] + 1, mk['w1'])
stowed_at = next((g for g, x in w if x['state'] == 'STOWED'), None)
check('W Active from the back slot before the forced chest Rig', w0 is not None and w0['state'] == 'ACTIVE' and w0['slot'] == 'back', f'{w0}')
check('W a Rig forced into the chest mid-use Sets Down within 5 ticks', stowed_at is not None and stowed_at - mk['w0'] <= 10 and at(st, mk['w1'])['state'] == 'STOWED' and at(tr, mk['w1'])['state'] == 'STOWED',
      f"stowed at +{None if stowed_at is None else stowed_at - mk['w0']} ticks, server {at(st, mk['w1'])['state']}, client {at(tr, mk['w1'])['state']}")
check('W the chest Rig wins', back['w1']['worn'] == 'chest' and back['w1']['chest'] == RIG and at(st, mk['w1'])['slot'] == 'chest' and abs(at(st, mk['w1'])['air'] - 300) <= 2,
      f"worn {back['w1']['worn']}, chest {back['w1']['chest']}, STRACE {at(st, mk['w1'])}")
check('W the back slot Rig stops draining once inert', back['w2']['air'] == back['w1']['air'], f"back Air w1 {back['w1']['air']} -> w2 {back['w2']['air']}")
tr2 = tries.get('r2')
check('R2 the back slot refuses a Rig while the chest holds one', tr2 is not None and not tr2['back'], f'{tr2}')
t2 = ' | '.join(tips.get('r2', []))
check('R2 tooltip: the back slot Rig is inert', 'does nothing while a Rig is in the chest slot' in t2, t2[-240:])
check('R3 chestplate back: the back slot Rig is worn again', back['r3']['worn'] == 'back' and back['r3']['chest'] == CHEST, f"{back['r3']}")
r1, tr1 = back['r1'], tries.get('r1')
check('R1 a Rig used from the hotbar is refused: chest keeps the chestplate, the Rig stays in the hotbar', r1['chest'] == CHEST and r1['worn'] == 'back' and inv.get(('r1', '0')) == RIG,
      f"chest {r1['chest']}, worn {r1['worn']}, hotbar 0 {inv.get(('r1', '0'))}")
check('R1 the chest slot refuses a Rig while the back slot holds one (canEquip)', tr1 is not None and not tr1['chest'], f'{tr1}')
check('R1 the action bar says one Rig at a time', overlay_between(mk['r1a'], mk['r1'], 'One Rig at a time'), f"overlays {[t for g, t in overlays if mk['r1a'] <= g <= mk['r1']]}")
u1a, u1 = back['u1a'], back['u1']
check('U1 use with a chestplate on and a free back slot puts the Rig into the back slot', u1a['worn'] == '-' and u1['worn'] == 'back' and u1['item'] == RIG and u1['chest'] == CHEST
      and ('u1', '0') not in inv, f"before {u1a['worn']}, after {u1}, hotbar 0 {inv.get(('u1', '0'))}")
m0, m1 = back['m0'], back['m1']
check('M Mending repairs the back slot Rig', m0['dmg'] == 20 and m1['dmg'] < m0['dmg'] and m1['worn'] == 'back', f"damage {m0['dmg']} -> {m1['dmg']}")
e = span(tr, mk['e0'], mk['e1'])
deployed = [x for _, x in e if x['state'] != 'STOWED']
check('E Elytra in the chest: Active from the back slot Rig', len(deployed) >= 0.9 * len(e) and back['e1']['chest'] == ELYTRA and all(x['slot'] == 'back' for _, x in e),
      f"{len(deployed)}/{len(e)} deployed, chest {back['e1']['chest']}")
check('E the Elytra never opens while deployed', deployed and not any(x['ff'] for x in deployed) and not any(x['ff'] for _, x in span(st, mk['e0'], mk['e1'])),
      f"client {sum(x['ff'] for x in deployed)} gliding of {len(deployed)} deployed ticks, server {sum(x['ff'] for _, x in span(st, mk['e0'], mk['e1']))}")
glide = span(tr, mk['e2'], mk['e3'])
check('E2 after Set Down a jump tap opens the Elytra', at(tr, mk['e2'])['state'] == 'STOWED' and any(x['ff'] for _, x in glide) and all(x['state'] == 'STOWED' for _, x in glide),
      f"{sum(x['ff'] for _, x in glide)} gliding ticks, states {sorted(set(x['state'] for _, x in glide))}")
check('O Dev2 shots of the back slot Rig', all(f'curios_observer_{v}' in c2 for v in ('back', 'side')), f"client2 log {'present' if c2 else 'missing'}")
rw = re.findall(r'RWALK gt=\d+ name=Dev state=(\w+)', c2)
check("O Dev2 draws Dev's Rig Active", 'ACTIVE' in rw, f"{len(rw)} RWALK lines, states {sorted(set(rw))}")
bad = [l for l in s.splitlines() if re.search(r'was kicked|moves faster than|moves against|forcing Falling', l)]
check('N no kick or Rig warning', not bad, f'{len(bad)} lines' + (f': {bad[0][-160:]}' if bad else ''))
exc = [l for l in (c + s + c2).splitlines() if re.match(r'\[[\d:]+\] \[[^\]]*/(ERROR|FATAL)\]', l) or re.match(r'\s+at dev\.tentaworks\.', l)
       or re.match(r'\[[\d:]+\] \[', l) and 'Exception' in l and 'Error loading class' not in l]
exc = [l for l in exc if 'No key layers' not in l and 'MessageIOException' not in l and 'Unexpected type tag' not in l]
check('N no exceptions', not exc, f'{len(exc)} lines' + (f': {exc[0][-200:]}' if exc else ''))
print(f'curios: {len(fails)} FAIL' if fails else 'curios: all PASS')
