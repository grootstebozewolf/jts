# Appium sequences — TestBuilder hardening

AutomationId click contracts for JTS TestBuilder. Compatible with Appium
desktop (`accessibility id` = `Component.name` = `jts.tb.*`).

## Loop

1. File hypothesis in Notion Visual suite (Status `pending`, Runner Appium) — **tag first**
2. Upstream reference (LineString / Polygon): golden → keep JSON, or **skip** (+ screenshot + bug note; do not fix upstream)
3. If golden: playback on PR #7 with **disc / circle / half moon**
4. Append `HISTORY.md`

## Layout

- `_fixtures/` — WKT fixtures
- `<Category>/` — sequence JSON
- `SKIP.md` — skip list mirror
- `HISTORY.md` — regression log

## Alpha order

Function categories A→Z (see GeometryFunctionRegistry). First: AffineTransformation.

## ClaimId pattern

`TB-AP-<CAT>-<FN>` · CaseId `case-tb-ap-<cat>-<fn>`
