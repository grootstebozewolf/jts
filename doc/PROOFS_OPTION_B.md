# OrientableSegment — thin optional adapter (Bible §3)

Canonical: [`EXACT_CURVE_BIBLE.md`](EXACT_CURVE_BIBLE.md).

## Architectural role

**`ExactCircularArc` is the privileged pure primitive.**  
`OrientableSegment` is a **demoted, optional adapter**. It must not become the centre of the design. It **composes** `ExactCircularArc`; it does not re-derive circumcircle or sweep.

Addresses PR #62 review: thin public surface, no public `AngleBetween` shim, forced composition, Exact* in `exactcurve`.

## Public surface (minimal)

| Type | Visibility | Members |
|------|------------|---------|
| `OrientableSegment` | public | `getStart`, `getEnd`, `length`, `orientationIndex`, `intersects` |
| `OrientableSegments` | public | `straight`, `arc(ExactCircularArc)`, `arc(s,m,e)` |
| `ArcOrientableSegment` / `StraightOrientableSegment` | **package-private** | — |
| `exactcurve.AngleBetween` | **package-private** | sweep helper for ExactCircularArc only |
| `exactarc.*` | **removed** | no public shim |

## Factory

```java
ExactCircularArc exact = new ExactCircularArc(s, m, e);
OrientableSegment seg = OrientableSegments.arc(exact);
```

## Trials

[`PROOFS_OPTION_B_HANDOVER.md`](PROOFS_OPTION_B_HANDOVER.md)
