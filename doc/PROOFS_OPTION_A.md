# Proofs Option A — superseded for architecture

Architectural source of truth is now
[`EXACT_CURVE_BIBLE.md`](EXACT_CURVE_BIBLE.md).

`ExactCircularArc` is the privileged Year-1 primitive in
`org.locationtech.jts.algorithm.exactcurve`. `OrientableSegment` is a
thin optional adapter and must compose Exact* types, not re-implement
geometry.

This file keeps the metric-cell notes that are still useful for the
1M handover.

## Types (Year 1 lock)

- `ExactCurve` — thin protocol: start, end, length, `pointAt`,
  `toLinear`, `isExact`. Not a rich base.
- `AngleBetween` — only `% 2π` / sweep owner
- `ExactCircularArc` — 3-control window + protocol
- `exactarc.AngleBetween` — deprecated alias for B-team imports

## Never (bible §6)

- Silent linearise flagged exact
- 74-file `SegmentString` rewrite
- OrientableSegment as the primary curve vocabulary
- Walk `SHARED_SNAPPED_RAY`

## Handover

`ExactArcOptionAMillionTrialTest` — N=1e6; L1 is analytic n-gon
`n·2r·sin(θ/2n)`. Report: `doc/PROOFS_OPTION_A_HANDOVER.md`.
