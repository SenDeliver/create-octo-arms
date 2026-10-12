# Create: Octo Arms

**A Create Backtank with four pneumatic Tentacles. Climb walls, hang from ceilings, ride Sable and Create Aeronautics Ships.**

![A player lifted off the ground by the four copper Tentacles of a Tentacle Rig](https://raw.githubusercontent.com/SenDeliver/create-octo-arms/main/docs/images/hero-carried-on-tentacles.png)

## What it does

Craft a **Tentacle Rig**: a Backtank with four **Tentacles** and a Precision Mechanism. Wear it in the chest slot and **Double Jump**. The Tentacles grab nearby block faces and carry you where you look: across the floor, up a wall, under a ledge or a ceiling. Your hands stay free, so you can mine, build and fight while you hang. The Rig runs on the Backtank's **Air** and refills like any Backtank.

## Features

- **Climb anything solid**: Tentacles grip any full block face: floor, wall, ceiling, the underside of a ledge, also underwater.
- **Move where you look**: `Space` goes up, `Shift` goes down. The Rig only moves you where at least two Tentacles can hold, so you can't walk off an edge.
- **Double Jump** to activate, also in mid-air: the Rig catches you with no fall damage. Double Jump again to be **Set Down**.
- **Crawl**: out of Air, the Rig does not drop you. It slowly lowers you to the floor.
- **Hands free**: mining, placing, fighting and using items all work while the Rig carries you.
- **Still a Backtank**: it holds Air, feeds a Diving Helmet, takes Capacity and refills under a spinning shaft.
- **Two tiers**: Copper (Reach 4) and Netherite (Reach 7, half the Air use, fire resistant).
- **Three enchantments**: Far Reach, Thrift and Swift, plus vanilla chest armor enchantments.
- **Never breaks**: like an Elytra, a worn-out Rig stops at 1 durability until you repair it.
- **Rig Settings** screen: Standing Height, Speed and Activation Mode (Double Jump or a key).
- **Ride Ships**: works fully with Sable Ships, Create Aeronautics included. Tentacles grip Ship blocks like world blocks and carry you along with a moving, turning Ship.
- A stepping gait everyone on the server sees, Sticker and piston sounds, and the "Doc's Rig" advancement.

![A player climbing a stone wall on four Tentacles](https://raw.githubusercontent.com/SenDeliver/create-octo-arms/main/docs/images/climbing-a-wall.png)

## Quick start

1. Make **Tentacles** on a Sequenced Assembly line (Piston Extension Pole: deploy Sticker, press, deploy Andesite Casing, 2 loops).
2. Craft a **Copper Tentacle Rig** on Mechanical Crafters: four Tentacles, a Precision Mechanism, Brass Casing, Copper Sheet and a Copper Backtank. A new Rig has no Air.
3. Place the Rig as a block under a spinning shaft to fill it. Pick it up again with an empty hand.
4. Wear it in the chest slot and Double Jump. Look where you want to go and walk.
5. Double Jump again to be Set Down. Sneak + use the Rig in the air to open Rig Settings.

Ponder (hold `W` over a Rig or Tentacle) shows all of this step by step.

## Ships (Sable, Create Aeronautics)

Octo Arms works fully with Sable Ships, and Create Aeronautics Ships are Sable Ships, so they are supported the same way. With Sable installed, the blocks of a Ship are Grips like world blocks. Grip a Ship's floor, walls, hull or underside, parked or moving, and move between the world and a Ship both ways. Air use counts only your own move. Server admins can turn it off with `ships.grip = false`.

![A player gripping the side of a Ship moving at 15 blocks per second](https://raw.githubusercontent.com/SenDeliver/create-octo-arms/main/docs/images/ship-moving.png)

## Compatibility

- **Ponder** scene for the Tentacle Rig.
- **Engineer's Goggles**: Air, fill rate, Reach, Top Speed and Air use on a placed Rig Block.
- **Create Display Link**: a Rig Block is a "Rig Air" source for Display Boards and Nixie Tubes.
- **JEI** (optional): all recipes in Create's own categories and in Smithing.
- **Sable** (optional): full Ship support, Create Aeronautics compatible.

![Engineer's Goggles on a placed Rig Block](https://raw.githubusercontent.com/SenDeliver/create-octo-arms/main/docs/images/goggles-rig-block.png)

## Requirements

| Mod | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.228+ |
| [Create](https://modrinth.com/mod/create) | 6.0.10+, below 6.1.0 |
| [Sable](https://modrinth.com/mod/sable) | optional, 2.0.5+, below 2.1 (for Ships) |
| [JEI](https://modrinth.com/mod/jei) | optional |

Install on **both** client and server, same version on both.

## Known limitations

- Tentacles grip full block faces only. Slabs, stairs, fences and other partial blocks are not Grips.
- Mechanical Crafting does not copy the Backtank's Air, so a new Rig starts empty.
- Ships with a scale other than 1 are ignored.
- Movement is client-driven and checked by the server. Not tested with server anti-cheat plugins or real network latency yet.

## Links

- [Source and docs](https://github.com/SenDeliver/create-octo-arms)
- [Issues](https://github.com/SenDeliver/create-octo-arms/issues)
- [Changelog](https://github.com/SenDeliver/create-octo-arms/blob/main/CHANGELOG.md)

MIT License. Built on Create (not bundled); uses Create's Backtank, Sticker and Ponder. Ship support through Sable (optional, not bundled). Developed with AI assistance (Claude); design, testing and direction by SenDeliver.
