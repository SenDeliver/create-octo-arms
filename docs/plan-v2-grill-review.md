# Actuator design review (2.0, worktree `octo-arms-act`)

Sources read: `docs/CONTEXT.md` (new Actuators section + edits), ADR 0007 (new), ADRs 0001/0002/0003/0006, abandoned `v2` CONTEXT.md + ADR 0007, 1.8.0 code (`RigServer`, `RigClient`, `RigSettings`, `TentaworksConfig`, `TentacleRigItem`, `RigAir`, `TentacleLayer`, `SableShips`), v2 `Grab.java` / `GrabRule.java`, vanilla 1.21.1 sources (`ServerGamePacketListenerImpl`, `ServerPlayerGameMode`, `Player`, `Mob`, `LivingEntity`), Create 6.0.10 `ExtendoGripItem` bytecode, sable-companion 1.6.0 API, Forge 47 `IForgePlayer`.

Facts that anchor several findings:
- Vanilla 1.21.1 server checks only distance for far actions: `ServerPlayerGameMode:143 canInteractWithBlock(pos, 1.0)`, `ServerGamePacketListenerImpl:1133` (use on block) and `:1594 canInteractWithEntity(aabb, 1.0)` (attack/interact). No line-of-sight check anywhere.
- Create's Extendo Grip adds `+3` (one) / `+5` (two) to `BLOCK_INTERACTION_RANGE` only (not entity range), as transient `ADD_VALUE` modifiers.
- sable-companion 1.6.0 `SubLevelAccess` is read-only (`logicalPose`, `lastPose`, `boundingBox`, id, name). v2 Ship Grab drove the ship via Sable internals (`ServerSubLevel`, mass tracker, `RigidBodyHandle`) pinned to `[2.0.5,2.1)`.
- Vanilla `Mob.setNoAi` is persisted as NBT `NoAI`; a no-AI mob skips `travel()` (no gravity, `LivingEntity:2734` just damps velocity).
- `RigSettings.of()` falls back to `DEFAULT` (speed 5.6) when the component is absent; Rigs whose owner never opened Rig Settings carry no component.
- Far Reach is capped at enchantment max level (III) and `farReachPerLevel` default 1, so stock Reach tops out at 7 (Copper) / 10 (Netherite); 16 only with config.

---

## Ranked findings

### 1. Reach attributes hand every hacked client a 7–16 block through-wall nuker/killaura
Scenario: any client on a server with the mod worn, modified or not, sends break/attack/use packets for targets up to 16 blocks away behind walls; the server accepts them because vanilla checks distance only. Legit clients are fine (the crosshair raycast gives sight for free), cheats are not. The abandoned v2 ADR named this exactly ("past the player's own range the server also needs sight from the eye or the body; vanilla checks only the distance"); new ADR 0007 says Far Use "costs no reimplementation" and drops the check.
Hits: Far Use, ADR 0007 consequence 2, v2 anti-cheat rule lost.
Q: Add a server-side sight check (eye to target, Grip sight rule) for any block/entity action beyond vanilla range, and refuse + log like the speed check does? Or accept the exposure on public servers?

### 2. Grab key G collides with Curios' default "open Curios inventory" key
Scenario: 1.8.0 made Curios an optional dependency and most packs have it. Curios' inventory key defaults to `G`. Every player with Curios presses G and gets the Curios screen (or a "conflicting key" red line) instead of a Grab. First-run impression: the headline feature does not work.
Hits: Grab (default key).
Q: Pick another default (V, B, C are free in vanilla + Create), or ship unbound like the two existing Rig keys and teach it in Ponder?

### 3. A held mob sits "ahead of the wearer's eye": it is the crosshair target
Scenario: Grab a cow. The cow's hitbox is now centred on the crosshair. "Use stays free, doors and chests still work": they do not, the pick hits the cow (vanilla picks entities before blocks). Far Use with a block in hand places nothing; a sword swing lands on the Pinned cow (free execution of a helpless target); Throw "along the camera" launches it through its own hold point. Same for a held Ship: a ship in front of the camera blanks the screen.
Hits: Grab, Throw, Far Use, Ship Grab.
Q: Hold the target off-centre (left Actuator, lower-left of view, like a lantern) and exclude the held entity from the wearer's pick and from the wearer's own attacks? Or keep it centred and define that attack = Throw and use/Far Use ignore the held entity?

