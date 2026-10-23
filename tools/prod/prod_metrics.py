"""Prod-pack checks: release jar on a NeoForge 21.1.250 installer server + client with the live pack.
usage: prod_metrics.py tools/logs/<tag>-prod"""
import re
import sys

pre = sys.argv[1]
c = open(f'{pre}-client.log', errors='replace').read()
cd = open(f'{pre}-client-debug.log', errors='replace').read() if __import__('os').path.exists(f'{pre}-client-debug.log') else c
s = open(f'{pre}-server.log', errors='replace').read()
sd = open(f'{pre}-server-debug.log', errors='replace').read()
r = open(f'{pre}-rcon.txt', errors='replace').read()
fails = []


def check(name, ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {name}: {text}")
    if not ok:
        fails.append(name)


num = r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
tr = {}
for m in re.finditer(r'TRACE t=\d+ gt=(\d+) pos=' + num + r' .*?held=(\d) state=(\w+) .*?ground=(\w+) hp=(\S+) air=(\d+) ff=(\w+)', c):
    tr[int(m[1])] = dict(p=tuple(map(float, m.group(2, 3, 4))), held=int(m[5]), state=m[6], ground=m[7] == 'true', hp=float(m[8]), ff=m[10] == 'true')
mk = {m[1]: (int(m[2]), m[3]) for m in re.finditer(r'CPOS (\S+) gt=(\d+) pos=\S+ state=(\w+)', c)}


def at(name):
    return mk[name][1] if name in mk else None


def rcon(cmd_prefix):
    return [m[1] for m in re.finditer(re.escape(cmd_prefix) + r'.*? => (.*)', r)]


known = r'Tracks:|copycat_catwalk|No key layers|ApiStatus|thruster_loop_OLD|trackwork:models|Invalid path in pack|Yggdrasil|sessionserver'
errs = [l for l in s.splitlines() + c.splitlines() if re.search(r'/(ERROR|FATAL)\]', l) and not re.search(known, l)]
tw = [l for l in errs if re.search(r'tentaworks|dev\.tentaworks', l, re.I)]
check('Boot', 'Done (' in s and 'Tentaworks 1.0.0' in sd + s, f"server Done, Tentaworks 1.0.0 listed; ERROR lines (known noise excluded): {len(errs)}, from Tentaworks: {len(tw)}")
for l in errs[:8]:
    print('   ', l[:200])
mix_s = sorted(set(re.findall(r'Mixing (\w+) from tentaworks\.mixins\.json', sd)))
mix_c = sorted(set(re.findall(r'Mixing (\w+) from tentaworks\.mixins\.json', cd)))
check('Mixins (server)', {'BacktankBlockEntityMixin', 'BacktankBlockEntityAccessor', 'ServerGamePacketListenerImplAccessor'} <= set(mix_s), f"{mix_s}")
check('Mixins (client)', {'BacktankRendererMixin', 'EntityRendererMixin', 'KineticBlockEntityGoggleMixin'} <= set(mix_c), f"{mix_c}")
check('Legworks loaded', re.search(r'Legworks 0\.1\.1', sd + s) is not None, 'Legworks 0.1.1 in the mod list')
check('Client joined', 'Dev joined the game' in s, '')
kick = re.findall(r'Dev lost connection: (.*)', s)
check('No kicks', all('Disconnected' in k for k in kick), f"disconnects: {kick}")
cf = rcon('> data get entity Dev abilities.mayfly')
check('M activate/climb/Set Down', at('m_active') == 'ACTIVE' and at('m_down') == 'STOWED' and len(cf) > 1 and all('0b' in x for x in cf[:2]),
      f"states {at('m_active')} -> {at('m_down')}; server mayfly {cf[:2]}")
climb = tr.get(mk['m_climb'][0], {}).get('p', (0, 0, 0))[1] if 'm_climb' in mk else None
check('M climb height', climb is not None and climb > -52, f"feet y after 2 s climb {climb}")
check('H hang + Set Down', at('h_hang') == 'ACTIVE' and at('h_down') == 'STOWED' and len(cf) > 3 and all('0b' in x for x in cf[2:4]),
      f"hang {at('h_hang')} (feet {tr.get(mk['h_hang'][0], {}).get('p') if 'h_hang' in mk else None}), down {at('h_down')}, server mayfly {cf[2:4]}")
ff = rcon('> data get entity Dev FallFlying')
eff = [tr[g]['ff'] for g in tr if 'e_catch' in mk and 'e_down' in mk and mk['e_catch'][0] - 20 <= g <= mk['e_down'][0] and tr[g]['state'] in ('ACTIVE', 'SETTING_DOWN')]
check('E elytra + Rig', at('e_catch') == 'ACTIVE' and at('e_down') == 'STOWED' and eff and not any(eff) and all('0b' in x for x in ff),
      f"catch {at('e_catch')}, Active ticks gliding {sum(eff)}/{len(eff)}, server FallFlying {ff}, down {at('e_down')}")
check('B backpack + Rig', at('b_climb') == 'ACTIVE' and at('b_down') == 'STOWED', f"climb {at('b_climb')}, down {at('b_down')}")
chest = rcon('> execute if block 3004 -60 6 minecraft:chest')
check('C Carry On + Rig', at('c_active') in ('ACTIVE', 'SETTING_DOWN') and at('c_down') == 'STOWED' and chest and 'failed' in chest[0].lower(),
      f"chest picked up: {chest}, Active while carrying: {at('c_active')}, down {at('c_down')}")
rb0 = rcon('> data get block 3008 -60 6')
rb1 = rcon('> data get block 3008 -60 -6')
keep = rb1 and 'Air: 500' in rb1[0] and 'far_reach' in rb1[0]
check('C Carry On Rig Block', keep, f"moved Rig Block keeps Air + enchantments: {rb1[0][:200] if rb1 else 'not placed'}")
gog = re.findall(r'CGOGGLE g1 +(.*)', c)
bt = re.findall(r'BACKTANK (.*)', c)
check('G goggles', any('/ 900' in g for g in gog), f"{[g.strip() for g in gog[:4]]}; BACKTANK lines {len(bt)}")
jei = re.findall(r'JEI (\S+) OUTPUT .* -> (\d+) recipes', c)
check('J JEI', len(jei) == 2 and all(int(n) > 0 for _, n in jei), f"{jei}")
pon = re.findall(r'script ponder (\S+) scene=(\S+)', c)
check('J Ponder', pon and pon[0][1] == 'tentaworks:tentacle_rig', f"{pon}")
check('I creative tab', 'creative_tab' in c and not re.search(r'already exists in the tab', c), 'creative inventory opened, Tentaworks tab selected')
# Sable
gaits = [tuple(map(float, g.split('@')[0].split(','))) for m in re.finditer(r'GAIT gt=\d+ state=\w+ pos=(\S+) .*? slots=(\S+)', c) for g in m[2].split(';') if g != '-']
pos = [tuple(map(float, m[1].split(','))) for m in re.finditer(r'GAIT gt=\d+ state=\w+ pos=(\S+) ', c)]
far = [g for g in gaits if abs(g[0]) > 4000 or abs(g[2]) > 4000 or g[1] < -64 or g[1] > 320]
check('S no plot-coordinate Grips', gaits and not far, f"{len(gaits)} Grip samples in GAIT lines, {len(far)} outside the test area")
check('S still Ship next to / under the wearer', at('s_hull') in ('ACTIVE',) and at('s_deck') is not None,
      f"walk into the hull: {at('s_hull')} at {tr.get(mk['s_hull'][0], {}).get('p') if 's_hull' in mk else None}; activate on the deck: {at('s_deck')} at {tr.get(mk['s_deck'][0], {}).get('p') if 's_deck' in mk else None}")
check('S moving Ship', at('s_moved') is not None, f"after the Ship moved: {at('s_moved')} at {tr.get(mk['s_moved'][0], {}).get('p') if 's_moved' in mk else None}")
if 's_wall' in mk:
    g1 = mk['s_wall'][0]
    xs = [tr[g]['p'][0] for g in tr if g1 - 60 <= g <= g1 and tr[g]['state'] != 'STOWED']
    warns = re.findall(r'Tentacle Rig of Dev (.*)', s)
    check('S Ship wall at body height', xs and max(xs) <= 3214.75 and at('s_wall') == 'ACTIVE' and not warns,
          f"walking east into a Ship wall (west face x=3215): x max {max(xs) if xs else None:.3f}, state {at('s_wall')}, server Rig warnings {warns}")
exc = [l for l in c.splitlines() if re.search(r'Exception|Caused by', l) and re.search(r'tentaworks', l)]
check('No Tentaworks exceptions (client)', not exc, f"{exc[:3]}")
print('RESULT', 'PASS' if not fails else 'FAIL ' + ','.join(fails))
