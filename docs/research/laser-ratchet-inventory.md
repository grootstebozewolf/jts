# Laser ratchet inventory

Part of #105 (wayfinder research). Inventory only — does **not** pick which types or operations a release bar must require.

**SoT scanned:** `origin/feature/sfa-curve-rgr` @ `b7394fd4ae6ec3b2e65f1c65d5872456d674f865` (fetched 2026-08-22).  
**Doc pin inside `doc/LASER_RATCHET.md`:** tip `c956b50d` (stale vs current branch tip).  
**Scoreboard pin:** originally user board @ `cf6e2b58` (commit `d62f87263`); counts unchanged through Year-1 lock (`6b1dbac1a`).

---

## 1. What “the laser ratchet” currently measures

Two committed artefacts share the word **ratchet**. They measure different things.

### 1.1 Performance contract (`t_laser ≤ 1.15 × t_chainsaw`)

This is the PERF-GATE. Same 15% slack everywhere it is named.

| Source | SHA / blob | Wording |
|--------|------------|---------|
| `doc/EXACT_CURVE_BIBLE.md` §2 item 6 | blob `9de6a6d8b15906c03faa7bd46550f6f7bc9716c2` | “Performance is a ratchet, not a race. Exact implementations must stay ≤ 1.15× the cost of densifying the same geometry. The ratchet is measured **per curve type**.” |
| `doc/EXACT_CURVE_BIBLE.md` §7 | same blob | ExactCircularArc must remain the fastest Exact* member. Other types may be slower; the 1.15× bar is vs densification of **the same type**. |
| `doc/LASER_RATCHET.md` | blob `f432ed0b6a63ad893f181450561e17dc08e6485c` | `t_laser ≤ 1.15 × t_chainsaw` · OverlayNGCurve never *Curved* · Draft v6 MMF Option B |
| `EPIC_SFA_CURVE_AWARENESS.md` field contract | blob `7268d64aa4a8815b724146fc4e69ff9f0caf1e1d` | Take the curve path only if `t_laser ≤ 1.15 × t_chainsaw`. Do not loosen 15%. Identity/chord-path rows use `assertChordPath`. |

**What is timed (laser):** the public curve-aware call (OverlayNGCurve, Geometry predicates, CurveOps constructions, public DHD/Fréchet/LEC/MIC, CurveWKB write+read, ExactCircularArc.length, OrientableSegment.orientationIndex).

**What is timed (chainsaw), by cell family:**

| Cell family | Chainsaw |
|-------------|----------|
| Overlay / predicates / most distance / hull / buffer | `CurveOps.linearise` at `CurveOps.TOLERANCE_FRACTION`, then the equivalent **core** algorithm |
| Discrete Hausdorff | linearise, then the **same** `DiscreteHausdorffDistance` class |
| Directed Hausdorff | linearise, then the same `DirectedHausdorffDistance` class |
| Discrete Fréchet | **not** linearise: `getCoordinates()` cloned as LineStrings (control-point discrete path) |
| LEC certified disc / ring | **n=4 control-point n-gon**, not full densify; other LEC rows vs `toLinear` |
| WKB | densify-then-core `WKBWriter`/`WKBReader` |
| ExactCircularArc P1 | densify polyline length (nChord=64) vs closed-form `length()` |
| OrientableSegment P1 | densify reference vs `orientationIndex` (p50 of 50k calls) |

**Harness (all `*PerfGateTest` classes):** not JMH. Homegrown `System.nanoTime()`, 15 warmups, 31 samples, **median**. Fail if `median(laser) / median(chainsaw) > 1.15`. A 0 ns chainsaw median is treated as timer resolution and skipped. Rows that **are** the chord path call `assertChordPath` and **skip the ratio** (keep the pair in the suite).

**JMH:** none on this branch. `git grep` of `org.openjdk.jmh` / `@Benchmark` on `origin/feature/sfa-curve-rgr` is empty.

### 1.2 Overlay exactness matrix (same name, not a timer)