### 4. Far Use is always on: accidental far hits, outlines at 7–16 blocks, Air drained while Stowed
Scenario: a player wearing a fitted Rig as plain chest armor sprint-jumps across a field, clicks to swing at a zombie, misses, and mines a block 7 blocks away; places a block on a distant wall when meaning to place at their feet; the block outline cursor sits on far blocks constantly. Every one of those "sips" Air, and Air is only refilled by taking the Rig off and placing it under a shaft. The classic Extendo Grip complaint, permanently, with a fuel bill. Fitting is permanent, so no way back.
Hits: Far Use, Actuator ("work whether Stowed or Active"), Fitting ("permanent"), Air.
Q: Add a Rig Setting `Far Use: always / Active only / off` (or hold-G to sleep both Actuators)? Should Far Use on blocks be free of Air (only Grab/Throw drink), or sip only beyond the Grip Reach of the hand?

### 5. Release list for a held target vanished
Scenario: v2 ADR listed when a hold must end "from one place": Stow, Setting Down, Crawl, Falling, logout, death, dimension change, target gone, target out of Reach, target in another level. The new glossary states only "no Air left: set down". Concrete holes: wearer logs out with a Pinned zombie, the zombie keeps NoAI (vanilla persists it) forever; wearer walks through a Nether portal, the held mob follows into the portal on its own (`handleNetherPortal` runs regardless of AI) and is left Pinned in the Nether; wearer throws an ender pearl ("use stays free") and the mob stays 40 blocks behind, still held or orphaned; wearer dies.
Hits: Grab, Falling ("Taking the Rig off, logging out, changing dimension ... Stows it at once" covers the Rig, not the hold), ADR 0007 "parts bin".
Q: Put the v2 release list back into Grab, word for word, plus "the target may not change dimension while held"? Does a Ride start release it too?

### 6. Pinned mob swept through walls; "set down where it is" can be inside a block
Scenario: held mob is 3 blocks in front; the wearer walks into a 1-wide corridor or turns 180 degrees fast (held Iron Golem sweeps a 3-block arc). v2 swept the target "with vanilla block collision, so it never passes a barrier"; the new text has no rule. Either the mob clips into blocks and takes suffocation damage (free kill, also for villagers: grab one through a window, walk away), or the implementer teleports it and it ends up in the wall, and "set down where it is" drops it inside stone.
Hits: Grab, Far Use crosshair, anti-grief (villager theft through a 1-block gap: the crosshair ray passes, the body does not).
Q: Restore "swept with block collision, never passes a barrier; a target blocked for N ticks is set down at its last free spot"? Should the Grab also need the Grip sight rule from the wearer's body (v2 `NO_SIGHT`)?

### 7. Thrown mobs are a PvP and grief vector without a PvP rule
Scenario: PvP off. Wearer grabs a zombie and throws it at another player; "Impact damage ... whatever it hits ... is hurt as well". Who is the damage source? If the wearer: PvP toggle and claim mods can refuse, but then also Piglins/Iron Golems/wolves aggro the wearer for a thrown zombie landing on them. If the thrown mob: PvP bypass, no claim mod sees it. v2 had "a player is thrown only where the PvP rule lets the wearer harm them" for Carry; Impact needs the same.
Hits: Throw, Impact, Grab (no players) made the old rule disappear.
Q: Impact is a damage source `tentaworks:impact` with the wearer as attacker, and Impact on a player is skipped when the wearer may not hurt them (vanilla `canHarmPlayer`)? Same gate for a thrown Ship pushing players?

### 8. Grab and Throw fire no events claim/protection mods can cancel
Scenario: FTB Chunks / Flan / OPAC protect a base. A wearer grabs a villager, a tamed-but-unknown mob or a name-tagged zombie villager from outside the claim boundary (crosshair reaches 7–16 blocks) and walks off. Vanilla interaction events (`PlayerInteractEvent.EntityInteract`, `AttackEntityEvent`) are how those mods refuse; a custom Grab packet bypasses them. Same for throwing mobs into a claim.
Hits: Grab, Server Config ("another player's tamed, owned or leashed animal only if ...") is the only protection.
Q: Fire `PlayerInteractEvent.EntityInteract` (cancellable) on Grab start and route Throw/Impact through a normal damage source so protection mods work for free? Add "name-tagged mobs count as owned"?

