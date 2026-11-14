#!/bin/bash
# Creates the local run dirs (gitignored): muted clients (run/, run2/), RCON-enabled dedicated server.
# RCON password: $RCON_PASSWORD, else the one already in the server dir's server.properties, else random.
# GAMEMODE=creative switches the server default game mode, ANIMALS=true keeps animals (horse in the ride suite); server.properties is rewritten every time.
# TW_SHARD=B..F: a parallel shard, dirs runs/<id>/client, runs/<id>/client2, runs/<id>/server, RCON 25608+100k / game 25609+100k (B k=1, C k=2 ...);
# a new shard server dir gets ops.json, usercache.json and config/tentaworks-server.toml from run/server when it has them.
cd "$(dirname "$0")/.."
case "$TW_SHARD" in
  '') C1=run; C2=run2; SV=run/server; K=0 ;;
  B|C|D|E|F) C1=runs/$TW_SHARD/client; C2=runs/$TW_SHARD/client2; SV=runs/$TW_SHARD/server; K=$(( $(printf %d "'$TW_SHARD") - 65 )) ;;
  *) echo "TW_SHARD must be empty or one of B C D E F, not '$TW_SHARD'"; exit 1 ;;
esac
mkdir -p $C1/config $SV/config $C2/config
if [ $SV != run/server ]; then
  for f in ops.json usercache.json config/tentaworks-server.toml; do
    [ -f run/server/$f ] && { [ ! -f $SV/$f ] || [ run/server/$f -nt $SV/$f ]; } && cp -p run/server/$f $SV/$f
  done
fi
for d in $C1 $C2; do
  [ -f $d/options.txt ] || cat > $d/options.txt <<'OPT'
soundCategory_master:0.0
pauseOnLostFocus:false
onboardAccessibility:false
tutorialStep:none
joinedFirstServer:true
skipMultiplayerWarning:true
narrator:0
OPT
  grep -q '^soundCategory_master:0.0' $d/options.txt || { sed -i '' '/^soundCategory_master:/d' $d/options.txt; echo 'soundCategory_master:0.0' >> $d/options.txt; }
  sed -i '' '/^key_key.tentaworks./d' $d/options.txt
  grep -q '^pauseOnLostFocus:false' $d/options.txt || { sed -i '' '/^pauseOnLostFocus:/d' $d/options.txt; echo 'pauseOnLostFocus:false' >> $d/options.txt; }
  # no FML early window: it takes the focus before the mod can ask for a window that does not (DevWindowFocusMixin)
  if [ -f $d/config/fml.toml ]; then sed -i '' 's/^earlyWindowControl = true/earlyWindowControl = false/' $d/config/fml.toml
  else printf 'earlyWindowControl = false\n' > $d/config/fml.toml; fi
done
echo 'eula=true' > $SV/eula.txt
PW=$(sed -n 's/^rcon.password=//p' $SV/server.properties 2>/dev/null)
PW=${RCON_PASSWORD:-${PW:-$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 20)}}
cat > $SV/server.properties <<PROPS
online-mode=false
enable-rcon=true
rcon.port=$((25608 + 100 * K))
rcon.password=$PW
server-port=$((25609 + 100 * K))
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
spawn-monsters=false
spawn-animals=${ANIMALS:-false}
spawn-npcs=false
difficulty=peaceful
gamemode=${GAMEMODE:-survival}
force-gamemode=true
allow-flight=false
view-distance=8
simulation-distance=8
motd=Tentaworks dev
PROPS
echo "run dirs ready"
