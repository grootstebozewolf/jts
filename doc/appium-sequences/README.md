# Appium sequences — TestBuilder hardening (Draft v6 MMF Option B)

AutomationId click contracts for JTS TestBuilder (`jts.tb.*`).
Aligned with [MMF_OPTION_B.md](../MMF_OPTION_B.md) / epic Draft **v6 MMF Option B**.

## Option B contracts

1. **Noder = Option B** (`SegmentKind` LINEARIZED | ARC | CERTIFIED)
2. **No silent linearization** — default `CurveLinearizationStrategy.LINEARIZED` **must warn**; `PRESERVE` keeps identity
3. Overlay name is **OverlayNGCurve**, never *Curved*
4. PERF-GATE slack **15%**
5. pr7 densify paths prepend `jts.tb.menu.edit.curveStrategy.linearized`

## Loop

1. Notion hypothesis first (tag, Status `pending`, Runner Appium)
2. Upstream LineString/Polygon: **golden** keep JSON, or Status **skip** (+ screenshot + bug note; do not fix upstream)
3. If golden: playback on PR #7 with **disc / circle / half moon**
4. Append `HISTORY.md` for full regression packs

## Status

| Bucket | Count |
|---|---|
| Playable categories | 46 + Affine |
| Skip (Notion) | category skips + promote skips |
| Fixtures | polygon, linestring, disc, circle, half-moon |

## Tests

```bash
bash dev/check-no-curved.sh
mvn -pl modules/app -am test \
  -Dtest=TbAppiumOptionBContractTest,TbAppiumPr7PlaybackTest,TbAppiumTranslatePlaybackTest,TbAppiumSequenceContractTest,TbAppiumCatalogPlaybackTest \
  -DfailIfNoTests=false -Djava.awt.headless=true -Dcheckstyle.skip=true -Dpmd.skip=true
```
