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
package org.locationtech.jts.algorithm.distance;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularArcDensifier;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * D-HF closed-form subset: public {@link DiscreteHausdorffDistance}
 * on two circular discs and the spec's single-arc witness. Frechet
 * and general arc-length sampling stay in
 * {@code CurveAwarenessSpecTest#test_D_HF_hausdorffFrechetCurveAware}.
 */
public class DiscreteHausdorffDistanceCurveTest extends GeometryTestCase {

  private static final String ARC = "CIRCULARSTRING (0 0, 2 3, 10 0)";
  private static final String BASELINE = "LINESTRING (0 0, 10 0)";
  private static final String DISC_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String DISC_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  private static final String MULTI_5 =
      "MULTISURFACE (CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)))";

  /** Apex height of the circle through (0,0), (2,3), (10,0). */
  private static final double APEX = Math.sqrt(949.0) / 6.0 - 7.0 / 6.0;
  private static final double TOL = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(DiscreteHausdorffDistanceCurveTest.class);
  }

  public DiscreteHausdorffDistanceCurveTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testArcToSegmentIsApexNotFarEnd() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry seg = readCurve(BASELINE);
    double oriented = DiscreteHausdorffDistance.orientedDistance(arc, seg);
    assertEquals("apex, not the far-end chord 10 or mid-control 3",
        APEX, oriented, TOL);
    assertTrue("must not report the far-end chord", oriented < 9.0);
    assertEquals(oriented,
        new DiscreteHausdorffDistance(arc, seg).orientedDistance(), 0.0);
    assertEquals(oriented,
        DiscreteHausdorffDistance.orientedDistance(arc, seg, 0.05), TOL);
    Coordinate[] a = arc.getCoordinates();
    Coordinate[] b = seg.getCoordinates();
    assertEquals(oriented, CircularArcDensifier.directedHausdorffArcToSegment(
        a[0], a[1], a[2], b[0], b[1]), 0.0);
  }

  public void testArcToSegmentDistanceLineLiesOnInputs() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry seg = readCurve(BASELINE);
    LineString line = DiscreteHausdorffDistance.orientedDistanceLine(arc, seg);
    assertEquals(APEX, line.getLength(), TOL);
    Coordinate p0 = line.getCoordinateN(0);
    Coordinate p1 = line.getCoordinateN(1);
    assertEquals("apex x", 5.0, p0.x, 1.0e-6);
    assertEquals("apex y", APEX, p0.y, 1.0e-6);
    assertEquals(0.0, p1.y, 1.0e-9);
    assertTrue("first endpoint on the arc", onArc(p0, arc));
    assertTrue("second endpoint on the segment", onSegment(p1, seg));
  }

  public void testTwoDiscsMatchCircleToCircle() throws Exception {
    Geometry a = readCurve(DISC_5);
    Geometry b = readCurve(DISC_CROSSING);
    double expected = CircularArcDensifier.directedHausdorffCircleToCircle(
        0, 0, 5, 7, 0, 5);
    double oriented = DiscreteHausdorffDistance.orientedDistance(a, b);
    double undirected = DiscreteHausdorffDistance.distance(a, b);
    assertEquals(expected, oriented, TOL);
    double back = CircularArcDensifier.directedHausdorffCircleToCircle(
        7, 0, 5, 0, 0, 5);
    assertEquals(Math.max(expected, back), undirected, TOL);
    assertEquals(oriented,
        new DiscreteHausdorffDistance(a, b).orientedDistance(), 0.0);
    LineString line = DiscreteHausdorffDistance.distanceLine(a, b);
    assertEquals(undirected, line.getLength(), TOL);
    assertTrue(onCircle(line.getCoordinateN(0), 0, 0, 5)
        || onCircle(line.getCoordinateN(0), 7, 0, 5));
    assertTrue(onCircle(line.getCoordinateN(1), 0, 0, 5)
        || onCircle(line.getCoordinateN(1), 7, 0, 5));
  }

  public void testSingleMemberMultiSurfaceUnwraps() throws Exception {
    Geometry a = readCurve(MULTI_5);
    Geometry b = readCurve(DISC_CROSSING);
    assertEquals(
        CircularArcDensifier.directedHausdorffCircleToCircle(0, 0, 5, 7, 0, 5),
        DiscreteHausdorffDistance.orientedDistance(a, b), TOL);
  }

  public void testPlainLineStringStillChordPath() throws Exception {
    Geometry line = readCurve("LINESTRING (0 0, 2 3, 10 0)");
    Geometry seg = readCurve(BASELINE);
    assertEquals(3.0, DiscreteHausdorffDistance.orientedDistance(line, seg), 0.0);
  }

  private static boolean onArc(Coordinate p, Geometry arc) {
    Coordinate[] c = arc.getCoordinates();
    double[] circ = CircularArcDensifier.circumcircle(c[0], c[1], c[2]);
    if (circ == null) return false;
    return Math.abs(Math.hypot(p.x - circ[0], p.y - circ[1]) - circ[2]) < 1.0e-6;
  }

  private static boolean onSegment(Coordinate p, Geometry seg) {
    Coordinate a = seg.getCoordinates()[0];
    Coordinate b = seg.getCoordinates()[1];
    double ab = a.distance(b);
    return Math.abs(a.distance(p) + p.distance(b) - ab) < 1.0e-8;
  }

  private static boolean onCircle(Coordinate p, double cx, double cy, double r) {
    return Math.abs(Math.hypot(p.x - cx, p.y - cy) - r) < 1.0e-6;
  }
}
