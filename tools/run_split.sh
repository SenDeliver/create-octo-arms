#!/bin/bash
# Dedicated server + client(s) over localhost. Fresh world each run.
# GRADLE_ARGS (e.g. -PnoJei) go to every gradle run.
# Clients boot while the server boots and join once the scene is done (signal/_connect, DevScript); TW_PREBOOT=0: old flow (client started after the scene, 2 s polls).
# usage: [TW_SPEED_MAX=20] [TW_REACH_COPPER=N] [TW_REACH_NETHERITE=N] [TW_CLIENT_WALK=true] [TW_CLIENT_FORWARD=flat] [TIMELINE=<rcon file run after Dev joins>] [SCRIPT2=<client script for Dev2>] [GAMEMODE=creative] [ANIMALS=true] tools/run_split.sh <rcon scene> <client script> <log prefix>
cd "$(dirname "$0")/.."
SCENE=$1; SCRIPT=$2; OUT=$3
mkdir -p "$(dirname "$OUT")"
for i in $(seq 1 480); do pgrep -f "bootstraplauncher.*$PWD/build/moddev" > /dev/null || break; sleep 0.25; done
if pgrep -f "bootstraplauncher.*$PWD/build/moddev" > /dev/null; then echo "a dev Minecraft of this repo is already running; not starting"; exit 1; fi
tools/setup_run.sh > /dev/null
rm -rf run/server/world run/signal run2/signal
# TW_SPEED_MAX (e.g. 20), TW_REACH_COPPER / TW_REACH_NETHERITE: this run's world overrides those keys (world/serverconfig beats config/)
if [ -n "$TW_SPEED_MAX$TW_REACH_COPPER$TW_REACH_NETHERITE" ] && [ -f run/server/config/tentaworks-server.toml ]; then
  mkdir -p run/server/world/serverconfig
  awk -v sp="$TW_SPEED_MAX" -v rc="$TW_REACH_COPPER" -v rn="$TW_REACH_NETHERITE" '/^\[/{sec=$0}
    sec=="[speed]" && $1=="max" && sp!="" {sub(/=.*/, "= " sp)}
    sec=="[reach]" && $1=="copper" && rc!="" {sub(/=.*/, "= " rc)}
    sec=="[reach]" && $1=="netherite" && rn!="" {sub(/=.*/, "= " rn)} {print}' run/server/config/tentaworks-server.toml > run/server/world/serverconfig/tentaworks-server.toml
fi
# TW_CLIENT_WALK (true|false): this run's client config animation.walkWhileHeld on both clients (default: the mod default, false)
# TW_CLIENT_FORWARD (look|flat): this run's client config movement.forward on both clients (default: the mod default, look)
for d in run run2; do
  rm -f $d/config/tentaworks-client.toml
  [ -n "$TW_CLIENT_WALK" ] && printf '[animation]\n\twalkWhileHeld = %s\n' "$TW_CLIENT_WALK" >> $d/config/tentaworks-client.toml
  [ -n "$TW_CLIENT_FORWARD" ] && printf '[movement]\n\tforward = "%s"\n' "$TW_CLIENT_FORWARD" >> $d/config/tentaworks-client.toml
done
[ -f tools/local.env ] && . tools/local.env
./gradlew runServer $GRADLE_ARGS > "$OUT-server.log" 2>&1 &
SPID=$!
if [ "${TW_PREBOOT:-1}" = 0 ]; then
for i in $(seq 1 150); do grep -q 'RCON running' "$OUT-server.log" && break; grep -qE 'BUILD FAILED' "$OUT-server.log" && break; sleep 2; done
python3 tools/rcon.py < "$SCENE" > "$OUT-rcon.txt"
./gradlew runClient $GRADLE_ARGS -Pconnect=localhost:25609 -Ptentaworks.script="$PWD/$SCRIPT" > "$OUT-client.log" 2>&1 &
CPID=$!
for i in $(seq 1 150); do grep -q 'Dev joined the game' "$OUT-server.log" && break; kill -0 $CPID 2>/dev/null || break; sleep 2; done
if [ -n "$SCRIPT2" ]; then
  ./gradlew runClient2 $GRADLE_ARGS -Pconnect=localhost:25609 -Ptentaworks.script2="$PWD/$SCRIPT2" > "$OUT-client2.log" 2>&1 &
  C2PID=$!
  for i in $(seq 1 150); do grep -q 'Dev2 joined the game' "$OUT-server.log" && break; kill -0 $C2PID 2>/dev/null || break; sleep 2; done
fi
[ -n "$TIMELINE" ] && python3 tools/rcon.py < "$TIMELINE" >> "$OUT-rcon.txt"
wait $CPID
[ -n "$C2PID" ] && wait $C2PID
printf 'tentaworks trace off\nstop\n' | python3 tools/rcon.py >> "$OUT-rcon.txt"
for i in $(seq 1 60); do kill -0 $SPID 2>/dev/null || break; sleep 2; done
else
for i in $(seq 1 1200); do grep -q '> Task :runServer' "$OUT-server.log" && break; grep -qE 'BUILD FAILED' "$OUT-server.log" && break; sleep 0.25; done
./gradlew runClient $GRADLE_ARGS -Ptentaworks.connect=localhost:25609 -Ptentaworks.script="$PWD/$SCRIPT" > "$OUT-client.log" 2>&1 &
CPID=$!
if [ -n "$SCRIPT2" ]; then
  # Dev2 starts once Dev's window exists, so Dev2's window still opens last
  for i in $(seq 1 1200); do grep -q 'Backend library' "$OUT-client.log" && break; kill -0 $CPID 2>/dev/null || break; sleep 0.25; done
  ./gradlew runClient2 $GRADLE_ARGS -Ptentaworks.connect=localhost:25609 -Ptentaworks.script2="$PWD/$SCRIPT2" > "$OUT-client2.log" 2>&1 &
  C2PID=$!
fi
for i in $(seq 1 1200); do grep -q 'RCON running' "$OUT-server.log" && break; grep -qE 'BUILD FAILED' "$OUT-server.log" && break; sleep 0.25; done
python3 tools/rcon.py < "$SCENE" > "$OUT-rcon.txt"
mkdir -p run/signal && touch run/signal/_connect
JOINED=
for i in $(seq 1 1200); do grep -q 'Dev joined the game' "$OUT-server.log" && JOINED=1 && break; kill -0 $CPID 2>/dev/null || break; sleep 0.25; done
if [ -n "$SCRIPT2" ]; then
  mkdir -p run2/signal && touch run2/signal/_connect
  for i in $(seq 1 1200); do grep -q 'Dev2 joined the game' "$OUT-server.log" && break; kill -0 $C2PID 2>/dev/null || break; sleep 0.25; done
fi
if [ -z "$JOINED" ]; then
  echo "Dev never joined; stopping the clients"
  cp run/logs/debug.log "$OUT-client-debug.log" 2>/dev/null; cp run/server/logs/debug.log "$OUT-server-debug.log" 2>/dev/null
  pkill -f "$PWD/build/moddev/clientRun" 2>/dev/null
  pkill -f "$PWD/build/moddev/client2Run" 2>/dev/null
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
pkill -f "$PWD/run/server" 2>/dev/null
echo "done: $OUT"
