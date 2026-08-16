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
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.WKBConstants;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.io.gml2.GMLWriter;
import org.locationtech.jts.io.kml.KMLWriter;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Leftover write honesty that #51 does not own (Architect SIGN
 * 16 Aug 2026, ClaimId MMF-IO). I/O type identity, not overlay
 * honesty. WKT CompoundCurve members, toText, GML/KML refuse.
 * Core WKB 8–12 + ISO 1008–3012 refuse is #51 on #7 — this file
 * pins throw / CurveWKBWriter, it does not reimplement flavour.
 * HOLD GEO-TIN 15–17. HOLD elliptic / Bézier. Cite 13249-3. No DOI.
 */
public class IoFlattenHonestyTest extends GeometryTestCase {

  private static final String CIRCULARSTRING =
      "CIRCULARSTRING (0 0, 5 5, 10 0)";
  private static final String COMPOUNDCURVE =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 20 0))";
  private static final String CURVEPOLYGON =
      "CURVEPOLYGON (CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0))";
  private static final String MULTICURVE =
      "MULTICURVE (CIRCULARSTRING (0 0, 5 5, 10 0))";
  private static final String MULTISURFACE =
      "MULTISURFACE (CURVEPOLYGON (CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0)))";

  public static void main(String[] args) {
    TestRunner.run(IoFlattenHonestyTest.class);
  }

  public IoFlattenHonestyTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static void assertRefused(String site, Runnable r) {
    try {
      r.run();
      fail(site + " must not flatten SQL/MM ISO/IEC 13249-3 types 8–12");
    }
    catch (IllegalArgumentException e) {
      assertTrue(site + " message: " + e.getMessage(),
          e.getMessage().indexOf("ISO/IEC 13249-3") >= 0
              || e.getMessage().indexOf("must not flatten") >= 0
              || e.getMessage().indexOf("CurveWKBWriter") >= 0);
    }
  }

  private static int typeCode(byte[] wkb) {
    int type = ((wkb[1] & 0xff) << 24) | ((wkb[2] & 0xff) << 16)
        | ((wkb[3] & 0xff) << 8) | (wkb[4] & 0xff);
    if ((wkb[0] & 0xff) == WKBConstants.wkbNDR) {
      type = (wkb[1] & 0xff) | ((wkb[2] & 0xff) << 8)
          | ((wkb[3] & 0xff) << 16) | ((wkb[4] & 0xff) << 24);
    }
    return type & 0xff;
  }

  public void testCoreWktWriterAllowsCircularStringKeyword() throws Exception {
    Geometry cs = readCurve(CIRCULARSTRING);
    String wkt = new WKTWriter().write(cs);
    assertTrue("CS keyword OK, was: " + wkt,
        wkt.toUpperCase().startsWith("CIRCULARSTRING"));
  }

  public void testCoreWktWriterRefusesCompoundCurveFlatten() throws Exception {
    Geometry cc = readCurve(COMPOUNDCURVE);
    assertRefused("WKTWriter", new Runnable() {
      public void run() { new WKTWriter().write(cc); }
    });
  }

  public void testToTextKeepsCompoundCurveMembers() throws Exception {
    Geometry cc = readCurve(COMPOUNDCURVE);
    String wkt = cc.toText();
    assertTrue("toText must keep CIRCULARSTRING member, was: " + wkt,
        wkt.toUpperCase().contains("CIRCULARSTRING"));
    CompoundCurve back = (CompoundCurve) new CurveWKTReader(
        new CurveGeometryFactory()).read(wkt);
    assertEquals(2, back.getNumMembers());
    assertTrue(back.getMemberN(0) instanceof CircularString);
  }

  public void testToTextKeepsCurveCollectionTags() throws Exception {
    assertTrue(readCurve(CURVEPOLYGON).toText().toUpperCase()
        .contains("CIRCULARSTRING"));
    assertTrue(readCurve(MULTICURVE).toText().toUpperCase()
        .startsWith("MULTICURVE"));
    assertTrue(readCurve(MULTISURFACE).toText().toUpperCase()
        .startsWith("MULTISURFACE"));
  }

  public void testGmlKmlRefuseCircularString() throws Exception {
    Geometry cs = readCurve(CIRCULARSTRING);
    assertRefused("GMLWriter", new Runnable() {
      public void run() { new GMLWriter().write(cs); }
    });
    assertRefused("KMLWriter", new Runnable() {
      public void run() { new KMLWriter().write(cs); }
    });
  }

  public void testCoreWkbWriterRefusesCurveFlatten() throws Exception {
    final Geometry cs = readCurve(CIRCULARSTRING);
    final Geometry cc = readCurve(COMPOUNDCURVE);
    assertRefused("WKBWriter CS", new Runnable() {
      public void run() { new WKBWriter().write(cs); }
    });
    assertRefused("WKBWriter CC", new Runnable() {
      public void run() { new WKBWriter().write(cc); }
    });
  }

  public void testCurveWkbWriterKeepsTypes8to12() throws Exception {
    CurveWKBWriter w = new CurveWKBWriter();
    assertEquals(WKBConstants.wkbCircularString,
        typeCode(w.write(readCurve(CIRCULARSTRING))));
    assertEquals(WKBConstants.wkbCompoundCurve,
        typeCode(w.write(readCurve(COMPOUNDCURVE))));
    assertEquals(WKBConstants.wkbCurvePolygon,
        typeCode(w.write(readCurve(CURVEPOLYGON))));
    assertEquals(WKBConstants.wkbMultiCurve,
        typeCode(w.write(readCurve(MULTICURVE))));
    assertEquals(WKBConstants.wkbMultiSurface,
        typeCode(w.write(readCurve(MULTISURFACE))));
  }

  public void testLinearRingIsOnlyAllowedWkbCollapse() {
    GeometryFactory gf = new GeometryFactory();
    Coordinate[] pts = new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(1, 1),
        new Coordinate(0, 0)
    };
    LinearRing ring = gf.createLinearRing(pts);
    LineString line = gf.createLineString(pts);
    assertEquals(typeCode(new WKBWriter().write(line)),
        typeCode(new WKBWriter().write(ring)));
    assertEquals(WKBConstants.wkbLineString,
        typeCode(new WKBWriter().write(ring)));
  }
}
