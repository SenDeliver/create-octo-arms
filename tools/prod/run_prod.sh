#!/bin/bash
# Prod-pack run: fresh world on the installer server (release jar only) + clientProd (pack + release jar + devtools jar).
# usage: [ALLOW_FLIGHT=true] tools/prod/run_prod.sh <rcon scene> <client script> <rcon timeline> <log prefix>
cd "$(dirname "$0")/../.."
SCENE=$1; SCRIPT=$2; TIMELINE=$3; OUT=$4
mkdir -p "$(dirname "$OUT")"
if pgrep -f bootstraplauncher > /dev/null; then echo "a Minecraft is already running; not starting"; exit 1; fi
tools/prod/setup_prod.sh > /dev/null
rm -rf run-prod/server/world run-prod/client/signal run-prod/client/screenshots
tools/prod/server.sh start > /dev/null
cp run-prod/server/console.log "$OUT-boot.log"
python3 tools/prod/rcon_prod.py < "$SCENE" > "$OUT-rcon.txt"
[ -f tools/local.env ] && . tools/local.env
./gradlew runClientProd -Pconnect=localhost:25619 -Ptentaworks.script="$PWD/$SCRIPT" > "$OUT-gradle.log" 2>&1 &
CPID=$!
for i in $(seq 1 150); do grep -q 'Dev joined the game' run-prod/server/console.log && break; kill -0 $CPID 2>/dev/null || break; sleep 2; done
python3 tools/prod/rcon_prod.py < "$TIMELINE" >> "$OUT-rcon.txt"
wait $CPID
tools/prod/server.sh stop > /dev/null
cp run-prod/server/console.log "$OUT-server.log"
cp run-prod/server/logs/debug.log "$OUT-server-debug.log"
cp run-prod/client/logs/latest.log "$OUT-client.log"
cp run-prod/client/logs/debug.log "$OUT-client-debug.log" 2>/dev/null
mkdir -p tools/shots && cp run-prod/client/screenshots/*.png tools/shots/ 2>/dev/null
echo "done: $OUT"
