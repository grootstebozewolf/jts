# Proofs Option B — predicate seam (B team)

Sister of Proofs Option A (exact-arc laser). Phase-4 audit: abstract straight vs arc at the **predicate** layer; heavy noding/OverlayNG/snap stay concrete.

## Maintainability layout

| Type | Role |
|------|------|
| `OrientableSegment` | Slim interface (no densify on API) |
| `StraightOrientableSegment` | Core `Orientation` / RLI parity |
| `ArcOrientableSegment` | Robust tangent-frame side (`CGAlgorithmsDD.signOfDet2x2`) |
| `ArcGeometry` | Single home for circle/sweep/intersect math |
| `OrientableDensifyReference` | **Test-only** densify-chord oracle |

## Precision

- Straight: bit-identical to core.
- Arc side: DD determinant on directed unit tangent × `(q−on)`.
- Arc∩seg / arc∩arc: densifier quadratic + sweep (one implementation via `ArcGeometry`).

## Handover

`PredicateOptionBMillionTrialTest` — N=1e6 · `doc/PROOFS_OPTION_B_HANDOVER.md`.
