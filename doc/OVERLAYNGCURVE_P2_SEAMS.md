# OverlayNGCurve seams · P2.0

A seam is where a kit returns null and the chordsaw takes over.

**Scoreboard (user 2026-08-17):** sewn 28 · stamped 6 · open 1 · named-miss 5 · total 35

Synced onto SoT session @ `c956b50d`. Contract: Draft v6 MMF Option B — `SegmentKind`, no silent linearize, name **OverlayNGCurve** never *Curved*, slack 15%.

**Named-miss leftover (honest):** H-ANNULUS-TANGENT refuse · open-arc H-DISC lineal · N-SS full hierarchy deferred. R-PR-HALF sewn. HP.4 stamped `SHARED_SNAPPED_RAY` (not a walk).

## Updates from B/C/D/E work

| id | change |
|----|--------|
| **H-SHELL-N-MIXED** | Public overlay **sewn** via `OverlayNGCircle` (R-OV / P2.5.5 Option B first slice). R1.7 kit still refuses (`assertNull`). |
| **H-ANNULUS-TANGENT** | Stay refuse punch; public path chordsaw + named stamp test. |
| **H-DISC** | Closed full-circle `CircularString` routes to `CircularDiscOverlay`; open arcs stay lineal R-AA / named miss. |
| **N-SS / P2.5.5** | **Started** via `OverlayNGCircle` + `CircularNodedSegmentString` / `SegmentKind`. Full 74-file hierarchy remains deferred. |

## Table

| id | kit | nodes | why | status | next |
|----|-----|-------|-----|--------|------|
| **R0** | OverlayNGCurve algebra | — | Self / empty identities. No geometry walk. | sewn | done |
| **R1** | retainOperand | 0 (containment or miss) | Retention when representable. Covering SUB/XOR skip — punched by D4 instead. | sewn | done |
| **R1.5-cross** | CircularDiscOverlay | 2 proper | Lens / blob / crescents. Closed form. | sewn | done |
| **R1.5-D4** | CircularDiscOverlay.nestedAnnulus | 0 (strict nest) | Punch inner ring. 16π, covers EEEE / coveredBy EE0E. | sewn | done · jts#10 |
| **H-ANNULUS-TANGENT** | CircularDiscOverlay.nestedAnnulus | 1 · d+r = R | Not strictly inside. Zero-width pinch. Noder names a degenerate edge at (5 0). Overlay does not punch. | stamped | keep refuse · public chordsaw |
| **TOUCH-ext** | CircularDiscOverlay / R1 | 1 · d = r1+r2 | CAP = {kiss}, regularized ∅. Noder zero-length edge. Oracle EXT_TANGENT FF2F01212. | sewn | done noder · overlay stays null |
| **TOUCH-int** | covers / nestedAnnulus | 1 · d = \|R−r\| | covers fires. SUB annulus pinches (V1). | stamped | same as H-ANNULUS-TANGENT |
| **R1.6-2** | TwoNodeClip | 2 | Walk the two nodes. Closed. | sewn | done |
| **H-FOUR** | NSpanClip | even ≥ 4 | One even-n assemble. Closed. | sewn | done |
| **R1.6-honesty** | CircularDiscPolygonOverlay | 0 line–circle | KEEP. Covering square minus CIRCLE_3 has 0 line–circle nodes. | stamped | keep |
| **H-SHELL-2** | TwoShellClip | 0 / 1 / 2 | Two-node lens, 0-node nest, 1-node CAP empty. | sewn | done |
| **H-SHELL-N** | NSpanClip | even ≥ 4 | Same assemble as H-FOUR. | sewn | done |
| **H-SHELL-N-ODD** | NSpanClip | 2 + 1 tangent | Zero-length opposite-label span, then even walk. | sewn | done · jts#11 |
| **H-SHELL-N-MIXED** | OverlayNGCircle (R-OV) | interval / noder | Collinear diameter overlap. R1.7 kit null; public OverlayNGCurve exact via Option B noder. | sewn | done · OverlayNGCircle |
| **H-SAME-CIRCLE** | CircleSweepOverlay | sweep intervals | Overlap as interval on one circle. | sewn | done |
| **H-DISC** | CircularDiscOverlay (closed CS) / CircularArcOverlay (open) | — | Closed full-circle CircularString → disc kit. Open arcs stay lineal CAP. | sewn / miss | closed route done |
| **R-LL** | CircularLineOverlay | line–circle hits | CAP Point/MultiPoint. | sewn | done |
| **R-AA** | CircularArcOverlay | sweep-filtered | Exact Point(s) when arcs meet. | sewn | done |
| **SAME-OUTER-HOLE** | SameOuterHoleOverlay | 0 | Punch. | sewn | done |
| **DIFF-OUTER-HOLE** | DifferentOuterHoleOverlay | 0 | In → punch. Out → ignore on CAP. | sewn | done |
| **H-SHELL-HOLE-CROSS** | BiteVsHole | even-n | Bite, not punch. | sewn | done · jts#18 |
| **H-SHELL-HOLE-X** | TwoHoleOverlay | hole–hole | 3-ring. Exact. | sewn | done · jts#19 |
| **H-SHELL-HOLE-OUTER** | BiteVsHole | interval | Non-crossing cousin. | sewn | done · jts#20 |
| **P2.3** | BiteVsHole | (0 1)/(0 2) | First face walk. | sewn | done · jts#18 |
| **CC-DISC-NEST** | CircularDiscOverlay.nestedAnnulus | 0 | Two-arc CC disc = D4. | sewn | done · jts#15 |
| **CC-NEST-ANNULUS** | R1.7 nest punch | 0 | Area 25π−(4+π). | sewn | done · jts#28 |
| **P2.1** | CurveSegmentString + Noder | 2/4/3 | Package-private. | sewn | done · jts#16 |
| **P2.2** | CurveSegmentNoder.edges | interval | Shared run is an edge. | sewn | done · jts#17 |
| **P2.4** | TwoHoleOverlay | (0.5 1)/(1 1.5) | 3-ring walk. | sewn | done · jts#19 |
| **P2.5.2** | CurveSegmentNoder.nodes(N) | union | All-pairs. | sewn | done · jts#23 |
| **P2.5.3** | CurveSegmentFaces | faces | Leave-angle walk. | sewn | done · jts#24 |
| **P2.5.4** | coincident leave | (±1,0)+(0,5) | Named stamp. | stamped | done · jts#25 |
| **HP** | CurveHotPixel | arc ∩ pixel | HP.4: faces after snap → `SHARED_SNAPPED_RAY` stamp. | stamped | done · not a walk · not HP.5 |
| **R-PR-HALF** | CurveExact.relate + HalfDisc | — | Sewn by R.2. | sewn | done |
| **N-SS / P2.5.5** | OverlayNGCircle + SegmentKind | N | MIXED + **proper-crossing two-shell expand**. Full 74-file hierarchy deferred. | open / expanded | deliberate Option B only |

## Named misses / refuses (honest)

- **H-ANNULUS-TANGENT** — overlay does not punch; chordsaw / stamp
- **H-DISC (open arcs)** — not filled discs; lineal R-AA
- **P2.5.4** — stamp, not HotPixel walk
- **HP** — stamped; not M.1 / ML.2
- **N-SS full hierarchy** — deferred beyond OverlayNGCircle
