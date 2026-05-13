# Option C spike — fail-fast `getExteriorRing()` on `CurvePolygon`

> Spike branch: `feature/sfa-curve-F-CP-spike-optionC`.
> Companion to [`SPEC_F_CP.md`](SPEC_F_CP.md) §FCP-DOVE Option C.

## The change

`CurvePolygon` overrides `Polygon.getExteriorRing()` and
`getInteriorRingN(int)` to throw `UnsupportedOperationException` with a
descriptive message that points callers at the new `getExteriorCurve()`
/ `getInteriorCurveN(int)` accessors. The structural shell is stored
in a new field on `CurvePolygon`; the parent class's `shell` field
still holds a linearised placeholder so `Polygon`-internal methods that
read the field directly (not via the getter) keep working.

`copyInternal()` and `toLinear()` route around the override via
explicit `super.getExteriorRing()` / `super.getInteriorRingN()` calls.

## Pre-spike estimate (from `SPEC_F_CP.md` §FCP-DOVE)

> Forces every caller to migrate; loudest diagnostic; most painful
> interim period because **any** third-party code that touches a
> CurvePolygon via the Polygon API blows up at runtime. Probably only
> acceptable behind a feature flag.

## Actual measurement — blast-radius probe

A CurvePolygon constructed from
`CURVEPOLYGON (CIRCULARSTRING (0 0, 10 0, 5 5, 0 5, 0 0))` was piped
through 32 representative `jts-core` operations and a few `jts-curved`
calls. The probe lives at
[`OptionCBlastRadiusProbe.java`](src/test/java/org/locationtech/jts/spec/curveawareness/OptionCBlastRadiusProbe.java).

| Outcome | Count |
|---|---|
| **OK** (operation completes) | **16** |
| **UOE** (fail-fast on `getExteriorRing()`) | **16** |
| Other error | 0 |

That's a **50 % blast radius** across common `Geometry` API surface.

### What still works

Operations that read `Polygon`'s internal `shell` / `holes` fields
directly, bypassing the public method:

```
getGeometryType()             getNumInteriorRing()
isEmpty()                     getCoordinates()
getDimension()                getCoordinate()
getBoundaryDimension()        getEnvelopeInternal()
getNumPoints()                getLength()
getArea()                     getBoundary()
copy()                        isSimple()
normalize()                   getExteriorCurve()  -- new
```

### What breaks

```
getExteriorRing()             reverse()
getInteriorRingN(0)           isValid()
getCentroid()                 intersects(other)
getInteriorPoint()            contains(other)
convexHull()                  intersection(other)
buffer(1.0)                   union(other)
toText()                      difference(other)
CurvedWKTWriter.write()       distance(other)
```

The damage list is significant: every relational predicate, every
overlay operation, buffer, toText, and even the curve-aware
`CurvedWKTWriter` end up routing through `getExteriorRing()`
internally and surface the UOE.

## Empirical confirmation in the jts-curved suite itself

On this spike branch, with no changes to test code, the default
`mvn -pl modules/curved test` run breaks two existing tests:

- `WKTCurvePolygonTest.testWKTRoundTripXY`
- `WKTMultiSurfaceTest.testWKTRoundTripXY`

Both call `new CurvedWKTWriter().write(curvePolygon)`, which routes
through `WKTWriter.appendPolygonText` → `polygon.getExteriorRing()`
→ UOE. This is the same blast-radius path the probe identified for
`CurvedWKTWriter.write()`, observed in the wild on real Phase-1 tests
rather than a synthetic probe.

## Verdict

**Option C is rule-out, including as a feature flag.**

The probe shows that the public `getExteriorRing()` method is reached
by the relational predicates (`intersects`, `contains`, `union`, …),
the buffer pipeline, the centroid / interior-point algorithms, and
`toText()`. These are first-line `Geometry` operations — they're the
*default* surface a `CurvePolygon` flows through. Even our own
`CurvedWKTWriter.write()` fails, because the writer delegates to
`appendPolygonText` which calls `polygon.getExteriorRing()`.

A "feature flag" for fail-fast would mean:
- With flag on, half the jts-core API throws on every CurvePolygon
  → unusable.
- With flag off, the behaviour reverts to Option A or Option B.

So a flagged Option C collapses to "Option A with an optional
explosion mode" — no design benefit over A.

C remains useful as a **diagnostic mode**: a `CurvedGeometryFactory`
flag that wraps a CurvePolygon to throw on Polygon-API access could
help a strict pipeline confirm that no non-curve-aware code touches
its CurvePolygons. That's a separate (smaller) feature, not the
default contract.

## What this spike kept in the branch

- `modules/curved/src/main/java/org/locationtech/jts/geom/curved/CurvePolygon.java`
  -- the throwing overrides + `getExteriorCurve()` / `getInteriorCurveN()`
  accessors + internal `super.*` routing for `copyInternal` / `toLinear`.
- `modules/curved/src/test/java/org/locationtech/jts/spec/curveawareness/OptionCBlastRadiusProbe.java`
  -- the 32-operation blast-radius probe (single test method, prints
  a tally to stdout, no assertions because the tally **is** the
  evidence).
- `NOTE_OPTION_C.md` -- this file.

Probe runs on demand:

```
mvn -pl modules/curved test -Dtest=OptionCBlastRadiusProbe
```

The default Surefire run excludes everything under
`spec/curveawareness/`, so the probe doesn't affect normal CI.

## Cross-reference

- Spec doc: [`SPEC_F_CP.md`](SPEC_F_CP.md) §FCP-DOVE.
- Related: epic [#1195](https://github.com/locationtech/jts/issues/1195) §7 risk #1.
- The 50 % figure should be quoted in any future discussion of C as a
  feature-flagged mode; "fail-fast on Polygon API" is correct in
  principle but lethal in practice given how pervasive
  `getExteriorRing()` is inside `jts-core`.
