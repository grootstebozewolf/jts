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

import org.locationtech.jts.geom.curved.CircularArcs.SnapDecision;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * PRC-SN (#1195): snapping an arc's control points to a fixed grid preserves the
 * arc only when the snapped circumcentre also lands on the grid (so centre /
 * radius / sweep stay grid-representable); otherwise it must be densified, and a
 * snap that collapses the points is degenerate. {@link CircularArcs#snapDecision}
 * computes this exactly (integer divisibility), and {@link CircularArcs#snapToScale}
 * is the FIXED-precision rounding. Both are pinned against the exact
 * CURVE_SNAP_DECISION / SNAP_SCALED oracle (NetTopologySuite.Proofs Rocq/Coq).
 */
public class CircularArcSnapTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CircularArcSnapTest.class);
  }

  public CircularArcSnapTest(String name) { super(name); }

  /** Semicircle on the integer grid: snapping is identity, centre (0,0) on grid. */
  public void testOnGridArcPreserved() {
    assertEquals(SnapDecision.PRESERVE,
        CircularArcs.snapDecision(5,0, 0,5, -5,0, 1));
  }

  /** Off-grid mid that snaps back to a grid arc with centre (0,0). */
  public void testSnapsBackToGridArc() {
    assertEquals(SnapDecision.PRESERVE,
        CircularArcs.snapDecision(5,0, 3,4, 0,5, 1));
  }

  /** Finer grid where the snapped centre is off-grid: must densify. */
  public void testOffGridCentreDensifies() {
    assertEquals(SnapDecision.DENSIFY,
        CircularArcs.snapDecision(5,0, 3.3,4.1, 0,5, 10));
  }

  /** Collinear control points snap to no arc at all. */
  public void testCollinearIsDegenerate() {
    assertEquals(SnapDecision.DEGEN,
        CircularArcs.snapDecision(0,0, 1,0, 2,0, 1));
  }

  public void testSnapToScaleRounding() {
    assertEquals(3.1, CircularArcs.snapToScale(3.14, 10), 0.0);
    assertEquals(3.0, CircularArcs.snapToScale(3.14, 1), 0.0);
    assertEquals(2.72, CircularArcs.snapToScale(2.71828, 100), 1e-12);
  }

  /** Pins the snap decision against the exact CURVE_SNAP_DECISION oracle vectors. */
  public void testDecisionMatchesOracleVectors() throws Exception {
    java.io.BufferedReader r = reader("curve_snap_decision_vectors.txt");
    String line; int checked = 0;
    while ((line = r.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      SnapDecision got = CircularArcs.snapDecision(
          Double.parseDouble(t[0]), Double.parseDouble(t[1]),
          Double.parseDouble(t[2]), Double.parseDouble(t[3]),
          Double.parseDouble(t[4]), Double.parseDouble(t[5]),
          Long.parseLong(t[6]));
      assertEquals("decision for " + s, SnapDecision.valueOf(t[7]), got);
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 10);
  }

  /** Pins the FIXED-precision rounding against the exact SNAP_SCALED oracle vectors. */
  public void testSnapScaledMatchesOracleVectors() throws Exception {
    java.io.BufferedReader r = reader("curve_snap_scaled_vectors.txt");
    String line; int checked = 0;
    while ((line = r.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      double scale = Double.parseDouble(t[0]);
      assertEquals("snap x for " + s, Double.parseDouble(t[3]),
          CircularArcs.snapToScale(Double.parseDouble(t[1]), scale), 0.0);
      assertEquals("snap y for " + s, Double.parseDouble(t[4]),
          CircularArcs.snapToScale(Double.parseDouble(t[2]), scale), 0.0);
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 5);
  }

  private java.io.BufferedReader reader(String name) {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curved/rocqref/" + name);
    assertNotNull(name + " resource", in);
    return new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
  }
}