### 9. Ship Grab depends on Sable internals and would re-narrow the dependency range 1.8.0 just widened
Scenario: 1.8.0 shipped `Sable 2.0.0+` (changelog, store pages, publish relations). The companion API is read-only; "the Ship follows the wearer", "Sable mass up to the tier's cap" and "attack Throws it" need `ServerSubLevel`, the mass tracker and `RigidBodyHandle` (v2 pinned `[2.0.5,2.1)`). A Sable 2.1 breaks Ship Grab or the whole mod at load.
Hits: Ship Grab, release rules (dependency floors in store text).
Q: Ship Grab behind a runtime check that disables itself silently when the internals are missing (reflection / `Class.forName`), keeping `2.0.0+`? Or ask Sable upstream for a companion `applyVelocity`/`mass` before 2.0.0 and ship Ship Grab in 2.1?

### 10. Grabbing the Ship you hang from: "all Grips" rule lets you drop yourself
Scenario: 3 of 4 Grips are on a Ship, 1 on a dock. The rule refuses only a Ship "that holds all the wearer's Grips", so the Grab is allowed; "Grips on it do not hold the wearer up" leaves 1 Grip, the wearer sags/falls, and the Ship follows the falling wearer at its offset. Feedback loop with Carry Ship.
Hits: Ship Grab, Carry Ship, Held.
Q: Refuse when the Ship is the wearer's Carry Ship (most Grips), or when losing its Grips would leave fewer than two?

### 11. Glossary contradicts itself on how a Ship is released
Scenario: Grab says "the Grab key again sets the target down ... use stays free"; Ship Grab says "Use sets it down, attack Throws it". Two implementers build two different things; a player holding a Ship right-clicks a lever and drops the Ship.
Hits: Grab vs Ship Grab.
Q: Ship Grab uses the same controls as Grab (G again = set down)? Replace "Use sets it down" with "same controls as Grab"?

### 12. "Tiers differ only in Reach and Air use" is no longer true
Scenario: Throw speed and Ship mass cap are per tier; Reach says "it is the whole difference between the tiers besides Air use"; Tiers says "They differ only in Reach and Air use".
Hits: Reach, Tiers, Throw, Ship Grab.
Q: Reword to "Reach, Air use, throw speed and Ship mass cap"? Or derive throw speed from Reach (speed = k × Reach) so the sentence stays true and Far Reach also throws farther?

### 13. Pinned via vanilla NoAI is fragile; held mobs have no gravity and no squirm
Scenario: implementer uses `Mob.setNoAi(true)`. It is written to NBT: server crash or chunk save mid-hold leaves a permanently frozen mob (a "statue" exploit on logout, see 5). A no-AI mob also skips `travel()`: set it down over a gap and it floats until AI returns, and "it only squirms" has no vanilla source of motion. Other mods' entities (Create seats, Mowzie's bosses) treat NoAI as "spawned by command".
Hits: Grab ("Pinned"), Throw ("takes normal fall damage").
Q: Pin with a transient attachment checked by a `Mob.serverAiStep`/goal mixin (never NBT), and give the held mob a scripted wiggle + facing the wearer? Re-enable gravity before Throw/set down in the one release path?

### 14. Netherite melee at 7 blocks (16 with config) trivialises PvE and PvP; no separate entity range
Scenario: Active at Standing Height 1.5, eye at ~3.1 blocks, sword hits at 7. No melee mob can reach back; Ravagers, Brutes, raids and Piglins become target practice, Pinned Grab adds a free "hold still". In PvP a fitted Netherite Rig (mid-game craft) hits a vanilla player from 7 while they need 3. Vanilla gives blocks 4.5 and entities 3 on purpose. Create's Extendo Grip extends blocks only.
Hits: Far Use ("every target counts the same ... at full Reach"), balance.
Q: Separate entity Far Use (e.g. Copper 3.5, Netherite 5, Far Reach does not add) from block Far Use, or a damage falloff beyond arm's length? Add `farUse.players` (default off) so server owners can keep PvP vanilla?

