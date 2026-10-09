"""Summarise a run_split log set: kick/warn counts, CPOS vs SPOS, TRACE vs STRACE drift. usage: analyze.py <log prefix> [player]"""
import re, sys, glob
pre = sys.argv[1]; who = sys.argv[2] if len(sys.argv) > 2 else 'Dev'
server = open(pre + '-server.log', errors='replace').read()
clients = {f: open(f, errors='replace').read() for f in sorted(glob.glob(pre + '-client*.log'))}
bad = ['moved wrongly', 'moved too quickly', 'Flying is not enabled', 'multiplayer.disconnect.flying', 'lost connection',
       'forcing Falling', 'non-finite', 'kicked', 'moves against Crawl']
sexc = [l for l in server.splitlines() if 'Exception' in l and 'ApiStatus' not in l]
print('server:', ', '.join(f'{b}={server.count(b)}' for b in bad), f'exceptions(excl. known)={len(sexc)}', *sexc[:3])
def secs(t): h, m, s_ = map(int, t.split(':')); return h * 3600 + m * 60 + s_
rcon = open(pre + '-rcon.txt', errors='replace').read()
tps = [secs(t) for t in re.findall(r'^(\d\d:\d\d:\d\d) > .*\b(?:tp|kill)\b', rcon, re.M)]
for c in clients.values():
    tps += [secs(t) for t in re.findall(r'\[(\d\d:\d\d:\d\d)\].*script: (?:cmd tp|rejoin)', c)]
quick = re.findall(r'\[(\d\d:\d\d:\d\d)\] \[Server thread/WARN\].*?: (\w+) moved too quickly', server)
unexplained = [(t, n) for t, n in quick if not any(0 <= secs(t) - pt <= 2 for pt in tps)]
print(f'moved too quickly: {len(quick)} total, {len(quick) - len(unexplained)} within 2 s after a tp/kill/rejoin (vanilla teleport artefact), unexplained={len(unexplained)}', unexplained[:5])
for f, c in clients.items():
    exc = [l for l in c.splitlines() if 'Exception' in l and 'neoforge-client.new.tmp.toml' not in l and 'ApiStatus' not in l]
    print(f, 'exceptions(excl. known)=', len(exc), *exc[:3], sep=' ')
num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
cpos = {m[0]: (int(m[1]), tuple(map(float, m[2:5]))) for m in re.findall(r'CPOS (\S+) gt=(\d+) pos=' + num, clients.get(pre + '-client.log', ''))}
spos = {m[0]: tuple(map(float, m[1:4])) for m in re.findall(r'SPOS (\S+) gt=\d+ ' + who + ' pos=' + num, server)}
for tag, (gt, c) in cpos.items():
    s = spos.get(tag)
    d = max(abs(a - b) for a, b in zip(c, s)) if s else float('nan')
    print(f'mark {tag:8s} gt={gt} client={c} server={s} drift={d:.3f}')
ctext = clients.get(pre + '-client.log', '')
tr = {int(m[0]): tuple(map(float, m[1:4])) for m in re.findall(r'TRACE t=\d+ gt=(\d+) pos=' + num, ctext)}
carried = {int(m) for m in re.findall(r'TRACE t=\d+ gt=(\d+) .*?state=(?:ACTIVE|SETTING_DOWN|FALLING|CRAWL)', ctext)}
st = {int(m[0]): tuple(map(float, m[1:4])) for m in re.findall(r'STRACE gt=(\d+) ' + who + ' pos=' + num, server)}
def dist(a, b): return sum((x - y) ** 2 for x, y in zip(a, b)) ** 0.5
def steady(g):
    for k in range(g - 4, g + 5):
        for d in (tr, st):
            if k in d and k - 1 in d and dist(d[k], d[k - 1]) > 1.5: return False
    return True
ticks = [g for g in st if g in tr and g - 1 in tr]
keep = [g for g in ticks if steady(g) and g in carried]
same = [dist(tr[g], st[g]) for g in keep]
lag = [min(dist(tr[g], st[g]), dist(tr[g - 1], st[g])) for g in keep]
if same:
    print(f'TRACE/STRACE ticks={len(ticks)} compared={len(keep)} (Rig not Stowed, teleport ticks +-4 skipped) max drift same gt={max(same):.3f} '
          f'allowing 1 tick lag={max(lag):.3f} ticks>0.5 (lag-allowed)={sum(1 for x in lag if x > 0.5)}')
