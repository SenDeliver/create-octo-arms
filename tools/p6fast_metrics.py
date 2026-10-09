"""Phase-6 fast-config check (speed.max 20, Swift II, Speed 20): the gait's Grips stay valid for the server at 26 b/s.
usage: p6fast_metrics.py tools/logs/<tag>-p6fast"""
import math
import re
import sys

pre = sys.argv[1]
c = open(f'{pre}-client.log', errors='replace').read()
s = open(f'{pre}-server.log', errors='replace').read()
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', c)}
tr = {int(m[1]): tuple(map(float, m.group(2, 3, 4))) for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num, c)}
st = {int(m[1]): (m[2], int(m[3])) for m in re.finditer(r'STRACE gt=(\d+) Dev pos=\S+ v=\S+ state=(\w+) grips=(\d)', s)}
fails = []


def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok:
        fails.append(name)


reach = re.search(r'CREACH fast reach=(\S+) top=(\S+)', c)
w = sorted(g for g in tr if mk['fasta'] <= g <= mk['fastb'])
dist = math.dist(tr[w[0]], tr[w[-1]])
speed = dist / ((w[-1] - w[0]) / 20)
forcing = len(re.findall(r'forcing Falling', s))
for name, a, b in (('flat', 'fasta', 'fastb'), ('climb', 'climba', 'climbb')):
    sw = [v for g, v in st.items() if mk[a] <= g <= mk[b]]
    check(f'F {name} at top speed', sw and all(x[0] == 'ACTIVE' for x in sw) and min(x[1] for x in sw) >= 2,
          f'server states {sorted(set(x[0] for x in sw))}, min valid Grips {min(x[1] for x in sw) if sw else "-"} (>= 2)')
check('F speed', speed > 20, f'{speed:.2f} b/s on the flat (top {reach[2] if reach else "?"} b/s)')
check('F no forced Falling', forcing == 0, f'{forcing} "forcing Falling" lines')
print('RESULT', 'PASS' if not fails else 'FAIL ' + ', '.join(fails))
