# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/SenDeliver/create-octo-arms/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/SenDeliver/create-octo-arms/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/SenDeliver/create-octo-arms/releases/tag/v1.0.0
