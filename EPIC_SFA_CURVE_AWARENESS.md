# Epic: SFA / ISO 19125-2 Curve Awareness in JTS

> **AI Disclosure** *(per the [Eclipse Foundation Generative AI Usage Guidelines for Committers](https://www.eclipse.org/projects/guidelines/genai/))*
>
> This document and the companion red-test class
> `modules/curved/src/test/java/org/locationtech/jts/spec/curveawareness/CurveAwarenessSpecTest.java`
> were largely AI-generated. The human contributor has reviewed and verified the
> technical content (cross-module impact, risk register, phase dependencies, TAG
> scope) for correctness. The AI-generated portions are made available under
> **CC0-1.0** (public domain dedication) and are *not* subject to the project's
> licence; human curation and edits are subject to the JTS dual licence.
>
> ```
> SPDX-License-Identifier: (EPL-2.0 OR EDL-1.0) AND CC0-1.0
> Assisted-by: Anthropic Claude Opus 4.7
> Assisted-by: xAI Grok
> ```

**Status:** Draft v3.
**Source branch:** [`feature/sfa-curve-buffer-spike`](https://github.com/grootstebozewolf/jts/tree/feature/sfa-curve-buffer-spike) on `grootstebozewolf/jts`.
**Audience:** locationtech/jts maintainers and contributors. Lift verbatim into a GitHub Epic / Discussion.

## 1. Goal

Make JTS preserve OGC SFA / ISO 19125-2 curve geometries — `CIRCULARSTRING`, `COMPOUNDCURVE`, `CURVEPOLYGON`, `MULTICURVE`, `MULTISURFACE`, `TRIANGLE` — through every algorithm where the math is sound, instead of silently linearising to flat parents on the way in.

Today `jts-curved` is a Phase-1 stand-in: types parse, round-trip WKT *coordinates*, and exist in the type hierarchy, but `jts-core` ignores their identity and densifies on contact. This epic tracks the lift, operation by operation.

## 2. Why

- **Performance.** Each densification step expands a typical arc to many chord points (≥50 for a half-circle at 1% chord-tolerance); pipelines that buffer → simplify → union compound the cost.
- **Precision.** Densify → operate → densify drifts control points by ULPs and breaks snap-to-grid round-trips against external SFA producers.
- **Interop.** PostGIS, Oracle Spatial, NetTopologySuite emit and consume curve geometries; today JTS round-trips them as visibly different shapes after every operation.
- **Discoverability.** TestBuilder cannot show a curved buffer or a curved boundary today; users of the spec types never see what their input was for.

## 3. Scope decisions

**In scope (this epic):**
- Algorithms operating on the six SFA curve types in 2-D.
- WKT round-trip with member structure (already partially landed).
- TestBuilder rendering and drawing tools.

**Deferred to sibling epics — explicitly tracked, not pretended-not-to-exist:**
- **WKB curve-aware extensions** (extended geometry-type codes 1000-series). PostGIS interop runs on WKB, so the *interop* benefit listed in §2 is incomplete without it. Not blocking the algorithm work, but the epic isn't *complete* without a tracked sibling. **Action:** open a child issue "WKB curve types" before this epic ships.
- 3-D solids (`POLYHEDRALSURFACE`, `TIN`). Their structural / drawing support landed in the spike for visualisation; no 3-D semantics here.
- Elliptic arcs (`ELLIPSARC`) — JTS has no ellipse model; adding one is bigger than this epic.
- Z / M ordinate interpolation across densified arcs (today they propagate from the 3 control points unchanged; every TAG that produces densified output should leave room for a future interpolation policy).

## 4. What's already landed (`feature/sfa-curve-buffer-spike`)

| Bucket | Summary |
|---|---|
| `arch:` | Structural `CompoundCurve` — segment-aware `copy` / `toLinear` |
| `arch:` | Member-structured WKT round-trip for `CompoundCurve` |
| `arch:` | `CurvedShapeWriter` walks `CompoundCurve` members, arc-renders |
| `feat:` | `LineHandlingFunctions.mergeCurves` (arc-aware sibling of `mergeLines`) |
| `fix:`  | Anchor member control points in `CompoundCurve.toLinear` (no junction drift) |
| `fix:`  | Block-comment paste regression in WKT panel |
| `spec:` | This epic + 49-method `CurveAwarenessSpecTest` red-test suite |
| (orig)  | `bufferCurveWithParams` linearisation hook (Phase-5 spike entry) |

These are foundations the rest of the work builds on, not separate sub-issues.

## 5. Tracking model

We are **not** opening 49 separate issues. Fragmenting the project board, spamming notifications, and forcing every reviewer to reconstruct the dependency graph is the wrong shape for work this large.

### Single source of truth: `CurveAwarenessSpecTest`

- Path: `modules/curved/src/test/java/org/locationtech/jts/spec/curveawareness/CurveAwarenessSpecTest.java`
- Run: `mvn -pl modules/curved test -Dtest=CurveAwarenessSpecTest`
- Starts with **49 failing methods**, one per TAG. Each `fail("TAG: …")` documents the spec.
- **When a TAG ships, delete its red-test method** — don't edit it green. Remaining method count is the live progress meter.
- Local silence while working: `mvn -Dtest='!CurveAwarenessSpecTest' test`.

### GitHub layout

- This document is the suggested epic body — adapt as the maintainers see fit.
- *Optional* milestone-shaped issues per **phase** with checklists referencing TAGs — at most 8 of them, never 49.
- Day-to-day work via PRs that:
  - Reference this epic.
  - Use the TAG in commit subject + PR title.
  - Delete the corresponding red-test method when behaviour lands (its own commit, see conventions below).

### Commit / PR convention

```
bucket: TAG short description
```

Buckets: `fix:`, `feat:`, `arch:`, `test:`, `spec:`, `refactor:`. (User-facing docs go under `spec:`.)

Examples:
- `feat: BUF-1 analytical single-arc CircularString buffer → CurvePolygon`
- `arch: F-CP CurvePolygon stores CompoundCurve shell + holes`
- `test: drop CurveAwarenessSpecTest#test_BUF_1_*` — the dedicated commit that closes a TAG by removing its red-test method.

## 6. Cross-module impact

Most TAGs ship purely inside `jts-curved` (extension module, opt-in). A handful require touching `jts-core` and therefore need a maintainer review up-front. Calling them out before they're proposed:

| TAG group | jts-core change? | Notes |
|---|---|---|
| **F-RD** | Possibly | Only if `ShapeWriter` needs new extension hooks for `CurvePolygon` rings. |
| **N-AA, N-AL, N-SS** | **Yes** | `SegmentString` / `Noder` hierarchy lives in core. Largest core surface in the epic. |
| **OV** | Indirect | Depends on N-SS; overlay pipeline itself stays in core. |
| **R-PR, R-CONT** | Indirect | `RelateOp` lives in core; if N-SS is the seam, no further core change needed. |
| **PLG** | **Yes** | `Polygonizer` lives in core; needs to accept `CompoundCurve` edges. |
| **PRC-SN** | Yes | `PrecisionModel.makePrecise` integration. |
| **DSF** | Yes (or shadow) | `Densifier` lives in core; alternative is to wrap and shadow it from `jts-curved`. |

Everything else is jts-curved-only or `jts-app` (TestBuilder).

## 7. Risks / open questions

- **Backwards compatibility on structural composites.** Algorithms that don't recognise a curve subtype today silently densify. After **F-CP**, a third-party algorithm that doesn't know about the new structural CurvePolygon ring will see a `CompoundCurve` shell where it expected a `LinearRing`, and may throw. We need a fallback contract: the structural ring must implement enough of `LinearRing`'s contract to keep old code limping (read-only chord coords still available), *or* the structural CurvePolygon must fail fast with a clear message instead of silently going wrong.
- **`equalsExact` semantic change** (R-EQ). Today `CIRCULARSTRING(p0,p1,p2).equalsExact(LINESTRING(p0,p1,p2))` returns true via shared coordinates. Making it false is per spec, but it's a behaviour change for any user comparing-by-WKT-text. Needs a release note.
- **Non-similarity affine** (AT-NS). Sheared arcs are ellipse arcs, which JTS doesn't model. The plan is to detect-and-densify, but: what does `getGeometryType()` return on the result? If `LineString`, we've changed the type silently. If still flagged as `CircularString`, the polyline lies about its identity. Decide before AT-NS lands.
- **Performance of public arc-arc / arc-line intersection** (N-AA, N-AL). The two-circle solve is fine for low cardinality, but `MCIndexNoder`-equivalent indexing of arc spans (bounding-box pruning of arcs) is non-trivial. Worth a benchmark before committing to a design.
- **Z / M propagation across densified arcs.** Out of scope for behaviour changes here, but every TAG that produces densified output (DSF, AT-NS, …) should choose a Z/M policy that doesn't lock us out of a proper interpolation later.

## 8. Definition of Done (epic-level)

The epic closes when **all** of:

1. `CurveAwarenessSpecTest` is empty — every TAG's red test deleted, replaced by green tests next to its production code.
2. The 6 curve types round-trip through every public `Geometry` operation in the user guide without producing flat output where curve-preserving output is mathematically possible.
3. The deferred WKB sibling epic has its own tracking issue. (We don't have to ship it inside this epic, but we can't pretend it doesn't exist.)
4. A release note covers the `equalsExact` change (see §7) and any other user-visible behaviour shifts.

**TB-FN (function-tree curve-awareness badges) is a stretch goal**, not a DoD criterion. Annotating every entry in TestBuilder's function tree is a multi-week task with low payoff relative to the algorithm work, and shouldn't gate epic closure.

## 9. Phases — work breakdown

Phases group TAGs that share dependencies or naturally land together. Within a phase, TAGs are usually independently shippable. Cross-phase dependencies are noted explicitly per phase.

### Phase 1 — Foundations (jts-curved structural completeness)

Mirror the `CompoundCurve` work onto the remaining composite types.

- **F-CP** Structural `CurvePolygon` — `CompoundCurve` shell + holes; `copyInternal`, `toLinear`, WKT reader/writer preserve structure.
- **F-MC** Structural `MultiCurve` — preserves member subtypes (`LineString` / `CircularString` / `CompoundCurve`).
- **F-MS** Structural `MultiSurface` — preserves `Polygon` vs `CurvePolygon` member subtypes.
- **F-RD** `CurvedShapeWriter` for `CurvePolygon` rings, `MultiCurve` and `MultiSurface` members.

**Hard prereq for:** all of Phase 2 (you can't return a `CompoundCurve` boundary of a `CurvePolygon` whose ring isn't a `CompoundCurve` to begin with).

### Phase 2 — Properties (Metrics, Boundary, Validity)

- **M-LEN-CS / M-LEN-CC** — analytical arc length.
- **M-AREA-CP** — Green's-theorem area with circular-segment correction.
- **M-DIM** — empty-curve dimension/coordinate-dimension guards.
- **B-CP / B-MS / B-CC** — curved boundaries.
- **V-CP** — `CurvePolygon` validity (arc self-intersection, sector orientation, holes-in-shell).
- **V-CS** — `CircularString` / `CompoundCurve` simplicity.

**Depends on:** Phase 1 (F-CP is a hard prereq for B-CP, M-AREA-CP, V-CP).

### Phase 3 — Measurement (Distance, Centroid, Interior point)

- **D-PT / D-AA** — analytical point-arc and arc-arc distance.
- **D-OP** — `DistanceOp` accepts curved inputs without forced densification.
- **D-HF** — `DiscreteHausdorffDistance` / `DiscreteFrechetDistance` parameterise by arc length.
- **C-LIN / C-AREA / C-IP** — centroids and interior point.

**Depends on:** Phase 1 (F-CP for `CurvePolygon` cases).

### Phase 4 — Construction (Buffer, Hulls, Simplification, Affine, Linear-Ref, Densifier)

- **BUF-1 / BUF-N / BUF-NEG** — analytical buffer.
- **OFF / VBF** — offset curve and variable buffer.
- **H-CV / H-CC** — convex / concave hull.
- **S-DP / S-VW / S-TP** — simplification preserves arc identity.
- **AT-S / AT-NS** — affine transforms (similarity preserves; non-similarity densifies — see §7 risk).
- **LRF-LEN / LRF-LOC** — linear referencing parameterised by arc length.
- **DSF** — densifier delegates to `toLinear`.

**Depends on:** Phase 1 (output may be `CurvePolygon`).

### Phase 5 — Noding foundation

- **N-AA** — public arc-arc intersection utility.
- **N-AL** — public arc-line intersection utility.
- **N-SS** — arc-aware `SegmentString` / `NodedSegmentString` and integration with the `Noder` hierarchy.

**Depends on:** Phase 1. **Touches `jts-core`** — see §6.

### Phase 6 — Overlay, Predicates, Polygonizer, Coverage

- **OV** — arc-preserving overlay output (`union` / `intersection` / `difference` / `symDifference`).
- **R-PR / R-CONT / R-EQ** — arc-aware relate, predicates, exact equality.
- **PLG** — `Polygonizer` accepts `CompoundCurve` edges and emits `CurvePolygon` faces.
- **COV** — `CoverageUnion` preserves shared arc edges.

**Depends on:** Phase 5.

### Phase 7 — Independent tracks

Three single-theme tracks that depend only on Phase 1 and have no inter-dependencies; can land in parallel with Phases 2–6 once Phase 1 ships.

- **Snapping**
  - **PRC-SN** — snap-to-grid preserves arc when the snapped `(R, centre, sweep)` still lies on grid; otherwise densify-and-snap chords.
- **Triangulation / Voronoi**
  - **TRI-DT / TRI-VR** — `DelaunayTriangulationBuilder` / `VoronoiDiagramBuilder` accept curved boundary input (densify internally via `toLinear(tolerance)`).
- **TestBuilder**
  - **TB-T** — `CompoundCurveTool`, `CurvePolygonTool` drawing UX.
  - **TB-FN** — function-tree curve-awareness badges (●/◯/✕). *Stretch goal* — see §8.

## 10. Suggested order

```
Phase 1 (Foundations)
   │
   ├──> Phase 2 (Properties)
   ├──> Phase 3 (Measurement)
   ├──> Phase 4 (Construction)
   ├──> Phase 5 (Noding)  ──>  Phase 6 (Overlay / Predicates / Polygonizer)
   └──> Phase 7 (Independent tracks: snap / triangulation / TestBuilder)
```

After Phase 1 finishes, Phases 2 / 3 / 4 / 5 / 7 can run in parallel; Phase 6 waits for Phase 5.

## 11. Conventions

- TAGs are short, unique, and stable. Renaming a TAG renames its test method too.
- One TAG per PR (or a tightly-coupled cluster). The PR deletes the corresponding spec method.
- **CI stays green by default.** The spec class is excluded from the default Surefire run via:

  ```xml
  <plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
      <excludes>
        <exclude>**/spec/curveawareness/*.java</exclude>
      </excludes>
    </configuration>
  </plugin>
  ```

  Contributors run it explicitly with `mvn -pl modules/curved test -Dtest=CurveAwarenessSpecTest` (which overrides the exclude). The remaining-method count is still the live progress meter, but it doesn't break CI on every push.

## 12. References

- OGC Simple Feature Access 1.2.1 / ISO 19125-2.
- `jts-curved` source: `modules/curved/`.
- Spike branch: `feature/sfa-curve-buffer-spike` on `grootstebozewolf/jts`.
- `CurveAwarenessSpecTest` (live progress meter).
