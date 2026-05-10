# Epic: SFA / ISO 19125-2 Curve Awareness in JTS

> **Status:** Draft, derived from the spike on [`feature/sfa-curve-buffer-spike`](https://github.com/grootstebozewolf/jts/tree/feature/sfa-curve-buffer-spike).
> **Format:** This file is meant to be lifted into the body of a GitHub epic on locationtech/jts. Each `S-*`/`F-*`/etc. tag below is the suggested sub-issue identifier; one tracking checkbox per tag.

## Goal

Make JTS Topology Suite genuinely curve-aware — preserve `CIRCULARSTRING`, `COMPOUNDCURVE`, `CURVEPOLYGON`, `MULTICURVE`, `MULTISURFACE`, and `TRIANGLE` (per OGC SFA / ISO 19125-2) as first-class geometries through every algorithm, not just as flat polylines that happen to round-trip the WKT keyword.

Today the `jts-curved` module is a deliberate *Phase-1 stand-in* (see the class-level doc on `CurvePolygon` / `CompoundCurve`): the types exist, parse, and round-trip their *coordinate* WKT, but every algorithm in `jts-core` treats them as their flat parent (`LineString`, `Polygon`, `GeometryCollection`) and silently linearises before computing. This epic tracks the work to lift each operation in turn.

## Why this matters

1. **Performance.** Densifying every arc to ≥50 chord points before each operation is a 50× hit on a clean geometry; chained operations compound it.
2. **Precision.** Repeated chord–rebuild–chord cycles drift control points by ULPs and break snap mismatches against external SFA-conforming sources.
3. **Interop.** Round-tripping with PostGIS, Oracle Spatial, and NetTopologySuite produces visibly different shapes after every operation today.
4. **Discoverability.** TestBuilder cannot currently show a curved buffer, a curved boundary, or a curved buffer of a curved input — every operation flattens silently, so users of the spec types never see the value.

## Out of scope (this epic)

- 3D solids: `POLYHEDRALSURFACE`, `TIN`. Their structural/drawing support landed in the spike for visualisation, but no algorithm in this epic handles 3-D semantics.
- Elliptic arcs (`COMPOUNDCURVE` ISO extensions like `ELLIPSARC`) — JTS doesn't model ellipse geometry.
- Z/M-ordinate interpolation across densified arcs. Today Z/M propagates as-is from the 3 control points; a separate ticket may add interpolation.
- Database / serialisation backends beyond WKT (WKB curve-aware extensions are tracked separately).

## Already landed on `feature/sfa-curve-buffer-spike`

| commit prefix | summary |
| --- | --- |
| `arch:` | Structural `CompoundCurve` (member list, segment-aware copy/toLinear) |
| `arch:` | Member-structured WKT round-trip for `CompoundCurve` |
| `arch:` | `CurvedShapeWriter` walks `CompoundCurve` member-by-member, arc-renders |
| `feat:` | `LineHandlingFunctions.mergeCurves` — endpoint-matching arc-aware sibling of `mergeLines` |
| `fix:`  | Anchor member control points in `CompoundCurve.toLinear` (no chord drift at junctions) |
| `fix:`  | Block-comment paste regression in WKT panel |
| (orig)  | `bufferCurveWithParams` linearisation hook (Phase-5 spike) |

These are *not* re-listed in the sub-issue table below — they are foundations the rest of the work builds on.

## Sub-issue table

Tags are short so they fit in commit subjects after the bucket prefix, e.g. `feat: BUF-1 analytical single-arc buffer`. Each row is intended to be assignable to a single contributor without stepping on a neighbour.

### Foundations (jts-curved structural completeness)

| tag | scope | depends on |
| --- | --- | --- |
| **F-CP** | Structural `CurvePolygon` — shell + holes carry `CompoundCurve` rings; `copyInternal` / `toLinear` walk them. WKT reader/writer round-trips structure. | (CompoundCurve done) |
| **F-MC** | Structural `MultiCurve` — preserves member subtypes (`LineString` vs `CircularString` vs `CompoundCurve`) through copy and WKT. | F-CP done first to mirror the pattern |
| **F-MS** | Structural `MultiSurface` — preserves `Polygon` vs `CurvePolygon` member subtypes. | F-CP |
| **F-RD** | `CurvedShapeWriter` rendering paths for structural `CurvePolygon`, `MultiCurve`, `MultiSurface` (today only `CircularString`/`CompoundCurve`/`MultiCurve` partially). | F-CP, F-MC, F-MS |

### Metrics

| tag | scope |
| --- | --- |
| **M-LEN-CS** | `CircularString.getLength` uses `R · sweep` (analytical arc length) per arc triple, not chord sum. |
| **M-LEN-CC** | `CompoundCurve.getLength` walks members and accumulates analytical lengths. |
| **M-AREA-CP** | `CurvePolygon.getArea` uses Green's theorem with circular-segment correction, not flat-polygon area. |
| **M-DIM** | `getDimension` / `getCoordinateDimension` correct for all curved subtypes (incl. empty). |

### Boundary

| tag | scope |
| --- | --- |
| **B-CP** | `CurvePolygon.getBoundary` returns a `CompoundCurve` (or `MultiCurve` if holes exist), not a flat `LinearRing`. |
| **B-MS** | `MultiSurface.getBoundary` returns a `MultiCurve`. |
| **B-CC** | `CompoundCurve.getBoundary` returns the two endpoints as a `MultiPoint` (open) or `MULTIPOINT EMPTY` (closed) — already correct for line semantics, but verify with the new structural type. |

### Buffer / Offset

| tag | scope |
| --- | --- |
| **BUF-1** | Analytical buffer of a single 3-point `CircularString` → `CurvePolygon(CompoundCurve(outerArc, cap0, innerArcRev, cap1))`. (Phase-1 of the spike's own plan.) |
| **BUF-N** | Multi-arc `CircularString` and `CompoundCurve` — accumulate side offsets into two long `CompoundCurves` (left/right), add caps. |
| **BUF-NEG** | Negative buffer: `R < d` shrink/eliminate cases handled gracefully. |
| **OFF**   | `OffsetCurve` arc-aware: takes a `CircularString` and emits an analytically-offset `CircularString`. |
| **VBF**   | `VariableBuffer` arc-aware (start/mid/end distance interpolation along arc). |

### Distance

| tag | scope |
| --- | --- |
| **D-PT** | Point-to-arc distance: project onto circle, clamp to arc sweep. |
| **D-AA** | Arc-to-arc distance: two-circle distance, clamped. |
| **D-OP** | `DistanceOp` for curved inputs without densification. |
| **D-HF** | `DiscreteHausdorffDistance` / `DiscreteFrechetDistance` curve-aware. |

### Predicates / Relate (DE-9IM)

| tag | scope |
| --- | --- |
| **R-PR** | Arc-aware `relate(...)` matrix — interior/boundary/exterior of arcs. |
| **R-CONT** | `contains/within/overlaps/touches/crosses/intersects/disjoint/coveredBy/covers` for any combination of curved + flat. |
| **R-EQ**  | `equalsTopo` / `equalsExact` for curve-bearing geometries (treat `CIRCULARSTRING(p0, p1, p2)` and a chord `LINESTRING(p0, p1, p2)` as **not** equal under `equalsExact`). |

### Noding (foundation for overlay & predicates)

| tag | scope |
| --- | --- |
| **N-AA** | Arc-vs-arc intersection (analytical, two-circle → 0/1/2 points, then sweep-clip). |
| **N-AL** | Arc-vs-line-segment intersection (line-circle → 0/1/2 points, then segment-clip). |
| **N-SS** | `SegmentString` arc variant + arc-aware `Noder` (so the existing overlay pipeline can consume it). |

### Overlay / Boolean

| tag | scope | depends |
| --- | --- | --- |
| **OV** | `Geometry.union/intersection/difference/symDifference` arc-preserving. Output is `CurvePolygon` / `CompoundCurve` / `MultiSurface` where the boundary is curved. | N-AA, N-AL, N-SS |

### Centroid / Interior Point

| tag | scope |
| --- | --- |
| **C-LIN** | Centroid of `CircularString` / `CompoundCurve` via arc-length-weighted mean (sector centroids combined). |
| **C-AREA** | Centroid of `CurvePolygon` via sector-weighted mean. |
| **C-IP** | `InteriorPointArea` for `CurvePolygon` — pick a point provably inside the curved boundary. |

### Validity

| tag | scope |
| --- | --- |
| **V-CP** | `IsValidOp` for `CurvePolygon`: no boundary self-intersection (arc + arc + line topology), correct ring orientation, holes inside shell. |
| **V-CS** | `IsSimpleOp` for `CircularString` / `CompoundCurve` (no self-intersection along the arc sweep). |

### Hulls

| tag | scope |
| --- | --- |
| **H-CV** | `ConvexHull` for arc geometries — extreme points of an arc (the 4 cardinal-direction points within sweep + 2 endpoints). |
| **H-CC** | `ConcaveHull` for arc geometries. |

### Simplification

| tag | scope |
| --- | --- |
| **S-DP** | `DouglasPeuckerSimplifier` recognises arc spans (preserves arc identity; does not flatten an arc to its chord). |
| **S-VW** | `VWSimplifier` curve-aware. |
| **S-TP** | `TopologyPreservingSimplifier` curve-aware. |

### Affine transforms

| tag | scope |
| --- | --- |
| **AT-S**  | Similarity transforms (rotation, uniform scale, translation, reflection) preserve `CircularString` identity — transform 3 control points, keep arc. |
| **AT-NS** | Non-similarity transforms (shear, non-uniform scale) detect → `toLinear(tolerance)` → transform polyline (since shear of an arc is an ellipse arc, which JTS doesn't model). |

### Linear referencing

| tag | scope |
| --- | --- |
| **LRF-LEN** | `LengthIndexedLine` over `CircularString` parameterises by arc length, not chord-cumulative length. |
| **LRF-LOC** | `LocationIndexedLine` for `CompoundCurve` (member-aware). |

### Densifier interop

| tag | scope |
| --- | --- |
| **DSF** | `Densifier` recognises `CircularString` input and uses `toLinear(tolerance)` instead of segment-by-segment chord splitting. |

### Triangulation / Voronoi

| tag | scope |
| --- | --- |
| **TRI-DT** | `DelaunayTriangulationBuilder` accepts curved boundary input (densifies internally with `toLinear(tolerance)`). |
| **TRI-VR** | `VoronoiDiagramBuilder` same. |

### Polygonizer / Coverage

| tag | scope |
| --- | --- |
| **PLG** | `Polygonizer` accepts `CompoundCurve` input and emits `CurvePolygon` faces. |
| **COV** | `CoverageUnion` arc-aware. |

### Snapping / Precision

| tag | scope |
| --- | --- |
| **PRC-SN** | Snap-to-grid for `CircularString`: snap the 3 control points; if R, C and sweep stay on grid the arc is preserved, otherwise densify and snap chord points. |

### TestBuilder integration

| tag | scope |
| --- | --- |
| **TB-T**  | `CompoundCurveTool`, `CurvePolygonTool` drawing tools (sibling of the existing `CircularStringTool` / `TriangleTool` / `TinTool`). |
| **TB-FN** | Function-tree coverage badge: every entry in the function tree gets a small visual marker (`◯` curve-passthrough, `●` curve-aware native, `✕` flattens) so it's discoverable from the UI which operations to trust on curved inputs. |

## Suggested order

```
   ┌───────────────────┐
   │ Foundations       │  F-CP, F-MC, F-MS, F-RD
   └──────┬────────────┘
          │
   ┌──────▼────────────┐
   │ Metrics           │  M-LEN, M-AREA, M-DIM
   │ Boundary          │  B-CP, B-MS, B-CC
   │ Validity          │  V-CP, V-CS
   └──────┬────────────┘
          │
   ┌──────▼────────────┐
   │ Distance          │  D-PT, D-AA, D-OP, D-HF
   │ Centroid          │  C-LIN, C-AREA, C-IP
   └──────┬────────────┘
          │
   ┌──────▼────────────┐
   │ Buffer / Offset   │  BUF-1, BUF-N, BUF-NEG, OFF, VBF
   │ Hulls             │  H-CV, H-CC
   │ Simplification    │  S-DP, S-VW, S-TP
   │ Affine            │  AT-S, AT-NS
   │ Linear-Ref        │  LRF-LEN, LRF-LOC
   │ Densifier         │  DSF
   │ Triangulation     │  TRI-DT, TRI-VR
   │ Snapping          │  PRC-SN
   └──────┬────────────┘
          │  (parallelisable from here)
   ┌──────▼────────────┐
   │ Noding            │  N-AA, N-AL, N-SS
   └──────┬────────────┘
          │
   ┌──────▼────────────┐
   │ Overlay           │  OV
   │ Predicates/Relate │  R-PR, R-CONT, R-EQ
   │ Polygonizer/Cov.  │  PLG, COV
   └───────────────────┘
```

## Conventions

- Bucketed commit subjects per [memory feedback](#): `fix:|feat:|spec:|test:|arch:|refactor:`. Prefix the sub-issue tag right after the bucket, e.g. `feat: BUF-1 analytical single-arc buffer of a CircularString`.
- One tag per commit. If a commit naturally spans two tags, that usually means the tags should merge in the table above — propose an edit to this file in the same PR.
- Every sub-issue lands with a green test under its tag; the corresponding red test in `CurveAwarenessSpecTest` (see below) is **deleted** (not just edited green) when the sub-issue closes, so the spec class stays a live count of remaining work.

## Red tests

The spec is captured as a single failing test class on the spike branch: [`modules/curved/src/test/java/org/locationtech/jts/spec/curveawareness/CurveAwarenessSpecTest.java`](modules/curved/src/test/java/org/locationtech/jts/spec/curveawareness/CurveAwarenessSpecTest.java). One test method per sub-issue tag, each documenting the gap with a `fail("TAG: ...")` message. Running `mvn -pl modules/curved test -Dtest=CurveAwarenessSpecTest` prints a report of every operation that still needs work.

The class is named `…SpecTest.java` (Surefire-discovered) so it shows up in CI as red — the deliberate intent is "the build is red until JTS is curve-aware". If a contributor wants to silence it locally while working on a single tag, they can `mvn -Dtest='!CurveAwarenessSpecTest' test`. Once a tag's behaviour is implemented, **delete the corresponding test method** (do not edit it green) — the count of remaining methods is the count of remaining work.
