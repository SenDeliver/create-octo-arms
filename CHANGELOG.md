# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Platforms: the same version number ships for every supported Minecraft version (1.21.1 NeoForge, 1.20.1 Forge). An entry that applies to one platform only starts with `**1.20.1 only:**` or `**1.21.1 only:**`; untagged entries apply to all. Release tags carry the platform, `vX.Y.Z+mc1.21.1` and `vX.Y.Z+mc1.20.1`; the bare tags up to `v1.5.0` are 1.21.1 releases.

## [Unreleased]

## [1.5.1] - 2026-10-24

### Fixed

- No view bobbing while the Rig carries you. With the body on the floor (looking down, low Standing Height) vanilla bobbed the camera and the hand as if you walked; with View Bobbing on it looked like a sprint with camera shake. Client only.

### Compatibility

- Client-only change, network unchanged (registrar "8"). 1.5.0 and 1.5.1 clients and servers mix freely.

## [1.5.0] - 2026-10-20

### Fixed

- Tentacles no longer reach through the floor or a wall. A Grip must be in sight of the wearer: the straight line from the body to the Grip point crosses no block. A low body (Standing Height 0, Shift, Crawl) on a floor with a cave under it used to grip the cave's floor and walls through the platform, and the lower Tentacle segments went through the floor.
- The back Tentacles never aim under the floor the wearer stands on: a low body plants them on its own floor instead of hunting for something deeper.

### Compatibility

- Server and client change (the sight rule is checked on both sides, network payloads unchanged). A 1.4.0 client on a newer server gets its through-block Grips rejected (Falling after 10 ticks if fewer than two remain); a newer client on a 1.4.0 server keeps working. Update both.

### Platforms

- **1.20.1 only:** First Forge 1.20.1 build of 1.5.0 (Create 6.0.8, Forge 47.1.3+, Java 17). Not included: Sable Ships, in-game config screen. Items keep their data in NBT; worlds do not transfer between Minecraft versions. 1.21.1 clients cannot join a 1.20.1 server and the other way round.

## [1.4.0] - 2026-10-20

### Added

