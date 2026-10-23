#!/bin/bash
# start|stop the prod-pack dedicated server (Java 17, Forge 47 installer layout). Log: run-prod/server/console.log
# Java: $JAVA17_HOME (set it in tools/local.env), else the first Gradle-provisioned JDK 17, else java on PATH.
cd "$(dirname "$0")/../.."
[ -f tools/local.env ] && . tools/local.env
cd run-prod/server
[ -n "$JAVA17_HOME" ] || JAVA17_HOME=$(ls -d ~/.gradle/jdks/*-17-*/jdk-17*/Contents/Home 2>/dev/null | head -1)
J=${JAVA17_HOME:+$JAVA17_HOME/bin/}java
ARGS=$(ls libraries/net/minecraftforge/forge/1.20.1-*/unix_args.txt 2>/dev/null | tail -1)
case "$1" in
  start)
    if pgrep -f bootstraplauncher > /dev/null; then echo "a Minecraft is already running; not starting"; pgrep -fl bootstraplauncher | cut -c1-120; exit 1; fi
    [ -n "$ARGS" ] || { echo "no Forge 1.20.1 install in run-prod/server (tools/prod/setup_prod.sh says how)"; exit 1; }
    "$J" -version 2>&1 | grep -q 'version "17' || { echo "not a Java 17: $J"; exit 1; }
    rm -f console.log
    nohup "$J" @user_jvm_args.txt @$ARGS nogui > console.log 2>&1 < /dev/null &
    echo $! > server.pid
    for i in $(seq 1 120); do grep -q 'RCON running' console.log && break; kill -0 $(cat server.pid) 2>/dev/null || break; sleep 2; done
    grep -E 'Done \(' console.log | tail -1
    ;;
  stop)
    [ -f server.pid ] || exit 0
    printf 'stop\n' | python3 ../../tools/prod/rcon_prod.py > /dev/null 2>&1
    for i in $(seq 1 60); do kill -0 $(cat server.pid) 2>/dev/null || break; sleep 2; done
    kill $(cat server.pid) 2>/dev/null; rm -f server.pid; echo stopped
    ;;
esac
