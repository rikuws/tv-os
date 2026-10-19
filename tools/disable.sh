#!/bin/bash
# Disable packages for user 0 (never uninstalls). One pm call per package, because
# chained pm commands over adb can silently stop after the first one.
# Each successfully disabled package is appended to backup/disabled-by-tvos.txt,
# which tools/restore.sh reads to undo everything.
#   tools/disable.sh pkg1 pkg2 ...      or      tools/disable.sh -f listfile
DIR="$(cd "$(dirname "$0")/.." && pwd)"
TV="$DIR/tools/tv"
LIST="$DIR/backup/disabled-by-tvos.txt"
mkdir -p "$DIR/backup"; touch "$LIST"

pkgs=()
if [ "${1:-}" = "-f" ]; then
  while read -r p; do pkgs+=("$p"); done < <(grep -v -e '^#' -e '^\s*$' "$2" | awk '{print $1}')
else
  pkgs=("$@")
fi

for p in "${pkgs[@]}"; do
  out=$("$TV" shell "pm disable-user --user 0 $p" 2>&1 | tr -d '\r')
  printf '%-50s %s\n' "$p" "$out"
  if echo "$out" | grep -q "new state: disabled-user"; then
    grep -qx "$p" "$LIST" || echo "$p" >> "$LIST"
  fi
done
