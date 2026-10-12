#!/usr/bin/env python3
"""Observer suite checks: Dev2 saw Dev's Rig at T1/T2/T3 and no render exception/crash on either client."""
import re, sys
p = sys.argv[1]
fails = 0
def read(f):
    try: return open(f, errors='replace').read()
    except FileNotFoundError: return ''
c1, c2, srv = read(p + '-client.log'), read(p + '-client2.log'), read(p + '-server.log')
bad = re.compile(r'InternalError|unsafe memory|ReportedException|Rendering entity|SIGSEGV|A fatal error|tentaworks.*Exception|Exception.*tentaworks|TentacleRenderer|TentacleLayer')
for name, log in (('client', c1), ('client2', c2), ('server', srv)):
    hits = [l for l in log.splitlines() if bad.search(l) and 'Found mod file' not in l]
    print(('FAIL' if hits else 'PASS'), name, 'render exceptions:', len(hits))
    for l in hits[:5]: print('   ', l[:200])
    fails += bool(hits)
TAGS = sys.argv[2].split(',') if len(sys.argv) > 2 else ['t1', 't2', 't3']
for tag in TAGS:
    seen = f'CPOS {tag} ' in c2 or f'CPOS {tag}seen' in c2
    rig = re.search(r'CSOUND %s rig=Dev plants=(\d+)' % tag, c2)
    ok = seen and rig is not None
    print(('PASS' if ok else 'FAIL'), tag, 'Dev2 reached mark:', seen, "Dev's Rig visual on Dev2:", rig is not None, 'plants:', rig.group(1) if rig else '-')
    fails += not ok
if 't2' in TAGS: print(('PASS' if 'script rejoin' in c2 and srv.count('Dev2 joined the game') >= 2 else 'FAIL'), 't2 rejoin happened')
print(('PASS' if 'CPOS end' in c1 else 'FAIL'), 'Dev reached end')
sys.exit(1 if fails else 0)
