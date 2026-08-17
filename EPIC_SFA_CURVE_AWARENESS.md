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

**Status:** Draft v6 MMF Option B (2026-08-17). Tip `47c0f33e` · [LASER_RATCHET.md](doc/LASER_RATCHET.md) · [CHAINSAW_LASER_PROGRAM.md](doc/CHAINSAW_LASER_PROGRAM.md) · [OVERLAYNGCURVE_P2_SEAMS.md](doc/OVERLAYNGCURVE_P2_SEAMS.md).
**Source:** Parent epic [locationtech/jts#1195](https://github.com/locationtech/jts/issues/1195). Fork SoT [grootstebozewolf/jts#7](https://github.com/grootstebozewolf/jts/pull/7) `feature/sfa-curve-rgr`. MMF draft [grootstebozewolf/jts#61](https://github.com/grootstebozewolf/jts/pull/61) `cursor/jts-issue-1195-c5d1` — Option B `SegmentKind`, no-silent-linearize strategy, WKB 18–21 greenfield, OFF/BUF/VBF/COV/H-CC/PLG + TB-FN badges. Slack still 15%. No upstream locationtech PR until dr-jts engages.
**Origin (historical):** [`feature/sfa-curve-buffer-spike`](https://github.com/grootstebozewolf/jts/tree/feature/sfa-curve-buffer-spike) — Draft v3 of this epic and the 49-method spec class. Draft v5 (2026-08-16) described #7 @ `210f1b16` with OV-P1 kits; Bar 2 stayed off #7 until this MMF fold.
**Audience:** locationtech/jts maintainers and contributors. Lift verbatim into a GitHub Epic / Discussion.

**Field contract (PERF-GATE):** Maintainable → Reliable → Faster. Take the curve path only if `t_laser ≤ 1.15 × t_chainsaw`. Do not loosen 15%. The overlay name is **OverlayNGCurve**, never *Curved*. **Noder = Option B** (`SegmentKind` LINEARIZED / ARC / CERTIFIED); index may lie under `PrecisionModel`. **No silent linearization** — `CurveLinearizationStrategy` default LINEARIZED always warns.

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

### 4.0 MMF Option B fold ([#61](https://github.com/grootstebozewolf/jts/pull/61), 17 Aug 2026)

Draft off #7. SoT stays the fork; no upstream PR.

| Bucket | Summary | TAG honesty |
|---|---|---|
| `arch:` | **Option B** `SegmentKind` (`LINEARIZED` / `ARC` / `CERTIFIED`) on core `SegmentString`; `CircularNodedSegmentString`; `IntersectionAdder.mayCollapseToChord`; OverlayNG prepared edges. Index may lie under `PrecisionModel`. | N-SS path is Option B, not the rejected 74-file Option B *lie* framing. |
| `arch:` | **`CurveLinearizationStrategy`**: default `LINEARIZED` always logs a warning; `PRESERVE` keeps type. Wired through `CurveOps.linearise`. | No silent flatten. |
| `feat:` | **WKB 18–21** SIGN greenfield: `CRV-CLOTHOID` / `PRF-BEZIER` / `PRF-ELLIPSE` / `CRV-NURBS` (+ ISO `+1000/+2000/+3000`). Factory stubs; `CurveWKBWriter`; core writer refuses flatten. 15–17 still Architect-gated. | I/O zoo partial–full for 18–21 types; ops still mostly chordsaw. |
| `feat:` | **OFF** shipped: public `OffsetCurve.getCurve` concentric single-arc (left-of-direction). | Full OFF TAG for 3-pt CS. Multi-arc still chordsaw. |
| `feat:` | **BUF-1 / BUF-NEG** shipped: open-arc corridor `CurvePolygon`; `|d|≥R` empty. **BUF-N** shipped: stadium dilation + open two-member line+arc corridor with round joins/caps. | Multi-arc (>2) CompoundCurve corridors still chainsaw. |
| `feat:` | **R-OV** OverlayNG-for-circles: H-SHELL-N-MIXED via `OverlayNGCircle` + `CurveSegmentString` bridge; BiteVsHole / TwoHoleOverlay folded. | Named R2 leftovers shrink; full OV still not green. |
| `feat:` | **HP.2 / HP.3** `CurveHotPixel` + `CurveHotPixelSnap` (PM-scale arc∩pixel; shared snapped ray stamp). | Not HP.4 faces; not core HotPixel rewrite. |
| `arch:` | **`CurveSegmentDcel`** (P2.5.7) package-private; **COV** `CurveCoverageUnion` dissolves shared shell members and keeps exterior `CIRCULARSTRING`s (hooked from `CoverageUnion`). **PLG** densifies on add (faces remain Polygon). | D-HF full TAG still red (apex closed-form exists). |
| `fix:` | **TB-FN #60**: Exec prefers `currentFunc`; param focus cannot re-bind to `Buffer.buffer`. **TB-FN badges** ●/◯/✕ via `Metadata.curveAwareness`. **Strategy picker** Edit menu LINEARIZED/PRESERVE; warn sink → Log tab. | Await UX SIGN on pin JAR. |
| `fix:` | **VBF honesty**: `VariableBuffer` + TestBuilder densify via equal-arc-length `CurveOps.lineariseArcLength` (warn). | Full arc-preserving variable offsets still optional laser. |
| `feat:` | **DSF / TRI / H-CC / LRF-LOC / C-IP / PRC-SN / V-CP / N-SS / R-PR** densify or Option B honesty paths shipped (see `CurveAwarenessGreenMetersTest`). | Meters deleted from red suite. |
| `docs:` | **`doc/latex/`** guide sources cherry-picked from #47 (Makefile + manuals + figures). Amyuni PDF binaries not force-swapped. | Phase 6 partial; `make` + UX SIGN still HOLD. |
| `test:` | **#56 locks**: `CurvePolygonToolTest` 16/16 — non-closing finish auto-closes to `CURVEPOLYGON`, Escape cancels with status, never silent empty / never `POLYGON` flatten. | HOLD issue close until PO UX SIGN. |

### 4.1 On #7 since the spike (historical tip `210f1b16`, still accurate for OV-P1 kits)

| Bucket | Summary | TAG honesty |
|---|---|---|
| `feat:` / `fix:` | **WKB 8–12.** Core `WKBReader` first-class cases: CircularString = count+coords; CompoundCurve / CurvePolygon / MultiCurve / MultiSurface = count + child WKBs (CurvePolygon rings carry their own type). `GeometryFactory.createCircularString` / `createCompoundCurve(LineString[])` / `createCurvePolygon(LineString, LineString[])` / `createMultiCurve` / `createMultiSurface` — default throws; `CurveGeometryFactory` implements. `new WKBReader(new CurveGeometryFactory()).read(type8)` returns `CircularString`. `CurveWKBReader` is the no-arg convenience. Locked XDR hex for `CIRCULARSTRING (0 0, 5 5, 10 0)` unchanged. Disc `CurvePolygon` area still 25π after round-trip. No `toLinear` on that path. | WKB sibling from Draft v3 — **landed on #7** (via #8). Not a new public I/O class beyond the factory surface + existing curve writer/reader convenience. |
| `feat:` | **OverlayNGCurve kits.** **R0** envelope-disjoint skip; **R1** retention when one envelope covers; **R1.5** `CircularDiscOverlay` two-disc closed form (CAP lens / CUP blob / SUB crescent / XOR crescents; crossing nodes `(3.5, ±√12.75)`); **R1.6** `CircularDiscPolygonOverlay` disc vs hole-free plain polygon (line–circle clip; half-disc area `12.5π`); **R1.7** CompoundCurve-shell kits (two-node, two-shell, collinear, 0/1-node, even-n, tangent-odd NSpan, same-outer hole, different-outer punch); **R-LL** CircularString / lineal CompoundCurve vs LineString; **R-AA** two CircularStrings (or lineal CompoundCurve vs CircularString) at exact nodes. Reverse `plain.op(curve)`, reverse SUB, MultiCurve/MultiSurface dispatch, nested Multi envelope skip. **D4** nested annulus: `CIRCLE_5 \ CIRCLE_3` is exact area `16π`, covers `EEEE` / coveredBy `EE0E`. **OV-P1 flipped.** Remaining named R2 on tip shrink after R-OV / hole kits on MMF; `H-ANNULUS-TANGENT` stays a named miss. Circular noder + arrangement is **OV-P2** under Option B. | **OV partial** (closed-form subset). Do **not** mark the *full* OV TAG green. Keep `test_OV_*`. |
| `feat:` | **R-CONT** certified disc PIP (interior + boundary band) in `CurveExact`. | **R-CONT partial**. |
| `feat:` | **R-PR DE-9IM** for disc vs Point, LineString, hole-free Polygon, and two discs. Point: interior `0F2FF1FF2`, boundary `FF20F1FF2`, exterior `FF2FF10F2`. Line: crossing `1F20F1102`, tangent `FF20F1102`, miss `FF2FF1102`, endpoint-interior `1020F1102`. Polygon: disjoint `FF2FF1212`, nested `212FF1FF2`, disc-in-square `2FF1FF212`, crossing `212101212`. Two discs: crossing `212101212`, disjoint `FF2FF1212`, nested `212FF1FF2` / `2FF1FF212`, ext tangent `FF2F01212`, int tangent `212F01FF2`, equal `2FFF1FFF2`. Finish slice: single-member MultiSurface both orders; `equalsTopo` on equal/crossing/rotated-control discs; full SFS table; `crosses` of two areas always false. Half-disc / CompoundCurve miss still null → linearise. | **R-PR partial**. Not “any combination of curved/flat”. Do not delete `test_R_PR_*` / `test_R_CONT_*`. |
| `feat:` | Distance closed-form helpers in `CurveExact` (arc-to-segment, overlapping discs `nearestPoints` 0, `decideTolerance` arc-aware). | Helpers only. **D-PT / D-AA / D-OP** still describe the public `DistanceOp` TAG — keep those spec methods red. |
| `feat:` | Disc / arc / CompoundCurve convex hull closed forms (`CurveConvexHull`). | **H-CV / H-CC partial**. |
| `feat:` | Public `DiscreteHausdorffDistance` on #7 (`0ca71b`) has closed-form for two pairs only: single-arc `CircularString` → single-segment `LineString` (apex √949/6 − 7/6 = 3.967640600249787), and two circular discs (10.0). A single-member `MultiSurface` of one disc is the same pair, not a third. Public DHD still sees chords in general. Exact path skips densify; densify 0.05 is not the laser. Keep the spec `fail()`. | **D-HF half-red**. Keep the spec method. **M.1 D-HF-DIR landed:** `DirectedHausdorffDistance` owns the same two Curve* pairs (see `doc/METRIC_KIT_MX.md`). |
| `feat:` | Typed LEC obstacle distance (point / segment / polygon / arc / disc) and certified disc closed form. Other obstacle sets keep the grid. | **LEC partial**. The public TAG is wider; do not describe LEC as a blank open. |
| `feat:` | Disc `CurvePolygon` area 25π. | **M-AREA-CP partial** (circular discs). Keep the spec method. |
| `arch:` | PERF-GATE: identity/chord-path rows use `assertChordPath`; slack stays **15%** (`1.15`). | Contract, not a TAG. |

These closed-form subsets of **OV** and **R-PR** did **not** wait for N-SS. Line–circle clip is used *inside* R1.6; R-LL / R-AA are overlay kits, not a public arc-arc / arc-line utility.

**Still open** (say so; do not close as “future work” a TAG that already has a named subset):

- Full **OV** TAG — OV-P1 + R-OV MIXED / hole kits; `H-ANNULUS-TANGENT` named miss; general arrangement still Option B unfinished.
- Public arc-arc / arc-line utilities (N-AA, N-AL) as standalone API
- Open mixed **BUF-N** corridors for >2 members; full **VBF** arc-length parameterisation
- Public `DiscreteHausdorffDistance` in general (D-HF); Fréchet
- Full LEC TAG
- RocqRefRunner SQL/MM suite for public Curve predicates
- `equalsExact` arc-vs-chord (R-EQ)
- PLG / COV (DCEL present; TAGs open)
- Most remaining Phase 2 / 4 / 7 meters in `CurveAwarenessSpecTest`
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

- **D-PT / D-AA / D-OP** — **Shipped (MMF #61):** analytical point-arc / arc-arc / DistanceOp green meters.
- **D-HF** — **Partial (half-red):** public DHD apex + disc closed forms; general pairs densify curve package inputs. Full TAG keep `fail()` in `CurveAwarenessSpecTest`.
- **C-LIN / C-AREA / C-IP** — **Shipped (MMF #61).**

**Depends on:** Phase 1 (F-CP for `CurvePolygon` cases).

### Phase 4 — Construction (Buffer, Hulls, Simplification, Affine, Linear-Ref, Densifier)

- **BUF-1 / BUF-N / BUF-NEG** — **Shipped (MMF #61):** open-arc corridor + stadium / N-member CompoundCurve.
- **OFF** — **Shipped (MMF #61):** concentric single-arc OffsetCurve.
- **VBF** — **Partial:** arc-length densify + warn shipped; arc-preserving offset laser still optional.
- **H-CV / H-CC** — **Shipped (MMF #61):** convex hull kits + ConcaveHull densify sites (hull fraction).
- **S-DP / S-VW / S-TP** — **Shipped (MMF #61):** 3-pt CircularString identity preserved.
- **AT-S / AT-NS** — **Shipped (MMF #61).**
- **LRF-LEN / LRF-LOC** — **Shipped (MMF #61).**
- **DSF** — **Shipped (MMF #61):** densifier → `toLinear`.

**Depends on:** Phase 1 (output may be `CurvePolygon`).

### Phase 5 — Noding foundation

- **N-AA / N-AL** — **Shipped (MMF #61):** `CurveIntersection` public utility.
- **N-SS** — **Partial (Option B):** `SegmentKind` + `CircularNodedSegmentString` + OverlayNG prepared edges shipped. Full hierarchy rewrite still OV-P2 / Bar 2 (different epic).

**Depends on:** Phase 1. **Touches `jts-core`** — see §6.

### Phase 6 — Overlay, Predicates, Polygonizer, Coverage

- **OV** — **Partial:** OverlayNGCurve kits + OverlayNGCircle MIXED + ClothoidOverlay 0-node. Full OV TAG not green.
- **R-PR / R-CONT / R-EQ** — **Partial→stronger (MMF #61):** disc DE-9IM + contains meters; arc-vs-chord `equalsExact` shipped earlier on #7.
- **PLG** — **Partial (MMF #61):** densify on add (faces remain Polygon; CurvePolygon faces laser open).
- **COV** — **Shipped (MMF #61):** `CurveCoverageUnion` keeps exterior CIRCULARSTRINGs.

**Depends on:** Phase 5 for *general* OV. **Phase 6 subsets already landed without a full circular noder.**

### Phase 7 — Independent tracks

Three single-theme tracks that depend only on Phase 1 and have no inter-dependencies; can land in parallel with Phases 2–6 once Phase 1 ships.

- **Snapping**
  - **PRC-SN** — **Shipped (MMF #61):** preserve CircularString when snapped centre on-grid.
- **Triangulation / Voronoi**
  - **TRI-DT / TRI-VR** — **Shipped (MMF #61):** densify curve sites.
- **TestBuilder**
  - **TB-T / TB-FN** — **Partial→stronger (MMF #61):** draw tools + badges + WarnSink/status; await UX SIGN #56/#60.

### Named misses still open (explicit list)

- **CLOTHOID-FRESNEL** — clothoid–circle / clothoid–line node (never chord-flatten).
- **D-HF full TAG** — general public Hausdorff beyond apex/disc closed forms.
- **VBF arc-offset laser** — preserve CircularString offsets under variable distance.
- **PLG CurvePolygon faces** — Polygonizer still emits Polygon.
- **HP.1 wrong-ring walk** — local curvature leave-angle order (pin; needs HotPixel).
- Remaining OverlayNGCurve R2 stamps as listed in §4.1 / ratchet.
- WKB **15–17** Architect-gated.
- RocqRefRunner SQL/MM suite for public Curve predicates (optional).
- Visual QA: await UX SIGN on #56 / #60 pin JAR.

Most Phase 2–7 *full* TAGs that still lack a closed-form or densify-honesty ship remain red in `CurveAwarenessSpecTest` (currently **D-HF** only). See [MMF_WALKTHROUGH.md](doc/MMF_WALKTHROUGH.md).

## 10. Suggested order

```
Phase 1 (Foundations) — done on #7 / MMF #61
   │
   ├──> Phase 2–4 / 7 TAGs — largely shipped on MMF #61 (see above)
   ├──> Phase 5 Option B SegmentKind — shipped; full noder hierarchy = OV-P2
   └──> Phase 6 subsets — OverlayNGCurve + COV + PLG densify on tip
```

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
- [#7](https://github.com/grootstebozewolf/jts/pull/7) `feature/sfa-curve-rgr` — SoT.
- MMF draft [#61](https://github.com/grootstebozewolf/jts/pull/61) `cursor/jts-issue-1195-c5d1` — Option B spine + WKB 18–21 + meter ship.
- OverlayNGCurve (package-private overlay helpers in `org.locationtech.jts.operation.overlayng.curve`).
- `CurveExact` (package-private in `org.locationtech.jts.geom.curve`).
- `doc/MMF_OPTION_B.md`, `doc/MMF_WALKTHROUGH.md`, `doc/latex/`.
- GEOS `src/io/WKBReader.cpp` / `WKBWriter.cpp` / `include/geos/io/WKBConstants.h` — type codes 8–12 and child-WKB layout.
- Spike branch (origin): `feature/sfa-curve-buffer-spike` on `grootstebozewolf/jts`.
- `CurveAwarenessSpecTest` — remaining full-TAG red: **D-HF** only (excluded from default Surefire). Live meter: `CurveAwarenessGreenMetersTest` + [MMF_WALKTHROUGH.md](doc/MMF_WALKTHROUGH.md).