`OverlayNGCurveRatchetTest` (blob `243a12d5956d38d307c62f9d940f6571a043af4b`) locks **which overlay cell is exact vs approximate** (`isApproximate()`), not wall time. Expected row encoding: `E` exact non-empty, `0` exact empty, `a` approximate. CAP/CUP/SUB/XOR on a two-disc matrix plus CompoundCurve-shell / CircularString kits.

These two ratchets are independent: an exact overlay cell can still fail the 1.15× gate, and a chord-path row can be exact-by-densify.

---

## 2. Curve types currently in the gates

Bible Year-1 lock (`doc/EXACT_CURVE_BIBLE.md` §3 / §5): **ExactCircularArc only**. Zoo siblings (ExactQuadraticBezier, ExactEllipticalArc, ExactClothoid, ExactNurbsSegment) are Year 2 and are **not** in any PerfGate / Ratchet class.

SFA types that **do** appear as operands in PERF-GATE / ratchet tests:

| Type | Shapes used | Notes |
|------|-------------|--------|
| `CIRCULARSTRING` | 3-pt open arc; same-circle overlapping arcs; full 5-pt ring | Certified single-arc / ring |
| `CURVEPOLYGON` of `CIRCULARSTRING` | certified discs r=5 / r=3 / far / crossing / ext-tangent / 3-4-5 kiss; empty | Dominant overlay + relate fixture |
| `COMPOUNDCURVE` (lineal) | line+arc | LEC, convexHull |
| `CURVEPOLYGON` of `COMPOUNDCURVE` | half-disc shells; collinear / nested / hanging halves; stadium four-cut; holed half-disc | Overlay kits R1.7; MIC stadium |
| `MULTISURFACE` of discs | one-member and two-disc | relate reverse; LEC two discs |
| Chord-shell `CURVEPOLYGON` (3-pt LINESTRING members) | not an arc | `assertChordPath` / exactness `aaaa` |
| Partners | `POINT`, `LINESTRING`, `POLYGON`, empty | not curve types |

**Absent from every `*PerfGateTest` and `OverlayNGCurveRatchetTest`:** `CLOTHOID` / WKB 18, Bézier, ellipse, NURBS, `MULTICURVE`, `TRIANGLE`, Exact* zoo types.

Bible §2.6 says measure **per curve type**. The committed scoreboard (`doc/LASER_RATCHET.md`) is **not** a per-type table. Map #103 already lists reconciling that scoreboard with the bible as unspecified.

---

## 3. Operations currently gated

Counts are `public void test*` methods @ `b7394fd4`.

### 3.1 `OverlayNGCurvePerfGateTest` — 79 methods (blob `d4ab621bc2579306c94fe4c8caf56215679e2549`)

Historical re-verify @ `d62f87263`: **79/79**. Still 79 methods on tip.

**Overlay (CAP/CUP/SUB/XOR) on discs / halves / arcs:**

- Disc algebra: self CAP, empty CUP, disjoint CAP, nested CAP/CUP/SUB/XOR, crossing CAP/CUP/SUB/XOR, reverse disjoint SUB, reverse nested SUB (**chord-path**), reverse crossing SUB
- Disc ∩ rectangle CAP; reverse disc ∩ rectangle SUB
- Half-disc ∩ disc CAP; half-disc ∩ square CAP
- Chord-shell overlay (**chord-path**); 3-pt LINESTRING vs line (**chord-path**); 3-pt LINESTRING vs CircularString (**chord-path**)
- Arc ∩ line CAP; arc ∩/∪/\ arc; same-circle ∩/∪/\ 
- Complementary halves, overlapping halves, two-shell lens, collinear halves, nested halves, one-node touch
- Four-cut two-shell CAP/CUP; same-outer hole CAP/CUP/SUB; different-outer hole nested CAP/CUP; different-outer hole lens CAP; four-cut disc ∩ band CAP/CUP

**Predicates / distance / hull (mostly certified disc vs point/line/poly/disc):**

