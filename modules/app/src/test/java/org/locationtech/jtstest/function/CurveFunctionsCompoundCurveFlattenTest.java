/*
 * Copyright (c) 2026 grootstebozewolf
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jtstest.function;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurveLinearizationStrategy;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * CRV-CC ticket 30: TestBuilder {@code toLinear} / {@code linearizeForOps}
 * is the named COMPOUNDCURVE fallback. It must warn, not silently
 * replace the compound with chords.
 */
public class CurveFunctionsCompoundCurveFlattenTest extends TestCase {

  private static final String COMPOUND =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 20 0))";

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() {
    return new TestSuite(CurveFunctionsCompoundCurveFlattenTest.class);
  }
  public CurveFunctionsCompoundCurveFlattenTest(String name) { super(name); }

  protected void tearDown() {
    CurveLinearizationStrategy.setWarnSink(null);
  }

  private static Geometry read(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testLinearizeForOpsWarnsAndDensifies() throws Exception {
    Geometry cc = read(COMPOUND);
    assertTrue(cc instanceof CompoundCurve);
    final List warns = new ArrayList();
    CurveLinearizationStrategy.setWarnSink(
        new CurveLinearizationStrategy.WarnSink() {
          public void warn(String message) {
            warns.add(message);
          }
        });
    Geometry lin = CurveFunctions.linearizeForOps(cc);
    assertFalse(lin instanceof CompoundCurve);
    assertTrue(lin instanceof LineString);
    assertTrue("named toLinear must warn, warns=" + warns, !warns.isEmpty());
    String msg = (String) warns.get(0);
    assertTrue(msg, msg.indexOf("CompoundCurve") >= 0
        || msg.indexOf("toLinear") >= 0);
  }

  public void testToLinearIsNamedPath() throws Exception {
    Geometry cc = read(COMPOUND);
    Geometry lin = CurveFunctions.toLinear(cc, 0.1);
    assertFalse(lin instanceof CompoundCurve);
    assertTrue(lin.getNumPoints() > cc.getNumPoints());
  }
}
