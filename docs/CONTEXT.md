# Octo Arms

Create: Octo Arms (mod id `tentaworks`, from its working name Tentaworks) is a Create addon: a worn Tentacle Rig, built from a Backtank, that carries the player on pneumatic tentacles across floors, walls and ceilings, Doc Ock style. Depends only on Create; Sable (optional) adds Ships.

## Language

**Tentacle Rig**:
A Backtank that has been fitted with four Tentacles and a controller. Worn in the chest slot (or the Back Slot), it still holds and supplies air like the Backtank it was made from. Comes in Copper and Netherite.
_Avoid_: harness, backpack, octopus pack, tentacle backtank

**Back Slot**:
The Curios `back` slot, a second place to wear a Rig when Curios is installed (adr/0006). A Back Slot Rig is a Rig and not armor: Air, Reach, Rig enchantments and Rig Settings work, armor value and armor enchantments do not. One Rig at a time; a chest Rig wins over a Back Slot one. The server config `curios.backSlot` can turn it off.
_Avoid_: curio slot, accessory slot, backpack slot

**Tentacle**:
One of the four pneumatic Limbs of a Rig that carry the wearer. Also the crafted item that becomes one. A Tentacle reaches out to a Grip and holds the wearer there; it never holds, hits or moves anything else (that is an Actuator's job).
_Avoid_: leg, arm, appendage

**Air**:
The Backtank's air, the only fuel of a Rig. A Rig holds twice the Air of the Backtank it was made from (Capacity doubled too). Idle holding drains a trickle, moving more, climbing up the most; Netherite drains half. Actuators drink as well, lightly: a sip per Far Use and per Dual Wield action, a trickle per tick of Grab, a gulp per Throw, tuned so a day's work never empties a tank on its own; Deft makes them free. With no Air left the Actuators hang limp and a held target is set down. Refilled only as a placed Rig Block. Creative players use no Air and need none.
_Avoid_: pressure, fuel, charge, energy

**Grip**:
A spot on any solid block surface (floor, wall or ceiling, including the underside of a ledge, the top of a bottom slab or a chest lid at its real height; nothing narrower than half a block) that a Tentacle tip holds on to, in sight of the wearer: the straight line from the body to the spot crosses no block, so nothing behind a wall or under the floor is a Grip. The only place a Tentacle meets the world. Tentacles never push, hit or damage anything; acting on the world is what Actuators do. A Grip on a Ship block is stored in that Ship's plot coordinates; the Ship is implied by the block, so the Grip stays put while the Ship moves.
_Avoid_: foothold, anchor, foot, contact

**Held**:
The wearer's state while at least two Tentacles have a Grip. Only Held positions are reachable; with fewer Grips the Rig sags the wearer toward the nearest spot where two Grips exist. The wearer's hands stay free the whole time: mining, placing, fighting and item use all work while Active.
_Avoid_: supported, anchored, stable

**Reach**:
How far from the wearer a Tentacle can place a Grip, and how far an Actuator can act: Far Use on blocks, mobs and players alike, and Grab. Both are measured from the Rig body, so an Actuator reaches exactly what a Tentacle could Grip, the floor around a standing wearer included. Fixed per tier (Copper 5, Netherite 7 blocks, set in the Server Config), raised by Far Reach.
_Avoid_: range, length, Strike Reach (removed before 2.0 shipped: mobs and blocks share one Reach)

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
How far above the floor the Rig lifts the wearer on activation and holds them when no height key is pressed. Chosen by the player in the Rig Settings, up to the Reach; a fresh Rig stands at 1.5 blocks.
_Avoid_: hover height, ride height, default height

**Rig Settings**:
The screen where the player tunes Standing Height, Speed, Activation Mode (Double Jump or key only) and, on a fitted Rig, Far Use (always, Rig out only, or off), within limits set by the Server Config. A fresh Rig starts at the Server Config's top Speed. Opened by sneak-using a Rig in hand, or by a key while wearing it.
_Avoid_: config, GUI, menu

**Rig Block**:
A Rig placed in the world. It is the Rig's only refill: like any Backtank it fills while a shaft spins above it, and it is picked up again as the same Rig with its Air.
_Avoid_: tank block, charger, filler

**Double Jump** (creative):
A worn Rig takes the Double Jump in creative too. Creative flight is reached by a third jump press while Active or by the fly key.

**Falling**:
What happens when a Held wearer loses Grips (blocks broken under them) and no new Grip is in Reach: vanilla gravity and fall damage return until a Grip is found again. Taking the Rig off, logging out, changing dimension or starting a Ride Stows it at once.
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

## Actuators

**Limb**:
The family word for a Tentacle or an Actuator; used only when a sentence is about both.
_Avoid_: arm, appendage

**Actuator**:
One of the two Limbs that can be fitted to a finished Rig above its Tentacles, to act on the world instead of carrying the wearer. Also the crafted item: one kind for both tiers, since Reach and Air use come from the Rig it sits on. Fitting is optional and permanent: a Rig without Actuators is a complete Rig that only walks, and a Rig from before 2.0 is exactly that until its owner fits a pair. Actuators work wherever the wearer's hands work: Stowed or Active, on a Ride, gliding, Falling, during a Control Hold. They add a single key, Grab: otherwise they do what the wearer's hands already do, farther. Each Actuator stands in for one hand: the one on the wearer's main-arm side is the main-hand Actuator, the other the off-hand Actuator (a left-handed wearer has them the other way round). Each carries its hand's item, in first and third person, and does every action of that hand at its tip, near or far, while the wearer's own arms stay still; an Actuator whose hand is empty can Grab. Not carried: an Extendo Grip, a Potato Cannon, a Zapper and a held map stay in the wearer's own hand.
_Avoid_: arm, hand, claw, grabber, manipulator, functional tentacle, Right Actuator and Left Actuator as roles (the sides follow the main arm)

**Fitting**:
Putting two Actuators on a Rig. Two ways: a finished Rig with an Actuator on each side at a crafting table (the Rig keeps its Air, enchantments, Rig Settings and damage, it only gains the Actuators), or the Rig's own Mechanical Crafting recipe with an Actuator added on each side, which builds a fitted Rig in one go. The plain Rig recipe stays as it is. A fitted Copper Rig upgraded to Netherite stays fitted. The first wear after Fitting unfolds and flexes both Actuators.
_Avoid_: upgrade, install, attach, mount

**Octo Rig**:
A Rig with Actuators fitted, and its item name: Copper Octo Rig, Netherite Octo Rig. The same item as the plain Rig, only named after its Fitting (an anvil name still wins). In prose a fitted Rig is still a Rig; say Octo Rig where the fitted one must be told apart.
_Avoid_: fitted Tentacle Rig as a name, Actuator Rig

**Rest Pose**:
Where the Actuators sit when they have nothing to do, Stowed or Active: rising from the Rig's shoulders and curling up a little above head height, spread wider than the wearer's body so they frame the player and stay out of the third-person view, half-open, breathing slowly with small idle gestures. Any action reaches out from the wearer's side, never through the camera: in first person an Actuator enters the view from the frame's edge and arcs to the target. All Limbs in first person are drawn at an opacity the player chooses in the client config, from solid (default) down to faint, never invisible.
_Avoid_: idle pose, folded, parked

**Far Use**:
An action of either hand (mining, placing, hitting, item use) on a target beyond the wearer's own arm's length but within Reach of the Rig body. That hand's Actuator performs it at its tip with that hand's item, as it performs the hand's near actions too; only a Far Use costs Air. With an Extendo Grip in hand the Rig adds no reach. Blocks, mobs and players are all reached at the full Reach, with any item, weapon or tool. Drops and XP from a block mined by Far Use fly to the wearer. Beyond arm's length the server also checks sight before it accepts the action: it picks again from the wearer's eye along the look, as the crosshair does, and the target is in sight when that pick finds it, or when a straight line from the eye to a visible point of it crosses no other block (the server's look is one tick behind the client's). It refuses the rest. Only an action that did something is a Far Use, pays Air and is shown to other players: a use that consumed the click, a hit that hurt, a mining start that makes progress. The crosshair tells the wearer beforehand: a claw mark on the acting side when an Actuator will reach the target, both sides for Dual Wield, a hollow pair just out of Reach (and a flash when a click lands on nothing for that reason), red when the Actuators hang limp.
_Avoid_: extended reach, long arm, reach mode

**Dual Wield**:
The off-hand item working along with the main-hand one, when both Actuators are free to act: a fitted Rig with Air, no heavy hold, the off-hand Actuator not holding. Mining: two tools that are both effective on the block break it at the sum of their speeds, each with its own Efficiency, and both wear; an off-hand tool that alone is effective does the work, passes the harvest check and takes the wear, the main-hand item none. Loot enchantments stay the main hand's. Melee: the off-hand weapon or tool adds its attack damage and its damage enchantments to the hit, scaled by the attack strength as the main hand's, and wears. Each block broken or hit landed this way costs a sip of Air on top of any Far Use. Item use is not sped up. The Server Config can turn it off.
_Avoid_: dual wielding (as a mode), two-handed, off-hand bonus

**Grab**:
The Grab key (default V, rebindable) on a mob or Ship block under the crosshair within Reach of the Rig body, in sight of it as a Grip must be: a free Actuator takes hold. A light target goes to the off-hand Actuator when the off hand is empty, else to the main-hand Actuator when the main hand is empty; with both hands full the Grab is refused. A heavy target and every Ship take both Actuators and need both hands empty. While one Actuator holds, the other hand works as usual: Far Use, mining, attacks, item use. An item that lands in the hand of a holding Actuator sets the target down. The target is kept at the tip, low on the holding Actuator's side and out of the crosshair, at the hold distance along the look: the Grab key held with the mouse wheel moves it nearer or farther, half a block a notch, between a spot clear of the wearer and Reach (the hotbar does not scroll then; every new Grab starts at the default distance). Use and sneak keep their vanilla meanings, and Shift and Space keep moving the wearer down and up while Active. Any living mob but never a player and never a boss (the Ender Dragon, the Wither, the Warden, the loader's bosses tag collected in `tentaworks:bosses`, anything over 100 max health), nothing riding or ridden, nothing already held by another wearer or leashed to anyone else, and another player's tamed, owned or leashed animal only if the Server Config allows it. A Grab asks the loader's entity-interaction event first, so claim and protection mods can refuse it. A refused Grab says why in one action-bar line (boss, owned, hands full, needs both Actuators, no Air, out of sight), and the crosshair says it beforehand: an open claw with the Grab key's name over a target a free Actuator can take, or a glyph with the reason. Blocks are never Grabbed; a Ship is (see Ship Grab). The held target is swept to the tip with block collision, so it never passes a wall, a pane or a door; a target that cannot follow for a moment is set down at its last free spot. While holding, a tap of the Grab key sets a mob down at the looked spot: on top of the block face under the crosshair, or beside a side face and down to the floor below (at most 3 blocks), where the mob's box fits, keeps clear of the wearer and is within Reach and in sight of the Rig body; the Actuator carries it there in a few ticks. An outline shows the spot, red when it is not valid; with no valid spot the mob is set down where it hangs. The wearer's own clicks pass through a held target, and use stays free, so doors and chests still work with a mob in the other Actuator. Stowing, Setting Down, a Ride, a glide or Falling do not end a hold. The hold ends, from one place, only when the wearer stops being there or the Actuators go limp: Crawl, death, logout, taking the Rig off, the wearer or the target changing dimension, the target gone or out of Reach, or no Air left; the target is set down where it is, awake again. A held mob is Pinned: no attacks, no fuse, no spells or shots, it only squirms. Set down, it stays calm for one second (a Creeper's fuse reset) and is then itself again; Thrown, it wakes at once. A held mob can be sheared, milked, named, saddled or leashed like any mob, and set down over a seat, boat or minecart it takes the seat. While the crosshair rests on something grabbable in Reach, the Actuator that would take it uncurls and points at it. A light target (up to 1.5 blocks wide and 2 tall: zombie, villager, cow, spider) takes one Actuator; a heavy one (anything bigger: Iron Golem, Ravager, Horse, Enderman, Ghast, every Ship) takes both, Far Use pauses, and walking and Rig Speed drop to 60% while it is held (Rides and glides unaffected).
_Avoid_: pick up, lift, carry

