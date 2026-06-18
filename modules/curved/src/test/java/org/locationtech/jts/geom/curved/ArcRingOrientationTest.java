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
 * V-CP building block (#1195): {@link ArcRing#signedArea} / {@link ArcRing#isCCW}
 * is the arc-aware signed area and orientation of a closed curved ring. Pinned
 * against the exact RING_ORIENTATION oracle (NetTopologySuite.Proofs Rocq/Coq
 * extraction), plus geometric anchors.
 */
public class ArcRingOrientationTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(ArcRingOrientationTest.class);
  }

  public ArcRingOrientationTest(String name) { super(name); }

  private static CoordinateArraySequence seq(double... xy) {
    Coordinate[] p = new Coordinate[xy.length / 2];
    for (int i = 0; i < p.length; i++) p[i] = new Coordinate(xy[2*i], xy[2*i+1]);
    return new CoordinateArraySequence(p);
  }

  private static final double Q = 3.5355339059327378;

  /** CCW full circle (4 arcs) encloses +pi r^2. */
  public void testCcwCircleArea() {
    CoordinateArraySequence ring = seq(5,0, Q,Q, 0,5, -Q,Q, -5,0, -Q,-Q, 0,-5, Q,-Q, 5,0);
    assertTrue(ArcRing.isCCW(ring));
    assertEquals(Math.PI * 25, ArcRing.signedArea(ring), 1e-9);
  }

  /** Reversing the ring flips the sign (CW, -pi r^2). */
  public void testCwCircleArea() {
    CoordinateArraySequence ring = seq(5,0, Q,-Q, 0,-5, -Q,-Q, -5,0, -Q,Q, 0,5, Q,Q, 5,0);
    assertFalse(ArcRing.isCCW(ring));
    assertEquals(-Math.PI * 25, ArcRing.signedArea(ring), 1e-9);
  }

  /** Half-disk (chord + upper arc) is half the disk area, CCW. */
  public void testHalfDiskArea() {
    CoordinateArraySequence ring = seq(-5,0, 0,0, 5,0,  0,5, -5,0);
    assertTrue(ArcRing.isCCW(ring));
    assertEquals(Math.PI * 25 / 2, ArcRing.signedArea(ring), 1e-9);
  }

  /** Straight (chord) triangle: arc-aware area equals the plain triangle area. */
  public void testChordTriangleArea() {
    CoordinateArraySequence ring = seq(0,0, 2,0, 4,0, 3,1.5, 2,3, 1,1.5, 0,0);  // triangle (0,0)(4,0)(2,3)
    assertEquals(6.0, ArcRing.signedArea(ring), 1e-9);
  }

  /** Pins orientation label + signed area against the exact RING_ORIENTATION oracle. */
  public void testMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curved/rocqref/curve_ring_orientation_vectors.txt");
    assertNotNull("ring orientation vectors resource", in);
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
      boolean expectCcw = "CCW".equals(t[base]);
      double expectArea = Double.parseDouble(t[base + 1]);
      CoordinateArraySequence ring = new CoordinateArraySequence(pts.toArray(new Coordinate[0]));
      assertEquals("signed area for " + s, expectArea, ArcRing.signedArea(ring), 1e-6 * Math.max(1.0, Math.abs(expectArea)));
      if (Math.abs(expectArea) > 1e-9)
        assertEquals("orientation for " + s, expectCcw, ArcRing.isCCW(ring));
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 10);
  }
}
