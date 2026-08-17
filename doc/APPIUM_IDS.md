# Appium / accessibility IDs — TestBuilder

Convention: `jts.tb.<surface>.<control>`.

Wired via `AutomationIds.set` → `Component.name` + `AccessibleContext.accessibleName`
(Appium desktop **accessibility id**).

## Shared (upstream + PR #7)

| ID | Control |
|---|---|
| `jts.tb.toolbar.case.prev` | Previous case |
| `jts.tb.toolbar.case.next` | Next case |
| `jts.tb.toolbar.case.new` | New case |
| `jts.tb.toolbar.case.copy` | Copy case |
| `jts.tb.toolbar.case.delete` | Delete case |
| `jts.tb.toolbar.zoom.oneToOne` | Zoom 1:1 |
| `jts.tb.toolbar.zoom.input` | Zoom to input |
| `jts.tb.toolbar.zoom.inputA` | Zoom to A |
| `jts.tb.toolbar.zoom.inputB` | Zoom to B |
| `jts.tb.toolbar.zoom.result` | Zoom to result |
| `jts.tb.toolbar.zoom.fullExtent` | Zoom full extent |
| `jts.tb.toolbar.draw.rectangle` | Draw rectangle |
| `jts.tb.toolbar.draw.polygon` | Draw polygon |
| `jts.tb.toolbar.draw.lineString` | Draw linestring |
| `jts.tb.toolbar.draw.point` | Draw point |
| `jts.tb.toolbar.mode.zoom` | Zoom mode |
| `jts.tb.toolbar.mode.pan` | Pan mode |
| `jts.tb.toolbar.mode.info` | Info mode |
| `jts.tb.toolbar.mode.editVertex` | Edit vertex |
| `jts.tb.toolbar.mode.move` | Move |
| `jts.tb.toolbar.extractElements` | Extract elements |
| `jts.tb.toolbar.selectElements` | Select elements |
| `jts.tb.toolbar.deleteVertex` | Delete vertex/element |
| `jts.tb.wkt.a` | WKT A text area |
| `jts.tb.wkt.b` | WKT B text area |
| `jts.tb.wkt.load` | Load geometry |
| `jts.tb.wkt.inspect` | Inspect |
| `jts.tb.wkt.exchange` | Exchange A/B |
| `jts.tb.wkt.a.copy` / `.paste` / `.clear` | A clipboard |
| `jts.tb.wkt.b.copy` / `.paste` / `.clear` | B clipboard |

## PR #7 only (curve)

| ID | Control |
|---|---|
| `jts.tb.toolbar.draw.circularString` | Draw CircularString |
| `jts.tb.toolbar.draw.compoundCurve` | Draw CompoundCurve |
| `jts.tb.toolbar.draw.curvePolygon` | Draw CurvePolygon |
| `jts.tb.toolbar.draw.triangle` | Draw Triangle |
| `jts.tb.toolbar.draw.tin` | Draw TIN |
| `jts.tb.menu.edit.curveStrategy.linearized` | *(Phase B)* |
| `jts.tb.menu.edit.curveStrategy.preserve` | *(Phase B)* |
| `jts.tb.status.curveStrategy` | *(Phase B)* |

## Wired in Phase A
Toolbar + WKT panel on this tree (includes curve draw tools).

## Seed: Appium click suite (hypothesis → golden → pr7)

See `doc/appium-sequences/README.md`. First category: AffineTransformation.translate
(`TB-AP-AFFINE-TRANSLATE`) — upstream polygon golden; pr7 disc/circle/half-moon playback.

## Seed: basics — 3-pt CircularString + Move (MMF)

Curve analogue of upstream `draw.polygon` / `draw.lineString` then `mode.move`.
Headless lock: `CircularStringMoveToolBasicsTest`.

1. `jts.tb.toolbar.draw.circularString`
2. Three canvas clicks (start / mid / end), e.g. `(-5,0)`, `(0,5)`, `(5,0)`
3. Finish the stream draw (same finish as other stream tools)
4. `jts.tb.toolbar.mode.move` → drag the geometry
5. Inspect `jts.tb.wkt.a`: still `CIRCULARSTRING (...)`, not `LINESTRING`, not empty

Pass: typed arc survives MoveTool. Do not lead demos with Buffer / function-panel SIGNs.

## Seed: U-shape almost-touch + CAP·CUP·SUB·XOR (canonical TOUCH)

U-shaped `CURVEPOLYGON` whose arms almost touch **past** OverlayNGCurve decide
tolerance. Topology wins over densify — **no donut**. Linearized matches native
on this witness. Headless lock: `OverlayNGCurveUShapeTouchBasicsTest`.

1. Paste U WKT into `jts.tb.wkt.a` → load (fixture in the test class)
2. Inspect: simply connected, no interior ring
3. Optional: load left/right lobes into A/B with tip gap `0.001`
4. Function tree **OverlayNGCurve**: CAP / CUP / SUB / XOR (+ `exactness`)
5. Pass: no donut; two lobes → CUP stays two bodies (`FF2FF1212`), not a sealed annulus
6. Never an OverlayNGCurve **TOUCH** button (DE-9IM / `touches` only)

## Function panel (Phase B)

| ID | Control |
|---|---|
| `jts.tb.fn.tree` | Function tree |
| `jts.tb.fn.exec` | Compute |
| `jts.tb.fn.execToNew` | Compute to new |
| `jts.tb.fn.param.0`..`4` | Distance / qsegs / cap / join / mitre |

## Verification

```bash
# Headless-safe (IDs + MMF basics seed):
mvn -pl modules/app -am test \
  -Dtest=AutomationIdsTest,AutomationIdsWiringTest,CircularStringMoveToolBasicsTest \
  -Djava.awt.headless=true -Dcheckstyle.skip=true -Dpmd.skip=true

# Full WKT panel IDs (needs display):
xvfb-run -a mvn -pl modules/app -am test -Dtest=AutomationIdsWiringTest \
  -Dcheckstyle.skip=true -Dpmd.skip=true
```
