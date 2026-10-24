"""Static Tentacle models (JSON elements), from the same part sizes as TentacleRenderer:
- block/tentacle_arms_folded[_netherite]: the four Tentacles folded against the tank (TentacleRenderer.SHOULDER / FOLD_* poses),
- block/<tier>_tentacle_rig + _item: forge:composite of Create's backtank model (by path) + the folded arms,
- item/tentacle_arm, item/incomplete_tentacle_arm: the Tentacle item, a diagonal arm.
Keep the poses and sizes in sync with TentacleRenderer (FOLD_*, SHOULDER, drawArm).
usage: python3 tools/gen_models.py"""
import json
import math
import os

OUT = 'src/main/resources/assets/tentaworks/models/'

SHOULDER = [(2.75, 10.0, 7.5), (13.25, 10.0, 7.5), (2.75, 3.0, 7.5), (13.25, 3.0, 7.5)]
ELBOW = [(2.75, 13.5, 7.5), (13.25, 13.5, 7.5), (2.75, 6.5, 7.5), (13.25, 6.5, 7.5)]
WRIST = [(2.75, 13.5, 11.0), (13.25, 13.5, 11.0), (2.75, 6.5, 11.0), (13.25, 6.5, 11.0)]
NORMAL = [(0, 1, 0), (0, 1, 0), (0, 1, 0), (0, 1, 0)]
WORN = {'shoulder': SHOULDER, 'elbow': ELBOW, 'wrist': WRIST, 'normal': NORMAL, 'hub': 1.0}
# Placed Rig Block only (not the worn Stowed pose): shorter segments tucked against the tank sides, pads inside the tank depth, smaller hubs.
BLOCK = {'shoulder': [(3.5, 9.0, 6.0), (12.5, 9.0, 6.0), (3.5, 3.0, 6.0), (12.5, 3.0, 6.0)],
         'elbow': [(3.5, 12.0, 6.0), (12.5, 12.0, 6.0), (3.5, 6.0, 6.0), (12.5, 6.0, 6.0)],
         'wrist': [(3.5, 12.0, 9.5), (12.5, 12.0, 9.5), (3.5, 6.0, 9.5), (12.5, 6.0, 9.5)],
         'normal': NORMAL, 'hub': 0.75, 'cogs': False}
PAD, STEM = 2.0, 1.5
AX = 'xyz'


def r(v):
    return round(v, 4)


def sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def axis_of(a, b):
    d = sub(b, a)
    i = max(range(3), key=lambda k: abs(d[k]))
    return i, (1 if d[i] > 0 else -1), abs(d[i])


def faces_tube(axis, w, d_len, tex, v0=0.0):
    """Tube texture: across = centred columns, along = rows from v0 (tile once per 16 px, capped)."""
    f = {}
    along = min(16.0, d_len)
    side_uv = [8 - w / 2, v0, 8 + w / 2, v0 + along]
    cap_uv = [8 - w / 2, 8 - w / 2, 8 + w / 2, 8 + w / 2]
    names = {0: ('east', 'west'), 1: ('up', 'down'), 2: ('south', 'north')}
    for i in range(3):
        for n in names[i]:
            if i == axis:
                f[n] = {'uv': [r(x) for x in cap_uv], 'texture': tex}
            else:
                face = {'uv': [r(x) for x in side_uv], 'texture': tex}
                if axis == 2 and n in ('east', 'west'):
                    face['rotation'] = 90
                if axis == 0 and n in ('up', 'down', 'north', 'south'):
                    face['rotation'] = 90
                f[n] = face
    return f


def faces_block(tex):
    return {n: {'uv': [0, 0, 16, 16], 'texture': tex} for n in ('north', 'south', 'east', 'west', 'up', 'down')}


def faces_band(axis, w, h, tex):
    f = faces_tube(axis, w, h, tex)
    for n, face in f.items():
        if 'rotation' in face or face['uv'][3] - face['uv'][1] == h:
            face['uv'] = [0, r(8 - h / 2), 16, r(8 + h / 2)]
    return f


def box(lo, hi, faces, rot=None):
    e = {'from': [r(min(a, b)) for a, b in zip(lo, hi)], 'to': [r(max(a, b)) for a, b in zip(lo, hi)], 'faces': faces}
    if rot:
        e['rotation'] = rot
    return e


def seg_box(a, b, frm, to, half, faces_fn):
    """Box along the axis-aligned segment a→b, from frm to to (px along it), square half-size."""
    i, s, _ = axis_of(a, b)
    lo, hi = list(a), list(a)
    for k in range(3):
        if k == i:
            lo[k] = a[k] + s * frm
            hi[k] = a[k] + s * to
        else:
            lo[k] = a[k] - half
            hi[k] = a[k] + half
    return box(lo, hi, faces_fn(i))