### 15. Extendo Grip stacks on top of the Rig reach; two arm animations
Scenario: Netherite Rig (7) + Far Reach III (10) + dual Extendo Grip (+5) = 15; with `farReachPerLevel` raised, past 16. Both Create's extendo arm and the Right Actuator animate the same click. Create's grip also triggers its own "visitBlock" effects.
Hits: Far Use, Reach cap ("up to 16"), Rest Pose / first-person look.
Q: With an Extendo Grip in hand the Actuator idles and the Rig adds nothing (skip the modifier when the stack is an Extendo Grip)? Or cap the total attribute at 16 via a `ADD_MULTIPLIED_TOTAL`-style clamp?

### 16. Off-hand ignores "main-hand action"
Scenario: the attribute extends both hands. Torch or shield in the off-hand: right-click at 7 blocks places the torch from the off-hand, which the glossary says is the hand's job (arm's length). Which Actuator animates an off-hand far place? One implementer cancels off-hand far use (`PlayerInteractEvent.RightClickBlock` for `OFF_HAND` beyond vanilla range), another lets it through.
Hits: Far Use ("Any main-hand action"), Actuator ("carries the main-hand item").
Q: Allow off-hand Far Use too (reword to "either hand") or refuse it server-side beyond arm's length?

### 17. Grab needs an EMPTY main hand; what happens when the hand fills while holding is undefined
Scenario: players keep 9 hotbar slots full; to Grab they must scroll to an empty slot (or drop something). After a Grab they scroll to a sword: is the mob dropped, does attack Throw or swing, does Far Use resume on the Pinned mob in the crosshair (see 3)? Stowed + empty hand + attack = Far Use punch, or Throw if holding. Silent in the glossary.
Hits: Grab, Throw, Far Use.
Q: "While holding, attack always Throws whatever the hand holds, and the hand may hold anything after the Grab" - yes? Why must the hand be empty to start at all, if G is its own key (v2 needed it because Grab was use-based)?

### 18. Light/heavy by "zombie or smaller" makes Spiders, Endermen and Cows surprising
Scenario: v2 measured width ≤ 1.0 and height ≤ 2.0. A Spider (1.4 wide) is heavy, both Actuators and a slow walk, while a Cow (0.9 × 1.4) is light; Enderman (2.9 tall) heavy; Ghast (4 × 4, 10 hp) heavy. "Spider Man" advancement and a heavy spider do not rhyme. "Walks slower" is undefined for an Active wearer (Rig Speed cut? by how much?) and for a gliding or riding one.
Hits: Grab, Rig Settings Speed, Elytra/Rides.
Q: Define heavy as width > 1 or height > 2 (v2 numbers) and say "Speed and walk speed × 0.6 while heavy, Elytra and Rides unaffected"? Or by max health (> 40 = heavy)?

### 19. Actuators during Rides, Elytra glide, Falling and Crawl are unspecified
Scenario: Rides Stow the Rig; Actuators "work Stowed" so a wearer on a horse or in a boat Grabs and Throws from the saddle, and a held mob is dragged by a client-authoritative vehicle (server lags one tick, mob jitters). Elytra (chest) + Back Slot Rig: grab a mob mid-glide at 30 m/s and tow it into terrain (see 6). Falling: can you Grab while plummeting? Crawl: "limp", clear.
Hits: Ride, ADR 0003, ADR 0006 Elytra consequence, Falling.
Q: "Actuators rest during a Ride, a glide and Falling; a held target is set down when one starts" - yes, or allow Far Use (harmless) but not Grab?

### 20. `c:bosses` does not exist on Forge 1.20.1; "Fridge Mover" is unreachable there
Scenario: the boss tag on Forge 47 is `forge:bosses`; the glossary hardcodes `c:bosses`. Fridge Mover needs Ship Grab (Sable, 1.21.1 only) and would sit greyed out forever in the 1.20.1 advancement tab. The port is "copy to 1.20.1", so this ships unless called out.
Hits: Grab, Advancements, port plan.
Q: Say "the loader's bosses tag" in the glossary and keep both ids in a mod tag `tentaworks:bosses`? Hide Fridge Mover (and Ship Grab config) on 1.20.1 via a datagen flag, or make it "move a mob 50 blocks" on both lines?

