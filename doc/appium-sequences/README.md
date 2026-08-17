# Appium sequences — TestBuilder hardening

AutomationId click contracts for JTS TestBuilder (`jts.tb.*`).

## Loop

1. Notion hypothesis first (tag, Status `pending`, Runner Appium)
2. Upstream LineString/Polygon: **golden** keep JSON, or Status **skip** (+ screenshot + bug note; do not fix upstream)
3. If golden: playback on PR #7 with **disc / circle / half moon**
4. Append `HISTORY.md` for full regression packs

## Status (2026-08-17 A→Z)

| Bucket | Count |
|---|---|
| Playable categories (sequences generated) | 46 + Affine |
| Skip categories (Notion Status=skip) | 13 |
| Sequence JSON files | ~187 |
| Fixtures | polygon, linestring, disc, circle, half-moon |

Catalog: `_catalog/playable.json`, `_catalog/skips.json`.

## ClaimId

`TB-AP-<CAT>-<FN>` · CaseId `case-tb-ap-<cat>-<fn>`

## Tests

```bash
mvn -pl modules/app -am test \
  -Dtest=TbAppiumSequenceContractTest,TbAppiumTranslatePlaybackTest,TbAppiumCatalogPlaybackTest \
  -DfailIfNoTests=false -Djava.awt.headless=true -Dcheckstyle.skip=true -Dpmd.skip=true
```
