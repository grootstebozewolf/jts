# JTS TestRunner (CLI)

Entry: `org.locationtech.jtstest.testrunner.JTSTestRunnerCmd`  
Module: `modules/tests` (also packaged with the app distribution).

## Run

```bash
# From a built tree:
mvn -pl modules/tests -am package -DskipTests -Dcheckstyle.skip=true -Dpmd.skip=true
java -cp "modules/tests/target/classes:modules/core/target/jts-core-*.jar:..." \
  org.locationtech.jtstest.testrunner.JTSTestRunnerCmd -files <xml...>
```

Or use the TestBuilder **File → Save As XML** run file, then feed it to the runner.

## Stabilization notes

- Exit code non-zero on failed cases.
- Curve geometries in XML must use CurveWKBWriter paths (see `CurveWKBExportHonestyTest` on this tree).
- Appium IDs apply to TestBuilder GUI only; TestRunner has no Swing surface.
