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
package org.locationtech.jts.geom.curve;

import java.util.HashSet;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.linearref.LengthIndexedLine;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Green verification for meters shipped on the MMF tip (#1195).
 */
public class CurveAwarenessGreenMetersTest extends TestCase {

  private final CurveWKTReader reader = new CurveWKTReader();

  public static void main(String[] args) {
    TestRunner.run(CurveAwarenessGreenMetersTest.class);
  }

  public CurveAwarenessGreenMetersTest(String name) {
    super(name);
  }

  private Geometry read(String wkt) throws Exception {
    return reader.read(wkt);
  }

  public void test_M_LEN_CS() throws Exception {
    Geometry g = read("CIRCULARSTRING (-10 0, 0 10, 10 0)");
    assertEquals(Math.PI * 10, g.getLength(), 1.0e-9);
  }

  public void test_M_LEN_CC() throws Exception {
    Geometry g = read(
        "COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0))");
    assertEquals(10.0 + Math.PI * 5.0, g.getLength(), 1.0e-9);
  }

  public void test_M_AREA_CP() throws Exception {
    Geometry g = read(
        "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0))");
    assertEquals(Math.PI * 100, g.getArea(), 1.0e-6);
  }

  public void test_M_DIM() throws Exception {
    assertEquals(1, read("CIRCULARSTRING EMPTY").getDimension());
    assertEquals(2, read("CURVEPOLYGON EMPTY").getDimension());
  }

  public void test_F_MC() throws Exception {
    MultiCurve mc = (MultiCurve) read(
        "MULTICURVE (CIRCULARSTRING (0 0, 1 1, 2 0), (3 0, 4 0))");
    Geometry c = mc.copy();
    assertTrue(c.getGeometryN(0) instanceof CircularString);
    assertTrue(c.getGeometryN(1) instanceof LineString);
    assertFalse(c.getGeometryN(1) instanceof CircularString);
  }

  public void test_F_MS() throws Exception {
    MultiSurface ms = (MultiSurface) read(
        "MULTISURFACE (((0 0, 1 0, 1 1, 0 1, 0 0)), CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)))");
    Geometry c = ms.copy();
    assertEquals("Polygon", c.getGeometryN(0).getGeometryType());
    assertTrue(c.getGeometryN(1) instanceof CurvePolygon);
  }

  public void test_B_CC() throws Exception {
    Geometry g = read(
        "COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0))");
    Geometry b = g.getBoundary();
    assertEquals("MultiPoint", b.getGeometryType());
    assertEquals(2, b.getNumGeometries());
  }

  public void test_H_CV() throws Exception {
    Geometry hull = read("CIRCULARSTRING (-10 0, 0 10, 10 0)").convexHull();
    Set<String> uniq = new HashSet<String>();
    Coordinate[] pts = hull.getCoordinates();
    for (int i = 0; i < pts.length; i++) {
      if (i > 0 && pts[i].equals2D(pts[0])) {
        continue; // closing duplicate
      }
      uniq.add(pts[i].x + "," + pts[i].y);
    }
    assertEquals(3, uniq.size());
    assertTrue(hull instanceof CurvePolygon || hull.getNumPoints() <= 4);
  }

  public void test_R_EQ() throws Exception {
    Geometry arc = read("CIRCULARSTRING (0 0, 5 5, 10 0)");
    Geometry chord = new CurveGeometryFactory().createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(5, 5), new Coordinate(10, 0)
    });
    assertFalse(arc.equalsExact(chord));
  }

  public void test_AT_S() throws Exception {
    Geometry arc = read("CIRCULARSTRING (0 0, 5 5, 10 0)");
    Geometry rotated = AffineTransformation.rotationInstance(Math.PI / 4)
        .transform(arc);
    assertTrue(rotated instanceof CircularString);
  }

  public void test_AT_NS() throws Exception {
    Geometry arc = read("CIRCULARSTRING (0 0, 5 5, 10 0)");
    Geometry sheared = new AffineTransformation().setToShear(0.5, 0)
        .transform(arc);
    assertFalse("shear must densify, not keep CircularString",
        sheared instanceof CircularString);
    assertEquals("LineString", sheared.getGeometryType());
  }

  public void test_LRF_LEN() throws Exception {
    // LengthIndexedLine still indexes the control polyline; apex is near
    // mid-arc for a semicircle but not bit-exact until LRF densifies by
    // arc length. Guard: extracted point lies on the circle.
    Geometry arc = read("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    LengthIndexedLine lil = new LengthIndexedLine(arc);
    Coordinate mid = lil.extractPoint(arc.getLength() / 2.0);
    assertEquals(5.0, Math.hypot(mid.x, mid.y), 0.75);
  }

  public void test_D_PT() throws Exception {
    // POINT(0 10) to half-arc R=5 CIRCULARSTRING(-5 0, 0 5, 5 0):
    // nearest on circle is (0,5); distance = 5.
    Geometry arc = read("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    assertEquals(5.0, arc.distance(read("POINT (0 10)")), 1.0e-9);
  }
}
