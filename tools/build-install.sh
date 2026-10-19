#!/bin/bash
# Build the Koti launcher, install it on the TV, grant Watch Next access, compile it
# ahead of time, and (with --set-home) make it the default home and disable the stock
# launcher. Builds the release variant (not debuggable: ~2x faster start-up) unless
# --debug is given.
#   tools/build-install.sh              # release build + install + restart Koti
#   tools/build-install.sh --set-home   # also set as default home, disable stock launcher
#   tools/build-install.sh --debug      # debuggable build (for run-as / debugging)
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)"
TV="$DIR/tools/tv"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
PKG=fi.koti.launcher
VARIANT=release
for a in "$@"; do [ "$a" = "--debug" ] && VARIANT=debug; done
TASK=assemble$(echo "${VARIANT:0:1}" | tr a-z A-Z)${VARIANT:1}
APK="$DIR/launcher/app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"

(cd "$DIR/launcher" && ./gradlew --no-daemon -q "$TASK")
"$TV" install "$APK"
"$TV" shell "pm grant $PKG android.permission.READ_TV_LISTINGS"
# Ahead-of-time compile everything ("speed") rather than waiting for idle-time profiling.
[ "$VARIANT" = release ] && "$TV" shell "cmd package compile -m speed -f $PKG" >/dev/null

if [[ " $* " == *" --set-home "* ]]; then
  "$TV" shell "cmd package set-home-activity $PKG/.MainActivity"
  out=$("$TV" shell "pm disable-user --user 0 com.google.android.tvlauncher")
  echo "$out"
  if echo "$out" | grep -q "disabled-user"; then
    grep -qx com.google.android.tvlauncher "$DIR/backup/disabled-by-tvos.txt" 2>/dev/null \
      || echo com.google.android.tvlauncher >> "$DIR/backup/disabled-by-tvos.txt"
  fi
fi

"$TV" shell "am force-stop $PKG"
"$TV" shell "input keyevent KEYCODE_HOME"
