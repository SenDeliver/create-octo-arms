# Octo Arms

Create: Octo Arms (mod id `tentaworks`, from its working name Tentaworks) is a Create addon: a worn Tentacle Rig, built from a Backtank, that carries the player on pneumatic tentacles across floors, walls and ceilings, Doc Ock style. Depends only on Create; Sable (optional) adds Ships.

## Language

**Tentacle Rig**:
A Backtank that has been fitted with four Tentacles and a controller. Worn in the chest slot, it still holds and supplies air like the Backtank it was made from. Comes in Copper and Netherite.
_Avoid_: harness, backpack, octopus pack, tentacle backtank

**Tentacle**:
One of the four pneumatic arms of a Rig. Also the crafted item that becomes one. A Tentacle reaches out to a Grip and holds the wearer there.
_Avoid_: leg, arm, limb, appendage

**Air**:
The Backtank's air, the only fuel of a Rig. Idle holding drains a trickle, moving more, climbing up the most; Netherite drains half. Refilled only as a placed Rig Block. Creative players use no Air and need none.
_Avoid_: pressure, fuel, charge, energy

**Grip**:
A spot on any solid block face (floor, wall or ceiling, including the underside of a ledge) that a Tentacle tip holds on to. The only place a Tentacle meets the world. Tentacles never push, hit or damage anything (fighting with them is a v2 idea). A Grip on a Ship block is stored in that Ship's plot coordinates; the Ship is implied by the block, so the Grip stays put while the Ship moves.
_Avoid_: foothold, anchor, foot, contact

**Held**:
The wearer's state while at least two Tentacles have a Grip. Only Held positions are reachable; with fewer Grips the Rig sags the wearer toward the nearest spot where two Grips exist. The wearer's hands stay free the whole time: mining, placing, fighting and item use all work while Active.
_Avoid_: supported, anchored, stable

**Reach**:
How far from the wearer a Tentacle can place a Grip. Fixed per tier (Copper shorter, Netherite longer); it is the whole difference between the tiers besides Air use.
_Avoid_: range, length

**Active**:
The Rig is carrying the wearer: Tentacles are gripping and the wearer's own walking, jumping, gravity and fall damage are replaced by Tentacle movement. Toggled by a Double Jump.
_Avoid_: on, deployed, engaged

**Stowed**:
The Rig is worn but not Active: Tentacles are folded against the wearer's back and the wearer moves normally.
_Avoid_: off, idle, retracted, inactive

**Double Jump**:
Pressing jump twice quickly. The on/off of the Rig: activates a Stowed Rig (also mid-air, where it catches the wearer once a Grip is in Reach, so no fall damage); on an Active Rig it starts Setting Down.
_Avoid_: toggle key, activate key

**Setting Down**:
"Just put me down": an Active Rig lowers the wearer while Held. Floor reached → wearer on their feet, Stowed. No floor before Grips run out (hanging under a ceiling, over a pit) → the Rig lets go at the lowest Held point and Stows; the wearer falls with normal fall damage. The only way a Rig stops carrying the wearer by choice.
_Avoid_: dismount, release, drop

**Standing Height**:
How far above the floor the Rig lifts the wearer on activation and holds them when no height key is pressed. Chosen by the player in the Rig Settings, up to the Reach.
_Avoid_: hover height, ride height, default height

**Rig Settings**:
The screen where the player tunes Standing Height, Speed and Activation Mode (Double Jump or key only), within limits set by the Server Config. Opened by sneak-using a Rig in hand, or by a key while wearing it.
_Avoid_: config, GUI, menu

**Rig Block**:
A Rig placed in the world. It is the Rig's only refill: like any Backtank it fills while a shaft spins above it, and it is picked up again as the same Rig with its Air.
_Avoid_: tank block, charger, filler

**Double Jump** (creative):
A worn Rig takes the Double Jump in creative too. Creative flight is reached by a third jump press while Active or by the fly key.

**Falling**:
What happens when a Held wearer loses Grips (blocks broken under them) and no new Grip is in Reach: vanilla gravity and fall damage return until a Grip is found again. Taking the Rig off, logging out or changing dimension Stows it at once.
_Avoid_: drop, release

**Crawl**:
The Rig's behaviour with no Air left: Grips hold, but the wearer can only move slowly, downward only, toward the nearest floor, after which the Rig Stows itself. No floor in Reach → it holds at the lowest Held point; a Double Jump lets go (as Setting Down). A Crawling wearer who loses Grips re-grabs into Crawl.
_Avoid_: limp, emergency mode, low power

**Idle**:
An Active Rig with no movement input: the gripping Tentacles hold the wearer still at the current spot; the spare ones hang free.
_Avoid_: hover, rest

**Step**:
One Tentacle letting go of its Grip and arcing to a new one while the others hold. Tentacles Step alone or in diagonal pairs (never more than two at once, never leaving fewer than two holding), so movement reads as a crawling gait.
_Avoid_: re-grip, swing

## Movement

**Heading**:
The direction the wearer travels while Active: the camera's full look direction (yaw and pitch) for forward, strafes sideways, with Space and Shift adding straight up and down in the world. The Rig always moves the wearer in the Heading as far as Grips allow.
_Avoid_: direction, input vector

**Hang**:
Being Active with every Grip above or beside the wearer, for example under a ledge or off a cliff edge. Nothing special: the Rig works the same in any orientation, and Hanging needs no more than normal Air to hold.
_Avoid_: ceiling mode, climb mode

**Tiers**:
Copper Tentacle Rig and Netherite Tentacle Rig, made from the matching Backtank. They differ only in Reach and Air use.

## Ships

**Ship**:
A Sable sub-level: a group of blocks that moves and turns as one body (Create Aeronautics builds its Ships on Sable). Its blocks are Grips like world blocks, if Sable is installed, the server config `ships.grip` is on and the Ship's scale is 1.
_Avoid_: contraption, vehicle, sub-level (in player text)

**Frame**:
Where a point lives: the world, or one Ship. Ship blocks sit at far-away plot coordinates of the same level; the Ship's pose maps them into the world. A Grip on a Ship is kept in the Ship's Frame.
_Avoid_: space, coordinate system

**Carry Ship**:
The Ship that holds most of the wearer's valid Grips (at least two). Each tick the Rig moves the wearer with it, rotation included. The wearer's own move (speed checks, Air) is the move minus the carry. When Sable already moves the wearer (standing on its deck), the Rig adds no carry of its own.
_Avoid_: parent ship, host, anchor ship

## Enchanting

**Rig Enchantments**:
A Rig takes every vanilla chest-armor enchantment (Protection line, Unbreaking, Mending, Thorns), Create's Capacity (more Air), and three of its own: Far Reach (more Reach), Thrift (less Air per move) and Swift (faster movement).
A Rig wears like armor but never breaks (elytra-style): at 1 durability it can't be Activated (an Active one Sets Down) until repaired by anvil or Mending; Air, enchantments and Rig Settings are never lost.
_Avoid_: upgrades, modifiers

**Server Config**:
Admin-set caps per tier: Reach, top Speed, Air costs and enchantment strengths; also whether Ships are Grips (`ships.grip`). Rig Settings are clamped to them.
_Avoid_: balance file, limits
