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

import java.util.EnumSet;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.io.ByteOrderValues;
import org.locationtech.jts.io.Ordinate;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBConstants;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Pins GEOS-compatible (libgeos/geos {@code 737737db}) WKB flavour
 * type words and WKT strings for the five ISO/IEC 13249-3 curve types
 * in 2-D, Z, M, and ZM. Not a 2-D 8–12 reader PR: those codes already
 * landed on #7.
 */
public class GeosCurveIOFlavourTest extends GeometryTestCase {

  /**
   * XDR ISO CircularStringZ=1008 for
   * {@code CIRCULARSTRING Z (0 0 1, 5 5 2, 10 0 3)}.
   */
  private static final String HEX_ISO_CIRCULARSTRING_Z =
      "00000003F000000003000000000000000000000000000000003FF0000000000000"
          + "4014000000000000401400000000000040000000000000004024000000000000"
          + "00000000000000004008000000000000";

  private static final String CS_2D = "CIRCULARSTRING (0 0, 5 5, 10 0)";
  private static final String CS_Z = "CIRCULARSTRING Z (0 0 1, 5 5 2, 10 0 3)";
  private static final String CS_M = "CIRCULARSTRING M (0 0 1, 5 5 2, 10 0 3)";
  private static final String CS_ZM = "CIRCULARSTRING ZM (0 0 1 4, 5 5 2 5, 10 0 3 6)";

  private static final String CC_2D =
      "COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0))";
  private static final String CC_Z =
      "COMPOUNDCURVE Z ((0 0 1, 10 0 2), CIRCULARSTRING Z (10 0 2, 15 5 3, 20 0 4))";
  private static final String CC_M =
      "COMPOUNDCURVE M ((0 0 1, 10 0 2), CIRCULARSTRING M (10 0 2, 15 5 3, 20 0 4))";
  private static final String CC_ZM =
      "COMPOUNDCURVE ZM ((0 0 1 4, 10 0 2 5), CIRCULARSTRING ZM (10 0 2 5, 15 5 3 6, 20 0 4 7))";

  private static final String CP_2D =
      "CURVEPOLYGON (CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0))";
  private static final String CP_Z =
      "CURVEPOLYGON Z (CIRCULARSTRING Z (-2 0 1, 0 2 2, 2 0 3, 0 -2 4, -2 0 1))";
  private static final String CP_M =
      "CURVEPOLYGON M (CIRCULARSTRING M (-2 0 1, 0 2 2, 2 0 3, 0 -2 4, -2 0 1))";
  private static final String CP_ZM =
      "CURVEPOLYGON ZM (CIRCULARSTRING ZM (-2 0 1 5, 0 2 2 6, 2 0 3 7, 0 -2 4 8, -2 0 1 5))";

  private static final String MC_2D =
      "MULTICURVE (CIRCULARSTRING (0 0, 5 5, 10 0))";
  private static final String MC_Z =
      "MULTICURVE Z (CIRCULARSTRING Z (0 0 1, 5 5 2, 10 0 3))";
  private static final String MC_M =
      "MULTICURVE M (CIRCULARSTRING M (0 0 1, 5 5 2, 10 0 3))";
  private static final String MC_ZM =
      "MULTICURVE ZM (CIRCULARSTRING ZM (0 0 1 4, 5 5 2 5, 10 0 3 6))";

  private static final String MS_2D =
      "MULTISURFACE (CURVEPOLYGON (CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0)))";
  private static final String MS_Z =
      "MULTISURFACE Z (CURVEPOLYGON Z (CIRCULARSTRING Z (-2 0 1, 0 2 2, 2 0 3, 0 -2 4, -2 0 1)))";
  private static final String MS_M =
      "MULTISURFACE M (CURVEPOLYGON M (CIRCULARSTRING M (-2 0 1, 0 2 2, 2 0 3, 0 -2 4, -2 0 1)))";
  private static final String MS_ZM =
      "MULTISURFACE ZM (CURVEPOLYGON ZM (CIRCULARSTRING ZM (-2 0 1 5, 0 2 2 6, 2 0 3 7, 0 -2 4 8, -2 0 1 5)))";

  public static void main(String[] args) {
    TestRunner.run(GeosCurveIOFlavourTest.class);
  }

  public GeosCurveIOFlavourTest(String name) {
    super(name);
  }

  public void testWriterDefaultFlavorIsExtended() {
    assertEquals(WKBConstants.wkbExtended, new CurveWKBWriter().getFlavor());
    assertEquals(WKBConstants.wkbExtended, new WKBWriter().getFlavor());
  }

