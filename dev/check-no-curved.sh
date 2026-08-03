#!/usr/bin/env bash
# Forbid the 'Curved' stem in source identifiers, package paths, and artifact ids.
# Decision: locationtech/jts#1193 — follow the NetTopologySuite 2022 'Curve' (no "d")
# naming. Rationale + exclusions live in RENAME_CONTRACT.md at the repo root.
#
# Invoke from the repo root: bash dev/check-no-curved.sh
# Exit code: 0 on clean tree, 1 on any violation.

set -u

excludes=(
  ':!modules/io/ora/src/main/java/org/locationtech/jts/io/oracle/OraReader.java'
  ':!modules/core/src/test/java/org/locationtech/jts/operation/buffer/OffsetCurveTest.java'
  ':!RENAME_CONTRACT.md'
  ':!dev/check-no-curved.sh'
)

violations=0

check () {
  local label="$1" pattern="$2"
  local hits
  hits=$(git grep -nE "$pattern" -- "${excludes[@]}" 2>/dev/null || true)
  if [ -n "$hits" ]; then
    echo "FAIL: $label"
    echo "$hits"
    echo
    violations=$((violations + 1))
  fi
}

check "F1 Curved<Cap> identifier (e.g. CurvedGeometryFactory, FooCurvedBar)" 'Curved[A-Z]'
check "F2 .curved /curved \\curved package or path segment"    '[./\]curved\b'
check "F3 jts-curved Maven artifactId or module name"          '\bjts-curved\b'

if [ "$violations" -ne 0 ]; then
  echo "Found $violations banned-stem group(s). Rename to 'Curve' or add to the"
  echo "allowlist in dev/check-no-curved.sh with a one-line reason."
  echo "See RENAME_CONTRACT.md."
  exit 1
fi

echo "OK: no 'Curved' stems found"
