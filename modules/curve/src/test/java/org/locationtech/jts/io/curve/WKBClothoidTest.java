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
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.WKBConstants;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * CRV-CLOTHOID WKB 18 greenfield round-trip (#1195 MMF SIGN).
 */
public class WKBClothoidTest extends TestCase {

  private static final double EPS = 1e-12;

  public static void main(String[] args) {
    TestRunner.run(WKBClothoidTest.class);
  }

  public WKBClothoidTest(String name) {
    super(name);
  }

  public void testRoundTripXy() throws Exception {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    ClothoidSegment cl = gf.createClothoid(
        new Coordinate(0, 0), 0.0, 0.0, 0.005, 80.0);
    byte[] wkb = new CurveWKBWriter().write(cl);
    assertEquals(WKBConstants.wkbClothoid, typeWord(wkb));

    Geometry back = new CurveWKBReader().read(wkb);
    assertTrue(back instanceof ClothoidSegment);
    ClothoidSegment out = (ClothoidSegment) back;
    assertEquals(0.0, out.getStartCoordinate().x, EPS);
    assertEquals(0.0, out.getStartCoordinate().y, EPS);
    assertEquals(0.0, out.getStartTangent(), EPS);
    assertEquals(0.0, out.getStartKappa(), EPS);
    assertEquals(0.005, out.getEndKappa(), EPS);
    assertEquals(80.0, out.getLength(), EPS);
    assertEquals(cl.getEndCoordinate().x, out.getEndCoordinate().x, 1e-9);
    assertEquals(cl.getEndCoordinate().y, out.getEndCoordinate().y, 1e-9);
  }

  public void testIsoZTypeWord() throws Exception {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    ClothoidSegment cl = gf.createClothoid(
        new Coordinate(1, 2, 3), 0.1, 0.0, 0.01, 40.0);
    CurveWKBWriter writer = new CurveWKBWriter(3);
    writer.setFlavor(WKBConstants.wkbIso);
    byte[] wkb = writer.write(cl);
    assertEquals(1018, typeWord(wkb));
    Geometry back = new CurveWKBReader().read(wkb);
    assertTrue(back instanceof ClothoidSegment);
  }

  public void testIsoMTypeWord() throws Exception {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    Coordinate start = new org.locationtech.jts.geom.CoordinateXYM(1, 2, 9.0);
    ClothoidSegment cl = gf.createClothoid(start, 0.1, 0.0, 0.01, 40.0);
    CurveWKBWriter writer = new CurveWKBWriter(3);
    writer.setFlavor(WKBConstants.wkbIso);
    writer.setOutputOrdinates(java.util.EnumSet.of(
        org.locationtech.jts.io.Ordinate.X,
        org.locationtech.jts.io.Ordinate.Y,
        org.locationtech.jts.io.Ordinate.M));
    byte[] wkb = writer.write(cl);
    assertEquals(2018, typeWord(wkb));
    Geometry back = new CurveWKBReader().read(wkb);
    assertTrue(back instanceof ClothoidSegment);
  }

  public void testIsoZmTypeWord() throws Exception {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    Coordinate start = new org.locationtech.jts.geom.CoordinateXYZM(1, 2, 3, 9.0);
    ClothoidSegment cl = gf.createClothoid(start, 0.1, 0.0, 0.01, 40.0);
    CurveWKBWriter writer = new CurveWKBWriter(4);
    writer.setFlavor(WKBConstants.wkbIso);
    byte[] wkb = writer.write(cl);
    assertEquals(3018, typeWord(wkb));
    Geometry back = new CurveWKBReader().read(wkb);
    assertTrue(back instanceof ClothoidSegment);
  }

  public void testCoreWriterRefusesFlatten() {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    ClothoidSegment cl = gf.createClothoid(
        new Coordinate(0, 0), 0.0, 0.0, 0.005, 80.0);
    try {
      new WKBWriter().write(cl);
      fail("core WKBWriter must not flatten clothoid");
    }
    catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().indexOf("Clothoid") >= 0
          || expected.getMessage().indexOf("CurveWKBWriter") >= 0);
    }
  }

  public void testDefaultFactoryRequiresCurveFactory() throws Exception {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    ClothoidSegment cl = gf.createClothoid(
        new Coordinate(0, 0), 0.0, 0.0, 0.005, 80.0);
    byte[] wkb = new CurveWKBWriter().write(cl);
    try {
      new WKBReader().read(wkb);
      fail("default factory must refuse clothoid construction");
    }
    catch (Exception expected) {
      // ParseException wrapping unsupported factory
      assertNotNull(expected.getMessage());
    }
  }

  private static int typeWord(byte[] wkb) {
    // XDR: byte0=0, type at bytes 1..4 big-endian
    if (wkb[0] == WKBConstants.wkbXDR) {
      return ((wkb[1] & 0xff) << 24) | ((wkb[2] & 0xff) << 16)
          | ((wkb[3] & 0xff) << 8) | (wkb[4] & 0xff);
    }
    // NDR little-endian
    return (wkb[1] & 0xff) | ((wkb[2] & 0xff) << 8)
        | ((wkb[3] & 0xff) << 16) | ((wkb[4] & 0xff) << 24);
  }
}
