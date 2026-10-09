#!/bin/bash
# start|stop the prod-pack dedicated server (Java 21, NeoForge installer layout). Log: run-prod/server/console.log
# Java: $JAVA21_HOME (set it in tools/local.env), else the first Gradle-provisioned JDK 21, else java on PATH.
cd "$(dirname "$0")/../.."
[ -f tools/local.env ] && . tools/local.env
cd run-prod/server
[ -n "$JAVA21_HOME" ] || JAVA21_HOME=$(ls -d ~/.gradle/jdks/*21*/jdk-21*/Contents/Home ~/.gradle/jdks/*21* 2>/dev/null | head -1)
J=${JAVA21_HOME:+$JAVA21_HOME/bin/}java
case "$1" in
  start)
    if pgrep -f bootstraplauncher > /dev/null; then echo "a Minecraft is already running; not starting"; pgrep -fl bootstraplauncher | cut -c1-120; exit 1; fi
    rm -f console.log
    nohup "$J" @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.250/unix_args.txt nogui > console.log 2>&1 < /dev/null &
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
