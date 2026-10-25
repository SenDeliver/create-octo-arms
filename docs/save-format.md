# Octo Arms save format (frozen in phase 1, extended in phases 2, 3, 4, 5, 6 and 7)

These strings are stored in worlds or sent between client and server. Renaming any of them breaks existing saves or client/server compatibility.
Code constants: `src/main/java/dev/tentaworks/TentaworksIds.java`.

## Registry names

| Kind | ID | Status |
|---|---|---|
| Item (worn Rig, chest slot) | `tentaworks:copper_tentacle_rig`, `tentaworks:netherite_tentacle_rig` | registered (phase 1). Create `BacktankItem` subclasses. |
| Block (placed Rig = Rig Block) | `tentaworks:copper_tentacle_rig`, `tentaworks:netherite_tentacle_rig` | registered (phase 1). Same id as the item, like Create's backtanks. |
| Item (placeable block item) | `tentaworks:copper_tentacle_rig_placeable`, `tentaworks:netherite_tentacle_rig_placeable` | registered (phase 1). Internal `BacktankBlockItem` the Rig item places through; `Block.asItem()` of the Rig Block. Never in the creative tab, but `/give` can make one. |
| Block entity type | `create:backtank` (Create's) | Rig Blocks are added to it with `BlockEntityTypeAddBlocksEvent`; there is no Octo Arms block entity type. |

| Enchantment (data-driven, `data/tentaworks/enchantment/*.json`) | `tentaworks:far_reach`, `tentaworks:thrift`, `tentaworks:swift`, `tentaworks:silent_step` (1.6) | phase 4. Stored by id in every enchanted Rig's `minecraft:enchantments` (and books' `minecraft:stored_enchantments`). |
| Item (crafting ingredient) | `tentaworks:tentacle` | phase 5. Plain item, stacks to 64. |
| Item (Sequenced Assembly transitional) | `tentaworks:incomplete_tentacle` | phase 5. Create `SequencedAssemblyItem` (stacks to 1); its progress is Create's `create:sequenced_assembly` component. Never in the creative tab. |
| Recipe | `tentaworks:sequenced_assembly/tentacle`, `tentaworks:mechanical_crafting/copper_tentacle_rig`, `tentaworks:mechanical_crafting/netherite_tentacle_rig`, `tentaworks:smithing/netherite_tentacle_rig` | phase 5 (datagen). Recipe ids sit in each player's known-recipe list; a renamed id is dropped silently (only the recipe book forgets it). |
| Advancement | `tentaworks:doc_rig` ("Doc's Rig", parent `create:backtank`), `tentaworks:recipes/combat/smithing/netherite_tentacle_rig` (recipe unlock) | phase 5. Saved per player in `world/advancements/<uuid>.json`: renaming loses the progress. |
| Display Source | `tentaworks:rig_air` | phase 7. Create Display Link source on both Rig Blocks; saved in every linked Display Link's NBT (`Source.Id`, plus its `Mode` 0 Air / 1 percent / 2 progress bar and `Label`). Renaming it unlinks the source (the link shows nothing until re-set). |
| Item tag | `tentaworks:enchantable/tentacle_rig` | phase 4. Both Rigs; `supported_items` and `primary_items` of the four enchantments. Datapacks may add to it. |

The spike item `tentaworks:tentacle_rig` never shipped and is gone.

Phase 4 also adds the enchantments (Silent Step with 1.6) to the vanilla enchantment tags `minecraft:non_treasure` and `minecraft:in_enchanting_table` (so also tradeable and random loot through `#minecraft:non_treasure`), and gives both Rigs durability: Copper 112, Netherite 592 (`ArmorItem.Type.CHESTPLATE.getDurability(7 / 37)`, as Create's diving helmet and boots). Damage is the vanilla `minecraft:damage` component; a placed Rig Block keeps it in Create's `Components` patch.
Phase 5: a Rig never breaks. Wear stops at max − 1 (damage 111 / 591); a stored damage above that (old item, `/give`) reads as max − 1. At that point the Rig is worn out: it cannot be Activated until repaired (anvil with a copper / netherite ingot, or Mending). No new component: "worn out" is derived from `minecraft:damage`.

Not saved in worlds (safe to change): Ponder scene `tentaworks:tentacle_rig` and its schematic `assets/tentaworks/ponder/tentacle_rig.nbt`, Ponder tag `tentaworks:tentacle_rigs`, creative tab `tentaworks:main`, lang keys, models, textures, the transient attribute modifier `tentaworks:rig_float` (never saved: added with `addTransientModifier`).
Key mappings `key.tentaworks.settings` and `key.tentaworks.toggle` (category `key.categories.tentaworks`, both unbound by default) are saved in each player's `options.txt`: renaming one only loses that player's binding.

## Block states

Rig Block: `facing` (north/south/west/east; Create `HorizontalKineticBlock`) and `waterlogged` (true/false). Both come from Create's `BacktankBlock`.

Phase 6 adds `arms` (true/false, default false): a placed Rig (`getStateForPlacement`) is `arms=true` and shows the folded Tentacles; the default state (`arms=false`, plain Create backtank model) is what Create's worn Backtank layer draws on the player, where the Tentacle layer draws the arms itself. Blocks saved before phase 6 load as `arms=false` (plain tank look, works the same); breaking and placing again shows the arms. The property name and values are frozen.

## Data components

A Rig item stores its Air in Create's `create:banktank_air` (int, Create's own typo) and its enchantments, name and other components the vanilla way.

