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
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * M.1 D-HF-DIR: public {@link DirectedHausdorffDistance} owns Curve*
 * for the same two certified pairs as DiscreteHausdorffDistance —
 * arc→segment apex and two discs. DHD sees the arc, not the control
 * polyline. Do not add named pairs. Full D-HF TAG stays red in
 * {@code CurveAwarenessSpecTest}.
 */
public class DirectedHausdorffDistanceCurveTest extends GeometryTestCase {

  private static final String ARC = "CIRCULARSTRING (0 0, 2 3, 10 0)";
  private static final String BASELINE = "LINESTRING (0 0, 10 0)";
  private static final String DISC_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String DISC_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";

  private static final double APEX = Math.sqrt(949.0) / 6.0 - 7.0 / 6.0;
  private static final double TOL = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(DirectedHausdorffDistanceCurveTest.class);
  }

  public DirectedHausdorffDistanceCurveTest(String name) {
    super(name);
  }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testArcToSegmentIsApexNotControlChord() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry seg = readCurve(BASELINE);
    double dhd = DirectedHausdorffDistance.distance(arc, seg);
    assertEquals("M.1: DirectedHausdorff sees arc apex", APEX, dhd, TOL);
    assertTrue("must not report mid-control chord height 3", dhd > 3.0);
    assertEquals(dhd, DiscreteHausdorffDistance.orientedDistance(arc, seg), TOL);

    Coordinate[] pts = DirectedHausdorffDistance.distancePoints(arc, seg);
    assertNotNull(pts);
    assertEquals(2, pts.length);
    assertEquals(APEX, pts[0].distance(pts[1]), TOL);

    // Control polyline of the same three points is the chord lie.
    Geometry chord = readCurve("LINESTRING (0 0, 2 3, 10 0)");
    assertEquals(3.0, DirectedHausdorffDistance.distance(chord, seg), 0.0);
  }

  public void testTwoDiscsMatchCircleToCircle() throws Exception {
    Geometry a = readCurve(DISC_5);
    Geometry b = readCurve(DISC_CROSSING);
    double expected = DiscreteHausdorffDistance.directedHausdorffCircleToCircle(
        0, 0, 5, 7, 0, 5);
    double dhd = DirectedHausdorffDistance.distance(a, b);
    assertEquals(expected, dhd, TOL);
    assertEquals(dhd, DiscreteHausdorffDistance.orientedDistance(a, b), TOL);
  }

  public void testInstanceFarthestPointsMatchStatic() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry seg = readCurve(BASELINE);
    DirectedHausdorffDistance hd = new DirectedHausdorffDistance(seg);
    Coordinate[] pts = hd.farthestPoints(arc);
    assertEquals(APEX, pts[0].distance(pts[1]), TOL);
  }
}
