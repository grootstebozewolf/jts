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
  ExactQuadraticBezier
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
| Year 2     | Introduce Zoo (siblings)                   | Quadratic Bézier → Ellipse → Clothoid → single-span NURBS |
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
- Sweep/angle logic is package-private on `exactcurve.AngleBetween` — not a
  public shim. Callers use `ExactCircularArc` cells.
- Colinear 3-control windows degrade to an exact chord. `isExact()` stays
  true (closed form). `isArc()` is the circular-vs-chord discriminator and
  is **not** on the thin protocol.
- `toLinear(tolerance)` is the only densify path and is named as such.
- `OrientableSegment` is an optional adapter (Bible §3): public surface is
  start/end/length/orientationIndex/intersects only; implementations are
  package-private and compose `ExactCircularArc`.