  public void testIsoTypeWords2dZMZM() throws Exception {
    assertIsoType(CS_2D, 2, WKBConstants.wkbCircularString);
    assertIsoType(CS_Z, 3, 1008);
    assertIsoType(CS_M, 3, Ordinate.createXYM(), 2008);
    assertIsoType(CS_ZM, 4, 3008);

    assertIsoType(CC_2D, 2, WKBConstants.wkbCompoundCurve);
    assertIsoType(CC_Z, 3, 1009);
    assertIsoType(CC_M, 3, Ordinate.createXYM(), 2009);
    assertIsoType(CC_ZM, 4, 3009);

    assertIsoType(CP_2D, 2, WKBConstants.wkbCurvePolygon);
    assertIsoType(CP_Z, 3, 1010);
    assertIsoType(CP_M, 3, Ordinate.createXYM(), 2010);
    assertIsoType(CP_ZM, 4, 3010);

    assertIsoType(MC_2D, 2, WKBConstants.wkbMultiCurve);
    assertIsoType(MC_Z, 3, 1011);
    assertIsoType(MC_M, 3, Ordinate.createXYM(), 2011);
    assertIsoType(MC_ZM, 4, 3011);

    assertIsoType(MS_2D, 2, WKBConstants.wkbMultiSurface);
    assertIsoType(MS_Z, 3, 1012);
    assertIsoType(MS_M, 3, Ordinate.createXYM(), 2012);
    assertIsoType(MS_ZM, 4, 3012);
  }

  public void testExtendedTypeWordsUseEwkbBits() throws Exception {
    assertExtendedType(CS_2D, 2, WKBConstants.wkbCircularString);
    assertExtendedType(CS_Z, 3, WKBConstants.wkbCircularString | 0x80000000);
    assertExtendedType(CS_M, 3, Ordinate.createXYM(),
        WKBConstants.wkbCircularString | 0x40000000);
    assertExtendedType(CS_ZM, 4,
        WKBConstants.wkbCircularString | 0x80000000 | 0x40000000);

    assertExtendedType(CC_Z, 3, WKBConstants.wkbCompoundCurve | 0x80000000);
    assertExtendedType(CP_Z, 3, WKBConstants.wkbCurvePolygon | 0x80000000);
    assertExtendedType(MC_Z, 3, WKBConstants.wkbMultiCurve | 0x80000000);
    assertExtendedType(MS_Z, 3, WKBConstants.wkbMultiSurface | 0x80000000);
  }

  public void testIsoCircularStringZBytePin() throws Exception {
    Geometry g = readCurve(CS_Z);
    CurveWKBWriter w = new CurveWKBWriter(3);
    w.setFlavor(WKBConstants.wkbIso);
    String hex = WKBWriter.toHex(w.write(g));
    assertEquals(HEX_ISO_CIRCULARSTRING_Z, hex);
    assertEquals(1008, typeWord(hex));
    assertEquals(WKBConstants.wkbIso, WKBReader.detectFlavor(1008));

    Geometry back = new CurveWKBReader(g.getFactory())
        .read(WKBReader.hexToBytes(HEX_ISO_CIRCULARSTRING_Z));
    assertTrue(back instanceof CircularString);
    Coordinate[] c = back.getCoordinates();
    assertEquals(1.0, c[0].getZ(), 0.0);
    assertEquals(2.0, c[1].getZ(), 0.0);
    assertEquals(3.0, c[2].getZ(), 0.0);
  }

  public void testBothFlavoursRoundTripZm() throws Exception {
    assertWkbRoundTrip(CS_ZM, 4, WKBConstants.wkbIso, CircularString.class);
    assertWkbRoundTrip(CS_ZM, 4, WKBConstants.wkbExtended, CircularString.class);
    assertWkbRoundTrip(CC_ZM, 4, WKBConstants.wkbIso, CompoundCurve.class);
    assertWkbRoundTrip(CC_ZM, 4, WKBConstants.wkbExtended, CompoundCurve.class);
    assertWkbRoundTrip(CP_ZM, 4, WKBConstants.wkbIso, CurvePolygon.class);
    assertWkbRoundTrip(CP_ZM, 4, WKBConstants.wkbExtended, CurvePolygon.class);
    assertWkbRoundTrip(MC_ZM, 4, WKBConstants.wkbIso, MultiCurve.class);
    assertWkbRoundTrip(MC_ZM, 4, WKBConstants.wkbExtended, MultiCurve.class);
    assertWkbRoundTrip(MS_ZM, 4, WKBConstants.wkbIso, MultiSurface.class);
    assertWkbRoundTrip(MS_ZM, 4, WKBConstants.wkbExtended, MultiSurface.class);
  }

