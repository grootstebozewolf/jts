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

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * V-CP / R-CONT building block (#1195): {@link ArcRingLocation#isInteriorPoint}
 * is the arc-aware strict-interior point-in-ring test for a curved control
 * sequence. Pinned against the exact POINT_IN_CURVE_RING oracle
 * (NetTopologySuite.Proofs Rocq/Coq extraction) in general position, plus
 * geometric anchors.
 */
public class ArcRingLocationTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(ArcRingLocationTest.class);
  }

  public ArcRingLocationTest(String name) { super(name); }

  private static CoordinateArraySequence seq(double... xy) {
    Coordinate[] p = new Coordinate[xy.length / 2];
    for (int i = 0; i < p.length; i++) p[i] = new Coordinate(xy[2*i], xy[2*i+1]);
    return new CoordinateArraySequence(p);
  }

  /** Half-disk (chord + upper arc): interior above the chord and inside the arc. */
  public void testHalfDisk() {
    CoordinateArraySequence ring = seq(-5,0, 0,0, 5,0,  0,5, -5,0);   // chord (-5,0)-(5,0) then upper arc
    assertTrue(ArcRingLocation.isInteriorPoint(ring, 0, 2));
    assertTrue(ArcRingLocation.isInteriorPoint(ring, 4, 1));          // near the arc on +x (was the fixed band bug)
    assertTrue(ArcRingLocation.isInteriorPoint(ring, -3, 1));
    assertFalse(ArcRingLocation.isInteriorPoint(ring, 0, -2));        // below the chord
    assertFalse(ArcRingLocation.isInteriorPoint(ring, 0, 6));         // above the arc
    assertFalse(ArcRingLocation.isInteriorPoint(ring, 4, 3));         // outside the circle (dist 5)
  }

  /** Full 4-arc circle: interior points in general position. */
  public void testFourArcCircle() {
    double q = 3.5355339059327378;
    CoordinateArraySequence ring = seq(5,0, q,q, 0,5, -q,q, -5,0, -q,-q, 0,-5, q,-q, 5,0);
    assertTrue(ArcRingLocation.isInteriorPoint(ring, 3, 3));
    assertTrue(ArcRingLocation.isInteriorPoint(ring, -3, 2));
    assertFalse(ArcRingLocation.isInteriorPoint(ring, 4, 4));         // outside
    assertFalse(ArcRingLocation.isInteriorPoint(ring, 10, 1));        // outside
  }

  /** Pins against the exact POINT_IN_CURVE_RING oracle vectors (general position). */
  public void testMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curved/rocqref/curve_point_in_ring_vectors.txt");
    assertNotNull("point-in-ring vectors resource", in);
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String line; int checked = 0;
    while ((line = r.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      int npts = Integer.parseInt(t[0]);
      List<Coordinate> pts = new ArrayList<Coordinate>();
      for (int i = 0; i < npts; i++)
        pts.add(new Coordinate(Double.parseDouble(t[1 + 2*i]), Double.parseDouble(t[2 + 2*i])));
      int base = 1 + 2*npts;
      double qx = Double.parseDouble(t[base]), qy = Double.parseDouble(t[base + 1]);
      boolean expected = "IN".equals(t[base + 2]);
      boolean got = ArcRingLocation.isInteriorPoint(
          new CoordinateArraySequence(pts.toArray(new Coordinate[0])), qx, qy);
      assertEquals("verdict for " + s, expected, got);
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 20);
  }
}
