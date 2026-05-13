# PRC-SN — Snap-to-grid for curved geometries

> Spike / dovetail notes for the **PRC-SN** sub-issue of the SFA Curve
> Awareness epic ([locationtech/jts#1195](https://github.com/locationtech/jts/issues/1195)).
> Phase 7 independent track.
> Companion to
> [`SnapToGridSpec.java`](src/test/java/org/locationtech/jts/spec/curveawareness/SnapToGridSpec.java)
> and [`SnapToGridCurrentBehaviourProbe.java`](src/test/java/org/locationtech/jts/spec/curveawareness/SnapToGridCurrentBehaviourProbe.java).

## What PRC-SN is

Epic §9 Phase 7 says:

> **PRC-SN** — snap-to-grid preserves arc when the snapped
> *(R, centre, sweep)* still lies on grid; otherwise densify-and-snap
> chords.

The user-facing operation is `GeometryPrecisionReducer.reduce(geom,
precisionModel)`, which today walks every coordinate through
`PrecisionModel.makePrecise(Coordinate)`. For a `CircularString` that
means each of the three control points snaps independently — the
*result* is a `CircularString` whose three control points lie on the
grid, but the arc through those points has a different centre and
radius than the input arc.

Worse, the result may not even be a valid `CircularString`: if two
control points collapse to the same grid cell, the arc is degenerate.

## Why this matters

- **Today's reducer is worse than Option A.** The
  [`SnapToGridCurrentBehaviourProbe`](src/test/java/org/locationtech/jts/spec/curveawareness/SnapToGridCurrentBehaviourProbe.java)
  shows that `GeometryPrecisionReducer.reduce` **unconditionally
  strips `CircularString` to `LineString`**, regardless of whether
  any control point actually needs snapping:

  | Input | Out type | Drift |
  |---|---|---|
  | `CIRCULARSTRING (0 0, 5 5, 10 0)` (already on grid) | `LineString` | none |
  | `CIRCULARSTRING (0.1 0.2, 5.3 5.4, 9.6 -0.4)` (snaps to integers) | `LineString` | (Δcentre, ΔR) |
  | `CIRCULARSTRING (0.2 0.2, 0.7 0.5, 1.4 0.3)` (sub-grid) | `LineString` | centre and R drift |
  | `CIRCULARSTRING (0.1 0.1, 0.2 0.2, 0.3 0.1)` (collapses) | `LineString` (0 pts) | empty result |
  | `CIRCULARSTRING (0 5, 5 0, 0 -5)` (quarter-circle on grid) | `LineString` | none |

  So the choice isn't "A vs D" — the current behaviour is *more
  aggressive than C* (always degrade type, even when there is nothing
  to snap). PRC-SN is the right place to introduce *any* type
  preservation through the reducer.

- **Snap-then-snap drift.** A pipeline that reads WKT, snaps, writes
  WKT, reads the snapped WKT, snaps again, … should converge. Today
  the arc identity drifts on every pass even when the control points
  are already on grid, because the "snapped arc" defined by the
  snapped control points is a different arc than the input.
- **Round-tripping with PostGIS / Oracle.** Both honour the arc
  identity through `ST_SnapToGrid`. JTS today silently produces a
  geometrically different arc with the same control-point coordinates,
  so a JTS→PostGIS hand-off after `reduce` carries the wrong shape.

## The four live options

| Option | Approach | Type stability | Trade-off |
|---|---|---|---|
| **A — snap control points only** (today) | snap each of the 3 control points independently | preserved (still `CircularString`) | Arc identity silently drifts. Easiest to implement (already done — no override needed). |
| **B — snap arc parameters** | derive *(R, centre, sweep)* of the input arc; snap centre and R to the grid; rebuild control points from the snapped parameters | preserved | Geometrically respects the arc nature. Hard: not every "snapped" arc has control points that land on grid points, so caller may need to choose what is more important — control points on grid, or arc parameters on grid. Also: how do you "snap a sweep angle"? |
| **C — densify then snap** | call `Linearizable.toLinear(tolerance)` first, then snap each chord coordinate via the standard precision reducer | result becomes `LineString` | Faithful to the original arc shape (the snapped chord polyline approximates the snapped image of the arc). Type changes silently; same surprise as AT-NS Option A. |
| **D — A when grid-friendly, else C** | try Option A's snap; check that the new arc through snapped control points has a grid-aligned centre and a grid-aligned radius; if so keep the `CircularString`; otherwise fall back to C | adaptive | Best of both worlds when the input arc cleanly snaps. Falls back to C silently when it doesn't — caller still has to handle the type change. Requires a "grid-friendly" definition. |

### Where we lean

**Option D**, with a clear definition of "grid-friendly":

```
the snapped CircularString is grid-friendly iff the circle through
its three snapped control points has
  • a centre whose coordinates are integer multiples of the grid step
    (within the precision model's representable resolution), and
  • a radius that is an integer multiple of the grid step.
```

Rationale:

- Option A alone is wrong by §7's stated direction ("preserves arc
  when the snapped *(R, centre, sweep)* still lies on grid; otherwise
  densify-and-snap chords"). The "otherwise" branch is real, so A
  doesn't suffice.
- Option B alone is too ambitious for a near-term ship: deriving and
  re-snapping arc parameters has multiple sub-decisions (does sweep
  snap? what if the new arc has a tighter radius than two grid steps?)
  and changes the control-point coordinates in ways downstream code
  may not expect.
- Option C alone gives up too quickly — for the *grid-friendly* case
  (uniform grid, integer-coordinate input, similarity already
  snapped) the arc lands on grid trivially and we shouldn't have to
  densify.
- Option D is the §7 phrasing rephrased as code: "preserve when
  preserve is faithful; densify otherwise".

The case **against** D is the silent type change in the fallback
branch. We mitigate the surprise the same way AT-NS Option A
mitigates it:

1. Document the type-change-on-fallback contract on the precision
   reducer entry point's Javadoc.
2. Provide a `CurvedGeometryReducer.isGridFriendly(CircularString,
   PrecisionModel)` static helper so callers can branch before
   calling `reduce` if type stability matters.

### What we are deferring

- **The arc-parameter snap definition** (Option B internals): is
  "centre on grid" enough, or do we also require "radius on grid"?
  This spike says both. A future refinement could weaken to one or
  the other if there's a use case for it.
- **`CompoundCurve` snapping.** PRC-SN extends naturally: each member
  is treated by its own rule (CircularString member → D; LineString
  member → standard chord snap). The spike focuses on `CircularString`
  for the test surface; the contract generalises.
- **`Z` / `M` ordinate snap policy on the densified fallback.** Same
  deferred-to-DSF question as AT-NS Option A.

## Pre-requisite that's already landed

The non-similarity densify path (AT-NS Option A) and the sagitta
densification (`feature/sfa-curve-toLinear-densification`) both
provide the "C" fallback. PRC-SN Option D's fallback is "if not
grid-friendly, call toLinear and snap chords" — same code path.

## Smallest concrete next step

1. **Maintainer ack on the option.** One-line A / B / C / D reply on
   the epic issue.
2. **`arch:` commit** updating this file to delete the unselected
   rows and record the choice.
3. **Implementation PR**:
   - Static helper
     `CurvedPrecisionReducer.isGridFriendly(CircularString, PrecisionModel)`
     using the centre / radius grid check above.
   - Override `Geometry.reverse` / `apply` / the precision-reduction
     path to invoke the helper for curve subclasses and dispatch to A
     or C accordingly. The simplest seam is a new top-level helper:
     `CurvedPrecisionReducer.reduce(curveGeom, pm)`.
   - Delete the now-green methods from `SnapToGridSpec`.

## Cross-reference

- Epic: [locationtech/jts#1195](https://github.com/locationtech/jts/issues/1195) Phase 7 — PRC-SN, §6 cross-module impact (PrecisionModel.makePrecise integration).
- Companion: [`SPEC_AT_NS.md`](SPEC_AT_NS.md) — the densify-on-non-faithful pattern has the same shape; PRC-SN reuses AT-NS's fallback path.
- Today's behaviour for the linearised polyline path is the well-tested
  `GeometryPrecisionReducer`; the spike does not propose touching it.
