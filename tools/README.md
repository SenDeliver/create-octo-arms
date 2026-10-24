# Dev test harness

Not part of the mod jar. Drives a dev server and real clients from the `dev` source set (`/tentaworks` dev commands, TRACE logs, DevScript client scripts) and checks the results from the logs.

Needs Python 3 and bash. Scripts use BSD `sed -i ''`, so they run on macOS as is.
Optional `tools/local.env` (gitignored) is sourced before Gradle runs, e.g.:

```bash
export JAVA_HOME=/path/to/jdk          # JDK for Gradle
export JAVA21_HOME=/path/to/jdk-21     # prod-pack server only (tools/prod/server.sh)
```

RCON password: `$RCON_PASSWORD`, else the one already in `server.properties`, else random. `rcon.py` reads port and password from `server.properties`.

## Dev runs (`run/`, `run2/`, gitignored)

| File | What it does |
|---|---|
| `setup_run.sh` | Creates `run/` and `run2/`: muted clients, flat survival dedicated server with RCON on 25608 (game port 25609). `GAMEMODE=creative` switches the game mode, `ANIMALS=true` keeps animals (`spawn-animals`). Fresh `run/server` (new clone or worktree): the scene's `op Dev` runs before Dev ever joined and resolves a Mojang UUID, not the offline one, so Dev's commands fail with "Unknown or incomplete command"; copy `ops.json` and `usercache.json` from a run dir that has seen Dev join. |
| `rcon.py` | Sends an RCON scenario from stdin to the dev server. `#sleep N`, `#await name` and `#signal name` are harness directives (sync with the client script). |
| `run_split.sh <scene> <client script> <log prefix>` | Fresh world: `runServer`, plays the scene over RCON, joins `runClient` (player `Dev`) running a DevScript, optional second client `Dev2` (`SCRIPT2=`), optional RCON `TIMELINE=`. Config overrides: `TW_SPEED_MAX`, `TW_REACH_COPPER`, `TW_REACH_NETHERITE` (server), `TW_CLIENT_WALK` (client `animation.walkWhileHeld`, both clients). `GRADLE_ARGS` (e.g. `-PnoJei`) go to every Gradle run. The clients boot while the server boots (Dev2 once Dev's window is up) and wait on a blank screen; each joins when `run_split.sh` writes its `signal/_connect` (Dev after the scene, Dev2 after Dev joined; DevScript `-Ptentaworks.connect=host:port`, the vanilla quickplay join); the timeline starts 1 s after the last join. A kicked client quits after 1 s. `TW_PREBOOT=0`: the old flow (client started after the scene with `-Pconnect`, 2 s polls); `run_all.sh` uses it for p9 and rideaero (a join right after the scene assembles a Ship failed p9 moving-ship checks in 2 of 3 runs). |
| `run_all.sh <tag>` | Full regression: every suite through `run_split.sh`, then copies screenshots to `shots/` and prints the checks. Logs in `tools/logs/<tag>-*`. p9 (Sable Ships) and rideaero run last with `-PwithSable`. |
| `scen/` | Scenes (`scene-*.txt`, RCON setup), timelines (`*-timeline.txt`, RCON during the run) and client scripts (`*-client.txt`, `*-watch.txt`) per suite. |
| `lang_check.py` | Every lang file has every key of the generated `en_us.json` (release rule, `docs/RELEASE.md`). Exit 1 on a miss. |
| `analyze.py <log prefix>` | Kick/warn counts and client vs server position drift for one run. |
| `spike_metrics.py`, `p1_metrics.py` … `p9_metrics.py`, `p6fast_metrics.py`, `ride_metrics.py`, `rideaero_metrics.py` | Per-suite checks on the logs; print PASS/FAIL lines. |

Quick start: `tools/run_all.sh mytest` (one suite: see the matching line in `run_all.sh`).

## Observer suites (obs, obs2, obs3; 1.1.1)

Dev2 watches Dev's Rig on a Sable Ship at moments where the observer's client may lack the Ship pose. Needs `-PwithSable`, scene `scene-obs.txt`. Checks (`obs_metrics.py <log prefix> <tags>`): no render exception on either client, Dev2 reached each mark with Dev's Rig visual present.

| Suite | Dev2 | Moments |
|---|---|---|
| obs | spectator | t1 far (ship untracked) -> next to Dev; t2 rejoin next to Dev; t3 the box Dev hangs on is assembled into a Ship and moved |
| obs2 | spectator, then survival | t4a-d watching from 100/115/125/130 blocks; t8 standing on the deck; t5 Dev activates on the deck; t6 `sable remove` mid-Grip |
| obs3 | survival with its own active Rig | f1 first person, f2 looking around, f3 third person (put ImmediatelyFast + Sodium jars into `run2/mods` to test the batching path) |

`GRADLE_ARGS=-PwithSable TIMELINE=tools/scen/obs-timeline.txt SCRIPT2=tools/scen/obs-watch.txt tools/run_split.sh tools/scen/scene-obs.txt tools/scen/obs-client.txt tools/logs/x-obs && python3 tools/obs_metrics.py tools/logs/x-obs t1,t2,t3`
(obs2: `obs2-*`, tags `t4a,t4b,t4c,t4d,t8,t5,t6`; obs3: `obs3-*`, tags `f1,f2,f3`.) None of them reproduced the 2026-10-12 observer crash on 1.1.0; they guard the 1.1.1 hardening.

## Pole suite (pole)

A free-standing 1x1 jungle log column (`scene-pole.txt`, x 5000 z 0, 41 high) and a 2x2 one (x 5100..5101 z 0..1, 20 high, wall torch on its east face 6 up). `pole_metrics.py <log prefix>`: F face climb and C/N corner climbs (Copper, Netherite) reach the top of the 1x1 (max feet >= 40); D/E/G/H/J climbs on the 2x2 (hugging the corner, 0.7 west / 0.4 south of it as in the owner's 2026-10-14 report, 0.7 off a face, 1.2 / 0.9 off the corner) reach its top (>= 19) and print the body's offset from the corner; S/T and R/Q go round each pole at height (>= 80 deg round it) with the Rig Active throughout. Corner climbs hold jump only: forward at a corner slides the body round the pole along its faces.

History: on 1.1.3 a corner climb stopped at Reach above the floor (3.10 / 6.10) and going round stopped at the face edge (32 deg): all faces of a pole seen from its corner lie in one body quadrant. 1.1.4 widened the quadrants by 0.5 (enough for a body up to 0.5 past both edges); the outside-edge rule in `Grips.count` (two faces of one block on different axes, both within Reach and facing the body, hold by themselves) replaced it.

`tools/run_split.sh tools/scen/scene-pole.txt tools/scen/pole-client.txt tools/logs/x-pole && python3 tools/pole_metrics.py tools/logs/x-pole`

## Tank suite (tank)

Issue #19 and the slab report after it: Create Fluid Tanks and recessed surfaces are Grips. `scene-tank.txt`: a 1x1 Fluid Tank column (x 5200 z 0, 20 high), a 2x2 Creative Fluid Tank column (x 5300..5301 z 0..1, 20 high), a 3x3 Fluid Tank roof (y -56 over x 5398..5400 z -1..1) and a floating 3x3 bottom-slab platform (y -50 over x 5499..5501 z -1..1). `tank_metrics.py <log prefix>`: F/N face climbs (Copper, Netherite) and the D corner climb reach the column top (max feet >= 19), R rises from the floor and still hangs under the roof 40 ticks after jump is released (feet >= 1.0), S walks east off the slab platform and stays held at its edge (min feet >= 8.0 above the ground, end x past the edge, held >= 2), the Rig Active throughout.

Why: a tank's collision shape for the empty collision context (what `isCollisionShapeFullBlock` caches) is cut down for campfire smoke, so tanks were never full blocks to the Rig, and faces were only ever taken on the block boundary, so a bottom slab's top (half height), a top slab's underside, snow, carpet, chest lids and stair steps were no Grips. Faces now come from the block's support shape (`Grips.faceRect`): per side, the part of the shape on its outermost plane, at that plane, when it is at least half a block wide both ways (fence posts, chains, rails' sides are out). A face on the block boundary is covered when the neighbour's face toward it contains it; a recessed face never is. Unit tests for the rule: `GripFaceTest` (`./gradlew test`, runs under FML so Create's blocks are real).

`tools/run_split.sh tools/scen/scene-tank.txt tools/scen/tank-client.txt tools/logs/x-tank && python3 tools/tank_metrics.py tools/logs/x-tank`

## Cave suite (cave)

The 2026-10-20 clipping report: with the Rig out and the body low, the lower Tentacle segments went through the floor; in spectator the tips held blocks of a cave under the floor. `scene-cave.txt`: an 11x11 stone platform (x 5595..5605 z -5..5, top y -49) over a one-high cave (air at y -51 inside, stone walls round the rim, cave floor top -51). The client script puts the Rig at Standing Height 0 (`rawsettings chest 0 ...`) on the platform centre looking 40° down (the Heading pushes into the floor, so the body stays at its lowest while walking), idles, walks, turns with `lookat` and walks back; Netherite (N: the back aim would sit 1.55 under the floor) and Copper (C: 0.5 under). `cave_metrics.py <log prefix>`: over each window no Grip point in the `GAIT` lines lies under the floor top, >= 2 Grips on >= 95 % of gait ticks, some stepping, the server sees >= 2 valid Grips on >= 95 % of its ticks, the body stays low (max feet <= -48.7), Active throughout; S: no `forcing Falling` on the server (the sight rule is the same on both sides). Shots `cave_low_netherite`, `cave_low_copper`.

## Sneak suites (sneak, sneak2)

Shift with the Rig out counts as sneaking without the crouch (PR #15). Scene `scene-sneak.txt`: a Create gearbox and a chest on the floor, a platform with an edge, a ladder wall, a glass water pool.

- `sneak` (`sneak_metrics.py`, Dev2 watching): client and server see the sneak with the Rig out, no crouch (pose Standing, eye 1.62), Shift still moves down; sneak + use with the wrench picks up the gearbox and stone goes on the chest; no edge hold, no ladder stop, no extra sinking under water; Dev2's client never sees Dev crouch.
- `sneak2` (`sneak2_metrics.py`): the hand-over. A Stowed: vanilla crouch on both sides and sneak + use untouched. T: Shift held while the Rig sets down and folds: the crouch starts right after the stow, the server loses the sneak for at most 2 ticks. R: Shift released with the Rig out: no sneak sticks within 5 ticks. H: sneak + use in the air with a Rig in hand opens Rig Settings while the Rig is out.

`TIMELINE=tools/scen/sneak2-timeline.txt tools/run_split.sh tools/scen/scene-sneak.txt tools/scen/sneak2-client.txt tools/logs/x-sneak2 && python3 tools/sneak2_metrics.py tools/logs/x-sneak2`

## Flat suite (flat)

Issue #18: client config `movement.forward`. Scene `empty.txt` (flat ground at x 8100), the client script flips the config in memory with the DevScript line `forward flat`.

- `flat` (`flat_metrics.py`): L in `look` mode, 60 down + W carries the body to the floor. K then holds the sprint key + W on the floor: no carried tick of the run sprints, widens the FOV (`fovmod` above the walking 1.000) or bobs the view (`bob` > 0; with the body on the floor vanilla `Player.aiStep` bobs it from onGround and the motion, `LocalPlayerBobMixin` zeroes it). F1/F2 in `flat` mode, 60 down or 60 up + W stays level and advances; F3 Space still climbs, F4 Shift still descends, F5 60 down + S backs up level. S: no kick or speed warning on the server.

`tools/run_split.sh tools/scen/empty.txt tools/scen/flat-client.txt tools/logs/x-flat && python3 tools/flat_metrics.py tools/logs/x-flat`

`TW_CLIENT_FORWARD=flat` on `run_split.sh` writes the config for both clients instead (any suite).

## Ride suites (ride, rideaero)

A Ride (seat, mount, boat, minecart, chain conveyor, zipline) Stows the Rig, nothing Activates it during the Ride, and Shift/jump/WASD go to the Ride (`docs/adr/0003-rig-yields-to-any-ride.md`). The server decides every dismount, so dismount checks read the server. Every Ride starts next to a wall with the Rig Active and Held (else the Rig drops to Falling, which keeps WASD and stows on the ground by itself). Each section resets: `ride @s dismount`, tp, Rig off and on (Stowed on both sides, Air 900). Checks are tagged `[setup]` (the scene did its part), `[guard]` (passes before and after the fix) and `[bug]` (failed on 1.2.1).

- `ride` (`ride_metrics.py`, scene `scene-ride.txt`, needs `ANIMALS=true`: with `spawn-animals=false` the server discards the horse on its first tick). A Stowed baseline: sit on a Create seat (empty hand, use), Shift dismounts. J after that dismount: Double Jump activates, Double Jump again sets down. S1 owner repro: Rig Active by the wall, sit: Stowed within 5 ticks on both sides, Shift dismounts. S2 seated Stowed, Double Jump; S3 key-only Activation Mode, toggle key: stays Stowed. S4 Rig Active, sit, 200 ticks: no Air used. S5 old client: seated while the server gets forged Active packets with real Grips (`tentaworks fake held 60`): the server keeps the Rig Stowed. S6/S7/S8 mount a minecart (`ride` command) from Setting Down, Crawl, Falling. B boat on a water lane: W moves it, A turns it (client `vrot`). H tamed saddled horse: W moves it, jump (held, released) makes it jump, the Rig stays Stowed; H5 Double Jump on the horse. M minecart on rails, stays Stowed after the dismount; J3 then Double Jump activates again. C chain conveyor (two conveyors linked by `tentaworks chain`, creative motor 64 RPM, wrench, look at the chain, use): C0 Stowed baseline carries the wearer; C1 with the Rig Active: Stowed and carried; C2 old client on the chain: forged Active packets (`tentaworks fake held 40`) while the server shows `ride=chain` (Create's hanging-player list, also used by Simulated's zipline): the server keeps the Rig Stowed. Stowed-window checks need 90% of the window's trace lines on each side.
- `rideaero` (`rideaero_metrics.py`, scene `scene-rideaero.txt`, `-PwithSable`, Aeronautics bundle). W1 seat inside a stone box Ship (`sable assemble area`), parked: sitting Stows, Shift dismounts. W2 the same Ship driven +x 4 b/s: seated 100 ticks, Stowed, Shift dismounts, no kick. W3 Simulated steering wheel preset to 30 deg (block entity NBT): Stowed baseline, Shift snaps it to 45 (read with `tentaworks nbt`); Control Hold with the Rig Active: Shift snaps the wheel, the wearer stays still (< 0.05), the Rig stays Active; Double Jump during the hold sets the Rig down; after letting go Shift moves the Rig again. W4 Simulated handle: `ClientHandleHandler.activeTick` sets the wearer's motion every tick, so a handle hold is a Ride: Stowed, W pulls closer, S lets out (eye to grab centre). W5 the handle grabbed sneaking (drag hold: drags its Ship, here a wall, moves no one, reads no keys) is no Control Hold: the Rig stays Active and W still moves it. Zipline not covered (needs a rope between two connectors and a wrench hold).

`ANIMALS=true TIMELINE=tools/scen/ride-timeline.txt tools/run_split.sh tools/scen/scene-ride.txt tools/scen/ride-client.txt tools/logs/x-ride && python3 tools/ride_metrics.py tools/logs/x-ride`
`GRADLE_ARGS=-PwithSable TIMELINE=tools/scen/rideaero-timeline.txt tools/run_split.sh tools/scen/scene-rideaero.txt tools/scen/rideaero-client.txt tools/logs/x-rideaero && python3 tools/rideaero_metrics.py tools/logs/x-rideaero`

Log fields (end of line): client `TRACE … veh=<vehicle type id|-> ride=<chain|-> hold=<Simulated hold interaction class|-> vrot=<vehicle yaw|->`, server `STRACE … veh=<id|-> ride=<chain|-> vrot=<yaw|->` (`ride=chain`: Create's client chain ride, on the server its hanging-player list). Dev commands: `tentaworks nbt X Y Z TAG` (SNBT line: block entity NBT), `tentaworks chain FROM TO` (links two chain conveyors). DevScript: `lookat x y z`, `mousebtn left|right press|release` (a real mouse button event: Simulated ends a hold only on the release event; pair it with `holdkey key.use on|off`), diagnostic `hit tag` (CHIT: crosshair target, hold, wheel raw/effective angle).

## Sable Ships suite (p9, v1.1)

Only `p9` and `rideaero` are built with `GRADLE_ARGS=-PwithSable` (Sable runtime bundle). They run last in `run_all.sh`; every other suite runs without the flag, so they keep testing the no-Sable path (`NoShips`) exactly as in 1.0. Without the flag p9 fails at `S0` (no `/sable` command).

One suite alone: `GRADLE_ARGS=-PwithSable TIMELINE=tools/scen/p9-timeline.txt tools/run_split.sh tools/scen/scene-p9.txt tools/scen/p9-client.txt tools/logs/x-p9 && python3 tools/p9_metrics.py tools/logs/x-p9`

Ships are hollow 7x5x7 stone boxes (`scene-p9.txt`), x 6000..7320:

| Zone | Ship | What | Checks |
|---|---|---|---|
| W | none | world wall idle hang, 200 ticks | Air baseline for every ship window |
| A | `p9a` on the floor, turned 30° | activate next to its wall, climb, idle hang | turned, climbs, Held (ship Grips >= 2 on >= 50 % of ticks), ship stays put, Air, drift |
| B | `p9b` floating | mid-air catch, then +x 4 b/s, +x 15 b/s (above `speed.max` 8), +z 4 b/s, up 3 b/s | per step: ship speed, Held, ship-frame displacement < 0.75, Air <= baseline + 1, drift; no stow across steps |
| C | `p9c` floating | yaw 20°/s, then roll 10°/s (wall tilts) | rate, Held, ship-frame displacement, drift, Air |
| D | `p9d` on the floor, flush with a world wall | climb the world wall, move sideways onto the hull and back, then straddle while the ship leaves at 2 b/s | ship Grips 0 -> >= 2 -> 0, Held across the seam, ends Active on the world wall |
| E | `p9e` floating | hang, `sable remove` | lets go within 10 ticks, falls >= 5 to the floor, ship gone, no exception |
| F | `p9f` floating | Rig Active standing on the roof, ship +x 4 b/s | stays Active, ship-frame displacement < 0.75 (no double carry, not left behind) |
| G | `p9g` floating | bow case: mid-air catch on the west wall (face normal -x), press against it (no gap), ship -x at 4 then 15 b/s toward the wearer | per step: ship speed, Held (ship Grips 100 %), server lag-free ride <= 0.1, client ride <= 0.1 with no one-tick step > 0.05 (a server correction would jump), Air, drift; INFO: player box vs hull (server and client, > 0 inside), kick/wrong-move lines |
| H | `p9h` floating | under the floor (head pressed to it): ship up 3 b/s, then down 3 b/s (floor comes at the head) | as G |
| R | `p9h` | Stowed on the roof by its west edge, ship up 3 b/s, Double Jump on the rising deck (Standing Height lift), then ship down 3 b/s | R1 feet 1.5 +-0.1 above the roof (lift target follows the deck), then as G |

Sounds (`sounds <tag>` CSOUND lines): an idle rider hears no whir (`create:cogs`) and no height hiss (`create:steam`) start over B (`b0..bend`), G and H: the Rig's motion is measured in the carry Ship's frame.

Sable never logs "moved wrongly" (it redirects `isCreative()` in `handleMovePlayer` to true), Ship hulls are not in `level.getCollisions` and Sable's entity collision returns a `ServerPlayer`'s move unchanged, so the server takes a lagging rider's position even inside the hull (G 15 b/s: box 0.6 inside, no effect). A problem could only show up as a position jump: hence the client ride and one-tick step checks.

On the client Sable can push a body that touches a hull starting to move toward it, once, by about one tick of hull motion (intermittent; seen G 4 b/s: 0.215 off the wall, H down: 0.083): `PUSH` in `p9_metrics.py` reports those ticks; a whir or hiss is allowed only within 2 ticks of one (`CSOUNDAT` gt).

Floating ships have their bottom 10 above the floor (out of Copper Reach 4). Mid-air catch as in p2 MJ1: `cmd tp`, `wait 2`, `doublejump`.

Sable 2.0.5 commands used (op level 2): `sable assemble area <from> <to>`, `sable name set <sub-levels> <name>`, `sable forceload add <sub-levels>`, `sable physics rotation <sub-levels> set axis <x y z> <degrees>`, `sable remove <sub-levels>`, `sable info <sub-levels>`. Selectors: `@e` all, `@l` latest, `@n` nearest pose position to the command source (RCON sits at world spawn, so the scene names each ship with `execute positioned <box centre> run sable name set @n <name>`), `@v` viewed, `@t` tracking, `@i` inside, with `[name=...]`, `[x=..,y=..,z=..]`, `[distance=..]`, `[sort=nearest]`, `[limit=N]` and more, or a UUID. Sable has no disassemble-to-world command; `remove` deletes the ship and its blocks.

### Dev command for ship motion (dev source set, Sable only)

Sable impulses fade under gravity and drag, and `sable physics translation` / `sable teleport` are teleports, so constant ship speeds come from:

- `/tentaworks ship <name> drive <vx> <vy> <vz> [<wx> <wy> <wz>]`: `v` in blocks/s, `w` in degrees/s about world axes through the rotation point. On each call it stores an anchor pose = the current pose. Each server tick it advances the anchor by `v/20` and `w/20`, then sets the body velocity with `RigidBodyHandle.addLinearAndAngularVelocity(target - current)`, where target = `v + 5 * (anchor.pos - pose.pos)` and `w + 5 * axisAngle(anchor.q * pose.q^-1)` (per second). The correction term cancels gravity and drag, so `drive 0 0 0` holds a ship in the air. Reply `ship <name> drive`, or `no ship <name>` when no sub-level has that name. Logs `SHIPDRIVE`.
- `/tentaworks ship <name> stop`: no more driving; normal Sable physics.

### p9 log contract (dev source set, `trace on`)

New fields go at the end of existing lines only: p1..p8 and `analyze.py` match by field order.

| Side | Line | Notes |
|---|---|---|
| server | `STRACE … mv=x,y,z sgrips=<n> carry=<uuid\|->` | `sgrips`: valid Grips whose block is on a ship; `carry`: carry ship (most valid Grips, >= 2) or `-` |
| client | `TRACE … ff=<bool> sheld=<n> carry=<uuid\|->` | `sheld`: held Grips on a ship (`RigClient.shipGrips()`); `carry`: the carry Ship's id (`RigClient.carryShip()`, same rule as the server), or `-`. It names the carry Ship also while Sable tracks the wearer (feet on its deck) and the Rig adds no carry of its own |
| server | `SSHIP gt=<gt> name=<name\|-> id=<uuid> pos=x,y,z q=x,y,z,w v=x,y,z w=x,y,z` | every sub-level of the overworld, each tick; `pos`/`q` = `logicalPose().position()` / `orientation()`; `v` b/s, `w` deg/s, world |
| client | `CSHIP gt=<gt> id=<uuid> pos=x,y,z q=x,y,z,w` | every client sub-level, each tick, from `DevScript` trace: the pose the client used for this tick's Grips (logical pose read in `MovementInputUpdateEvent` at LOWEST priority, after `RigClient`), printed with the TRACE line at `ClientTickEvent.Post` under its `gt` |
| server | `SHIPDRIVE gt=<gt> name=<name> v=x,y,z w=x,y,z` | once per `drive` call |
| server | `SHIPSTOP gt=<gt> name=<name>` | once per `stop` call |

| client | `TRACE … wa=<speed>` | v1.1.2: own `walkAnimation.speed()` (0 = body still), last field |
| client | `TRACE … sprint=<isSprinting> fovmod=<getFieldOfViewModifier> bob=<Player.bob> dm=<deltaMovement> wd=<walkDist - walkDistO>` | 1.5.0: sprint FOV and view bob of the wearer (`flat` K check), last fields |
| client | `RWALK gt=<gt> name=<player> state=<RigState\|-> wa=<speed>` | v1.1.2: every other player in the level, each traced tick: their Rig visual's state and walk animation speed as this client renders them |

Numbers `%.3f` (`q` `%.5f`). `p9_metrics.py` maps `id` to `name` through SSHIP. Ship frame = `q^-1 * (p - pos)`. Without CSHIP the drift check falls back to world frame with one tick of ship motion allowed.

Ship-frame drift allows one tick of player lag and a server pose up to 2 ticks newer than the client's (`SHIP_LAG`): on localhost the client's Ship pose trails the server's by ~0.5 tick (Sable interpolation) and the server's player position is the client's from 1 tick earlier, so at 15 b/s the same-tick ship-frame gap is ~1.1 blocks while the server's Grip check (last 10 poses) still matches. `E3` accepts a catch up to 3.2 above the floor (a falling Copper Rig with Air re-grabs at the edge of Reach, spike S7 3.10).

## Asset generators

| File | What it does |
|---|---|
| `gen_textures.py` | Tentacle part textures (16x16, Copper and Netherite) into `src/main/resources` (needs Pillow). |
| `gen_models.py` | Static Tentacle block/item models; keep in sync with `TentacleRenderer`. |
| `gen_ponder.py` | Builds the Ponder scene schematic; its docstring has the export command. |

## Prod-pack runs (`tools/prod/`, `run-prod/`, gitignored)

Release jar inside a full modpack, as players run it. Build first: `./gradlew build devJar`.

| File | What it does |
|---|---|
| `prod/setup_prod.sh [--no-devtools]` | Fills `run-prod/server` (NeoForge installer layout, install it first, the script prints how) and `run-prod/client` with the pack from `PACK=<dir of jars>` plus the release jar; the client also gets the test-only devtools jar. RCON on 25618, game port 25619. `ALLOW_FLIGHT=true` mirrors a server with flight allowed. |
| `prod/server.sh start\|stop` | Starts/stops that server with Java 21. |
| `prod/run_prod.sh <scene> <client script> <timeline> <log prefix>` | Fresh world on the prod-pack server, `runClientProd` with a DevScript, collects logs and screenshots. |
| `prod/rcon_prod.py` | Same as `rcon.py` for the prod-pack server. |
| `prod/scen/`, `prod/prod_metrics.py` | Prod-pack scenario and its checks. |

`shots/` (gitignored) gets the screenshots of the last `run_all.sh`.
