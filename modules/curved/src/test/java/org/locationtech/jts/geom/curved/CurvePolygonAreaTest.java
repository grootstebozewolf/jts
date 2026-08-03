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
package org.locationtech.jts.geom.curved;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * CRV-AREA: {@code getArea()} on a {@link CurvePolygon} must account for the
 * area swept by its arc rings, not just the polygon through their control
 * points.
 * <p>
 * CurvePolygon inherits {@code Polygon.getArea()}, which shoelaces the flat
 * {@code getExteriorRing()} view. For a circle described as two semicircular
 * arcs that is the inscribed quadrilateral -- area 8 for a radius-2 circle
 * whose true area is {@code 4*pi ~= 12.566}, a 36% underestimate.
 * <p>
 * The exact value comes from Green's theorem: the contribution of a circular
 * arc on centre {@code (cx, cy)} radius {@code r} sweeping {@code a0 -> a1} to
 * the contour integral {@code area = 1/2 * closed_integral(x dy - y dx)} is
 * <pre>
 *   1/2 * [ r^2 (a1 - a0)
 *           + cx * r * (sin a1 - sin a0)
 *           - cy * r * (cos a1 - cos a0) ]
 * </pre>
 * with the sign carried by the signed sweep, so no orientation heuristic is
 * needed. Straight pieces contribute the usual shoelace term.
 */
public class CurvePolygonAreaTest extends GeometryTestCase {

  /** Radius-2 circle as two semicircular arcs. True area 4*pi. */
  private static final String CIRCLE_R2 =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 0 2, -2 0, 0 -2, 2 0))";

  public static void main(String[] args) {
    TestRunner.run(CurvePolygonAreaTest.class);
  }

  public CurvePolygonAreaTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurvedWKTReader().read(wkt);
  }

  /** A circle of radius 2 has area 4*pi, not the inscribed quad's 8. */
  public void testCircleArea() throws Exception {
    assertEquals("radius-2 circle has area 4*pi",
        4.0 * Math.PI, readCurve(CIRCLE_R2).getArea(), 1.0e-6);
  }

  /** A unit circle has area pi. */
  public void testUnitCircleArea() throws Exception {
    Geometry g = readCurve("CURVEPOLYGON (CIRCULARSTRING (1 0, 0 1, -1 0, 0 -1, 1 0))");
    assertEquals("unit circle has area pi", Math.PI, g.getArea(), 1.0e-6);
  }

  /** The arc area strictly exceeds the inscribed control polygon's. */
  public void testArcAreaExceedsControlPolygonArea() throws Exception {
    double arcArea = readCurve(CIRCLE_R2).getArea();
    assertTrue("arc area " + arcArea + " must exceed the inscribed quad area 8",
        arcArea > 8.0 + 1.0e-9);
  }

  /** An arc hole is subtracted at its true swept area. */
  public void testArcHoleIsSubtracted() throws Exception {
    // Radius-4 circular shell with a radius-1 circular hole.
    Geometry g = readCurve("CURVEPOLYGON ("
        + "CIRCULARSTRING (4 0, 0 4, -4 0, 0 -4, 4 0), "
        + "CIRCULARSTRING (1 0, 0 1, -1 0, 0 -1, 1 0))");
    assertEquals("16*pi shell minus 1*pi hole",
        16.0 * Math.PI - Math.PI, g.getArea(), 1.0e-6);
  }

  /** Area is orientation-independent: reversing the ring cannot flip it. */
  public void testAreaIsOrientationIndependent() throws Exception {
    // Same circle traversed the other way round.
    Geometry cw = readCurve(
        "CURVEPOLYGON (CIRCULARSTRING (2 0, 0 -2, -2 0, 0 2, 2 0))");
    assertEquals("reversed traversal has the same area",
        4.0 * Math.PI, cw.getArea(), 1.0e-6);
  }

  /** Guard: an all-linear CurvePolygon keeps the plain shoelace area. */
  public void testLinearPolygonAreaUnchanged() throws Exception {
    Geometry g = readCurve("CURVEPOLYGON ((0 0, 4 0, 4 3, 0 3, 0 0))");
    assertEquals("4x3 rectangle", 12.0, g.getArea(), 1.0e-9);
  }

  /** Guard: a degenerate ring with colinear control points has zero area. */
  public void testColinearRingHasZeroArea() throws Exception {
    Geometry g = readCurve("CURVEPOLYGON ((0 0, 2 0, 4 0, 0 0))");
    assertEquals(0.0, g.getArea(), 1.0e-9);
  }

  /** Guard: an empty CurvePolygon has zero area. */
  public void testEmptyHasZeroArea() throws Exception {
    assertEquals(0.0, readCurve("CURVEPOLYGON EMPTY").getArea(), 1.0e-9);
  }
}
