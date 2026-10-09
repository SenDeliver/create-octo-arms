#!/bin/bash
# Dedicated server + client(s) over localhost. Fresh world each run.
# GRADLE_ARGS (e.g. -PnoJei) go to every gradle run.
# usage: [TW_SPEED_MAX=20] [TW_REACH_COPPER=N] [TW_REACH_NETHERITE=N] [TIMELINE=<rcon file run after Dev joins>] [SCRIPT2=<client script for Dev2>] [GAMEMODE=creative] tools/run_split.sh <rcon scene> <client script> <log prefix>
cd "$(dirname "$0")/.."
SCENE=$1; SCRIPT=$2; OUT=$3
mkdir -p "$(dirname "$OUT")"
for i in $(seq 1 60); do pgrep -f bootstraplauncher > /dev/null || break; sleep 2; done
if pgrep -f bootstraplauncher > /dev/null; then echo "a Minecraft is already running; not starting"; exit 1; fi
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
[ -f tools/local.env ] && . tools/local.env
./gradlew runServer $GRADLE_ARGS > "$OUT-server.log" 2>&1 &
SPID=$!
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
kill $SPID 2>/dev/null
wait $SPID 2>/dev/null
pkill -f "$PWD/run/server" 2>/dev/null
echo "done: $OUT"
