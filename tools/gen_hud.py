"""Crosshair HUD sprites of the Actuators (GUI atlas, own pixels): claw brackets left and right of the crosshair (solid and
hollow), the Grab claws, the reason glyphs, the set-down marker and the scroll wheel.
Shapes are drawn as ASCII grids: W white, L light grey, A amber, R red, G grey; every shape gets a dark one-pixel rim so it reads
on any background. White and light grey take the tint ActuatorHud draws them with (tier, red when limp).
usage: python3 tools/gen_hud.py"""
import os
from PIL import Image

OUT = 'src/main/resources/assets/tentaworks/textures/gui/sprites/hud/'
INK = {'W': (255, 255, 255, 255), 'L': (196, 196, 196, 255), 'A': (255, 196, 70, 255), 'a': (214, 150, 40, 255),
       'R': (255, 72, 72, 255), 'G': (200, 200, 200, 255), 'g': (150, 150, 150, 255)}
RIM = (18, 16, 18, 235)
HOLLOW_RING = (150, 150, 150, 255)
HOLLOW_FILL = (18, 16, 18, 120)

MARK = """
..WWW
.WW..
WW...
WW...
WL...
WW...
WW...
.WL..
..LLL
"""
CLAW_OPEN = """
...WWW...
...WLW...
..WWWWW..
.WW.W.WW.
.W..W..W.
WW..L..WW
W.......W
"""
CLAW_CLOSED = """
...WWW...
...WLW...
..WWWWW..
.WW...WW.
.W..L..W.
.WW.L.WW.
..WW.WW..
"""
TWO_HANDS = """
.AAA...AAA.
.AaA...AaA.
AAAAA.AAAAA
A.A.A.A.A.A
A...A.A...A
"""
HANDS_FULL = """
...AAA...
...AaA...
..AAAAA..
.AA...AA.
.A.aaa.A.
.A.aaa.A.
.AA...AA.
..AA.AA..
"""
REFUSED = """
...GGG..R
...GgG.RR
..GGGGRR.
.GG.GRRG.
.G..RR.G.
GG.RR..GG
G.RR....G
.RR......
"""
SET_DOWN = """
W...W
WW.WW
.WWW.
..W..
.....
LLLLL
"""
SCROLL = """
..W..
.WWW.
.....
.GGG.
.GgG.
.GGG.
.....
.WWW.
..W..
"""


def grid(text):
    rows = [r for r in text.strip('\n').split('\n')]
    w = max(len(r) for r in rows)
    return [r.ljust(w, '.') for r in rows]


def near(rows, x, y):
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            yy, xx = y + dy, x + dx
            if 0 <= yy < len(rows) and 0 <= xx < len(rows[0]) and rows[yy][xx] != '.':
                return True
    return False


def sprite(text, flip=False, hollow=False):
    rows = grid(text)
    if flip:
        rows = [r[::-1] for r in rows]
    h, w = len(rows), len(rows[0])
    im = Image.new('RGBA', (w + 2, h + 2), (0, 0, 0, 0))
    for y in range(-1, h + 1):
        for x in range(-1, w + 1):
            inside = 0 <= y < h and 0 <= x < w and rows[y][x] != '.'
            if inside:
                im.putpixel((x + 1, y + 1), HOLLOW_FILL if hollow else INK[rows[y][x]])
            elif near(rows, x, y):
                im.putpixel((x + 1, y + 1), HOLLOW_RING if hollow else RIM)
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    out = {
        'mark_left': sprite(MARK), 'mark_right': sprite(MARK, flip=True),
        'mark_left_hollow': sprite(MARK, hollow=True), 'mark_right_hollow': sprite(MARK, flip=True, hollow=True),
        'claw_open': sprite(CLAW_OPEN), 'claw_closed': sprite(CLAW_CLOSED), 'two_hands': sprite(TWO_HANDS),
        'hands_full': sprite(HANDS_FULL), 'refused': sprite(REFUSED), 'set_down': sprite(SET_DOWN), 'scroll': sprite(SCROLL),
    }
    for name, im in out.items():
        im.save(f'{OUT}{name}.png')
        print(name, im.size)


if __name__ == '__main__':
    main()
