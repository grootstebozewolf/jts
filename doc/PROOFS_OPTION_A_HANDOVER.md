# Proofs Option A — 1M-trial handover (Year-1 bible lock)

Architecture: [`EXACT_CURVE_BIBLE.md`](EXACT_CURVE_BIBLE.md).
Package: `org.locationtech.jts.algorithm.exactcurve`.

Seed `0xa7ea0001` · N=1000000 · box [-100.0,100.0]² · nChord=64

L1 oracle is the analytic n-gon `n·2r·sin(θ/2n) ≤ rθ`.

### L1 inscribed n-gon ≤ r·θ

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 0 |
| soft (ulp tie) | 1 |
| huge-r named | 0 |
| near-full named | 0 |
| agree rate | 1.0 |
| wall ns | 616237602 |

### L2 chord ≤ arc (`2 r sin(θ/2) ≤ rθ` + ulp)

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 0 |
| agree rate | 1.0 |
| wall ns | 557191248 |

### P1 static `length` vs densify polyline

| metric | value |
|---|---:|
| A ns (50k calls) | 20369346 |
| densify ns | 168346101 |
| ratio A/ref | 0.12099683853087871 |

## Verdict

Year-1 lock holds: ExactCircularArc is the privileged primitive;
`toLinear` is the only densify path; L1/L2 hard 0; P1 0.121 ≤ 1.15.
