# OrientableSegment — thin optional adapter (Bible §3)

Canonical architecture: [`EXACT_CURVE_BIBLE.md`](EXACT_CURVE_BIBLE.md).

## Status under the Bible

**`ExactCircularArc` is the privileged pure primitive.**  
`OrientableSegment` is a **demoted, optional adapter**. It must not become the centre of the design. Long-lived forms compose `ExactCircularArc` (and later other Exact* types) rather than re-implement geometry.

This package owns only **side** (`orientationIndex`) and **intersect**. Length, sweep, area, centroid, `pointAt`, and `toLinear` live on Exact*.

## Layout

| Type | Role |
|------|------|
| `ExactCircularArc` | Privileged ExactCurve* atom (A / Year-1 lock) |
| `ArcOrientableSegment` | Optional adapter: composes ExactCircularArc |
| `StraightOrientableSegment` | Straight carrier; Orientation / RLI parity |
| `OrientableSegments` | Factories — prefer `arc(ExactCircularArc)` |
| `ArcIntersects` | Named densifier bridge for ∩ (not an Exact* cell) |

## Hard rules (Bible §6, local)

- Never let this package become the primary way to talk about curves.
- Never claim exactness while calling densify internally.
- Never grow a rich abstract base under ExactCurve from this seam.
- Densify references stay in tests (`OrientableDensifyReference`), sampling via `ExactCircularArc.pointAt`.
- `org.locationtech.jts.algorithm.exactarc` is a deprecated alias only; new imports use `exactcurve`.

## Factory

```java
ExactCircularArc exact = new ExactCircularArc(s, m, e);
OrientableSegment seg = OrientableSegments.arc(exact); // preferred
```

## Trials

`PredicateOptionBMillionTrialTest` · [`PROOFS_OPTION_B_HANDOVER.md`](PROOFS_OPTION_B_HANDOVER.md)
