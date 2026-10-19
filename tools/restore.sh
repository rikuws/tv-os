#!/bin/bash
# Undo the debloat: re-enable every package listed in disabled-by-tvos.txt
# (written by tools/disable.sh), re-enable the stock launcher and make it the
# default home again. Pass package names to re-enable just those instead.
#
#   tools/restore.sh                      # re-enable everything we disabled
#   tools/restore.sh com.sony.dtv.promos  # re-enable one package
#   tools/restore.sh --keep-launcher      # re-enable apps, keep the custom launcher
set -u
DIR="$(cd "$(dirname "$0")/.." && pwd)"
TV="$DIR/tools/tv"
LIST="$DIR/backup/disabled-by-tvos.txt"
KEEP_LAUNCHER=0

pkgs=()
for a in "$@"; do
  case "$a" in
    --keep-launcher) KEEP_LAUNCHER=1 ;;
    *) pkgs+=("$a") ;;
  esac
done

if [ ${#pkgs[@]} -eq 0 ]; then
  [ -f "$LIST" ] || { echo "nothing to restore: $LIST not found"; exit 0; }
  while read -r p; do [ -n "$p" ] && pkgs+=("$p"); done < <(grep -v '^#' "$LIST" | awk '{print $1}')
fi

if [ "$KEEP_LAUNCHER" -eq 1 ]; then
  kept=(); for p in "${pkgs[@]}"; do [ "$p" != com.google.android.tvlauncher ] && kept+=("$p"); done
  pkgs=("${kept[@]}")
fi
[ ${#pkgs[@]} -eq 0 ] && { echo "nothing to restore"; exit 0; }

# One pm command per shell call: chained pm commands can silently stop after the first.
for p in "${pkgs[@]}"; do
  printf '%-55s ' "$p"
  "$TV" shell "pm enable --user 0 $p" | tr -d '\n'; echo
done

# Koti's screensaver replaced Google Ambient; point the system back at Ambient with it.
if printf '%s\n' "${pkgs[@]}" | grep -qx com.google.android.backdrop; then
  "$TV" shell "settings put secure screensaver_components com.google.android.backdrop/.Backdrop"
  "$TV" shell "settings put secure screensaver_default_component com.google.android.backdrop/.Backdrop"
  echo "screensaver restored to Google Ambient"
fi

if [ "$KEEP_LAUNCHER" -eq 0 ] && printf '%s\n' "${pkgs[@]}" | grep -qx com.google.android.tvlauncher; then
  "$TV" shell "cmd package set-home-activity com.google.android.tvlauncher/.MainActivity"
  "$TV" shell "input keyevent KEYCODE_HOME" >/dev/null
  echo "stock launcher restored as default home"
fi

# Drop restored packages from the list so it keeps reflecting what is still disabled.
if [ -f "$LIST" ]; then
  tmp=$(mktemp)
  grep -v -x -F -f <(printf '%s\n' "${pkgs[@]}") "$LIST" > "$tmp" || true
  mv "$tmp" "$LIST"
fi
