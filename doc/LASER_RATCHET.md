# Laser ratchet

Tip `47c0f33e` · PR #7 · `feature/sfa-curve-rgr`  
Prior pin: `d62f8726` (post B/C/D/E)

**Contract:** `t_laser ≤ 1.15 × t_chainsaw` · OverlayNGCurve never *Curved* · Draft v6 MMF Option B

## Scoreboard (user 2026-08-17 @ `cf6e2b58`)

| metric | count |
|--------|------:|
| green | 16 |
| chainsaw-only | 3 |
| measured | 11 (11 hold) |

Sequence on tip: **Sync → M.3 → R.2 → ML.4 → stop**. Program: [CHAINSAW_LASER_PROGRAM.md](CHAINSAW_LASER_PROGRAM.md).

Re-verify:

```bash
bash dev/check-no-curved.sh
mvn -pl modules/curve -am test -Dtest=OverlayNGCurvePerfGateTest \
  -DfailIfNoTests=false -Dcheckstyle.skip=true -Dpmd.skip=true
```

## Related

- Program: [CHAINSAW_LASER_PROGRAM.md](CHAINSAW_LASER_PROGRAM.md)
- Seams board: [OVERLAYNGCURVE_P2_SEAMS.md](OVERLAYNGCURVE_P2_SEAMS.md)
- Option B: [MMF_OPTION_B.md](MMF_OPTION_B.md)
- Metric kit: [METRIC_KIT_MX.md](METRIC_KIT_MX.md)

## Re-verify @ `47c0f33e` (post M.1/M.2)

User scoreboard at `cf6e2b58` (green 16 · chainsaw-only 3 · measured 11 hold) remains the board pin; gate still holds on tip. Named-miss leftover count on seams board stays **3** open-style leftovers after B/C/D/E honesty (H-ANNULUS-TANGENT refuse · open H-DISC lineal · N-SS full hierarchy deferred).
