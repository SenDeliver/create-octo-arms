#!/bin/bash
# Dedicated server + client(s) over localhost. Fresh world each run.
# GRADLE_ARGS (e.g. -PnoJei) go to every gradle run.
# Clients boot while the server boots and join once the scene is done (signal/_connect, DevScript); TW_PREBOOT=0: old flow (client started after the scene, 2 s polls).
# TW_SHARD=B..F: runs the suite in a parallel shard (dirs runs/<id>/client, runs/<id>/client2, runs/<id>/server, RCON 25608+100k, game 25609+100k, B k=1;
# gradle runs runClient<id>, runClient2<id>, runServer<id>; shots in <client dir>/screenshots and <client2 dir>/screenshots, printed at the end). Empty: run/, run2/, run/server, 25608/25609.
# usage: [TW_SHARD=B] [TW_SPEED_MAX=20] [TW_SPEED_CHECK=false] [TW_SWIFT_PER_LEVEL=N] [TW_FAR_REACH_PER_LEVEL=N] [TW_REACH_COPPER=N] [TW_REACH_NETHERITE=N] [TW_BACK_SLOT=false] [TW_DATAPACK=<dir>] [TW_CLIENT_WALK=true] [TW_CLIENT_FORWARD=flat] [TIMELINE=<rcon file run after Dev joins>] [SCRIPT2=<client script for Dev2>] [GAMEMODE=creative] [ANIMALS=true] tools/run_split.sh <rcon scene> <client script> <log prefix>
cd "$(dirname "$0")/.."
SCENE=$1; SCRIPT=$2; OUT=$3
TW_REACH_COPPER=${TW_REACH_COPPER:-4}
case "$TW_SHARD" in
  '') C1=run; C2=run2; SV=run/server; K=0 ;;
  B|C|D|E|F) C1=runs/$TW_SHARD/client; C2=runs/$TW_SHARD/client2; SV=runs/$TW_SHARD/server; K=$(( $(printf %d "'$TW_SHARD") - 65 )) ;;
  *) echo "TW_SHARD must be empty or one of B C D E F, not '$TW_SHARD'"; exit 1 ;;
esac
RPORT=$((25608 + 100 * K)); GPORT=$((25609 + 100 * K))
# a run's JVM command line ends with @build/moddev/<run name>RunProgramArgs.txt; run names client, client2, server (+ shard id) never prefix each other
MDEV="$PWD/build/moddev"
mkdir -p "$(dirname "$OUT")"
busy() { pgrep -f "bootstraplauncher.*$MDEV/(client|client2|server)${TW_SHARD}Run" > /dev/null || [ -n "$(lsof -nP -iTCP:$RPORT -iTCP:$GPORT -sTCP:LISTEN 2>/dev/null)" ]; }
for i in $(seq 1 480); do busy || break; sleep 0.25; done
if busy; then
  echo "a dev Minecraft of this repo${TW_SHARD:+ (shard $TW_SHARD)} is still running, or port $RPORT (RCON) / $GPORT (game) has a listener (another worktree?); not starting"
  lsof -nP -iTCP:$RPORT -iTCP:$GPORT -sTCP:LISTEN 2>/dev/null
  exit 1
