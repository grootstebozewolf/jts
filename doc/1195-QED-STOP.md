# #1195 Year-1 QED∨QEX stop (not epic close)

Pin: `feature/sfa-curve-rgr` @ `266116407d4bc76fb7afd59b334ba7573e9644a5`.

This is a **Year-1 circular honesty stop**. It does **not** close the epic or the fork SoT.

- [locationtech/jts#1195](https://github.com/locationtech/jts/issues/1195) stays **OPEN**.
- Fork [PR #7](https://github.com/grootstebozewolf/jts/pull/7) stays **OPEN**.

Bible: [`EXACT_CURVE_BIBLE.md`](EXACT_CURVE_BIBLE.md) §2 (exact where claimed; no silent linearisation), §3 (ExactCircularArc privileged), §4.2 (thin protocol), §5 (Year-1 circular only), §6 (Never), §8 (OverlayNGCurve never *Curved*), §9 / A1. Tip pin as above.

| claim | verdict | evidence | never |
|---|---|---|---|
| R-CONT PIP: circular disc vs Point / MultiPoint is `d²` vs `r²`, no densify | **QED** | `modules/curve/src/test/java/org/locationtech/jts/geom/curve/CurveExactPipTest.java` (`testBulgePointIsInterior`, `testOnCircleIsBoundary`); `CurveOps.contains` → `CurveExact` (`d²` vs `r²`); board-sister green lineage `e2fc4989` | densify claiming exact; silent flatten |
| D-HF two-pair closed form (apex CS→LS; two discs). Full TAG stays red | **QED** | `modules/curve/src/test/java/org/locationtech/jts/algorithm/distance/DiscreteHausdorffDistanceCurveTest.java`; public DHD lock `0ca71b`; `CurveAwarenessSpecTest#test_D_HF_hausdorffFrechetCurveAware` keeps `fail()` | full TAG green; public DHD exact in general (still chords) |
| Year-1 ExactCircularArc + OrientableSegment + A1 + laser ratchet #129 | **QED** | `6b1dbac1` (#63); `36ed1dce` (#66); `81a16be9` (#125 A1); `5865e55fd9c1` (#129); `ExactCircularArcTest#testExactCurveProtocolSurface`; `OrientableSegmentTest` | grow `ExactCurve`; remint ADR-0004; Year-2 Exact* on this branch |
| OV-P0 OverlayNGCurve Phase 0 kits. CompoundCurve leftover R2 is named densify | **PARTIAL** | `modules/curve/src/test/java/org/locationtech/jts/operation/overlayng/curve/OverlayNGCurvePhase0Test.java` kits green (`testR2_exactAnswersAreNotFlagged`); leftover R2 **NAMED-APPROX** (`isApproximate()=true`); refuse silent flatten is `feature/zoo` [#131](https://github.com/grootstebozewolf/jts/pull/131), off #7 | OverlayNGCurve*Curved*; silent flatten as exact |
| SpecTest `fail()` theater = QED because Surefire excludes the class | **QEX** | `CurveAwarenessSpecTest` still `fail()` (D-HF full TAG); `modules/curve/pom.xml` Surefire `**/spec/curveawareness/*.java` exclude is CI silence, not proof | hide `fail()`; QED by forbidding Surefire |
| CRV-TOUCH / public noder / `OverlayNGCurve.TOUCH` | **HOLD** | Architect Never. `OverlayNGCurve` ops are CAP·CUP·SUB·XOR only. Pins: `OverlayNGCurveUShapeTouchBasicsTest`, `CurveExactRelateTouchTest` (no `TOUCH` field). [#38](https://github.com/grootstebozewolf/jts/pull/38) / [#27](https://github.com/grootstebozewolf/jts/pull/27) stay **off #7** | `OverlayNGCurve.TOUCH` fifth op; public noder on #7 |
| Exact* zoo on #7 | **HOLD** | Bible §5 Year 2. `03efaa099`. Library work on `feature/zoo`, not a #7 leftover | start Year-2 Exact* here; grow `ExactCurve` |
| ML.2 / HP.4 / N-SS / `SHARED_SNAPPED_RAY` | **HOLD** | STOPPED. [`LASER_RATCHET.md`](LASER_RATCHET.md); Bible §6 | non-linear core `SegmentString`; start N-SS / walk `SHARED_SNAPPED_RAY` |

## Never (this stop)

- Non-linear `SegmentString` in core.
- Densify claiming exact.
- Grow `ExactCurve`.
- Start N-SS / `SHARED_SNAPPED_RAY` / Year-2 Exact* on this branch.
- Remint ADR-0004.
- Rename OverlayNGCurve to *Curved*.
- Hide `CurveAwarenessSpecTest` `fail()`.
