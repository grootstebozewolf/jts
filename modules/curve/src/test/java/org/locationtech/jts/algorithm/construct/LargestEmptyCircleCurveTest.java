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
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Certified LEC cell (NTS.Proofs #466) plus the typed obstacle-distance
 * oracle: CircularString / CompoundCurve / multi-disc / mixed sets
 * measure the arc or disc, not the control chord.
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

  /**
   * A CircularString obstacle is the arc, not the control chord.
   * From a point above the apex the nearest site is the apex, not
   * the mid-control (2, 3) and not the chord at y = 0.
   */
  public void testCircularStringArcObstacleUsesApexNotChord() throws Exception {
    Geometry arc = readCurve("CIRCULARSTRING (0 0, 2 3, 10 0)");
    Coordinate apex = apexOf(new Coordinate(0, 0), new Coordinate(2, 3),
        new Coordinate(10, 0));
    Point query = arc.getFactory().createPoint(new Coordinate(5, 8));
    ObstacleDistance od = new ObstacleDistance(arc);
    Coordinate[] np = od.nearestPoints(query);
    assertTrue("nearest site is the apex, not the mid-control",
        np[0].distance(apex) < 1.0e-6);
    assertTrue("must not snap to the mid-control",
        np[0].distance(new Coordinate(2, 3)) > 0.5);
    assertTrue("must not snap to the chord", Math.abs(np[0].y) > 1.0);

    // Box contains the apex so the obstacle binds; a chord obstacle
    // would instead take the box MIC (r = 1).
    Geometry box = readCurve(
        "POLYGON ((0 3.5, 10 3.5, 10 5.5, 0 5.5, 0 3.5))");
    Point center = LargestEmptyCircle.getCenter(arc, box, 0.01);
    double r = LargestEmptyCircle.getRadiusLine(arc, box, 0.01).getLength();
    double expectedY = (5.5 + apex.y) / 2.0;
    double expectedR = 5.5 - expectedY;
    assertEquals(5.0, center.getX(), 0.05);
    assertEquals(expectedY, center.getY(), 0.05);
    assertEquals(expectedR, r, 0.05);
    assertTrue("must not report the box MIC (chord path, r = 1)",
        Math.abs(r - 1.0) > 0.1);
  }

  /**
   * A LineString of the same three points stays on the chord path.
   */
  public void testLineStringOfThreePointsIsNotAnArc() throws Exception {
    Geometry line = readCurve("LINESTRING (0 0, 2 3, 10 0)");
    Point query = line.getFactory().createPoint(new Coordinate(5, 8));
    ObstacleDistance od = new ObstacleDistance(line);
    Coordinate[] np = od.nearestPoints(query);
    assertEquals(5.0, np[0].x, 1.0e-9);
    assertEquals(0.0, np[0].y, 1.0e-9);
    assertEquals(8.0, od.distance(query), 1.0e-9);
  }

  /**
   * CompoundCurve is the min of a LineString member and a
   * CircularString member, not {@code toLinear}.
   */
  public void testCompoundCurveLinePlusArc() throws Exception {
    Geometry cc = readCurve(
        "COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 12 3, 20 0))");
    Coordinate apex = apexOf(new Coordinate(10, 0), new Coordinate(12, 3),
        new Coordinate(20, 0));
    ObstacleDistance od = new ObstacleDistance(cc);
    Point overLine = cc.getFactory().createPoint(new Coordinate(5, 8));
    Point overArc = cc.getFactory().createPoint(new Coordinate(15, 8));
    assertEquals(8.0, od.distance(overLine), 1.0e-9);
    assertEquals(overArc.getCoordinate().distance(apex),
        od.distance(overArc), 1.0e-6);
    assertTrue("arc query must not use the line y=0",
        od.distance(overArc) < 7.5);

    Geometry box = readCurve(
        "POLYGON ((10 3.5, 20 3.5, 20 5.5, 10 5.5, 10 3.5))");
    Point center = LargestEmptyCircle.getCenter(cc, box, 0.01);
    double r = LargestEmptyCircle.getRadiusLine(cc, box, 0.01).getLength();
    double expectedY = (5.5 + apex.y) / 2.0;
    assertEquals(15.0, center.getX(), 0.05);
    assertEquals(expectedY, center.getY(), 0.05);
    assertEquals(5.5 - expectedY, r, 0.05);
    assertEquals(r, od.distance(center), 0.05);
  }

  /**
   * Two disjoint discs inside a large square: the LEC sits in the
   * gap. A query in the gap sees {@code min_i max(0, |p-c_i|-r_i)}.
   */
  public void testTwoDiscsInASquareSitInTheGap() throws Exception {
    Geometry discs = readCurve(
        "MULTISURFACE ("
            + "CURVEPOLYGON (CIRCULARSTRING (1 5, 2 6, 3 5, 2 4, 1 5)), "
            + "CURVEPOLYGON (CIRCULARSTRING (7 5, 8 6, 9 5, 8 4, 7 5)))");
    Geometry square = readCurve("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    Point gap = discs.getFactory().createPoint(new Coordinate(5, 5));
    ObstacleDistance od = new ObstacleDistance(discs);
    assertEquals(2.0, od.distance(gap), 1.0e-9);
    Point insideLeft = discs.getFactory().createPoint(new Coordinate(2, 5));
    assertEquals(0.0, od.distance(insideLeft), 1.0e-9);

    Point center = LargestEmptyCircle.getCenter(discs, square, 0.01);
    double r = LargestEmptyCircle.getRadiusLine(discs, square, 0.01)
        .getLength();
    assertEquals(5.0, center.getX(), 0.05);
    assertEquals(5.0, center.getY(), 0.05);
    assertEquals(2.0, r, 0.05);
  }

  /**
   * Mixed POINT + CircularString: min of the typed distances.
   */
  public void testPointPlusArcMixed() throws Exception {
    Geometry mixed = readCurve(
        "GEOMETRYCOLLECTION (POINT (5 12), CIRCULARSTRING (0 0, 2 3, 10 0))");
    Coordinate apex = apexOf(new Coordinate(0, 0), new Coordinate(2, 3),
        new Coordinate(10, 0));
    ObstacleDistance od = new ObstacleDistance(mixed);
    Point mid = mixed.getFactory().createPoint(new Coordinate(5,
        (12.0 + apex.y) / 2.0));
    double toPoint = mid.getCoordinate().distance(new Coordinate(5, 12));
    double toApex = mid.getCoordinate().distance(apex);
    assertEquals(Math.min(toPoint, toApex), od.distance(mid), 1.0e-6);

    Geometry box = readCurve(
        "POLYGON ((-5 3, 15 3, 15 16, -5 16, -5 3))");
    Point center = LargestEmptyCircle.getCenter(mixed, box, 0.01);
    double r = LargestEmptyCircle.getRadiusLine(mixed, box, 0.01)
        .getLength();
    double expectedY = (12.0 + apex.y) / 2.0;
    assertEquals(5.0, center.getX(), 0.08);
    assertEquals(expectedY, center.getY(), 0.08);
    assertEquals(12.0 - expectedY, r, 0.08);
    assertEquals(r, od.distance(center), 0.05);
    Coordinate radiusPt = LargestEmptyCircle.getRadiusLine(mixed, box, 0.01)
        .getCoordinateN(1);
    assertTrue("radius site is the apex or the point, not the mid-control",
        radiusPt.distance(apex) < 0.15
            || radiusPt.distance(new Coordinate(5, 12)) < 0.15);
    assertTrue(radiusPt.distance(new Coordinate(2, 3)) > 0.5);
  }

  /**
   * Disc + LineString: min of the filled-disc distance and the
   * segment distance.
   */
  public void testDiscPlusLineStringMixed() throws Exception {
    Geometry mixed = readCurve(
        "GEOMETRYCOLLECTION ("
            + "CURVEPOLYGON (CIRCULARSTRING (1 5, 2 6, 3 5, 2 4, 1 5)), "
            + "LINESTRING (8 0, 8 10))");
    ObstacleDistance od = new ObstacleDistance(mixed);
    Point q = mixed.getFactory().createPoint(new Coordinate(5, 5));
    // disc at (2,5) r=1 → 2; line x=8 → 3
    assertEquals(2.0, od.distance(q), 1.0e-9);
  }

  private static Coordinate apexOf(Coordinate a, Coordinate b, Coordinate c) {
    double[] cc = DiscreteHausdorffDistance.circumcircle(a, b, c);
    return new Coordinate(cc[0], cc[1] + cc[2]);
  }
}
