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
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * M-AREA-CP (#1195): {@link CurvePolygon#getArea()} must account for the
 * circular segments of curved (CircularString) rings, so a disk described as a
 * closed CircularString has area pi*R^2 -- not the area of the control-point
 * polygon.
 */
public class CurvePolygonAreaTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CurvePolygonAreaTest.class);
  }

  public CurvePolygonAreaTest(String name) { super(name); }

  private final CurvedGeometryFactory gf = new CurvedGeometryFactory();

  private CircularString cs(double... xy) {
    Coordinate[] pts = new Coordinate[xy.length / 2];
    for (int i = 0; i < pts.length; i++)
      pts[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
    return gf.createCircularString(new CoordinateArraySequence(pts));
  }

  private static final double R = 10;
  private static final double DISK = Math.PI * R * R;

  /** Disk as two semicircle arcs (CCW). */
  public void testDiskAreaTwoArcs() {
    CurvePolygon disk = gf.createCurvePolygon(cs(10,0, 0,10, -10,0, 0,-10, 10,0));
    assertEquals(DISK, disk.getArea(), 1e-6 * DISK);
  }

  /**
   * Arc-awareness guard (the core M-AREA-CP requirement): a disk built from
   * arcs must have area &pi;R&sup2;, strictly greater than the inscribed
   * control-point polygon the inherited {@link org.locationtech.jts.geom.Polygon}
   * behaviour would measure. A regression that dropped the circular-segment
   * correction would collapse the two.
   */
  public void testAreaIsArcAwareNotControlPolygon() {
    double q = R / Math.sqrt(2);
    double[] xy = {10,0, q,q, 0,10, -q,q, -10,0, -q,-q, 0,-10, q,-q, 10,0};
    CurvePolygon disk = gf.createCurvePolygon(cs(xy));
    // Shoelace area of the control points (the inscribed polygon).
    double poly = 0;
    for (int i = 0; i + 2 < xy.length; i += 2)
      poly += xy[i] * xy[i + 3] - xy[i + 2] * xy[i + 1];
    poly = Math.abs(poly) / 2;
    assertTrue("control polygon must be inscribed (< disk)", poly < DISK - 1.0);
    assertEquals("arc-aware area is the full disk", DISK, disk.getArea(), 1e-6 * DISK);
    assertTrue("disk area must exceed the inscribed control polygon",
        disk.getArea() > poly + 1.0);
  }

  /** Disk as four quarter arcs. */
  public void testDiskAreaFourArcs() {
    double q = R / Math.sqrt(2);
    CurvePolygon disk = gf.createCurvePolygon(cs(
        10,0, q,q, 0,10, -q,q, -10,0, -q,-q, 0,-10, q,-q, 10,0));
    assertEquals(DISK, disk.getArea(), 1e-6 * DISK);
  }

  /** Orientation must not matter: a clockwise disk has the same area. */
  public void testDiskAreaClockwise() {
    CurvePolygon disk = gf.createCurvePolygon(cs(10,0, 0,-10, -10,0, 0,10, 10,0));
    assertEquals(DISK, disk.getArea(), 1e-6 * DISK);
  }

  /** A circular hole (R=3) is subtracted from the circular shell (R=10): pi*(100-9). */
  public void testDiskWithCircularHole() {
    CircularString shell = cs(10,0, 0,10, -10,0, 0,-10, 10,0);
    CircularString hole  = cs(3,0, 0,3, -3,0, 0,-3, 3,0);
    CurvePolygon ring = gf.createCurvePolygon(shell, new org.locationtech.jts.geom.LineString[]{ hole });
    assertEquals(Math.PI * (100 - 9), ring.getArea(), 1e-6 * Math.PI * 100);
  }

  /** The signed segment magnitude must match the exact oracle ARC_AREA vectors. */
  public void testSegmentAreaMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curved/rocqref/curve_arc_area_vectors.txt");
    assertNotNull("area vectors resource", in);
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String line; int checked = 0;
    while ((line = r.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      double sx = Double.parseDouble(t[0]), sy = Double.parseDouble(t[1]);
      double mx = Double.parseDouble(t[2]), my = Double.parseDouble(t[3]);
      double ex = Double.parseDouble(t[4]), ey = Double.parseDouble(t[5]);
      double expected = Double.parseDouble(t[6]);
      double got = Math.abs(CircularArcs.signedSegmentArea(sx, sy, mx, my, ex, ey));
      assertEquals("segment area for " + s, expected, got, 1e-9 * Math.max(1.0, expected));
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 5);
  }
}