- The Rig shows a durability bar above its Air bar once damaged, so wear is visible without the tooltip. ([#19](https://github.com/SenDeliver/create-octo-arms/issues/19))

### Fixed

- Tentacles grip Create's Fluid Tanks (and Creative Fluid Tanks). A face is a Grip when it is sturdy in the vanilla sense (what torches and rails attach to), not only when the block is a full cube. ([#19](https://github.com/SenDeliver/create-octo-arms/issues/19))
- Tentacles grip recessed surfaces at their real height: the top of a bottom slab, the underside of a top slab, slab sides, stair steps, snow, carpet, farmland, closed trapdoors, chest lids, wall tops, Create depots; the broad faces of doors, panes and iron bars too. A horizontal slab can be passed over and under like a full block. Nothing narrower than half a block (fence posts, chains, rails' sides), nothing with a hole under the tip (hopper and cauldron rims, scaffolding sides) and nothing the player walks through (one layer of snow, hanging signs) is a Grip.

### Compatibility

- Server and client change (the Grip rule is checked on both sides, network payloads unchanged). A newer client on a 1.3.1 server gets its tank, slab and other new Grips rejected after 10 ticks (Falling); a 1.3.1 client on a newer server keeps working, every Grip it knows is still valid. Update both.

## [1.3.1] - 2026-10-18

### Added

- Client config `movement.forward`: `flat` makes `W`/`S` move level like creative flight, whatever you look at; `Space`/`Shift` change the height. Default `look` keeps the camera-following movement. ([#18](https://github.com/SenDeliver/create-octo-arms/issues/18))
- The client and server configs are editable in game under Mods > Octo Arms > Config.

### Compatibility

- Client-only change: works with a 1.3.0 server, no network change.

## [1.3.0] - 2026-10-17

### Fixed

- `Shift` leaves seats, boats, minecarts and horses again when you sat down with the Rig out. Boats and horses steer and jump normally.
- Create's chain conveyor carries you even if the Rig was out when you grabbed it.
- Create Aeronautics controls work with the Rig out: `Shift` snaps the steering wheel while you hold it, the Rig keeps its hold and stays still. Handles pull you in and `W`/`S` work them.

### Changed

- Sitting down, mounting, grabbing a chain conveyor or zipline, or a handle pulling you in stows the Rig. It can't be activated during the ride and stays stowed after it: Double Jump again to use it.

### Compatibility

- No network change: mixes with 1.2.x. A 1.2.x client on an updated server also gets the seat fix (the server stows the Rig).

## [1.2.1] - 2026-10-16

### Added

- Translations: Chinese (Simplified, Traditional), Czech, French, German, Italian, Japanese, Korean, Polish, Portuguese (Brazil), Spanish (Spain, Mexico), Turkish, Ukrainian, Vietnamese. Native speakers welcome to suggest fixes.

### Compatibility

- Client-only change: works with a 1.2.0 server.

## [1.2.0] - 2026-10-15

### Changed

- Sneaking works while the Rig is out. `Shift` still moves you down and now also counts as sneaking, so sneak + right click (Create's wrench, placing against a block, ...) works while you hang. You don't crouch: the camera stays put. The usual sneak perks apply (no trampling, no magma burn), and `Shift` dismounts a seat.

### Compatibility

- Needs the update on both client and server for the full effect. With an older server, other players see you crouch while you sneak.

## [1.1.5] - 2026-10-14

### Fixed

- Climbing a pole or a building corner from the diagonal no longer gets stuck partway.
- Moving around a pole now works. The Rig keeps hold while you go round the corner.

### Compatibility

- Client-side only. 1.1.x clients and servers mix freely. (1.1.4 was not published; its fix is included here.)

## [1.1.3] - 2026-10-14

Same content as 1.1.2, re-released so the published jar carries the correct `octo-arms-` name everywhere.

### Fixed

- No more "walking in the sky": the body hangs still while the Rig or a Ship carries you. Client-side, also for players you watch.

### Added

- Client config `config/tentaworks-client.toml`: `animation.walkWhileHeld = true` brings the walk animation back.

### Changed

- Jar renamed to `octo-arms-<version>+mc1.21.1.jar` (was `tentaworks-…`). Mod id stays `tentaworks`, worlds and configs are unaffected. Delete the old `tentaworks-*.jar` when updating.

### Compatibility

- 1.1.x clients and servers mix freely; no network or save changes.

## [1.1.2] - 2026-10-14

### Fixed

- No more "walking in the sky": the body hangs still while the Rig or a Ship carries you. Client-side, also for players you watch.

### Added

- Client config `config/tentaworks-client.toml`: `animation.walkWhileHeld = true` brings the walk animation back.

### Changed

- Jar renamed to `octo-arms-<version>+mc1.21.1.jar` (was `tentaworks-…`). Mod id stays `tentaworks`, worlds and configs are unaffected. Delete the old `tentaworks-*.jar` when updating.

### Compatibility

- 1.1.x clients and servers mix freely; no network or save changes.

## [1.1.1] - 2026-10-12

### Fixed

- Client crash (`InternalError: a fault occurred in an unsafe memory access operation` in `TentacleRenderer.sides`) for players watching a wearer grip a Ship whose pose their client had not received yet. The Grip point was taken at raw plot coordinates (about 1.28 million blocks out), each such arm was about 1.5 GB of vertices and two of them overflowed the vertex buffer, so it crashed only some of the time. Both guards log a warning when they trigger. Grips beyond twice the max reach, or with a non-finite point, are no longer drawn, and a sleeve never tiles more than 64 blocks.
- First-person Rig drawing no longer ends a batch on the shared buffer source mid-frame; it uses its own buffer. Avoids freeing builders other render layers still hold under batching mods (ImmediatelyFast).

## [1.1.0] - 2026-10-09

First public release (Modrinth, CurseForge).

The Rig grips Sable Ships (also Create Aeronautics Ships) and rides them.

### Added

- Ship Grips: with Sable installed, Tentacles grip the blocks of a Ship like world blocks: floor, wall, ceiling and hull, parked or moving.
- The wearer rides the Ship: a Held wearer is carried along with it, also when it turns. The Ship is the one that holds most of the wearer's Grips. Moving between world blocks and a Ship works both ways.
- Server config key `ships.grip` (default `true`). Off: Ship blocks are never Grips. No effect without Sable.

### Changed

- A Grip on a Ship is stored in the Ship's own (plot) coordinates. The Ship is found from the block, so the Grip stays put while the Ship moves.
- Air use, speed checks, Crawl and Setting Down count only the wearer's own move, not the Ship's. Air on a Ship uses the mean of the last 10 ticks.
- The server checks a Ship Grip against the Ship's last 10 poses, so client lag on a moving Ship does not drop Grips.
- A Ship removed while Held: the wearer loses those Grips and falls, as with broken blocks.
- Standing on a deck: Sable already moves the wearer, so the Rig adds no carry of its own.
- Ships with a scale other than 1 are ignored.

### Compatibility

- **Clients must update.** The network registrar goes from `7` to `8`: 1.0 clients cannot join a 1.1 server, and 1.1 clients cannot join a 1.0 server. Packet bytes are unchanged, but a Grip can now be in Ship coordinates.
- No world or save change. Grips are not saved. 1.0 worlds load as is.
- Sable is an **optional** dependency, `[2.0.5,2.1)`. Create Aeronautics is not needed, but its Ships are Sable Ships, so they work. Without Sable the mod works as in 1.0.
- Tested with Sable 2.0.5 and the Create Aeronautics 1.3.2 bundle.

## [1.0.0] - 2026-10-09

First release as **Create: Octo Arms** (mod id `tentaworks`), server-internal, not published to mod sites. The project was called Tentaworks during development; the mod id keeps that name.

### Added

- Copper and Netherite Tentacle Rig: a Backtank with four pneumatic Tentacles, worn in the chest slot. Copper: Reach 4. Netherite: Reach 7, half the Air use, fire resistant.
- Active movement over floors, walls, ceilings, ledges and underwater. Move where you look, `Space` up, `Shift` down; movement only goes where at least two Tentacles can hold.
- Double Jump to activate (also mid-air, catching the wearer without fall damage) and Double Jump again for Setting Down. Hang, Idle, Falling and Crawl (slow descent on empty Air).
- Air use from the Backtank: holding, moving and climbing rates; Netherite uses half. Rig Block (a placed Rig) refills under a spinning shaft and is picked up again with its Air, enchantments and settings.
- Rig Settings screen: Standing Height, Speed and Activation Mode (Double Jump or key only). Two key binds, unbound by default.
- Enchantments Far Reach (I–III), Thrift (I–III) and Swift (I–II). The Rig also takes vanilla chest armor enchantments and Create's Capacity.
- Elytra-style durability: a worn-out Rig stops at 1 durability until repaired by anvil (Copper / Netherite Ingot) or Mending. Nothing is lost.
- Recipes: Tentacle by Sequenced Assembly, Rigs by Mechanical Crafting, Netherite Rig also by Smithing from a Copper Rig.
- Stepping gait animation seen by all players, Sticker and piston sounds, particles.
- Create-style tooltips with Rig stats, a Ponder scene, Engineer's Goggles info on Rig Blocks, a "Rig Air" Display Link source, JEI recipes through Create's categories, and the "Doc's Rig" advancement.
- Server config for Reach, top Speed, Air costs, Crawl speed and enchantment strengths.

### Notes

- Earlier builds were internal test versions and were not published.

[Unreleased]: https://github.com/SenDeliver/create-octo-arms/compare/v1.5.1%2Bmc1.21.1...HEAD
[1.5.1]: https://github.com/SenDeliver/create-octo-arms/compare/v1.5.0...v1.5.1%2Bmc1.21.1
[1.5.0]: https://github.com/SenDeliver/create-octo-arms/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/SenDeliver/create-octo-arms/compare/v1.3.1...v1.4.0
[1.3.1]: https://github.com/SenDeliver/create-octo-arms/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/SenDeliver/create-octo-arms/compare/v1.2.1...v1.3.0
[1.2.1]: https://github.com/SenDeliver/create-octo-arms/compare/v1.2.0...v1.2.1
[1.1.0]: https://github.com/SenDeliver/create-octo-arms/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/SenDeliver/create-octo-arms/releases/tag/v1.0.0
