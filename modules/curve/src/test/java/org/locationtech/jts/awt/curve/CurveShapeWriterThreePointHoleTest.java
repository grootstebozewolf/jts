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
package org.locationtech.jts.awt.curve;

import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * #114 witness is four-item {@code (A, B, C, A)}. That list is not
 * in the PostGIS model (odd control count &gt; 1). The JTS-only
 * annulus exception is reverted. Do not invent a 5th control.
 * Not #86 / #87 draw. Not a paint remake.
 */
public class CurveShapeWriterThreePointHoleTest extends GeometryTestCase {

  /** PO-attached witness. Do not substitute another ring. */
  private static final String WITNESS =
      "CURVEPOLYGON (CIRCULARSTRING (60 380, 240 440, 404 326, 60 380), "
      + "CIRCULARSTRING (141 84, 270 28, 170 290, 141 84))";

  private static final String SEMICIRCLE_HOLE =
      "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, -10 0), "
      + "CIRCULARSTRING (-3 0, 0 3, 3 0, -3 0))";

  /**
   * Compact major-arc hole: A=(-3,0), B=(0,-3), C=(0,3) on r=3.
   * Shell is the PostGIS 5-token circle; hole is still 4-item.
   */
  private static final String MAJOR_ARC_HOLE =
      "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0), "
      + "CIRCULARSTRING (-3 0, 0 -3, 0 3, -3 0))";

  public static void main(String[] args) {
    TestRunner.run(CurveShapeWriterThreePointHoleTest.class);
  }

  public CurveShapeWriterThreePointHoleTest(String name) { super(name); }

  private static void assertFourItemRejected(String wkt) {
    try {
      new CurveWKTReader().read(wkt);
      fail("Expected reject 4-item CIRCULARSTRING (A, B, C, A)");
    } catch (ParseException e) {
      assertTrue(e.getMessage().indexOf("odd") >= 0
          || e.getMessage().indexOf("Four-item") >= 0);
    } catch (Exception e) {
      fail("unexpected: " + e);
    }
  }

  public void testWitnessFourItemRejected() {
    assertFourItemRejected(WITNESS);
  }

  public void testSemicircleHoleFourItemRejected() {
    assertFourItemRejected(SEMICIRCLE_HOLE);
  }

  public void testMajorArcFourItemHoleRejected() {
    assertFourItemRejected(MAJOR_ARC_HOLE);
  }
}
