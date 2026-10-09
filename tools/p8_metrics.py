"""Phase-8 checks (code-review fixes): forged zero-motion climb drains climb Air, ghost-block hang falls, through-wall Grips,
Grips next to unloaded chunks, packet bursts (one handled per tick, relay on change, quiet non-finite log), real-move speed
check, worn-out Setting Down rise, lenient Rig Settings codec, goggle sync at 16 RPM, input cost at Reach 4/10(/16),
empty-Rig long fall kicked (fall guard only for a Rig that can catch).
usage: p8_metrics.py tools/logs/<tag>-p8 [tools/logs/<tag>-p8r16]"""
import re
import sys

pre = sys.argv[1]
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
r = open(f'{pre}-rcon.txt', errors='replace').read()
fails = []


def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok:
        fails.append(name)


num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
tr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num + r' .*?held=(\d) state=(\w+) .*?ground=(\w+) hp=(\S+) air=(\d+) ff=(\w+)', c):
    tr[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), held=int(m[5]), state=m[6], ground=m[7] == 'true', air=int(m[9]))
st = {}
for m in re.finditer(r'STRACE gt=(\d+) Dev pos=' + num + r' v=\S+ state=(\w+) grips=(\d) cf=(\S+) .*?air=(\d+) mv=' + num, s):
    st[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), state=m[5], air=int(m[8]), mv=tuple(map(float, m.group(9, 10, 11))))
mk = {m[1]: int(m[2]) for m in re.finditer(r'CPOS (\S+) gt=(\d+)', c)}


def decrements(a, b):
    g = [x for x in sorted(st) if a <= x <= b]
    ticks = [x for i, x in enumerate(g[1:], 1) if st[x]['air'] < st[g[i - 1]]['air']]
    return ticks, (st[g[0]]['air'] - st[g[-1]]['air']) if g else 0


# A forged zero-motion climb
dt, used = decrements(mk['aclimb0'] + 5, mk['aclimb1'])
gaps = [b - a for a, b in zip(dt, dt[1:])]
mv0 = [x for x in st if mk['aclimb0'] + 5 <= x <= mk['aclimb1'] and abs(st[x]['mv'][1]) < 1e-6]
win = [x for x in st if mk['aclimb0'] + 5 <= x <= mk['aclimb1']]
rise = tr[max(x for x in tr if x <= mk['aclimb1'])]['p'][1] - tr[min(x for x in tr if x >= mk['aclimb0'])]['p'][1]
check('A forged zero climb', gaps and sum(gaps) / len(gaps) <= 12 and len(mv0) >= 0.8 * len(win) and rise > 5,
      f"client rose {rise:.1f} blocks while the server saw claimed motion 0 in {len(mv0)}/{len(win)} ticks; Air -{used}, ticks per Air {sum(gaps) / max(1, len(gaps)):.1f} (climb 10, idle 80)")
_, idle = decrements(mk['aidle0'], mk['aidle1'])
check('A idle control', idle <= 2, f"Air -{idle} in {mk['aidle1'] - mk['aidle0']} idle ticks (80 per Air)")

# P input cost
inp = {m[1]: (int(m[2]), float(m[3]), float(m[4]), m[5]) for m in re.finditer(r'CINPUT (\S+) ticks=(\d+) avg_us=(\S+) max_us=(\S+) reach=(\S+)', c)}
if len(sys.argv) > 2:
    c16 = open(f'{sys.argv[2]}-client.log', errors='replace').read()
    inp.update({m[1]: (int(m[2]), float(m[3]), float(m[4]), m[5]) for m in re.finditer(r'CINPUT (\S+) ticks=(\d+) avg_us=(\S+) max_us=(\S+) reach=(\S+)', c16)})
for k in ('r4', 'r10', 'r16'):
    if k in inp:
        n, avg, mx, reach = inp[k]
        check(f'P input cost {k}', n > 50 and avg < 4000, f"Reach {reach}: {n} Active ticks, avg {avg:.0f} us, max {mx:.0f} us per input tick")

# G ghost platform
g0 = mk['gghost']
after = [x for x in sorted(tr) if g0 <= x <= mk['gend']]
hy = tr[g0]['p'][1]
down = next((x for x in after if tr[x]['p'][1] < hy - 1.0), None)
end_c = tr[after[-1]]['state']
end_s = st[max(x for x in st if x <= mk['gend'])]['state']
seg = s[s.find('GHOST'):]
check('G ghost platform', down is not None and down - g0 <= 70 and end_c == end_s and 'forcing Falling' in seg[:seg.find('NETSTATS t0') if 'NETSTATS t0' in seg else None],
      f"hang feet y {hy:.2f}; client started to fall {None if down is None else down - g0} ticks after the ghost (float kick at 80); end client {end_c} / server {end_s} at y {tr[after[-1]]['p'][1]:.2f}")


def between(a, b):
    i = s.find(a)
    j = s.find(b, i + 1)
    return s[i:j] if i >= 0 and j >= 0 else ''


