"""Sneak suite: while the Rig is out with Shift held, client and server see a sneak but no crouch, and sneak + use works.
usage: sneak_metrics.py <log prefix>"""
import re, sys
pre = sys.argv[1]
client = open(pre + '-client.log', errors='replace').read()
server = open(pre + '-server.log', errors='replace').read()
out = re.compile(r'state=(?:ACTIVE|SETTING_DOWN|FALLING|CRAWL)')
c = [l for l in client.splitlines() if 'TRACE t=' in l and out.search(l) and 'sneak=true' in l]
s = [l for l in server.splitlines() if 'STRACE' in l and ' Dev ' in l and out.search(l) and 'sneak=true' in l]
crouch = [l for l in c if 'crouch=true' in l or 'pose=CROUCHING' in l]
eyes = sorted({m for l in c for m in re.findall(r'eye=([\d.]+)', l)})
scrouch = [l for l in s if 'pose=CROUCHING' in l]
ys = [float(m) for l in c for m in re.findall(r'pos=-?[\d.]+,(-?[\d.]+),', l)]
print(f'client ticks sneaking with the Rig out: {len(c)}, crouching: {len(crouch)}, eye heights: {eyes}')
print(f'server ticks sneaking with the Rig out: {len(s)}, crouching: {len(scrouch)}')
if ys: print(f'feet while sneaking: {max(ys):.3f} -> {min(ys):.3f} (Shift moves down)')
for l in re.findall(r'SNEAKTEST .*', server): print(l)
for l in re.findall(r'SCREEN \S+', client): print(l)
ok = c and s and not crouch and not scrouch and 'did not' not in server and 'not placed' not in server and ys and min(ys) < max(ys) - 1
print('PASS' if ok else 'FAIL')
