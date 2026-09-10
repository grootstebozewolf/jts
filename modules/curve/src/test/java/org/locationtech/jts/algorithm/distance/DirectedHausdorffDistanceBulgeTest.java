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
 * M.2 D-HF-ARC bulge sensitivity: same endpoints, different mid-control
 * bulge ⇒ different directed Hausdorff to the baseline. Uses the existing
 * arc→segment closed form (no new named-pair type). Control polylines
 * remain the chord lie.
 */
public class DirectedHausdorffDistanceBulgeTest extends GeometryTestCase {

  private static final String BASE = "LINESTRING (0 0, 10 0)";
  /** Mid not at the apex x — arc HD ≠ mid-control height. */
  private static final String ARC_TALL = "CIRCULARSTRING (0 0, 2 5, 10 0)";
  private static final String ARC_FLAT = "CIRCULARSTRING (0 0, 2 1, 10 0)";
  private static final String CHORD_TALL = "LINESTRING (0 0, 2 5, 10 0)";
  private static final String CHORD_FLAT = "LINESTRING (0 0, 2 1, 10 0)";

  private static final double TOL = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(DirectedHausdorffDistanceBulgeTest.class);
  }

  public DirectedHausdorffDistanceBulgeTest(String name) {
    super(name);
  }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testTallerBulgeHasLargerDirectedHausdorff() throws Exception {
    Geometry base = readCurve(BASE);
    Geometry tall = readCurve(ARC_TALL);
    Geometry flat = readCurve(ARC_FLAT);
    double dTall = DirectedHausdorffDistance.distance(tall, base);
    double dFlat = DirectedHausdorffDistance.distance(flat, base);
    assertTrue("taller bulge must increase directed HD (" + dTall + " > "
        + dFlat + ")", dTall > dFlat + 1.0e-6);
  }

  public void testDirectedMatchesDiscreteClosedFormAtBothBulges()
      throws Exception {
    Geometry base = readCurve(BASE);
    Geometry tall = readCurve(ARC_TALL);
    Geometry flat = readCurve(ARC_FLAT);
    assertEquals(
        DiscreteHausdorffDistance.orientedDistance(tall, base),
        DirectedHausdorffDistance.distance(tall, base), TOL);
    assertEquals(
        DiscreteHausdorffDistance.orientedDistance(flat, base),
        DirectedHausdorffDistance.distance(flat, base), TOL);
  }

  public void testControlPolylineIsNotTheArcAnswer() throws Exception {
    Geometry base = readCurve(BASE);
    Geometry tallArc = readCurve(ARC_TALL);
    Geometry tallChord = readCurve(CHORD_TALL);
    Geometry flatArc = readCurve(ARC_FLAT);
    Geometry flatChord = readCurve(CHORD_FLAT);

    assertEquals(5.0, DirectedHausdorffDistance.distance(tallChord, base), 0.0);
    assertEquals(1.0, DirectedHausdorffDistance.distance(flatChord, base), 0.0);

    double arcTall = DirectedHausdorffDistance.distance(tallArc, base);
    double arcFlat = DirectedHausdorffDistance.distance(flatArc, base);
    assertFalse("tall arc apex must not equal mid-control height 5",
        Math.abs(arcTall - 5.0) < 1.0e-6);
    assertFalse("flat arc apex must not equal mid-control height 1",
        Math.abs(arcFlat - 1.0) < 1.0e-6);
    assertTrue("bulge sensitivity: tall > flat", arcTall > arcFlat + 1.0e-6);
  }
}