fi
# the server must own its ports before any RCON line is sent: otherwise the scene would land on another session's server
bound() {
  grep -q 'RCON running' "$OUT-server.log" && ! grep -q 'FAILED TO BIND' "$OUT-server.log" && return 0
  echo "the server did not come up on $RPORT / $GPORT (see $OUT-server.log); stopping this run"
  pkill -f "$MDEV/client${TW_SHARD}Run" 2>/dev/null; pkill -f "$MDEV/client2${TW_SHARD}Run" 2>/dev/null
  kill $SPID $FPID 2>/dev/null
  exit 3
}
tools/setup_run.sh > /dev/null
rm -rf "${SV:?}/world" "${C1:?}/signal" "${C2:?}/signal"
# TW_DATAPACK=<dir>: a datapack copied into the fresh world before boot (a new world enables every pack it finds), e.g. a test entry in a tag.
if [ -n "$TW_DATAPACK" ]; then mkdir -p $SV/world/datapacks && cp -R "$TW_DATAPACK" $SV/world/datapacks/; fi
# TW_SPEED_MAX (e.g. 20), TW_SPEED_CHECK (true|false), TW_SWIFT_PER_LEVEL / TW_FAR_REACH_PER_LEVEL (a level past the max: levels are capped at max_level), TW_REACH_COPPER (default 4, the 1.x Reach the movement suites are built on) / TW_REACH_NETHERITE, TW_BACK_SLOT (true|false): this run's world overrides those keys (world/serverconfig beats config/).
# A [speed] check key or [curios] section missing from an older config/ copy is added (NeoForge fills the other keys with defaults).
[ -f $SV/config/tentaworks-server.toml ] || echo "WARN: no $SV/config/tentaworks-server.toml, the TW_* server config overrides (TW_REACH_COPPER=$TW_REACH_COPPER ...) are NOT applied; copy it from a run dir that has booted once"
if [ -n "$TW_SPEED_MAX$TW_SPEED_CHECK$TW_SWIFT_PER_LEVEL$TW_FAR_REACH_PER_LEVEL$TW_REACH_COPPER$TW_REACH_NETHERITE$TW_BACK_SLOT" ] && [ -f $SV/config/tentaworks-server.toml ]; then
  mkdir -p $SV/world/serverconfig
  awk -v sp="$TW_SPEED_MAX" -v sc="$TW_SPEED_CHECK" -v sw="$TW_SWIFT_PER_LEVEL" -v fr="$TW_FAR_REACH_PER_LEVEL" -v rc="$TW_REACH_COPPER" -v rn="$TW_REACH_NETHERITE" -v bs="$TW_BACK_SLOT" '/^\[/{if (sec=="[speed]" && sc!="" && !sck) printf "\tcheck = %s\n", sc; sec=$0}
    sec=="[speed]" && $1=="max" && sp!="" {sub(/=.*/, "= " sp)}
    sec=="[speed]" && $1=="check" && sc!="" {sub(/=.*/, "= " sc); sck=1}
    sec=="[enchant]" && $1=="swiftPerLevel" && sw!="" {sub(/=.*/, "= " sw)}
    sec=="[enchant]" && $1=="farReachPerLevel" && fr!="" {sub(/=.*/, "= " fr)}
    sec=="[reach]" && $1=="copper" && rc!="" {sub(/=.*/, "= " rc)}
    sec=="[reach]" && $1=="netherite" && rn!="" {sub(/=.*/, "= " rn)}
    sec=="[curios]" && $1=="backSlot" && bs!="" {sub(/=.*/, "= " bs); seen=1} {print}
    END {if (sec=="[speed]" && sc!="" && !sck) printf "\tcheck = %s\n", sc; if (bs!="" && !seen) printf "\n[curios]\n\tbackSlot = %s\n", bs}' $SV/config/tentaworks-server.toml > $SV/world/serverconfig/tentaworks-server.toml
fi
# TW_CLIENT_WALK (true|false): this run's client config animation.walkWhileHeld on both clients (default: the mod default, false)
# TW_CLIENT_FORWARD (look|flat): this run's client config movement.forward on both clients (default: the mod default, look)
for d in $C1 $C2; do
  rm -f $d/config/tentaworks-client.toml
  [ -n "$TW_CLIENT_WALK" ] && printf '[animation]\n\twalkWhileHeld = %s\n' "$TW_CLIENT_WALK" >> $d/config/tentaworks-client.toml
  [ -n "$TW_CLIENT_FORWARD" ] && printf '[movement]\n\tforward = "%s"\n' "$TW_CLIENT_FORWARD" >> $d/config/tentaworks-client.toml
done
[ -f tools/local.env ] && . tools/local.env
# TW_MINIMIZED=1: the clients open minimized (WSLg stacks new windows on top of everything; no stray mouse turns the camera). Not for suites that take screenshots.
TW_MIN_ARG=; [ "$TW_MINIMIZED" = 1 ] && TW_MIN_ARG=-Ptentaworks.minimized=1
# shards cascade their windows (DevWindowFocusMixin, tentaworks.winpos) so none is fully covered; the default shard keeps vanilla's centred window
WP1=; WP2=; [ $K -gt 0 ] && WP1=-Ptentaworks.winpos=$((96 * K)),$((30 + 48 * K)) && WP2=-Ptentaworks.winpos=$((96 * K + 48)),$((54 + 48 * K))
# the clients open without taking the focus (-Ptentaworks.nofocus=1 below); tools/keep_focus.sh is an opt-in fallback (macOS, TW_KEEP_FOCUS=1)
rm -f $C1/signal/_focus_done
tools/keep_focus.sh $C1/signal/_focus_done 600 &
FPID=$!
./gradlew runServer$TW_SHARD $GRADLE_ARGS > "$OUT-server.log" 2>&1 &
SPID=$!
if [ "${TW_PREBOOT:-1}" = 0 ]; then
for i in $(seq 1 150); do grep -q 'RCON running' "$OUT-server.log" && break; grep -qE 'BUILD FAILED' "$OUT-server.log" && break; sleep 2; done
bound
python3 tools/rcon.py < "$SCENE" > "$OUT-rcon.txt"
./gradlew runClient$TW_SHARD $GRADLE_ARGS -Ptentaworks.nofocus=1 $TW_MIN_ARG $WP1 -Pconnect=localhost:$GPORT -Ptentaworks.script="$PWD/$SCRIPT" > "$OUT-client.log" 2>&1 &
CPID=$!
for i in $(seq 1 150); do grep -q 'Dev joined the game' "$OUT-server.log" && break; kill -0 $CPID 2>/dev/null || break; sleep 2; done
if [ -n "$SCRIPT2" ]; then
  ./gradlew runClient2$TW_SHARD $GRADLE_ARGS -Ptentaworks.nofocus=1 $TW_MIN_ARG $WP2 -Pconnect=localhost:$GPORT -Ptentaworks.script2="$PWD/$SCRIPT2" > "$OUT-client2.log" 2>&1 &
  C2PID=$!
  for i in $(seq 1 150); do grep -q 'Dev2 joined the game' "$OUT-server.log" && break; kill -0 $C2PID 2>/dev/null || break; sleep 2; done
