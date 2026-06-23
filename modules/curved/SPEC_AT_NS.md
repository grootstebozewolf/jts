# AT-NS — Affine transform of a curved geometry

> Spike / dovetail notes for the **AT-NS** sub-issue of the SFA Curve
> Awareness epic ([locationtech/jts#1195](https://github.com/locationtech/jts/issues/1195)).
> Companion to the red-test class
> [`AffineTransformOnCircularStringSpec.java`](src/test/java/org/locationtech/jts/spec/curveawareness/AffineTransformOnCircularStringSpec.java)
> and the current-behaviour probe
> [`AffineCurrentBehaviourProbe.java`](src/test/java/org/locationtech/jts/spec/curveawareness/AffineCurrentBehaviourProbe.java).

## What AT-NS is

Today, `Geometry.apply(AffineTransformation)` walks every coordinate
and lets the transformation mutate it in place. The geometry keeps
its class (a `CircularString` stays a `CircularString`), and the three
sheared / non-uniformly-scaled control points define a *different*
circle than the elliptical image of the original arc. So the type tag
survives, the math doesn't.

Epic §7 risk #3 calls this out explicitly:

> Non-similarity affine (AT-NS). Sheared arcs are ellipse arcs, which
> JTS doesn't model. The plan is to detect-and-densify, but: what does
> `getGeometryType()` return on the result? If `LineString`, we've
> changed the type silently. If still flagged as `CircularString`, the
> polyline lies about its identity. Decide before AT-NS lands.

## What changes on non-similarity

Given a 2-D affine transform with linear part
$\begin{pmatrix}a & b\\\\ c & d\end{pmatrix}$, the transform is a
**similarity** iff it preserves angles and uniformly scales lengths.
Equivalent algebraic test:

$$a^2 + c^2 = b^2 + d^2 \quad\text{and}\quad ab + cd = 0$$

The first equation says the two basis vectors map to equal-length
vectors (uniform scale); the second says they map to orthogonal
vectors (no shear). Translations and reflections also satisfy this.

A circular arc transformed under a similarity is still a circular
arc; transformed under a non-similarity it becomes an elliptical arc
(JTS doesn't model these). So the AT-NS contract has to pick what to
do in the non-similarity branch.

## The three live options

| Option | Non-similarity behaviour | `getGeometryType()` | Trade-off |
|---|---|---|---|
| **A — silent densify** | densify the arc to a polyline first, then transform the polyline | result is `LineString` (or `Polygon` for closed types) | Geometrically faithful — the polyline approximates the true elliptical image. Type changes silently though, so callers must inspect after the call. The default tolerance becomes a `CurvedGeometryFactory` setting; for `tolerance = 0` it defaults per the existing `Linearizable` contract. |
| **B — preserve type, accept geometric drift** | transform the 3 control points; the new `CircularString` is the circle through the transformed points | result is `CircularString` (today's behaviour) | Type stays stable, callers don't have to check. Geometry lies: the curve through the new control points is **not** the elliptical image of the original arc. Surprising for any caller that expected affine fidelity. |
| **C — fail-fast** | detect non-similarity, throw `UnsupportedOperationException` | n/a | Loudest diagnostic. Every caller has to migrate to "densify first, then transform". Probably only acceptable behind a `CurvedGeometryFactory` strict-mode flag. |

### Where we lean

**Option A.** The §7 wording — "the plan is to detect-and-densify" —
matches A. The case for A:

- It is the *only* option where the resulting `Geometry` is a faithful
  representation of "the affine image of the original arc". B's result
  is a different geometric curve entirely; C avoids the question.
- The type change is observable and recoverable: a curve-aware caller
  can branch on `g.getGeometryType().equals("CircularString")` after
  the transform to know whether their input survived.
- The cost — losing the arc identity on non-similarity — is real, but
  it is *correct* and matches the answer a user would get from any
  curve-aware library (PostGIS's `ST_Affine` produces a polyline on
  non-similarity for the same reason).
- A drops in cleanly with the existing `Linearizable` contract: the
  non-similarity branch becomes "call `toLinear(tolerance)`, then
  transform the result".

The case against A is the silent type change. A `CircularString` flows
into `apply` and a `LineString` flows out. Two mitigations live in the
implementation PR:

1. Document the type-change behaviour on the `Linearizable` Javadoc
   so it is the *expected* shape, not a surprise.
2. Provide a static helper `AffineTransformations.isSimilarity(at)`
   so callers can branch before calling `apply` if the type stability
   matters for them.

**Option B** stays available as the result you get if you call
`apply` on the *control-point sequence directly*, which is what the
default `Geometry.apply` does today. Callers that want B can construct
the transformed CircularString from the transformed coordinates
explicitly:

```java
CoordinateSequence cs = original.getCoordinateSequence().copy();
at.transform(cs);
CircularString result = factory.createCircularString(cs);
```

This is a clean three-line escape hatch; B doesn't need to be the
default to remain reachable.

**Option C** survives as a `CurvedGeometryFactory` strict-mode flag —
the same diagnostic-mode role we sketched for FCP-DOVE Option C in
`SPEC_F_CP.md`. Not the default contract.

### What we're deferring

- The default tolerance value (if A wins) is the same open question
  as FCP-DOVE Option A's `getExteriorRing()` linearisation tolerance.
  Likely a `CurvedGeometryFactory` field; precise default goes in the
  implementation PR.
- Z / M ordinate propagation across the densified polyline. Today
  `apply` is XY-only; densification inherits whatever the existing
  `toLinear` does, which today is "control points only". A future Z/M
  interpolation policy will land alongside `DSF` (Densifier).
- Composite types (`CompoundCurve`, `CurvePolygon`, `MultiCurve`,
  `MultiSurface`): the spike focuses on `CircularString`, but A's
  contract generalises directly to every type that implements
  `Linearizable`. Each curve subclass's `apply` impl needs the
  similarity-check + densify branch.

## Smallest concrete next step

1. **Maintainer ack on the option.** One-line "A / B / C" reply on the
   epic issue, same shape as FCP-DOVE.
2. **`arch:` commit** updating this file to delete the unselected
   rows and record the choice.
3. **Implementation PR**:
   - `AffineTransformations.isSimilarity(AffineTransformation)` static
     helper in `jts-curved` (could also live in `jts-core` if the
     epic maintainer wants a public predicate there).
   - Override `CircularString.apply(AffineTransformation)` (and the
     other curve types) to branch on `isSimilarity`: similarity
     dispatches to the inherited control-point transform; non-similarity
     calls `toLinear(tolerance)` then transforms the linearised result.
   - Delete the green methods from
     `AffineTransformOnCircularStringSpec.java` per the §5 convention.

## Pre-requisite that's already landed

The `feature/sfa-curve-toLinear-densification` branch on the fork
provides arc-correct sagitta densification for `CircularString.toLinear`.
That's the densifier the Option-A non-similarity branch wires through.

## Leaving the door open

If the implementation PR surfaces a third use case — say, a
"transform-and-keep-tag-even-when-lying" flag because some pipeline
needs the type to stay stable for downstream serialisation — that
becomes a Phase-1.5 ticket, not a re-litigation of A vs B. The default
contract should stay arc-correct.

## References

- Epic: [locationtech/jts#1195](https://github.com/locationtech/jts/issues/1195) §7 risk #3.
- Companion: [`SPEC_F_CP.md`](SPEC_F_CP.md) — the FCP-DOVE decision used the same
  three-option / lean-A / smallest-next-step structure.
- Source PR: [locationtech/jts#1194](https://github.com/locationtech/jts/pull/1194) — Phase-1 extension hooks + opt-in jts-curved.