contains, covers, within (reverse), relate, intersects, disjoint, overlaps, touches (R.1 T-ext), equalsTopo, crosses, MultiSurface relate, `distance`, `isWithinDistance`, `convexHull`

Chord-path overlay rows (ratio skipped): `rev nested SUB`, `3-pt LINESTRING shell`, `3-pt LINESTRING vs line`, `3-pt LINESTRING vs CircularString`.

### 3.2 `OverlayNGCurveRatchetTest` — 43 methods (exactness, not 1.15×)

Committed matrix (javadoc table): self `EE00`; empty partner `0EEE` / `0E0E`; disjoint `0EEE`; covers `EEEE`; coveredBy `EE0E`; crossing `EEEE`. Claimed exact cells: CAP 8/8, CUP 8, SUB 8, XOR 8 on the two-disc matrix. Chord-shell crossing is `aaaa`. Additional rows: disc×rectangle, half-disc×disc/square, arc×line, arc×arc, same-circle, complementary/overlapping/collinear/nested halves, two-shell lens, one-node touch, four-cut, holes, R0 nearly-disjoint exact, diagonal-touch **must stay approximate** (margin gate).

### 3.3 Other PERF-GATE classes

| Class | Tests | Operations | Types |
|-------|------:|------------|--------|
| `ReverseDispatchPerfGateTest` blob `4ded76ead11ff234ad9babd793b8313664c14684` | 15 | reverse `plain.op(curve)`: intersects, contains, covers, distance, isWithinDistance, OverlayNGCurve CAP/CUP/SUB, MultiSurface vs plain | discs, one arc, MultiSurface |
| `CurveOpsDistConPerfGateTest` blob `1df99979bca48041e914384b197f3b5f8a5eb470` | 10 | `distance` far discs / arc-point; `convexHull` disc / half-arc / CompoundCurve; `buffer` disc +1; plus four exactness (not timer) rows | disc, half-arc, CompoundCurve |
| `DistanceConstructionPerfGateTest` (app) blob `356c95fdfc67c1943310f3e84b7c3a4ad2d2260b` | 9 | TestBuilder shims: DiscreteHausdorff two discs + arc-baseline; nearestPoints arc-point; MIC disc + stadium; plus exactness (disc radius, stadium closed-form, half-disc is **not** a stadium laser) | disc, arc, stadium CompoundCurve, half-disc |
| `LargestEmptyCirclePerfGateTest` blob `f33811e36dee46ce101dc499f12b024dab7efb4d` | 8 | public LEC vs n=4 / `toLinear`; chord-path: plain square, points-only, plain polygon | disc, CircularString ring/arc, CompoundCurve, two-disc MultiSurface |
| `DirectedHausdorffDistancePerfGateTest` blob `ba4b748d8c4ca38a015e279fee5d74da92630b9a` | 4 | DHD two discs; arc→segment; M.2 tall bulge; M.3 `isFullyWithinDistance` | disc, 3-pt arc |
| `DiscreteFrechetDistancePerfGateTest` blob `dbdbda4fae8a50f1c6325d2aef770bbd1b7c255d` | 5 | two discs; arc-segment witness; concentric full-circle rings (M.5 `F=\|R−r\|`); identity + plain LineString **chord-path** | disc, arc, concentric CircularString rings |
| `DiscreteHausdorffDistancePerfGateTest` blob `e81969c249b42a78a775b52575a1ab621bc9ea15` | 3 | two discs; arc-segment witness; plain LineString **chord-path** | disc, arc |
| `CurveWKBPerfGateTest` blob `ff9ff079f030d7219bd58e2076887ffe3055fcd4` | 2 | first-class curve WKB write+read vs densify-then-WKB; plain LineString **chord-path** | disc only (curve row) |

Metric-kit map (`doc/METRIC_KIT_MX.md` blob `104044ee0f7657ff92d90229cfb93d727ca78775`): M.1–M.3 and M.5 **landed** (DHD Curve* pairs, bulge, IWD, concentric Fréchet). **Hold:** M.4 stadium/CompoundCurve Hausdorff; M.6+ DistanceOp TAG.