fi
mkdir -p $C1/signal && touch $C1/signal/_focus_done
[ -n "$TIMELINE" ] && python3 tools/rcon.py < "$TIMELINE" >> "$OUT-rcon.txt"
wait $CPID
[ -n "$C2PID" ] && wait $C2PID
printf 'tentaworks trace off\nstop\n' | python3 tools/rcon.py >> "$OUT-rcon.txt"
for i in $(seq 1 60); do kill -0 $SPID 2>/dev/null || break; sleep 2; done
else
for i in $(seq 1 1200); do grep -q '> Task :runServer' "$OUT-server.log" && break; grep -qE 'BUILD FAILED' "$OUT-server.log" && break; sleep 0.25; done
./gradlew runClient$TW_SHARD $GRADLE_ARGS -Ptentaworks.nofocus=1 $TW_MIN_ARG $WP1 -Ptentaworks.connect=localhost:$GPORT -Ptentaworks.script="$PWD/$SCRIPT" > "$OUT-client.log" 2>&1 &
CPID=$!
if [ -n "$SCRIPT2" ]; then
  # Dev2 starts once Dev's window exists, so Dev2's window still opens last
  for i in $(seq 1 1200); do grep -q 'Backend library' "$OUT-client.log" && break; kill -0 $CPID 2>/dev/null || break; sleep 0.25; done
  ./gradlew runClient2$TW_SHARD $GRADLE_ARGS -Ptentaworks.nofocus=1 $TW_MIN_ARG $WP2 -Ptentaworks.connect=localhost:$GPORT -Ptentaworks.script2="$PWD/$SCRIPT2" > "$OUT-client2.log" 2>&1 &
  C2PID=$!
fi
for i in $(seq 1 1200); do grep -q 'RCON running' "$OUT-server.log" && break; grep -qE 'BUILD FAILED' "$OUT-server.log" && break; sleep 0.25; done
bound
python3 tools/rcon.py < "$SCENE" > "$OUT-rcon.txt"
mkdir -p $C1/signal && touch $C1/signal/_connect
JOINED=
for i in $(seq 1 1200); do grep -q 'Dev joined the game' "$OUT-server.log" && JOINED=1 && break; kill -0 $CPID 2>/dev/null || break; sleep 0.25; done
if [ -n "$SCRIPT2" ]; then
  mkdir -p $C2/signal && touch $C2/signal/_connect
  for i in $(seq 1 1200); do grep -q 'Dev2 joined the game' "$OUT-server.log" && break; kill -0 $C2PID 2>/dev/null || break; sleep 0.25; done
fi
touch $C1/signal/_focus_done
if [ -z "$JOINED" ]; then
  echo "Dev never joined; stopping the clients"
  cp $C1/logs/debug.log "$OUT-client-debug.log" 2>/dev/null; cp $SV/logs/debug.log "$OUT-server-debug.log" 2>/dev/null
  pkill -f "$MDEV/client${TW_SHARD}Run" 2>/dev/null
  pkill -f "$MDEV/client2${TW_SHARD}Run" 2>/dev/null
else
  sleep 1
  [ -n "$TIMELINE" ] && python3 tools/rcon.py < "$TIMELINE" >> "$OUT-rcon.txt"
fi
wait $CPID
[ -n "$C2PID" ] && wait $C2PID
printf 'tentaworks trace off\nstop\n' | python3 tools/rcon.py >> "$OUT-rcon.txt"
for i in $(seq 1 480); do kill -0 $SPID 2>/dev/null || break; sleep 0.25; done
fi
kill $SPID 2>/dev/null
wait $SPID 2>/dev/null
kill $FPID 2>/dev/null
pkill -f "$PWD/$SV" 2>/dev/null
[ -n "$TW_SHARD" ] && echo "shots: $C1/screenshots $C2/screenshots"
echo "done: $OUT"
