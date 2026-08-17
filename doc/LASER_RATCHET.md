# Laser ratchet

Tip `9964b4f9` · PR #7 · `feature/sfa-curve-rgr`  
Prior pin: `cf6e2b58` (docs retip epic to SoT HEAD `416ba68d` · #7 / #61)

**Contract:** `t_laser ≤ 1.15 × t_chainsaw` · OverlayNGCurve never *Curved* · Draft v6 MMF Option B

## Scoreboard (user 2026-08-17 @ `cf6e2b58`)

| metric | count |
|--------|------:|
| green | 16 |
| chainsaw-only | 3 |
| measured | 11 (11 hold) |

Re-verify after B/C/D/E (`9964b4f9`) with:

```bash
bash dev/check-no-curved.sh
mvn -pl modules/curve -am test -Dtest=OverlayNGCurvePerfGateTest \
  -DfailIfNoTests=false -Dcheckstyle.skip=true -Dpmd.skip=true
```

## Related

- Seams board: [OVERLAYNGCURVE_P2_SEAMS.md](OVERLAYNGCURVE_P2_SEAMS.md)
- Option B: [MMF_OPTION_B.md](MMF_OPTION_B.md)

## Re-verify @ `9964b4f9` (post B/C/D/E)

`OverlayNGCurvePerfGateTest` **79/79** · `OverlayNGCurveSeamsBCDTest` 3/3 · `OverlayNGCircleTest` 7/7 · `check-no-curved` OK.  
User scoreboard at `cf6e2b58` (green 16 · chainsaw-only 3 · measured 11 hold) remains the board pin; gate still holds on tip.
