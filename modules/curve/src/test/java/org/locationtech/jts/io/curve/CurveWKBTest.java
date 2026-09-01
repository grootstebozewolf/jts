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
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBConstants;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * First-class WKB for SQL/MM types 8–12. Writers emit control points,
 * not a densified polyline. Readers reconstruct the curve subclass.
 */
public class CurveWKBTest extends GeometryTestCase {

  private static final String ARC =
      "CIRCULARSTRING (0 0, 5 5, 10 0)";
  private static final String DISC =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String TWO_ARCS =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0))";
  private static final String MULTI_CURVE =
      "MULTICURVE (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String MULTI_SURFACE =
      "MULTISURFACE (CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)))";

  /**
   * Default {@link WKBWriter} (XDR / big-endian, 2D, no SRID) for
   * {@code CIRCULARSTRING (0 0, 5 5, 10 0)}: type 8, three control points.
   */
  private static final String HEX_CIRCULARSTRING =
      "000000000800000003000000000000000000000000000000004014000000000000401400000000000040240000000000000000000000000000";

  public static void main(String[] args) {
    TestRunner.run(CurveWKBTest.class);
  }

  public CurveWKBTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static Geometry roundTrip(Geometry g) throws Exception {
    byte[] wkb = new CurveWKBWriter().write(g);
    return new CurveWKBReader(g.getFactory()).read(wkb);
  }

  public void testCircularStringRoundTrip() throws Exception {
    Geometry g = readCurve(ARC);
    Geometry back = roundTrip(g);
    assertTrue(back instanceof CircularString);
    assertEquals(CircularString.class, back.getClass());
    assertEquals("CircularString", back.getGeometryType());
    assertTrue(g.equalsExact(back));
    Coordinate[] a = g.getCoordinates();
    Coordinate[] b = back.getCoordinates();
    assertEquals(3, b.length);
    for (int i = 0; i < a.length; i++) {
      assertEquals(a[i].x, b[i].x, 0.0);
      assertEquals(a[i].y, b[i].y, 0.0);
    }
  }

  public void testCircularStringLockedHex() throws Exception {
    Geometry g = readCurve(ARC);
    String hex = WKBWriter.toHex(new CurveWKBWriter().write(g));
    assertEquals(HEX_CIRCULARSTRING, hex);
    Geometry back = new CurveWKBReader(g.getFactory())
        .read(WKBReader.hexToBytes(HEX_CIRCULARSTRING));
    assertTrue(back instanceof CircularString);
    assertTrue(g.equalsExact(back));
    assertEquals(WKBConstants.wkbCircularString, typeCode(hex));
  }

  public void testCurvePolygonDiscKeepsArea() throws Exception {
    Geometry g = readCurve(DISC);
    Geometry back = roundTrip(g);
    assertTrue(back instanceof CurvePolygon);
    CurvePolygon cp = (CurvePolygon) back;
    assertTrue(cp.getExteriorCurve() instanceof CircularString);
    assertEquals(25.0 * Math.PI, back.getArea(), 1.0e-9);
    assertEquals(WKBConstants.wkbCurvePolygon, typeCode(WKBWriter.toHex(
        new CurveWKBWriter().write(g))));
  }

  public void testCompoundCurvePreservesMembers() throws Exception {
    Geometry g = readCurve(TWO_ARCS);
    Geometry back = roundTrip(g);
    assertTrue(back instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) back;
    assertEquals(2, cc.getNumMembers());
    assertTrue(cc.getMemberN(0) instanceof CircularString);
    assertTrue(cc.getMemberN(1) instanceof CircularString);
    assertEquals(WKBConstants.wkbCompoundCurve, typeCode(WKBWriter.toHex(
        new CurveWKBWriter().write(g))));
  }

  public void testMultiCurveOfDiscBoundary() throws Exception {
    Geometry g = readCurve(MULTI_CURVE);
    Geometry back = roundTrip(g);
    assertTrue(back instanceof MultiCurve);
    assertEquals(1, back.getNumGeometries());
    assertTrue(back.getGeometryN(0) instanceof CircularString);
    assertEquals(WKBConstants.wkbMultiCurve, typeCode(WKBWriter.toHex(
        new CurveWKBWriter().write(g))));
  }

  public void testMultiSurfaceOfDisc() throws Exception {
    Geometry g = readCurve(MULTI_SURFACE);
    Geometry back = roundTrip(g);
    assertTrue(back instanceof MultiSurface);
    assertEquals(1, back.getNumGeometries());
    assertTrue(back.getGeometryN(0) instanceof CurvePolygon);
    assertEquals(25.0 * Math.PI, back.getGeometryN(0).getArea(), 1.0e-9);
    assertEquals(WKBConstants.wkbMultiSurface, typeCode(WKBWriter.toHex(
        new CurveWKBWriter().write(g))));
  }

  public void testDoesNotLineariseOnWrite() throws Exception {
    Geometry g = readCurve(ARC);
    byte[] curve = new CurveWKBWriter().write(g);
    // Three control points, not a densified LineString: 1+4+4+3*16 = 57 bytes.
    assertEquals(57, curve.length);
    assertEquals(WKBConstants.wkbCircularString, typeCode(WKBWriter.toHex(curve)));
  }

  public void testEmptyCurvePolygon() throws Exception {
    Geometry g = readCurve("CURVEPOLYGON EMPTY");
    Geometry back = roundTrip(g);
    assertTrue(back instanceof CurvePolygon);
    assertTrue(back.isEmpty());
  }

  public void testCoreReaderWithCurveFactoryBuildsCircularString() throws Exception {
    Geometry g = new WKBReader(new CurveGeometryFactory())
        .read(WKBReader.hexToBytes(HEX_CIRCULARSTRING));
    assertTrue(g instanceof CircularString);
    assertEquals(CircularString.class, g.getClass());
    assertEquals(WKBConstants.wkbCircularString, typeCode(HEX_CIRCULARSTRING));
    Geometry again = new CurveWKTReader(new CurveGeometryFactory()).read(g.toText());
    assertTrue(again instanceof CircularString);
    assertTrue(g.equalsExact(again));
  }

  public void testCoreReaderWithCurveFactoryDiscKeepsArea() throws Exception {
    Geometry disc = readCurve(DISC);
    byte[] wkb = new CurveWKBWriter().write(disc);
    Geometry back = new WKBReader(new CurveGeometryFactory()).read(wkb);
    assertTrue(back instanceof CurvePolygon);
    assertEquals(25.0 * Math.PI, back.getArea(), 1.0e-9);
    assertTrue(((CurvePolygon) back).getExteriorCurve() instanceof CircularString);
  }

  public void testDefaultWKBReaderType8MentionsFactory() {
    try {
      new WKBReader().read(WKBReader.hexToBytes(HEX_CIRCULARSTRING));
      fail("Expected ParseException from default WKBReader for type 8");
    } catch (Throwable e) {
      assertTrue("Expected ParseException, got: " + e, e instanceof ParseException);
      String msg = e.getMessage();
      assertTrue(msg, msg.indexOf("Unknown WKB type 8") < 0);
      String lower = msg.toLowerCase();
      assertTrue(msg, lower.indexOf("factory") >= 0);
      assertTrue(msg, lower.indexOf("curve") >= 0);
    }
  }

  private static int typeCode(String hex) {
    byte[] wkb = WKBReader.hexToBytes(hex);
    // XDR: byte 0 is endian, bytes 1–4 are the type int.
    return ((wkb[1] & 0xff) << 24) | ((wkb[2] & 0xff) << 16)
        | ((wkb[3] & 0xff) << 8) | (wkb[4] & 0xff);
  }
}