**Throw**:
Attack while an Actuator holds something, when the main hand is empty or the Grab key is held: the target is flung along the camera at the tier's throw speed (Netherite faster). With an item in the main hand a plain attack is the item's own (it hits, mines, and may hit the held mob), so the Throw is then the Grab key held with attack. A thrown mob takes normal fall damage and Impact damage when it slams into a block or another mob, which is hurt as well. A thrown Ship flies as a body and Sable settles the collision. Fliers are thrown the same and simply recover.
_Avoid_: launch, yeet (in player text)

**Impact**:
The damage a Thrown mob takes, and deals, when it hits a block or another mob before it stops; grows with the speed at that moment. Dealt once per Throw as the wearer's own damage, so loot, XP and protection rules see the wearer; a player is hurt by Impact only where the server's PvP rule lets the wearer harm them.
_Avoid_: collision damage, crash

## Rides and controls

**Ride**:
Anything other than the Rig that carries the wearer: a seat, a mount, a boat or minecart, a chain conveyor, a zipline, a handle pulling the wearer to it. The Rig carries alone: starting a Ride Stows it at once, nothing Activates it during a Ride (jump belongs to the Ride), and it stays Stowed after.
_Avoid_: vehicle, riding (a Rig on a Ship is carried, not riding), mount (as the general word)

