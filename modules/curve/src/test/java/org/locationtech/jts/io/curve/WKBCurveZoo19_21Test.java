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
package org.locationtech.jts.io.curve;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.BezierCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.EllipseCurve;
import org.locationtech.jts.geom.curve.NurbsCurve;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.io.WKBConstants;
import org.locationtech.jts.io.WKBWriter;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * WKB 19–21 greenfield round-trips (#1195 MMF SIGN).
 */
public class WKBCurveZoo19_21Test extends TestCase {

  private static final double EPS = 1e-12;

  public static void main(String[] args) {
    TestRunner.run(WKBCurveZoo19_21Test.class);
  }

  public WKBCurveZoo19_21Test(String name) {
    super(name);
  }

  public void testBezierRoundTrip() throws Exception {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    CoordinateArraySequence seq = new CoordinateArraySequence(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(1, 2), new Coordinate(3, 2),
        new Coordinate(4, 0)
    });
    BezierCurve bz = gf.createBezierCurve(seq);
    byte[] wkb = new CurveWKBWriter().write(bz);
    assertEquals(WKBConstants.wkbBezier, typeWord(wkb));
    Geometry back = new CurveWKBReader().read(wkb);
    assertTrue(back instanceof BezierCurve);
    assertEquals(4, back.getNumPoints());
    assertEquals(4.0, ((BezierCurve) back).getCoordinateN(3).x, EPS);
  }

  public void testBezierIsoZ() throws Exception {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    CoordinateArraySequence seq = new CoordinateArraySequence(new Coordinate[] {
        new Coordinate(0, 0, 1), new Coordinate(1, 1, 2),
        new Coordinate(2, 1, 3), new Coordinate(3, 0, 4)
    });
    BezierCurve bz = gf.createBezierCurve(seq);
    CurveWKBWriter w = new CurveWKBWriter(3);
    w.setFlavor(WKBConstants.wkbIso);
    byte[] wkb = w.write(bz);
    assertEquals(1019, typeWord(wkb));
    Geometry back = new CurveWKBReader().read(wkb);
    assertTrue(back instanceof BezierCurve);
    assertEquals(1.0, back.getCoordinate().getZ(), EPS);
    assertEquals(4.0, ((BezierCurve) back).getCoordinateN(3).getZ(), EPS);
  }

  public void testEllipseIsoZ() throws Exception {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    EllipseCurve el = gf.createEllipseCurve(1, 2, 9, 5, 3, 0.0, 0.0, Math.PI);
    CurveWKBWriter w = new CurveWKBWriter(3);
    w.setFlavor(WKBConstants.wkbIso);
    byte[] wkb = w.write(el);
    assertEquals(1020, typeWord(wkb));
    Geometry back = new CurveWKBReader().read(wkb);
    assertTrue(back instanceof EllipseCurve);
    assertEquals(9.0, ((EllipseCurve) back).getCentreZ(), EPS);
  }

  public void testNurbsIsoZ() throws Exception {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    CoordinateArraySequence seq = new CoordinateArraySequence(new Coordinate[] {
        new Coordinate(0, 0, 5), new Coordinate(1, 1, 6),
        new Coordinate(2, 1, 7), new Coordinate(3, 0, 8)
    });
    NurbsCurve nu = gf.createNurbsCurve(seq, 3, new double[] { 1, 1, 1, 1 },
        new double[] { 0, 0, 0, 0, 1, 1, 1, 1 });
    CurveWKBWriter w = new CurveWKBWriter(3);
    w.setFlavor(WKBConstants.wkbIso);
    byte[] wkb = w.write(nu);
    assertEquals(1021, typeWord(wkb));
    Geometry back = new CurveWKBReader().read(wkb);
    assertTrue(back instanceof NurbsCurve);
    assertEquals(5.0, back.getCoordinate().getZ(), EPS);
  }

  public void testEllipseRoundTrip() throws Exception {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    EllipseCurve el = gf.createEllipseCurve(0, 0, Double.NaN, 5, 3, 0.0, 0.0,
        2 * Math.PI);
    byte[] wkb = new CurveWKBWriter().write(el);
    assertEquals(WKBConstants.wkbEllipse, typeWord(wkb));
    Geometry back = new CurveWKBReader().read(wkb);
    assertTrue(back instanceof EllipseCurve);
    EllipseCurve out = (EllipseCurve) back;
    assertEquals(5.0, out.getSemiMajor(), EPS);
    assertEquals(3.0, out.getSemiMinor(), EPS);
    assertTrue(out.isFullEllipse());
  }

  public void testNurbsRoundTrip() throws Exception {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    CoordinateArraySequence seq = new CoordinateArraySequence(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(1, 1), new Coordinate(2, 1),
        new Coordinate(3, 0)
    });
    double[] weights = { 1, 1, 1, 1 };
    double[] knots = { 0, 0, 0, 0, 1, 1, 1, 1 }; // degree 3, n=4 → 8 knots
    NurbsCurve nu = gf.createNurbsCurve(seq, 3, weights, knots);
    byte[] wkb = new CurveWKBWriter().write(nu);
    assertEquals(WKBConstants.wkbNurbs, typeWord(wkb));
    Geometry back = new CurveWKBReader().read(wkb);
    assertTrue(back instanceof NurbsCurve);
    NurbsCurve out = (NurbsCurve) back;
    assertEquals(3, out.getDegree());
    assertEquals(4, out.getWeights().length);
    assertEquals(8, out.getKnots().length);
    Geometry linear = out.toLinear(0.1);
    assertTrue(linear.getNumPoints() >= 2);
  }

  public void testCoreWriterRefusesFlatten() {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    BezierCurve bz = gf.createBezierCurve(new CoordinateArraySequence(
        new Coordinate[] {
            new Coordinate(0, 0), new Coordinate(1, 1),
            new Coordinate(2, 1), new Coordinate(3, 0)
        }));
    try {
      new WKBWriter().write(bz);
      fail("must not flatten Bezier");
    }
    catch (IllegalArgumentException expected) {
      assertNotNull(expected.getMessage());
    }
  }

  private static int typeWord(byte[] wkb) {
    if (wkb[0] == WKBConstants.wkbXDR) {
      return ((wkb[1] & 0xff) << 24) | ((wkb[2] & 0xff) << 16)
          | ((wkb[3] & 0xff) << 8) | (wkb[4] & 0xff);
    }
    return (wkb[1] & 0xff) | ((wkb[2] & 0xff) << 8)
        | ((wkb[3] & 0xff) << 16) | ((wkb[4] & 0xff) << 24);
  }
}
