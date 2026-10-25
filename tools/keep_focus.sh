#!/bin/bash
# macOS: while dev Minecraft clients boot they take the keyboard focus (GLFW activates the new window). Run in the background
# alongside them: whenever a java process becomes the front app, the app that was in front before it gets the focus back,
# so the person can keep working. Stops when the file $1 appears or after $2 seconds (default 300). TW_KEEP_FOCUS=0 disables.
[ "$(uname)" = Darwin ] || exit 0
[ "${TW_KEEP_FOCUS:-1}" = 0 ] && exit 0
STOP=$1; LIMIT=${2:-300}
front() { osascript -e 'tell application "System Events" to get name of first application process whose frontmost is true' 2>/dev/null; }
LAST=$(front)
case "$LAST" in java|"") LAST=Finder;; esac
END=$((SECONDS + LIMIT))
while [ $SECONDS -lt $END ]; do
  [ -n "$STOP" ] && [ -e "$STOP" ] && exit 0
  F=$(front)
  if [ "$F" = java ]; then
    osascript -e "tell application \"System Events\" to set frontmost of first application process whose name is \"$LAST\" to true" 2>/dev/null
  elif [ -n "$F" ]; then
    LAST=$F
  fi
  sleep 0.3
done