**Control Hold**:
Holding use on a Ship control (steering wheel, throttle lever) while the Rig is out. The Rig keeps its Grips and holds the wearer still while the movement keys work the control; jump stays the Rig's (Double Jump still Sets Down); letting go hands the keys back to the Rig. A handle held normally moves the wearer: that is a Ride. A handle grabbed sneaking drags its Ship instead, reads no keys and is neither: the Rig carries on, and the Ship does not move (Simulated drags only for a wearer standing on something).
_Avoid_: steering mode, piloting

## Movement

**Heading**:
The direction the wearer travels while Active: the camera's full look direction (yaw and pitch) for forward, strafes sideways, with Space and Shift adding straight up and down in the world. The Rig always moves the wearer in the Heading as far as Grips allow. Client config `movement.forward = flat` drops the pitch: forward is level, like walking, and only Space and Shift change the height.
_Avoid_: direction, input vector

**Hang**:
Being Active with every Grip above or beside the wearer, for example under a ledge or off a cliff edge. Nothing special: the Rig works the same in any orientation, and Hanging needs no more than normal Air to hold.
_Avoid_: ceiling mode, climb mode

**Tiers**:
Copper Tentacle Rig and Netherite Tentacle Rig, made from the matching Backtank. They differ in Reach, Air use, throw speed and the Ship mass an Actuator pair can lift.

