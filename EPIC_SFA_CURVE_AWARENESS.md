# Epic: SFA / ISO 19125-2 Curve Awareness in JTS

> **AI Disclosure** *(per the [Eclipse Foundation Generative AI Usage Guidelines for Committers](https://www.eclipse.org/projects/guidelines/genai/))*
>
> This document and the companion red-test class
> `modules/curve/src/test/java/org/locationtech/jts/spec/curveawareness/CurveAwarenessSpecTest.java`
> were largely AI-generated. The human contributor has reviewed and verified the
> technical content (cross-module impact, risk register, phase dependencies, TAG
> scope) for correctness. The AI-generated portions are made available under
> **CC0-1.0** (public domain dedication) and are *not* subject to the project's
> licence; human curation and edits are subject to the JTS dual licence.
>
> ```
> SPDX-License-Identifier: (EPL-2.0 OR EDL-1.0) AND CC0-1.0
> Assisted-by: xAI Grok (grok-4.3)
> Assisted-by: Claude (Opus-4.7)
> Assisted-by: Cursor Grok (grok-4.6)
> ```

**Status:** Draft v5 (2026-08-16).
**Source:** Parent epic [locationtech/jts#1195](https://github.com/locationtech/jts/issues/1195). Fork work: [grootstebozewolf/jts#7](https://github.com/grootstebozewolf/jts/pull/7) `feature/sfa-curve-rgr`, tip `210f1b16` (merge of [#11](https://github.com/grootstebozewolf/jts/pull/11)). [#8](https://github.com/grootstebozewolf/jts/pull/8) PERF-GATE, [#10](https://github.com/grootstebozewolf/jts/pull/10) nested annulus (`92fdbb71`), and [#11](https://github.com/grootstebozewolf/jts/pull/11) odd-n tangent NSpan (`d8c5824f`) are **merged into #7**. Draft [#12](https://github.com/grootstebozewolf/jts/pull/12) (`3cc5f1a5`) stamps `H-SHELL-HOLE-CROSS` + `H-SHELL-HOLE-X`; draft [#13](https://github.com/grootstebozewolf/jts/pull/13) (`73703036`) stamps `H-ANNULUS-TANGENT`. Both drafts are stacked, not merged. Slack still 15%. No new public API.
**Origin (historical):** [`feature/sfa-curve-buffer-spike`](https://github.com/grootstebozewolf/jts/tree/feature/sfa-curve-buffer-spike) — Draft v3 of this epic and the 49-method spec class. Draft v4 (2026-08-15, tip `488c48ba`) described #8 as the live stacked branch; that merge order is done.
**Audience:** locationtech/jts maintainers and contributors. Lift verbatim into a GitHub Epic / Discussion.

**Field contract (PERF-GATE):** Maintainable → Reliable → Faster. Take the curve path only if `t_laser ≤ 1.15 × t_chainsaw`. Do not loosen 15%. The overlay name is **OverlayNGCurve**, never *Curved*.

## 1. Goal

Make JTS preserve OGC SFA / ISO 19125-2 curve geometries — `CIRCULARSTRING`, `COMPOUNDCURVE`, `CURVEPOLYGON`, `MULTICURVE`, `MULTISURFACE`, `TRIANGLE` — through every algorithm where the math is sound, instead of silently linearising to flat parents on the way in.

Every circular pair that has a closed form has a kit. Everything else is a named miss and the chordsaw. We will not write a circular noder in this PR.

`jts-curve` is no longer only a parse-and-WKT stand-in: types exist, WKT and WKB (codes 8–12) round-trip without densify, and a closed-form kit set (overlay / relate / distance / hull / area / public DHD two-pair Hausdorff / LEC obstacle distance) beats the chord baseline on certified shapes. Public DHD still sees chords in general; Fréchet is still open. Overlay **OV-P1** has flipped: every circular pair with a closed form has a kit; remaining overlay work is named R2 misses, not a blank “CompoundCurve still open”. `jts-core` still densifies on contact for everything that is not one of those subsets. This epic tracks the lift, operation by operation — full TAG vs closed-form subset, not “good enough as linearised”. **Do not mark OV green** for the *full* TAG. Circular noder + arrangement is **OV-P2 / Bar 2** — a different epic; do not start it here.

## 2. Why

- **Performance.** Each densification step expands a typical arc to many chord points (≥50 for a half-circle at 1% chord-tolerance); pipelines that buffer → simplify → union compound the cost.
- **Precision.** Densify → operate → densify drifts control points by ULPs and breaks snap-to-grid round-trips against external SFA producers.
- **Interop.** PostGIS, Oracle Spatial, NetTopologySuite emit and consume curve geometries; WKB 8–12 now reconstructs the curve subclass when the factory can. Algorithms that still linearise remain the interop gap.
- **Discoverability.** TestBuilder cannot show a curved buffer or a curved boundary today; users of the spec types never see what their input was for.

## 3. Scope decisions

**In scope (this epic):**
- Algorithms operating on the six SFA curve types in 2-D.
- WKT round-trip with member structure (landed on #7).
- **WKB ISO/OGC type codes 8–12** (on #7 now; landed via #8). CircularString, CompoundCurve, CurvePolygon, MultiCurve, MultiSurface. The old “1000-series” wording was the ISO Z/M *dimension offset* (`type + 1000/2000/3000` for Z / M / ZM), not the curve type codes. Core `WKBReader` switches 8–12 the way GEOS does; construction is delegated to `GeometryFactory`. Writer: `CurveWKBWriter` emits 8–12 (hook before instanceof). Core `WKBWriter` does not emit 8–12 unless that hook/subclass is used.
- TestBuilder rendering and drawing tools.

**Deferred to sibling epics — explicitly tracked, not pretended-not-to-exist:**
- 3-D solids (`POLYHEDRALSURFACE`, `TIN`). Their structural / drawing support landed in the spike for visualisation; no 3-D semantics here.
- Elliptic arcs (`ELLIPSARC`) — JTS has no ellipse model; adding one is bigger than this epic.
- Z / M ordinate interpolation across densified arcs (today they propagate from the 3 control points unchanged; every TAG that produces densified output should leave room for a future interpolation policy).
- **OV-P2 / Bar 2** — circular noder + arrangement + general PLG / COV. Different epic. Do not start.

## 4. What's already landed

### 4.1 On #7 since the spike (tip `210f1b16`)

| Bucket | Summary | TAG honesty |
|---|---|---|
| `feat:` / `fix:` | **WKB 8–12.** Core `WKBReader` first-class cases: CircularString = count+coords; CompoundCurve / CurvePolygon / MultiCurve / MultiSurface = count + child WKBs (CurvePolygon rings carry their own type). `GeometryFactory.createCircularString` / `createCompoundCurve(LineString[])` / `createCurvePolygon(LineString, LineString[])` / `createMultiCurve` / `createMultiSurface` — default throws; `CurveGeometryFactory` implements. `new WKBReader(new CurveGeometryFactory()).read(type8)` returns `CircularString`. `CurveWKBReader` is the no-arg convenience. Locked XDR hex for `CIRCULARSTRING (0 0, 5 5, 10 0)` unchanged. Disc `CurvePolygon` area still 25π after round-trip. No `toLinear` on that path. | WKB sibling from Draft v3 — **landed on #7** (via #8). Not a new public I/O class beyond the factory surface + existing curve writer/reader convenience. |
| `feat:` | **OverlayNGCurve kits.** **R0** envelope-disjoint skip; **R1** retention when one envelope covers; **R1.5** `CircularDiscOverlay` two-disc closed form (CAP lens / CUP blob / SUB crescent / XOR crescents; crossing nodes `(3.5, ±√12.75)`); **R1.6** `CircularDiscPolygonOverlay` disc vs hole-free plain polygon (line–circle clip; half-disc area `12.5π`); **R1.7** CompoundCurve-shell kits (two-node, two-shell, collinear, 0/1-node, even-n, tangent-odd NSpan, same-outer hole, different-outer punch); **R-LL** CircularString / lineal CompoundCurve vs LineString; **R-AA** two CircularStrings (or lineal CompoundCurve vs CircularString) at exact nodes. Reverse `plain.op(curve)`, reverse SUB, MultiCurve/MultiSurface dispatch, nested Multi envelope skip. **D4** nested annulus: `CIRCLE_5 \ CIRCLE_3` is exact area `16π`, covers `EEEE` / coveredBy `EE0E`. **OV-P1 flipped.** Remaining named R2: `H-SHELL-N-MIXED`, `H-SHELL-HOLE-CROSS`, `H-SHELL-HOLE-X`, `H-ANNULUS-TANGENT`. Circular noder + arrangement is **OV-P2 / Bar 2** — different epic; do not start. | **OV partial** (closed-form subset). OV-P1 flipped; do **not** mark the *full* OV TAG green. Not a general circular noder. Keep `test_OV_*`. |
| `feat:` | **R-CONT** certified disc PIP (interior + boundary band) in `CurveExact`. | **R-CONT partial**. |
| `feat:` | **R-PR DE-9IM** for disc vs Point, LineString, hole-free Polygon, and two discs. Point: interior `0F2FF1FF2`, boundary `FF20F1FF2`, exterior `FF2FF10F2`. Line: crossing `1F20F1102`, tangent `FF20F1102`, miss `FF2FF1102`, endpoint-interior `1020F1102`. Polygon: disjoint `FF2FF1212`, nested `212FF1FF2`, disc-in-square `2FF1FF212`, crossing `212101212`. Two discs: crossing `212101212`, disjoint `FF2FF1212`, nested `212FF1FF2` / `2FF1FF212`, ext tangent `FF2F01212`, int tangent `212F01FF2`, equal `2FFF1FFF2`. Finish slice: single-member MultiSurface both orders; `equalsTopo` on equal/crossing/rotated-control discs; full SFS table; `crosses` of two areas always false. Half-disc / CompoundCurve miss still null → linearise. | **R-PR partial**. Not “any combination of curved/flat”. Do not delete `test_R_PR_*` / `test_R_CONT_*`. |
| `feat:` | Distance closed-form helpers in `CurveExact` (arc-to-segment, overlapping discs `nearestPoints` 0, `decideTolerance` arc-aware). | Helpers only. **D-PT / D-AA / D-OP** still describe the public `DistanceOp` TAG — keep those spec methods red. |
| `feat:` | Disc / arc convex hull closed forms. | **H-CV partial**. **H-CC** still open. |
| `feat:` | Public `DiscreteHausdorffDistance` on #7 (`0ca71b`) has closed-form for two pairs only: single-arc `CircularString` → single-segment `LineString` (apex √949/6 − 7/6 = 3.967640600249787), and two circular discs (10.0). A single-member `MultiSurface` of one disc is the same pair, not a third. Public DHD still sees chords in general. Exact path skips densify; densify 0.05 is not the laser. Keep the spec `fail()`. | **D-HF half-red**. Keep the spec method. |
| `feat:` | Typed LEC obstacle distance (point / segment / polygon / arc / disc) and certified disc closed form. Other obstacle sets keep the grid. | **LEC partial**. The public TAG is wider; do not describe LEC as a blank open. |
| `feat:` | Disc `CurvePolygon` area 25π. | **M-AREA-CP partial** (circular discs). Keep the spec method. |
| `arch:` | PERF-GATE: identity/chord-path rows use `assertChordPath`; slack stays **15%** (`1.15`). | Contract, not a TAG. |

These closed-form subsets of **OV** and **R-PR** did **not** wait for N-SS. Line–circle clip is used *inside* R1.6; R-LL / R-AA are overlay kits, not a public arc-arc / arc-line utility and not an arc `SegmentString`.

**Still open** (say so, do not close as “future work” a TAG that already has a named subset):

- Full **OV** TAG — OV-P1 flipped; remaining named R2 are `H-SHELL-N-MIXED`, `H-SHELL-HOLE-CROSS`, `H-SHELL-HOLE-X`, `H-ANNULUS-TANGENT`. Circular noder + arrangement is OV-P2 / Bar 2 (different epic).
- Public arc-arc / arc-line utilities and arc `SegmentString` (N-AA, N-AL, N-SS)
- Open-arc buffer, CompoundCurve hull
- Public `DiscreteHausdorffDistance` (D-HF) — still sees chords in general. On #7 via `0ca71b` closed-form for two pairs only: single-arc `CircularString` → single-segment `LineString` (apex √949/6 − 7/6 = 3.967640600249787), and two circular discs (10.0). A single-member `MultiSurface` of one disc is the same pair, not a third.
- Full LEC TAG (typed obstacle distance landed; public TAG is wider)
- Fréchet (still open; not a D-HF stand-in)
- OFF = concentric arc (refactor)
- RocqRefRunner SQL/MM suite for public Curve predicates
- TestBuilder inspector `ClassCastException` (visual QA)
- `equalsExact` arc-vs-chord (R-EQ)
- Most of Phase 2 / 4 / 7 TAGs

### 4.2 Historical — `feature/sfa-curve-buffer-spike` (Draft v3 origin)

| Bucket | Summary |
|---|---|
| `arch:` | Structural `CompoundCurve` — segment-aware `copy` / `toLinear` |
| `arch:` | Member-structured WKT round-trip for `CompoundCurve` |
| `arch:` | `CurveShapeWriter` walks `CompoundCurve` members, arc-renders |
| `feat:` | `LineHandlingFunctions.mergeCurves` (arc-aware sibling of `mergeLines`) |
| `fix:`  | Anchor member control points in `CompoundCurve.toLinear` (no junction drift) |
| `fix:`  | Block-comment paste regression in WKT panel |
| `spec:` | This epic + 49-method `CurveAwarenessSpecTest` red-test suite |
| (orig)  | `bufferCurveWithParams` linearisation hook (Phase-5 spike entry) |

#7 carried the SFA/SQL-MM types, WKT, and structural-type work forward from that spike. #8 stacked the PERF-GATE and the first lasers, then merged into #7. #10 and #11 merged on top. Drafts #12 and #13 remain stacked.

## 5. Tracking model

We are **not** opening 49 separate issues. Fragmenting the project board, spamming notifications, and forcing every reviewer to reconstruct the dependency graph is the wrong shape for work this large.

### Full-TAG red list: `CurveAwarenessSpecTest`

- Path: `modules/curve/src/test/java/org/locationtech/jts/spec/curveawareness/CurveAwarenessSpecTest.java`
- Run: `mvn -pl modules/curve test -Dtest=CurveAwarenessSpecTest`
- Still has **all 49 `fail()` methods**. That “delete the method when a TAG ships” meter **froze** when work moved to stacked PRs and closed-form lasers.
- The class is the **full-TAG red list**, excluded from default Surefire. It is **not** the live scoreboard. Do **not** delete a method because a closed-form subset landed (OV-P1 kits, R-PR disc cells, disc area, public DHD two pairs, …). Delete only when the *full* TAG ships.
- Local silence while working: `mvn -Dtest='!CurveAwarenessSpecTest' test`.

### Live progress meter

Remaining **full** TAGs (still red in the spec class) plus the **green tests next to production code on #7** and the Notion board [JTS curve laser — progress](https://www.notion.so/JTS-curve-laser-progress-3bd1c9833b068135bd74e817b67e77eb). A maintainer reading this epic should look at OverlayNGCurve / `CurveExact` / WKB tests on #7 and that page, not at “49 methods remaining”.

### GitHub layout

- This document is the suggested epic body — adapt as the maintainers see fit.
- *Optional* milestone-shaped issues per **phase** with checklists referencing TAGs — at most 8 of them, never 49.
- Day-to-day work via PRs that:
  - Reference this epic.
  - Use the TAG in commit subject + PR title.
  - Delete the corresponding red-test method only when the **full** TAG lands (its own commit, see conventions below). Partial lasers keep the spec method.

### Commit / PR convention

```
bucket: TAG short description
```

Buckets: `fix:`, `feat:`, `arch:`, `test:`, `spec:`, `refactor:`. (User-facing docs go under `spec:`.)

Examples:
- `feat: BUF-1 analytical single-arc CircularString buffer → CurvePolygon`
- `arch: F-CP CurvePolygon stores CompoundCurve shell + holes`
- `test: drop CurveAwarenessSpecTest#test_BUF_1_*` — the dedicated commit that closes a **full** TAG by removing its red-test method.

## 6. Cross-module impact

Most TAGs ship purely inside `jts-curve` (extension module, opt-in). A handful require touching `jts-core` and therefore need a maintainer review up-front. Calling them out before they're proposed:

| TAG group | jts-core change? | Notes |
|---|---|---|
| **WKB 8–12** | **Yes (on #7; landed via #8)** | `WKBReader` switch, `WKBConstants` 8–12, `GeometryFactory` `create*` stubs (default throw). `CurveGeometryFactory` / `CurveWKBWriter` / `CurveWKBReader` stay in `jts-curve`. |
| **F-RD** | Possibly | Only if `ShapeWriter` needs new extension hooks for `CurvePolygon` rings. |
| **N-AA, N-AL, N-SS** | **Yes** | `SegmentString` / `Noder` hierarchy lives in core. **Largest remaining core surface** in the epic. OV-P2 / Bar 2 — different epic; do not start from this PR. |
| **OV** | Indirect | *General* overlay still depends on N-SS; the pipeline itself stays in core. Closed-form kits (OverlayNGCurve R0–R1.7, R-LL, R-AA, nested annulus) shipped in `jts-curve` without a public noder. OV-P1 flipped. |
| **R-PR, R-CONT** | Indirect | `RelateOp` lives in core. Disc cells shipped via `CurveExact` in `jts-curve`. General “any curved/flat pair” still wants N-SS. |
| **D-HF** | Two pairs only | Public `DiscreteHausdorffDistance` still sees chords in general. On #7 via `0ca71b` closed-form for two pairs only: single-arc `CircularString` → single-segment `LineString` (apex √949/6 − 7/6 = 3.967640600249787), and two circular discs (10.0). A single-member `MultiSurface` of one disc is the same pair, not a third. Exact path skips densify; densify 0.05 is not the laser. |
| **LEC** | Yes (partial) | Typed obstacle distance (point / segment / polygon / arc / disc) and certified disc closed form. Public TAG is wider. |
| **PLG** | **Yes** | `Polygonizer` lives in core; needs to accept `CompoundCurve` edges. |
| **PRC-SN** | Yes | `PrecisionModel.makePrecise` integration. |
| **DSF** | Yes (or shadow) | `Densifier` lives in core; alternative is to wrap and shadow it from `jts-curve`. |

Everything else is jts-curve-only or `jts-app` (TestBuilder).

## 7. Risks / open questions

- **Backwards compatibility on structural composites.** Algorithms that don't recognise a curve subtype today silently densify. After **F-CP**, a third-party algorithm that doesn't know about the new structural CurvePolygon ring will see a `CompoundCurve` shell where it expected a `LinearRing`, and may throw. We need a fallback contract: the structural ring must implement enough of `LinearRing`'s contract to keep old code limping (read-only chord coords still available), *or* the structural CurvePolygon must fail fast with a clear message instead of silently going wrong.
- **`equalsExact` semantic change** (R-EQ). Today `CIRCULARSTRING(p0,p1,p2).equalsExact(LINESTRING(p0,p1,p2))` returns true via shared coordinates. Making it false is per spec, but it's a behaviour change for any user comparing-by-WKT-text. Needs a release note. Still open.
- **Non-similarity affine** (AT-NS). Sheared arcs are ellipse arcs, which JTS doesn't model. The plan is to detect-and-densify, but: what does `getGeometryType()` return on the result? If `LineString`, we've changed the type silently. If still flagged as `CircularString`, the polyline lies about its identity. Decide before AT-NS lands.
- **Performance of public arc-arc / arc-line intersection** (N-AA, N-AL). The two-circle solve is fine for low cardinality, but `MCIndexNoder`-equivalent indexing of arc spans (bounding-box pruning of arcs) is non-trivial. Worth a benchmark before committing to a design. R1.6 uses line–circle clip internally; R-LL / R-AA are overlay kits. None of those is the public utility. Do not start a circular noder from this PR.
- **Z / M propagation across densified arcs.** Out of scope for behaviour changes here, but every TAG that produces densified output (DSF, AT-NS, …) should choose a Z/M policy that doesn't lock us out of a proper interpolation later.

## 8. Definition of Done (epic-level)

The epic closes when **all** of:

1. `CurveAwarenessSpecTest` is empty — every TAG's red test deleted, replaced by green tests next to its production code.
2. The 6 curve types round-trip through every public `Geometry` operation in the user guide without producing flat output where curve-preserving output is mathematically possible.
3. **WKB curve types** — satisfied on #7 (landed via #8). ISO/OGC codes 8–12 read through core `WKBReader` + a curve-capable factory; `CurveWKBWriter` emits them. The Draft v3 “open a WKB sibling issue” action is done by that work, not by a separate issue. (3-D / ELLIPSARC / Z-M interpolation remain deferred as in §3.)
4. A release note covers the `equalsExact` change (see §7) and any other user-visible behaviour shifts.

**TB-FN (function-tree curve-awareness badges) is a stretch goal**, not a DoD criterion. Annotating every entry in TestBuilder's function tree is a multi-week task with low payoff relative to the algorithm work, and shouldn't gate epic closure.

**OV-P1 is not epic DoD.** The full OV TAG stays red until a general circular noder and arrangement exist; that is OV-P2 / Bar 2, a different epic.

## 9. Phases — work breakdown

Phases group TAGs that share dependencies or naturally land together. Within a phase, TAGs are usually independently shippable. Cross-phase dependencies are noted explicitly per phase.

**Closed-form subsets of Phase 6 (and pieces of Phases 2–4) already landed on #7 without Phase 5.** That does not turn those TAGs green. Annotate **partial** and name the subset. General overlay / polygonizer / coverage / `equalsExact` still wait on Phase 5. OV-P1 flipped; remaining named R2 are listed in §4.1.

### Phase 1 — Foundations (jts-curve structural completeness)

Mirror the `CompoundCurve` work onto the remaining composite types.

- **F-CP** Structural `CurvePolygon` — `CompoundCurve` shell + holes; `copyInternal`, `toLinear`, WKT reader/writer preserve structure.
- **F-MC** Structural `MultiCurve` — preserves member subtypes (`LineString` / `CircularString` / `CompoundCurve`).
- **F-MS** Structural `MultiSurface` — preserves `Polygon` vs `CurvePolygon` member subtypes.
- **F-RD** `CurveShapeWriter` for `CurvePolygon` rings, `MultiCurve` and `MultiSurface` members.

**Hard prereq for:** all of Phase 2 (you can't return a `CompoundCurve` boundary of a `CurvePolygon` whose ring isn't a `CompoundCurve` to begin with).

### Phase 2 — Properties (Metrics, Boundary, Validity)

- **M-LEN-CS / M-LEN-CC** — analytical arc length.
- **M-AREA-CP** — Green's-theorem area with circular-segment correction. **Partial (circular discs):** disc `CurvePolygon` area is 25π. Keep the spec method.
- **M-DIM** — empty-curve dimension/coordinate-dimension guards.
- **B-CP / B-MS / B-CC** — curved boundaries.
- **V-CP** — `CurvePolygon` validity (arc self-intersection, sector orientation, holes-in-shell).
- **V-CS** — `CircularString` / `CompoundCurve` simplicity.

**Depends on:** Phase 1 (F-CP is a hard prereq for B-CP, M-AREA-CP, V-CP).

### Phase 3 — Measurement (Distance, Centroid, Interior point)

- **D-PT / D-AA** — analytical point-arc and arc-arc distance. Closed-form helpers exist in `CurveExact`; the public `DistanceOp` TAG is still red.
- **D-OP** — `DistanceOp` accepts curved inputs without forced densification. Still the public TAG; keep the spec method.
- **D-HF** — **Partial (half-red):** public DHD still sees chords in general. On #7 via `0ca71b` closed-form for two pairs only: single-arc `CircularString` → single-segment `LineString` (apex √949/6 − 7/6 = 3.967640600249787), and two circular discs (10.0). A single-member `MultiSurface` of one disc is the same pair, not a third. Exact path skips densify; densify 0.05 is not the laser. Keep the spec `fail()`.
- **C-LIN / C-AREA / C-IP** — centroids and interior point.

**Depends on:** Phase 1 (F-CP for `CurvePolygon` cases).

### Phase 4 — Construction (Buffer, Hulls, Simplification, Affine, Linear-Ref, Densifier)

- **BUF-1 / BUF-N / BUF-NEG** — analytical buffer. Open-arc buffer still open.
- **OFF / VBF** — offset curve and variable buffer. **OFF** = concentric arc (refactor) still open.
- **H-CV / H-CC** — convex / concave hull. **H-CV partial:** disc / arc convex hull closed forms exist. **H-CC** still open. CompoundCurve hull still open.
- **S-DP / S-VW / S-TP** — simplification preserves arc identity.
- **AT-S / AT-NS** — affine transforms (similarity preserves; non-similarity densifies — see §7 risk).
- **LRF-LEN / LRF-LOC** — linear referencing parameterised by arc length.
- **DSF** — densifier delegates to `toLinear`.

**Depends on:** Phase 1 (output may be `CurvePolygon`).

### Phase 5 — Noding foundation

- **N-AA** — public arc-arc intersection utility. **Still red.**
- **N-AL** — public arc-line intersection utility. **Still red.** Line–circle clip is used inside R1.6; R-LL / R-AA are overlay kits; those are not this TAG.
- **N-SS** — arc-aware `SegmentString` / `NodedSegmentString` and integration with the `Noder` hierarchy. **Still red.** OV-P2 / Bar 2 — different epic; do not start from this PR.

**Depends on:** Phase 1. **Touches `jts-core`** — see §6. Phase 6 *general* overlay still depends on N-SS.

### Phase 6 — Overlay, Predicates, Polygonizer, Coverage

- **OV** — arc-preserving overlay output (`union` / `intersection` / `difference` / `symDifference`). **Partial (closed-form subset):** OverlayNGCurve R0 / R1 / R1.5 two-disc / R1.6 disc vs hole-free plain polygon / R1.7 CompoundCurve-shell kits (two-node, two-shell, collinear, 0/1-node, even-n, tangent-odd NSpan, same-outer hole, different-outer punch) / R-LL / R-AA / D4 nested annulus (`16π`, covers `EEEE` / coveredBy `EE0E`). **OV-P1 flipped.** Remaining named R2: `H-SHELL-N-MIXED`, `H-SHELL-HOLE-CROSS`, `H-SHELL-HOLE-X`, `H-ANNULUS-TANGENT`. **Not** a general circular noder. Circular noder + arrangement is OV-P2 / Bar 2 — different epic; do not start. Do not mark the *full* OV TAG green.
- **R-PR / R-CONT / R-EQ** — arc-aware relate, predicates, exact equality. **R-PR / R-CONT partial:** certified disc PIP; DE-9IM for disc vs Point / LineString / hole-free Polygon / second disc; MultiSurface unwrap; SFS table; two areas never `crosses`. Half-disc / CompoundCurve still null → linearise. **R-EQ** (arc-vs-chord `equalsExact`) still open.
- **PLG** — `Polygonizer` accepts `CompoundCurve` edges and emits `CurvePolygon` faces.
- **COV** — `CoverageUnion` preserves shared arc edges.

**Depends on:** Phase 5 for *general* OV / PLG / COV / R-EQ. **Phase 6 subsets already landed without N-SS** — see §4.1 and §10.

### Phase 7 — Independent tracks

Three single-theme tracks that depend only on Phase 1 and have no inter-dependencies; can land in parallel with Phases 2–6 once Phase 1 ships.

- **Snapping**
  - **PRC-SN** — snap-to-grid preserves arc when the snapped `(R, centre, sweep)` still lies on grid; otherwise densify-and-snap chords.
- **Triangulation / Voronoi**
  - **TRI-DT / TRI-VR** — `DelaunayTriangulationBuilder` / `VoronoiDiagramBuilder` accept curved boundary input (densify internally via `toLinear(tolerance)`).
- **TestBuilder**
  - **TB-T** — `CompoundCurveTool`, `CurvePolygonTool` drawing UX.
  - **TB-FN** — function-tree curve-awareness badges (●/◯/✕). *Stretch goal* — see §8.
  - Visual QA still open: TestBuilder inspector `ClassCastException`.

Most of Phase 2 / 4 / 7 TAGs are still the full red list. Also still open (not a phase of their own): RocqRefRunner SQL/MM suite for public Curve predicates. **LEC** is partial (typed obstacle distance for point / segment / polygon / arc / disc); the public TAG is wider.

## 10. Suggested order

```
Phase 1 (Foundations)
   │
   ├──> Phase 2 (Properties)            M-AREA-CP partial: circular discs
   ├──> Phase 3 (Measurement)           CurveExact helpers; D-HF half-red
                                        (public DHD two pairs via 0ca71b; still sees chords; Fréchet open)
   ├──> Phase 4 (Construction)          H-CV partial: disc / arc hulls
   ├──> Phase 5 (Noding)  ──>  Phase 6 *general* OV / PLG / COV / R-EQ
   │                            (OV-P2 / Bar 2 — different epic)
   │
   └──> Phase 6 *subsets* (already on #7, did not wait for N-SS)
            OverlayNGCurve R0 / R1 / R1.5 / R1.6 / R1.7 + R-LL / R-AA
            D4 nested annulus; OV-P1 flipped
            remaining named R2: MIXED / HOLE-CROSS / HOLE-X / H-ANNULUS-TANGENT
            R-PR / R-CONT closed-form disc cells
   └──> Phase 7 (Independent tracks: snap / triangulation / TestBuilder)
```

After Phase 1 finishes, Phases 2 / 3 / 4 / 5 / 7 can run in parallel. **Phase 6 general overlay still waits for Phase 5.** Phase 6 *subsets* (two-disc overlay, disc-vs-polygon overlay, CompoundCurve-shell kits, lineal R-LL / R-AA, nested annulus, disc DE-9IM) already shipped on #7 without a public noder. The phase graph must not say “overlay requires a noder” as if those subsets were blocked.

## 11. Conventions

- TAGs are short, unique, and stable. Renaming a TAG renames its test method too.
- One TAG per PR (or a tightly-coupled cluster). The PR deletes the corresponding spec method only when the **full** TAG lands. Partial lasers keep the method.
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

  Contributors run it explicitly with `mvn -pl modules/curve test -Dtest=CurveAwarenessSpecTest` (which overrides the exclude). The remaining-method count is the **full-TAG red list**, not the live progress meter (see §5). It does not break CI on every push.

## 12. References

- OGC Simple Feature Access 1.2.1 / ISO 19125-2.
- `jts-curve` source: `modules/curve/`.
- Parent epic: [locationtech/jts#1195](https://github.com/locationtech/jts/issues/1195).
- [#7](https://github.com/grootstebozewolf/jts/pull/7) `feature/sfa-curve-rgr` — live tip `210f1b16` (merge of #11). SFA/SQL-MM types, WKT, structural types, PERF-GATE, OverlayNGCurve kits, `CurveExact`, WKB 8–12.
- [#8](https://github.com/grootstebozewolf/jts/pull/8) `cursor/curve-perf-gate-45a0` — **merged into #7**. PERF-GATE and the first closed-form lasers, including WKB 8–12.
- [#10](https://github.com/grootstebozewolf/jts/pull/10) nested annulus (`92fdbb71`) — **merged into #7**.
- [#11](https://github.com/grootstebozewolf/jts/pull/11) odd-n tangent NSpan (`d8c5824f`) — **merged into #7**.
- Draft [#12](https://github.com/grootstebozewolf/jts/pull/12) (`3cc5f1a5`) — stamps `H-SHELL-HOLE-CROSS` + `H-SHELL-HOLE-X`. Stacked, not merged.
- Draft [#13](https://github.com/grootstebozewolf/jts/pull/13) (`73703036`) — stamps `H-ANNULUS-TANGENT`. Stacked, not merged.
- OverlayNGCurve (package-private overlay helpers in `org.locationtech.jts.operation.overlayng.curve`).
- `CurveExact` (package-private in `org.locationtech.jts.geom.curve`).
- GEOS `src/io/WKBReader.cpp` / `WKBWriter.cpp` / `include/geos/io/WKBConstants.h` — type codes 8–12 and child-WKB layout.
- Spike branch (origin): `feature/sfa-curve-buffer-spike` on `grootstebozewolf/jts`.
- `CurveAwarenessSpecTest` — full-TAG red list (49 `fail()` methods still present; excluded from default Surefire). Live meter: #7 green tests + [JTS curve laser — progress](https://www.notion.so/JTS-curve-laser-progress-3bd1c9833b068135bd74e817b67e77eb).
