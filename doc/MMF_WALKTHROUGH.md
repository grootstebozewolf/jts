# MMF Option B — verification walkthrough (#1195)

Evidence for draft [PR #61](https://github.com/grootstebozewolf/jts/pull/61) on fork SoT [#7](https://github.com/grootstebozewolf/jts/pull/7). Tip branch: `cursor/jts-issue-1195-c5d1`.

## Automated smoke (2026-08-17)

```bash
bash dev/check-no-curved.sh   # OK: no 'Curved' stems
mvn -pl modules/core,modules/curve,modules/app -am test \
  -Dtest=SegmentStringContractTest,WKBClothoidTest,WKBCurveZoo19_21Test,CurveOffsetCurveTest,CurveBufferArcTest,OverlayNGCircleTest,CurveHotPixelTest,CurveSegmentDcelTest,CurveLinearizationStrategyTest,CurveAwarenessGreenMetersTest,CurveAwarenessBadgeTest,ClothoidOverlayTest,GeometryLocationsWriterCurveZooTest \
  -DfailIfNoTests=false -Dcheckstyle.skip=true -Dpmd.skip=true
```

Observed: **BUILD SUCCESS** — green meters 36/36; signed I/O 8–12; preview HOLD 18–21 layouts (not SIGNED I/O); SegmentKind PM snap ≠ kind rename; ClothoidOverlay 5/5; zoo inspect labels 1/1.

## Phase accept checklist

| Phase | Evidence |
|---|---|
| 0 Strategy | `CurveLinearizationStrategyTest`; WarnSink → TB Log |
| 1 Option B | `SegmentStringContractTest` (incl. PM kind stability); OverlayNGCircle |
| 2 Bar2/HP | HP.1–3, DCEL, ClothoidOverlay, BiteVsHole/TwoHole on tip |
| 3 Ops | OFF/BUF/VBF/COV green meters |
| 4 preview HOLD 18–21 | `WKBClothoidTest` / `WKBCurveZoo19_21Test` (preview layouts; not SIGNED I/O) |
| 5 TB UX | badges, strategy menu+status, #56/#60 code; **await UX SIGN** |
| 6 Docs | `doc/MMF_OPTION_B.md`, `doc/latex/` Option B prose; **PDF `make` HOLD** |

## Remaining red (by design)

- **D-HF** — apex closed-form exists; full TAG `fail()` kept per meter text.

## Human HOLD

- UX SIGN on issues #56 / #60 (pin JAR)
- Guides: `cd doc/latex && make` when TeX Live available; do not force-merge conflicting Amyuni PDF binaries from #47
- Do not force-fold #28 / #41 (would delete tip MMF work)

## Manual UX (when SIGN ready)

1. Launch TestBuilder from MMF tip JAR.
2. Edit → Curve strategy: LINEARIZED — status strip + Log warn on densify.
3. Draw CurvePolygon; finish without close → CURVEPOLYGON (not POLYGON).
4. Select Buffer, focus a param, switch function — no silent Buffer rebound (#60).
5. Inspect Bezier/Ellipse/NURBS WKT — facet labels show type.
