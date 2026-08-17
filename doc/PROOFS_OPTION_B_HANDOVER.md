# Proofs Option B — 1M-trial handover

Seed `0xc0ffeeb007` · N=1000000 · box [-100.0,100.0]² · densify nChord=64

### S1 straight orientationIndex vs Orientation.index

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 0 |
| soft agree (on-curve / collinear tie) | 0 |
| agree rate (1 - hard/tried) | 1.0 |
| wall ns | 178139918 |

### S2 straight intersects vs RobustLineIntersector

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 0 |
| soft agree (on-curve / collinear tie) | 0 |
| agree rate (1 - hard/tried) | 1.0 |
| wall ns | 367162802 |

### A1 arc orientationIndex vs densify reference

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 3849 |
| soft agree (on-curve / collinear tie) | 0 |
| agree rate (1 - hard/tried) | 0.996151 |
| wall ns | 5147315689 |

### A2 arc×segment intersects vs densify+RLI

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 4 |
| soft agree (on-curve / collinear tie) | 0 |
| agree rate (1 - hard/tried) | 0.999996 |
| wall ns | 4485544948 |

### P1 arc orientationIndex latency vs densify

| metric | value |
|---|---:|
| B p50 ns (50k calls) | 68837393 |
| densify p50 ns | 162303463 |
| ratio B/ref | 0.42412769097847286 |


## Verdict

B-team seam: straight parity 100%; arc vs densify ≥ 0.99 (A1=0.996151, A2=0.999996); PERF p50 ratio 0.42412769097847286 ≤ 1.15.

Residual A1 hard disagrees are densify-chord vs arc-tangent frame disagreements off the curve (nChord=64), not silent flatten.
