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
 * N-AA (#1195): {@link CircularArcs#intersectArc} is the analytical
 * intersection of two circular arcs, each clamped to its directed sweep. The
 * closed-form circle/circle solve and the sweep clamping are pinned against the
 * exact ARC_ARC_XY oracle (NetTopologySuite.Proofs Rocq/Coq extraction), plus a
 * few hand-checked geometric anchors.
 */
public class CircularArcsArcIntersectionTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CircularArcsArcIntersectionTest.class);
  }

  public CircularArcsArcIntersectionTest(String name) { super(name); }

  /** Two circles R=5 at (0,0) and (6,0) cross at (3,+/-4); right/left semis pick both. */
  public void testTwoCrossings() {
    double[][] x = CircularArcs.intersectArc(0,5, 5,0, 0,-5,  6,5, 1,0, 6,-5);
    assertEquals(2, x.length);
    assertTrue(hasPoint(x, 3, 4));
    assertTrue(hasPoint(x, 3, -4));
  }

  /** Upper semis of the same two circles share only (3,4). */
  public void testSingleCrossing() {
    double[][] x = CircularArcs.intersectArc(5,0, 0,5, -5,0,  11,0, 6,5, 1,0);
    assertEquals(1, x.length);
    assertTrue(hasPoint(x, 3, 4));
  }

  /** Externally tangent circles touch at (5,0), interior to both sweeps. */
  public void testInteriorTangent() {
    double[][] x = CircularArcs.intersectArc(0,-5, 5,0, 0,5,  10,5, 5,0, 10,-5);
    assertEquals(1, x.length);
    assertTrue(hasPoint(x, 5, 0));
  }

  /** Crossing circles, but the second arc's sweep avoids both crossings. */
  public void testOutOfSweepIsEmpty() {
    double[][] x = CircularArcs.intersectArc(5,0, 0,5, -5,0,  11,0, 6,-5, 1,0);
    assertEquals(0, x.length);
  }

  /** Concentric arcs share a sub-arc, not isolated points: reported empty. */
  public void testConcentricIsEmpty() {
    double[][] x = CircularArcs.intersectArc(5,0, 0,5, -5,0,  3,0, 0,3, -3,0);
    assertEquals(0, x.length);
  }

  /** Pins counts and points against the exact ARC_ARC_XY oracle vectors. */
  public void testMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curved/rocqref/curve_arc_arc_vectors.txt");
    assertNotNull("arc-arc vectors resource", in);
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String line; int checked = 0;
    while ((line = r.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      double[] a = new double[12];
      for (int i = 0; i < 12; i++) a[i] = Double.parseDouble(t[i]);
      int cnt = Integer.parseInt(t[12]);
      double[][] got = CircularArcs.intersectArc(
          a[0],a[1], a[2],a[3], a[4],a[5], a[6],a[7], a[8],a[9], a[10],a[11]);
      assertEquals("count for " + s, cnt, got.length);
      for (int k = 0; k < cnt; k++) {
        double ex = Double.parseDouble(t[13 + 2*k]), ey = Double.parseDouble(t[14 + 2*k]);
        assertTrue("expected point (" + ex + "," + ey + ") for " + s, hasPoint(got, ex, ey));
      }
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 10);
  }

  private static boolean hasPoint(double[][] pts, double x, double y) {
    for (double[] p : pts)
      if (Math.hypot(p[0] - x, p[1] - y) < 1e-7) return true;
    return false;
  }
}
