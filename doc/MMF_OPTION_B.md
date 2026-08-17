# MMF Option B — fork quality gate (#1195)

Draft companion to [PR #61](https://github.com/grootstebozewolf/jts/pull/61) on SoT [PR #7](https://github.com/grootstebozewolf/jts/pull/7). Not an upstream LocationTech proposal until maintainers engage.

## Contract

1. **Noder = Option B.** `[i,i+1]` is `SegmentKind.LINEARIZED | ARC | CERTIFIED`. Default earth is linearized. Exact consumers ask `mayCollapseToChord`. Spatial indexes may use PM-scaled chord/expanded bounds (the allowed lie). **PM snap must not rename kind** — see `SegmentStringContractTest#testPrecisionModelSnapDoesNotChangeSegmentKind`.
2. **No silent linearization.** `CurveLinearizationStrategy` default is `LINEARIZED` and **must warn**. `PRESERVE` keeps curve identity.
3. **Laser order:** Maintainable → soundness/precision → functionality/performance. Prefer chainsaw when faster unless (1) or (2) require a laser. PERF-GATE slack **15%**.
4. **Overlay name:** `OverlayNGCurve`, never *Curved*.
5. **WKB zoo SIGN:** types **18–21** (`CRV-CLOTHOID`, `PRF-BEZIER`, `PRF-ELLIPSE`, `CRV-NURBS`) plus ISO `+1000/+2000/+3000`. Types **15–17** stay Architect-gated. XY + ISO Z locked in `WKBClothoidTest` / `WKBCurveZoo19_21Test`.

## Shipped meter clusters (green)

Option B + WKB 18–21 + OFF/BUF-*/VBF + DSF/TRI + N-AA/AL/N-SS + F-*/B-* + LRF-LEN/LOC + AT-S/NS + C-LIN/AREA/IP + S-* + OV + D-AA/OP + R-CONT/PR + V-CS/CP + PRC-SN + H-CC + PLG + COV + TB-T/FN.

Still red by design: **D-HF** (full TAG keep `fail()`; apex closed-form + general curve densify honesty landed).

### Named misses (Phase 2 accept — explicit)

- CLOTHOID-FRESNEL; VBF arc-offset laser; PLG CurvePolygon faces; HP.1 wrong-ring walk; OverlayNGCurve R2 stamps; WKB 15–17 Architect-gated.

Folded pins onto tip: **#55** `logoClothoid`, **#52** write-flatten honesty, **#40** `ClothoidOverlay`, **#26** HP.1.

## Verify (smoke)

See also [MMF_WALKTHROUGH.md](MMF_WALKTHROUGH.md).

```bash
bash dev/check-no-curved.sh
mvn -pl modules/core,modules/curve,modules/app -am test \
  -Dtest=SegmentStringContractTest,WKBClothoidTest,WKBCurveZoo19_21Test,CurveOffsetCurveTest,CurveBufferArcTest,OverlayNGCircleTest,CurveHotPixelTest,CurveSegmentDcelTest,CurveLinearizationStrategyTest,CurveAwarenessGreenMetersTest,CurveAwarenessBadgeTest,ClothoidOverlayTest,GeometryLocationsWriterCurveZooTest \
  -DfailIfNoTests=false -Dcheckstyle.skip=true -Dpmd.skip=true
```

## TestBuilder (Phase 5)

- Log tab receives `CurveLinearizationStrategy` densify warnings via `WarnSink`.
- Edit menu: **Curve strategy: LINEARIZED (warn)** / **PRESERVE**.

## Guides (Phase 6)

LaTeX sources live under `doc/latex/` (from draft [#47](https://github.com/grootstebozewolf/jts/pull/47), cherry-picked without force-merging the conflicting binary PDFs). Rebuild with TeX Live:

```
cd doc/latex && make
```

In-tree `doc/*.pdf` stubs remain until a clean `make` is signed off.

## HOLD

- Issue [#56](https://github.com/grootstebozewolf/jts/issues/56): tip locks `CurvePolygonToolTest` (16/16); leave open until PO UX SIGN on pin JAR.
- Issue [#60](https://github.com/grootstebozewolf/jts/issues/60): Exec/`currentFunc` fix landed; await UX SIGN.
- Guides [#47](https://github.com/grootstebozewolf/jts/pull/47): LaTeX rebuild CONFLICTING — do not force-merge.
- Do not open `locationtech/jts` PRs from this branch without PO word.
