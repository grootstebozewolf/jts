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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * V-CP (#1195) — arc-aware {@link CurvePolygon#isValid()}: rings closed and
 * arc-simple, holes nested in the shell, holes mutually disjoint, orientation
 * agnostic. Verified by geometric anchors and a densified cross-check against
 * core {@link Polygon#isValid()} on tessellated rings.
 */
public class CurvePolygonValidityTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(CurvePolygonValidityTest.class); }
  public CurvePolygonValidityTest(String name) { super(name); }

  private static final CurvedGeometryFactory GF = new CurvedGeometryFactory();
  private static final GeometryFactory PF = new GeometryFactory();

  // full circle as a CCW CircularString ring (two semicircle arcs)
  private static CircularString circle(double cx, double cy, double r) {
    return GF.createCircularString(new CoordinateArraySequence(new Coordinate[]{
        new Coordinate(cx+r,cy), new Coordinate(cx,cy+r), new Coordinate(cx-r,cy),
        new Coordinate(cx,cy-r), new Coordinate(cx+r,cy) }));
  }
  private static CircularString circleCW(double cx, double cy, double r) {   // reversed orientation
    return GF.createCircularString(new CoordinateArraySequence(new Coordinate[]{
        new Coordinate(cx+r,cy), new Coordinate(cx,cy-r), new Coordinate(cx-r,cy),
        new Coordinate(cx,cy+r), new Coordinate(cx+r,cy) }));
  }
  private static LineString polyline(double... xy) {
    Coordinate[] c = new Coordinate[xy.length/2];
    for (int i=0;i<c.length;i++) c[i]=new Coordinate(xy[2*i],xy[2*i+1]);
    return PF.createLineString(c);
  }
  private static CurvePolygon cp(LineString shell, LineString... holes) {
    return GF.createCurvePolygon(shell, holes);
  }

  // ---------- anchors ----------

  public void testCleanDiskWithHoleValid() {
    assertTrue(cp(circle(0,0,5), circle(0,0,2)).isValid());
  }

  public void testValidIsOrientationAgnostic() {
    assertTrue("CW shell still valid", cp(circleCW(0,0,5), circle(0,0,2)).isValid());
    assertTrue("CW hole still valid",  cp(circle(0,0,5), circleCW(0,0,2)).isValid());
  }

  public void testHoleOutsideShellInvalid() {
    assertFalse(cp(circle(0,0,5), circle(20,0,2)).isValid());
  }

  public void testOverlappingHolesInvalid() {
    assertFalse(cp(circle(0,0,10), circle(-2,0,3), circle(2,0,3)).isValid());
  }

  public void testHoleCrossingShellInvalid() {
    assertFalse(cp(circle(0,0,5), circle(3,0,5)).isValid());   // hole boundary crosses shell
  }

  public void testSelfCrossingShellInvalid() {
    // bow-tie plain-polyline shell (normalized to chords) is not simple
    assertFalse(cp(polyline(0,0, 4,4, 4,0, 0,4, 0,0)).isValid());
  }

  public void testMixedCurvedShellStraightHole() {
    // curved disk shell with a straight triangular hole inside
    assertTrue(cp(circle(0,0,6), polyline(-1,-1, 1,-1, 0,1, -1,-1)).isValid());
  }

  public void testNoHolesValid() {
    assertTrue(cp(circle(0,0,3)).isValid());
  }

  /** Densified cross-check: arc-aware isValid agrees with core Polygon.isValid on tessellated rings. */
  public void testDensifiedCrossCheck() {
    Random rnd = new Random(13L);
    int checked = 0;
    for (int it=0; it<1500; it++) {
      double r = 4 + rnd.nextDouble()*4;                 // shell radius
      double hr = 0.5 + rnd.nextDouble()*2.5;            // hole radius
      double hx = rnd.nextDouble()*16-8, hy = rnd.nextDouble()*16-8;
      double d = Math.hypot(hx,hy);
      final double M = 0.5;
      // general position: hole strictly inside, strictly outside, or clearly crossing
      boolean inside = d + hr + M < r;
      boolean outside = d - hr - M > r;
      boolean crossing = Math.abs(d - r) > M && d - hr + M < r && d + hr - M > r;
      if (!(inside || outside || crossing)) continue;
      checked++;
      CurvePolygon arc = cp(circle(0,0,r), circle(hx,hy,hr));
      Polygon tess = PF.createPolygon(ring(0,0,r,720), new LinearRing[]{ ring(hx,hy,hr,720) });
      assertEquals(tess.isValid(), arc.isValid());
    }
    assertTrue("enough cross-checked cases ("+checked+")", checked > 300);
  }

  // ---------- helpers ----------

  private static LinearRing ring(double cx, double cy, double r, int n) {
    Coordinate[] c = new Coordinate[n+1];
    for (int i=0;i<n;i++){ double a=2*Math.PI*i/n; c[i]=new Coordinate(cx+r*Math.cos(a), cy+r*Math.sin(a)); }
    c[n]=c[0];
    return PF.createLinearRing(c);
  }
}
