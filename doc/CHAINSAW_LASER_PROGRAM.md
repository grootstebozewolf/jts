# Chainsaw → Laser · arc-native program

Tip pin: `feature/sfa-curve-rgr` @ `316258a8` (PR #128) · Year-1 circular re-verify measured on `29b5d169`.  
Canonical architecture: [`EXACT_CURVE_BIBLE.md`](EXACT_CURVE_BIBLE.md).  
Day-zero feed: [`LASER_RATCHET.md`](LASER_RATCHET.md) / [`laser-ratchet.json`](laser-ratchet.json).

**Year-1 lock:** `ExactCircularArc` is the privileged pure primitive (`exactcurve` + thin `ExactCurve`).  
`OrientableSegment` is a demoted, optional adapter (Bible §3).

Prior sequence stop: M.5→ML.2→HP.4→N-SS expand @ `c956b50d` — **still STOPPED**.

Contract: parity or named densify-shim · `t_laser ≤ 1.15 × t_chainsaw` · no silent ConcaveHull · OverlayNGCurve never *Curved* · core `SegmentString` stays linear.

## Glossary

| Term | Meaning |
|------|---------|
| **ExactCurve*** | Privileged immutable value types (`exactcurve`) |
| **OrientableSegment** | Thin optional side/intersect adapter — not the centre of design |
| JTS MMF “Option B” | `SegmentKind` typed carrier — orthogonal |

## Holds

- Full **D-HF TAG** green (`fail()` kept)
- **M.4** / **R.3** / **ML.3** / **HP.5**
- 74-file N-SS lie · curvature-order on `SHARED_SNAPPED_RAY`
- Year-2 zoo + `ClothoidHalleyPerfGateTest`
- Extra 1M §7 near-degenerate cells
- 64-a Proofs campaign

## Year-1 circular re-verify (this pin)

Gates emit JSONL (`target/laser-ratchet/rows.jsonl`); `dev/run-year1-laser-ratchet.sh` / `dev/assemble-laser-ratchet.sh` write the Proofs-schema feed.  
CI: `.github/workflows/laser-ratchet.yml` (not `build-and-test.yml`). Version history is `git log -- doc/laser-ratchet.json`. Stdout-only is the discarded-timing bug.

Cell scoreboard this run: **green 119 · chainsaw-only 13 · HOLD named**.  
Previously ungauged observatory harnesses (WKB, DHD, Discrete Hausdorff/Fréchet, LEC, MultiCurve) now have `now_*`. ReverseDispatch has no red column.

## Pointers

- [EXACT_CURVE_BIBLE.md](EXACT_CURVE_BIBLE.md)
- [PROOFS_OPTION_A.md](PROOFS_OPTION_A.md) · [PROOFS_OPTION_A_HANDOVER.md](PROOFS_OPTION_A_HANDOVER.md)
- [ORIENTABLE_SEGMENT_ADAPTER.md](ORIENTABLE_SEGMENT_ADAPTER.md) · [PROOFS_OPTION_B.md](PROOFS_OPTION_B.md)
- [LASER_RATCHET.md](LASER_RATCHET.md) · [OVERLAYNGCURVE_P2_SEAMS.md](OVERLAYNGCURVE_P2_SEAMS.md)
