# Proofs Option B — predicate seam (B team)

Sister of Proofs Option A (exact closed-form cells). Phase-4 audit: abstract straight vs arc at the **predicate** layer; heavy noding/OverlayNG/snap stay concrete.

## Types

- `OrientableSegment` — directed piece (straight or arc window)
- `StraightOrientableSegment` — parity with `Orientation` / segment×segment
- `ArcOrientableSegment` — circular 3-control window; side + intersect vs densify reference

## Never

- Silent linearise flagged exact
- 74-file `SegmentString` rewrite
- Premature OverlayNG rewrite from this package

## Handover

`PredicateOptionBMillionTrialTest` — N=1e6 suites; report under `doc/PROOFS_OPTION_B_HANDOVER.md` + artifacts.