ns = {m[1]: (int(m[2]), int(m[3]), int(m[4]), float(m[5]), float(m[6])) for m in re.finditer(r'NETSTATS (\S+) processed=(\d+) relays=(\d+) ticks=(\d+) avg_tick_us=(\S+) max_tick_us=(\S+)', s)}
t = between('NETSTATS t0', 'NETSTATS t1')
check('T through-wall Grips', t.count('forcing Falling') == 1, f"forged Grips on the far face of a wall: {t.count('forcing Falling')} forcing (want 1; the real-Grip control after it adds none)")
e = between('NETSTATS e0', 'NETSTATS e1')
pr = between('NETSTATS e1', 'PROBE')
check('E unloaded neighbour', 'fake edge' in e and 'CHUNKLOAD 1253 ' not in e and 'CHUNKLOAD 1253 ' in pr,
      f"Grips on x=20047 facing east into unloaded chunk 1253: loads during the forged packets {e.count('CHUNKLOAD 1253 ')}; control (reading that block) loads it: {'CHUNKLOAD 1253 ' in pr}")
b0, b1, b2 = ns['b0'], ns['b1'], ns['b2']
check('B burst handled once per tick', b1[0] - b0[0] <= b1[2] + 1 and b1[1] - b0[1] <= 5,
      f"40 ticks x 50 forged packets: handled {b1[0] - b0[0]} in {b1[2]} ticks (the harness client also sends while Active), relayed {b1[1] - b0[1]}; server tick avg {b1[3]:.0f} us, max {b1[4]:.0f} us")
nanseg = between('NETSTATS b1', 'NETSTATS b2')
check('B non-finite log quiet', nanseg.count('non-finite') == 1, f"2000 non-finite packets: {nanseg.count('non-finite')} log line(s)")
s6 = between('fake slide6 ', 'fake slide20 ')
s20 = between('fake slide20 ', 'fake downrise ')
check('S speed check', 'faster than its top Speed' not in s6 and 'faster than its top Speed' in s20,
      f"server-side slide 6 b/s: {s6.count('faster than')} forcing; 20 b/s: {s20.count('faster than')} forcing (top Speed 8)")
w = s[s.find('fake downrise '):]
check('W Setting Down rise', 'moves against SETTING_DOWN' in w[:w.find('SRIG codec') if 'SRIG codec' in w else None], "forged Setting Down packets with a rising motion: stowed")
cod = re.search(r'SRIG codec Dev slot=\w+ item=tentaworks:copper_tentacle_rig settings=(.*)', s)
check('C lenient settings codec', cod is not None and '2.5' in cod[1] and 'DOUBLE_JUMP' in cod[1], f"give with activation:\"bogus\" -> {cod[1] if cod else 'no item'}")

# L goggles at 16 RPM
gog = {}
for m in re.finditer(r'CGOGGLE (l\d) +(\d+) / 900', c):
    gog[m[1]] = int(m[2])
srv = [int(x) for x in re.findall(r'data get block 4600 -60 0 Air => .*?has the following block data: (\d+)', r)]
ok = len(gog) == 3 and len(srv) == 3 and gog['l1'] < gog['l2'] < gog['l3'] and all(abs(gog[f'l{i + 1}'] - srv[i]) <= 3 for i in range(3))
check('L goggle sync 16 RPM', ok, f"client {[gog.get(k) for k in ('l1', 'l2', 'l3')]} vs server {srv}")

# U untested items
u1 = c[c.find('bound key.tentaworks.toggle'):c.find('CPOS u1_key')]
cp = re.search(r'CPOS u1_key gt=\d+ pos=\S+ state=(\w+)', c)
check('U1 key-only toggle on a worn-out Rig', cp is not None and cp[1] == 'STOWED' and 'TITLE' in u1, f"state after the key {cp[1] if cp else None}; warning {re.findall(r'subtitle=\"(.*?)\"', u1)}")
inv = {m[1]: m[2] for m in re.finditer(r'SINV (m[01]) Dev slot=38 (.*)', s)}
d = {k: re.search(r'ench=(\S+) .*?damage=(\d+)/\d+ air=(\d+) settings=(.*)', v) for k, v in inv.items()}
ok = len(d) == 2 and all(d.values()) and int(d['m1'][2]) < int(d['m0'][2]) and d['m1'][1] == d['m0'][1] and d['m1'][3] == d['m0'][3] == '500' and d['m1'][4] == d['m0'][4]
check('U2 Mending keeps Air/enchants/settings', ok, f"before {inv.get('m0')}; after {inv.get('m1')}")
nix = re.search(r'data get block 4611 -59 0 => (.*)', r)
check('U4 nixie progress bar', nix is not None and 'Text' in nix[1], f"{nix[1][:220] if nix else None}")
src = re.search(r'data get block 4621 -60 0 Source => (.*)', r)
check('U5 Display Link source via GUI', src is not None and 'tentaworks:rig_air' in src[1], f"{src[1][:160] if src else None}")
cp = re.search(r'CPOS u6_water gt=\d+ pos=(\S+) state=(\w+)', c)
check('U6 underwater Active', cp is not None and cp[2] == 'ACTIVE', f"{cp.groups() if cp else None} (shots p8_underwater*.png)")

# K empty Rig long fall
k = s[s.find('tp Dev 4700.5 300') if 'tp Dev 4700.5 300' in s else s.rfind('Teleported Dev'):]
kicked = re.search(r'Dev lost connection: (Flying is not enabled|.*flying)', s)
check('K empty Rig not guarded', kicked is not None, f"empty Rig falling from y 300: {kicked[0] if kicked else 'no kick'}")
early = s[:s.find('Dev was kicked')] if 'Dev was kicked' in s else s
check('No kick before K', 'Flying is not enabled' not in early and 'lost connection' not in early and s.count('Dev was kicked') <= 1, "no kick/flying disconnect before the deliberate one")
print('RESULT', 'PASS' if not fails else 'FAIL ' + ','.join(fails))
