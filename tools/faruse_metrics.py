"""Far Use pass/fail from a faruse suite log set (TW_REACH_COPPER=6). usage: faruse_metrics.py tools/logs/<tag>-faruse"""
import re, sys
pre = sys.argv[1]
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
fails = []

def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok: fails.append(name)

spos = {}
for m in re.finditer(r'SPOS (\S+) gt=(\d+) Dev .*? state=(\w+) .*? air=(\d+) .*? lift=(\S+),(\S+) gheld=(-?\d+) gheavy=(\w+) .*? adebt=(\S+) mspd=(\S+) xp=(\d+) fitted=(\w+) deft=\w+ farmode=(\S+)', s):
    spos[m[1]] = dict(gt=int(m[2]), state=m[3], air=int(m[4]), lift=(float(m[5]), float(m[6])), held=int(m[7]), heavy=m[8] == 'true',
                      debt=float(m[9]), mspd=float(m[10]), xp=int(m[11]), fitted=m[12] == 'true', mode=m[13])
far = [dict(gt=int(m[1]), kind=m[2], at=m[3], type=m[4], dist=float(m[5]), range=float(m[6]), lift=float(m[7]), far=m[8], sight=m[9] == 'true',
            asking=m[10] == 'true', canceled=m[11] == 'true')
       for m in re.finditer(r'SFAR gt=(\d+) Dev kind=(\w+) at=(\S+) type=(\S+) dist=(\S+) range=(\S+) lift=(\S+) far=(\w+) sight=(\w+) asking=(\w+) canceled=(\w+)', s)]
for f, m in zip(far, re.finditer(r'SFAR gt=\d+ Dev kind=\w+ .*? canceled=\w+(?: bdist=(\S+))?', s)):
    f['bdist'] = float(m[1]) if m[1] else None
dual = [dict(gt=int(m[0]), kind=m[1], off=m[2], offdmg=int(m[3]), air=float(m[4]), maindmg=int(m[5]))
        for m in re.findall(r'SDUAL gt=(\d+) Dev kind=(\w+) off=(\S+) offdmg=(\d+) air=(\S+) maindmg=(\d+)', s)]
drops = {m[1]: dict(gt=int(m[0]), items=m[2], far=float(m[3]), xp=int(m[4])) for m in re.findall(r'SDROPS gt=(\d+) Dev pos=(\S+) n=\d+ items=(\S+) far=(\S+) xp=(\d+)', s)}
probe = {}
for m in re.finditer(r'PROBE (-?\d+), (-?\d+), (-?\d+) Block\{([^}]+)\}', s):
    probe.setdefault(f'{m[1]},{m[2]},{m[3]}', []).append(m[4])
hurt = [dict(gt=int(m[0]), id=m[1], source=m[2], amount=float(m[3]), by=m[4]) for m in re.findall(r'MHURT gt=(\d+) id=(\d+) type=\S+ source=(\S+) amount=(\S+) by=(\S+)', s)]
mobs = {}
for m in re.finditer(r'SMOB gt=\d+ id=(\d+) type=(\S+) pos=(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)', s):
    mobs.setdefault(m[1], (m[2], float(m[3]), float(m[5])))
overlay = [(int(g), t) for g, t in re.findall(r'OVERLAY gt=(\d+) text="([^"]*)"', c)]
chit = {m[0]: m[1] for m in re.findall(r'CHIT (\S+) type=\w+ .*? entity=(\S+)', c)}
chitb = {m[0]: (m[1], m[2]) for m in re.findall(r'CHIT (\S+) type=(\w+) block=(\S+)', c)}
cact = {m[0]: dict(brange=float(m[1]), erange=float(m[2])) for m in re.findall(r'CACT (\S+) gt=\d+ .*? brange=(\S+) erange=(\S+)', c)}
ctr = {int(g): int(f) for g, f in re.findall(r'TRACE t=\d+ gt=(\d+) .*? far=(\d+) xh=', c)}
NO_SIGHT = 'Out of sight: the Actuator does not reach through blocks'

def mob(x, z):
    return next((i for i, (t, mx, mz) in mobs.items() if abs(mx - x) < 0.05 and abs(mz - z) < 0.05), None)

def sfar(kind, at):
    return [f for f in far if f['kind'] == kind and f['at'] == at]

def last_probe(pos):
    return probe.get(pos, ['?'])[-1]

def paid(a, b):
    return (spos[a]['air'] - spos[b]['air']) + (spos[b]['debt'] - spos[a]['debt'])

def refused_note(gt):
    return any(t == NO_SIGHT and 0 <= g - gt <= 5 for g, t in overlay)

