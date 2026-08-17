# MMF Option B — fork quality gate (#1195)

Draft companion to [PR #61](https://github.com/grootstebozewolf/jts/pull/61) on SoT [PR #7](https://github.com/grootstebozewolf/jts/pull/7). Not an upstream LocationTech proposal until maintainers engage.

## Contract

1. **Noder = Option B.** `[i,i+1]` is `SegmentKind.LINEARIZED | ARC | CERTIFIED`. Default earth is linearized. Exact consumers ask `mayCollapseToChord`. Spatial indexes may use PM-scaled chord/expanded bounds (the allowed lie).
2. **No silent linearization.** `CurveLinearizationStrategy` default is `LINEARIZED` and **must warn**. `PRESERVE` keeps curve identity.
3. **Laser order:** Maintainable → soundness/precision → functionality/performance. Prefer chainsaw when faster unless (1) or (2) require a laser. PERF-GATE slack **15%**.
4. **Overlay name:** `OverlayNGCurve`, never *Curved*.
5. **WKB zoo SIGN:** types **18–21** (`CRV-CLOTHOID`, `PRF-BEZIER`, `PRF-ELLIPSE`, `CRV-NURBS`) plus ISO `+1000/+2000/+3000`. Types **15–17** stay Architect-gated.

## Verify (smoke)

```bash
bash dev/check-no-curved.sh
mvn -pl modules/core,modules/curve,modules/app -am test \
  -Dtest=SegmentStringContractTest,WKBClothoidTest,WKBCurveZoo19_21Test,CurveOffsetCurveTest,CurveBufferArcTest,OverlayNGCircleTest,CurveHotPixelTest,CurveSegmentDcelTest,CurveLinearizationStrategyTest,CurvePolygonToolTest,SpatialFunctionPanelFocusTest \
  -DfailIfNoTests=false -Dcheckstyle.skip=true -Dpmd.skip=true
```

## HOLD

- Issue [#56](https://github.com/grootstebozewolf/jts/issues/56): tip locks `CurvePolygonToolTest` (16/16); leave open until PO UX SIGN on pin JAR.
- Issue [#60](https://github.com/grootstebozewolf/jts/issues/60): Exec/`currentFunc` fix landed; await UX SIGN.
- Do not open `locationtech/jts` PRs from this branch without PO word.
