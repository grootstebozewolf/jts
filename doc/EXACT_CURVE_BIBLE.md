# Exact Curve Bible

*JTS Arc-Native Programme*  
Canonical architectural document — August 2026

---

### 1. Purpose

This document is the single source of truth for the long-term design of exact curve support in JTS.

It defines the principles, the foundational decision, the shape of the ExactCurve* family, the rules that may never be broken, and the expected evolutionary path over a 10-year horizon.

All future work on circular arcs, clothoids, Béziers, elliptical arcs, NURBS, and any subsequent curve types must conform to this bible.

---

### 2. Core Principles

1. **Representation first**  
   Pure, immutable value types are the foundation. Behavioural abstractions are secondary and temporary.

2. **Exact where claimed**  
   A method or type that claims exactness must be exact. Densification is only allowed through an explicitly documented shim (`toLinear(tolerance)`).

3. **No silent linearisation**  
   Linearisation must never happen as a hidden fallback. The chainsaw path is always visible and intentional.

4. **Composition over hierarchy**  
   Prefer sibling pure types + a thin common protocol. Deep inheritance and god-interfaces are forbidden.

5. **Core contracts stay honest**  
   `org.locationtech.jts.noding.SegmentString` remains a linear segment contract. Any curve-aware noding lives in `jts-curve` only.

6. **Performance is a ratchet, not a race**  
   Exact implementations must stay ≤ 1.15× the cost of densifying the same geometry. The ratchet is measured per curve type.

7. **10-year maintainability > short-term cleverness**  
   Every design choice is evaluated against cognitive load and abstraction debt in year 5–10.

---

### 3. Foundational Decision (Year 1)

**ExactCircularArc is the privileged pure primitive.**

It is the atom of the system. All higher constructs must be expressible in terms of it or as siblings to it.

Option B (`OrientableSegment`) is demoted to a thin, optional adapter. It must not become the centre of the design. Any long-lived version of it must compose ExactCircularArc (and later other Exact* types) rather than re-implement geometry.

This decision is final.

---

### 4. The ExactCurve* Model

#### 4.1 Shape

```text
org.locationtech.jts.algorithm.exactcurve

  ExactCircularArc          // privileged, closed-form
  ExactCubicBezier          // amendment A1: was ExactQuadraticBezier
  ExactEllipticalArc
  ExactClothoid
  ExactNurbsSegment         // single-span first

  ExactCurve                // thin sealed interface
```

#### 4.2 ExactCurve protocol (thin)

Only the following methods belong on the common interface:

- `Coordinate getStart()`
- `Coordinate getEnd()`
- `double length()`
- `Coordinate pointAt(double t)`          // t ∈ [0,1]
- `Geometry toLinear(double tolerance)`
- `boolean isExact()`

All other operations (projection, curvature, intersection, offset, etc.) live on the concrete types.

#### 4.3 Design rules for new types

- Must be pure value types (immutable).
- Must own their own mathematics.
- Must not force ExactCircularArc to pay for their complexity.
- Must document their precision contract clearly (exact vs high-quality numerical).
- Single-span NURBS before multi-span NURBS.
- Affine reductions (e.g. ellipse → circle) are allowed only when exactness is preserved and documented.

---

### 5. Evolutionary Roadmap

| Period     | Focus                                      | Status of Zoo                          |
|------------|--------------------------------------------|----------------------------------------|
| Year 1     | Lock ExactCircularArc                      | Circular only                          |
| Year 2     | Introduce Zoo (siblings)                   | Ellipse → Cubic Bézier → Clothoid → single-span NURBS (amendment A1) |
| Year 3–4   | Consumers (relate, metrics, limited noding)| Stable ExactCurve protocol             |
| Year 5–7   | Selective expansion (multi-span NURBS, etc.)| Only when clear demand exists          |
| Year 8–10  | Maintenance & hardening                    | Minimal surface growth                 |

---

### 6. Hard Rules (Never)

- Never make `SegmentString` non-linear in core.
- Never claim exactness while calling densify internally.
- Never grow a rich abstract base class under ExactCurve.
- Never let OrientableSegment (or successor) become the primary way to talk about curves.
- Never start the full 74-file N-SS hierarchy from a HotPixel or Proofs card.
- Never walk a `SHARED_SNAPPED_RAY`.
- Never prioritise short-term performance wins that increase long-term abstraction debt.

---

### 7. Performance & Precision Contract

- ExactCircularArc must remain the fastest member of the family (closed form).
- Other types are allowed to be more expensive; the 1.15× ratchet is measured against densification of *the same type*.
- Every Exact* type must carry property-based / million-trial style tests for its critical operations.
- Floating-point stress cases (near-degenerate, major/minor, full cycle, near-zero length) are first-class requirements.

---

### 8. Relationship to the rest of JTS

- Laser kits (OverlayNGCurve, CurveExact, MIC/LEC, metrics, DE-9IM, etc.) consume Exact* types.
- Densify remains the documented escape hatch and the chainsaw path.
- Any future CurveSegmentString lives only inside `jts-curve` and presents a chord envelope + typed payload.
- Naming remains strict: `OverlayNGCurve` never becomes `*Curved*`.

---

### 9. Change Control

