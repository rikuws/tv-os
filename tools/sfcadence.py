#!/usr/bin/env python3
"""Delivered frame cadence from `dumpsys SurfaceFlinger --latency <layer>`.
Counts how many displayed frames came 1, 2, 3+ vsyncs after the previous one."""
import sys
lines = [l.split() for l in sys.stdin if l.strip()]
period = next(int(l[0]) for l in lines if len(l) == 1)
t = sorted({int(l[1]) for l in lines if len(l) == 3 and 0 < int(l[1]) < 2**62})   # merged samples
gaps = [b - a for a, b in zip(t, t[1:])]
burst = [g for g in gaps if g < 6 * period]          # ignore idle pauses between animations
if not burst: sys.exit("no frames")
hist = {}
for g in burst:
    k = min(3, round(g / period)); hist[k] = hist.get(k, 0) + 1
n = len(burst)
fps = 1e9 / (sum(burst) / n)
print(f"{n} frame gaps in animation: " + ", ".join(f"{k}{'+' if k == 3 else ''} vsync: {v} ({100*v/n:.0f}%)" for k, v in sorted(hist.items())) + f"  ~ {fps:.0f} fps")
