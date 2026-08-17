# Metric kit M.X

Tip on PR #7 SoT. Contract: Draft v6 MMF Option B · PERF-GATE 15% · OverlayNGCurve never *Curved*.

| Rung | Status |
|------|--------|
| **M.0** | Distance helpers + D-HF two-pair + Fréchet subset (partial) |
| **M.1** | **Landed** — `DirectedHausdorffDistance` owns Curve* for the same two certified pairs (arc→segment apex; two discs). Control polyline still chord lie. `DirectedHausdorffDistanceCurveTest` + PERF-GATE. |
| **M.2+** | Not started (bulge / IWD / stadium / continuous Fréchet / DistanceOp TAG) |

Do not expand DiscreteHausdorff named pairs. Full D-HF TAG stays `fail()` in `CurveAwarenessSpecTest`.