Changes to this bible require explicit architectural sign-off.  
Local optimisations and new curve types are welcome; changes to the principles or the foundational decision are not.

This document supersedes all previous Proofs option discussions, temporary STOP rules, and ad-hoc sequencing notes once accepted.

---

**End of Bible**

*Signed as Architect & Sequencer — August 2026*

## Implementation notes (Year 1 lock)

- Java 8: `ExactCurve` is a thin interface, conceptually sealed (only Exact*
  types implement it). Do not add methods. Do not introduce an abstract base.
- `org.locationtech.jts.algorithm.exactarc` is a deprecated alias package so
  the B-team `AngleBetween` import still compiles. New code uses `exactcurve`.
- Colinear 3-control windows degrade to an exact chord. `isExact()` stays
  true (closed form). `isArc()` is the circular-vs-chord discriminator and
  is **not** on the thin protocol.
- `toLinear(tolerance)` is the only densify path and is named as such.
- Optional adapter: `OrientableSegment` / `OrientableSegments` (Bible §3) —
  public surface is start/end/length/orientationIndex/intersects only;
  implementations are package-private and compose `ExactCircularArc`.
  See `doc/ORIENTABLE_SEGMENT_ADAPTER.md`.

### Audit (`feature/sfa-curve-rgr` @ `d8c4c9b8`)

Tree matches §3–§4.2 and the bullets above: `ExactCircularArc` + thin
`ExactCurve` in `org.locationtech.jts.algorithm.exactcurve`; no extra
protocol methods; `OrientableSegment` composes `ExactCircularArc`; no
Year-2 `Exact*` zoo types; `exactarc.AngleBetween` remains a deprecated
alias.

Already locked on this branch:

- [#63](https://github.com/grootstebozewolf/jts/pull/63) `6b1dbac1` —
  Year-1 lock (`ExactCircularArc`, thin `ExactCurve`, `toLinear` only).
- [#66](https://github.com/grootstebozewolf/jts/pull/66) `36ed1dce` —
  `OrientableSegment` adapter.
- [#125](https://github.com/grootstebozewolf/jts/pull/125) `81a16be9` —
  amendment A1 (Year-2 zoo membership only; §2/§3 untouched).

### Year-1 leftover punch

- Protocol-surface pin (`ExactCurve` exactly six methods; `isArc` off
  the interface) and colinear/coincident `toLinear` chord pins — closed
  by the Year-1 closeout PR (tests + protocol javadoc only).
- Core `SegmentString` already grew `SegmentKind` /
  `CircularNodedSegmentString` (MMF Option B, pre-bible). Do not expand.
  Do not unwind here. **HOLD**.
- Extra 1M cells for §7 near-degenerate / full-cycle stress: existing
  L1/L2/P1 handover stands (`doc/PROOFS_OPTION_A_HANDOVER.md`). Unit
  pins for major-arc and coincident chord added; extra 1M cells **HOLD**.

### HOLD (do not implement from this lock)

- Year-2 zoo: `ExactEllipticalArc`, `ExactCubicBezier`, `ExactClothoid`,
  `ExactNurbsSegment`
- N-SS hierarchy, HotPixel-driven N-SS, Proofs 64-a sweep
- `SHARED_SNAPPED_RAY` walk
- Making `SegmentString` non-linear (or remaking the existing Option B
  kinds)
- Growing `ExactCurve` / a rich abstract base
- Reminting ADR-0004
- Renaming `OverlayNGCurve` to `*Curved*`
- Non-circular ports

## Amendments

### A1 — ExactCubicBezier replaces ExactQuadraticBezier (2026-08-27)

Signed off under §9 by the Architect & Sequencer during the Proofs #508
grilling session; recorded as NetTopologySuite.Proofs ADR-0004.

- **§4.1**: the zoo's Bézier member is `ExactCubicBezier`. The membership
  criterion is wild provenance: Esri's `BezierCurveSegment` is cubic, the
  Proofs corpus (`RelateBezier3.v`) and oracle (`B` token, 8 coordinates)
  are cubic, ISO/IEC 13249-3:2016 defines no Bézier, and no engine ships a
  quadratic-only segment. Quadratic remains reachable as the exact,
  rational degree-elevation special case inside the cubic type.
- **§5 Year 2 order re-derived**: quadratic's closed-form length was the
  premise of "Quadratic Bézier first"; cubic arc length has no elementary
  closed form. New order: **Ellipse → Cubic Bézier → Clothoid →
  single-span NURBS**, with the 3-point-circular-arc ↔ `rx = ry`
  elliptical-arc bridge theorem as the ellipse's first rung (the last
  closed-form equality target in the zoo; the affine reduction is
  sanctioned by §4.3).
- All other principles, the foundational decision (§3), and the hard
  rules (§6) are untouched.

### Library note — SQL/MM types on `feature/zoo` (2026-09-01)

ISO/IEC 13249-3 SQL/MM Spatial curve types (`CircularString`,
`CompoundCurve`, `CurvePolygon`, `MultiCurve`, `MultiSurface`) live on
`feature/zoo`. They are library geometry types, not Year-2 `Exact*`
zoo members. Year-2 `ExactEllipticalArc` / `ExactCubicBezier` /
`ExactClothoid` / `ExactNurbsSegment` remain HOLD. §2 and §3 unchanged.
