# OrientableSegment adapter — 1M-trial handover

Bible §3: ExactCircularArc privileged; these trials cover the optional OrientableSegment side/intersect adapters only.

Seed `0xc0ffeeb007` · N=1000000 · box [-100.0,100.0]² · densify nChord=64 (via ExactCircularArc.pointAt)

### S1 straight orientationIndex vs Orientation.index

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 0 |
| soft agree (on-curve / collinear tie) | 0 |
| agree rate (1 - hard/tried) | 1.0 |
| wall ns | 170300766 |

### S2 straight intersects vs RobustLineIntersector

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 0 |
| soft agree (on-curve / collinear tie) | 0 |
| agree rate (1 - hard/tried) | 1.0 |
| wall ns | 358822845 |

### A1 arc orientationIndex vs densify reference

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 3849 |
| soft agree (on-curve / collinear tie) | 0 |
| agree rate (1 - hard/tried) | 0.996151 |
| wall ns | 4316500697 |

### A2 arc×segment intersects vs densify+RLI

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 4 |
| soft agree (on-curve / collinear tie) | 0 |
| agree rate (1 - hard/tried) | 0.999996 |
| wall ns | 4315755173 |

### P1 arc orientationIndex latency vs densify

| metric | value |
|---|---:|
| B p50 ns (50k calls) | 16581896 |
| densify p50 ns | 141057842 |
| ratio B/ref | 0.11755387552292201 |


## Verdict

Optional OrientableSegment adapters: straight parity 100%; arc vs densify ≥ 0.99 (A1=0.996151, A2=0.999996); PERF p50 ratio 0.11755387552292201 ≤ 1.15.

Residual A1 hard disagrees are densify-chord vs arc-tangent frame disagreements off the curve (nChord=64), not silent flatten. ExactCircularArc remains the privileged primitive (Bible §3).
