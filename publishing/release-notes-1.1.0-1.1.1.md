First public release as **Create: Octo Arms** (mod id `tentaworks`). Covers 1.1.0 and the 1.1.1 bug fix. Install 1.1.1 on both client and server.

### Added (1.1.0)

- Copper and Netherite Tentacle Rig: a Backtank with four pneumatic Tentacles, worn in the chest slot. Copper: Reach 4. Netherite: Reach 7, half the Air use, fire resistant.
- Movement over floors, walls, ceilings, ledges and underwater. Move where you look, `Space` up, `Shift` down; the Rig only goes where at least two Tentacles can hold. Hands stay free.
- Double Jump to activate (also mid-air, catching you without fall damage) and Double Jump again for Setting Down. Hang, Falling and Crawl (slow descent on empty Air).
- Air use from the Backtank: holding, moving and climbing rates; Netherite uses half. The Rig Block (a placed Rig) refills under a spinning shaft and is picked up again with its Air, enchantments and settings.
- Ship Grips (optional, needs Sable): Tentacles grip the blocks of a Ship, parked or moving, and the Rig carries you along with it, also through turns. Create Aeronautics Ships are Sable Ships, so they work too. Server config `ships.grip` turns it off.
- Rig Settings screen: Standing Height, Speed and Activation Mode (Double Jump or key only). Two key binds, unbound by default.
- Enchantments Far Reach (I–III), Thrift (I–III) and Swift (I–II), plus vanilla chest armor enchantments and Create's Capacity.
- Elytra-style durability: a worn-out Rig stops at 1 durability until repaired by anvil (Copper / Netherite Ingot) or Mending. Nothing is lost.
- Recipes: Tentacle by Sequenced Assembly, Rigs by Mechanical Crafting, Netherite Rig also by Smithing from a Copper Rig (keeps Air).
- Stepping gait seen by all players, Sticker and piston sounds.
- Create-style tooltips, a Ponder scene, Engineer's Goggles info on Rig Blocks, a "Rig Air" Display Link source, JEI recipes and the "Doc's Rig" advancement.
- Server config for Reach, top Speed, Air costs, Crawl speed, enchantment strengths and Ship Grips.

### Fixed (1.1.1)

- **Client crash when watching someone climb a Ship.** With Sable Ships, players near a Rig wearer could crash while the wearer climbed a Ship (`InternalError: a fault occurred in an unsafe memory access operation` in `TentacleRenderer`). The crash was random and never hit the wearer. If the watching client had not yet received the Ship's position, the Tentacles were drawn toward the Ship's storage location far outside the world, and the renderer ran out of vertex memory. Such Grips are now skipped until the Ship position arrives, and a Tentacle can never be drawn longer than its reach.
- First-person Rig rendering no longer flushes the shared render buffer mid-frame. Safer with batching mods such as ImmediatelyFast.

### Notes

- Requires Create 6.0.10+ (below 6.1). Sable 2.0.5+ (below 2.1) is optional and only needed for Ships.
- Install on both client and server. 1.1.0 and 1.1.1 can mix, but only a 1.1.1 client is protected from the Ship crash.
- If a Grip is skipped, the client log says `Grip of <player> not drawn`. Please attach that line if you report a Ship-related rendering problem.
- Earlier 1.0.x builds were internal test versions and were not published. 1.0 worlds load as is, but 1.0 clients cannot join a 1.1 server.

[1.1.0]: https://github.com/SenDeliver/create-octo-arms/releases/tag/v1.1.0
[1.1.1]: https://github.com/SenDeliver/create-octo-arms/releases/tag/v1.1.1