### 21. 1.20.1 refill may erase the Actuators
Scenario: Create 0.5.1's `BacktankBlockEntity` saves Air, enchantments and custom name; on 1.21.1 Create 6 keeps a full `componentPatch`, on 1.20.1 arbitrary NBT may not survive place → fill → pick up. A fitted Rig placed under a shaft comes back unfitted, Actuators gone. Already worth checking for Rig Settings on 1.20.1 today.
Hits: Fitting ("permanent"), Rig Block, ADR 0002.
Q: Confirm on the 1.20.1 branch that `rig_settings` survives place/pickup; if not, add a block-entity NBT round-trip for the Rig's whole tag before Actuators are built on it?

### 22. Rig Settings default = server max silently speeds up 1.x Rigs that never saved settings
Scenario: `RigSettings.of()` returns `DEFAULT` when the component is missing. A player who never opened Rig Settings has no component; after 2.0 their Rig runs at 8 b/s instead of 5.6 with no action from them. Also "Speed default = server max" means admins raising `speed.max` later change nothing for saved Rigs but everything for unsaved ones.
Hits: Rig Settings ("A fresh Rig starts at the Server Config's top Speed").
Q: Accept and note it in the changelog, or write the component on first wear so "fresh" means crafted after 2.0?

### 23. First-person transparency 100 % by default hides the "cool" entirely; the term is undefined
Scenario: "transparency 100 % default, 10 % floor". If 100 % transparency = invisible, the Rest Pose and every "arc into view from the frame's edge" is invisible by default; players never see the headline animation. If it means opacity, the Actuators fill the lower frame at chest height. The 1.8.0 first-person pass (`TentacleLayer.onFirstPerson`) draws Tentacles fully opaque today, so 2.0 would silently change 1.x's look.
Hits: Rest Pose, client config, owner goal "look cool".
Q: Transparency applies to the Rest Pose only, an acting Actuator is always drawn? Default 60 % so it is seen but does not block the crosshair? Name it `firstPerson.actuatorAlpha` in the glossary with one sentence.

### 24. Rest Pose "over the shoulders at chest height" fights the third-person camera, Elytra and shoulder parrots
Scenario: F5 camera looks over the shoulder; two half-open limbs breathing at chest height sit in the middle of that view. Elytra wings (chest slot, Back Slot Rig) and shoulder parrots render in the same volume. Idle gestures for every nearby wearer are per-frame work on busy servers.
Hits: Rest Pose, ADR 0006 rendering split.
Q: Fold lower (hip height, behind the arms) when Stowed and raise to shoulder height only while Active? Hide parrots while fitted?

### 25. Impact numbers, Air numbers and Far Use billing are undefined; two implementers differ
Scenario: "sip / trickle / gulp", "grows with speed", "tier's throw speed". Open questions an implementer must answer: Air per mined block vs per tick of mining vs per swing; Impact once per throw or per bounce; whether Impact on the thrown mob can one-shot (Netherite throw into the ceiling while Hanging = repeatable execution of anything grabbable, limited only by Air, which is now doubled); Grab start rate limit (v2 had one start per N ticks); XP and loot attribution for Impact kills (`lastHurtByPlayer` window, Looting). Existing config style is ticks-per-Air, so match it.
Hits: Air, Throw, Impact, Server Config.
Q: Fix the numbers: Far Use 1 Air per accepted far action; Grab `air.grab` ticks per Air; Throw 20 Air; Impact = vanilla fall damage of the speed-equivalent drop, capped at 10 hearts per throw, dealt once; Grab start no more than once per 10 ticks? Impact kills count as the wearer's for XP/loot?

### 26. Two wearers, one mob; held mob vs other players' hits
Scenario: v2 refused a target already "held by anyone". Nothing now stops a second wearer grabbing the same cow (tug of war, position fights every tick), or grabbing a mob that another player is leading or fighting. Other players can still hit the Pinned mob (fine), but can they Grab it from the first wearer (steal)?
Hits: Grab.
Q: Restore "not already held" and "not leashed to anyone but the wearer"?

### 27. Pinned Creeper as a portable bomb
Scenario: Grab a Creeper (fuse paused), carry it into another player's base, set it down next to their chests; the fuse resumes next to them. `mobGriefing` on and PvP off do not stop it. Same with a Zombie set down inside a villager hall.
Hits: Grab ("comes back to life the moment it is set down"), anti-grief.
Q: Creeper set down calm (fuse reset, 3 s no-ignite), or add `grab.hostiles` config, or lean on finding 8 (claim mods refuse the set-down position via the event)?