def folded_arm(slot, t, pose=WORN):
    s, e, w, n = pose['shoulder'][slot], pose['elbow'][slot], pose['wrist'][slot], pose['normal'][slot]
    k = pose['hub']
    out = []
    out.append(box([c - 1.75 * k for c in s], [c + 1.75 * k for c in s], faces_block(t['hub'])))
    ox = -1 if slot % 2 == 0 else 1
    x = s[0] + ox * 1.8 * k
    cogs = pose.get('cogs', True)
    if cogs:
        out.append(box([x, s[1] - 2 * k, s[2] - 2 * k], [x, s[1] + 2 * k, s[2] + 2 * k],
                   {'east': {'uv': [0, 0, 16, 16], 'texture': t['cog']}, 'west': {'uv': [0, 0, 16, 16], 'texture': t['cog']}}))
    _, _, l1 = axis_of(s, e)
    sl = max(0.6 * l1, 2.5)
    out.append(seg_box(s, e, 1.0, sl - 0.15, 1.5, lambda i: faces_tube(i, 3, sl - 1.15, t['sleeve'])))
    if sl >= 4.5:
        out.append(seg_box(s, e, 1.75, 2.75, 1.9, lambda i: faces_band(i, 3.8, 1.0, t['collar'])))
    out.append(seg_box(s, e, sl - 1.2, sl, 1.9, lambda i: faces_band(i, 3.8, 1.2, t['collar'])))
    out.append(seg_box(s, e, sl - 1.0, l1, 1.0, lambda i: faces_tube(i, 2, l1 - sl + 1.0, t['rod'])))
    rot = {'angle': 45, 'axis': 'x', 'origin': list(e)}
    out.append(box([e[0] - 1.5 * k, e[1] - 1.65 * k, e[2] - 1.65 * k], [e[0] + 1.5 * k, e[1] + 1.65 * k, e[2] + 1.65 * k], faces_block(t['hub']), rot))
    out.append(box([e[0] - 2.5 * k, e[1] - 0.6, e[2] - 0.6], [e[0] + 2.5 * k, e[1] + 0.6, e[2] + 0.6], faces_tube(0, 1.2, 5 * k, t['rod']), rot))
    for cx in ((-2.0 * k, 2.0 * k) if cogs else ()):
        out.append(box([e[0] + cx, e[1] - 1.8 * k, e[2] - 1.8 * k], [e[0] + cx, e[1] + 1.8 * k, e[2] + 1.8 * k],
                       {'east': {'uv': [0, 0, 16, 16], 'texture': t['cog']}, 'west': {'uv': [0, 0, 16, 16], 'texture': t['cog']}}, rot))
    _, _, l2 = axis_of(e, w)
    fl = max(0.55 * l2, 2.0)
    if fl > 1.6:
        out.append(seg_box(e, w, 1.5, fl - 0.15, 1.2, lambda i: faces_tube(i, 2.4, fl - 1.65, t['sleeve'])))
    out.append(seg_box(e, w, fl - 1.0, fl, 1.55, lambda i: faces_band(i, 3.1, 1.0, t['collar'])))
    out.append(seg_box(e, w, fl - 1.0, l2, 0.75, lambda i: faces_tube(i, 1.5, l2 - fl + 1.0, t['rod'])))
    out.append(box([c - 1.25 * k for c in w], [c + 1.25 * k for c in w], faces_block(t['hub'])))
    tip = tuple(c - k * (PAD + STEM) for c, k in zip(w, n))
    i = [k for k in range(3) if n[k] != 0][0]
    sgn = n[i]
    lo, hi = [c - 2.25 for c in tip], [c + 2.25 for c in tip]
    lo[i], hi[i] = tip[i] + sgn * 0.004, tip[i] + sgn * PAD
    st = 'create:block/sticker'
    names = {0: ('east', 'west'), 1: ('up', 'down'), 2: ('south', 'north')}
    pos_name, neg_name = names[i]
    slime_name, back_name = (neg_name, pos_name) if sgn > 0 else (pos_name, neg_name)
    pf = {}
    for k in range(3):
        for nm in names[k]:
            pf[nm] = {'uv': [8.5, 0, 15.5, 2.0], 'texture': '#sticker'}
            if (i == 0 and nm in ('up', 'down', 'north', 'south')) or (i == 2 and nm in ('east', 'west')):
                pf[nm]['rotation'] = 90
    pf[slime_name] = {'uv': [8.5, 8.5, 15.5, 15.5], 'texture': '#sticker'}
    pf[back_name] = {'uv': [0.5, 8.5, 7.5, 15.5], 'texture': '#sticker'}
    out.append(box(lo, hi, pf))
    stem_a = tuple(c + k * PAD for c, k in zip(tip, n))
    out.append(seg_box(stem_a, w, 0, STEM + 0.5, 0.75, lambda j: faces_tube(j, 1.5, STEM + 0.5, t['rod'])))
    return out


