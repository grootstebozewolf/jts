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
 * D-AA family (#1195): {@link CircularArcs#distanceArcToSegment} is the
 * analytical minimum distance between a circular arc and a line segment (zero
 * when they intersect) — the arc/line building block for {@code DistanceOp} on
 * curved inputs (D-OP). Pinned against the exact ARC_SEGMENT_DISTANCE oracle
 * (NetTopologySuite.Proofs Rocq/Coq extraction) plus geometric anchors. The
 * common arc is the upper semicircle R=5 about the origin.
 */
public class CircularArcSegmentDistanceTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CircularArcSegmentDistanceTest.class);
  }

  public CircularArcSegmentDistanceTest(String name) { super(name); }

  private static double d(double px, double py, double qx, double qy) {
    return CircularArcs.distanceArcToSegment(5,0, 0,5, -5,0, px,py, qx,qy);
  }

  public void testCrossingIsZero() {
    assertEquals(0.0, d(-5,4, 5,4), 1e-9);          // y=4 crosses at (±3,4)
  }

  public void testApexToLineInterior() {
    assertEquals(3.0, d(-5,8, 5,8), 1e-9);          // apex (0,5) to line y=8
  }

  public void testEndpointToFarSegment() {
    assertEquals(15.0, d(20,-5, 20,5), 1e-9);       // (5,0) to vertical x=20
  }

  public void testInteriorApproachShortSegment() {
    assertEquals(2.0, d(-2,7, 2,7), 1e-9);          // apex (0,5) to short line y=7
  }

  /** A segment whose perpendicular foot is off the arc span falls to an endpoint. */
  public void testOffSpanFallsToEndpoint() {
    // vertical line x=8 to the right; nearest arc point is the endpoint (5,0)
    assertEquals(3.0, d(8,-5, 8,5), 1e-9);
  }

  /** Pins distances against the exact ARC_SEGMENT_DISTANCE oracle vectors. */
  public void testMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curved/rocqref/curve_arc_segment_distance_vectors.txt");
    assertNotNull("arc-segment distance vectors resource", in);
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String line; int checked = 0;
    while ((line = r.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      double got = CircularArcs.distanceArcToSegment(
          Double.parseDouble(t[0]), Double.parseDouble(t[1]), Double.parseDouble(t[2]),
          Double.parseDouble(t[3]), Double.parseDouble(t[4]), Double.parseDouble(t[5]),
          Double.parseDouble(t[6]), Double.parseDouble(t[7]), Double.parseDouble(t[8]), Double.parseDouble(t[9]));
      double exp = Double.parseDouble(t[10]);
      assertEquals("distance for " + s, exp, got, 1e-9 * Math.max(1.0, Math.abs(exp)));
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 10);
  }
}
