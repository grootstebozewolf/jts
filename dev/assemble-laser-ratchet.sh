#!/usr/bin/env bash
# Assemble doc/laser-ratchet.json from PerfGate JSONL.
# Gates emit target/laser-ratchet/rows.jsonl (stdout-only is the bug).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
OUT="${1:-$ROOT/doc/laser-ratchet.json}"
TIP="${LASER_RATCHET_TIP:-$(git rev-parse HEAD)}"
DIR="${LASER_RATCHET_DIR:-$ROOT/target/laser-ratchet}"
ROWS="$DIR/rows.jsonl"
if [[ ! -f "$ROWS" ]]; then
  echo "missing $ROWS — run Year-1 PerfGate tests first" >&2
  exit 1
fi
CP="$ROOT/modules/core/target/test-classes"
if [[ ! -d "$CP" ]]; then
  echo "compile jts-core tests first (mvn -pl modules/core test-compile)" >&2
  exit 1
fi
IMPORTED="${LASER_RATCHET_IMPORTED:-$(date -u +%Y-%m-%d)}"
BRANCH="${LASER_RATCHET_BRANCH:-feature/sfa-curve-rgr}"
PR="${LASER_RATCHET_PR:-}"
exec java -Dlaser.ratchet.tip="$TIP" -Dlaser.ratchet.dir="$DIR" \
  -Dlaser.ratchet.imported="$IMPORTED" \
  -Dlaser.ratchet.branch="$BRANCH" \
  -Dlaser.ratchet.pr="$PR" \
  -cp "$CP" test.jts.perf.LaserRatchetSink assemble "$OUT" "$ROWS"
