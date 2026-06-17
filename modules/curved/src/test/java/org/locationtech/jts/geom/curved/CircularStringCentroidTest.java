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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * C-LIN (#1195): {@link CircularString#getCentroid()} must be the arc-length
 * weighted centroid of the circular arcs (each arc centroid at distance
 * {@code r*sin(theta/2)/(theta/2)} from the centre along the bisector), not the
 * chord-polyline centroid inherited from {@link LineString}.
 */
public class CircularStringCentroidTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CircularStringCentroidTest.class);
  }

  public CircularStringCentroidTest(String name) { super(name); }

  private final CurvedGeometryFactory gf = new CurvedGeometryFactory();

  private CircularString cs(double... xy) {
    Coordinate[] pts = new Coordinate[xy.length / 2];
    for (int i = 0; i < pts.length; i++)
      pts[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
    return gf.createCircularString(new CoordinateArraySequence(pts));
  }

  private static final double R = 5;

  /** Semicircle arc centroid is at (0, 2R/pi). */
  public void testSemicircleCentroid() {
    Point c = cs(R,0, 0,R, -R,0).getCentroid();
    assertEquals(0.0, c.getX(), 1e-9);
    assertEquals(2 * R / Math.PI, c.getY(), 1e-9);
  }

  /** Quarter-circle arc centroid is at (2R/pi, 2R/pi). */
  public void testQuarterCircleCentroid() {
    double q = R / Math.sqrt(2);
    Point c = cs(R,0, q,q, 0,R).getCentroid();
    assertEquals(2 * R / Math.PI, c.getX(), 1e-9);
    assertEquals(2 * R / Math.PI, c.getY(), 1e-9);
  }

  /** Full circle (two semicircles) is centroid-symmetric about its centre. */
  public void testFullCircleCentroidAtCentre() {
    Point c = cs(R,0, 0,R, -R,0, 0,-R, R,0).getCentroid();
    assertEquals(0.0, c.getX(), 1e-9);
    assertEquals(0.0, c.getY(), 1e-9);
  }

  /** Per-arc centroid matches the exact ARC_CENTROID oracle (Rocq/Coq) vectors. */
  public void testArcCentroidMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curved/rocqref/curve_arc_centroid_vectors.txt");
    assertNotNull("centroid vectors resource", in);
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String line; int checked = 0;
    while ((line = r.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      double[] c = CircularArcs.arcCentroid(
          Double.parseDouble(t[0]), Double.parseDouble(t[1]),
          Double.parseDouble(t[2]), Double.parseDouble(t[3]),
          Double.parseDouble(t[4]), Double.parseDouble(t[5]));
      double ecx = Double.parseDouble(t[6]), ecy = Double.parseDouble(t[7]);
      assertEquals("centroid x for " + s, ecx, c[0], 1e-9 * Math.max(1.0, Math.abs(ecx)));
      assertEquals("centroid y for " + s, ecy, c[1], 1e-9 * Math.max(1.0, Math.abs(ecy)));
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 5);
  }
}
