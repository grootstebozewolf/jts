# R-EQ — `equalsExact` semantics for curved geometries

> Spike / dovetail notes for the **R-EQ** sub-issue of the SFA Curve
> Awareness epic ([locationtech/jts#1195](https://github.com/locationtech/jts/issues/1195))
> and §7 risk #2.
> Companion to
> [`EqualsExactSemanticSpec.java`](src/test/java/org/locationtech/jts/spec/curveawareness/EqualsExactSemanticSpec.java)
> and [`EqualsExactCurrentBehaviourProbe.java`](src/test/java/org/locationtech/jts/spec/curveawareness/EqualsExactCurrentBehaviourProbe.java).

## What R-EQ is

Today:

```java
Geometry cs = new CurvedWKTReader().read("CIRCULARSTRING (0 0, 5 5, 10 0)");
Geometry ls = new CurvedWKTReader().read("LINESTRING (0 0, 5 5, 10 0)");
cs.equalsExact(ls);   // returns TRUE  -- spec-incorrect, type mismatch
ls.equalsExact(cs);   // returns TRUE  -- intentional? see asymmetry note below
```

Per OGC SFA `ST_Equals` is point-set equality so even the spec-side
answer for these two curves is debatable. The §7 wording uses
`equalsExact` (JTS's structural-equality method) and asserts the spec
intent is **false** — two geometries of different types should not be
"exactly equal" even when their control points coincide.

## Why it returns true today

`Geometry.equalsExact(Geometry)` is implemented as `equalsExact(other, 0)`
in each concrete subclass. That method's first check is:

```java
if (!isEquivalentClass(other)) return false;
```

The default `isEquivalentClass` in `Geometry` is strict
(`getClass().getName().equals(other.getClass().getName())`). But
`LineString` overrides it to be **lenient** so that a coordinate-
identical `LinearRing` compares equal to a plain `LineString`:

```java
// LineString.java
protected boolean isEquivalentClass(Geometry other) {
  return other instanceof LineString;
}
```

`CircularString` and `CompoundCurve` both **extend `LineString`**, so
they inherit that lenient check. A `CircularString` calling
`equalsExact` with a `LineString` argument passes the equivalence
gate and then compares coordinates — which match. Returns `true`.

`Polygon` does **not** override `isEquivalentClass`, so
`CurvePolygon.equalsExact(Polygon)` already returns `false` (strict
class-name check via the inherited `Geometry` default). The R-EQ
issue is therefore narrowly scoped to the two `LineString`-extending
curve types — `CircularString` and `CompoundCurve`.

## The asymmetry trap

The cleanest fix is an override on each LineString-extending curve
type:

```java
@Override
protected boolean isEquivalentClass(Geometry other) {
  return other instanceof CircularString;   // and respectively for CompoundCurve
}
```

That makes `cs.equalsExact(ls)` return `false`. But:

```java
ls.equalsExact(cs);   // still returns TRUE
```

Because `LineString.equalsExact` calls `this.isEquivalentClass(other)`
— not `other.isEquivalentClass(this)`. From the LineString side,
`other instanceof LineString` (a CircularString is a LineString) so
the gate passes. After the fix, `equalsExact` is **asymmetric**:

| Caller | Argument | Today | After R-EQ |
|---|---|---|---|
| `CircularString` | `LineString` | true | **false** |
| `LineString` | `CircularString` | true | still true |
| `CircularString` | `CircularString` (same coords) | true | true |
| `LineString` | `LineString` (same coords) | true | true |

The asymmetry is **inherent to `LineString`'s lenient policy** — it
exists to make `LinearRing` compare equal to a coordinate-identical
`LineString`, which is correct and desired. Tightening
`LineString.isEquivalentClass` to a strict class-name match would
break that equivalence and is a much larger behaviour change than R-EQ.

## The three live options

| Option | Approach | Symmetry | Trade-off |
|---|---|---|---|
| **A — override curve side only** | `isEquivalentClass` override on `CircularString`, `CompoundCurve` (and any other LineString-extending curve subtype the epic adds later) | asymmetric: `cs.eE(ls) == false`, `ls.eE(cs) == true` | Minimal change. Fixes the §7 spec violation on the curve side. The asymmetry is real but matches the *direction of awareness*: the curve-aware type knows it differs from a polyline, the unaware LineString doesn't. Needs a release-note bullet. |
| **B — tighten `LineString.isEquivalentClass` to strict** | `getClass() == LineString.class` (or `other instanceof LineString && !(other instanceof CircularString) && …`) | symmetric for curve-vs-line, but **breaks `LinearRing` ↔ `LineString` equivalence** | A much bigger behaviour change. Existing users comparing a `LinearRing` to a coordinate-identical `LineString` would suddenly get `false`. Real production code does this. Probably needs a deprecation cycle in `jts-core`. |
| **C — new `equalsExactCurveAware(Geometry)` method** | Keep `equalsExact` semantics unchanged; add a new method that does the strict comparison | preserves all current behaviour | Doesn't fix `equalsExact`; just adds a parallel API. Surface-area cost; nobody who has the bug today calls the new method. |

### Where we lean

**Option A.** Three reasons:

1. It fixes the spec violation on the side where the type information lives — a `CircularString` knows it's different from a `LineString`.
2. The asymmetry is a release-note item, not a correctness regression — coordinate-identical inputs of different types simply yield different answers depending on which side initiates the comparison.
3. The implementation is one method per curve subtype, no `jts-core` change required. Option B's `jts-core` change is too big for the marginal symmetry gain.

The case **against** A is the asymmetry surprise. Most users won't notice (most `equalsExact` calls compare same-type instances), but a release-note bullet is mandatory and the curve-types' Javadoc should call out the asymmetric contract.

**Option B** stays as a future cleanup if the project ever wants `LinearRing ↔ LineString` equivalence to also tighten. That's a separate, larger conversation.

**Option C** is rejected here: it adds API surface without fixing the bug.

## Measurement on this branch

The spike implements Option A on `CircularString` and `CompoundCurve`,
runs the full curved-module test suite, and counts breakage:

- **`mvn -pl modules/curved test` before override**: 55/55 green
- **after override** (this commit): 55/55 green

No existing test in `jts-curved` relied on the spec-incorrect
behaviour. `jts-core` doesn't construct `CircularString` or
`CompoundCurve`, so its 2288 tests are unaffected. The spec suite
(`EqualsExactSemanticSpec`) flips its three curve-vs-line tests from
red to green; the one test documenting the asymmetry stays green.

## What this spike kept in the branch

- `modules/curved/src/main/java/.../CircularString.java` — adds the
  `isEquivalentClass` override
- `modules/curved/src/main/java/.../CompoundCurve.java` — same
- `modules/curved/SPEC_R_EQ.md` — this file
- `modules/curved/src/test/java/.../spec/curveawareness/EqualsExactSemanticSpec.java`
  — red→green spec for Option A
- `modules/curved/src/test/java/.../spec/curveawareness/EqualsExactCurrentBehaviourProbe.java`
  — probe that prints today's truth table

Probe runs on demand: `mvn -pl modules/curved test -Dtest=EqualsExactCurrentBehaviourProbe`.

## Smallest concrete next step

A one-line **A / B / C** maintainer ack on the epic issue. Option A is
already implemented on this spike branch, so the implementation PR
is essentially:

1. Cherry-pick the two `isEquivalentClass` overrides from this
   branch.
2. Add Javadoc to `CircularString` / `CompoundCurve` calling out the
   asymmetric contract with `LineString.equalsExact(CircularString)`.
3. Add the release-note bullet.
4. Delete the now-green methods in `EqualsExactSemanticSpec` per the
   §5 convention.

## Cross-reference

- Epic: [locationtech/jts#1195](https://github.com/locationtech/jts/issues/1195) §7 risk #2.
- Companion: [`SPEC_F_CP.md`](SPEC_F_CP.md) and [`SPEC_AT_NS.md`](SPEC_AT_NS.md) — same option-spike + ack-then-implement shape.
