# Does PR #7 HEAD still match the #56 and #60 SIGN scripts?

Research for [grootstebozewolf/jts#70](https://github.com/grootstebozewolf/jts/issues/70).
Code only. TestBuilder was not launched. This is not a UX SIGN.

## Short answer

| Script | Verdict | One line |
|--------|---------|----------|
| [#56](https://github.com/grootstebozewolf/jts/issues/56) PO gesture lock | **match** | `CurvePolygonTool` at HEAD is byte-identical to the [PR #57](https://github.com/grootstebozewolf/jts/pull/57) merge; later `feature/sfa-curve-rgr` work did not retouch finish / double-click / Escape. |
| [#60](https://github.com/grootstebozewolf/jts/issues/60) TB-FN | **match** | [PR #61](https://github.com/grootstebozewolf/jts/pull/61) `Exec`/`currentFunc` still prefers the selected function over the tree; the only later edit on that panel is Appium IDs. |

Later work on `feature/sfa-curve-rgr` after those merges did **not** regress the product paths the two SIGN scripts require.

## Pins (primary sources)

| Object | SHA / ref | Owner |
|--------|-----------|--------|
| PR #7 HEAD / `origin/feature/sfa-curve-rgr` | `a10708ed32eb85bfac6875912aea5c5ef3903262` (`a10708ed3 Merge pull request #67 …`) | [PR #7](https://github.com/grootstebozewolf/jts/pull/7) `headRefOid` |
| This research branch | `research/pr7-head-sign-scripts` created from that SHA (not from `feature/orientation-robustness-tests`) | method |
| [PR #57](https://github.com/grootstebozewolf/jts/pull/57) merge | `075740ec3a95e4154df467067f203dcd8eb6fd37` | `gh pr view 57` `mergeCommit` |
| Last `CurvePolygonTool.java` edit | `7b0d144cdb6d8cb49809e6b6487ba0b4abc09611` (PR #57 tip) | `git log -1 -- CurvePolygonTool.java` |
| [PR #61](https://github.com/grootstebozewolf/jts/pull/61) merge | `f54278a18943095834e7fd7cd0967704069caad6` | `gh pr view 61` `mergeCommit` |
| Issue #56 PO lock | comment [5309149580](https://github.com/grootstebozewolf/jts/issues/56#issuecomment-5309149580) | issue body + comments |
| Issue #60 TB-FN repro | issue body (no comments) | [issue #60](https://github.com/grootstebozewolf/jts/issues/60) |

Ancestry on HEAD (first-parent): `075740ec` is an ancestor of `a10708ed`; `f54278a1` is an ancestor of `a10708ed`.

Blob identity:

- `CurvePolygonTool.java` at `075740ec` == HEAD blob `bb2c4086c87b235ab2392ac049381bd7bdb4bb66` (`git diff 075740ec HEAD -- CurvePolygonTool.java` empty).
- `SpatialFunctionPanel.java` at `f54278a1` blob `3e3dea9b989a8ad7dc9d8258af7a103c53fec22e` vs HEAD blob `cc9b4def84b736ba56f8d27bcc595e481252c626` — Appium `AutomationIds.set` only (`4d304972980c0b1e938fa5c7abd2b7f5b997a18c`).

## #56 — Silent CurvePolygon drop / PO lock

PO lock (issue #56 comment, 16 Aug 2026):

1. Click the start vertex, or double-click anywhere → auto-close and commit `CURVEPOLYGON` with the current shell (`CIRCULARSTRING` or `COMPOUNDCURVE`).
2. Escape → cancel. Status: `CurvePolygon cancelled.` Canvas empty on purpose. Not silent.
3. No holes in this tool.
4. Never flatten to `POLYGON`. Never drop the in-progress geom on a non-closing finish.

Observed bug on the same issue: non-closing double-click dropped the in-progress geom to empty. That finish becomes auto-close.

### 1. Click-start or double-click anywhere commits a closed CURVEPOLYGON

`CurvePolygonTool` javadoc and `mousePressed` / `isFinishingRelease` / `bandFinished` at `7b0d144cd` / HEAD:

- True same-spot double-click (`clickCount >= 2` and `isNearLastPress`) sets `pendingDoubleClickFinish`; `isFinishingRelease` is that flag, not parent `LineBandTool`'s any `clickCount == 2` (`modules/app/src/main/java/org/locationtech/jtstest/testbuilder/ui/tools/CurvePolygonTool.java` at HEAD).
- First click on the start vertex after **three or more** captured points calls `finishGesture()` and returns without treating it as cancel.
- `isFinishClick(capturedCount, onStartVertex, trueDoubleClick)`: double-click commits at `capturedCount >= 2`; click-start needs `capturedCount >= 3`.
- `closeCircularShell` appends the start vertex when needed and, if the leftover is even, inserts a complementary-arc control (not a chord midpoint). `null` only when there is no shell (`size < 2`).
- `bandFinished` (when not cancelling) `setGeometryType(CURVEPOLYGON)`, `closeCircularShell`, `geomModel().addComponent(shell)`, `updateGeom`.

`LineBandTool.finishGesture` (`modules/app/src/main/java/org/locationtech/jtstest/testbuilder/ui/tools/LineBandTool.java`) always `coordinates.clear()` after `bandFinished` — that is how the rubber-band is consumed on both commit and cancel.

Commit type: `GeometryEditModel.addComponent` switch `GeometryType.CURVEPOLYGON` → `GeometryCombiner.addCurvePolygon` (`modules/app/src/main/java/org/locationtech/jtstest/testbuilder/model/GeometryEditModel.java`). That factory path is `createCurvePolygon(shell, null)` with a `CircularString` through the closed odd-count controls (`GeometryCombiner.java`). Tests at HEAD: `CurvePolygonToolTest.testDoubleClickAnywhereAutoClosesToCircleNotChord`, `testFirstClickOnStartCommitsNotCancel`, `testAddComponentKeepsCurvePolygonNotPolygon`.

Live mouse tool builds a **CircularString** shell only (class javadoc: not a mixed-shell CompoundCurve editor). A shell that is already a `CompoundCurve` is left as `COMPOUNDCURVE` (`testExistingCompoundShellIsNotLinearized`). That is the PO “current shell (CIRCULARSTRING or COMPOUNDCURVE)” wording: draw path is CircularString; compound is not linearized.

### 2. Escape cancels with exact status; canvas empty; not silent

- `isCancelKey` is `VK_ESCAPE` only.
- `cancelInProgress` writes `CANCELLED_STATUS` (`"CurvePolygon cancelled."` including the period) via `JTSTestBuilder.controller().setStatus`, then `finishGesture()` with `cancelling = true` so `bandFinished` returns without `addComponent`. `finishGesture` then clears coordinates (empty band).
- `cancelStealsInputTab()` / `cancelCallsDisplayInfo()` are `false`. `showCancelled` does not call `displayInfo` / `showInfoTab`.
- Click-start / double-click `clearStatusOnCommit()` with `COMMIT_CLEARS_STATUS` `""`.

Controller: `JTSTestBuilderController.setStatus` → `TestCasePanel.setStatus` on the Case/PM strip (`JTSTestBuilderController.java`, `TestCasePanel.java`). Tests: `CurvePolygonToolTest.testCancelledStatusIsExact`, `TestCasePanelStatusTest`.

### 3. No holes

Tool javadoc: exterior shell only. `addCurvePolygon` uses `createCurvePolygon(shell, null)`. No hole-capture path in `CurvePolygonTool`.

### 4. Never flatten to POLYGON; never drop on a non-closing finish

- `closeCircularShell` repairs even leftovers instead of aborting (`testEvenAfterNaiveCloseIsRepairedNotDropped`). Non-closing double-click after two or more points is a finish (`isFinishClick(..., trueDoubleClick)`), not a drop.
- Combiner comment: “this does not emit a linearized Polygon.” `CurvePolygonToolTest.assertCurvePolygonCircularString` requires WKT `CURVEPOLYGON (CIRCULARSTRING` and `!(g instanceof Polygon)`.
- One-point / empty input still returns `null` (`testOnePointDoesNotCommit`). That is not the #56 SIGN gesture (an in-progress shell, then non-closing double-click).

### Later work after `075740ec`

`git log 075740ec..HEAD -- CurvePolygonTool.java CurvePolygonToolTest.java` is empty for the tool and its test.

The only later hit on `TestCasePanel.java` is `732c64639d6924ea2c51dd81fbbb41ffacaa2bde` (`feat(app): Appium IDs for curve strategy menu and status strip`): it sets `AutomationIds.STATUS_CURVE_STRATEGY` on the **same** `lblStatus`. That is a name on the existing strip cell; `setStatus` / `reserveStatusRoom` for `"CurvePolygon cancelled."` are unchanged vs `7b0d144cd`.

[PR #61](https://github.com/grootstebozewolf/jts/pull/61) also writes the same strip at startup and from the Edit-menu strategy picker (`JTSTestBuilderFrame` / `JTSTestBuilderMenuBar`: `"Curve strategy: LINEARIZED"` / `"PRESERVE"`). That overwrites the strip when strategy is shown; it does **not** change Escape writing `"CurvePolygon cancelled."` and is not a `CurvePolygonTool` finish/cancel change. `git diff 075740ec f54278a1 -- CurvePolygonTool.java` is empty.

## #60 — TB-FN Function param focus leak

Issue #60 body (pin `JTSTestBuilder-pr7.jar` @ `61eb3377`):

1. Load `JTS.logoLines` into A (MULTICURVE / COMPOUNDCURVE / CIRCULARSTRING).
2. Select Geometry function **translate** (AffineTranslation).
3. Click the **dX** field to set 10.

Expected: translate dX/dY accept 10 and 8. Tree click does not run Buffer. A stays the curved logo.

Observed on that JAR: clicking dX silently selected `Buffer.buffer`, ran `Buffer.buffer(MultiCurve[17], 0.0)` with no Run click, result `POLYGON EMPTY`, A empty.

Laser notes in [PR #61](https://github.com/grootstebozewolf/jts/pull/61) squash message (`f54278a1`): *“fix: TB-FN #60 Exec stays on selected function, not Buffer — getMetaFunction prefers currentFunc over tree selection so focusing the shared dX/Distance param field cannot re-bind Exec to Buffer.buffer. Null-safe functionChanged; param fields request focus on press.”*

That patch is still in HEAD.

### Load logoLines into A

`JTSFunctions.logoLines` returns `CurveGeometryFactory.createMultiCurve` of J / T / S members (`modules/app/src/main/java/org/locationtech/jtstest/function/JTSFunctions.java`). `JTSFunctionsLogoLinesCurveTest` at HEAD still asserts `MultiCurve` plus CompoundCurve + CircularString members.

### Select translate; dX / dY are its scalar params

`AffineTransformationFunctions.translate` (`modules/app/src/main/java/org/locationtech/jtstest/function/AffineTransformationFunctions.java`):

```java
@Metadata(description="Translates a geometry by an offset (dx,dy)")
public static Geometry translate(Geometry g,
    @Metadata(title="dX") double dx,
    @Metadata(title="dY") double dy)
```

`SpatialFunctionPanel.updateParameters` copies `func.getParameterNames()` onto the shared fields (`txtDistance` → dX, `txtQuadrantSegs` → dY). Both fields get `pinParamFocus` in `uiInit`.

### Clicking dX must not re-bind Exec to Buffer

At HEAD (`SpatialFunctionPanel.java`, including the Appium-ID commit):

- `getMetaFunction()` starts from `currentFunc`, falling back to `geomFuncPanel.getFunction()` only if `currentFunc == null`. Comment cites TB-FN #60.
- Exec button: `execFunction(false)` → `execFunction(getMetaFunction(), createNew)` which assigns `currentFunc = func` then `ResultController.execute`.
- `ResultController.functionInvocation` uses `functionPanel.getFunction()` which is `currentFunc` (`SpatialFunctionPanel.getFunction`).
- `functionChanged`: null-safe return; sets `currentFunc`; **clears Live Exec** (`cbExecAuto.setSelected(false)`) so a prior Buffer Live Exec is not inherited.
- `pinParamFocus`: `mousePressed` → `requestFocusInWindow` on the scalar field so the press is not treated as a function-tree selection.

Tree invocation vs selection (`GeometryFunctionTreePanel.java`): single-click `TreeSelectionListener` → `functionSelected` → `functionChanged`. Double-click → `functionInvoked` → `execFunction(e.getFunction(), false)` with that tree node. The #60 leak is “click dX runs Buffer with no Run”; it is not “double-click Buffer in the tree,” which still executes Buffer by design.

Live Exec path (`JTSTestBuilderController.geometryChanged`): runs `resultController.execute(false)` only when `isAutoExecute()`. Selecting translate now forces that checkbox off.

### A stays the curved logo (translate, not Buffer)

`translate` is `AffineTransformation.translationInstance(dx, dy).transform(g)`. PR #61 also folded CompoundCurve/CurvePolygon apply honesty (`f54278a1` message: *“fix: CompoundCurve/CurvePolygon translate visits all member controls”*). `JTSFunctionsLogoLinesTranslateTest` at HEAD asserts `logoLines` + `(10, 8)` stays `MultiCurve` / `COMPOUNDCURVE` / `CIRCULARSTRING`, and Function-tree `translate` is the same `copy(); apply(trans)` as MoveTool. That is a unit pin of the apply, not a GUI SIGN.

`git log f54278a1..HEAD` on `AffineTransformationFunctions.java` / `GeometryComponentTransformer.java` is empty.

### Later work after `f54278a1`

`git diff f54278a1 HEAD -- SpatialFunctionPanel.java` is only `AutomationIds` on the tree, Exec, Exec-New, and param widgets (`4d3049729`). `SpatialFunctionPanelFocusTest.java` is unchanged since `f54278a1`.

That test only asserts `translate` and `buffer` are distinct `GeometryFunction` names. It does **not** instantiate the panel or click dX. The product behavior the SIGN needs lives in `getMetaFunction` / `pinParamFocus` / Live-Exec reset, which HEAD still has.

## What this does not claim

- No TestBuilder process, no JAR build, no canvas click, no Notion flip.
- Issues [#56](https://github.com/grootstebozewolf/jts/issues/56) and [#60](https://github.com/grootstebozewolf/jts/issues/60) stay open; UX SIGN is still the close gate on those `[visual-qa]` tickets.
- Shared status-strip ID (`STATUS_CURVE_STRATEGY` on the Case/PM label) is an Appium naming collision, not a #56 finish/cancel regression.
- `SpatialFunctionPanelFocusTest` is a weak names pin; a remaining Swing-only failure would need a UX SIGN on a JAR built from `a10708ed`, not a code mismatch vs PR #57 / PR #61.
