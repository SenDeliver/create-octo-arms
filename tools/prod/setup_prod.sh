#!/bin/bash
# Prod-pack test dirs (gitignored run-prod/): NeoForge 21.1.250 dedicated server (installer) + clientProd run dir.
# Server mods = live client pack minus client-only jars + release jar. Client mods = full pack + release jar + test-only devtools jar.
# ALLOW_FLIGHT=true mirrors prod server.properties (default false: strictest, vanilla float kick on).
# PACK=<dir of modpack jars> (never modified; default ../client-mods next to the repo). RCON password: $RCON_PASSWORD, else kept, else random.
# usage: [PACK=dir] tools/prod/setup_prod.sh [--no-devtools]
cd "$(dirname "$0")/../.."
PACK=${PACK:-../client-mods}
JAR=build/libs/octo-arms-$(sed -n "s/^mod_version=//p" gradle.properties)+mc1.21.1.jar
CLIENT_ONLY='^(xaerominimap|MouseTweaks|InventoryProfilesNext|libIPN)'
S=run-prod/server; C=run-prod/client
[ -f $S/libraries/net/neoforged/neoforge/21.1.250/unix_args.txt ] || { echo "install the server first: mkdir -p $S && cd $S && java -jar neoforge-21.1.250-installer.jar --installServer"; exit 1; }
rm -rf $S/mods $C/mods; mkdir -p $S/mods $C/mods $C/config
for j in $PACK/*.jar; do
  n=$(basename "$j")
  cp "$j" $C/mods/
  echo "$n" | grep -qE "$CLIENT_ONLY" || cp "$j" $S/mods/
done
cp $JAR $S/mods/
cp $JAR $C/mods/
[ "$1" = "--no-devtools" ] || cp build/devlibs/tentaworks-devtools-$(sed -n "s/^mod_version=//p" gradle.properties).jar $C/mods/
echo 'eula=true' > $S/eula.txt
echo '-Xms3G -Xmx3G' > $S/user_jvm_args.txt
PW=$(sed -n 's/^rcon.password=//p' $S/server.properties 2>/dev/null)
PW=${RCON_PASSWORD:-${PW:-$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 20)}}
cat > $S/server.properties <<PROPS
online-mode=false
enforce-secure-profile=false
allow-flight=${ALLOW_FLIGHT:-false}
enable-rcon=true
rcon.port=25618
rcon.password=$PW
server-port=25619
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
difficulty=peaceful
gamemode=${GAMEMODE:-survival}
force-gamemode=true
view-distance=8
simulation-distance=8
motd=Tentaworks prod-pack test
PROPS
if [ -f $S/config/sable-server.toml ]; then sed -i '' 's/^\([[:space:]]*sub_level_substeps_per_tick[[:space:]]*=\).*/\1 2/' $S/config/sable-server.toml; fi
[ -f $C/options.txt ] || printf 'onboardAccessibility:false\ntutorialStep:none\njoinedFirstServer:true\nskipMultiplayerWarning:true\nnarrator:0\n' > $C/options.txt
for kv in soundCategory_master:0.0 pauseOnLostFocus:false; do
  k=${kv%%:*}; sed -i '' "/^$k:/d" $C/options.txt; echo "$kv" >> $C/options.txt
done
sed -i '' '/^key_key.tentaworks./d' $C/options.txt
echo "server mods: $(ls $S/mods | wc -l)  client mods: $(ls $C/mods | wc -l)"
