# OrientableSegment — thin optional adapter

Canonical: [`EXACT_CURVE_BIBLE.md`](EXACT_CURVE_BIBLE.md) §3.

**`ExactCircularArc` is the privileged primitive.**  
This package is a demoted optional adapter for **side** and **intersect** only. It composes ExactCircularArc; it does not re-derive geometry.

## Public surface

| API | Role |
|-----|------|
| `OrientableSegment` | `getStart`, `getEnd`, `length`, `orientationIndex`, `intersects` |
| `OrientableSegments` | `straight`, `arc(ExactCircularArc)`, `arc(s,m,e)` |

Implementations are package-private.

```java
ExactCircularArc exact = new ExactCircularArc(s, m, e);
OrientableSegment seg = OrientableSegments.arc(exact);
```
