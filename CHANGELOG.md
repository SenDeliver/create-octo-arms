# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Platforms: the same version number ships for every supported Minecraft version (1.21.1 NeoForge, 1.20.1 Forge). An entry that applies to one platform only starts with `**1.20.1 only:**` or `**1.21.1 only:**`; untagged entries apply to all. Release tags carry the platform, `vX.Y.Z+mc1.21.1` and `vX.Y.Z+mc1.20.1`; the bare tags up to `v1.5.0` are 1.21.1 releases.

## [Unreleased]

### Added

- Server config `speed.check` (default on): off, the server no longer Stows a Rig for moving faster than its top Speed, for servers that lag badly.

### Fixed

- A Rig moving at its top Speed was Stowed on a lagging server ("moves faster than its top Speed: stowing"), dropping the wearer: the speed check counted server ticks, so below about 13 TPS, or when the server caught up after a stall, a legal move read as too fast. The allowed distance now follows the wearer's move packets, like vanilla's move check, counted only up to the real time that passed, so extra packets buy no speed. Thanks @snipernumber3.

## [2.0.0] - 2026-11-09

Actuators: two more arms for the Rig, one for each hand. The four Tentacles carry you as before; the Actuators mine, build, fight, Grab and Throw out to the Rig's Reach.

### Added

- **Actuator**, a new item for both tiers. Sequenced Assembly on a Piston Extension Pole: deploy Brass Casing, press, deploy Sticker, 2 loops.
- **Fitting**: put a Rig between two Actuators in one row of a crafting table. It keeps its Air, enchantments, Rig Settings and damage and becomes a Copper or Netherite **Octo Rig**. Or build one in one go on Mechanical Crafters: the Rig recipe with an Actuator on each side. Fitting is optional and permanent. A Copper Octo Rig smithed to Netherite stays fitted.
- Each Actuator is a hand. The one on your main-arm side carries the main-hand item, the other the off-hand item, and each does what its hand does, in first and third person. They work wherever your hands work: Stowed or Active, on a Ride, gliding, Falling.
- **Far Use**: mine, place, hit and use out to the Rig's Reach (Copper 5, Netherite 7, more with Far Reach), on blocks, mobs and players. Never through a wall. Drops and XP of a block mined this way fly to you. With an Extendo Grip in hand the Rig adds no reach.
- Rig Settings row **Far Use** on an Octo Rig: Always (default), Rig out only, or Off.
- **Dual Wield**: two tools that both suit the block mine at the sum of their speeds, and both wear. An off-hand tool also works alone. An off-hand weapon adds its damage to your hit.
- **Grab**, key `V` (rebindable; Curios keeps `G`): an Actuator with an empty hand takes the mob you look at and holds it at your side. A held mob is Pinned: no attacks, no Creeper fuse. Your other hand keeps working. You can shear, milk, name, saddle or leash a held mob.
- Light mobs (zombie, villager, cow, spider) take one Actuator. Heavy ones (Iron Golem, Ravager, Horse, Enderman, Ghast) take both, need both hands empty and slow you to 60%.
- Never Grabbed: players, bosses (Ender Dragon, Wither, Warden, anything over 100 max health, the `tentaworks:bosses` tag), anything riding or ridden, another player's pet unless the server allows it. Claim and protection mods are asked first. A refused Grab says why on the action bar.
- **Set Down**: tap `V` and the mob is put where you look. An outline shows the spot, red when it does not fit. Looking at a seat, boat or minecart puts it in. A mob that was set down stays calm for one second.
- Hold `V` and turn the mouse wheel to move what you hold nearer or farther.
- **Throw**: attack while holding. With an item in the main hand, hold `V` and attack. A thrown mob takes fall damage and **Impact** damage when it hits a block or another mob, which is hurt too. Impact counts as your damage and hurts players only where PvP allows.
- A hold lasts through Stowing, Setting Down, Rides, gliding and Falling. It ends on Crawl, death, logout, taking the Rig off, a dimension change, or when the Air runs out.
- **1.21.1 only:** **Ship Grab**: with Sable and both hands empty, `V` on a Ship block takes the whole Ship in both Actuators. It follows your view; set it down or throw it like a mob. The Ship must be scale 1, within the tier's mass cap and have nobody else on board. A Ship that is all that holds you up is refused.
- Crosshair marks in first person: which Actuator will reach, both for Dual Wield, hollow when just out of Reach, red without Air. An open claw shows a mob you can Grab, a glyph shows why not. A holding Actuator's bracket closes, and with `V` down a small gauge shows the hold distance. Key hints show on the first two holds of a game session.
- **Deft**, a fifth Rig enchantment (one level): the Actuators use no Air. It does nothing on a Rig without Actuators.
- Advancements: Fitted, Special Delivery, Hang In There, Spider, Man, The Power of the Sun, Intelligence Is a Privilege.
- **1.21.1 only:** Advancement Fridge Mover (Grab a Ship and set it down 50 blocks away), shown only with Sable.
- Client config: `firstPerson.limbOpacity` (how solid Tentacles and Actuators are drawn in first person, 1.0 down to 0.1), `hud.crosshair` and `hud.hints` (turn the marks and the key hints off).

