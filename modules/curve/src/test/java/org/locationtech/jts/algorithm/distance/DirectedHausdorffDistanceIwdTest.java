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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * M.3 D-HF-IWD: {@link DirectedHausdorffDistance#isFullyWithinDistance}
 * uses the certified Curve* oriented distance (arc apex / discs), not
 * the control-polyline lie. Within / beyond the apex; chord path
 * unchanged for plain geometries.
 */
public class DirectedHausdorffDistanceIwdTest extends GeometryTestCase {

  private static final String ARC = "CIRCULARSTRING (0 0, 2 3, 10 0)";
  private static final String BASELINE = "LINESTRING (0 0, 10 0)";
  private static final String DISC_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String DISC_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";

  /** Same closed form as M.1: √949/6 − 7/6. */
  private static final double APEX = Math.sqrt(949.0) / 6.0 - 7.0 / 6.0;
  private static final double TOL = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(DirectedHausdorffDistanceIwdTest.class);
  }

  public DirectedHausdorffDistanceIwdTest(String name) {
    super(name);
  }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testArcWithinJustAboveApexIsFalse() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry seg = readCurve(BASELINE);
    assertEquals(APEX, DirectedHausdorffDistance.distance(arc, seg), TOL);
    assertFalse("just under apex must fail IWD",
        DirectedHausdorffDistance.isFullyWithinDistance(arc, seg, APEX - 1.0e-6));
  }

  public void testArcWithinAtAndAboveApexIsTrue() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry seg = readCurve(BASELINE);
    assertTrue("at apex",
        DirectedHausdorffDistance.isFullyWithinDistance(arc, seg, APEX));
    assertTrue("above apex",
        DirectedHausdorffDistance.isFullyWithinDistance(arc, seg, APEX + 0.1));
  }

  public void testControlPolylineChordLiePreserved() throws Exception {
    Geometry chord = readCurve("LINESTRING (0 0, 2 3, 10 0)");
    Geometry seg = readCurve(BASELINE);
    // Chord HD is mid-control height 3, not the arc apex.
    assertTrue(DirectedHausdorffDistance.isFullyWithinDistance(chord, seg, 3.0));
    assertFalse(DirectedHausdorffDistance.isFullyWithinDistance(chord, seg, 2.999));
    Geometry arc = readCurve(ARC);
    assertFalse("arc beyond chord threshold must fail (sees apex)",
        DirectedHausdorffDistance.isFullyWithinDistance(arc, seg, 3.0));
  }

  public void testTwoDiscsWithinAndBeyond() throws Exception {
    Geometry a = readCurve(DISC_5);
    Geometry b = readCurve(DISC_CROSSING);
    double d = DirectedHausdorffDistance.distance(a, b);
    assertTrue(DirectedHausdorffDistance.isFullyWithinDistance(a, b, d));
    assertTrue(DirectedHausdorffDistance.isFullyWithinDistance(a, b, d + 1.0e-6));
    assertFalse(DirectedHausdorffDistance.isFullyWithinDistance(a, b, d - 1.0e-6));
  }
}