  public void testIsoHasNoSrid() throws Exception {
    Geometry g = readCurve(CS_2D);
    g.setSRID(4326);
    CurveWKBWriter iso = new CurveWKBWriter(2, ByteOrderValues.BIG_ENDIAN, true);
    iso.setFlavor(WKBConstants.wkbIso);
    byte[] wkb = iso.write(g);
    assertEquals(WKBConstants.wkbCircularString, typeWord(WKBWriter.toHex(wkb)));
    assertEquals(57, wkb.length);

    CurveWKBWriter ext = new CurveWKBWriter(2, ByteOrderValues.BIG_ENDIAN, true);
    byte[] ewkb = ext.write(g);
    assertEquals(WKBConstants.wkbCircularString | 0x20000000,
        typeWord(WKBWriter.toHex(ewkb)));
    assertEquals(61, ewkb.length);
  }

  public void testCurvePolygonRingsAreChildWkb() throws Exception {
    Geometry g = readCurve(CP_Z);
    CurveWKBWriter w = new CurveWKBWriter(3);
    w.setFlavor(WKBConstants.wkbIso);
    byte[] wkb = w.write(g);
    assertEquals(1010, typeWord(WKBWriter.toHex(wkb)));
    int numRings = ((wkb[5] & 0xff) << 24) | ((wkb[6] & 0xff) << 16)
        | ((wkb[7] & 0xff) << 8) | (wkb[8] & 0xff);
    assertEquals(1, numRings);
    assertEquals(0, wkb[9]);
    int ringType = ((wkb[10] & 0xff) << 24) | ((wkb[11] & 0xff) << 16)
        | ((wkb[12] & 0xff) << 8) | (wkb[13] & 0xff);
    assertEquals(1008, ringType);
  }

  public void testWktSpacedSuffixNotGlued() throws Exception {
    assertEquals(CS_2D, writeWkt(CS_2D, 2));
    assertEquals(CS_Z, writeWkt(CS_Z, 3));
    assertEquals(CS_M, writeWkt(CS_M, 4));
    assertEquals(CS_ZM, writeWkt(CS_ZM, 4));

    assertEquals(CC_2D, writeWkt(CC_2D, 2));
    assertEquals(CC_Z, writeWkt(CC_Z, 3));
    assertEquals(CC_M, writeWkt(CC_M, 4));
    assertEquals(CC_ZM, writeWkt(CC_ZM, 4));

    assertEquals(CP_2D, writeWkt(CP_2D, 2));
    assertEquals(CP_Z, writeWkt(CP_Z, 3));
    assertEquals(CP_M, writeWkt(CP_M, 4));
    assertEquals(CP_ZM, writeWkt(CP_ZM, 4));

    assertEquals(MC_2D, writeWkt(MC_2D, 2));
    assertEquals(MC_Z, writeWkt(MC_Z, 3));
    assertEquals(MC_M, writeWkt(MC_M, 4));
    assertEquals(MC_ZM, writeWkt(MC_ZM, 4));

    assertEquals(MS_2D, writeWkt(MS_2D, 2));
    assertEquals(MS_Z, writeWkt(MS_Z, 3));
    assertEquals(MS_M, writeWkt(MS_M, 4));
    assertEquals(MS_ZM, writeWkt(MS_ZM, 4));
  }

  public void testWktReaderAcceptsGluedAndSpaced() throws Exception {
    Geometry spaced = readCurve("CIRCULARSTRING Z (0 0 1, 5 5 2, 10 0 3)");
    Geometry glued = readCurve("CIRCULARSTRINGZ(0 0 1, 5 5 2, 10 0 3)");
    assertTrue(spaced instanceof CircularString);
    assertTrue(glued instanceof CircularString);
    assertEquals(1.0, glued.getCoordinates()[0].getZ(), 0.0);
    assertTrue(spaced.equalsExact(glued));

    Geometry zmGlued = readCurve("CIRCULARSTRINGZM(0 0 1 4, 5 5 2 5, 10 0 3 6)");
    assertEquals(4.0, zmGlued.getCoordinates()[0].getM(), 0.0);
  }

  public void testCompoundCurveLineStringIsBareCircularStringTagged() throws Exception {
    String emitted = writeWkt(CC_2D, 2);
    assertTrue(emitted, emitted.startsWith("COMPOUNDCURVE (("));
    assertTrue(emitted, emitted.indexOf("CIRCULARSTRING (") > 0);
    assertTrue(emitted, emitted.indexOf("LINESTRING") < 0);
  }

