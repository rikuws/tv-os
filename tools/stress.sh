#!/bin/bash
# Koti performance stress test.
#
# Keys are injected as raw events on the IR remote's input device (/dev/input/event0)
# with `sendevent`, exactly like the physical remote. (`input keyevent` starts a Java VM
# per call, which on this 4x1 GHz CPU steals enough time to make any app look janky.)
#
# The headline metric is what reaches the screen: SurfaceFlinger's present timestamps,
# reported as the share of frames that arrived on the very next vsync (60 fps). gfxinfo's
# "janky frames" is not used: this TV's compositor is double-buffered, so nearly every
# frame exceeds gfxinfo's 16 ms latency threshold even when the screen updates every vsync.
#   tools/stress.sh
set -u
DIR="$(cd "$(dirname "$0")/.." && pwd)"
TV="$DIR/tools/tv"
PKG=fi.koti.launcher
LAYER="$PKG/$PKG.MainActivity#0"

# Device-side helpers: press <linux keycode>; click <code> <times> <gap seconds>
H='DEV=/dev/input/event0; press() { sendevent $DEV 1 $1 1; sendevent $DEV 0 0 0; sendevent $DEV 1 $1 0; sendevent $DEV 0 0 0; }; click() { n=0; while [ $n -lt $2 ]; do press $1; sleep $3; n=$((n+1)); done; }; R=106; L=105; U=103; D=108; OK=352; BACK=158'

segment() {   # segment <label> <device commands>
  # SurfaceFlinger keeps only the last 128 frames, so sample it every second during the
  # segment and merge, so every frame of the segment counts (not just its tail).
  "$TV" shell "rm -f /data/local/tmp/lat.txt; dumpsys SurfaceFlinger --latency-clear >/dev/null;
    (while [ ! -f /data/local/tmp/lat.stop ]; do dumpsys SurfaceFlinger --latency '$LAYER' >> /data/local/tmp/lat.txt; sleep 1; done) &
    sh -c '$H; $2'; sleep 0.5; touch /data/local/tmp/lat.stop; sleep 1.2; rm -f /data/local/tmp/lat.stop" >/dev/null
  printf '  %-34s ' "$1"
  "$TV" shell "cat /data/local/tmp/lat.txt" | "$DIR/.venv/bin/python" "$DIR/tools/sfcadence.py"
}

"$TV" shell "input keyevent KEYCODE_HOME" >/dev/null
sleep 4
echo "frames on time (next vsync) per segment:"
segment "dock, fast clicks (150 ms)"        'for i in 1 2 3; do click $R 4 0.15; click $L 4 0.15; done'
segment "dock, art change every press"      'for i in 1 2 3; do click $R 2 0.4; click $L 2 0.4; done'
segment "dock, mashing (50 ms)"             'for i in 1 2 3 4; do click $R 4 0.05; click $L 4 0.05; done; sleep 0.8'
segment "continue row, fast clicks"         'click $D 1 0.4; for i in 1 2; do click $R 6 0.15; click $L 6 0.15; done'
segment "continue row, mashing"             'for i in 1 2 3; do click $R 8 0.05; click $L 8 0.05; done; sleep 0.6; click $U 1 0.4'
segment "rows up/down"                      'for i in 1 2 3 4 5; do click $D 1 0.3; click $U 1 0.3; done'
segment "sidebar open/close"                'for i in 1 2 3; do click $L 5 0.12; sleep 0.6; click $BACK 1 0.7; done'
segment "apps page open, walk, close"       'click $L 5 0.12; sleep 0.4; click $D 1 0.3; click $OK 1 1.0; for i in 1 2; do click $R 4 0.15; click $D 1 0.2; click $L 4 0.15; click $U 1 0.2; done; click $BACK 1 1.0'
segment "carousel crossfades (idle on dock)" 'click $U 1 0.5; sleep 22'

echo "memory and start-up:"
"$TV" shell "dumpsys meminfo $PKG | grep -E 'TOTAL:' | head -1" | sed 's/^ */  memory (PSS kB) /'
"$TV" shell "am force-stop $PKG; logcat -c" >/dev/null
sleep 1
"$TV" shell "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME" | grep -E "TotalTime" | sed 's/^/  cold start, first frame (ms): /'
sleep 4
"$TV" shell "logcat -d -s ActivityManager:I | grep 'Fully drawn' | grep $PKG" | sed 's/.*+/  cold start, dock drawn: +/'
"$TV" shell "am start -a android.settings.SETTINGS" >/dev/null
sleep 3
"$TV" shell "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME" | grep -E "TotalTime" | sed 's/^/  return home (ms): /'
