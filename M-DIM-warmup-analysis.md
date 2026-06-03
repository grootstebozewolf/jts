# #1195 Next TAG Identification (warmup): risk/cost analysis

**Date**: current session (post PRC-SN RGR + review/sign-off comment on epic)

**Source of guidance**:
- EPIC_SFA_CURVE_AWARENESS.md §6 (cross-module impact), §7 (risks), §9 (phases), §10 (suggested order)
- TRIAGE_NTS_JTS_ISSUES.md explicit "RGR next candidates (low risk/cost after F-CP): B-CC ..., **M-DIM (already partial)**, M-LEN-CS ..."
- CurveAwarenessSpecTest.java live meter (red methods = open TAGs)
- Current tree state (PRC-SN-rgr + parallel rgr branches for prior TAGs)
- Proofs risk/cost ordering precedent (cheapest/safest first)

**Live remaining TAGs (from meter, ~48 reds; only M-AREA-CP red deleted/shipped)**:
- Phase 1 F-*: F-CP, F-MC, F-MS, F-RD (structural; some spikes/branches exist)
- Phase 2: M-LEN-CS, M-LEN-CC (reds still), M-DIM, B-CP, B-MS, B-CC (reds), V-CP, V-CS (reds)
- Phase 3 D-*/C-*
- Phase 4 BUF-*/OFF/VBF/H-*/S-*/AT-*/LRF-*/DSF
- Phase 5 N-AA/N-AL/N-SS (core touch per epic §6)
- Phase 6 OV/R-*/PLG/COV (high, depend on 5)
- Phase 7: TRI-*, TB-*, PRC-SN (red kept per RGR convention)

**Risk factors** (from epic):
- Core changes: N-*/N-SS, PLG, DSF, F-RD (hooks?), PRC-SN (done via utility). High review bar.
- §7 risks: AT-NS (ellipse, getGeometryType lie), R-EQ (semantic change, needs release note), N-AA/N-AL perf (indexing arcs), Z/M policy on densify outputs, backwards compat post-F-CP.
- Complexity: new heavy algos (full buffer, overlay, noding, distance op).
- Proofs coverage: arc primitives (#64), buffer (#65), snap/overlay (#66 - we used for PRC-SN).

**Cost factors**:
- Reuse: existing CircularArcs, CurvedArea (sector), structuralRings/curvedRings (on tree + B-CP branch), CurvedPrecisionReducer, WKT, toLinear anchors, isEmpty checks.
- New math/tests: high for D-OP/HF, BUF, N-*, OV, R-PR; low for guards/overrides on length/area/boundary/dim/centroid (using prior primitives).
- Branch momentum: many rgr/spikes done (B-CP-rgr, B-MS-rgr, F-RD-rgr, M-LEN-*-rgr, V-*-rgr, B-CC-rgr etc.). PRC-SN + M-AREA hardened+reviewed on their lines.
- Verification: green structural/ area /reducer tests exist; adversarial/RefRunner/CurveRefRunner pattern ready for harden; proofs vectors/oracles for some.

**Risk/cost scoring for top candidates (lowest first)**:
1. **M-DIM** (empty curved dim/coordinate-dim guards):
   - Risk: **Minimal/zero**. jts-curved only. No core. No new math. No §7 risks. "Already partial" per triage (smoke asserts pass via inheritance, just needs explicit guard vs refactor regression).
   - Cost: **Lowest** (minutes to hours). Add @Override getDimension()/getCoordinateDimension() in CircularString (1), CompoundCurve (1), CurvePolygon (2), MultiCurve/MultiSurface as needed. Handle EMPTY + normal cases. Update javadocs. Green test trivial (reuse red WKTs + asserts; perhaps in CompoundCurveStructureTest or CurvePolygonAreaTest).
   - Proofs: not required (trivial).
   - Position: Immediately after M-AREA-CP shipped note in meter/Phase 2. Independent of most.
   - Why next: Matches triage "low risk/cost" callout explicitly. Warmup after heavy PRC-SN + M-AREA harden/review. Quick meter win, documents contract. Can be done on new branch, RGR, keep red fail.
   - Epic alignment: Listed in Phase 2 right after M-*/B-*.

2. **B-CP** (or B-MS):
   - Risk: Low (curved-only per §6 table; F-CP partial via curvedRings already in tree).
   - Cost: Low-medium. **Existing RGR branch** feature/sfa-curve-B-CP-rgr with commit "RGR for TAG B-CP": simplest getBoundary override using structuralRings(), DRY helper (also for copy/reverse), allLinearRings soundness for degen, green verif in (compiled) structural spec, boundary hardening via orient ref. Similar for B-MS. Can resume, review/harden (add adversarial if needed), integrate like M-AREA.
   - From peek: handles 0-hole (return copy of structural ring preserving subtype), holes (MultiCurve unless pure linear degen -> super MLS).
   - Red test expects CompoundCurve for simple case, MultiCurve preserving for MS.
   - Ties to proofs (orient refSign used in prior harden).
   - Why good: Leverages pre-existing work (reduces cost), continues Phase 2 boundary wave (B-CC had similar), low risk.

3. Other lowish: H-CV (extreme points, reuse controls), C-LIN/C-AREA (reuse arc-length + sector area from M-LEN/M-AREA primitives on branches), D-PT (reuse circle/angle/pointOnArc math).

**Higher risk/cost (defer)**: F-RD (possible core hooks), N-*/Phase5/6 (core + perf + complexity per triage), AT-NS (explicit §7 risk), DSF/PLG (core), full BUF (heavy, proofs #65 pending?).

**Recommendation for next (warmup)**: **M-DIM**

- Cheapest/safest per triage doc + epic structure.
- Perfect warmup: small, isolated, builds confidence post-PRC-SN, no ambiguity in impl approach (simple guards + tests).
- Then immediately follow with B-CP (resume its rgr branch for efficiency).
- All prior pattern followed: new feature/sfa-curve-M-DIM-rgr (I created one in this session as prep), RGR (red stays), proofs where makes sense (none here), green + meter, optional harden.
- After: can parallel other low (centroids, hulls) or pick up B-CP/V- integration if wanted before bigger phases.
- Branch created locally: feature/sfa-curve-M-DIM-rgr (on top of current PRC-SN state + header fix).

**Next actions if proceed**:
- On the M-DIM branch: implement guard(s), add/ extend green test, update any javadocs/epic notes, run curved tests (non-spec), verify meter still lists it red until ready to ship.
- Post review comment or update triage if significant.
- Then pick B-CP or next lowest.

This keeps "outratio by two orders" momentum with safe, incremental wins + cross-verif.

## Implementation complete (RGR)

- Overrides added + javadocs in 5 classes.
- Green test added + passes (8/8 in CompoundCurveStructureTest).
- Spec updated with "implemented" note (red fail kept).
- mvn tests: structure clean; M-DIM smoke asserts now succeed (only TAG fail in meter).
- Branch pushed: https://github.com/grootstebozewolf/jts/tree/feature/sfa-curve-M-DIM-rgr
- Per epic: ready for PR; red delete only on ship commit.

Next per prior: B-CP resume or other low risk/cost.
