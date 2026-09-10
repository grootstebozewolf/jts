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
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.buffer.OffsetCurve;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * OFF (#1195): public {@link OffsetCurve} on a single-arc CircularString
 * returns a concentric CircularString. Positive distance is left of
 * direction (CCW ⇒ inward).
 */
public class CurveOffsetCurveTest extends TestCase {

  private static final double EPS = 1e-10;
  /** Upper semicircle centre (0,0) R=5, CCW. */
  private static final String SEMI_R5 = "CIRCULARSTRING (5 0, 0 5, -5 0)";

  public static void main(String[] args) {
    TestRunner.run(CurveOffsetCurveTest.class);
  }

  public CurveOffsetCurveTest(String name) {
    super(name);
  }

  public void testCcwPositiveIsInward() throws Exception {
    Geometry cs = new CurveWKTReader().read(SEMI_R5);
    Geometry result = OffsetCurve.getCurve(cs, 1.0);
    assertTrue(result instanceof CircularString);
    assertRadius((CircularString) result, 0, 0, 4);
  }

  public void testCcwNegativeIsOutward() throws Exception {
    Geometry cs = new CurveWKTReader().read(SEMI_R5);
    Geometry result = OffsetCurve.getCurve(cs, -1.0);
    assertTrue(result instanceof CircularString);
    assertRadius((CircularString) result, 0, 0, 6);
  }

  public void testCollapseReturnsEmpty() throws Exception {
    Geometry cs = new CurveWKTReader().read(SEMI_R5);
    assertTrue(OffsetCurve.getCurve(cs, 5.0).isEmpty());
    assertTrue(OffsetCurve.getCurve(cs, 7.0).isEmpty());
  }

  public void testMultiArcFallsThrough() throws Exception {
    Geometry cs = new CurveWKTReader().read(
        "CIRCULARSTRING (1 0, 0 1, -1 0, 0 -1, 1 0)");
    Geometry result = OffsetCurve.getCurve(cs, 0.5);
    assertFalse(result instanceof CircularString);
  }

  public void testEndpointsScaled() throws Exception {
    Geometry cs = new CurveWKTReader().read(SEMI_R5);
    Coordinate[] c = OffsetCurve.getCurve(cs, -1.0).getCoordinates();
    assertEquals(3, c.length);
    assertEquals(6.0, c[0].x, EPS);
    assertEquals(0.0, c[0].y, EPS);
    assertEquals(0.0, c[1].x, EPS);
    assertEquals(6.0, c[1].y, EPS);
    assertEquals(-6.0, c[2].x, EPS);
    assertEquals(0.0, c[2].y, EPS);
  }

  private static void assertRadius(CircularString cs, double cx, double cy,
      double r) {
    Coordinate[] c = cs.getCoordinates();
    assertEquals(3, c.length);
    for (int i = 0; i < c.length; i++) {
      assertEquals(r, Math.hypot(c[i].x - cx, c[i].y - cy), EPS);
    }
  }
}