def spread_arms_model():
    """Rig item icon: the four Tentacles spread out from the tank sides in an X (upper pair up and out, lower pair down and out)."""
    t = VARS
    els = []
    for slot in range(4):
        s = SHOULDER[slot]
        left = slot % 2 == 0
        upper = slot < 2
        angle = (45 if left else -45) * (1 if upper else -1)
        rot = {'angle': angle, 'axis': 'z', 'origin': list(s)}
        els.append(box([c - 1.75 for c in s], [c + 1.75 for c in s], faces_block(t['hub'])))
        x, y, z = s
        sg = 1 if upper else -1

        def span(a, b):
            return (y + sg * a, y + sg * b) if upper else (y + sg * b, y + sg * a)

        y0, y1 = span(1.75, 6.5)
        els.append(box([x - 1.5, y0, z - 1.5], [x + 1.5, y1, z + 1.5], faces_tube(1, 3, 4.75, t['sleeve']), rot))
        y0, y1 = span(5.3, 6.5)
        els.append(box([x - 1.9, y0, z - 1.9], [x + 1.9, y1, z + 1.9], faces_band(1, 3.8, 1.2, t['collar']), rot))
        y0, y1 = span(6.5, 10.5)
        els.append(box([x - 1, y0, z - 1], [x + 1, y1, z + 1], faces_tube(1, 2, 4, t['rod']), rot))
        y0, y1 = span(10.5, 12.5)
        pf = {n: {'uv': [8.5, 0, 15.5, 2.0], 'texture': '#sticker'} for n in ('north', 'south', 'east', 'west')}
        pf['up' if upper else 'down'] = {'uv': [8.5, 8.5, 15.5, 15.5], 'texture': '#sticker'}
        pf['down' if upper else 'up'] = {'uv': [0.5, 8.5, 7.5, 15.5], 'texture': '#sticker'}
        els.append(box([x - 2.25, y0, z - 2.25], [x + 2.25, y1, z + 2.25], pf, rot))
    tex = textures('copper')
    tex['sticker'] = 'create:block/sticker'
    tex['particle'] = 'create:block/copper_backtank'
    return {'render_type': 'minecraft:cutout', 'textures': tex, 'elements': els}


def textures(tier):
    s = '_netherite' if tier == 'netherite' else ''
    return {k: f'tentaworks:block/tentacle_{k}{s}' for k in ('sleeve', 'rod', 'collar', 'hub', 'cog')}


VARS = {k: '#' + k for k in ('sleeve', 'rod', 'collar', 'hub', 'cog')}


def write(path, data):
    os.makedirs(os.path.dirname(OUT + path), exist_ok=True)
    with open(OUT + path, 'w') as f:
        json.dump(data, f, indent=1)
        f.write('\n')


def overhang(elements):
    """Largest distance (px) any element corner reaches outside the 0..16 block, 45-degree rotations applied."""
    worst = 0.0
    for el in elements:
        lo, hi = el['from'], el['to']
        corners = [[a, b, c] for a in (lo[0], hi[0]) for b in (lo[1], hi[1]) for c in (lo[2], hi[2])]
        rot = el.get('rotation')
        if rot:
            ang, ax, o = math.radians(rot['angle']), AX.index(rot['axis']), rot['origin']
            i, j = [q for q in range(3) if q != ax]
            for p in corners:
                u, v = p[i] - o[i], p[j] - o[j]
                p[i], p[j] = o[i] + u * math.cos(ang) - v * math.sin(ang), o[j] + u * math.sin(ang) + v * math.cos(ang)
        for p in corners:
            worst = max(worst, max(max(-c, c - 16) for c in p))
    return worst


def arms_model():
    elements = []
    for slot in range(4):
        elements += folded_arm(slot, VARS, BLOCK)
    print(f'block fold overhang {overhang(elements):.2f} px (worn fold {overhang(sum((folded_arm(i, VARS) for i in range(4)), [])):.2f} px)')
    tex = textures('copper')
    tex['sticker'] = 'create:block/sticker'
    tex['particle'] = 'create:block/copper_backtank'
    return {'render_type': 'minecraft:cutout', 'textures': tex, 'elements': elements}