print('== Setup')
want = {'z7': (11104.05, 102.5), 'z1': (11005.3, 30.5), 'z2': (11005.3, 40.5), 'z3': (11003.5, 60.5), 'z4': (11104.05, 10.5), 'z5': (11105.4, 20.5), 'z6': (11108.3, 40.5), 'g1': (11003.5, 50.5)}
ids = {k: mob(*v) for k, v in want.items()}
for k, i in ids.items():
    check(f'[setup] {k} spawned', i is not None, f'id {i} {mobs.get(i, ("-",))[0]}')
n1a = spos.get('n1a', {})
check('[setup] Netherite Rig worn and fitted', n1a.get('fitted') and n1a.get('mode') == 'always', f"{n1a}")
if not all(ids.values()) or not n1a:
    print('RESULT FAIL setup'); sys.exit(0)

NLIFT, CLIFT = (3.22, 4.72), (2.22, 3.72)
print('== Netherite (Reach 7)')
check('N lift 3.22 blocks, 4.72 mobs Stowed (Reach 7 from the body)', n1a['lift'] == NLIFT and n1a['state'] == 'STOWED', f"lift {n1a['lift']} state {n1a['state']}")
x = sfar('lclick', '11007,-59,0')
check('N1 far mine at 6.5 accepted', x and x[0]['far'] == 'FAR' and not x[0]['canceled'] and sfar('break', '11007,-59,0') and last_probe('11007,-59,0') == 'minecraft:air',
      f"{x[:1]} probe {last_probe('11007,-59,0')}")
d = drops.get('11007,-59,0')
check('N1 drops at the wearer', d and d['far'] <= 0.6, f'{d}')
check('N1 wearer client shows the reach (FarUsePacket ttl)', x and any(ctr.get(g, 0) > 0 for g in range(x[0]['gt'], x[0]['gt'] + 6)),
      f"client far ttl {[ctr.get(g) for g in range(x[0]['gt'], x[0]['gt'] + 6)] if x else '-'}")
x = sfar('lclick', '11007,-59,4')
check('N2 out of sight refused (stone wall)', x and x[0]['far'] == 'FAR' and not x[0]['sight'] and x[0]['canceled'] and not sfar('break', '11007,-59,4')
      and last_probe('11007,-59,4') == 'minecraft:dirt' and refused_note(x[0]['gt']), f"{x[:1]} probe {last_probe('11007,-59,4')} note {x and refused_note(x[0]['gt'])}")
p = paid('n3a', 'n3b')
check('N3 Air paid Stowed: 3 Far Uses at air.farUse 8 x Netherite 0.5', abs(p - 12) < 0.01 and spos['n3b']['state'] == 'STOWED',
      f"paid {p:.3f} (air {spos['n3a']['air']}->{spos['n3b']['air']}, debt {spos['n3a']['debt']}->{spos['n3b']['debt']})")
ok = all(last_probe(f'11007,-59,{z}') == 'minecraft:air' and drops.get(f'11007,-59,{z}', {}).get('far', 9) <= 0.6 for z in (8, 9, 10))
check('N3 three far mines, drops at the wearer', ok, f"{[(z, last_probe(f'11007,-59,{z}'), drops.get(f'11007,-59,{z}')) for z in (8, 9, 10)]}")
d = drops.get('11007,-59,14')
check('N3 XP at the wearer (diamond ore)', d and d['xp'] == 0 and d['far'] <= 0.6 and spos['n3xb']['xp'] > spos['n3xa']['xp'],
      f"drops {d}, wearer xp {spos['n3xa']['xp']} -> {spos['n3xb']['xp']}")
x = sfar('lclick', '11007,-60,18')
check('N4 Active far mine', spos['n4a']['state'] == 'ACTIVE' and x and x[0]['far'] == 'FAR' and not x[0]['canceled'] and last_probe('11007,-60,18') == 'minecraft:air',
      f"state {spos['n4a']['state']} {x[:1]} probe {last_probe('11007,-60,18')}")
check('N5 Setting off: no lift, the far dig stays vanilla-refused', spos['n5off']['lift'] == (0, 0) and spos['n5off']['mode'] == 'off'
      and not sfar('break', '11007,-59,22') and last_probe('11007,-59,22') == 'minecraft:dirt', f"lift {spos['n5off']['lift']} probe {last_probe('11007,-59,22')}")
