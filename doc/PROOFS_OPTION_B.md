# Proofs Option B — predicate seam (B team)

Lightweight **side + intersect** on top of A's ExactCurve* front-end.

## Layout

| Type | Role |
|------|------|
| `ExactCircularArc` (A) | Closed-form circle / sweep / `inArc` / length / area |
| `ArcOrientableSegment` (B) | Thin wrapper: `exactArc()` + filter→DD side + intersect |
| `StraightOrientableSegment` (B) | Core Orientation / RLI parity |
| `ArcGeometry` | Intersect/sample only — no second circumcircle owner |
| `OrientableDensifyReference` | Test-only densify oracle |

## Factory

```java
OrientableSegments.arc(start, mid, end);
OrientableSegments.arc(exactCircularArc); // preferred when A already built it
```

## Handover

`PredicateOptionBMillionTrialTest` · `doc/PROOFS_OPTION_B_HANDOVER.md`
