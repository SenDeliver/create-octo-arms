---
status: accepted
---
# Acting on the world is done by two fitted Actuators, not by the walking Tentacles

The first 2.0 design (branch `v2`, never released) let two of the four Tentacles leave their Grips and Work or Grab, driven by an Active Limb key, two Claw Slots and a Mode the HUD derived from them. In play the controls were not intuitive: which limb a click drives, what the Claw holds and why a click grabbed instead of placed all had to be learned. We decided that the four Tentacles only carry the wearer, exactly as in 1.x, and that acting on the world belongs to two separate Actuators fitted to a finished Rig. They need no selection: the Right Actuator extends whatever the main hand does to the Rig's Reach, the Left Actuator Grabs on one key with an empty hand. They work Stowed as well as Active. The Rig item and its recipe are untouched, so every 1.x Rig keeps working and Fitting is a choice.

## Considered Options

- Free Tentacles with Modes (the `v2` branch): fewer limbs to draw, but the control ceremony above, and the legs went missing whenever the arms were busy.
- Grab by gesture instead of a key: hold use cannot grab mobs whose use already does something (villagers, horses); sneak and use fights Shift, which moves an Active wearer down and must keep doing so.

## Consequences

- Actuators are a component on the Rig, not a new Rig item; a Rig without them is complete. Three recipes: the Rig as before, the Rig recipe with an Actuator on each side, and Fitting a finished Rig at a crafting table while keeping its Air, enchantments, settings and damage.
- One new key (Grab). Far Use rides vanilla's reach attributes, so mining, placing, hitting and item use at Reach cost no reimplementation on either Minecraft line.
- A held mob is Pinned (no AI) so a Grab is always safe for the wearer; this is the price of a toy-safe Grab and is deliberate.
- Server stays the authority for Grab, Throw and Impact as ADR 0007 of the old branch argued; carrying the wearer stays client-driven (ADR 0001).
- The old branch is a parts bin only (Claw model, advancement set, Ship Grab checks); its ADRs 0004, 0005 and 0007 are void.
