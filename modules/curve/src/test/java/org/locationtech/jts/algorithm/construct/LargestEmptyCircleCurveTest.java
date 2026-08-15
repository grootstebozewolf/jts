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
package org.locationtech.jts.algorithm.construct;

import org.locationtech.jts.algorithm.RocqRefRunner;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Certified LEC cell: a circular disc as boundary with its own
 * circumference as the linear obstacle. Exact answer is (centre, r),
 * pinned through {@link RocqRefRunner} against NTS.Proofs #466.
 */
public class LargestEmptyCircleCurveTest extends GeometryTestCase {

  private static final String DISC_2 =
      "CURVEPOLYGON (CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0))";
  private static final String RING_2 =
      "CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0)";
  private static final String DISC_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String MULTI_5 =
      "MULTISURFACE (CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)))";

  public static void main(String[] args) {
    TestRunner.run(LargestEmptyCircleCurveTest.class);
  }

  public LargestEmptyCircleCurveTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testR2CircleIsExactTwoNotSqrt2() throws Exception {
    Geometry disc = readCurve(DISC_2);
    Geometry ring = readCurve(RING_2);
    Point center = LargestEmptyCircle.getCenter(ring, disc, 0.01);
    LineString radius = LargestEmptyCircle.getRadiusLine(ring, disc, 0.01);
    assertEquals(0.0, center.getX(), 0.0);
    assertEquals(0.0, center.getY(), 0.0);
    assertEquals(RocqRefRunner.LEC_CIRCLE_EXACT_R2_BITS,
        Double.doubleToRawLongBits(radius.getLength()));
    assertTrue("must not report the n=4 chorded radius",
        Double.doubleToRawLongBits(radius.getLength())
            != RocqRefRunner.LEC_CIRCLE_CHORDED_R2_N4_BITS);
    RocqRefRunner.Result r = RocqRefRunner.runLecCircle(ring, disc, 0.0, 0.0, 2.0, 0.01);
    assertTrue(r.toString(), r.isSound());
  }

  public void testR5DiscCentreAndRadius() throws Exception {
    Geometry disc = readCurve(DISC_5);
    Point center = LargestEmptyCircle.getCenter(disc, disc, 0.01);
    LineString radius = LargestEmptyCircle.getRadiusLine(disc, 0.01);
    assertEquals(0.0, center.getX(), 0.0);
    assertEquals(0.0, center.getY(), 0.0);
    assertEquals(5.0, radius.getLength(), 0.0);
    Coordinate c = center.getCoordinate();
    assertEquals(5.0, c.distance(radius.getCoordinateN(1)), 0.0);
    assertTrue(LargestEmptyCircle.hasCertifiedClosedForm(disc, null));
  }

  public void testCircularStringEncodingAgrees() throws Exception {
    Geometry disc = readCurve(DISC_2);
    Geometry ring = readCurve(RING_2);
    double rd = LargestEmptyCircle.getRadiusLine(disc, null, 0.01).getLength();
    double rr = LargestEmptyCircle.getRadiusLine(ring, null, 0.01).getLength();
    double both = LargestEmptyCircle.getRadiusLine(ring, disc, 0.01).getLength();
    assertEquals(2.0, rd, 0.0);
    assertEquals(rd, rr, 0.0);
    assertEquals(rd, both, 0.0);
  }

  public void testSingleMemberMultiSurfaceUnwraps() throws Exception {
    Geometry multi = readCurve(MULTI_5);
    assertEquals(5.0,
        LargestEmptyCircle.getRadiusLine(multi, multi, 0.01).getLength(), 0.0);
  }

  public void testPlainSquareOfFourChordsIsNotTheDisk() throws Exception {
    Geometry ring = readCurve("LINESTRING (-2 0, 0 2, 2 0, 0 -2, -2 0)");
    Geometry poly = readCurve("POLYGON ((-2 0, 0 2, 2 0, 0 -2, -2 0))");
    assertTrue(!LargestEmptyCircle.hasCertifiedClosedForm(ring, poly));
    LineString radius = LargestEmptyCircle.getRadiusLine(ring, poly, 0.01);
    assertEquals(Math.sqrt(2.0), radius.getLength(), 0.02);
    assertTrue(Math.abs(radius.getLength() - 2.0) > 0.1);
  }
}
