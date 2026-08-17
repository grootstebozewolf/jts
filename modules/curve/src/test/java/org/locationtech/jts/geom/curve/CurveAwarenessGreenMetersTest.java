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
import org.locationtech.jts.geom.PrecisionModel;
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

  public void test_F_CP() throws Exception {
    Geometry g = read(
        "CURVEPOLYGON ((CIRCULARSTRING (0 0, 5 5, 10 0), CIRCULARSTRING (10 0, 5 -5, 0 0)))");
    assertTrue(g instanceof CurvePolygon);
    assertTrue(((CurvePolygon) g).getExteriorCurve() instanceof CompoundCurve);
  }

  public void test_B_CP() throws Exception {
    Geometry g = read(
        "CURVEPOLYGON ((CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 0 0)))");
    Geometry b = g.getBoundary();
    assertTrue(b instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) b;
    assertEquals(2, cc.getNumMembers());
    assertTrue(cc.getMemberN(0) instanceof CircularString);
  }

  public void test_B_MS() throws Exception {
    Geometry g = read(
        "MULTISURFACE (((0 0, 10 0, 10 10, 0 10, 0 0)), "
            + "CURVEPOLYGON ((CIRCULARSTRING (20 0, 25 5, 30 0), (30 0, 20 0))))");
    Geometry b = g.getBoundary();
    assertEquals("MultiCurve", b.getGeometryType());
    assertTrue(b instanceof MultiCurve);
  }

  public void test_LRF_LEN() throws Exception {
    Geometry arc = read("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    LengthIndexedLine lil = new LengthIndexedLine(arc);
    Coordinate mid = lil.extractPoint(arc.getLength() / 2.0);
    assertEquals(0.0, mid.x, 1.0e-9);
    assertEquals(5.0, mid.y, 1.0e-9);
  }

  public void test_C_LIN() throws Exception {
    Geometry arc = read("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    assertEquals(2.0 * 5.0 / Math.PI, arc.getCentroid().getY(), 1.0e-9);
  }

  public void test_C_AREA() throws Exception {
    Geometry disc = read(
        "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0))");
    assertEquals(0.0, disc.getCentroid().getX(), 1.0e-6);
    assertEquals(0.0, disc.getCentroid().getY(), 1.0e-6);
  }

  public void test_D_OP() throws Exception {
    Geometry arc = read("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    assertEquals(5.0,
        org.locationtech.jts.operation.distance.DistanceOp.distance(arc,
            read("POINT (0 10)")),
        1.0e-9);
  }

  public void test_D_AA() throws Exception {
    Geometry a1 = read("CIRCULARSTRING (-10 0, -5 5, 0 0)");
    Geometry a2 = read("CIRCULARSTRING (10 0, 15 5, 20 0)");
    assertEquals(10.0, a1.distance(a2), 1.0e-9);
  }

  public void test_R_CONT() throws Exception {
    Geometry disc = read(
        "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0))");
    assertTrue(disc.contains(read("POINT (5 5)")));
    assertFalse(disc.contains(read("POINT (20 20)")));
  }

  public void test_OV() throws Exception {
    Geometry d1 = read(
        "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))");
    Geometry d2 = read(
        "CURVEPOLYGON (CIRCULARSTRING (0 0, 5 5, 10 0, 5 -5, 0 0))");
    Geometry u = d1.union(d2);
    assertTrue(u instanceof CurvePolygon);
  }

  public void test_S_DP() throws Exception {
    Geometry arc = read("CIRCULARSTRING (-10 0, 0 10, 10 0)");
    Geometry simp = org.locationtech.jts.simplify.DouglasPeuckerSimplifier
        .simplify(arc, 1.0);
    assertTrue(simp instanceof CircularString);
  }

  public void test_S_VW() throws Exception {
    Geometry arc = read("CIRCULARSTRING (-10 0, 0 10, 10 0)");
    Geometry simp = org.locationtech.jts.simplify.VWSimplifier.simplify(arc,
        1.0);
    assertTrue(simp instanceof CircularString);
  }

  public void test_S_TP() throws Exception {
    Geometry arc = read("CIRCULARSTRING (-10 0, 0 10, 10 0)");
    Geometry simp = org.locationtech.jts.simplify.TopologyPreservingSimplifier
        .simplify(arc, 1.0);
    assertTrue(simp instanceof CircularString);
  }

  public void test_V_CS() throws Exception {
    Geometry g = read(
        "CIRCULARSTRING (0 0, 5 5, 10 0, 5 -5, 0 0, 5 5, 10 0)");
    assertFalse(g.isSimple());
  }

  public void test_F_RD() throws Exception {
    Geometry cp = read(
        "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))");
    org.locationtech.jts.awt.curve.CurveShapeWriter w =
        new org.locationtech.jts.awt.curve.CurveShapeWriter();
    java.awt.Shape s = w.toShape(cp);
    assertNotNull(s);
    assertFalse(s.getBounds2D().isEmpty());
  }

  public void test_LRF_LOC() throws Exception {
    Geometry cc = read(
        "COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0))");
    org.locationtech.jts.linearref.LocationIndexedLine loc =
        new org.locationtech.jts.linearref.LocationIndexedLine(cc);
    org.locationtech.jts.linearref.LinearLocation ll = loc
        .indexOf(new Coordinate(15, 5));
    assertEquals(1, ll.getComponentIndex());
  }

  public void test_C_IP() throws Exception {
    Geometry disc = read(
        "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))");
    Coordinate ip = org.locationtech.jts.algorithm.InteriorPointArea
        .getInteriorPoint(disc);
    assertNotNull(ip);
    assertTrue(disc.contains(disc.getFactory().createPoint(ip)));
    assertTrue(Math.hypot(ip.x, ip.y) < 4.0);
  }

  public void test_PRC_SN() throws Exception {
    Geometry cs = read("CIRCULARSTRING (0 0, 5 5, 10 0)");
    PrecisionModel pm = new PrecisionModel(1.0);
    Geometry red = org.locationtech.jts.precision.GeometryPrecisionReducer
        .reduce(cs, pm);
    assertTrue(red instanceof CircularString);
  }

  public void test_V_CP() throws Exception {
    Geometry disc = read(
        "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))");
    assertTrue(disc.isValid());
    Geometry crossed = read(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 0 5, 0 0)))");
    assertFalse(crossed.isValid());
  }

  public void test_R_PR() throws Exception {
    Geometry disc = read(
        "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))");
    Geometry inside = read("POINT (0 0)");
    Geometry outside = read("POINT (10 10)");
    assertEquals("0F2FF1FF2", disc.relate(inside).toString());
    assertEquals("FF2FF10F2", disc.relate(outside).toString());
  }

  public void test_N_SS() {
    org.locationtech.jts.noding.CircularNodedSegmentString ss =
        org.locationtech.jts.noding.CircularNodedSegmentString.arc(
            new Coordinate(0, 0), new Coordinate(5, 5),
            new Coordinate(10, 0), null);
    assertEquals(org.locationtech.jts.noding.SegmentKind.ARC,
        ss.getSegmentKind(0));
    assertTrue(ss.isExact(0));
    assertFalse(ss.mayCollapseToChord(0));
  }
}
