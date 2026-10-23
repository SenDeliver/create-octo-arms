# Dev test harness

Not part of the mod jar. Drives a dev server and real clients from the `dev` source set (`/tentaworks` dev commands, TRACE logs, DevScript client scripts) and checks the results from the logs.

Needs Python 3 and bash. Scripts use BSD `sed -i ''`, so they run on macOS as is.
Optional `tools/local.env` (gitignored) is sourced before Gradle runs, e.g.:

```bash
export JAVA_HOME=/path/to/jdk          # JDK for Gradle
export JAVA17_HOME=/path/to/jdk-17     # prod-pack server only (tools/prod/server.sh)
```

RCON password: `$RCON_PASSWORD`, else the one already in `server.properties`, else random. `rcon.py` reads port and password from `server.properties`.

## Dev runs (`run/`, `run2/`, gitignored)

| File | What it does |
|---|---|
| `setup_run.sh` | Creates `run/` and `run2/`: muted clients, flat survival dedicated server with RCON on 25608 (game port 25609). `GAMEMODE=creative` switches the game mode, `ANIMALS=true` keeps animals (`spawn-animals`). Fresh `run/server` (new clone or worktree): the scene's `op Dev` runs before Dev ever joined and resolves a Mojang UUID, not the offline one, so Dev's commands fail with "Unknown or incomplete command"; copy `ops.json` and `usercache.json` from a run dir that has seen Dev join. |
| `rcon.py` | Sends an RCON scenario from stdin to the dev server. `#sleep N`, `#await name` and `#signal name` are harness directives (sync with the client script). |
| `run_split.sh <scene> <client script> <log prefix>` | Fresh world: `runServer`, plays the scene over RCON, joins `runClient` (player `Dev`) running a DevScript, optional second client `Dev2` (`SCRIPT2=`), optional RCON `TIMELINE=`. Config overrides: `TW_SPEED_MAX`, `TW_REACH_COPPER`, `TW_REACH_NETHERITE` (server), `TW_CLIENT_WALK` (client `animation.walkWhileHeld`, both clients). `GRADLE_ARGS` (e.g. `-PnoJei`) go to every Gradle run. The clients boot while the server boots (Dev2 once Dev's window is up) and wait on a blank screen; each joins when `run_split.sh` writes its `signal/_connect` (Dev after the scene, Dev2 after Dev joined; DevScript `-Ptentaworks.connect=host:port`, the vanilla quickplay join); the timeline starts 1 s after the last join. A kicked client quits after 1 s. `TW_PREBOOT=0`: the old flow (client started after the scene with `-Pconnect`, 2 s polls); no suite uses it on this branch. |
| `run_all.sh <tag>` | Full regression: every suite through `run_split.sh`, then copies screenshots to `shots/` and prints the checks. Logs in `tools/logs/<tag>-*`. No Sable suites on this branch (p9, rideaero, obs*, listship are 1.21.1 only). |
| `scen/` | Scenes (`scene-*.txt`, RCON setup), timelines (`*-timeline.txt`, RCON during the run) and client scripts (`*-client.txt`, `*-watch.txt`) per suite. |
| `lang_check.py` | Every lang file has every key of the generated `en_us.json` (release rule, `docs/RELEASE.md`). Exit 1 on a miss. |
| `analyze.py <log prefix>` | Kick/warn counts and client vs server position drift for one run. |
| `spike_metrics.py`, `p1_metrics.py` … `p8_metrics.py`, `p6fast_metrics.py`, `ride_metrics.py` | Per-suite checks on the logs; print PASS/FAIL lines. |

Quick start: `tools/run_all.sh mytest` (one suite: see the matching line in `run_all.sh`).

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

- `flat` (`flat_metrics.py`): L in `look` mode, 60 down + W carries the body to the floor. F1/F2 in `flat` mode, 60 down or 60 up + W stays level and advances; F3 Space still climbs, F4 Shift still descends, F5 60 down + S backs up level. S: no kick or speed warning on the server.

`tools/run_split.sh tools/scen/empty.txt tools/scen/flat-client.txt tools/logs/x-flat && python3 tools/flat_metrics.py tools/logs/x-flat`

`TW_CLIENT_FORWARD=flat` on `run_split.sh` writes the config for both clients instead (any suite).

## Ride suite (ride)

A Ride (seat, mount, boat, minecart, chain conveyor, zipline) Stows the Rig, nothing Activates it during the Ride, and Shift/jump/WASD go to the Ride (`docs/adr/0003-rig-yields-to-any-ride.md`). The server decides every dismount, so dismount checks read the server. Every Ride starts next to a wall with the Rig Active and Held (else the Rig drops to Falling, which keeps WASD and stows on the ground by itself). Each section resets: `ride @s dismount`, tp, Rig off and on (Stowed on both sides, Air 900). Checks are tagged `[setup]` (the scene did its part), `[guard]` (passes before and after the fix) and `[bug]` (failed on 1.2.1).

- `ride` (`ride_metrics.py`, scene `scene-ride.txt`, needs `ANIMALS=true`: with `spawn-animals=false` the server discards the horse on its first tick). A Stowed baseline: sit on a Create seat (empty hand, use), Shift dismounts. J after that dismount: Double Jump activates, Double Jump again sets down. S1 owner repro: Rig Active by the wall, sit: Stowed within 5 ticks on both sides, Shift dismounts. S2 seated Stowed, Double Jump; S3 key-only Activation Mode, toggle key: stays Stowed. S4 Rig Active, sit, 200 ticks: no Air used. S5 old client: seated while the server gets forged Active packets with real Grips (`tentaworks fake held 60`): the server keeps the Rig Stowed. S6/S7/S8 mount a minecart (`ride` command) from Setting Down, Crawl, Falling. B boat on a water lane: W moves it, A turns it (client `vrot`). H tamed saddled horse: W moves it, jump (held, released) makes it jump, the Rig stays Stowed; H5 Double Jump on the horse. M minecart on rails, stays Stowed after the dismount; J3 then Double Jump activates again. C chain conveyor (two conveyors linked by `tentaworks chain`, creative motor 64 RPM, wrench, look at the chain, use): C0 Stowed baseline carries the wearer; C1 with the Rig Active: Stowed and carried; C2 old client on the chain: forged Active packets (`tentaworks fake held 40`) while the server shows `ride=chain` (Create's hanging-player list, also used by Simulated's zipline): the server keeps the Rig Stowed. Stowed-window checks need 90% of the window's trace lines on each side.

`ANIMALS=true TIMELINE=tools/scen/ride-timeline.txt tools/run_split.sh tools/scen/scene-ride.txt tools/scen/ride-client.txt tools/logs/x-ride && python3 tools/ride_metrics.py tools/logs/x-ride`

Log fields (end of line): client `TRACE … veh=<vehicle type id|-> ride=<chain|-> hold=<Simulated hold interaction class|-> vrot=<vehicle yaw|->`, server `STRACE … veh=<id|-> ride=<chain|-> vrot=<yaw|->` (`ride=chain`: Create's client chain ride, on the server its hanging-player list). Dev commands: `tentaworks nbt X Y Z TAG` (SNBT line: block entity NBT), `tentaworks chain FROM TO` (links two chain conveyors). DevScript: `lookat x y z`, `mousebtn left|right press|release` (a real mouse button event: Simulated ends a hold only on the release event; pair it with `holdkey key.use on|off`), diagnostic `hit tag` (CHIT: crosshair target, hold, wheel raw/effective angle).

## Log contract (dev source set, `trace on`)

New fields go at the end of existing lines only: the metrics and `analyze.py` match by field order. On this branch (no Sable, `NoShips`) `STRACE … sgrips=0 carry=-` and `TRACE … sheld=0 carry=-` never change; they stay in place.

| Side | Line | Notes |
|---|---|---|
| client | `TRACE … wa=<speed>` | v1.1.2: own `walkAnimation.speed()` (0 = body still), last field |
| client | `RWALK gt=<gt> name=<player> state=<RigState\|-> wa=<speed>` | v1.1.2: every other player in the level, each traced tick: their Rig visual's state and walk animation speed as this client renders them |

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
| `prod/setup_prod.sh [--no-devtools]` | Fills `run-prod/server` (Forge 1.20.1 installer layout, install it first, the script prints how) and `run-prod/client` with the pack from `PACK=<dir of jars>` plus the release jar; the client also gets the test-only devtools jar. RCON on 25618, game port 25619. `ALLOW_FLIGHT=true` mirrors a server with flight allowed. |
| `prod/server.sh start\|stop` | Starts/stops that server with Java 17. |
| `prod/run_prod.sh <scene> <client script> <timeline> <log prefix>` | Fresh world on the prod-pack server, `runClientProd` with a DevScript, collects logs and screenshots. |
| `prod/rcon_prod.py` | Same as `rcon.py` for the prod-pack server. |
| `prod/scen/`, `prod/prod_metrics.py` | Prod-pack scenario and its checks. |

`shots/` (gitignored) gets the screenshots of the last `run_all.sh`.
