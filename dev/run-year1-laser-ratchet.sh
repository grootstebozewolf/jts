#!/usr/bin/env bash
# Year-1 circular PerfGates → doc/laser-ratchet.json (Proofs-schema feed).
# ClothoidHalley / Year-2 zoo / N-SS / SHARED_SNAPPED_RAY walk are HOLD — not run.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export LASER_RATCHET_DIR="${LASER_RATCHET_DIR:-$ROOT/target/laser-ratchet}"
export LASER_RATCHET_TIP="${LASER_RATCHET_TIP:-$(git rev-parse HEAD)}"
mkdir -p "$LASER_RATCHET_DIR"
rm -f "$LASER_RATCHET_DIR/rows.jsonl"

# Class names only (surefire 2.15 cannot mix Class#method with a list).
# Do not add ClothoidHalleyPerfGateTest.
GATES="LargestEmptyCirclePerfGateTest\
,DirectedHausdorffDistancePerfGateTest\
,DiscreteFrechetDistancePerfGateTest\
,DiscreteHausdorffDistancePerfGateTest\
,CurveOpsDistConPerfGateTest\
,MultiCurvePerfGateTest\
,ReverseDispatchPerfGateTest\
,CurveWKBPerfGateTest\
,OverlayNGCurvePerfGateTest\
,DistanceConstructionPerfGateTest\
,ExactArcOptionAMillionTrialTest\
,PredicateOptionBMillionTrialTest"

mvn -pl modules/core,modules/curve,modules/app -am test \
  -Dtest="$GATES" \
  -DfailIfNoTests=false \
  -Dcheckstyle.skip=true \
  -Dpmd.skip=true \
  -Dlaser.ratchet.dir="$LASER_RATCHET_DIR" \
  -Dlaser.ratchet.tip="$LASER_RATCHET_TIP"

exec bash "$ROOT/dev/assemble-laser-ratchet.sh" \
  "${1:-$ROOT/doc/laser-ratchet.json}"
