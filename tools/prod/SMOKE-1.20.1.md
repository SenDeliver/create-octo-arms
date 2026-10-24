# Release jar smoke, 1.20.1 build

The harness runs Mojang names in a dev environment. SRG, refmap and access transformer mistakes only show with the reobf jar on a real Forge install. Run this before every `+mc1.20.1` release.

## 1. Jar checks

`./gradlew build`, then on `build/libs/octo-arms-<version>+mc1.20.1.jar`:

- [ ] contains `tentaworks.refmap.json`, `tentaworks.mixins.json`, `META-INF/mods.toml`, `META-INF/accesstransformer.cfg`, `pack.mcmeta` (`pack_format` 15)
- [ ] `unzip -p <jar> META-INF/MANIFEST.MF` has `MixinConfigs: tentaworks.mixins.json`
- [ ] plural data folders only: `recipes/`, `loot_tables/`, `advancements/`, `tags/items/`, `tags/blocks/`
- [ ] `unzip -l <jar> | grep -i neoforge` is empty
- [ ] refmap maps every vanilla target to `m_…`/`f_…` names (Create targets are `remap=false` and not in it)

## 2. Dedicated server (Forge 47, Java 17)

Server dir `run-prod/server` (gitignored): `java -jar forge-1.20.1-<47.x>-installer.jar --installServer <dir>`, then `PACK=<dir with only the Create 6.0.8 jar> tools/prod/setup_prod.sh --no-devtools`. Add `-Dmixin.debug.verbose=true` to `run-prod/server/user_jvm_args.txt` to log every applied mixin. `tools/prod/server.sh start|stop`.

- [ ] log has `Mixing <name> from tentaworks.mixins.json` for the 5 common mixins: `LivingEntitySneakMixin`, `PlayerSneakMixin`, `ServerGamePacketListenerImplAccessor`, `BacktankBlockEntityAccessor`, `BacktankBlockEntityMixin`
- [ ] no `Mixin apply failed`, `RuntimeException: Mixin transformation`, `IllegalAccessError`, `NoSuchMethodError`, `NoSuchFieldError`
- [ ] access transformer: over RCON `forceload add 0 0`, `setblock 0 -59 0 tentaworks:copper_tentacle_rig{Air:777}`, `data get block 0 -59 0` shows `id: "create:backtank"`, `Air: 777`, `VanillaTag: {}`; no "invalid for block" warning; Air still there after a restart

`runClientProd` cannot join here: it runs the dev (Mojang-named) game, so SRG jars fail to load (Flywheel's mixin fails first). Use a real launcher client.

## 3. Client log (real Forge 47 client, mods: Create 6.0.8 + the jar)

- [ ] client log has `Mixing <name> from tentaworks.mixins.json` for all 10 mixins (the 5 above plus `LivingEntityWalkMixin`, `LocalPlayerSneakMixin`, `KineticBlockEntityGoggleMixin`, `EntityRendererMixin`, `BacktankRendererMixin`); needs `-Dmixin.debug.verbose=true` in the launcher JVM args
- [ ] joins the server, no disconnect, no client `ERROR` besides Create's `Error loading class: xaero/...` warning

## 4. Manual pass in game (owner)

Survival unless noted. Watch the server console for kicks (`Flying is not enabled`, `moved too quickly` away from teleports).

- [ ] Double Jump activates the Rig; Tentacles render on the back (third person) and in first person
- [ ] climb a wall, hang under a ceiling, climb a pole (jungle log), a fluid tank, a slab edge
- [ ] Shift: sneak-use works with the Rig out (wrench a block, place on a chest), no crouch while Held
- [ ] Double Jump again: Setting Down, then Stowed
- [ ] ride a boat and a horse with the Rig on: Rig stows, no kick
- [ ] enchanting table offers Far Reach / Thrift / Swift on a Rig; anvil applies them and Capacity
- [ ] Sequenced Assembly and Mechanical Crafting recipes craft a Rig (JEI shows both)
- [ ] goggles on a placed Rig Block show Air; Display Link source `Rig Air` on a board
- [ ] Ponder scene opens from the Rig and the Rig Block (hold W)
- [ ] Rig Settings opens with its keybind (worn Rig), changes stick after relog; both keys (Rig Settings, Toggle) are in Controls under Octo Arms
- [ ] Rig Block: place, refill under a motor, pick up keeps Air, enchants and settings
- [ ] Air drains while Active; with no Air left the Rig drops to Crawl (slow, down only to the nearest floor, then Stows); an empty Rig does not activate
- [ ] long fall: the Rig catches, no fall damage, no kick for flying after a long carry
- [ ] smithing: Copper Rig + netherite ingot gives a Netherite Rig with Air, enchants, settings and name kept

## Runs

| Version | Sections 1-3 | Section 4 |
|---|---|---|
| 1.5.0+mc1.20.1 | pass: server on Forge 47.4.27 and 47.1.33, client on 47.4.27 (10/10 mixins, AT Air 777/1234 kept over a restart) | open |