check('N5 Rig out: no lift Stowed, lift Active', spos['n5rs']['lift'] == (0, 0) and spos['n5rs']['state'] == 'STOWED'
      and spos['n5ra']['lift'] == NLIFT and spos['n5ra']['state'] == 'ACTIVE', f"Stowed {spos['n5rs']['lift']}, Active {spos['n5ra']['lift']}")
check('N6 Extendo Grip: no lift (Create lifts the range itself)', spos['n6']['lift'] == (0, 0) and cact.get('n6', {}).get('brange', 0) > 4.5,
      f"lift {spos['n6']['lift']}, client block range {cact.get('n6', {}).get('brange')}")
x = sfar('attack', ids['z1'])
h = [e for e in hurt if e['id'] == ids['z1'] and e['by'] == 'Dev']
check('N7 far hit at 4.5 lands', x and x[0]['far'] == 'FAR' and not x[0]['canceled'] and abs(x[0]['dist'] - 4.5) < 0.05 and len(h) == 1, f"{x[:1]} hurt {h}")
x = sfar('attack', ids['z2'])
h = [e for e in hurt if e['id'] == ids['z2'] and e['by'] == 'Dev']
check('N8 far hit out of sight refused (cheat path)', x and not x[0]['sight'] and x[0]['canceled'] and not h and refused_note(x[0]['gt']), f"{x[:1]} hurt {h}")
check('N9 heavy hold pauses the lift', spos['n9h']['heavy'] and spos['n9h']['lift'] == (0, 0) and abs(spos['n9h']['mspd'] - 0.06) < 1e-4
      and spos['n9hoff']['lift'] == NLIFT, f"held {spos['n9h']['held']} heavy {spos['n9h']['heavy']} lift {spos['n9h']['lift']} mspd {spos['n9h']['mspd']}, after {spos['n9hoff']['lift']}")
check('N9 light hold keeps the lift', spos['n9l']['held'] >= 0 and not spos['n9l']['heavy'] and spos['n9l']['lift'] == NLIFT, f"{spos['n9l']}")
x = [f for f in far if f['kind'] == 'interact' and f['type'] in ('minecraft:iron_golem', 'minecraft:husk')]
check('N9 Grab claim probe is no Far Use', x and all(f['asking'] for f in x), f'{len(x)} probe interacts, asking {[f["asking"] for f in x]}')

print('== Copper (Reach 6, blocks and mobs)')
c1 = spos['c1a']
check('C lift 2.22 blocks, 3.72 mobs (Reach 6 from the body)', c1['lift'] == CLIFT and c1['fitted'], f"lift {c1['lift']}")
x = sfar('lclick', '11106,-59,0')
d = drops.get('11106,-59,0')
check('C1 far mine at 5.5', x and x[0]['far'] == 'FAR' and not x[0]['canceled'] and last_probe('11106,-59,0') == 'minecraft:air' and d and d['far'] <= 0.6,
      f"{x[:1]} probe {last_probe('11106,-59,0')} drops {d}")
p = paid('c1a', 'c1b')
check('C1 Air sip 0.5 paid', abs(p - 0.5) < 0.01, f"paid {p:.3f} (air {c1['air']}->{spos['c1b']['air']}, debt {c1['debt']}->{spos['c1b']['debt']})")
x = sfar('lclick', '11106,-59,4')
check('C2 behind glass panes refused', x and not x[0]['sight'] and x[0]['canceled'] and last_probe('11106,-59,4') == 'minecraft:dirt' and refused_note(x[0]['gt']),
      f"{x[:1]} probe {last_probe('11106,-59,4')}")
x = sfar('attack', ids['z4'])
h = [e for e in hurt if e['id'] == ids['z4'] and e['by'] == 'Dev']
check('C3 hit at 3.25 lands (past the 3.0 arm)', x and x[0]['far'] == 'FAR' and 3.0 < x[0]['dist'] <= 3.5 and not x[0]['canceled'] and len(h) == 1,
      f"{x[:1]} hurt {h}")
x = sfar('attack', ids['z5'])
h = [e for e in hurt if e['id'] == ids['z5'] and e['by'] == 'Dev']
check('C4 mob at 4.6 lands (mobs at the full Reach)', chit.get('c4') == ids['z5'] and x and x[0]['far'] == 'FAR' and not x[0]['canceled'] and len(h) == 1,
      f"crosshair entity {chit.get('c4')}, {x[:1]} hurt {h}")
h = [e for e in hurt if e['id'] == ids['z6'] and e['by'] == 'Dev']
x = sfar('attack', ids['z6'])
check('C6 mob at 7.5 (past Reach of the body): crosshair misses, cheat attack refused, no damage', chit.get('c6') == '-' and not h and all(e['far'] == 'OUT' and e['canceled'] for e in x),
      f"crosshair entity {chit.get('c6')}, hurt {h}, SFAR {sfar('attack', ids['z6'])}")
