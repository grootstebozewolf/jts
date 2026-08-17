# Proofs Option A — 1M-trial handover

Seed `0xa7ea0001` · N=1000000 · box [-100.0,100.0]² · densify nChord=64

### L1 exact length ≥ densify chord-sum

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 0 |
| soft agree (within relative 1e-8 slack) | 1665 |
| agree rate (1 - hard/tried) | 1.0 |
| wall ns | 3265921488 |

### L2 chord ≤ arc

| metric | value |
|---|---:|
| tried | 1000000 |
| hard disagree | 0 |
| agree rate | 1.0 |
| wall ns | 548465443 |

### P1 length latency vs densify

| metric | value |
|---|---:|
| A ns (50k calls) | 24625367 |
| densify ns | 142025784 |
| ratio A/ref | 0.1733865943665553 |

## Verdict

A-team seam: `chord ≤ arc` 100%; length ≥ densify chords 100% (1665 soft ulp ties on huge-r / near-full windows); PERF ratio 0.173 ≤ 1.15.

Soft L1 ties are densify-polyline vs `r·θ` ulps, not a silent flatten.
