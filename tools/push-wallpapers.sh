#!/bin/bash
# Sync wallpapers/*.jpg|png|webp to the launcher's private folder on the TV.
# Only changed files are sent; photos deleted locally are deleted on the TV too.
# The home screen picks a new one from this set every time you return to it.
#   tools/push-wallpapers.sh            # sync ./wallpapers
#   tools/push-wallpapers.sh ~/Pictures/tv   # sync another folder
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)"
TV="$DIR/tools/tv"
SRC="${1:-$DIR/wallpapers}"
PKG=fi.koti.launcher
TMP=/data/local/tmp/koti-wall

# Koti's WallpaperProvider (shell-only) manages its private photo folder; works on the
# release build, where run-as is unavailable.
URI=content://$PKG.files/wallpapers
remote=$("$TV" shell "content query --uri $URI" | sed -n 's/.*name=\([^,]*\), md5=\([0-9a-f]*\).*/\2  \1/p' || true)
"$TV" shell "mkdir -p $TMP && chmod 755 $TMP"

local_names=()
shopt -s nullglob nocaseglob
for f in "$SRC"/*.jpg "$SRC"/*.jpeg "$SRC"/*.png "$SRC"/*.webp; do
  name=$(basename "$f" | tr ' ' '_')
  local_names+=("$name")
  sum=$(md5 -q "$f")
  if echo "$remote" | grep -q "^$sum  $name\$"; then
    echo "unchanged  $name"
    continue
  fi
  "$TV" push "$f" "$TMP/$name" >/dev/null
  "$TV" shell "content write --uri $URI/$name < '$TMP/$name' && rm '$TMP/$name'"
  echo "pushed     $name"
done

# Remove photos that are no longer in the local folder.
for r in $(echo "$remote" | awk '{print $2}'); do
  keep=0
  for l in "${local_names[@]:-}"; do [ "$l" = "$r" ] && keep=1; done
  if [ $keep -eq 0 ]; then
    "$TV" shell "content delete --uri $URI/$r" >/dev/null
    echo "removed    $r"
  fi
done
"$TV" shell "rmdir $TMP 2>/dev/null; true"
echo "$(echo "${local_names[@]:-}" | wc -w | tr -d ' ') photos in rotation"