## Ships

**Ship**:
A Sable sub-level: a group of blocks that moves and turns as one body (Create Aeronautics builds its Ships on Sable). Its blocks are Grips like world blocks, if Sable is installed, the server config `ships.grip` is on and the Ship's scale is 1.
_Avoid_: contraption, vehicle, sub-level (in player text)

**Ship Grab**:
Grab on a Ship (1.21.1 with Sable only): the Grab key with both hands empty on any block of the Ship, and both Actuators take the Ship. Needs the Server Config `grab.ships` and `ships.grip`, a Ship of scale 1 within Reach, Sable mass up to the tier's cap, the clicked face in sight of the wearer, no other player on board, and not the Ship the wearer stands on or the wearer's Carry Ship. While held the held face stays where it was in the wearer's view (no farther than Reach − 0.5 from the eye), so the Ship swings round the wearer as they look about, and its yaw turns with the wearer's while its pitch and roll stay, and Grips on it do not hold the wearer up. The Grab key with the wheel moves the held face nearer or farther along the look; a tap lets the Ship go where it is. Same Throw, release rules and Air as Grab. Without Sable, refused silently.
_Avoid_: lift ship, tow, ship carry, contraption grab

**Frame**:
Where a point lives: the world, or one Ship. Ship blocks sit at far-away plot coordinates of the same level; the Ship's pose maps them into the world. A Grip on a Ship is kept in the Ship's Frame.
_Avoid_: space, coordinate system

**Carry Ship**:
The Ship that holds most of the wearer's valid Grips (at least two). Each tick the Rig moves the wearer with it, rotation included. The wearer's own move (speed checks, Air) is the move minus the carry. When Sable already moves the wearer (standing on its deck), the Rig adds no carry of its own.
_Avoid_: parent ship, host, anchor ship

## Enchanting

**Rig Enchantments**:
A Rig takes every vanilla chest-armor enchantment (Protection line, Unbreaking, Mending, Thorns), Create's Capacity (more Air), and five of its own: Far Reach (more Reach), Thrift (less Air per move), Swift (faster movement), Silent Step (with the Rig out, no vibrations for Sculk Sensors, Shriekers and Wardens, and no Rig sounds) and Deft (one level: the Actuators use no Air; it does nothing on an unfitted Rig and says so in the tooltip).
A Rig wears like armor but never breaks (elytra-style): at 1 durability it can't be Activated (an Active one Sets Down) until repaired by anvil or Mending; Air, enchantments and Rig Settings are never lost.
_Avoid_: upgrades, modifiers

**Server Config**:
Admin-set caps per tier: Reach, top Speed, Air costs, throw speed, Ship mass cap and enchantment strengths; also whether Ships are Grips (`ships.grip`), whether Ships can be Grabbed, whether Far Use may hit players, whether others' pets can be Grabbed and whether Dual Wield works (`actuators.dualWield`). Rig Settings are clamped to them.
_Avoid_: balance file, limits
