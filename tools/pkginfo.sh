#!/bin/bash
# Print version + unique components (activities/services/receivers/providers) for each package given.
DIR="$(cd "$(dirname "$0")" && pwd)"
for p in "$@"; do
  echo "################ $p"
  "$DIR/tv" shell "dumpsys package $p" > /tmp/.pkg.$$ 2>&1
  grep -m3 -E "versionName=|lastUpdateTime=|firstInstallTime=" /tmp/.pkg.$$ | sed 's/^ */   /'
  grep -oE "$p/[A-Za-z0-9_.\$]+" /tmp/.pkg.$$ | sort -u | sed 's/^/   /' | head -40
  grep -oE "Action: \"[^\"]+\"" /tmp/.pkg.$$ | sort -u | head -15 | sed 's/^/     /'
done
rm -f /tmp/.pkg.$$
