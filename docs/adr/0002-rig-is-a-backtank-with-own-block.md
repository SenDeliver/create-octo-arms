# The Rig is a Create Backtank subclass with its own placeable block

A Tentacle Rig is a `BacktankItem` subclass tagged as a pressurized air source, so Create's diving gear, Capacity enchantment and back rendering work unchanged. It has its own placeable block, registered into Create's backtank block-entity type, because Create refills air only while a backtank is placed under a spinning shaft and a Rig placed through Create's own placeable would be picked up as a plain Backtank.

We rejected an accessories-slot item (extra dependency, loses Create's air plumbing) and a "click a placed backtank to transfer air" refill (new rule to learn, and the Rig still needs a block to be picked up with its air).

## Consequences

- Placed Rigs are saved under Octo Arms block ids: frozen once worlds contain them.
- Crafting from a Backtank does not keep its air; a new Rig starts empty.
- Create draws copper cogs on any non-Create backtank block; Netherite Rig needs its own renderer tweak.
