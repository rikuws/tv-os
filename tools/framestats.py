#!/usr/bin/env python3
"""Summarise `dumpsys gfxinfo <pkg> framestats` into per-phase medians (ms)."""
import statistics, sys
rows, hdr = [], None
for line in sys.stdin:
    line = line.strip()
    if line.startswith("Flags,IntendedVsync"):
        hdr = line.rstrip(",").split(","); continue
    if hdr and line and line[0].isdigit() and line.count(",") >= len(hdr) - 1:
        v = line.rstrip(",").split(",")
        if len(v) >= len(hdr) and v[0] == "0":
            rows.append(dict(zip(hdr, map(int, v[:len(hdr)]))))
if not rows: sys.exit("no frames")
def ph(a, b): return [(r[b] - r[a]) / 1e6 for r in rows if r[b] > 0 and r[a] > 0]
phases = [("input+anim", "IntendedVsync", "PerformTraversalsStart"),
          ("layout+record (UI)", "PerformTraversalsStart", "SyncQueued"),
          ("sync (RT)", "SyncStart", "IssueDrawCommandsStart"),
          ("issue draw (RT/GPU)", "IssueDrawCommandsStart", "SwapBuffers"),
          ("swap", "SwapBuffers", "FrameCompleted"),
          ("TOTAL", "IntendedVsync", "FrameCompleted")]
print(f"{len(rows)} frames")
for name, a, b in phases:
    d = sorted(ph(a, b))
    if d: print(f"  {name:22s} median {statistics.median(d):6.1f}  p90 {d[int(len(d)*0.9)-1]:6.1f}  max {d[-1]:6.1f}")
