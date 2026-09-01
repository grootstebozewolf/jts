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
 * Certified closed-form subset of public {@link DiscreteFrechetDistance}:
 * two circular discs (F = HD of the boundaries) and the D-HF witness
 * (endpoint-aligned minor arc over its chord). The full TAG
 * (arc-length sample + Frechet) stays in
 * {@code CurveAwarenessSpecTest#test_D_HF_hausdorffFrechetCurveAware}.
 */
public class DiscreteFrechetDistanceCurveTest extends GeometryTestCase {

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
    TestRunner.run(DiscreteFrechetDistanceCurveTest.class);
  }

  public DiscreteFrechetDistanceCurveTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testWitnessArcVsChordIsApexAndEqualsHausdorff() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry seg = readCurve(BASELINE);
    double frechet = DiscreteFrechetDistance.distance(arc, seg);
    double hausdorff = DiscreteHausdorffDistance.distance(arc, seg);
    assertEquals("apex, not mid-control 3 or a discrete coupling onto an endpoint",
        APEX, frechet, TOL);
    assertEquals(hausdorff, frechet, TOL);
    DiscreteFrechetDistance inst = new DiscreteFrechetDistance(arc, seg);
    Coordinate[] pair = inst.getCoordinates();
    assertEquals(frechet, pair[0].distance(pair[1]), TOL);
    assertTrue(DiscreteFrechetDistance.hasCertifiedClosedForm(arc, seg));
    assertEquals(frechet, DiscreteFrechetDistance.distance(seg, arc), TOL);
  }

  public void testTwoDiscsMatchHausdorff() throws Exception {
    Geometry a = readCurve(DISC_5);
    Geometry b = readCurve(DISC_CROSSING);
    double frechet = DiscreteFrechetDistance.distance(a, b);
    double hausdorff = DiscreteHausdorffDistance.distance(a, b);
    assertEquals(hausdorff, frechet, TOL);
    double ab = DiscreteHausdorffDistance.directedHausdorffCircleToCircle(
        0, 0, 5, 7, 0, 5);
    double ba = DiscreteHausdorffDistance.directedHausdorffCircleToCircle(
        7, 0, 5, 0, 0, 5);
    assertEquals(Math.max(ab, ba), frechet, TOL);
    assertTrue(DiscreteFrechetDistance.hasCertifiedClosedForm(a, b));
  }

  public void testEqualDiscsAreZero() throws Exception {
    Geometry a = readCurve(DISC_5);
    Geometry b = readCurve(DISC_5);
    assertEquals(0.0, DiscreteFrechetDistance.distance(a, b), TOL);
    assertEquals(0.0, DiscreteHausdorffDistance.distance(a, b), TOL);
  }

  public void testSingleMemberMultiSurfaceUnwraps() throws Exception {
    Geometry a = readCurve(MULTI_5);
    Geometry b = readCurve(DISC_CROSSING);
    assertEquals(
        DiscreteHausdorffDistance.distance(a, b),
        DiscreteFrechetDistance.distance(a, b), TOL);
  }

  public void testPlainLineStringIsControlDiscrete() throws Exception {
    Geometry line = readCurve("LINESTRING (0 0, 2 3, 10 0)");
    Geometry seg = readCurve(BASELINE);
    double frechet = DiscreteFrechetDistance.distance(line, seg);
    assertEquals(Math.sqrt(13.0), frechet, TOL);
    assertTrue("must not treat a LineString as an arc",
        Math.abs(frechet - APEX) > 0.1);
    assertTrue(!DiscreteFrechetDistance.hasCertifiedClosedForm(line, seg));
  }

  public void testReversedSegmentIsNotClosedForm() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry reversed = readCurve("LINESTRING (10 0, 0 0)");
    assertTrue(!DiscreteFrechetDistance.hasCertifiedClosedForm(arc, reversed));
    double frechet = DiscreteFrechetDistance.distance(arc, reversed);
    assertEquals(10.0, frechet, TOL);
    assertTrue(Math.abs(frechet - APEX) > 0.1);
  }

  /**
   * M.5: concentric full-circle rings — continuous F = |R−r|.
   */
  public void testConcentricRingsContinuousFrechetIsRadiusGap() throws Exception {
    Geometry outer = readCurve(
        "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)");
    Geometry inner = readCurve(
        "CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0)");
    assertTrue(DiscreteFrechetDistance.hasCertifiedClosedForm(outer, inner));
    assertEquals(2.0, DiscreteFrechetDistance.distance(outer, inner), TOL);
    assertEquals(2.0, DiscreteFrechetDistance.distance(inner, outer), TOL);
    // Discrete Hausdorff on control diamonds is not the continuous answer.
    assertTrue(Math.abs(DiscreteHausdorffDistance.distance(outer, inner) - 2.0)
        > 0.1);
  }

  public void testOffCentreRingsAreNotConcentricCell() throws Exception {
    Geometry a = readCurve(
        "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)");
    Geometry b = readCurve(
        "CIRCULARSTRING (-3 2, 0 5, 3 2, 0 -1, -3 2)");
    assertTrue(!DiscreteFrechetDistance.hasCertifiedClosedForm(a, b));
  }
}
