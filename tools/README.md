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
| `run_all.sh <tag>` | Full regression: every suite through `run_split.sh`, then copies screenshots to `shots/` and prints the checks. Logs in `tools/logs/<tag>-*`. |
| `scen/` | Scenes (`scene-*.txt`, RCON setup), timelines (`*-timeline.txt`, RCON during the run) and client scripts (`*-client.txt`, `*-watch.txt`) per suite. |
| `analyze.py <log prefix>` | Kick/warn counts and client vs server position drift for one run. |
| `spike_metrics.py`, `p1_metrics.py` … `p8_metrics.py`, `p6fast_metrics.py` | Per-suite checks on the logs; print PASS/FAIL lines. |

Quick start: `tools/run_all.sh mytest` (one suite: see the matching line in `run_all.sh`).

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