| Component | Codec (saved) | Added |
|---|---|---|
| `tentaworks:rig_settings` | compound: `standing_height` float (blocks, default 1.5), `speed` float (blocks/s, default 5.6), `activation` string `double_jump` \| `key` (default `double_jump`) | phase 3 |

Every field is optional (missing = default), so a Rig without the component behaves exactly like phase 2. Values are clamped on write and on read: `standing_height` 0 … the Rig's Reach, `speed` min(4.3, `speed.max`) … `speed.max`, both rounded to 0.1. The field names and the two `activation` strings are frozen.
The component is network-synced (stream codec: float, float, UTF-8 string). A placed Rig Block keeps it in Create's `Components` patch, so place/pick up keeps the settings.

## Block entity NBT (Create `BacktankBlockEntity`, keys owned by Create)

| Key | Type | Meaning |
|---|---|---|
| `Air` | int | stored Air |
| `Timer` | int | ticks to the next refill step |
| `CapacityEnchantment` | int | Capacity level copied from the placed item |
| `CustomName` | string (JSON text) | item custom name |
| `Components` | compound | full component patch of the placed item; re-applied on pickup and on drops (minus Air, which is copied separately) |

Phase 7 adds no keys: a placed Rig Block only syncs this NBT to clients more often (every 10 ticks while its Air changes) so goggles show Air rising. The network protocol is unchanged (registrar stays `6`).

## Network (play phase, registrar version `8`)

| Payload | Direction | Fields |
|---|---|---|
| `tentaworks:rig_motion` | client → server | `RigState` enum ordinal, motion `Vec3`, Tentacles: 4 fixed slots (front left, front right, back left, back right), each a present flag, then Grip (point `Vec3`, block pos, face) + Step phase byte (255 = planted). A Grip on a Sable Ship has point and block in the Ship's plot coords; the block names the Ship |
| `tentaworks:rig_state` | server → client | entity id, `RigState` enum ordinal, Tentacles (same 4 slots; slots whose Grip the server rejected are empty) |
| `tentaworks:rig_settings` | client → server | slot var-int (`0` main hand, `1` off hand, `2` chest), Rig Settings (stream codec above). The server clamps and writes the component to the Rig in that slot; anything else in the slot is ignored. |

`RigState` order is part of the wire format: `STOWED, ACTIVE, SETTING_DOWN, FALLING, CRAWL`. A change needs a registrar version bump.
Phase 2 appended `CRAWL` and bumped the registrar from `1` to `2`: a phase-1 client cannot join a phase-2 server (NeoForge refuses the channel), and the other way round.
Phase 3 added `tentaworks:rig_settings` and bumped the registrar to `3`: phase-2 and phase-3 clients and servers cannot mix.
Phase 4 kept every payload as is but bumped the registrar to `4`: Reach and Speed now depend on enchantments on both sides, and a phase-3 server would reject a phase-4 client's Far Reach Grips (forced Falling mid-hang).
Phase 5 kept every payload as is but bumped the registrar to `5`: the worn-out rule must match on both sides (a phase-4 client would keep claiming Active on a worn-out Rig and be forced to Setting Down every tick). The new items and recipes would block mixed versions anyway (registry sync).
Phase 6 changed both Rig payloads to the fixed 4-slot Tentacles with a Step phase per slot (other clients animate each Tentacle by slot) and bumped the registrar to `6`: phase-5 and phase-6 clients and servers cannot mix.
Phase 8 kept every payload as is but bumped the registrar to `7` (server packet handling changed); 1.0.0 shipped with `7`.
1.1.0 keeps every payload byte-identical but bumps the registrar to `8`: a Grip point and block may now be in a Ship's plot coords, and the `rig_motion` motion is the Rig's own move without the carry Ship's. 1.0 clients cannot join a 1.1 server, and the other way round.

## Server Config (`tentaworks-server.toml`, per world in `serverconfig/`)

| Key | Default | Range |
|---|---|---|
| `reach.copper` | 4.0 | 1–16 |
| `reach.netherite` | 7.0 | 1–16 |
| `speed.max` | 8.0 | 1–20 |
| `air.idle` | 80.0 | 1–72000 (ticks per 1 Air, Copper, held still) |
| `air.move` | 20.0 | 1–72000 (ticks per 1 Air, Copper, moving level or down, Setting Down) |
| `air.climb` | 10.0 | 1–72000 (ticks per 1 Air, Copper, net +Y) |
| `air.netheriteFactor` | 0.5 | 0–4 (Netherite Air use relative to Copper) |
| `crawl.speed` | 1.0 | 0.1–8 (blocks per second, Crawl descent) |
| `enchant.farReachPerLevel` | 1.0 | 0–8 (blocks of Reach per Far Reach level; Reach with Far Reach is capped at 16) |
| `enchant.thriftPerLevel` | 0.15 | 0–0.3 (Air use cut per Thrift level; the total cut never exceeds 0.9) |
| `enchant.swiftPerLevel` | 0.15 | 0–1 (Speed and top Speed gain per Swift level) |
| `ships.grip` | true | true/false (1.1.0: Rigs grip Sable Ships; off = Ship blocks are never Grips. No effect without Sable) |

Enchantment levels above an enchantment's `max_level` (e.g. from `/give`) count as the max level.

`speed.max` also caps the Speed in every player's Rig Settings, and the Reach caps Standing Height. No client config: the design lists no client-only options.

Renaming a key only resets its value to the default.
