# JTS TestRunner (CLI)

Entry: `org.locationtech.jtstest.testrunner.JTSTestRunnerCmd`  
Module: `modules/tests`.

## Build + run (parity smoke)

```bash
# From a worktree root:
mvn -pl modules/tests -am install -DskipTests -Dcheckstyle.skip=true -Dpmd.skip=true
mvn -pl modules/tests -am dependency:build-classpath \
  -Dmdep.outputFile=/tmp/jts-tr.cp -DincludeScope=runtime -q

CP="$(cat /tmp/jts-tr.cp):modules/tests/target/classes"
for m in core tests app io/common curve; do
  j=$(ls modules/$m/target/jts-*.jar 2>/dev/null | grep -vE 'sources|javadoc' | head -1)
  [ -n "$j" ] && CP="$CP:$j"
done

java -cp "$CP" org.locationtech.jtstest.testrunner.JTSTestRunnerCmd \
  -files modules/tests/src/test/resources/testxml/general/TestSimple.xml
```

Expected: `44 cases with 44 tests -- 44 passed` (exit 0).

## Notes

- Exit code non-zero on failed cases.
- Curve geometries in XML must use CurveWKBWriter paths on the PR #7 tree
  (`CurveWKBExportHonestyTest`).
- Appium IDs apply to TestBuilder GUI only; TestRunner has no Swing surface.
