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
    LargestEmptyCircle lec = new LargestEmptyCircle(disc, disc, 0.01);
    lec.getCenter();
    assertTrue("disc closed form is not the point-site walk",
        !lec.usedPointSiteCandidates());
  }

  /**
   * A CircularString obstacle is not a point-site set. The grid (or
   * the disc closed form for a full ring) stays; Apollonius is not
   * this PR.
   */
  public void testArcObstacleIsNotPointSiteEnumeration() throws Exception {
    Geometry arc = readCurve("CIRCULARSTRING (0 0, 2 3, 10 0)");
    Geometry box = readCurve("POLYGON ((0 3.5, 10 3.5, 10 5.5, 0 5.5, 0 3.5))");
    LargestEmptyCircle lec = new LargestEmptyCircle(arc, box, 0.01);
    lec.getCenter();
    assertTrue(!lec.usedPointSiteCandidates());
    assertTrue(!LargestEmptyCircle.hasCertifiedClosedForm(arc, box));
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

    // Centre must lie in the box; include the box ring as an obstacle
    // so the circle itself stays inside (otherwise LEC walks to a
    // corner, farther from the arc than the apex cell).
    Geometry box = readCurve(
        "POLYGON ((4 3.5, 6 3.5, 6 5.5, 4 5.5, 4 3.5))");
    Geometry obs = withBoundaryObstacle(arc, box);
    Point center = LargestEmptyCircle.getCenter(obs, box, 0.01);
    double r = LargestEmptyCircle.getRadiusLine(obs, box, 0.01).getLength();
    double expectedY = (5.5 + apex.y) / 2.0;
    double expectedR = 5.5 - expectedY;
    assertEquals(5.0, center.getX(), 0.25);
    assertEquals(expectedY, center.getY(), 0.25);
    assertEquals(expectedR, r, 0.08);
    assertEquals(r, od.distance(center), 0.05);
    Coordinate onArc = od.nearestPoints(center)[0];
    assertTrue("nearest site on the arc is the apex, not the mid-control",
        onArc.distance(apex) < 0.25);
    assertTrue("must not snap to the mid-control",
        onArc.distance(new Coordinate(2, 3)) > 0.5);
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
    Coordinate apex = apexOf(new Coordinate(0, 0), new Coordinate(2, 3),
        new Coordinate(10, 0));
    assertTrue("two segments, not the arc apex",
        np[0].distance(apex) > 0.5);
    assertTrue("not the endpoint-to-endpoint chord at y=0",
        np[0].y > 0.5);
    // projection onto (2 3, 10 0)
    assertEquals(2.9863013698630136, np[0].x, 1.0e-6);
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
        "POLYGON ((14 3.5, 16 3.5, 16 5.5, 14 5.5, 14 3.5))");
    Geometry obs = withBoundaryObstacle(cc, box);
    Point center = LargestEmptyCircle.getCenter(obs, box, 0.01);
    double r = LargestEmptyCircle.getRadiusLine(obs, box, 0.01).getLength();
    double expectedY = (5.5 + apex.y) / 2.0;
    assertEquals(15.0, center.getX(), 0.25);
    assertEquals(expectedY, center.getY(), 0.25);
    assertEquals(5.5 - expectedY, r, 0.08);
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
    Geometry square = readCurve("POLYGON ((0 3, 10 3, 10 7, 0 7, 0 3))");
    Point gap = discs.getFactory().createPoint(new Coordinate(5, 5));
    ObstacleDistance od = new ObstacleDistance(discs);
    assertEquals(2.0, od.distance(gap), 1.0e-9);
    Point insideLeft = discs.getFactory().createPoint(new Coordinate(2, 5));
    assertEquals(0.0, od.distance(insideLeft), 1.0e-9);

    Geometry obs = withBoundaryObstacle(discs, square);
    Point center = LargestEmptyCircle.getCenter(obs, square, 0.01);
    double r = LargestEmptyCircle.getRadiusLine(obs, square, 0.01)
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
        "POLYGON ((0.5 3, 9.5 3, 9.5 16, 0.5 16, 0.5 3))");
    Geometry obs = withBoundaryObstacle(mixed, box);
    Point center = LargestEmptyCircle.getCenter(obs, box, 0.01);
    double r = LargestEmptyCircle.getRadiusLine(obs, box, 0.01)
        .getLength();
    double expectedY = (12.0 + apex.y) / 2.0;
    assertEquals(5.0, center.getX(), 0.5);
    assertEquals(expectedY, center.getY(), 0.5);
    assertEquals(12.0 - expectedY, r, 0.15);
    assertEquals(r, od.distance(center), 0.05);
    Coordinate onMixed = od.nearestPoints(center)[0];
    assertTrue("nearest site is the apex or the point, not the mid-control",
        onMixed.distance(apex) < 0.3
            || onMixed.distance(new Coordinate(5, 12)) < 0.3);
    assertTrue(onMixed.distance(new Coordinate(2, 3)) > 0.5);
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

  /**
   * ML.4: hole-free half-disc shell is filled. Interior query is 0;
   * below the diameter is the typed shell distance, not a control chord.
   */
  public void testHoleFreeHalfDiscIsFilledShell() throws Exception {
    Geometry half = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))");
    ObstacleDistance od = new ObstacleDistance(half);
    Point inside = half.getFactory().createPoint(new Coordinate(0, 2));
    Point below = half.getFactory().createPoint(new Coordinate(0, -2));
    Point outside = half.getFactory().createPoint(new Coordinate(0, 8));
    assertEquals(0.0, od.distance(inside), 0.0);
    assertEquals(2.0, od.distance(below), 1.0e-9);
    assertEquals(3.0, od.distance(outside), 1.0e-9);

    Geometry square = readCurve("POLYGON ((-6 -3, 6 -3, 6 6, -6 6, -6 -3))");
    Geometry obs = withBoundaryObstacle(half, square);
    Point center = LargestEmptyCircle.getCenter(obs, square, 0.01);
    double r = LargestEmptyCircle.getRadiusLine(obs, square, 0.01).getLength();
    // Largest empty pocket is below the diameter in the square.
    assertTrue("centre must not sit inside the filled half-disc",
        !half.covers(center));
    assertEquals(od.distance(center), r, 0.05);
    assertTrue("radius reaches the diameter, not a far corner only",
        r < 3.5);
  }

  /**
   * ML.4: certified stadium shell is filled. Interior of the capsule is
   * distance 0; above a side is the cap radius clearance.
   */
  public void testHoleFreeStadiumIsFilledShell() throws Exception {
    // STADIUM_IN: caps at (±1, 2), r=1, sides y=1 and y=3.
    Geometry stad = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 1, -2 2, -1 3), (-1 3, 1 3), "
            + "CIRCULARSTRING (1 3, 2 2, 1 1), (1 1, -1 1)))");
    ObstacleDistance od = new ObstacleDistance(stad);
    Point inside = stad.getFactory().createPoint(new Coordinate(0, 2));
    assertEquals(0.0, od.distance(inside), 0.0);
    Point above = stad.getFactory().createPoint(new Coordinate(0, 5));
    assertEquals(2.0, od.distance(above), 1.0e-6);
  }

  /**
   * LEC centre is only required to lie in the boundary; the circle
   * may leave it. Including the ring as an obstacle keeps the
   * circle inside, which is the configuration the location
   * assertions describe.
   */
  private static Geometry withBoundaryObstacle(Geometry obstacles,
      Geometry box) {
    return obstacles.getFactory().createGeometryCollection(
        new Geometry[] { obstacles, box.getBoundary() });
  }

  private static Coordinate apexOf(Coordinate a, Coordinate b, Coordinate c) {
    double[] cc = DiscreteHausdorffDistance.circumcircle(a, b, c);
    return new Coordinate(cc[0], cc[1] + cc[2]);
  }
}
