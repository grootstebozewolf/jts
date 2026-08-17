# Proofs Option A — exact arc front-end (A team)

Sister of Proofs Option B (predicate seam / `OrientableSegment`). Phase-4
audit: exact closed-form cells at the **metric** layer; heavy noding /
OverlayNG / snap stay concrete.

## Types

- `ExactCircularArc` — 3-control window: atan2 sweep, `r·θ` length,
  `chord ≤ arc`, in-arc, circular-segment area, arc-length centroid
- Front-end for `CircularString.getLength()` and
  `CircularArcDensifier.arcLength` (densifier no longer owns `r·θ`)

## Never

- Silent linearise flagged exact
- 74-file `SegmentString` rewrite
- Premature OverlayNG rewrite from this package
- Touch B-team `orientable` types

## Handover

`ExactArcOptionAMillionTrialTest` — N=1e6 suites; report under
`doc/PROOFS_OPTION_A_HANDOVER.md`.
