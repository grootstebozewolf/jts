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
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * R-CONT first cell: certified PIP of a circular disc against a Point
 * or MultiPoint. {@code d²} vs {@code r²}, no densify. A miss returns
 * {@code null} so {@link CurveOps} can take the chords alone.
 */
public class CurveExactPipTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String SQUARE =
      "POLYGON ((-6 -6, 6 -6, 6 6, -6 6, -6 -6))";

  public static void main(String[] args) {
    TestRunner.run(CurveExactPipTest.class);
  }

  public CurveExactPipTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testBulgePointIsInterior() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry p = readCurve("POINT (3 3)");
    assertEquals(Boolean.TRUE, CurveExact.contains(disc, p));
    assertEquals(Boolean.TRUE, CurveExact.covers(disc, p));
    assertEquals(Location.INTERIOR, locate(disc, 3, 3));
  }

  public void testOnCircleIsBoundary() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry p = readCurve("POINT (5 0)");
    assertEquals(Boolean.FALSE, CurveExact.contains(disc, p));
    assertEquals(Boolean.TRUE, CurveExact.covers(disc, p));
    assertEquals(Location.BOUNDARY, locate(disc, 5, 0));
  }

  public void testExteriorIsNeither() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry p = readCurve("POINT (6 0)");
    // Envelope already excludes (6 0); the primitive still answers.
    assertEquals(Boolean.FALSE, CurveExact.contains(disc, p));
    assertEquals(Boolean.FALSE, CurveExact.covers(disc, p));
    assertEquals(Location.EXTERIOR, locate(disc, 6, 0));
  }

  public void testCentreIsInterior() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry p = readCurve("POINT (0 0)");
    assertEquals(Boolean.TRUE, CurveExact.contains(disc, p));
    assertEquals(Boolean.TRUE, CurveExact.covers(disc, p));
    assertEquals(Location.INTERIOR, locate(disc, 0, 0));
  }

  public void testMultiPointAllInterior() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry pts = readCurve("MULTIPOINT ((3 3), (0 0))");
    assertEquals(Boolean.TRUE, CurveExact.contains(disc, pts));
    assertEquals(Boolean.TRUE, CurveExact.covers(disc, pts));
    assertTrue(disc.contains(pts));
    assertTrue(disc.covers(pts));
  }

  public void testMultiPointInteriorAndBoundary() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry pts = readCurve("MULTIPOINT ((3 3), (5 0))");
    assertEquals(Boolean.FALSE, CurveExact.contains(disc, pts));
    assertEquals(Boolean.TRUE, CurveExact.covers(disc, pts));
    assertFalse(disc.contains(pts));
    assertTrue(disc.covers(pts));
  }

  public void testMultiPointWithExterior() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry pts = readCurve("MULTIPOINT ((3 3), (6 0))");
    assertEquals(Boolean.FALSE, CurveExact.contains(disc, pts));
    assertEquals(Boolean.FALSE, CurveExact.covers(disc, pts));
    assertFalse(disc.contains(pts));
    assertFalse(disc.covers(pts));
  }

  public void testHalfDiscPuntalIsR2() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry inside = readCurve("POINT (3 3)");
    Geometry below = readCurve("POINT (0 -2)");
    assertEquals(Boolean.TRUE, CurveExact.contains(half, inside));
    assertEquals(Boolean.TRUE, CurveExact.covers(half, inside));
    assertEquals(Boolean.FALSE, CurveExact.contains(half, below));
    assertEquals(Boolean.FALSE, CurveExact.covers(half, below));
  }

  public void testNotPuntalReturnsNull() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry square = readCurve(SQUARE);
    assertNull("disc vs polygon is not this cell",
        CurveExact.contains(disc, square));
    assertNull(CurveExact.covers(disc, square));
  }

  /**
   * Deep interior and far exterior agree with densify-then-core.
   * The bulge point is not compared to linearise: the control diamond
   * is a known false-negative, and that is the certified answer.
   */
  public void testParityWhereChainsawAgrees() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry centre = readCurve("POINT (0 0)");
    Geometry far = readCurve("POINT (100 100)");
    Geometry chord = CurveOps.linearise(disc);
    assertEquals(chord.contains(centre), disc.contains(centre));
    assertEquals(chord.covers(centre), disc.covers(centre));
    assertEquals(chord.contains(far), disc.contains(far));
    assertEquals(chord.covers(far), disc.covers(far));
  }

  private static int locate(Geometry disc, double x, double y) {
    CircularArcDensifier.Circle c = CurveExact.circularDisc(disc);
    return CurveExact.locatePoint(c, new Coordinate(x, y), c.r * c.r);
  }
}
