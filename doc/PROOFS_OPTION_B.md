# Proofs Option B — predicate seam (B team)

Lightweight **side + intersect** on A's {@code ExactCircularArc}.

## Layout

| Type | Role |
|------|------|
| `ExactCircularArc` (A) | Circle / sweep / `inArc` / `pointAt` / length |
| `ArcOrientableSegment` (B) | Thin wrap: filter→DD side; densifier intersect + `isOnSweep` |
| `StraightOrientableSegment` (B) | Core Orientation / RLI |
| Densify reference (test) | Samples via `ExactCircularArc.pointAt` |

## Factory

```java
OrientableSegments.arc(exactCircularArc); // preferred
OrientableSegments.arc(start, mid, end);
```
