"""Phase-7 checks: goggles on Rig Blocks (Air rising under a shaft, Reach / Speed / Air use), Display Link "Rig Air"
(board follows the Rig's Air, percent mode, link GUI), Ponder scene for every component, Ponder hint, no errors.
usage: p7_metrics.py tools/logs/<tag>-p7"""
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


gog = {}
for m in re.finditer(r'CGOGGLE (\S+) (.*)', c):
    gog.setdefault(m[1], []).append(m[2].strip())


def air(tag):
    for line in gog.get(tag, []):
        m = re.match(r'(\d+) / (\d+) \((\d+)%\)', line)
        if m:
            return int(m[1]), int(m[2])
    return None


def has(tag, text):
    return any(text in line for line in gog.get(tag, []))


a1, a2 = air('g1'), air('g2')
check('G1 goggles show Air rising under the shaft', a1 and a2 and a2[0] > a1[0] and a1[1] == 900 and has('g1', 'Tentacle Rig'),
      f'g1 {a1}, g2 {a2} (60 ticks apart at 20 Air/s)')
check('G2 fill rate and enchant-derived stats (Copper, Far Reach II, Swift I, 128 RPM)',
      has('g2', 'Filling: +20.0 Air/s') and has('g2', 'Reach: 6.0 blocks') and has('g2', 'Top Speed: 9.2 b/s') and has('g2', 'Kinetic Stats'),
      f'{gog.get("g2", [])[3:6]}')
check('G3 Netherite Rig Block without a shaft', has('g3', 'Not filling') and has('g3', 'Reach: 7.0 blocks') and has('g3', 'Air use: 50%') and air('g3') == (300, 900),
      f'{gog.get("g3", [])[2:7]}')

reads = re.findall(r'data get block 3510 -60 0 Air => .*?data: (\d+)\s.*?data get block 3512 -59 0 => .*?Sections: \[\{Text: \'"([^"]*)"\'.*?\{Text: \'"([^"]*)"\'', r, re.S)
vals = [(int(a), lab, txt) for a, lab, txt in reads]
nums = [(a, int(t)) for a, lab, t in vals if t.isdigit()]
pct = [(a, t) for a, lab, t in vals if t.endswith('%')]
check('D1 display board shows the Rig Air and follows it', len(nums) >= 2 and nums[1][1] > nums[0][1] and all(0 <= a - n <= 40 for a, n in nums) and vals[0][1].strip() == 'Air',
      f'(rig Air, board) {nums}, label {vals[0][1] if vals else None!r}')
check('D2 percent mode', bool(pct) and abs(int(pct[0][1][:-1]) - 100 * pct[0][0] / 900) <= 5, f'{pct}')
src = re.findall(r'Source: \{[^}]*Id: "([^"]+)"', r)
check('D3 link keeps the tentaworks:rig_air source (validated against the Rig Block)', src and all(x == 'tentaworks:rig_air' for x in src), f'{src}')
scr = re.search(r'SCREEN (\S+)', c)
check('D4 Display Link screen opens on the link', scr and scr[1].endswith('DisplayLinkScreen'), scr[1] if scr else 'none')

opened = dict(re.findall(r'script ponder (\S+) scene=(\S+)', c))
want = ['tentaworks:copper_tentacle_rig', 'tentaworks:netherite_tentacle_rig', 'tentaworks:tentacle']
check('P1 Ponder scene for every component', all(opened.get(i) == 'tentaworks:tentacle_rig' for i in want), f'{opened}')
seeks = re.findall(r'script ponder seek (\d+) scene=\S+ total=(\d+)', c)
check('P2 scene plays through every seek', len(seeks) >= 10 and int(seeks[0][1]) > 1000, f'{len(seeks)} seeks, total {seeks[0][1] if seeks else "-"} ticks')
tip = re.findall(r'CTIP hint (.*)', c)
check('H Ponder hint on a hovered Rig', any('to Ponder' in t for t in tip) and any(t.startswith('Air:') for t in tip), f'{[t.strip() for t in tip[:6]]}')
noise = ('ScheduledForRemoval', 'No key layers')
errs = [l for l in (c + s).splitlines() if re.search(r'Exception|/ERROR\]', l) and not any(n in l for n in noise)]
check('E no client/server errors', not errs, f'{len(errs)} {errs[:2]}')
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
