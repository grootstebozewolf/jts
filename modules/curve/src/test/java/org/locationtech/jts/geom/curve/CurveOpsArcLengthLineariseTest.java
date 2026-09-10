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
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * VBF: arc-length densify for VariableBuffer parameterisation.
 */
public class CurveOpsArcLengthLineariseTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(CurveOpsArcLengthLineariseTest.class);
  }

  public CurveOpsArcLengthLineariseTest(String name) {
    super(name);
  }

  @Override
  protected void tearDown() {
    CurveLinearizationStrategy.clearThreadOverride();
    CurveLinearizationStrategy.setDefault(CurveLinearizationStrategy.LINEARIZED);
  }

  public void testHalfCircleEqualArcLengthSamples() throws Exception {
    Geometry cs = new CurveWKTReader().read("CIRCULARSTRING (5 0, 0 5, -5 0)");
    LineString lin = (LineString) CurveOps.lineariseArcLength(cs, 8);
    assertEquals(9, lin.getNumPoints());
    // Equal central angles ⇒ equal chord lengths on a circle.
    double first = lin.getCoordinateN(0).distance(lin.getCoordinateN(1));
    for (int i = 1; i < lin.getNumPoints() - 1; i++) {
      double seg = lin.getCoordinateN(i).distance(lin.getCoordinateN(i + 1));
      assertEquals("equal arc-length chords", first, seg, 1.0e-9);
    }
    // Inscribed chord polyline is short of πr; converges with n.
    double expectedChord = 8 * 2.0 * 5.0 * Math.sin(Math.PI / 16.0);
    double total = 0;
    for (int i = 0; i < lin.getNumPoints() - 1; i++) {
      total += lin.getCoordinateN(i).distance(lin.getCoordinateN(i + 1));
    }
    assertEquals(expectedChord, total, 1.0e-9);
    assertTrue(total < cs.getLength());
  }

  public void testPreserveSkipsDensify() throws Exception {
    Geometry cs = new CurveWKTReader().read("CIRCULARSTRING (5 0, 0 5, -5 0)");
    CurveLinearizationStrategy.setThreadOverride(
        CurveLinearizationStrategy.PRESERVE);
    assertSame(cs, CurveOps.lineariseArcLength(cs, 8));
  }
}
