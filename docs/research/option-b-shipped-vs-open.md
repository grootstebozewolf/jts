# Inventory: MMF Option B shipped vs open (SoT tip)

Inventory only. This note does **not** decide a release bar, re-grade TAGs, or retip the epic.

## Provenance (against tip SHA)

| Item | Value |
|---|---|
| SoT branch | `origin/feature/sfa-curve-rgr` |
| **Tip SHA** | `b7394fd4ae6ec3b2e65f1c65d5872456d674f865` (2026-08-22) `Merge pull request #98 … (#97)` |
| Epic file | `EPIC_SFA_CURVE_AWARENESS.md` blob `7268d64aa4a8815b724146fc4e69ff9f0caf1e1d` |
| Epic last-touch | `6b1dbac1a251aa24e03301d2e5fc58ede9ccd7bc` (2026-08-17) `Year-1 lock: ExactCircularArc (Exact Curve Bible) (#63)` |
| Epic **Status** line cited tip | `c956b50d421c4b15eb763de5b7d67fae6a201c29` (2026-08-17) `feat(curve): HP.4 faces-after-snap stamp + N-SS Option-B crossing expand` |
| MMF Option B file | `doc/MMF_OPTION_B.md` blob `e042c0285ee732c15962b3ff1eec05ff5355d2b2` |
| MMF Option B last-touch | `f54278a18943095834e7fd7cd0967704069caad6` (2026-08-17) merge of [#61](https://github.com/grootstebozewolf/jts/pull/61) |
| PR #61 | MERGED 2026-08-17; head `bcb14fc56727f7266f76d0133c568cf3c61487b0`; merge commit `f54278a18943095834e7fd7cd0967704069caad6` |
| Supporting (epic points here; not a second source of truth) | `doc/MMF_WALKTHROUGH.md` last-touch same #61 merge; `doc/LASER_RATCHET.md` / `doc/OVERLAYNGCURVE_P2_SEAMS.md` last-touch `6b1dbac1` (same as epic) |

**SHA lag (honesty, not a bar):** `doc/MMF_OPTION_B.md` is frozen at the #61 fold. The epic Status header was later pinned to `c956b50d`. Origin SoT has moved to `b7394fd4` (33 commits after `6b1dbac1`, including Rocq FFI #64/#67 and TestBuilder extract/edit-vertex lasers #74–#98). Those later commits are **not** reflected in the MMF/epic ship tables.

Primary sources: `EPIC_SFA_CURVE_AWARENESS.md` §4.0 / §9 / named-miss list; `doc/MMF_OPTION_B.md`; PR #61 body.

---

## Contract / honesty notes (both files)

Quoted as listed; not evaluated.

From `doc/MMF_OPTION_B.md` **Contract** and `EPIC_SFA_CURVE_AWARENESS.md` **Field contract (PERF-GATE)**:

1. **Noder = Option B.** `[i,i+1]` is `SegmentKind.LINEARIZED | ARC | CERTIFIED`. Default earth is linearized. Exact consumers ask `mayCollapseToChord`. Spatial indexes may use PM-scaled chord/expanded bounds (“the allowed lie”). **PM snap must not rename kind.**
2. **No silent linearization.** `CurveLinearizationStrategy` default `LINEARIZED` **must warn**; `PRESERVE` keeps type.
3. **Laser order:** Maintainable → soundness/precision → functionality/performance. PERF-GATE slack **15%** (`t_laser ≤ 1.15 × t_chainsaw`). Do not loosen 15%.
4. **Overlay name:** `OverlayNGCurve`, never *Curved*.
5. **WKB zoo SIGN:** types **18–21** (`CRV-CLOTHOID`, `PRF-BEZIER`, `PRF-ELLIPSE`, `CRV-NURBS`) plus ISO `+1000/+2000/+3000`. Types **15–17** stay **Architect-gated**.
6. Epic: “We will not write a circular noder in this PR.” Full OV TAG stays red until OV-P2 / Bar 2. **OV-P1 is not epic DoD.**
7. Epic §4.0: N-SS path is Option B, **not** the rejected 74-file Option B *lie* framing.
8. Epic: **TB-FN is a stretch goal**, not a DoD criterion.
9. Epic: Slack still 15%. No upstream locationtech PR until dr-jts engages.

---

## A. What `doc/MMF_OPTION_B.md` lists as shipped

**Shipped meter clusters (green)** — the file names this one lump (partials included; see honesty below):

> Option B + WKB 18–21 + OFF/BUF-\*/VBF + DSF/TRI + N-AA/AL/N-SS + F-\*/B-\* + LRF-LEN/LOC + AT-S/NS + C-LIN/AREA/IP + S-\* + OV + D-AA/OP + R-CONT/PR + V-CS/CP + PRC-SN + H-CC + PLG + COV + TB-T/FN.

Expanded TAG tokens as written:

| Cluster as written | TAG tokens |
|---|---|
| Option B | `SegmentKind` / N-SS Option B spine |
| WKB 18–21 | zoo SIGN (not 15–17) |
| OFF / BUF-\* / VBF | **OFF**, **BUF-1**, **BUF-N**, **BUF-NEG**, **VBF** |
| DSF / TRI | **DSF**, **TRI** (walkthrough/epic split TRI-DT / TRI-VR) |
| N-AA / AL / N-SS | **N-AA**, **N-AL**, **N-SS** |
| F-\* / B-\* | Phase 1 **F-CP / F-MC / F-MS / F-RD**; Phase 2 **B-CP / B-MS / B-CC** |
| LRF-LEN / LOC | **LRF-LEN**, **LRF-LOC** |
| AT-S / NS | **AT-S**, **AT-NS** |
| C-LIN / AREA / IP | **C-LIN**, **C-AREA**, **C-IP** |
| S-\* | **S-DP**, **S-VW**, **S-TP** |
| OV | **OV** (see partial honesty in epic) |
| D-AA / OP | **D-AA**, **D-OP** (epic also ships **D-PT**) |
| R-CONT / PR | **R-CONT**, **R-PR** |
| V-CS / CP | **V-CS**, **V-CP** |
| PRC-SN | **PRC-SN** |
| H-CC | **H-CC** (epic also ships **H-CV**) |
| PLG | **PLG** |
| COV | **COV** |
| TB-T / FN | **TB-T**, **TB-FN** |

**Folded pins onto tip** (MMF file): **#55** `logoClothoid`, **#52** write-flatten honesty, **#40** `ClothoidOverlay`, **#26** HP.1.

Walkthrough (supporting): green meters 36/36 on 2026-08-17 smoke; phases 0–4 accept; Phase 5/6 code+prose.

PR #61 body: “Phases 0–4 accept; Phase 5/6 code+prose (await human UX/PDF SIGN). Remaining red: **D-HF** only.”

---

## B. What `doc/MMF_OPTION_B.md` lists as still open

### Still red by design

- **D-HF** — full TAG keep `fail()`; “apex closed-form + general curve densify honesty landed”.

### Named misses (Phase 2 accept — explicit)

- **CLOTHOID-FRESNEL**
- **VBF** arc-offset laser
- **PLG** CurvePolygon faces
- **HP.1** wrong-ring walk
- OverlayNGCurve **R2** stamps
- WKB **15–17** Architect-gated

### HOLD (not TAG-green, not closed)

- Issue [#56](https://github.com/grootstebozewolf/jts/issues/56): `CurvePolygonToolTest` 16/16 on tip; leave open until PO **UX SIGN** on pin JAR.
- Issue [#60](https://github.com/grootstebozewolf/jts/issues/60): Exec/`currentFunc` fix landed; await **UX SIGN**.
- Guides [#47](https://github.com/grootstebozewolf/jts/pull/47): LaTeX rebuild CONFLICTING — do not force-merge Amyuni PDF binaries. In-tree `doc/*.pdf` stubs remain until `cd doc/latex && make` is signed off.
- Do not open `locationtech/jts` PRs from this branch without PO word.
- PR #61 also: do not force-fold **#28 / #41** (would delete tip MMF work).

---

## C. What the epic lists as shipped (MMF fold)

Source: `EPIC_SFA_CURVE_AWARENESS.md` **§4.0** (MMF Option B fold, #61, 17 Aug 2026) and **§9** phase list.

### §9 TAGs marked **Shipped (MMF #61)**

| TAG | Epic honesty note (same bullet) |
|---|---|
| **D-PT / D-AA / D-OP** | analytical point-arc / arc-arc / DistanceOp green meters |
| **C-LIN / C-AREA / C-IP** | — |
| **BUF-1 / BUF-N / BUF-NEG** | open-arc corridor + stadium / N-member CompoundCurve |
| **OFF** | concentric single-arc OffsetCurve |
| **H-CV / H-CC** | convex hull kits + ConcaveHull densify sites (hull fraction) |
| **S-DP / S-VW / S-TP** | 3-pt CircularString identity preserved |
| **AT-S / AT-NS** | — |
| **LRF-LEN / LRF-LOC** | — |
| **DSF** | densifier → `toLinear` |
| **N-AA / N-AL** | `CurveIntersection` public utility |
| **COV** | `CurveCoverageUnion` keeps exterior CIRCULARSTRINGs |
| **PRC-SN** | preserve CircularString when snapped centre on-grid |
| **TRI-DT / TRI-VR** | densify curve sites |

§10: Phase 1 (Foundations) — **done on #7 / MMF #61** (**F-CP / F-MC / F-MS / F-RD**).

### §4.0 buckets marked shipped (with TAG honesty column)

| Bucket | Summary as listed | TAG honesty as listed |
|---|---|---|
| `arch:` | Option B `SegmentKind` (`LINEARIZED` / `ARC` / `CERTIFIED`); `CircularNodedSegmentString`; `IntersectionAdder.mayCollapseToChord`; OverlayNG prepared edges. Index may lie under `PrecisionModel`. | N-SS path is Option B, not the rejected 74-file Option B *lie* framing. |
| `arch:` | `CurveLinearizationStrategy`: default `LINEARIZED` always logs a warning; `PRESERVE` keeps type. Wired through `CurveOps.linearise`. | No silent flatten. |
| `feat:` | **WKB 18–21** SIGN greenfield: `CRV-CLOTHOID` / `PRF-BEZIER` / `PRF-ELLIPSE` / `CRV-NURBS` (+ ISO `+1000/+2000/+3000`). Factory stubs; `CurveWKBWriter`; core writer refuses flatten. 15–17 still Architect-gated. | I/O zoo **partial–full** for 18–21 types; **ops still mostly chordsaw**. |
| `feat:` | **OFF** shipped: public `OffsetCurve.getCurve` concentric single-arc (left-of-direction). | **Full OFF TAG for 3-pt CS. Multi-arc still chordsaw.** |
| `feat:` | **BUF-1 / BUF-NEG** shipped: open-arc corridor `CurvePolygon`; `\|d\|≥R` empty. **BUF-N** shipped: stadium dilation + open two-member line+arc corridor with round joins/caps. | **Multi-arc (>2) CompoundCurve corridors still chainsaw.** |
| `feat:` | **R-OV** OverlayNG-for-circles: H-SHELL-N-MIXED via `OverlayNGCircle` + `CurveSegmentString` bridge; BiteVsHole / TwoHoleOverlay folded. | Named R2 leftovers shrink; **full OV still not green**. |
| `feat:` | **HP.2 / HP.3** `CurveHotPixel` + `CurveHotPixelSnap` (PM-scale arc∩pixel; shared snapped ray stamp). | **Not HP.4 faces; not core HotPixel rewrite.** |
| `arch:` | `CurveSegmentDcel` (P2.5.7) package-private; **COV** `CurveCoverageUnion`; **PLG** densifies on add (faces remain Polygon). | **D-HF full TAG still red** (apex closed-form exists). |
| `fix:` | **TB-FN #60**; **TB-FN badges** ●/◯/✕; strategy picker LINEARIZED/PRESERVE; warn sink → Log tab. | Await UX SIGN on pin JAR. |
| `fix:` | **VBF honesty**: `VariableBuffer` + TestBuilder densify via equal-arc-length `CurveOps.lineariseArcLength` (warn). | Full arc-preserving variable offsets still optional laser. |
| `feat:` | **DSF / TRI / H-CC / LRF-LOC / C-IP / PRC-SN / V-CP / N-SS / R-PR** densify or Option B honesty paths shipped (`CurveAwarenessGreenMetersTest`). | Meters deleted from red suite. |
| `docs:` | `doc/latex/` cherry-picked from #47. Amyuni PDF binaries not force-swapped. | Phase 6 partial; `make` + UX SIGN still HOLD. |
| `test:` | **#56 locks**: `CurvePolygonToolTest` 16/16 — auto-close to `CURVEPOLYGON`, never `POLYGON` flatten. | HOLD issue close until PO UX SIGN. |

Pre-MMF, still listed as landed on #7 (historical tip `210f1b16`, §4.1): **WKB 8–12**; OverlayNGCurve closed-form kits (R0 / R1 / R1.5 / R1.6 / R1.7 / R-LL / R-AA / D4); **R-CONT** / **R-PR** disc cells; **H-CV / H-CC** closed-form subsets; **D-HF** two-pair closed form; **LEC** typed subset; **M-AREA-CP** disc 25π; PERF-GATE 15%.

---

## D. What the epic lists as partial (not full-TAG green)

| TAG | Epic wording |
|---|---|
| **M-AREA-CP** | Partial (circular discs): disc `CurvePolygon` area is 25π. Keep the spec method. |
| **D-HF** | Partial (half-red): public DHD apex + disc closed forms; general pairs densify curve package inputs. Full TAG keep `fail()` in `CurveAwarenessSpecTest`. |
| **VBF** | Partial: arc-length densify + warn shipped; arc-preserving offset laser still optional. |
| **N-SS** | Partial (Option B): `SegmentKind` + `CircularNodedSegmentString` + OverlayNG prepared edges shipped. Full hierarchy rewrite still **OV-P2 / Bar 2** (different epic). |
| **OV** | Partial: OverlayNGCurve kits + OverlayNGCircle MIXED + ClothoidOverlay 0-node. **Full OV TAG not green.** |
| **R-PR / R-CONT / R-EQ** | Partial→stronger (MMF #61): disc DE-9IM + contains meters; arc-vs-chord `equalsExact` shipped earlier on #7. |
| **PLG** | Partial (MMF #61): densify on add (faces remain Polygon; CurvePolygon faces laser open). |
| **TB-T / TB-FN** | Partial→stronger (MMF #61): draw tools + badges + WarnSink/status; await UX SIGN #56/#60. |
| **LEC** (§4.1) | Partial: typed obstacle distance + certified disc; public TAG is wider. |
| WKB 18–21 ops | I/O zoo partial–full; ops still mostly chordsaw. |

Phase 2 bullets **without** a Shipped/Partial annotation in §9 (not in the MMF named-miss list either): **M-LEN-CS / M-LEN-CC**, **M-DIM**. **B-CP / B-MS / B-CC** are unannotated in §9 but appear in MMF’s shipped `F-*/B-*` cluster.

---

## E. What the epic lists as still open (named misses + HOLD)

### Named misses still open (explicit list, §9)

- **CLOTHOID-FRESNEL** — clothoid–circle / clothoid–line node (never chord-flatten).
- **D-HF full TAG** — general public Hausdorff beyond apex/disc closed forms.
- **VBF arc-offset laser** — preserve CircularString offsets under variable distance.
- **PLG CurvePolygon faces** — Polygonizer still emits Polygon.
- **HP.1 wrong-ring walk** — local curvature leave-angle order (pin; needs HotPixel).
- Remaining OverlayNGCurve **R2 stamps** as listed in §4.1 / ratchet.
- WKB **15–17** Architect-gated.
- RocqRefRunner SQL/MM suite for public Curve predicates (optional).
- Visual QA: await UX SIGN on #56 / #60 pin JAR.

Epic closing line of §9: remaining full-TAG red in `CurveAwarenessSpecTest` is currently **D-HF only**.

### R2 / overlay named leftovers the epic still points at

From §4.1 (historical OV-P1 kits, “still accurate for OV-P1”) plus `doc/OVERLAYNGCURVE_P2_SEAMS.md` @ same last-touch `6b1dbac1`:

- Full **OV** TAG — general arrangement still Option B unfinished; do not mark full OV green.
- **H-ANNULUS-TANGENT** — stay refuse punch; public path chordsaw + named stamp.
- **H-DISC (open arcs)** — not filled discs; lineal R-AA.
- **N-SS full hierarchy** — 74-file rewrite deferred (OverlayNGCircle + Option B expand only).
- **P2.5.4** — stamp, not HotPixel walk.
- **HP** seam — stamped `SHARED_SNAPPED_RAY`; not HP.5.

`doc/LASER_RATCHET.md` (cited from epic Status): sequence **M.5 → ML.2 → HP.4 → N-SS expand → stop** landed @ `c956b50d`. Holds listed there: **M.4 · R.3 · ML.3 catalog · HP.5 · 74-file N-SS · full D-HF TAG**.

### Deferred sibling epics (§3) — tracked, not pretended absent

- 3-D solids (`POLYHEDRALSURFACE`, `TIN`) — no 3-D semantics here.
- Elliptic arcs (`ELLIPSARC`).
- Z / M ordinate interpolation across densified arcs.
- **OV-P2 / Bar 2** — circular noder + arrangement + general PLG / COV. Different epic. Do not start.

### Epic DoD still open (epic-level, not MMF meter)

1. `CurveAwarenessSpecTest` empty (today: **D-HF** remains).
2. Six curve types round-trip through every public `Geometry` operation without flat output where curve-preserving output is possible.
3. WKB 8–12 — epic says **satisfied on #7** (via #8).
4. Release note covering `equalsExact` and other user-visible shifts.

---

## F. Internal contradictions (doc vs doc at this blob; not a bar)

These are honesty notes about the **texts themselves** at tip `b7394fd4`. Inventory only.

1. **Cited tip vs origin tip.** Epic Status and ratchet/seams pin `c956b50d`. SoT tip is `b7394fd4`. MMF Option B file was not edited after #61 merge `f54278a1`.
2. **§4.0 vs Status / ratchet on HP.4.** §4.0 honesty: HP.2/HP.3 “**Not HP.4 faces**”. Status line and `doc/LASER_RATCHET.md` say **HP.4** landed at `c956b50d`.
3. **§5 vs §9/§12 on the spec class.** §5 still says the spec class “Still has **all 49 `fail()` methods**” and that the delete-on-ship meter **froze**. §9 / §12 say remaining full-TAG red is **D-HF only**. Cross-check at tip (not a re-grade): `modules/curve/src/test/java/org/locationtech/jts/spec/curveawareness/CurveAwarenessSpecTest.java` has a single `fail("D-HF: …")` — matches §9/§12, not §5.
4. **§4.1 “Still open” vs §9 Shipped.** §4.1 (labelled historical tip `210f1b16`) still lists as open: public **N-AA / N-AL**; mixed **BUF-N** >2; **D-HF** general + Fréchet; full **LEC**; RocqRefRunner; **R-EQ**; **PLG / COV** “TAGs open”; “most remaining Phase 2 / 4 / 7 meters”. §9 later marks N-AA/N-AL, BUF-\*, COV, and most Phase 2–7 meters shipped, PLG partial, remaining red **D-HF only**.
5. **R-EQ.** §9: arc-vs-chord `equalsExact` “shipped earlier on #7”. §7 Risks: `equalsExact` semantic change “**Still open**” (release-note / behaviour-change question).
6. **AT-NS.** §9: **Shipped (MMF #61)**. §7: “Decide before AT-NS lands” (sheared arc type identity).
7. **HP.1 pin vs named miss.** MMF: folded pin **#26 HP.1**. Same file + epic named-miss list: **HP.1 wrong-ring walk** still open.
8. **VBF / PLG / OV / N-SS / TB-\*** sit in MMF’s “shipped meter clusters (green)” **and** in partial/named-miss/HOLD lists. Epic §4.0/§9 is the more specific honesty split (densify/warn/kit shipped; laser/full TAG open).
9. **D-PT.** Epic §9 ships **D-PT**. MMF cluster writes **D-AA/OP** only.
10. **H-CV.** Epic §9 ships **H-CV / H-CC**. MMF cluster writes **H-CC** only.

---

## G. Compact TAG rollup (as the two files list them)

Statuses below are **the documents’ labels**, not an independent verdict.

| TAG | MMF_OPTION_B | Epic §4.0 / §9 |
|---|---|---|
| F-CP / F-MC / F-MS / F-RD | shipped cluster `F-*` | Phase 1 done on #7 / #61 |
| M-LEN-CS / M-LEN-CC / M-DIM | (not named) | unannotated in §9 |
| M-AREA-CP | (not named) | **Partial** (discs) |
| B-CP / B-MS / B-CC | shipped cluster `B-*` | unannotated in §9 |
| V-CS / V-CP | shipped cluster | V-CP in §4.0 shipped-honesty row |
| D-PT | (not named) | **Shipped** |
| D-AA / D-OP | shipped cluster | **Shipped** |
| D-HF | **still red** (by design) | **Partial / named miss / only remaining spec `fail()`** |
| C-LIN / C-AREA / C-IP | shipped cluster | **Shipped** |
| BUF-1 / BUF-N / BUF-NEG | shipped cluster `BUF-*` | **Shipped**; honesty: multi-arc (>2) corridors chainsaw |
| OFF | shipped cluster | **Shipped**; honesty: 3-pt CS full, multi-arc chordsaw |
| VBF | shipped cluster **and** named miss (arc-offset laser) | **Partial** |
| H-CV | (not named) | **Shipped** |
| H-CC | shipped cluster | **Shipped** |
| S-DP / S-VW / S-TP | shipped cluster `S-*` | **Shipped** (3-pt CS identity) |
| AT-S / AT-NS | shipped cluster | **Shipped** (§7 still flags AT-NS type question) |
| LRF-LEN / LRF-LOC | shipped cluster | **Shipped** |
| DSF | shipped cluster | **Shipped** |
| N-AA / N-AL | shipped cluster | **Shipped** (`CurveIntersection`) |
| N-SS | shipped cluster | **Partial** Option B; full hierarchy OV-P2 |
| OV | shipped cluster | **Partial**; full TAG not green |
| R-PR / R-CONT | shipped cluster | **Partial→stronger** |
| R-EQ | (not in cluster) | Partial→stronger / §7 still open |
| PLG | shipped cluster **and** named miss (CurvePolygon faces) | **Partial** |
| COV | shipped cluster | **Shipped** |
| PRC-SN | shipped cluster | **Shipped** |
| TRI-DT / TRI-VR | shipped cluster `TRI` | **Shipped** |
| TB-T / TB-FN | shipped cluster | **Partial→stronger**; UX SIGN HOLD |
| LEC | (not named) | §4.1 **Partial** |
| WKB 8–12 | (pre-MMF) | landed on #7 via #8 |
| WKB 18–21 | shipped cluster | shipped I/O; ops mostly chordsaw |
| WKB 15–17 | named miss (Architect-gated) | named miss (Architect-gated) |
| CLOTHOID-FRESNEL | named miss | named miss |
| HP.1 | named miss (pin #26 folded) | named miss |
| HP.2 / HP.3 | (folded via MMF spine) | shipped; “not HP.4” in §4.0 |
| HP.4 | (not in MMF file; post-#61) | Status/ratchet: landed @ `c956b50d` |
| R2 / H-ANNULUS-TANGENT | named miss “R2 stamps” | named miss |
| RocqRefRunner SQL/MM | (not in named-miss list) | named miss (optional) |
| #56 / #60 UX SIGN | HOLD | HOLD |
| #47 PDF `make` | HOLD | HOLD |

Fréchet is named only in epic §4.1 “Still open”, not in the §9 named-miss list or in `doc/MMF_OPTION_B.md`.
