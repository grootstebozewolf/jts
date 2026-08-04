# Curve naming contract

## Decision

JTS adopts the noun **`Curve`** (not the adjective `Curved`) as the canonical
stem for curve-aware geometry types, packages, artifacts, and APIs. This
mirrors the choice NetTopologySuite made in 2022 for its satellite extension
repo `NetTopologySuite.Curve`.

Discussion: [locationtech/jts#1193](https://github.com/locationtech/jts/discussions/1193).

## Why noun, not adjective

`Curve` names a first-class geometry family in OGC SFA / ISO 19125-2 — the
sibling of `Surface`, `Point`, etc. `Curved` reads as a modifier and bakes the
wrong abstraction level into the API surface. Once factories, readers, writers,
and operators all carry the stem, the difference compounds: `CurveGeometryFactory`
is a factory for curves; `CurvedGeometryFactory` reads as "a curved factory",
which is meaningless.

## What's banned

The CI naming guard (`dev/check-no-curved.sh`) rejects three patterns:

| # | Pattern | Catches |
|---|---|---|
| F1 | `Curved[A-Z]` | Any identifier containing `Curved` followed by a capital letter — at the start (`CurvedGeometryFactory`) or in the middle (`BufferCurveWithParamsCurvedTest`, `test_distanceOpForCurvedInputs`). Case-sensitive: the lowercase English word "curved" in comments stays allowed. |
| F2 | `[./\\]curved\b` | Package segments (`…geom.curved;`), import paths, directory paths (`modules/curved/`), JPMS module names. Leading char must be `.`, `/`, or `\`; trailing is a word boundary so `;`, `<`, `"`, end-of-line all qualify. |
| F3 | `\bjts-curved\b` | The old Maven artifactId; appears in `pom.xml`, dependency declarations, READMEs. |
| F4 | `>curved<` | A Maven `<module>curved</module>` entry. Added 2026-08-03 because F2 could not see it: the stem has no `.`, `/` or `\` in front of it there, so the guard reported OK while `modules/pom.xml` still named a directory that had been renamed, and the build broke where the guard had passed. |

## What's *not* banned (by design)

- **English-word "curved"** in comments, JavaDoc, or prose. F1 requires a
  following capital (identifier shape); F2 requires a path or package delimiter.
  Writing `// densify the curved boundary` in a new comment is fine. We're
  policing names, not vocabulary.
- **Git history.** Existing commits with `[curved]` prefix or touching the old
  `modules/curved/` path stay. No rebase, no force-push. New commits from the
  rename forward use `[curve]`.
- **The `CIRCULARSTRING` legacy branch.** Predates the module.

## Allowlist (upstream JTS files)

These files contain the literal word `Curved`/`curved` for legitimate
upstream-JTS reasons and are explicitly excluded from the guard:

| Path | Reason |
|---|---|
| `modules/io/ora/src/main/java/org/locationtech/jts/io/oracle/OraReader.java` | Oracle SDO uses `Curved` in its own geometry type naming. Out of scope. |
| `modules/core/src/test/java/org/locationtech/jts/operation/buffer/OffsetCurveTest.java` | The English word "curved" appears in JavaDoc prose. Upstream test, not a curve-awareness type. |

Adding to the allowlist requires a one-line reason in
`dev/check-no-curved.sh` alongside the path.

## Rollout (2026-05-15)

1. Rename commit on `feature/sfa-curve-buffer-spike` root: directory move
   (`modules/curved/` → `modules/curve/`), package renames, class renames,
   pom.xml updates, EPIC doc cleanup. Same commit lands this contract and the
   guard.
2. Stabilise build + `mvn -pl modules/curve test -Dtest=CurveAwarenessSpecTest`
   on the root.
3. Forward-merge into each active spike branch
   (`F-CP`, `F-CP-optionA/B/C`, `F-MC-F-MS`, `AT-NS`, `R-EQ`, `PRC-SN`,
   `compoundcurve-members`, `extension-points`, `multisurface-function`,
   `testbuilder-ui`, `tin-tool`, `toLinear-densification`, `triangle-tool`).
4. Forward-merge into `feature/sfa-curve-clothoid-playground` last; it's held
   pending upstream feedback on PR #1.

## Module README addendum (to apply during the rename commit)

Add the following paragraph to `modules/curve/README.md` (which is
`modules/curved/README.md` pre-rename) so the rule is visible at the module
entry point:

> **Naming.** This module uses the noun **`Curve`** (no "d") as the canonical
> stem — `CurveGeometryFactory`, `CurveWKTReader`, package
> `org.locationtech.jts.geom.curve`, etc. The CI naming guard
> (`dev/check-no-curved.sh`) rejects the legacy `Curved` stem in new code. See
> `RENAME_CONTRACT.md` at the repo root for the full rule and allowlist.

## Escalation

If the surface grows (multiple near-miss names, e.g. `Curvature*`,
`CurveAware*`, or contributors keep tripping the guard), promote the rule from
`dev/check-no-curved.sh` to a Checkstyle `RegexpSinglelineJava` rule. Custom
analyzers (Error Prone, Roslyn) are out of scope until the module ships
publicly.
