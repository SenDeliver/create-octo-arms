#!/bin/bash
# Creates the local run dirs (gitignored): muted clients (run/, run2/), RCON-enabled dedicated server.
# RCON password: $RCON_PASSWORD, else the one already in run/server/server.properties, else random.
# GAMEMODE=creative switches the server default game mode, ANIMALS=true keeps animals (horse in the ride suite); server.properties is rewritten every time.
cd "$(dirname "$0")/.."
mkdir -p run/config run/server/config run2/config
for d in run run2; do
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
echo 'eula=true' > run/server/eula.txt
PW=$(sed -n 's/^rcon.password=//p' run/server/server.properties 2>/dev/null)
PW=${RCON_PASSWORD:-${PW:-$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 20)}}
cat > run/server/server.properties <<PROPS
online-mode=false
enable-rcon=true
rcon.port=25608
rcon.password=$PW
server-port=25609
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
