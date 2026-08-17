# Chainsaw → Laser · arc-native program

Tip pin: `feature/sfa-curve-rgr` with ExactCurve* Year-1 lock (#63) and optional OrientableSegment adapter.  
Canonical architecture: [`EXACT_CURVE_BIBLE.md`](EXACT_CURVE_BIBLE.md).

**Year-1 lock:** `ExactCircularArc` is the privileged pure primitive (`exactcurve` + thin `ExactCurve`).  
`OrientableSegment` is a demoted, optional adapter (Bible §3).

Prior sequence stop: M.5→ML.2→HP.4→N-SS expand @ `c956b50d`.

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

## Pointers

- [EXACT_CURVE_BIBLE.md](EXACT_CURVE_BIBLE.md)
- [PROOFS_OPTION_A.md](PROOFS_OPTION_A.md) · [PROOFS_OPTION_A_HANDOVER.md](PROOFS_OPTION_A_HANDOVER.md)
- [ORIENTABLE_SEGMENT_ADAPTER.md](ORIENTABLE_SEGMENT_ADAPTER.md) · [PROOFS_OPTION_B.md](PROOFS_OPTION_B.md)
- [LASER_RATCHET.md](LASER_RATCHET.md) · [OVERLAYNGCURVE_P2_SEAMS.md](OVERLAYNGCURVE_P2_SEAMS.md)
