"""Builds the Ponder scene schematic in a dedicated-server world and exports it with /tentaworks schematic.
Run: python3 tools/gen_ponder.py, then
TIMELINE=tools/scen/ponder-export.txt tools/run_split.sh tools/scen/empty.txt tools/scen/quit-client.txt tools/logs/ponder-export \
  with GRADLE_ARGS=-Ptentaworks.ponderOut=$PWD/src/main/resources/assets/tentaworks/ponder
Scene coords (9x9 plate at y 0): Rig Block (1,1,1) under a creative motor; crafter grid x 3..5 y 1..3 z 6 facing north,
exit west into a depot (2,1,6); wall x 7 z 0..4 y 1..5 with a ledge y 6 x 4..7 z 2..4."""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
X, Y, Z = 3300, -40, 300
SIZE, HEIGHT = 9, 8


def at(x, y, z, block):
    return f"setblock {X+x} {Y+y} {Z+z} {block}"


def fill(a, b, block):
    return f"fill {X+a[0]} {Y+a[1]} {Z+a[2]} {X+b[0]} {Y+b[1]} {Z+b[2]} {block}"


def scene():
    out = [fill((-1, -1, -1), (SIZE, HEIGHT, SIZE), "minecraft:air"), fill((0, -1, 0), (SIZE - 1, -1, SIZE - 1), "minecraft:barrier")]
    for x in range(SIZE):
        for z in range(SIZE):
            out.append(at(x, 0, z, "minecraft:white_concrete" if (x + z) % 2 == 0 else "minecraft:snow_block"))
    out.append(at(1, 1, 1, "tentaworks:copper_tentacle_rig[facing=north,arms=true]{Air:200}"))
    out.append(at(1, 2, 1, "create:creative_motor[facing=down]{ScrollValue:64}"))
    out.append(f"tentaworks pondergrid {X+3} {Y+1} {Z+6}")
    out.append(at(2, 1, 6, "create:depot"))
    out.append(fill((7, 1, 0), (7, 5, 4), "minecraft:stone_bricks"))
    out.append(fill((4, 6, 2), (7, 6, 4), "minecraft:stone_bricks"))
    return out + ["#sleep 1", f"tentaworks schematic tentacle_rig {X} {Y} {Z} {X+SIZE-1} {Y+HEIGHT-1} {Z+SIZE-1}"]


if __name__ == "__main__":
    lines = ["forceload add 3290 290 3320 320", "#sleep 3"] + scene() + ["#sleep 1", "#signal a"]
    with open(os.path.join(HERE, "scen", "ponder-export.txt"), "w") as f:
        f.write("\n".join(lines) + "\n")
    print("wrote scen/ponder-export.txt")
