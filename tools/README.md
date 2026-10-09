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
| `setup_run.sh` | Creates `run/` and `run2/`: muted clients, flat survival dedicated server with RCON on 25608 (game port 25609). `GAMEMODE=creative` switches the game mode. |
| `rcon.py` | Sends an RCON scenario from stdin to the dev server. `#sleep N`, `#await name` and `#signal name` are harness directives (sync with the client script). |
| `run_split.sh <scene> <client script> <log prefix>` | Fresh world: `runServer`, plays the scene over RCON, joins `runClient` (player `Dev`) running a DevScript, optional second client `Dev2` (`SCRIPT2=`), optional RCON `TIMELINE=`. Config overrides: `TW_SPEED_MAX`, `TW_REACH_COPPER`, `TW_REACH_NETHERITE`. `GRADLE_ARGS` (e.g. `-PnoJei`) go to every Gradle run. |
| `run_all.sh <tag>` | Full regression: every suite through `run_split.sh`, then copies screenshots to `shots/` and prints the checks. Logs in `tools/logs/<tag>-*`. p9 (Sable Ships) runs last with `-PwithSable`. |
| `scen/` | Scenes (`scene-*.txt`, RCON setup), timelines (`*-timeline.txt`, RCON during the run) and client scripts (`*-client.txt`, `*-watch.txt`) per suite. |
| `analyze.py <log prefix>` | Kick/warn counts and client vs server position drift for one run. |
| `spike_metrics.py`, `p1_metrics.py` … `p9_metrics.py`, `p6fast_metrics.py` | Per-suite checks on the logs; print PASS/FAIL lines. |

Quick start: `tools/run_all.sh mytest` (one suite: see the matching line in `run_all.sh`).

## Sable Ships suite (p9, v1.1)

Only `p9` is built with `GRADLE_ARGS=-PwithSable` (Sable runtime bundle). It runs last in `run_all.sh`; every other suite runs without the flag, so they keep testing the no-Sable path (`NoShips`) exactly as in 1.0. Without the flag p9 fails at `S0` (no `/sable` command).

One suite alone: `GRADLE_ARGS=-PwithSable TIMELINE=tools/scen/p9-timeline.txt tools/run_split.sh tools/scen/scene-p9.txt tools/scen/p9-client.txt tools/logs/x-p9 && python3 tools/p9_metrics.py tools/logs/x-p9`

Ships are hollow 7x5x7 stone boxes (`scene-p9.txt`), x 6000..6720:

| Zone | Ship | What | Checks |
|---|---|---|---|
| W | none | world wall idle hang, 200 ticks | Air baseline for every ship window |
| A | `p9a` on the floor, turned 30° | activate next to its wall, climb, idle hang | turned, climbs, Held (ship Grips >= 2 on >= 50 % of ticks), ship stays put, Air, drift |
| B | `p9b` floating | mid-air catch, then +x 4 b/s, +x 15 b/s (above `speed.max` 8), +z 4 b/s, up 3 b/s | per step: ship speed, Held, ship-frame displacement < 0.75, Air <= baseline + 1, drift; no stow across steps |
| C | `p9c` floating | yaw 20°/s, then roll 10°/s (wall tilts) | rate, Held, ship-frame displacement, drift, Air |
| D | `p9d` on the floor, flush with a world wall | climb the world wall, move sideways onto the hull and back, then straddle while the ship leaves at 2 b/s | ship Grips 0 -> >= 2 -> 0, Held across the seam, ends Active on the world wall |
| E | `p9e` floating | hang, `sable remove` | lets go within 10 ticks, falls >= 5 to the floor, ship gone, no exception |
| F | `p9f` floating | Rig Active standing on the roof, ship +x 4 b/s | stays Active, ship-frame displacement < 0.75 (no double carry, not left behind) |

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
| client | `TRACE … ff=<bool> sheld=<n> carry=<uuid\|->` | `sheld`: held Grips on a ship; `carry`: ship the client carries the wearer with, or `-` |
| server | `SSHIP gt=<gt> name=<name\|-> id=<uuid> pos=x,y,z q=x,y,z,w v=x,y,z w=x,y,z` | every sub-level of the overworld, each tick; `pos`/`q` = `logicalPose().position()` / `orientation()`; `v` b/s, `w` deg/s, world |
| client | `CSHIP gt=<gt> id=<uuid> pos=x,y,z q=x,y,z,w` | every client sub-level, each tick, from `DevScript` trace: the pose the client used for this tick's Grips (logical pose at `ClientTickEvent.Post`) |
| server | `SHIPDRIVE gt=<gt> name=<name> v=x,y,z w=x,y,z` | once per `drive` call |

Numbers `%.3f` (`q` `%.5f`). `p9_metrics.py` maps `id` to `name` through SSHIP. Ship frame = `q^-1 * (p - pos)`. Without CSHIP the drift check falls back to world frame with one tick of ship motion allowed.

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

`shots/` holds curated screenshots from past runs.
