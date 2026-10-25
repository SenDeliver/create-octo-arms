"""Dig suite: a carried wearer mines as fast as one standing on the floor (vanilla slows anyone not on the ground to a fifth).
usage: dig_metrics.py <log prefix>"""
import re, sys
pre = sys.argv[1]
client = open(pre + '-client.log', errors='replace').read()
server = open(pre + '-server.log', errors='replace').read()
digs = {m[0]: dict(ticks=int(m[1]), broken=m[2] == 'true', ground=m[3] == 'true', state=m[4])
        for m in re.findall(r'CDIG (\S+) gt0=\d+ gt1=\d+ ticks=(\d+) broken=(\w+) ground=(\w+) state=(\w+)', client)}
breaks = re.findall(r'SBREAK gt=\d+ Dev pos=.*? block=(\S+) ground=(\w+) state=(\w+)', server)
checks = []
def check(name, ok, detail):
    checks.append(ok)
    print(('PASS ' if ok else 'FAIL ') + name + ': ' + detail)
b0, b1, b2 = (digs.get(k) for k in ('b0', 'b1', 'b2'))
check('B0 setup: Stowed on the floor breaks stone in about 23 ticks', b0 is not None and b0['broken'] and b0['ground'] and b0['state'] == 'STOWED' and 15 <= b0['ticks'] <= 35, str(b0))
check('B1 carried at Standing Height (not on the ground): same time as B0', b0 is not None and b1 is not None and b1['broken'] and not b1['ground'] and b1['state'] == 'ACTIVE'
      and b1['ticks'] <= b0['ticks'] * 1.3 + 2, f'{b1} vs B0 {b0 and b0["ticks"]} ticks (vanilla airborne: about {b0 and b0["ticks"] * 5})')
check('B2 carried with the body on the floor: same time as B0', b0 is not None and b2 is not None and b2['broken'] and b2['state'] == 'ACTIVE'
      and b2['ticks'] <= b0['ticks'] * 1.3 + 2, f'{b2} vs B0 {b0 and b0["ticks"]} ticks')
check('S server: accepted all three breaks', len(breaks) == 3 and all(b[0] == 'minecraft:stone' for b in breaks), f'{len(breaks)} SBREAK lines {breaks}')
kicks = len(re.findall(r'moved too quickly|moved wrongly|moves faster than|kicked', server))
check('S server: no kick, no speed warning', kicks == 0, f'{kicks} hits')
print('PASS' if all(checks) else 'FAIL')
