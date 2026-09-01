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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * ML.2: convex hole-free CurvePolygon MIC. HALF_DISC is the witness
 * (centre on the symmetry ray, r = R/2). Disc (ML.0) and stadium (ML.1)
 * stay first. Nonconvex / holed miss.
 */
public class CurveExactConvexMicTest extends GeometryTestCase {

  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String HALF_LOWER =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 -5, 5 0), (5 0, -5 0)))";
  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String NONCONVEX_PINCH =
      "CURVEPOLYGON (COMPOUNDCURVE ("
          + "CIRCULARSTRING (0 0, -2 2, 0 4), (0 4, 0 3), "
          + "CIRCULARSTRING (0 3, 2 2, 0 1), (0 1, 0 0)))";

  public static void main(String[] args) {
    TestRunner.run(CurveExactConvexMicTest.class);
  }

  public CurveExactConvexMicTest(String name) {
    super(name);
  }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testHalfDiscMicIsHalfRadiusOnAxis() throws Exception {
    CircularArcDensifier.Circle mic = CurveExact.halfDiscMic(readCurve(HALF_DISC));
    assertNotNull(mic);
    assertEquals(0.0, mic.cx, 0.0);
    assertEquals(2.5, mic.cy, 0.0);
    assertEquals(2.5, mic.r, 0.0);
    assertEquals(mic.cx, CurveExact.mic(readCurve(HALF_DISC)).cx, 0.0);
    assertEquals(mic.cy, CurveExact.mic(readCurve(HALF_DISC)).cy, 0.0);
    assertEquals(mic.r, CurveExact.mic(readCurve(HALF_DISC)).r, 0.0);
  }

  public void testLowerHalfDiscMic() throws Exception {
    CircularArcDensifier.Circle mic = CurveExact.mic(readCurve(HALF_LOWER));
    assertNotNull(mic);
    assertEquals(0.0, mic.cx, 0.0);
    assertEquals(-2.5, mic.cy, 0.0);
    assertEquals(2.5, mic.r, 0.0);
  }

  public void testDiscStillMl0First() throws Exception {
    CircularArcDensifier.Circle mic = CurveExact.mic(readCurve(CIRCLE_5));
    assertEquals(0.0, mic.cx, 0.0);
    assertEquals(0.0, mic.cy, 0.0);
    assertEquals(5.0, mic.r, 0.0);
    assertNull(CurveExact.halfDiscMic(readCurve(CIRCLE_5)));
  }

  public void testNonconvexPinchMisses() throws Exception {
    assertNull(CurveExact.halfDiscMic(readCurve(NONCONVEX_PINCH)));
    assertNull(CurveExact.mic(readCurve(NONCONVEX_PINCH)));
  }
}
