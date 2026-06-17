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
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * C-AREA (#1195): {@link CurvePolygon#getCentroid()} is the area-weighted
 * centroid accounting for the circular segments of curved rings. Symmetric
 * disks are checked exactly (centroid = centre); asymmetric shapes and holes are
 * checked against an independent finely-densified-polygon centroid.
 */
public class CurvePolygonCentroidTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CurvePolygonCentroidTest.class);
  }

  public CurvePolygonCentroidTest(String name) { super(name); }

  private final CurvedGeometryFactory gf = new CurvedGeometryFactory();

  private CircularString cs(double... xy) {
    Coordinate[] pts = new Coordinate[xy.length / 2];
    for (int i = 0; i < pts.length; i++)
      pts[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
    return gf.createCircularString(new CoordinateArraySequence(pts));
  }

  public void testDiskCentroidAtCentre() {
    Point c = gf.createCurvePolygon(cs(10,0, 0,10, -10,0, 0,-10, 10,0)).getCentroid();
    assertEquals(0.0, c.getX(), 1e-7);
    assertEquals(0.0, c.getY(), 1e-7);
  }

  public void testOffsetDiskCentroidAtCentre() {
    // disk radius 4 centred at (3,-2)
    Point c = gf.createCurvePolygon(cs(7,-2, 3,2, -1,-2, 3,-6, 7,-2)).getCentroid();
    assertEquals(3.0, c.getX(), 1e-7);
    assertEquals(-2.0, c.getY(), 1e-7);
  }

  public void testAsymmetricLensMatchesDensified() {
    // upper semicircle (R=5) over a shallow lower arc -> centroid above the axis
    CircularString shell = cs(-5,0, 0,5, 5,0, 0,-1, -5,0);
    Point c = gf.createCurvePolygon(shell).getCentroid();
    double[] ref = densifiedCentroid(shell, null, 4000);
    assertEquals(ref[0], c.getX(), 1e-3);
    assertEquals(ref[1], c.getY(), 1e-3);
    assertTrue("centroid should sit above the x-axis", c.getY() > 0.5);
  }

  public void testOffCentreHoleShiftsCentroid() {
    CircularString shell = cs(10,0, 0,10, -10,0, 0,-10, 10,0);
    CircularString hole  = cs(7,0, 4,3, 1,0, 4,-3, 7,0);   // R=3 disk centred at (4,0)
    Point c = gf.createCurvePolygon(shell, new org.locationtech.jts.geom.LineString[]{ hole }).getCentroid();
    double[] ref = densifiedCentroid(shell, new CircularString[]{ hole }, 4000);
    assertEquals(ref[0], c.getX(), 1e-3);
    assertEquals(ref[1], c.getY(), 1e-3);
    assertTrue("hole on +x side shifts centroid to -x", c.getX() < -0.1);
  }

  /**
   * Pins the per-arc circular-segment centroid ({@link CircularArcs#segmentCentroid},
   * the moment building block of the area centroid) against the exact
   * ARC_AREA_CENTROID oracle (NetTopologySuite.Proofs Rocq/Coq extraction). The
   * vectors are canonical arcs (mid at the angular midpoint); the oracle projects
   * the segment-centroid distance along the mid control-point direction, so
   * off-midpoint mids are excluded here and covered by the densified cross-checks.
   */
  public void testSegmentCentroidMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curved/rocqref/curve_arc_area_centroid_vectors.txt");
    assertNotNull("segment centroid vectors resource", in);
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String line; int checked = 0;
    while ((line = r.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      double[] c = CircularArcs.segmentCentroid(
          Double.parseDouble(t[0]), Double.parseDouble(t[1]),
          Double.parseDouble(t[2]), Double.parseDouble(t[3]),
          Double.parseDouble(t[4]), Double.parseDouble(t[5]));
      double ecx = Double.parseDouble(t[6]), ecy = Double.parseDouble(t[7]);
      assertEquals("segment centroid x for " + s, ecx, c[0], 1e-9 * Math.max(1.0, Math.abs(ecx)));
      assertEquals("segment centroid y for " + s, ecy, c[1], 1e-9 * Math.max(1.0, Math.abs(ecy)));
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle vectors", checked >= 10);
  }

  // ---- independent reference: tessellate arcs into a fine polygon, use JTS centroid ----

  private double[] densifiedCentroid(CircularString shell, CircularString[] holes, int nPerArc) {
    LinearRing shellRing = gf.createLinearRing(densify(shell, nPerArc));
    LinearRing[] holeRings = new LinearRing[holes == null ? 0 : holes.length];
    for (int i = 0; i < holeRings.length; i++)
      holeRings[i] = gf.createLinearRing(densify(holes[i], nPerArc));
    Polygon p = gf.createPolygon(shellRing, holeRings);
    Point c = p.getCentroid();
    return new double[]{ c.getX(), c.getY() };
  }

  private Coordinate[] densify(CircularString cs, int nPerArc) {
    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    List<Coordinate> pts = new ArrayList<Coordinate>();
    for (int i = 0; i + 2 < n; i += 2) {
      double sx = seq.getX(i),     sy = seq.getY(i);
      double mx = seq.getX(i + 1), my = seq.getY(i + 1);
      double ex = seq.getX(i + 2), ey = seq.getY(i + 2);
      double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
      double s2 = sx*sx+sy*sy, m2 = mx*mx+my*my, e2 = ex*ex+ey*ey;
      double cx = (s2*(my-ey) + m2*(ey-sy) + e2*(sy-my)) / d;
      double cy = (s2*(ex-mx) + m2*(sx-ex) + e2*(mx-sx)) / d;
      double r = Math.hypot(sx - cx, sy - cy);
      double a0 = Math.atan2(sy - cy, sx - cx);
      double am = Math.atan2(my - cy, mx - cx);
      double ae = Math.atan2(ey - cy, ex - cx);
      boolean ccw = d > 0;
      double theta = sweep(a0, am, ccw) + sweep(am, ae, ccw);
      int dir = ccw ? 1 : -1;
      int kstart = (i == 0) ? 0 : 1;     // shared endpoint already added by previous arc
      for (int k = kstart; k <= nPerArc; k++) {
        double ang = a0 + dir * theta * k / nPerArc;
        pts.add(new Coordinate(cx + r * Math.cos(ang), cy + r * Math.sin(ang)));
      }
    }
    pts.add(new Coordinate(pts.get(0)));  // close exactly
    return pts.toArray(new Coordinate[0]);
  }

  private static double sweep(double from, double to, boolean ccw) {
    double t = ccw ? (to - from) : (from - to);
    t %= 2 * Math.PI;
    if (t < 0) t += 2 * Math.PI;
    return t;
  }
}