def item_arm(complete):
    """Tentacle item: shoulder hub low left, upper segment up-right at 45 deg, elbow hub, forearm straight up, Sticker pad on top."""
    t = VARS
    up = {'angle': -45, 'axis': 'z', 'origin': [5, 3, 8]}
    els = []
    if complete:
        els.append(box([3, 1, 6], [7, 5, 10], faces_block(t['hub'])))
        els.append(box([2.9, 1.2, 6.2], [2.9, 4.8, 9.8], {'west': {'uv': [0, 0, 16, 16], 'texture': t['cog']}}))
        els.append(box([3.5, 5, 6.5], [6.5, 9, 9.5], faces_tube(1, 3, 4, t['sleeve']), up))
        els.append(box([3.1, 8, 6.1], [6.9, 9.2, 9.9], faces_band(1, 3.8, 1.2, t['collar']), up))
        els.append(box([4, 9, 7], [6, 12, 9], faces_tube(1, 2, 3, t['rod']), up))
        els.append(box([10.5, 9, 6.5], [13.5, 12, 9.5], faces_block(t['hub'])))
        els.append(box([10.4, 9.2, 6.7], [10.4, 11.8, 9.3], {'west': {'uv': [0, 0, 16, 16], 'texture': t['cog']}}))
        els.append(box([11, 12, 7], [13, 13.5, 9], faces_tube(1, 2, 1.5, t['sleeve'])))
        els.append(box([11.25, 13.5, 7.25], [12.75, 14, 8.75], faces_tube(1, 1.5, 0.5, t['rod'])))
        pad = ([9.75, 14, 5.75], [14.25, 16, 10.25])
    else:
        els.append(box([4, 1, 7], [6, 13, 9], faces_tube(1, 2, 12, t['rod']), {'angle': -22.5, 'axis': 'z', 'origin': [5, 7, 8]}))
        pad = ([3, 13, 6], [7.5, 15, 10.5])
    pf = {n: {'uv': [8.5, 0, 15.5, 2.0], 'texture': '#sticker'} for n in ('north', 'south', 'east', 'west')}
    pf['up'] = {'uv': [8.5, 8.5, 15.5, 15.5], 'texture': '#sticker'}
    pf['down'] = {'uv': [0.5, 8.5, 7.5, 15.5], 'texture': '#sticker'}
    els.append(box(pad[0], pad[1], pf, None if complete else {'angle': -22.5, 'axis': 'z', 'origin': [5, 7, 8]}))
    tex = textures('copper')
    tex['sticker'] = 'create:block/sticker'
    tex['particle'] = 'tentaworks:block/tentacle_sleeve'
    return {'parent': 'block/block', 'render_type': 'minecraft:cutout', 'textures': tex, 'elements': els,
            'display': {
                'gui': {'rotation': [20, -30, 0], 'translation': [0, 0, 0], 'scale': [0.85, 0.85, 0.85]},
                'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
                'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.8, 0.8, 0.8]},
                'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.5, 0.5, 0.5]},
                'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
                'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
            }}


def main():
    write('block/tentacle_arms_folded.json', arms_model())
    nt = textures('netherite')
    nt['particle'] = 'create:block/netherite_backtank'
    write('block/tentacle_arms_folded_netherite.json', {'parent': 'tentaworks:block/tentacle_arms_folded', 'textures': nt})
    write('block/tentacle_arms_spread.json', spread_arms_model())
    write('block/tentacle_arms_spread_netherite.json', {'parent': 'tentaworks:block/tentacle_arms_spread', 'textures': nt})
    for tier in ('copper', 'netherite'):
        n = '_netherite' if tier == 'netherite' else ''
        for suffix, tank, arms in (('', 'block', 'folded'), ('_item', 'item', 'spread')):
            model = {
                'parent': 'block/block',
                'loader': 'forge:composite',
                'render_type': 'minecraft:cutout',
                'textures': {'particle': f'create:block/{tier}_backtank'},
                'children': {
                    'tank': {'parent': f'create:block/{tier}_backtank/{tank}'},
                    'arms': {'parent': f'tentaworks:block/tentacle_arms_{arms}{n}'},
                },
            }
            if suffix:
                model['display'] = {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.48, 0.48, 0.48]},
                                    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
                                    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.2, 0.2, 0.2]}}
            write(f'block/{tier}_tentacle_rig{suffix}.json', model)
    write('item/tentacle_arm.json', item_arm(True))
    write('item/incomplete_tentacle_arm.json', item_arm(False))
    print('written', OUT)


if __name__ == '__main__':
    main()
