"""Sneak hand-over checks (PR #15 review): Stowed sneak untouched, crouch returns when the Rig folds with Shift held, release is seen,
Rig Settings open from the hand with the Rig out. usage: sneak2_metrics.py <log prefix>"""
import re, sys
pre = sys.argv[1]
client = open(pre + '-client.log', errors='replace').read()
server = open(pre + '-server.log', errors='replace').read()
SCREEN = 'dev.tentaworks.client.RigSettingsScreen'
ct = {int(m[0]): dict(state=m[1], sneak=m[2] == 'true', crouch=m[3] == 'true', pose=m[4], eye=float(m[5]))
      for m in re.findall(r'TRACE t=\d+ gt=(\d+) .*?state=(\w+) .*? sneak=(\w+) crouch=(\w+) pose=(\w+) eye=([\d.]+)', client)}
st = {int(m[0]): dict(state=m[1], sneak=m[2] == 'true', pose=m[3])
      for m in re.findall(r'STRACE gt=(\d+) Dev .*?state=(\w+) .*? sneak=(\w+) pose=(\w+)', server)}
mk = {m[0]: int(m[1]) for m in re.findall(r'CPOS (\S+) gt=(\d+)', client)}
fails = []

def check(ok, name, text):
    if not ok: fails.append(name)
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")

def span(d, a, b):
    return [(g, d[g]) for g in sorted(d) if a <= g <= b]

need = ('a1', 'a2', 't1', 't2', 't3', 'r1', 'r2', 'h1')
if not all(k in mk for k in need) or not ct or not st:
    check(False, 'marks', f'missing marks or traces: marks {sorted(mk)}, client ticks {len(ct)}, server ticks {len(st)}')
else:
    # A: Stowed, Shift held: vanilla crouch on both sides
    ca = span(ct, mk['a1'], mk['a1'] + 5); sa = span(st, mk['a1'], mk['a1'] + 5)
    check(ca and all(x['state'] == 'STOWED' and x['crouch'] and x['pose'] == 'CROUCHING' and x['sneak'] for _, x in ca)
          and sa and all(x['state'] == 'STOWED' and x['sneak'] and x['pose'] == 'CROUCHING' for _, x in sa),
          'A Stowed sneak untouched: crouch on both sides', f'client {[(x["state"], x["crouch"], x["pose"]) for _, x in ca][:2]}, server {[(x["state"], x["sneak"], x["pose"]) for _, x in sa][:2]}')
    check('SNEAK2 A stowed wrench picked up' in server, 'A Stowed sneak + use picks up the gearbox', re.findall(r'SNEAK2 A .*', server)[:1])
    # T: Rig out + Shift at t1, then stow with Shift still held
    t1 = ct.get(mk['t1']); s1 = st.get(mk['t1'])
    check(t1 and t1['state'] == 'ACTIVE' and t1['sneak'] and not t1['crouch'] and t1['pose'] == 'STANDING' and s1 and s1['state'] == 'ACTIVE' and s1['sneak'] and s1['pose'] == 'STANDING',
          'T1 Rig out + Shift: sneak without crouch on both sides', f'client {t1}, server {s1}')
    cw = span(ct, mk['t1'], mk['t2']); sw = span(st, mk['t1'], mk['t2'])
    stow_c = next((g for g, x in cw if x['state'] == 'STOWED'), None)
    stow_s = next((g for g, x in sw if x['state'] == 'STOWED'), None)
    check(stow_c is not None and stow_s is not None, 'T stows while Shift is held', f'client stow gt {stow_c}, server stow gt {stow_s}, t2 gt {mk["t2"]}')
    if stow_c and stow_s:
        after_c = [x for g, x in cw if g >= stow_c + 3]
        after_s = [x for g, x in sw if g >= stow_s + 3]
        check(after_c and all(x['crouch'] and x['pose'] == 'CROUCHING' and x['sneak'] for x in after_c),
              'T2 crouch returns on the client right after the stow', f'{len(after_c)} ticks, crouching on {sum(x["crouch"] for x in after_c)}, poses {sorted({x["pose"] for x in after_c})}')
        check(after_s and all(x['sneak'] and x['pose'] == 'CROUCHING' for x in after_s),
              'T2 crouch returns on the server right after the stow', f'{len(after_s)} ticks, sneak on {sum(x["sneak"] for x in after_s)}, poses {sorted({x["pose"] for x in after_s})}')
        lost = [g for g, x in sw if not x['sneak']]
        check(len(lost) <= 2, 'T server keeps the sneak across the stow (<= 2 ticks lost)', f'server sneak=false on {len(lost)} ticks {lost[:6]}')
    t3 = span(st, mk['t3'] - 2, mk['t3'])
    check(t3 and all(not x['sneak'] and x['pose'] == 'STANDING' for _, x in t3), 'T3 Shift released Stowed: server standing, no sneak', f'{[(x["sneak"], x["pose"]) for _, x in t3]}')
    # R: release with the Rig out
    r1 = st.get(mk['r1']); cr1 = ct.get(mk['r1'])
    check(r1 and r1['state'] == 'ACTIVE' and r1['sneak'] and cr1 and cr1['sneak'], 'R1 Rig out + Shift: both sides sneak', f'server {r1}, client {cr1}')
    rs = span(st, mk['r1'] + 5, mk['r2']); rc = span(ct, mk['r1'] + 5, mk['r2'])
    check(rs and all(not x['sneak'] and x['state'] == 'ACTIVE' for _, x in rs) and rc and all(not x['sneak'] for _, x in rc),
          'R2 Shift released with the Rig out: no sneak sticks (server and client, within 5 ticks)',
          f'server sneak on {sum(x["sneak"] for _, x in rs)}/{len(rs)} ticks, client on {sum(x["sneak"] for _, x in rc)}/{len(rc)}')
    # H: Rig in hand, Rig out, sneak + use in the air
    screens = re.findall(r'SCREEN (\S+)', client)
    h = ct.get(mk['h1'])
    check(screens and screens[0] == SCREEN and h and h['state'] == 'ACTIVE', 'H sneak + use in the air with the Rig in hand opens Rig Settings while the Rig is out', f'screens {screens}, state at h1 {h and h["state"]}')
print('PASS' if not fails else 'FAIL ' + ', '.join(fails))
