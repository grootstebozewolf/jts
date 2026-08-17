# Proofs Option A — 1M-trial handover (M→S→P refactor 2)

Seed `0xa7ea0001` · N=1000000 · box [-100.0,100.0]² · nChord=64

L1 oracle is the analytic n-gon `n·2r·sin(θ/2n) ≤ rθ`, not a
`densifyArcUniform` polyline (those re-snap start/end off the float
circle).

Sweep owner: `AngleBetween` (`atan2(cross,dot)` + `DirectedSweep`).
Densifier has no private `% 2π` copy.

### L1 inscribed n-gon ≤ r·θ

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 0 |
| soft (ulp tie) | 1 |
| huge-r named | 0 |
| near-full named | 0 |
| agree rate | 1.0 |
| wall ns | 646848364 |

### L2 chord ≤ arc (`2 r sin(θ/2) ≤ rθ` + ulp)

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 0 |
| agree rate | 1.0 |
| wall ns | 561631358 |

### P1 static `length` vs densify polyline

| metric | value |
|---|---:|
| A ns (50k calls) | 20653022 |
| densify ns | 171508403 |
| ratio A/ref | 0.12041988403332052 |

## Verdict

Maintainability: `AngleBetween` is the only `% 2π` / sweep owner;
`DirectedSweep` keeps ccw + θ together; densifier delegates. B
`ArcGeometry` still has a private `normPos`.
Soundness: L1/L2 hard 0. Branch-cut tiny arcs no longer collapse to a
false full turn (B A1 still names densify leftovers).
PERF: 0.120 ≤ 1.15 (B P1 ~0.42 after their ArcGeometry+DD refactor).
