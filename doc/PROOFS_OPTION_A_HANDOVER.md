# Proofs Option A — 1M-trial handover (post-refactor)

Seed `0xa7ea0001` · N=1000000 · box [-100.0,100.0]² · nChord=64

L1 oracle is the analytic n-gon `n·2r·sin(θ/2n) ≤ rθ`, not a
`densifyArcUniform` polyline (those re-snap start/end off the float
circle).

### L1 inscribed n-gon ≤ r·θ

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 0 |
| soft (ulp tie) | 1 |
| huge-r named | 0 |
| near-full named | 0 |
| agree rate | 1.0 |
| wall ns | 604456259 |

### L2 chord ≤ arc (`2 r sin(θ/2) ≤ rθ` + ulp)

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 0 |
| agree rate | 1.0 |
| wall ns | 557066136 |

### P1 static `length` vs densify polyline

| metric | value |
|---|---:|
| A ns (50k calls) | 22669024 |
| densify ns | 175927827 |
| ratio A/ref | 0.12885411243100275 |

## Verdict

Maintainability: `AngleBetween` owns sweep; one circumcircle/r.
Soundness: L1/L2 hard 0 (B A1 still names thousands of densify leftovers).
PERF: 0.129 ≤ 1.15 (B P1 ~0.42 after their ArcGeometry+DD refactor).
