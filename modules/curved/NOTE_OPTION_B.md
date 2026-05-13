# Option B spike — return-type widening of `Polygon.getExteriorRing()`

> Spike branch: `feature/sfa-curve-F-CP-spike-optionB`.
> Companion to [`SPEC_F_CP.md`](SPEC_F_CP.md) §FCP-DOVE Option B.
> **Status of branch: intentionally does not compile.** The single
> `Polygon.java` edit is the spike; the compile-error stream IS the
> finding. See [`SPIKE_OPTION_B_compile_errors.txt`](SPIKE_OPTION_B_compile_errors.txt)
> for the full log.

## The change

One file, two method signatures:

```diff
- public LinearRing getExteriorRing() { return shell; }
+ public LineString getExteriorRing() { return shell; }

- public LinearRing getInteriorRingN(int n) { return holes[n]; }
+ public LineString getInteriorRingN(int n) { return holes[n]; }
```

That's the entire mechanical surface of Option B. The internal field
declarations stay `LinearRing` — a structural `CurvePolygon` subclass
would override the accessors and store its structural shell in a
separate field. Conceptually identical to Option A's storage layout
from that point on.

## Pre-spike estimate (from `SPEC_F_CP.md` §FCP-DOVE)

> Breaks every caller that does `(LinearRing) p.getExteriorRing()` or
> that relies on LinearRing-specific API. Requires sweeping `jts-core`
> for casts. The actual count of `(LinearRing)` casts on results of
> `getExteriorRing()` inside `jts-core` is the deciding number.

Original cast survey: **10 explicit `(LinearRing)` cast sites** on
`getExteriorRing()` + `getInteriorRingN()` results in `jts-core/main`.
At that count, B looked attractive.

## Actual measurement

`mvn -pl modules/core compile` after the two-line widening:

| Metric | Count |
|---|---|
| Compile errors (main tree only) | **108** |
| Distinct files affected | **25** |
| Highest-impact file | `operation/valid/IsValidOp.java` (12 errors) |
| Files with 4+ errors | 19 of 25 |

The original "10 cast sites" number was the count of **explicit**
`(LinearRing)` casts. The compiler also flags every **implicit
assignment**:

```java
LinearRing shell = polygon.getExteriorRing();   // no cast, fails to compile
return new Polygon(polygon.getExteriorRing(), polygon.getFactory()); // arg type mismatch
```

These are 10× more common than the explicit casts. The full
breakdown, file by file:

```
12  operation/valid/IsValidOp.java
 8  coverage/CoverageRingEdges.java
 6  operation/valid/PolygonTopologyAnalyzer.java
 6  operation/valid/IndexedNestedPolygonTester.java
 4  triangulate/polygon/PolygonHoleJoiner.java
 4  simplify/PolygonHullSimplifier.java
 4  shape/CubicBezierCurve.java
 4  operation/relateng/RelateNG.java
 4  operation/relateng/RelateGeometry.java
 4  operation/relateng/AdjacentEdgeLocator.java
 4  operation/overlayng/RobustClipEnvelopeComputer.java
 4  operation/overlayng/EdgeNodingBuilder.java
 4  operation/buffer/BufferCurveSetBuilder.java
 4  io/kml/KMLWriter.java
 4  io/gml2/GMLWriter.java
 4  geomgraph/GeometryGraph.java
 4  geom/util/GeometryTransformer.java
 4  geom/util/GeometryFixer.java
 4  coverage/CoverageRing.java
 4  algorithm/locate/SimplePointInAreaLocator.java
 4  algorithm/PointLocator.java
 2  triangulate/VoronoiDiagramBuilder.java
 2  shape/fractal/SierpinskiCarpetBuilder.java
 2  coverage/CoverageGapFinder.java
 2  algorithm/construct/ExactMaxInscribedCircle.java
```

And this is `jts-core/main` only. The test tree (42 additional
`getExteriorRing()` call sites) almost certainly produces another
similar-sized batch.

## Read on what to do with the 108 sites

Most of the 108 sites are mechanical fixes — change the local variable
type from `LinearRing` to `LineString`, or add an explicit cast where
the consumer genuinely needs a `LinearRing` (e.g., passing to
`new Polygon(...)` whose constructor takes `LinearRing`). The compiler
flags every one of them, so no archaeology is needed; just sweep.

But:

- **The `Polygon` constructor itself takes `LinearRing shell, LinearRing[] holes`.**
  Many of the 108 sites are passing `getExteriorRing()` results
  *back* into a polygon constructor (e.g., `GeometryFixer`,
  `CoverageRingEdges`, `BufferCurveSetBuilder`). Widening the
  constructor too would multiply the surface; not widening it forces
  every one of those sites to cast, defeating the "single source of
  truth" argument that motivated B in the first place.

- **The `getExteriorRing().copy()` pattern is also pervasive.**
  `Geometry.copy()` returns `Geometry`, and callers cast back to the
  declared type. After widening to `LineString`, those still need to
  cast (`(LineString) ring.copy()`), and the cast target changes from
  `LinearRing` to `LineString` — a silent semantic shift the original
  author didn't intend.

## Verdict

**Option B is rule-out as a near-term Phase-1 landing.**

It is not technically infeasible — the 108 sites are mechanically
fixable — but the actual cost is:

1. A sweeping `jts-core` PR touching 25 files plus their tests.
2. A binary-incompatible API change for every downstream consumer
   (PostGIS-Java, GeoTools, etc.) — they would see `NoSuchMethodError`
   at runtime against a JTS jar built against the new API, since the
   JVM looks up methods by signature including return type.
3. The "single source of truth" argument doesn't survive contact with
   the `Polygon` constructor signature — most of the 108 sites would
   end up adding explicit `(LinearRing)` casts to satisfy the
   constructor, recreating the two-tier API Option A would have made
   explicit.

We don't need to rule out B forever. After F-CP lands under Option A,
a follow-up `arch:` change can revisit B as a *deprecation cycle*
(widen the API, keep the LinearRing-returning method as deprecated
shim, do the 108-site sweep over multiple PRs).

## What this spike kept in the branch

- `modules/core/src/main/java/org/locationtech/jts/geom/Polygon.java`
  — the two-method widening as a single hunk.
- `NOTE_OPTION_B.md` — this file.
- `SPIKE_OPTION_B_compile_errors.txt` — the full mvn error log
  preserved as evidence.

The branch is intentionally non-compiling; checking it out should
land you exactly on the discovery surface, not on a clean tree
pretending the option is straightforward.

## Cross-reference

- Spec doc: [`SPEC_F_CP.md`](SPEC_F_CP.md) §FCP-DOVE.
- Related: epic [#1195](https://github.com/locationtech/jts/issues/1195) §7 risk #1.
- The "deciding number" line in `SPEC_F_CP.md` should be updated
  post-merge to reference this measurement.
