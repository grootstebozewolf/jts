# Upstream PR Queue — JTS Curve Awareness EPIC ([locationtech/jts#1195](https://github.com/locationtech/jts/issues/1195))

> Status as of 2026-06-17. Tracks the order in which the per-TAG branches on
> `grootstebozewolf/jts` are opened as PRs against `locationtech/jts:master`.
> Nothing below is opened yet — the queue is held until the foundation PRs land.

## Why a queue (and not 12 PRs at once)

Every TAG branch is built on top of the F-CP foundation, which today lives only
in the **open** gate PRs:

- [#1194](https://github.com/locationtech/jts/pull/1194) — SQL/MM extension hooks + opt-in `jts-curved` module
- [#1198](https://github.com/locationtech/jts/pull/1198) — F-CP / F-MC / F-MS structural composites

Because that foundation is **not yet in `master`**, a PR from a TAG branch to
`master` would diff against `master` and include the entire unmerged foundation
plus the TAG — a large, conflated, non-self-contained diff. (You cannot set a
fork branch as the base of an upstream PR, so fork→upstream PRs can't truly stack
on each other.) Each TAG PR is therefore opened **only after its base reaches
`master`**, so every diff is a clean single-TAG change — the "one TAG, one
reviewable PR" model from EPIC §4.

PR bodies follow the #1200 wording style (problem → change bullets → verification
→ footer). Titles use the EPIC bucket prefix + TAG.

## Dependency tree

```
#1194 (extension hooks) ─► #1198 (F-CP/F-MC/F-MS foundation)        [must merge first]
   ├─ M-DIM        (independent of M-LEN-CS)
   ├─ B-CC         (independent of M-LEN-CS)
   └─ M-LEN-CS ─┬─ M-AREA-CP ─► C-AREA
                ├─ C-LIN
                ├─ D-PT
                ├─ N-AL ─► N-AA ─► V-CS
                ├─ PRC-SN
                └─ DSF
```

## Firing plan

1. When **#1198** merges to `master` → open Tier 1 (M-LEN-CS, M-DIM, B-CC).
2. When **M-LEN-CS** merges → open Tier 2 (M-AREA-CP, C-LIN, D-PT, N-AL, PRC-SN, DSF).
3. When **M-AREA-CP** merges → open C-AREA. When **N-AL** merges → open N-AA;
   when **N-AA** merges → open V-CS.

This keeps the maintainer's queue to ~3 PRs at a time, never 12 at once. Webhook
events don't cover CI-merge transitions, so the merge of each base is checked
explicitly before firing the next tier.

---

## Tier 0 — already open upstream (gate)

| PR | TAG(s) | State |
|----|--------|-------|
| [#1194](https://github.com/locationtech/jts/pull/1194) | base / extension hooks | open |
| [#1198](https://github.com/locationtech/jts/pull/1198) | F-CP, F-MC, F-MS | open |

## Tier 1 — open when #1198 is merged (base: `master`)

### ① `feat: M-LEN-CS analytical CircularString length (r·θ) (#1195)` — head `feature/sfa-curve-M-LEN-CS`
`CircularString.getLength()` inherited `LineString`'s chord-polyline length,
undercounting every arc. It now sums the analytical arc length `r·θ` over each
consecutive control-point triple.
- Accumulate the sweep in the arc's rotational direction so a sub-arc > π is
  measured the long way; collinear triples fall back to the chord.
- Add `CircularArcs` (arc-primitive home) and `CircularStringLengthTest`.
- Verified against the NetTopologySuite.Proofs extracted oracle (`ARC_LENGTH`),
  incl. major/clockwise arcs; also fixed a latent branch-cut bug in the in-repo
  `CurveRefRunner`.

Part of #1195. Depends on #1198.

### ② `test: M-DIM dimension of curve/surface types (#1195)` — head `feature/sfa-curve-M-DIM` *(independent)*
The extended types already report the correct topological dimension by
inheriting from their JTS supertypes; this holds for empty instances and
preserves Z/M. M-DIM needs no production change.
- Add `CurveDimensionTest` pinning the lineal/areal mapping (+ empty +
  coordinate-dimension) so a future supertype refactor can't silently regress it.

Part of #1195. Depends on #1198.

### ③ `test: B-CC CompoundCurve boundary (open = endpoints, closed = empty) (#1195)` — head `feature/sfa-curve-B-CC` *(independent)*
The boundary of a (flat phase-1) `CompoundCurve` already follows the Mod-2 rule
via the inherited `BoundaryOp`; no production change needed.
- Add `CompoundCurveBoundaryTest`: open → first/last control points (never the
  arc mid point, checked with semicircle controls), closed loops (incl. an arc
  full circle) → empty, boundary dimension `P`/`FALSE`.

Part of #1195. Depends on #1198.

## Tier 2 — open when M-LEN-CS (①) is merged (base: `master`)

### ④ `feat: M-AREA-CP arc-aware CurvePolygon.getArea() (#1195)` — head `feature/sfa-curve-M-AREA-CP`
`CurvePolygon.getArea()` returned the control-polygon area, so a disk shell came
out as its inscribed polygon. It now adds the signed circular-segment correction
`(r²/2)(θ−sinθ)` per arc to the endpoint-polygon shoelace, holes subtracted, so a
disk is exactly `πr²`.
- Segment sign = orientation of (start, mid, end), combining coherently with the
  shoelace term regardless of ring orientation.
- Verified against the oracle (`ARC_AREA`), incl. major/clockwise arcs.

Part of #1195. Depends on ① (M-LEN-CS).

### ⑤ `feat: C-LIN arc-length-weighted CircularString centroid (#1195)` — head `feature/sfa-curve-C-LIN`
`getCentroid()` returned the chord-polyline centroid; it now returns the
arc-length-weighted centroid (each arc's mass at `r·sin(θ/2)/(θ/2)` from its
centre).
- Verified against the oracle `ARC_CENTROID` mode over major/CW/off-centre arcs.

Part of #1195. Depends on ①.

### ⑥ `feat: D-PT analytical point-to-arc distance (#1195)` — head `feature/sfa-curve-D-PT`
Adds `CircularArcs.distancePointToArc`: radial distance when the foot is within
the sweep, else the nearer endpoint.
- Verified against the oracle `ARC_DISTANCE` mode (~1e-15 over major/CW/off-centre
  arcs + off-span points).

Part of #1195. Depends on ①.

### ⑦ `feat: N-AL circular arc / line-segment intersection (#1195)` — head `feature/sfa-curve-N-AL`
Adds `CircularArcs.intersectSegment`: exact circle-line solve clamped to the arc
sweep and segment range (0/1/2 points).
- Bit-pinned against the oracle `ARC_SEGMENT_XY` mode (the proper arc-segment
  enumerator; the old `ARC_LINE_XY` single-point projection is superseded) —
  24 committed vectors + 400 random pairs.

Part of #1195. Depends on ①.

### ⑧ `feat: PRC-SN arc snap-to-grid decision (#1195)` — head `feature/sfa-curve-PRC-SN`
Adds `CircularArcs.snapDecision` (PRESERVE/DENSIFY/DEGEN) + `snapToScale`. After
snapping control points to a grid, PRESERVE iff the circumcentre also lands on the
grid — an exact `BigInteger` divisibility test.
- Verified against the oracle `CURVE_SNAP_DECISION` / `SNAP_SCALED` modes (all
  three outcomes, 500 random cases).

Part of #1195. Depends on ①.

### ⑨ `feat: DSF curve-aware densification via arc tessellation in toLinear (#1195)` — head `feature/sfa-curve-DSF`
Makes `CircularString`/`CompoundCurve` `toLinear(tol)` sample arcs to sagitta ≤
tol (`CircularArcs.tessellate`); `tol≤0` keeps control points (preserves the F-CP
`toLinear(0.0)` contract). Adds `CurvedDensifier` routing `Linearizable` through
`toLinear`, else the core `Densifier`.
- Verified: points on the arc, chord error ≤ tol vs a 4000-pt reference, a
  half-circle at 1% → 12 chords/13 points (EPIC §2), plain `LineString` matches
  the core `Densifier`.

Part of #1195. Depends on ①.

## Tier 3 — open when parent merges

### ⑩ `feat: C-AREA arc-aware CurvePolygon area centroid (#1195)` — head `feature/sfa-curve-C-AREA` — after M-AREA-CP (④)
Adds Green's-theorem area moments (segment first-moment `CircularArcs.segmentCentroid`),
holes subtracted, divided by the arc-aware area.
- Verified against the oracle `ARC_AREA_CENTROID` mode (general off-midpoint arcs,
  ~8e-14 over 400 cases — this pinning surfaced, and the oracle then fixed, a
  mid-direction bug) + a densified cross-check.

Part of #1195. Depends on ④.

### ⑪ `feat: N-AA circular arc / arc intersection (#1195)` — head `feature/sfa-curve-N-AA` — after N-AL (⑦)
Adds `CircularArcs.intersectArc`: two-circle radical-line solve clamped to both
sweeps (0/1/2 points; concentric → empty).
- Verified against the oracle `ARC_ARC_XY` mode (exact counts, 300 random arc
  pairs).

Part of #1195. Depends on ⑦.

## Tier 4 — open after N-AA (⑪)

### ⑫ `feat: V-CS arc-aware CircularString simplicity (#1195)` — head `feature/sfa-curve-V-CS`
`isSimple()` tested the chord polyline; it now tests the arcs
(`ArcStringSimplicity`) — simple iff no two pieces meet except at shared
adjacency/closing endpoints.
- Reuses `intersectArc` / `intersectSegment` + `RobustLineIntersector`; handles
  same-circle and collinear-overlap runs.
- Verified with anchors (incl. an `ARC_ARC_XY`-confirmed crosser) + a densified
  `isSimple` cross-check over 60 random chains.

Part of #1195. Depends on ⑪.

---

## Not in this queue

- **Oracle wishlist:** `ARC_SEGMENT_XY` (W1 ✅ delivered — N-AL now bit-pinned),
  `ARC_ARC_DISTANCE` (W2 → D-AA), `CP_VALID`/`RING_SIMPLE` (W3 → V-CP),
  `ARC_OFFSET_XY` (W4 → BUF/OFF), `CURVE_RELATE_MATRIX` (W5 → R-PR/R-CONT).
  See [proofs#224](https://github.com/grootstebozewolf/NetTopologySuite.Proofs/issues/224)
  and #1195 §7.
- **Still open, no branch yet:** V-CP, D-AA, D-OP, D-HF, the Phase-4 construction
  set, N-SS, Phase-6 (overlay/predicates/polygonizer/coverage), F-RD, TRI-*, TB-*.