### 3.4 1M-trial PERF cells (Exact* / adapter, not OverlayNGCurve)

| File | What | Committed ratio |
|------|------|-----------------|
| `ExactArcOptionAMillionTrialTest` blob `1a13c0db214584c2662f02a0a7dfdf717e9b18de` + `doc/PROOFS_OPTION_A_HANDOVER.md` blob `bd012f53dac08078de296d9597ab916ca6a2c4c0` | ExactCircularArc static `length` vs densify (50k calls) | P1 A/ref **0.12099683853087871** ≤ 1.15 |
| `PredicateOptionBMillionTrialTest` blob `8056a9fa63de43d0933021ed6e85c56a7703edc0` + `doc/PROOFS_OPTION_B_HANDOVER.md` blob `254661146cd453982b4fe4ee22cbdbfe644e0167` | OrientableSegment arc `orientationIndex` vs densify p50 | **0.11755387552292201** ≤ 1.15 |

L1/L2 in Option A are **correctness** (hard disagree 0), not the 1.15× ratchet.

### 3.5 Operations **not** in a PERF-GATE class

No `*PerfGateTest` times: OffsetCurve, VariableBuffer, CoverageUnion, polygonize, noding/HotPixel, WKT, clothoid/bezier/ellipse/nurbs I/O, general DistanceOp, general DiscreteHausdorff beyond the two certified pairs, MultiCurve overlay, full LEC TAG. Epic / MMF docs still claim lasers for some of those (BUF/OFF kits); they are **not** in the 1.15× Surefire cells inventoried here.

---

## 4. Where numbers live

### 4.1 Committed scoreboard (counts, not timings)

`doc/LASER_RATCHET.md` (blob `f432ed0b6a63ad893f181450561e17dc08e6485c`):

| metric | count |
|--------|------:|
| green | 16 |
| chainsaw-only | 3 |
| measured | 11 (11 hold) |

No committed legend maps those 16 / 3 / 11 onto named types, TAGs, or PerfGate methods. The table is labelled “user board pin”; first landing (`d62f87263`) dated it 2026-08-17 @ `cf6e2b58`. Later retips (`ce651b3d6`, `6b1dbac1a`) kept the counts and dropped the re-verify command block. **Not** a per-type bible §2.6 table.

Related committed boards (not 1.15× numbers):

- `doc/OVERLAYNGCURVE_P2_SEAMS.md` — sewn 28 · stamped 6 · open 1 · named-miss 5 · total 35
- `doc/METRIC_KIT_MX.md` — M.0–M.6+ rung status
- `EPIC_SFA_CURVE_AWARENESS.md` — TAG honesty; spec class is “**not** the live scoreboard”
- Notion “JTS curve laser — progress” (external; not in git)

### 4.2 Committed millisecond snapshots (javadoc tables)

These are **one historical Surefire run** (OpenJDK 21, median of 31 after 15 warmups), baked into class comments. They are not regenerated by CI.

**`OverlayNGCurvePerfGateTest` — red (before gate):**

| case | laser | chainsaw | ratio |
|------|------:|---------:|------:|
| disjoint CAP | 4.169 ms | 0.139 ms | 30.0 |
| nested CAP | 7.894 ms | 0.646 ms | 12.2 |
| nested CUP | 7.914 ms | 0.563 ms | 14.1 |
| crossing CAP | 3.712 ms | 0.339 ms | 11.0 |

**After gate (same comment):** disjoint CAP 0.001 / 0.092 (0.016); nested CAP 0.211 / 0.599 (0.35); nested CUP 0.165 / 0.319 (0.52); crossing CAP 0.017 / 0.681 (0.025). Crossing CUP 0.039, SUB 0.026, XOR 0.040.

**`CurveOpsDistConPerfGateTest` — red then after:**