x = sfar('rclick', '11106,-59,30')
check('C5 place at distance', x and x[0]['far'] == 'FAR' and not x[0]['canceled'] and last_probe('11105,-59,30') == 'minecraft:dirt',
      f"{x[:1]} probe {last_probe('11105,-59,30')}")

print('== The floor ahead (Copper, real crosshair, held attack)')
for tag, z, st in (('n10a', 70, 'STOWED'), ('n10b', 74, 'ACTIVE')):
    pos = f'11105,-61,{z}'
    x = sfar('lclick', pos)
    br = sfar('break', pos)
    a0, a1 = spos.get(tag), spos.get(tag + '1')
    notes = [g for g, t in overlay if t == NO_SIGHT and a0 and a1 and a0['gt'] <= g <= a1['gt']]
    check(f'{tag.upper()} floor 5 ahead {st}: crosshair on it, FAR, in sight, mined, no refusal', a0 and a0['state'] == st and chitb.get(tag) == ('BLOCK', pos)
          and x and x[0]['far'] == 'FAR' and x[0]['sight'] and not x[0]['canceled'] and br and not br[0]['canceled'] and last_probe(pos) == 'minecraft:air' and not notes,
          f"state {a0 and a0['state']} crosshair {chitb.get(tag)} {x[:1]} break {br[:1]} probe {last_probe(pos)} refusals {notes}")

print('== Only effective actions are Far Uses (Copper)')
def ttl_in(a, b):
    return [ctr[g] for g in range(spos[a]['gt'], spos[b]['gt'] + 1) if ctr.get(g, 0) > 0]
if all(k in spos for k in ('n11a', 'n11a1', 'n11b', 'n11b1')):
    x = sfar('rclick', '11106,-59,78')
    p = paid('n11a', 'n11a1')
    check('N11 empty hand on a far stone: accepted, does nothing, no Air, no reach shown', chitb.get('n11a') == ('BLOCK', '11106,-59,78') and x and x[0]['far'] == 'FAR'
          and not x[0]['canceled'] and abs(p) < 0.01 and not ttl_in('n11a', 'n11a1'), f"crosshair {chitb.get('n11a')} {x[:1]} paid {p:.3f} far ttl {ttl_in('n11a', 'n11a1')}")
    p = paid('n11b', 'n11b1')
    lever = re.findall(r'PROBE 11105, -60, 82 Block\{minecraft:lever\}\[[^\]]*powered=(\w+)', s)
    check('N11 a far lever flips: Air sip 0.5 and reach shown', chitb.get('n11b') == ('BLOCK', '11105,-60,82') and abs(p - 0.5) < 0.01 and ttl_in('n11b', 'n11b1')
          and lever[-1:] == ['true'], f"crosshair {chitb.get('n11b')} paid {p:.3f} far ttl {ttl_in('n11b', 'n11b1')[:3]} lever powered {lever}")
else:
    check('N11 marks present', False, f"{[k for k in ('n11a', 'n11a1', 'n11b', 'n11b1') if k not in spos]}")

print('== Reach from the body, Dual Wield (Copper)')
x = sfar('lclick', '11106,-61,86')
b = sfar('break', '11106,-61,86')
check('N12 floor at the edge of Reach 6 from the body: FAR by the body distance (eye past 6), mined',
      chitb.get('n12', ('', ''))[1] == '11106,-61,86' and x and x[0]['far'] == 'FAR' and not x[0]['canceled'] and x[0]['dist'] > 6.0
      and x[0]['bdist'] is not None and x[0]['bdist'] <= 6.0 and b and last_probe('11106,-61,86') == 'minecraft:air',
      f"crosshair {chitb.get('n12')} {x[:1]} break {len(b)} probe {last_probe('11106,-61,86')}")

def dig(at):
    x, b = sfar('lclick', at), sfar('break', at)
    return (x[0]['gt'], b[0]['gt'] - x[0]['gt']) if x and b and not b[0]['canceled'] else (None, None)

g13, t13 = dig('11106,-59,90')
d13 = drops.get('11106,-59,90')
u13 = [e for e in dual if e['kind'] == 'mine' and g13 is not None and g13 <= e['gt'] <= g13 + 40]
check('N13 off-hand stone pickaxe alone mines stone fast, cobblestone drops, off-hand pickaxe wears, main-hand shovel does not, Air paid',
      t13 is not None and t13 <= 20 and d13 and 'minecraft:cobblestone' in d13['items'] and len(u13) == 1
      and u13[0]['off'] == 'minecraft:stone_pickaxe' and u13[0]['offdmg'] == 1 and u13[0]['maindmg'] == 0,
      f"ticks {t13} drops {d13} SDUAL {u13}")