### Changed

- A Rig holds twice the Air of the Backtank it was made from, Capacity included.
- Actuators use a little Air. Copper Rig: 0.5 per Far Use or Dual Wield action, 1 per 2 seconds of holding, 10 per Throw; Netherite half. With no Air they hang limp and let go.
- Copper Rig Reach 4 → 5 blocks. Its highest Standing Height is 5 too.
- A Rig whose Speed was never set in the Rig Settings now runs at the server's top Speed (8 blocks/s by default) instead of 5.6. A Speed you saved stays.

### Fixed

- No more rubber-banding ("moved wrongly") when going round an outside corner at about 7 blocks/s or faster.
- In creative, opening the inventory no longer Stows an Active Rig (since 1.8.0).

### Compatibility

- Network changed (registrar "11"). 1.8.0 and 2.0.0 clients and servers cannot join each other: update both.
- Existing Rigs keep working and stay unfitted. Fit Actuators whenever you like; nothing on the Rig is lost.
- Existing Rigs keep their Air while the cap doubles, so the Air bar shows half until the Rig is refilled once.
- A server config file written by an older version keeps `reach.copper = 4.0`. Set it to 5.0 by hand to get the new default.
- A Rig with no saved Speed now moves at the server's top Speed (`speed.max`), see Changed.
- New server config keys (defaults): `air.capacityFactor` 2.0, `air.farUse` 0.5, `air.dualWield` 0.5, `air.grab` 40 (ticks per Air), `air.throw` 10, `farUse.players` true, `actuators.dualWield` true, `grab.tamedOthers` false, `grab.throwSpeed.copper` 0.8, `grab.throwSpeed.netherite` 1.2 (blocks per tick), `grab.heavySpeedFactor` 0.6, `impact.damagePerSpeed` 12, `impact.minSpeed` 0.3, `impact.cap` 20.
- New client config keys (defaults): `firstPerson.limbOpacity` 1.0, `hud.crosshair` true, `hud.hints` true.
- **1.21.1 only:** New server config keys for Ship Grab: `grab.ships` true (also needs `ships.grip`), `grab.massCap.copper` 2000, `grab.massCap.netherite` 6000 (Sable mass). Checked on Sable 2.0.0 to 2.0.6. Without Sable, or if a later Sable changes the parts it uses, Ship Grab does nothing and the rest of the mod works.

## [1.8.0] - 2026-11-08

### Added

