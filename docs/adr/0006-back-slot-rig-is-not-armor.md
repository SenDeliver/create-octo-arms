# A Rig in the Curios back slot is a Rig, not armor

Players ask to wear their chest plate and the arms at once (issue #17). With Curios installed the Rig fits the back slot. The Rig is a Backtank and a chest armor item with armor value and armor enchantments, so a copy in the back slot would stack armor with the chest plate. We decided that a Back Slot Rig keeps everything that makes it a Rig (Air, Reach, the four Rig enchantments, Capacity, Rig Settings, durability wear from hits so the "1 durability can't Rise" rule holds) and nothing that makes it armor: no armor value, Protection and Thorns on it do nothing there. One Rig at a time: the back slot refuses a Rig while the chest slot holds one and the chest slot refuses a Rig while the back slot holds one; if both get one anyway (commands, dispensers, other mods), the chest Rig is worn and the Back Slot Rig does nothing. A Rig whose worn stack or slot changes mid-use Sets Down first. The server config `curios.backSlot` (default on) lets a pack keep the chest-plate trade-off: off, the back slot refuses Rigs and one already there does nothing.

Using a Rig from the hand follows the slots: an empty chest slot takes it as armor does, a chest plate stays on and the Rig goes to a free back slot when Curios and the config allow, a Rig worn anywhere refuses with an action-bar line, otherwise vanilla's armor swap.

ADR 0002 rejected an accessories-slot item in 1.0 because the Rig had to be a Backtank; this does not reopen that: the item stays a Backtank, the back slot is only a second place to wear it.

## Considered Options

- Full twin of the chest slot: double armor, clearly too strong.
- Rig only, no durability wear in the back slot: an unbreakable Rig that never needs Mending, and two repair stories for one item.
- An Air or Reach penalty for the back slot: tuning and confusion for little gain; the config toggle is enough.

## Consequences

- Curios is an optional dependency on both Minecraft lines; Create reads Backtank Air from armor slots and its backtank supplier hook, and Create's own Curios compat already registers curio stacks tagged as Air sources there, so the Back Slot Rig needs no extra code for Air. Curios mends worn curios on XP pickup, so Mending needs none either.
- Curios never wears curio stacks: a mixin at the head of `Player.hurtArmor` wears the Back Slot Rig with the chest-armor amount and gate.
- Rendering splits: chest slot draws the Rig as the chest armor, the Curios renderer draws a Back Slot Rig (tank and Tentacles) over the chest armor.
- The enchanting table and anvil still allow Protection on a Rig; the tooltip says when an enchantment is idle because of the slot.
- Elytra in the chest slot with a Rig in the back slot is allowed: the Elytra never opens while the Rig is deployed, Set Down then glide works.
- The Rig Settings packet names the slot (back = id 3), registrar "9": an old client cannot join a new server.
