# Proofs Option A — 1M-trial handover

Seed `0xa7ea0001` · N=1000000 · box [-100.0,100.0]² · densify nChord=64

Numbers filled after `ExactArcOptionAMillionTrialTest` on this branch.

### L1 exact length ≥ densify chord-sum

| metric | value |
|---|---:|
| tried | (run) |
| hard disagree | (run) |
| soft agree (equal within 1e-9) | (run) |
| agree rate (1 - hard/tried) | (run) |
| wall ns | (run) |

### L2 chord ≤ arc

| metric | value |
|---|---:|
| tried | (run) |
| hard disagree | (run) |
| agree rate | (run) |
| wall ns | (run) |

### P1 length latency vs densify

| metric | value |
|---|---:|
| A p50 ns (50k calls) | (run) |
| densify p50 ns | (run) |
| ratio A/ref | (run) |

## Verdict

Pending first green run on `cursor/proofs-option-a-exact-arc-92cd`.
