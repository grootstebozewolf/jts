# Proofs Option A — exact arc front-end (A team)

Sister of Proofs Option B (predicate seam / `OrientableSegment`). Phase-4
audit: exact closed-form cells at the **metric** layer; heavy noding /
OverlayNG / snap stay concrete.

## Types

- `AngleBetween` — Proofs #64 `atan2(cross,dot)` + mid long/short
- `ExactCircularArc` — 3-control window: `r·θ`, `chord ≤ arc` via
  `2 r sin(θ/2) ≤ r θ`, in-arc on `d²`, segment area, centroid
- Circumcircle **and r** are `CircularArcDensifier.circumcircle` (one
  determinant, one radius — no second mean-r)
- Front-end for `CircularString.getLength()` / centroid and
  `CircularArcDensifier.arcLength`

## Never

- Silent linearise flagged exact
- 74-file `SegmentString` rewrite
- Premature OverlayNG rewrite from this package
- Touch B-team `orientable` types

## Handover

`ExactArcOptionAMillionTrialTest` — N=1e6; L1 is analytic n-gon
`n·2r·sin(θ/2n)`, not a densify polyline. Report:
`doc/PROOFS_OPTION_A_HANDOVER.md`.
