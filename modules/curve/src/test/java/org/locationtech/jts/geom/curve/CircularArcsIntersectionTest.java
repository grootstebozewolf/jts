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

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * N-AL (#1195): {@link CircularArcs#intersectSegment} returns the points where a
 * circular arc meets a line segment, restricted to the arc's swept span and the
 * segment extent. Verified against closed-form circle/line intersections.
 * <p>
 * Arc used in most cases: the upper semicircle of radius 5 about the origin,
 * (5,0) - (0,5) - (-5,0).
 */
public class CircularArcsIntersectionTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(CircularArcsIntersectionTest.class); }
  public CircularArcsIntersectionTest(String name) { super(name); }

  // upper semicircle R=5
  private double[][] hit(double px, double py, double qx, double qy) {
    return CircularArcs.intersectSegment(5,0, 0,5, -5,0, px,py, qx,qy);
  }

  private static boolean has(double[][] pts, double x, double y) {
    for (double[] p : pts)
      if (Math.hypot(p[0] - x, p[1] - y) < 1e-9) return true;
    return false;
  }

  public void testTwoCrossings() {
    double[][] pts = hit(-10,3, 10,3);                 // y=3 -> x=±4 on upper arc
    assertEquals(2, pts.length);
    assertTrue(has(pts, 4, 3));
    assertTrue(has(pts, -4, 3));
  }

  public void testLowerCrossingsExcludedByArcSpan() {
    double[][] pts = hit(-10,-3, 10,-3);               // y=-3 hits the circle but on the lower (excluded) arc
    assertEquals(0, pts.length);
  }

  public void testTangentSinglePoint() {
    double[][] pts = hit(-10,5, 10,5);                 // y=5 tangent at apex
    assertEquals(1, pts.length);
    assertTrue(has(pts, 0, 5));
  }

  public void testMissNoPoints() {
    assertEquals(0, hit(-10,6, 10,6).length);          // y=6 misses the circle
  }

  public void testSegmentExtentClipsOneRoot() {
    // y=0 meets the circle at (±5,0); arc endpoints are (5,0) and (-5,0).
    // Segment (0,0)-(10,0) only contains (5,0).
    double[][] pts = CircularArcs.intersectSegment(5,0, 0,5, -5,0, 0,0, 10,0);
    assertEquals(1, pts.length);
    assertTrue(has(pts, 5, 0));
  }

  public void testOffsetArcCrossing() {
    // circle centre (3,-2), r=2: arc (5,-2)-(3,0)-(1,-2) upper half; vertical line x=3 -> (3,0) apex
    double[][] pts = CircularArcs.intersectSegment(5,-2, 3,0, 1,-2, 3,-10, 3,10);
    assertEquals(1, pts.length);
    assertTrue(has(pts, 3, 0));
  }

  /**
   * Pins counts and points against the exact ARC_SEGMENT_XY oracle
   * (NetTopologySuite.Proofs Rocq/Coq extraction), over 0/1/2 crossings, a
   * tangent, a clipped segment, and major / clockwise / offset arcs.
   */
  public void testMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curve/rocqref/curve_arc_segment_vectors.txt");
    assertNotNull("arc-segment vectors resource", in);
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String line; int checked = 0;
    while ((line = r.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      double[] v = new double[10];
      for (int i = 0; i < 10; i++) v[i] = Double.parseDouble(t[i]);
      int cnt = Integer.parseInt(t[10]);
      double[][] got = CircularArcs.intersectSegment(
          v[0],v[1], v[2],v[3], v[4],v[5], v[6],v[7], v[8],v[9]);
      assertEquals("count for " + s, cnt, got.length);
      for (int k = 0; k < cnt; k++) {
        double ex = Double.parseDouble(t[11 + 2*k]), ey = Double.parseDouble(t[12 + 2*k]);
        assertTrue("expected point (" + ex + "," + ey + ") for " + s, has(got, ex, ey));
      }
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 10);
  }
}
