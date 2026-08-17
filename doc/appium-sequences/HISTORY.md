# Appium sequence history

Append-only log for full regression packs.

## 2026-08-17

- Notion Status option `skip` added to Visual test suite.
- Hypothesis filed + **pass**: `TB-AP-AFFINE-TRANSLATE` — https://app.notion.com/p/3bf1c9833b06814cac12fb10f7c7d81e
- Upstream polygon translate **golden** kept (AutomationId sequence JSON).
- pr7 playback green: disc, circle, half-moon (`TbAppiumTranslatePlaybackTest`).
- Fixtures + contract tests under `doc/appium-sequences/` + `.../appium/`.

## 2026-08-17 — A→Z loop

- Generated AutomationId sequences for 46 playable categories × (upstream + disc/circle/half-moon).
- Notion: 13 Status=skip (non-Exec categories); remaining Status=pending hypotheses filed + tagged Appium.
- Affine.translate already pass; catalog smoke `TbAppiumCatalogPlaybackTest`.
- Skip list mirrored in SKIP.md / `_catalog/skips.json`.
- Full regression pack = play all `*.pr7.json` where upstream `golden:true` (or pr7-only).

## 2026-08-17 — Promote each hypothesis

Semantic promote on polygon + disc + circle + half-moon (Function registry invoke):

- **pass:** 39 playable (+ Affine already pass) → Notion Status pass
- **skip (promote):** 7 — CreateFractalShape.kochSnowflake (RuntimeException), Labelling.labelPoint (NOT_FOUND), LinearReferencing.extractPoint (IllegalArgumentException), OverlayCommonBitsRemoved.union (NOT_FOUND), OverlayEnhancedPrecision.union (NOT_FOUND), OverlayNGSnapping.union (TopologyException on disc), TestCaseGeometry.bufferMitredJoin (NOT_FOUND)
- Category skips (non-Exec): 13 unchanged
- No upstream bugs fixed; skip reasons recorded in SKIP.md
- Artifact: `/opt/cursor/artifacts/appium-promote-results.tsv`

## 2026-08-17 — PR #7 full playback

- `TbAppiumPr7PlaybackTest`: all non-skipped `*.pr7.json` played on disc/circle/half-moon fixtures via Function registry (Exec apply path).
- Result: **BUILD SUCCESS** (1/1, ≥100 sequences played, skipped JSON excluded).
- Log: `/opt/cursor/artifacts/pr7-playback.log`

## 2026-08-17 — Refactor v6 MMF Option B

- Stamp `optionB` block on pr7 sequences; densify categories prepend strategy LINEARIZED AutomationId.
- Add `TbAppiumOptionBContractTest` (warn on LINEARIZED, PRESERVE identity, strategy IDs, no Curved).
- Re-run MMF Option B smoke + Appium suite.