| case | before laser/chainsaw (ratio) | after |
|------|-------------------------------|-------|
| distance far discs | 4.735 / 4.000 (1.18) | 0.009 / 4.797 (0.002) |
| distance arc-point | 0.071 / 0.069 (1.02) | 0.001 / 0.097 (0.015) |
| convexHull disc | 1.031 / 1.064 (0.97) | 0.003 / 1.141 (0.003) |
| convexHull half-arc | 0.369 / 0.356 (1.04) | 0.002 / 0.533 (0.004) |
| buffer disc +1 | 0.802 / 0.342 (2.35) | 0.001 / 0.807 (0.002) |

**`DistanceConstructionPerfGateTest` — red then after:**

| case | before | after |
|------|--------|-------|
| Hausdorff two discs | 29.215 / 29.212 (1.00) | 0.007 / 15.5 (0.000) |
| Hausdorff arc-baseline | 0.049 / 0.049 (1.00) | 0.009 / 0.063 (0.14) |
| nearest arc-point | 0.016 / 0.012 (1.29) | 0.001 / 0.105 (0.01) |
| MIC disc | 0.738 / 0.386 (1.91) | 0.001 / 0.971 (0.001) |

No similar baked ms table in DirectedHausdorff / DiscreteHausdorff / Fréchet / LEC / WKB / ReverseDispatch classes — those only fail with live medians.

### 4.3 Live numbers (not in git)

On PerfGate failure: `label: laser X ms > chainsaw Y ms (ratio R > 1.15)`. Million-trial P1 prints `A_ns` / `densify_ns` / `ratio` to stdout. Nothing writes a CSV/JSON scoreboard.

### 4.4 How to re-run (from the dropped pin, still valid)

```bash
mvn -pl modules/curve -am test -Dtest=OverlayNGCurvePerfGateTest \
  -DfailIfNoTests=false -Dcheckstyle.skip=true -Dpmd.skip=true
```

Other classes: same `-Dtest=` with the simple class name. App module gate: `-pl modules/app`.

---

## 5. Gaps that matter for a later bar (facts only)

- Bible §2.6 wants **per-type** 1.15× vs densify-of-same-type. Gates today are **per-operation / per-fixture** on circular SFA shapes, plus one ExactCircularArc length cell. No Bézier / ellipse / clothoid / NURBS cell exists to satisfy “per curve type” for the zoo.
- The only committed **scoreboard** is the 16 / 3 / 11 count table; it does not name the 16 greens or the 11 holds.
- `OverlayNGCurveRatchetTest` is an exactness lock, not a timing lock.
- No JMH module or committed JMH result files.
- `doc/LASER_RATCHET.md` tip SHA (`c956b50d`) is behind `origin/feature/sfa-curve-rgr` (`b7394fd4`).

---

## Citations (branch `origin/feature/sfa-curve-rgr` @ `b7394fd4ae6ec3b2e65f1c65d5872456d674f865`)

| Path | Blob |
|------|------|
| `doc/LASER_RATCHET.md` | `f432ed0b6a63ad893f181450561e17dc08e6485c` |
| `doc/EXACT_CURVE_BIBLE.md` | `9de6a6d8b15906c03faa7bd46550f6f7bc9716c2` |
| `doc/CHAINSAW_LASER_PROGRAM.md` | `b5f8757aee25256488b204465bad5518ad59c40a` |
| `doc/METRIC_KIT_MX.md` | `104044ee0f7657ff92d90229cfb93d727ca78775` |
| `doc/PROOFS_OPTION_A_HANDOVER.md` | `bd012f53dac08078de296d9597ab916ca6a2c4c0` |
| `doc/PROOFS_OPTION_B_HANDOVER.md` | `254661146cd453982b4fe4ee22cbdbfe644e0167` |
| `EPIC_SFA_CURVE_AWARENESS.md` | `7268d64aa4a8815b724146fc4e69ff9f0caf1e1d` |
| `modules/curve/.../OverlayNGCurvePerfGateTest.java` | `d4ab621bc2579306c94fe4c8caf56215679e2549` |
| `modules/curve/.../OverlayNGCurveRatchetTest.java` | `243a12d5956d38d307c62f9d940f6571a043af4b` |
