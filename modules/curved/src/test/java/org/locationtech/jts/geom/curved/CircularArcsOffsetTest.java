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
 * OFF (#1195): {@link CircularArcs#offsetArc} offsets a circular arc radially by
 * a signed distance — same centre and sweep, radius {@code r + d} — preserving
 * arc identity (the R±d parallel arcs), and collapses to {@code null} when
 * {@code r + d <= 0}. Pinned against the exact ARC_OFFSET_XY oracle
 * (NetTopologySuite.Proofs Rocq/Coq extraction) plus geometric anchors.
 */
public class CircularArcsOffsetTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CircularArcsOffsetTest.class);
  }

  public CircularArcsOffsetTest(String name) { super(name); }

  /** Outward offset of the R=5 upper semicircle by 1 -> R=6 arc, same centre/sweep. */
  public void testOutwardOffset() {
    double[] o = CircularArcs.offsetArc(5,0, 0,5, -5,0, 1.0);
    assertNotNull(o);
    assertArc(o, 6,0, 0,6, -6,0);
  }

  /** Inward offset by -1 -> R=4 arc. */
  public void testInwardOffset() {
    double[] o = CircularArcs.offsetArc(5,0, 0,5, -5,0, -1.0);
    assertArc(o, 4,0, 0,4, -4,0);
  }

  /** Offset that reaches the centre (r + d == 0) collapses to empty (null). */
  public void testCollapseAtCentre() {
    assertNull(CircularArcs.offsetArc(5,0, 0,5, -5,0, -5.0));
  }

  /** Offset with |d| > r collapses to empty (null). */
  public void testCollapseBeyondCentre() {
    assertNull(CircularArcs.offsetArc(5,0, 0,5, -5,0, -7.0));
  }

  /** Collinear (non-circular) triple has no circle: null. */
  public void testCollinearIsNull() {
    assertNull(CircularArcs.offsetArc(0,0, 1,0, 2,0, 1.0));
  }

  /** Pins offsets against the exact ARC_OFFSET_XY oracle vectors (including EMPTY). */
  public void testMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curved/rocqref/curve_arc_offset_vectors.txt");
    assertNotNull("offset vectors resource", in);
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String line; int checked = 0;
    while ((line = r.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      double[] o = CircularArcs.offsetArc(
          Double.parseDouble(t[0]), Double.parseDouble(t[1]), Double.parseDouble(t[2]),
          Double.parseDouble(t[3]), Double.parseDouble(t[4]), Double.parseDouble(t[5]),
          Double.parseDouble(t[6]));
      if ("EMPTY".equals(t[7])) {
        assertNull("expected EMPTY for " + s, o);
      } else {
        assertNotNull("expected an arc for " + s, o);
        for (int i = 0; i < 6; i++) {
          double exp = Double.parseDouble(t[7 + i]);
          assertEquals("ord " + i + " for " + s, exp, o[i], 1e-9 * Math.max(1.0, Math.abs(exp)));
        }
      }
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 10);
  }

  private static void assertArc(double[] o, double sx, double sy, double mx, double my, double ex, double ey) {
    assertNotNull(o);
    double[] exp = { sx, sy, mx, my, ex, ey };
    for (int i = 0; i < 6; i++) assertEquals("ordinate " + i, exp[i], o[i], 1e-9);
  }
}
