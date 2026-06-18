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
 * D-AA (#1195): {@link CircularArcs#distanceArcToArc} is the analytical minimum
 * distance between two circular arcs, each clamped to its sweep (zero when they
 * intersect). The closed-form solve — endpoint projections plus the line-of-
 * centres interior approach — is pinned against the exact ARC_ARC_DISTANCE oracle
 * (NetTopologySuite.Proofs Rocq/Coq extraction), with a few geometric anchors.
 */
public class CircularArcsArcDistanceTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CircularArcsArcDistanceTest.class);
  }

  public CircularArcsArcDistanceTest(String name) { super(name); }

  private static double d(double[] a, double[] b) {
    return CircularArcs.distanceArcToArc(a[0],a[1],a[2],a[3],a[4],a[5], b[0],b[1],b[2],b[3],b[4],b[5]);
  }

  /** Intersecting arcs are zero distance apart. */
  public void testIntersectingIsZero() {
    assertEquals(0.0, d(new double[]{0,5,5,0,0,-5}, new double[]{6,5,1,0,6,-5}), 1e-9);
  }

  /** Disjoint arcs: nearest endpoints (5,0)-(15,0) are 10 apart. */
  public void testDisjointEndpoints() {
    assertEquals(10.0, d(new double[]{5,0,0,5,-5,0}, new double[]{25,0,20,5,15,0}), 1e-9);
  }

  /** Concentric arcs whose spans overlap are |rA - rB| apart. */
  public void testConcentricRadialGap() {
    // R=5 upper semi vs R=3 arc that overlaps in direction -> gap 2
    double[] a = {5,0, 0,5, -5,0};
    double[] b = {3,0, 0,3, -3,0};
    assertEquals(2.0, d(a, b), 1e-9);
  }

  /** Interior closest approach along the line of centres: apex (0,5) to nadir (0,7). */
  public void testInteriorApproach() {
    // A: upper semicircle centre (0,0) r5 (apex (0,5)); B: lower semicircle centre
    // (0,12) r5 (nadir (0,7)). Centres 12 apart (> 10), so disjoint; gap = 12-5-5 = 2.
    double[] a = {5,0, 0,5, -5,0};
    double[] b = {5,12, 0,7, -5,12};
    assertEquals(2.0, d(a, b), 1e-9);
  }

  /** Symmetry: distance(A,B) == distance(B,A). */
  public void testSymmetry() {
    double[] a = {5,0, 0,5, -5,0};
    double[] b = {25,0, 20,5, 15,0};
    assertEquals(d(a, b), d(b, a), 1e-12);
  }

  /** Pins distances against the exact ARC_ARC_DISTANCE oracle vectors. */
  public void testMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curved/rocqref/curve_arc_arc_distance_vectors.txt");
    assertNotNull("arc-arc distance vectors resource", in);
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String line; int checked = 0;
    while ((line = r.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      double got = CircularArcs.distanceArcToArc(
          Double.parseDouble(t[0]), Double.parseDouble(t[1]), Double.parseDouble(t[2]),
          Double.parseDouble(t[3]), Double.parseDouble(t[4]), Double.parseDouble(t[5]),
          Double.parseDouble(t[6]), Double.parseDouble(t[7]), Double.parseDouble(t[8]),
          Double.parseDouble(t[9]), Double.parseDouble(t[10]), Double.parseDouble(t[11]));
      double exp = Double.parseDouble(t[12]);
      assertEquals("distance for " + s, exp, got, 1e-9 * Math.max(1.0, Math.abs(exp)));
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 10);
  }
}