- Curios support (issue #17): with Curios installed the Rig fits the back slot, so a chestplate stays on. A Rig in the back slot is a Rig and not armor: Air, Reach, Far Reach, Thrift, Swift, Silent Step, Capacity and Rig Settings all work from there, Create's Diving Helmet and air HUD draw on it, Mending repairs it and hits wear it like chest armor; it gives no armor value and Protection, Thorns and the other armor enchantments do nothing there (the tooltip says so). One Rig at a time: the back slot refuses a Rig while the chest slot holds one and the other way round, with an action-bar line; if both get one anyway, the chest Rig wins and the other is inert. A Rig that changes slot or is swapped for another while out Sets Down first. Using a Rig from the hand with a chestplate on puts it into a free back slot; with an empty chest slot it is worn as armor, as before. Elytra in the chest slot and the Rig in the back slot work together: the Elytra never opens while the Rig is deployed. Both Rigs are tagged `curios:back`; players get the slot from the mod's own Curios entity file.
- Server config `curios.backSlot` (default on): off, the back slot refuses Rigs and one already there does nothing, for packs that want to keep the chestplate trade-off. Ignored without Curios.

### Compatibility

- Network changed (registrar "9"): the Rig Settings packet names the slot, so clients and servers must both run this version or newer.
- Curios is optional on both lines: 1.21.1 Curios 9.2.0+ (NeoForge), 1.20.1 Curios 5.3.0+ (Forge). Nothing changes without it.
- Wider dependency ranges, checked by compiling against every release and comparing every mixin target: Create 6.0.4 or newer (was 6.0.10 on 1.21.1, 6.0.8 on 1.20.1), Sable 2.0.0 or newer on 1.21.1 (was 2.0.5; built against 2.0.6 now). Create 6.0.0 to 6.0.3 stay out: they lack the recipe datagen API this mod uses.

## [1.7.1] - 2026-11-07

### Changed

- New textures for the Tentacles and the Rig, 16x16 in Create's palette: shaded copper sleeves with a riveted seam, polished steel rods, bevelled brass collars with bolts, a riveted joint housing with a brass bearing, and a proper brass cogwheel. Netherite is no longer a recolour: blackstone housings, dark steel and crimson fittings like the Netherite Backtank. Pure resource change, a resource pack can still override every file.

### Compatibility

- Network unchanged (registrar "8"). Client-only: an old client or server pairs with the new one.

## [1.7.0] - 2026-11-07

### Fixed

- The game died at startup, without a crash report, next to Forbidden Arcanus (issue #23): both mods replaced the same airborne check in the mining speed, and Mixin lets only one mod replace a call. Octo Arms now wraps the calls it changes (mining speed, sweep attacks, the sneak pose and the client crouch) instead of replacing them, so any mod touching the same code keeps working alongside it.

### Changed

- Ukrainian translation reads more naturally: Reach is now «Дальність», and Create and vanilla names (Backtank, Precision Mechanism, Sculk Sensor, Sculk Shrieker, Warden) match the game. Thanks @Zhenkass.

### Compatibility

- Network unchanged (registrar "8"). Mixin-only change on both sides; an old client or server pairs with the new one.
- **1.20.1 only:** the jar now bundles MixinExtras 0.4.1 (Jar-in-Jar), which the wrapped mixins need; NeoForge ships it on 1.21.1.

## [1.6.0] - 2026-10-26

### Added

- Silent Step, a fourth Rig enchantment (one level, enchanting table and anvil like the others). While the Rig is out the wearer dampens their own vibrations, like an item on wool: Sculk Sensors, Shriekers and Wardens hear neither the Tentacles, nor the wearer's steps, blocks broken or placed and items used; and the Rig plays none of its sounds. Stowed, the wearer walks and sounds as usual.

### Fixed

- Mining while carried ran at a fifth of the speed: vanilla slows anyone not on the ground, and a Held wearer is not. Mining with the Rig out is now as fast as standing (Falling stays slow). The same airborne check also blocked sword sweep attacks while carried; they work again.
- A spectator wearing a Rig could Double Jump: the Rig activated, took the spectator's flight away and the server fought the client over it. Spectators never activate the Rig; switching to spectator with the Rig out stows it, and nothing of a spectator's jumps reaches the Rig.

### Changed

- Smoother Tentacles. A Tentacle that loses its Grip (Stow, a fall, a Grip gone) lets go and swings in over a few ticks instead of snapping to its hanging pose. Activating and catching yourself in the air arc the Tentacles out from the back instead of drawing them straight through the body. A Step to the opposite wall or from floor to ceiling arcs across (the tip used to jump and the pad flip half way). Steps around a corner bow toward the wearer, away from the block.
- Tentacles keep out of walls: an elbow that would sit in a block straightens or bends to the far side; the spare Tentacles hang down in world space, swing after the wearer, and pull in from a wall or the floor instead of going through it.
- The body of a carried wearer turns with the head, smoothly. The shoulders and all four Tentacles used to twitch with the direction of travel and swing across the back on a fast turn; turning on the spot no longer makes the Rig re-step.
- Other players' Rigs animate every Step in full under packet loss (arcs used to freeze, skip or teleport), and a Rig walked into view is drawn deployed, without the unfold and the activation hiss.
- Pads sit inside the edges of a face (no more half off a ledge) and keep their orientation on the face while the wearer turns. Lighting runs along each segment (no brightness flip at a block edge, no black elbow in a wall). In first person the segments near the eye are trimmed instead of blinking out. Level of detail and view culling no longer pop at their boundaries. The sleeve texture no longer crawls as the arm telescopes.

### Compatibility

- Network unchanged (registrar "8"). Silent Step and the mining fix need the new build on both sides: the enchantment is server data (an old client shows its raw id), the mining speed is checked on both, and the Rig sounds are the client's. The Tentacle animation changes are client-only.

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

[Unreleased]: https://github.com/SenDeliver/create-octo-arms/compare/v2.0.0%2Bmc1.21.1...HEAD
[2.0.0]: https://github.com/SenDeliver/create-octo-arms/compare/v1.8.0%2Bmc1.21.1...v2.0.0%2Bmc1.21.1
[1.8.0]: https://github.com/SenDeliver/create-octo-arms/compare/v1.7.1%2Bmc1.21.1...v1.8.0%2Bmc1.21.1
[1.7.1]: https://github.com/SenDeliver/create-octo-arms/compare/v1.7.0%2Bmc1.21.1...v1.7.1%2Bmc1.21.1
[1.7.0]: https://github.com/SenDeliver/create-octo-arms/compare/v1.6.0%2Bmc1.21.1...v1.7.0%2Bmc1.21.1
[1.6.0]: https://github.com/SenDeliver/create-octo-arms/compare/v1.5.1%2Bmc1.21.1...v1.6.0%2Bmc1.21.1
[1.5.1]: https://github.com/SenDeliver/create-octo-arms/compare/v1.5.0...v1.5.1%2Bmc1.21.1
[1.5.0]: https://github.com/SenDeliver/create-octo-arms/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/SenDeliver/create-octo-arms/compare/v1.3.1...v1.4.0
[1.3.1]: https://github.com/SenDeliver/create-octo-arms/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/SenDeliver/create-octo-arms/compare/v1.2.1...v1.3.0
[1.2.1]: https://github.com/SenDeliver/create-octo-arms/compare/v1.2.0...v1.2.1
[1.1.0]: https://github.com/SenDeliver/create-octo-arms/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/SenDeliver/create-octo-arms/releases/tag/v1.0.0
