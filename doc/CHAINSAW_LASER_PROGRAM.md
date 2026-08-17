# Chainsaw → Laser · arc-native program

Tip pin: `feature/sfa-curve-rgr` @ `47c0f33e` (advance on land).  
Contract: parity or named densify-shim · `t_laser ≤ 1.15 × t_chainsaw` · no silent ConcaveHull · OverlayNGCurve never *Curved*.

Scoreboard (user board): **green 16 · chainsaw-only 3 · measured 11 hold**. See [LASER_RATCHET.md](LASER_RATCHET.md).

## Holds (do not start)

- **ML.2** convex hole-free CurvePolygon MIC
- **HP.4** HotPixel walk
- **N-SS expand** / P2.5.5 as a start signal
- **M.5** continuous Fréchet before DHD owns Curve*
- Full **D-HF TAG** green (keep `fail()` in `CurveAwarenessSpecTest`)

## Sequence locked (this branch)

**Sync → M.3 → R.2 → ML.4 → stop**

| Step | Rung | Do |
|------|------|----|
| Sync | docs | This file + ratchet / seams / epic pointers |
| M.3 | D-HF-IWD | `DirectedHausdorffDistance.isFullyWithinDistance` uses exact Curve* pairs |
| R.2 | DE-9IM | Half-disc / open `CircularString` vs Point / Line / disc |
| ML.4 | LEC | Wire `ObstacleDistance` to a hole-free shell (Proofs #474 candidates exhaustive; not a new theorem) |

## Kit map (abbreviated)

### Metric (M.X)

| Rung | Status |
|------|--------|
| M.0 | Done — distance helpers + two-pair D-HF / Fréchet subset |
| M.1 | Landed — DHD owns Curve* (arc→segment; discs) |
| M.2 | Landed — bulge sensitivity |
| **M.3** | **This sequence** — `isFullyWithinDistance` |
| M.4+ | Hold / later (stadium HD, Fréchet, DistanceOp TAG) |

### Relate (R.X)

| Rung | Status |
|------|--------|
| R.0 | Done — disc DE-9IM lock |
| R.1 | Landed — TOUCH `FF2F01212` public relate |
| **R.2** | **This sequence** — half-disc / CircularString |
| R.3+ | Stadium / CompoundCurve shell — not this stop |

### MIC / LEC (ML.X)

| Rung | Status |
|------|--------|
| ML.0 | Done — disc identity + LEC disc-over-disk |
| ML.1 | Landed — stadium MIC |
| ML.2 | **Hold** |
| ML.3 | Named misses (holed / pinch / annulus) — keep grid |
| **ML.4** | **This sequence** — ObstacleDistance hole-free shell assembly |
| ML.5+ | Apollonius / IsRadiusWithin — after ML.4 |

## Named leftovers (not this stop)

Bar-1 style leftovers may still include: H-DISC · CC-NEST-ANNULUS · R1.6-honesty · R-PR-HALF (until R.2 lands) · TOUCH-int · edge-constrained weighted · HP seams.  
Do not expand DiscreteHausdorff named pairs. Do not densify-then-flag-exact.

## Related

- [LASER_RATCHET.md](LASER_RATCHET.md)
- [OVERLAYNGCURVE_P2_SEAMS.md](OVERLAYNGCURVE_P2_SEAMS.md)
- [METRIC_KIT_MX.md](METRIC_KIT_MX.md)
- [MMF_OPTION_B.md](MMF_OPTION_B.md)
