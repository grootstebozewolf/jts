# Laser ratchet

Tip measured `29b5d169` on `feature/sfa-curve-rgr` (base `316258a8` = merge of PR #128).  
Feed: [`laser-ratchet.json`](laser-ratchet.json) — Proofs vendors this file for day-zero reading.  
Gates emit `target/laser-ratchet/rows.jsonl`; assemble with `dev/assemble-laser-ratchet.sh`.  
Prior pin: `c956b50d` (user-board 16 / 3 / 11 hold — kit grain, stale vs cell feed).

**Contract:** `t_laser ≤ 1.15 × t_chainsaw` · OverlayNGCurve never *Curved* · Year-1 circular only

## Scoreboard (Year-1 circular cells, this run)

OpenJDK 8 · nanoTime p50 of 31 after 15 warmups · one machine.  
Do not hand-edit numbers; they come from the JSONL.

| metric | count |
|--------|------:|
| green (laser ≤ 1.15×) | 119 |
| chainsaw-only (named chord-path) | 13 |
| measured operation rows | 130 |
| primitive gates | 2 (P1-A 0.122×, P1-B 0.309×) |
| HOLD (not run / not Year-1) | see below |

119 = 117 operation lasers + 2 primitive. Max non-chord operation ratio this run: **0.674** (LEC CompoundCurve vs toLinear). Zero Year-1 circular lasers missed 1.15×.

**Previously ungauged on the Proofs observatory — now have numbers:**

| harness | cells |
|---------|-------|
| CurveWKBPerfGateTest | disc WKB 0.019× · plain LineString chord-path |
| DirectedHausdorffDistancePerfGateTest | 4 lasers, all ≤ 0.021× |
| DiscreteHausdorffDistancePerfGateTest | two discs 0.0003× · arc-segment 0.028× · plain chord-path |
| DiscreteFrechetDistancePerfGateTest | discs / arc / M.5 rings ≤ 0.56× · 2 chord-path |
| LargestEmptyCirclePerfGateTest | 5 lasers ≤ 0.674× · 3 chord-path |
| MultiCurvePerfGateTest | length vs linearise **0.0039×** (was missing from Proofs JSON) |

ReverseDispatch: 15 cells, `now_*` only — no red column invented.

Sequence M.5 → ML.2 → HP.4 → N-SS expand remains **STOPPED**. Not resumed.

## HOLD (refused)

- Year-2 zoo: ExactEllipticalArc, ExactCubicBezier, ExactClothoid, ExactNurbsSegment
- ClothoidHalleyPerfGateTest (Year-2)
- Full D-HF TAG green (`fail()` kept)
- M.4 / R.3 / ML.3 / HP.5
- 74-file N-SS · `SHARED_SNAPPED_RAY` walk (stamp may exist; not walked)
- Extra 1M §7 near-degenerate cells
- 64-a Proofs sweep / 64-file campaign
- Growing ExactCurve · non-linear core SegmentString · OverlayNGCurve rename · reminting ADR-0004

## Related

- Program: [CHAINSAW_LASER_PROGRAM.md](CHAINSAW_LASER_PROGRAM.md)
- Seams: [OVERLAYNGCURVE_P2_SEAMS.md](OVERLAYNGCURVE_P2_SEAMS.md)
- Metric: [METRIC_KIT_MX.md](METRIC_KIT_MX.md)
- Bible: [EXACT_CURVE_BIBLE.md](EXACT_CURVE_BIBLE.md)

## Re-verify @ `29b5d169` (impl = post-#128 `316258a8`)

`OverlayNGCurvePerfGateTest` 80/80 · other Year-1 `*PerfGateTest` + P1-A/P1-B **BUILD SUCCESS**.  
`check-no-curved` OK. Feed written from the same JVM run (not stdout transcription).
