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

import java.util.Random;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * CCIRC (#1195, novel TAG): {@link CircularArcs#chordCrossesCircle} decides whether
 * a line segment <i>straddles</i> the circle supporting a circular arc &mdash;
 * exactly one endpoint strictly inside it. This is a fast circle-membership clip /
 * Delaunay-style predicate for curved geometry, exactly decidable from the sign of
 * {@code |p-C|^2 - r^2} versus {@code |q-C|^2 - r^2}.
 * <p>
 * Grounded against the NetTopologySuite.Proofs extracted oracle
 * ({@code ARC_CHORD_CROSSES_CIRCLE} mode, exact): the committed
 * {@code curve_arc_chord_circle_vectors.txt} are its TRUE/FALSE verdicts on
 * general-position endpoints. An endpoint within ~1 ULP of the circle is a graze
 * where the fp impl could differ from the exact oracle; such endpoints are
 * excluded from the vectors and bounded by {@link #testAgreesWithExactMembership}.
 */
public class CircularArcChordCircleTest extends TestCase {

  private static final String VECTORS =
      "/org/locationtech/jts/geom/curved/rocqref/curve_arc_chord_circle_vectors.txt";

  public static void main(String[] args) { TestRunner.run(CircularArcChordCircleTest.class); }
  public CircularArcChordCircleTest(String name) { super(name); }

  // ---- structured sanity (upper semicircle, supporting circle r=5 at origin) ----

  private static boolean cc(double px, double py, double qx, double qy) {
    return CircularArcs.chordCrossesCircle(5,0, 0,5, -5,0, px, py, qx, qy);
  }

  public void testBothInsideDoesNotStraddle() {
    assertFalse(cc(3, 0, 0, 3));                 // both within r=5
  }

  public void testInsideToOutsideStraddles() {
    assertTrue(cc(0, 0, 10, 0));                 // centre (in) to (10,0) (out)
  }

  public void testSecantBothOutsideDoesNotStraddle() {
    assertFalse(cc(-10, 0, 10, 0));              // crosses the circle twice, but both ends outside
  }

  public void testDisjointOutsideDoesNotStraddle() {
    assertFalse(cc(10, 10, 20, 20));
  }

  public void testEndpointOnCircleIsNotInside() {
    assertFalse(cc(5, 0, 20, 0));                // (5,0) is on the circle -> not strictly inside
  }

  // ---- oracle pin ----

  public void testMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(VECTORS);
    assertNotNull("chord/circle vectors resource", in);
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String s; int checked = 0, trues = 0;
    while ((s = r.readLine()) != null) {
      s = s.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      boolean expected = "TRUE".equals(t[10]);
      boolean got = CircularArcs.chordCrossesCircle(
          Double.parseDouble(t[0]), Double.parseDouble(t[1]),
          Double.parseDouble(t[2]), Double.parseDouble(t[3]),
          Double.parseDouble(t[4]), Double.parseDouble(t[5]),
          Double.parseDouble(t[6]), Double.parseDouble(t[7]),
          Double.parseDouble(t[8]), Double.parseDouble(t[9]));
      assertEquals("straddle for " + s, expected, got);
      if (expected) trues++;
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 20);
    assertTrue("vector set must contain straddling (TRUE) cases", trues >= 10);
  }

  // ---- adversarial: impl vs exact endpoint-membership, away from circle grazes ----

  /**
   * The predicate must equal "exactly one endpoint inside the circle" computed
   * directly from the (exactly reconstructed) circle centre and radius. A
   * disagreement is permitted only when an endpoint lies within {@code GRAZE} of
   * the circle; a clear-margin disagreement is a real defect and fails.
   */
  public void testAgreesWithExactMembership() {
    final double GRAZE = 1e-7;
    Random rnd = new Random(20260707L);
    int checked = 0;
    for (int i = 0; i < 2000; i++) {
      double r = 2 + rnd.nextDouble() * 12 + 0.137;
      double cx = rnd.nextDouble() * 12 - 6, cy = rnd.nextDouble() * 12 - 6;
      double a0 = rnd.nextDouble() * 2 * Math.PI;
      double sweep = (rnd.nextBoolean() ? 1 : -1) * Math.toRadians(50 + rnd.nextDouble() * 250);
      double[] arc = {
          cx + r * Math.cos(a0),           cy + r * Math.sin(a0),
          cx + r * Math.cos(a0 + sweep/2), cy + r * Math.sin(a0 + sweep/2),
          cx + r * Math.cos(a0 + sweep),   cy + r * Math.sin(a0 + sweep) };
      double[] p = randPoint(rnd, cx, cy, r);
      double[] q = randPoint(rnd, cx, cy, r);
      boolean impl = CircularArcs.chordCrossesCircle(
          arc[0],arc[1],arc[2],arc[3],arc[4],arc[5], p[0],p[1], q[0],q[1]);
      double dp = Math.hypot(p[0] - cx, p[1] - cy) - r;
      double dq = Math.hypot(q[0] - cx, q[1] - cy) - r;
      boolean ref = (dp < 0) != (dq < 0);
      if (impl != ref) {
        assertTrue("clear-margin straddle disagreement (dp=" + dp + ", dq=" + dq + ")",
            Math.abs(dp) <= GRAZE || Math.abs(dq) <= GRAZE);
      }
      checked++;
    }
    assertEquals(2000, checked);
  }

  private static double[] randPoint(Random rnd, double cx, double cy, double r) {
    double ang = rnd.nextDouble() * 2 * Math.PI;
    double rad = rnd.nextBoolean() ? r * (0.1 + rnd.nextDouble() * 0.7)   // inside
                                   : r * (1.2 + rnd.nextDouble() * 1.3);  // outside
    return new double[]{ cx + rad * Math.cos(ang), cy + rad * Math.sin(ang) };
  }
}