g14a, t14a = dig('11106,-59,94')
g14b, t14b = dig('11106,-59,98')
u14a = [e for e in dual if g14a is not None and g14a <= e['gt'] <= g14a + 40]
u14b = [e for e in dual if e['kind'] == 'mine' and g14b is not None and g14b <= e['gt'] <= g14b + 40]
check('N14 two stone pickaxes dig faster than one (about twice), both wear', t14a is not None and t14b is not None and t14b * 1.5 <= t14a and not u14a
      and len(u14b) == 1 and u14b[0]['offdmg'] == 1 and u14b[0]['maindmg'] == 2,
      f"one {t14a} ticks, two {t14b} ticks, SDUAL one {u14a} two {u14b}")
h3 = [e for e in hurt if e['id'] == ids['z4'] and e['by'] == 'Dev']
h15 = [e for e in hurt if e['id'] == ids['z7'] and e['by'] == 'Dev']
u15 = [e for e in dual if e['kind'] == 'hit']
check('N15 off-hand iron sword adds its damage to an empty-hand hit, wears, Air paid',
      len(h15) == 1 and h3 and h15[0]['amount'] >= h3[0]['amount'] + 3.5 and len(u15) == 1 and u15[0]['off'] == 'minecraft:iron_sword' and u15[0]['offdmg'] == 1
      and abs(u15[0]['gt'] - h15[0]['gt']) <= 1,
      f"hurt {h15} (empty hand {h3[:1]}) SDUAL {u15}")

print('== Continuous digging (Copper, two shovels, the real crosshair, a held attack while the look sweeps)')
for tag, st, what, n, m in (('d1', 'ACTIVE', 'diamond, the far strip of a 1 wide trench floor', 10, 10), ('d2', 'STOWED', 'diamond, a 2 wide trench floor', 9, 9),
                            ('d3', 'ACTIVE', 'stone, the 1 wide trench floor', 25, 5), ('d4', 'ACTIVE', 'diamond, fast rasters over the open floor', 25, 25),
                            ('d5', 'ACTIVE', 'diamond, the wall under a ledge', 13, 13)):
    a, b = spos.get(tag + 'a'), spos.get(tag + 'b')
    if not (a and b):
        check(f'{tag.upper()} marks present', False, f"{[k for k in (tag + 'a', tag + 'b') if k not in spos]}")
        continue
    w = [f for f in far if a['gt'] <= f['gt'] <= b['gt'] and f['far'] == 'FAR']
    starts = [f for f in w if f['kind'] == 'lclick']
    breaks = [f for f in w if f['kind'] == 'break' and not f['canceled']]
    bad = [f for f in starts if not f['sight'] or f['canceled']] + [f for f in w if f['kind'] == 'break' and f['canceled']]
    notes = [g for g, t in overlay if t == NO_SIGHT and a['gt'] <= g <= b['gt'] + 5]
    check(f'{tag.upper()} {st} {what}: at least {n} far starts and {m} far breaks, none refused, no "Out of sight"',
          a['state'] == st and len(starts) >= n and len(breaks) >= m and not bad and not notes,
          f"state {a['state']} far starts {len(starts)} breaks {len(breaks)} refused {len(bad)} {[(f['gt'], f['kind'], f['at']) for f in bad][:4]} notes {notes[:4]}")
x = sfar('lclick', '11205,-57,44')
check('D6 the floor behind a stone wall (cheat path): refused, the block stays', x and x[0]['far'] == 'FAR' and not x[0]['sight'] and x[0]['canceled']
      and not [f for f in sfar('break', '11205,-57,44') if not f['canceled']] and last_probe('11205,-57,44') == 'minecraft:dirt' and refused_note(x[0]['gt']),
      f"{x[:1]} probe {last_probe('11205,-57,44')} note {x and refused_note(x[0]['gt'])}")

exc = [l for l in (c + s).splitlines() if 'Exception' in l and 'ApiStatus' not in l and 'authlib' not in l and 'neoforge-client.new.tmp.toml' not in l
       and 'Error loading class: net/irisshaders/' not in l and 'org.gradle.' not in l and 'MessageIOException' not in l
       and 'sun.nio.fs.UnixException' not in l]
check('E exceptions', not exc, f'{len(exc)} exception lines' + (f': {exc[0][:200]}' if exc else ''))
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
