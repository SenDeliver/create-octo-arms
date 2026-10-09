"""Tentacle part textures (16x16, block atlas), Copper and Netherite tiers, in Create's palette (own pixels, no Create files copied).
Tubes (sleeve, rod) are drawn by TentacleRenderer with U centred on u=8 and V tiled once per block: detail stays in columns 4..11, rows wrap 15 -> 0.
Hub = mini casing (whole texture per face), collar = a band (centre rows), cog = a decal disc (transparent outside).
The Sticker pad uses create:block/sticker by path.
usage: python3 tools/gen_textures.py"""
import math
import os
from PIL import Image

OUT = 'src/main/resources/assets/tentaworks/textures/block/'

COPPER = dict(
    sleeve=['#6b3426', '#904931', '#a75a40', '#c87456', '#e49d63'],
    rod=['#4f524f', '#6e736f', '#868d88', '#a3aaa4', '#c2c8c2'],
    collar=['#5a3e1e', '#9e6947', '#cea05a', '#f7cb6c', '#ffeb8c'],
    frame=['#3a3d3b', '#505351', '#707570', '#8f9691', '#b5bcb6'],
    core=['#2b1d10', '#553a1f', '#70522e'],
    bolt=['#9e6947', '#f7cb6c'],
    cog=['#3a3d3b', '#707570', '#9aa49d', '#bcc4bd'],
    cogcore=['#553a1f', '#82613a'],
)
NETHERITE = dict(
    sleeve=['#171111', '#241f20', '#322727', '#49393f', '#5d565d'],
    rod=['#2a2628', '#3f3b3e', '#555056', '#6f6a70', '#8e8990'],
    collar=['#171111', '#3f303b', '#51444e', '#6c5a66', '#8a7686'],
    frame=['#171111', '#241f20', '#373537', '#4c474b', '#6a646a'],
    core=['#0e0b0b', '#1d1718', '#2c2526'],
    bolt=['#6c2426', '#c0484b'],
    cog=['#171111', '#373537', '#4c474b', '#6a646a'],
    cogcore=['#241f20', '#49393f'],
)


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def img():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def tube(p, ring_every, rivets):
    """Round-looking tube face: columns shaded by distance from the centre line, a seam ring every ring_every rows."""
    im = img()
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5)
            shade = 4 if d < 1 else 3 if d < 2 else 2 if d < 3 else 1 if d < 4 else 2
            if x in (6,) and d < 2:
                shade = 4
            r = y % ring_every
            if r == 0:
                shade = max(0, shade - 2)
            elif r == 1:
                shade = min(4, shade + 1)
            im.putpixel((x, y), rgb(p[shade]))
    for (x, y) in rivets:
        im.putpixel((x, y), rgb(p[0]))
        im.putpixel((x, y - 1), rgb(p[4]))
    return im


def collar(p):
    im = img()
    for y in range(16):
        for x in range(16):
            edge = y in (0, 15) or x in (0, 15)
            shade = 0 if edge else 1 if y in (1, 14) or x in (1, 14) else 3 if (x + y) % 5 == 0 else 2
            if 6 <= y <= 9:
                shade = 3 if y in (7, 8) else 2
                if x % 4 == 1:
                    shade = 0 if y == 8 else 4
            im.putpixel((x, y), rgb(p[shade]))
    return im


def hub(t):
    f, c, b = t['frame'], t['core'], t['bolt']
    im = img()
    for y in range(16):
        for x in range(16):
            ring = min(x, y, 15 - x, 15 - y)
            if ring == 0:
                col = f[0]
            elif ring == 1:
                col = f[4] if (x < 8 and y < 8) else f[3] if x + y < 16 else f[2]
            elif ring == 2:
                col = f[2] if x + y < 16 else f[1]
            elif ring == 3:
                col = f[0]
            else:
                col = c[1] if (x + y) % 3 else c[2]
                if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 4.5:
                    col = b[1] if x + y < 15 else b[0]
            im.putpixel((x, y), rgb(col))
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        im.putpixel((x, y), rgb(f[0]))
    return im


def cog(t):
    g, core = t['cog'], t['cogcore']
    im = img()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            tooth = (math.cos(a * 8) > 0.2)
            outer = 7.6 if tooth else 6.0
            if r > outer:
                continue
            if r > 5.0:
                col = g[3] if dx + dy < -1 else g[2] if dx + dy < 3 else g[1]
                if r > outer - 0.8:
                    col = g[0]
            elif r > 4.0:
                col = g[0]
            elif r > 1.6:
                col = core[1] if (x + y) % 2 else core[0]
            else:
                col = g[0]
            im.putpixel((x, y), rgb(col))
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, t in (('', COPPER), ('_netherite', NETHERITE)):
        tube(t['sleeve'], 8, ((7, 5), (8, 13))).save(f'{OUT}tentacle_sleeve{name}.png')
        tube(t['rod'], 16, ()).save(f'{OUT}tentacle_rod{name}.png')
        collar(t['collar']).save(f'{OUT}tentacle_collar{name}.png')
        hub(t).save(f'{OUT}tentacle_hub{name}.png')
        cog(t).save(f'{OUT}tentacle_cog{name}.png')
    print('written', OUT)


if __name__ == '__main__':
    main()
