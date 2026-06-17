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
package org.locationtech.jts.geom.curved;

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

  public static void main(String args[]) {
    TestRunner.run(CircularArcsIntersectionTest.class);
  }

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
}
