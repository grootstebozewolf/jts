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
 * INC (#1195, novel TAG): {@link CircularArcs#inCircleSign} is the exact
 * in-circumcircle predicate &mdash; the robustness primitive for curved-geometry
 * triangulation / TIN and for detecting concyclic arc control points (so
 * consecutive arcs on one circle can be merged). It returns the exact sign of the
 * incircle determinant: {@code +1} if the query is strictly inside the
 * circumcircle of a CCW triangle, {@code -1} outside, {@code 0} concyclic.
 * <p>
 * Grounded against the NetTopologySuite.Proofs extracted oracle
 * ({@code INCIRCLE_EXACT} mode). Because the predicate is computed in
 * {@link java.math.BigDecimal} from the exact binary value of each {@code double},
 * it is bit-exact &mdash; it agrees with the exact oracle on every case, including
 * exactly concyclic (ZERO) configurations, with <b>no tolerance band</b> (contrast
 * the PIX / CCIRC grazing predicates).
 */
public class CircularArcInCircleTest extends TestCase {

  private static final String VECTORS =
      "/org/locationtech/jts/geom/curved/rocqref/curve_incircle_vectors.txt";

  public static void main(String[] args) { TestRunner.run(CircularArcInCircleTest.class); }
  public CircularArcInCircleTest(String name) { super(name); }

  // ---- structured sanity: CCW triangle (0,0)(4,0)(0,4), circumcircle centre (2,2) ----

  public void testStrictlyInsideIsPositive() {
    assertEquals(1, CircularArcs.inCircleSign(0,0, 4,0, 0,4, 1,1));
  }

  public void testStrictlyOutsideIsNegative() {
    assertEquals(-1, CircularArcs.inCircleSign(0,0, 4,0, 0,4, 10,10));
  }

  public void testConcyclicIsZero() {
    // (0,0)(4,0)(0,4) and (4,4) all lie on the circle centred (2,2), r=2*sqrt2
    assertEquals(0, CircularArcs.inCircleSign(0,0, 4,0, 0,4, 4,4));
    // unit square is concyclic
    assertEquals(0, CircularArcs.inCircleSign(0,0, 1,0, 1,1, 0,1));
  }

  public void testOrientationFlipsSign() {
    // swapping two vertices reverses triangle orientation -> negates the sign
    assertEquals(1,  CircularArcs.inCircleSign(0,0, 4,0, 0,4, 1,1));
    assertEquals(-1, CircularArcs.inCircleSign(0,0, 0,4, 4,0, 1,1));
  }

  public void testQueryOnAVertexIsZero() {
    // a triangle vertex is trivially on its own circumcircle
    assertEquals(0, CircularArcs.inCircleSign(0,0, 4,0, 0,4, 4,0));
  }

  // ---- oracle pin (exact: must match every vector, including ZERO) ----

  public void testMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(VECTORS);
    assertNotNull("incircle vectors resource", in);
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String s; int checked = 0, zeros = 0;
    while ((s = r.readLine()) != null) {
      s = s.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      int expected = Integer.parseInt(t[8]);
      int got = CircularArcs.inCircleSign(
          Double.parseDouble(t[0]), Double.parseDouble(t[1]),
          Double.parseDouble(t[2]), Double.parseDouble(t[3]),
          Double.parseDouble(t[4]), Double.parseDouble(t[5]),
          Double.parseDouble(t[6]), Double.parseDouble(t[7]));
      assertEquals("incircle sign for " + s, expected, got);
      if (expected == 0) zeros++;
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 20);
    assertTrue("vector set must include exactly concyclic (ZERO) cases", zeros >= 5);
  }

  // ---- exactness invariants (no oracle needed at runtime) ----

  /**
   * Two exact invariants over random inputs: swapping a triangle's orientation
   * negates the sign, and four points placed exactly on a constructed circle are
   * always reported concyclic (ZERO) regardless of the query order.
   */
  public void testExactInvariants() {
    Random rnd = new Random(20260708L);
    for (int i = 0; i < 5000; i++) {
      double ax = rnd.nextInt(2001) - 1000, ay = rnd.nextInt(2001) - 1000;
      double bx = rnd.nextInt(2001) - 1000, by = rnd.nextInt(2001) - 1000;
      double cx = rnd.nextInt(2001) - 1000, cy = rnd.nextInt(2001) - 1000;
      double dx = rnd.nextInt(2001) - 1000, dy = rnd.nextInt(2001) - 1000;
      int s1 = CircularArcs.inCircleSign(ax,ay, bx,by, cx,cy, dx,dy);
      int s2 = CircularArcs.inCircleSign(ax,ay, cx,cy, bx,by, dx,dy);   // orientation flipped
      assertEquals("orientation flip must negate the sign", -s1, s2);
    }
    // Pythagorean points on the circle r=5 centred (0,0) are exactly concyclic.
    double[][] onCircle = { {5,0},{0,5},{-5,0},{0,-5},{3,4},{4,3},{-3,4},{4,-3},{-4,-3} };
    for (int i = 0; i < onCircle.length; i++) {
      double[] a = onCircle[i];
      double[] b = onCircle[(i + 1) % onCircle.length];
      double[] c = onCircle[(i + 2) % onCircle.length];
      double[] d = onCircle[(i + 3) % onCircle.length];
      // skip collinear leading triples (none here, but be safe)
      double area2 = (b[0]-a[0])*(c[1]-a[1]) - (b[1]-a[1])*(c[0]-a[0]);
      if (area2 == 0) continue;
      assertEquals("points on a common circle must be concyclic (ZERO)",
          0, CircularArcs.inCircleSign(a[0],a[1], b[0],b[1], c[0],c[1], d[0],d[1]));
    }
  }
}