### 28. Glossary uses its own avoided words
Scenario: Tentacle `_Avoid_: limb` while Tentacle is defined as "pneumatic limbs" and Actuator as "one of the two limbs"; Throw `_Avoid_: yeet (in player text)` while the new advancement is titled "Yeet" (player text). Grab `_Avoid_: carry` next to the term Carry Ship. Ship Grab says "hull block" without defining hull (any Ship block? v2 meant "a block whose own use does nothing", a reason that is gone).
Hits: Tentacle, Actuator, Throw, Ship Grab, Advancements.
Q: Allow "limb" as the family word for Tentacles + Actuators and drop it from Avoid? Rename "Yeet" ("Air Mail", "Special Delivery") or allow it as the one joke? Replace "hull block" with "any block of the Ship"?

### 29. Remote players need the Far Use target and a smooth held mob
Scenario: other clients see the Right Actuator reach for the wearer's target, but the vanilla swing packet carries no target; `RigStatePacket` (registrar "9") must grow a target/held-entity field, bumping protocol on both lines (fine for 2.0, but plan it). Held-mob position pushed by server teleports every tick jitters on every client; v2's Carry used `startRiding` + a rider-position mixin for players, which interpolates for free.
Hits: Feasibility, ADR 0001 (server never computes paths), network.
Q: Held mob rides the wearer (passenger + `positionRider` mixin on both sides) rather than per-tick teleport? Remote Far Use arm aims at the remote's own head-ray hit (cheap, approximate) or at a synced target?

### 30. Fitting recipe needs a custom recipe type; the Actuator item itself is undefined
Scenario: a vanilla shaped recipe cannot copy components/NBT from the Rig in the grid; the crafting-table Fitting needs a custom `Recipe` on both lines (and JEI display). The Actuator's own recipe (what it is made of, Sequenced Assembly or not, cost relative to a Tentacle) is not in the glossary; the v2 Claw parts bin is the only hint. Anvil-combining a fitted and an unfitted Rig (repair) is unspecified. No un-Fit route exists for the player in finding 4.
Hits: Fitting, Actuator.
Q: Actuator = Sequenced Assembly on a Tentacle (so it is "a Tentacle that acts") or a Precision Mechanism recipe? Add an "Unfit" recipe (Rig alone → Rig + 2 Actuators)?

---

## What the abandoned v2 had that the new text silently lost
- Server-side sight check for actions past vanilla range (finding 1).
- The release list and "cannot change dimension while held" (5).
- Collision sweep for the held target (6) and the Grip sight rule for the Grab target.
- PvP gate on throws touching players (7).
- "Not already held" and leash rule (26).
- Grab start rate limit and `air.grab` / `air.work` as named config keys (25).
- HUD line for refusals: v2 showed Mode + Active Limb; the new design has no feedback at all for "why did G do nothing" (boss, owned, too heavy, no Air, Curios key conflict). Add an action-bar line per refusal, as `tentaworks.rig.one_at_a_time` does today.

## Cool-factor opportunities (cheap, high delight)
1. Telegraph: when the crosshair rests on a grabbable mob within Reach, the Left Actuator uncurls and points at it (client-only, Rest Pose variant). Teaches Grab with no text and looks alive.
2. Drops come home: items and XP from a block mined by Far Use get a velocity toward the wearer (or spawn at the Actuator tip and get pulled in). Removes the walk-to-your-drops tax that makes long reach feel worse, not better.
3. Shear/milk/name-tag the held mob: already implied by "use stays free" once finding 3 is fixed; call it out in README and Ponder (hold a sheep, shear it, set it down). Same with a saddle, a lead, a bucket on a held cow.
4. Set down onto a Create seat or into a boat: if the set-down spot is a seat/boat, the mob is seated (`startRiding`). Turns Grab into the mob-transport tool players actually want and makes Fridge Mover meaningful without Sable.
5. Throw feedback: a short camera kick on Throw, a `clank` + dust puff on Impact, and the Actuator holding a "follow-through" pose for 10 ticks. Camera kick is one call; sells the weight of Netherite throws.
6. Fitting moment: first wear after Fitting plays a 1 s unfold-and-flex of both Actuators (the "Fitted" advancement toast at the same moment).
