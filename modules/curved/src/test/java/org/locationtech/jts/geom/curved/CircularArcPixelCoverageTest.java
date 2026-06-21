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
 * PIX (#1195, novel TAG): {@link CircularArcs#passesThroughPixel} decides whether
 * a circular arc passes through the unit grid cell centred on an integer pixel
 * (the closed square {@code [ix-0.5,ix+0.5] x [iy-0.5,iy+0.5]}). This is the
 * arc-aware grid-coverage / rasterisation primitive motivating the epic
 * (TestBuilder display of curved results, snap-to-grid), and it builds on the
 * already-oracle-pinned arc/segment intersection (N-AL).
 * <p>
 * Grounded against the NetTopologySuite.Proofs extracted oracle
 * ({@code ARC_PASSES_THROUGH_PIXEL} mode, exact): the committed
 * {@code curve_arc_pixel_vectors.txt} are the oracle's TRUE/FALSE verdicts on
 * general-position arcs. Pixels within ~1 ULP of an edge/corner graze are
 * excluded from the vectors: there the floating-point implementation can differ
 * from the exact oracle, which {@link #testAgreesWithDenseSamplingAwayFromGrazes}
 * characterises (agreement is exact except in that vanishing boundary band).
 */
public class CircularArcPixelCoverageTest extends TestCase {

  private static final String VECTORS =
      "/org/locationtech/jts/geom/curved/rocqref/curve_arc_pixel_vectors.txt";

  public static void main(String[] args) { TestRunner.run(CircularArcPixelCoverageTest.class); }
  public CircularArcPixelCoverageTest(String name) { super(name); }

  // ---- structured sanity (upper semicircle r=5: (5,0)-(0,5)-(-5,0)) ----

  public void testEndpointPixel() {
    // (5,0) is an endpoint -> its pixel is covered
    assertTrue(CircularArcs.passesThroughPixel(5,0, 0,5, -5,0, 5, 0));
  }

  public void testApexPixel() {
    // apex (0,5) -> pixel (0,5) covered; pixel (0,4) below is not
    assertTrue(CircularArcs.passesThroughPixel(5,0, 0,5, -5,0, 0, 5));
    assertFalse(CircularArcs.passesThroughPixel(5,0, 0,5, -5,0, 0, 4));
  }

  public void testCellOnArcPathCoveredAndInteriorNot() {
    // the arc runs near (4,3) and (3,4); the cell at (3,3) sits inside the disk,
    // below the arc, and is not touched
    assertTrue(CircularArcs.passesThroughPixel(5,0, 0,5, -5,0, 4, 3));
    assertTrue(CircularArcs.passesThroughPixel(5,0, 0,5, -5,0, 3, 4));
    assertFalse(CircularArcs.passesThroughPixel(5,0, 0,5, -5,0, 3, 3));
  }

  public void testLowerArcExcludedBySpan() {
    // the upper semicircle does not reach the lower pixels of the same circle
    assertFalse(CircularArcs.passesThroughPixel(5,0, 0,5, -5,0, 0, -5));
    assertFalse(CircularArcs.passesThroughPixel(5,0, 0,5, -5,0, 4, -3));
  }

  // ---- oracle pin ----

  /** Every committed oracle vector: the impl verdict must match exactly. */
  public void testMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(VECTORS);
    assertNotNull("pixel vectors resource", in);
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String s; int checked = 0, trues = 0;
    while ((s = r.readLine()) != null) {
      s = s.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      boolean expected = "TRUE".equals(t[8]);
      boolean got = CircularArcs.passesThroughPixel(
          Double.parseDouble(t[0]), Double.parseDouble(t[1]),
          Double.parseDouble(t[2]), Double.parseDouble(t[3]),
          Double.parseDouble(t[4]), Double.parseDouble(t[5]),
          Long.parseLong(t[6]), Long.parseLong(t[7]));
      assertEquals("pixel coverage for " + s, expected, got);
      if (expected) trues++;
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 20);
    assertTrue("vector set must contain covered (TRUE) pixels", trues >= 10);
  }

  // ---- adversarial: impl vs a dense-sampling reference, away from grazes ----

  /**
   * Hunts for disagreements between {@link CircularArcs#passesThroughPixel} and a
   * brute-force densely-sampled reference. A disagreement is permitted only when
   * the arc grazes the cell boundary within {@code GRAZE} (the floating-point
   * boundary band the oracle vectors also exclude); a disagreement with a clear
   * geometric margin would be a real defect and fails.
   */
  public void testAgreesWithDenseSamplingAwayFromGrazes() {
    final double GRAZE = 1e-6;
    Random rnd = new Random(20260706L);
    int checked = 0;
    for (int i = 0; i < 400; i++) {
      double r = 2 + rnd.nextDouble() * 12 + 0.1234;
      double cx = rnd.nextDouble() * 12 - 6, cy = rnd.nextDouble() * 12 - 6;
      double a0 = rnd.nextDouble() * 2 * Math.PI;
      double sweep = (rnd.nextBoolean() ? 1 : -1) * Math.toRadians(40 + rnd.nextDouble() * 260);
      double[] arc = {
          cx + r * Math.cos(a0),            cy + r * Math.sin(a0),
          cx + r * Math.cos(a0 + sweep/2),  cy + r * Math.sin(a0 + sweep/2),
          cx + r * Math.cos(a0 + sweep),    cy + r * Math.sin(a0 + sweep) };
      // a pixel somewhere on/near the arc's bounding box
      long ix = Math.round(cx + r * Math.cos(a0 + sweep * rnd.nextDouble()));
      long iy = Math.round(cy + r * Math.sin(a0 + sweep * rnd.nextDouble()));
      boolean impl = CircularArcs.passesThroughPixel(arc[0],arc[1],arc[2],arc[3],arc[4],arc[5], ix, iy);
      double margin = denseRefMargin(arc, cx, cy, r, a0, sweep, ix, iy);
      boolean ref = margin <= 0;               // <=0 means a sample fell inside the closed cell
      if (impl != ref) {
        assertTrue("clear-margin disagreement at pixel (" + ix + "," + iy + "), margin=" + margin,
            Math.abs(margin) <= GRAZE);
      }
      checked++;
    }
    assertEquals(400, checked);
  }

  /**
   * Signed margin of the densely-sampled arc to the closed cell: {@code <= 0} if a
   * sample lies inside the cell (covered), else the smallest distance any sample
   * came to the cell.
   */
  private static double denseRefMargin(double[] arc, double cx, double cy, double r,
                                       double a0, double sweep, long ix, long iy) {
    double x0 = ix - 0.5, x1 = ix + 0.5, y0 = iy - 0.5, y1 = iy + 0.5;
    int N = 20000;
    double best = Double.MAX_VALUE;
    for (int k = 0; k <= N; k++) {
      double ang = a0 + sweep * k / N;
      double x = cx + r * Math.cos(ang), y = cy + r * Math.sin(ang);
      if (x >= x0 && x <= x1 && y >= y0 && y <= y1) return -1;          // inside
      double dx = Math.max(Math.max(x0 - x, x - x1), 0);
      double dy = Math.max(Math.max(y0 - y, y - y1), 0);
      best = Math.min(best, Math.hypot(dx, dy));
    }
    return best;
  }
}
