# Proofs Option A — exact arc front-end (A team)

Sister of Proofs Option B (predicate seam / `OrientableSegment`). Phase-4
audit: exact closed-form cells at the **metric** layer; heavy noding /
OverlayNG / snap stay concrete.

## Types

- `AngleBetween` — **only** `% 2π` / sweep owner. Proofs #64
  `atan2(cross,dot)` + mid long/short. `DirectedSweep` keeps
  orientation and magnitude together. `CircularArcDensifier` delegates
  (no private `normPos`).
- `ExactCircularArc` — 3-control window: `r·θ`, `chord ≤ arc` via
  `2 r sin(θ/2) ≤ r θ`, in-arc on `d²` + `travelled`, segment area,
  one signed-sweep centroid formula
- Circumcircle **and r** are `CircularArcDensifier.circumcircle` (one
  determinant, one radius — no second mean-r)
- Front-end for `CircularString.getLength()` / centroid and
  `CircularArcDensifier.arcLength`

## Never

- Silent linearise flagged exact
- 74-file `SegmentString` rewrite
- Premature OverlayNG rewrite from this package
- Touch B-team `orientable` types

## Merge note for B

B round-2 (`59976b5a`) copied the v1 `AngleBetween` (3-atan2 subtract).
This branch's file is a compatible superset: same method names, Proofs
`atan2(cross,dot)` implementation, `DirectedSweep`, `travelled`. Take
**this** `AngleBetween.java` on merge; do not keep the v1 copy.

## Handover

`ExactArcOptionAMillionTrialTest` — N=1e6; L1 is analytic n-gon
`n·2r·sin(θ/2n)`, not a densify polyline. Report:
`doc/PROOFS_OPTION_A_HANDOVER.md`.