  public void testCoreWkbWriterDoesNotEmitLineStringType2() throws Exception {
    Geometry g = readCurve(CS_2D);
    try {
      byte[] wkb = new WKBWriter().write(g);
      fail("core WKBWriter must not flatten CircularString, got type "
          + typeWord(WKBWriter.toHex(wkb)));
    }
    catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().indexOf("CurveWKBWriter") >= 0);
    }
    byte[] honest = new CurveWKBWriter().write(g);
    assertEquals(WKBConstants.wkbCircularString, typeWord(WKBWriter.toHex(honest)));
    assertTrue(typeWord(WKBWriter.toHex(honest)) != WKBConstants.wkbLineString);
  }

  /**
   * GEO-TIN (Triangle=15, PolyhedralSurface=16, TIN=17) waits Architect
   * SIGN. This draft is curve 8–12 plus ISO/EXTENDED Z/M/ZM only.
   */
  public void testWkb15_16_17StayUnknown() {
    assertUnknownWkbType("010F000000", 15);
    assertUnknownWkbType("0110000000", 16);
    assertUnknownWkbType("0111000000", 17);
    assertUnknownWkbType("01F7030000", 15);
  }

  public void testCoreWkbWriterDoesNotFlattenOtherCurveTypes() throws Exception {
    assertCoreRefuses(CC_2D);
    assertCoreRefuses(CP_2D);
    assertCoreRefuses(MC_2D);
    assertCoreRefuses(MS_2D);
  }

  private static void assertUnknownWkbType(String hex, int typeCode) {
    byte[] wkb = WKBReader.hexToBytes(hex);
    try {
      new WKBReader().read(wkb);
      fail("core WKBReader must throw for WKB type " + typeCode);
    }
    catch (ParseException e) {
      assertTrue(e.getMessage(),
          e.getMessage().indexOf("Unknown WKB type " + typeCode) >= 0);
    }
    try {
      new CurveWKBReader().read(wkb);
      fail("CurveWKBReader must throw for WKB type " + typeCode);
    }
    catch (ParseException e) {
      assertTrue(e.getMessage(),
          e.getMessage().indexOf("Unknown WKB type " + typeCode) >= 0);
    }
  }

  private static void assertCoreRefuses(String wkt) throws Exception {
    Geometry g = readCurve(wkt);
    try {
      new WKBWriter().write(g);
      fail("core WKBWriter must not flatten " + g.getGeometryType());
    }
    catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().indexOf("8") >= 0
          || e.getMessage().indexOf("CurveWKBWriter") >= 0);
    }
  }

  private static void assertIsoType(String wkt, int dim, int expectedType)
      throws Exception {
    assertIsoType(wkt, dim, null, expectedType);
  }

  private static void assertIsoType(String wkt, int dim,
      EnumSet<Ordinate> ordinates, int expectedType) throws Exception {
    Geometry g = readCurve(wkt);
    CurveWKBWriter w = new CurveWKBWriter(dim);
    if (ordinates != null) {
      w.setOutputOrdinates(ordinates);
    }
    w.setFlavor(WKBConstants.wkbIso);
    int type = typeWord(WKBWriter.toHex(w.write(g)));
    assertEquals(wkt + " ISO type", expectedType, type);
    if (expectedType >= 1000) {
      assertEquals(WKBConstants.wkbIso, WKBReader.detectFlavor(type));
    }
  }

  private static void assertExtendedType(String wkt, int dim, int expectedType)
      throws Exception {
    assertExtendedType(wkt, dim, null, expectedType);
  }

  private static void assertExtendedType(String wkt, int dim,
      EnumSet<Ordinate> ordinates, int expectedType) throws Exception {
    Geometry g = readCurve(wkt);
    CurveWKBWriter w = new CurveWKBWriter(dim);
    if (ordinates != null) {
      w.setOutputOrdinates(ordinates);
    }
    assertEquals(WKBConstants.wkbExtended, w.getFlavor());
    int type = typeWord(WKBWriter.toHex(w.write(g)));
    assertEquals(wkt + " Extended type", expectedType, type);
  }

  private static void assertWkbRoundTrip(String wkt, int dim, int flavor,
      Class<?> expectedClass) throws Exception {
    Geometry g = readCurve(wkt);
    CurveWKBWriter w = new CurveWKBWriter(dim);
    w.setFlavor(flavor);
    Geometry back = new CurveWKBReader(g.getFactory()).read(w.write(g));
    assertEquals(expectedClass, back.getClass());
    Coordinate[] a = g.getCoordinates();
    Coordinate[] b = back.getCoordinates();
    assertEquals(a.length, b.length);
    for (int i = 0; i < a.length; i++) {
      assertEquals(a[i].x, b[i].x, 0.0);
      assertEquals(a[i].y, b[i].y, 0.0);
      assertEquals(a[i].getZ(), b[i].getZ(), 0.0);
      assertEquals(a[i].getM(), b[i].getM(), 0.0);
    }
  }

  private static String writeWkt(String wkt, int dim) throws Exception {
    return new CurveWKTWriter(dim).write(readCurve(wkt));
  }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static int typeWord(String hex) {
    byte[] wkb = WKBReader.hexToBytes(hex);
    return ((wkb[1] & 0xff) << 24) | ((wkb[2] & 0xff) << 16)
        | ((wkb[3] & 0xff) << 8) | (wkb[4] & 0xff);
  }
}
