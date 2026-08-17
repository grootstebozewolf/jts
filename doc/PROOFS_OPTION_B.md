# Proofs Option B — predicate seam (B team)

Sister of Proofs Option A. Lane lock: B owns side + intersect only.

## Round 2 layout (maintainability → precision → perf)

| Type | Role |
|------|------|
| `OrientableSegment` | Slim interface |
| `StraightOrientableSegment` | Core Orientation / RLI parity |
| `ArcOrientableSegment` | Cached circle + `AngleBetween` sweep; filter→DD side |
| `ArcGeometry` | One circumcircle + intersect home |
| `AngleBetween` | Shared with A (Proofs #64 atan2(cross,dot)) |
| `OrientableDensifyReference` | Test-only densify oracle |

## Does not own

A closed-form length / area / centroid cells (`ExactCircularArc`).

## Handover

`PredicateOptionBMillionTrialTest` · `doc/PROOFS_OPTION_B_HANDOVER.md`
