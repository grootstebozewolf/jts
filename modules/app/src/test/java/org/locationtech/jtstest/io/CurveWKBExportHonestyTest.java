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
package org.locationtech.jtstest.io;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.WKBConstants;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jtstest.cmd.CommandOptions;
import org.locationtech.jtstest.cmd.CommandOutput;
import org.locationtech.jtstest.cmd.GeometryOutput;
import org.locationtech.jtstest.testbuilder.io.IOUtil;
import org.locationtech.jtstest.testbuilder.io.XMLTestWriter;
import org.locationtech.jtstest.testbuilder.model.TestCaseEdit;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Default / export WKB paths must use {@code CurveWKBWriter}, not the
 * flattening core {@code WKBWriter}. A CircularString that hits
 * {@code instanceof LineString} would emit type 2; a CurvePolygon that
 * hits Polygon would emit type 3.
 */
public class CurveWKBExportHonestyTest extends TestCase {

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
  private static final String LINESTRING = "LINESTRING (0 0, 1 1)";
  private static final String POLYGON = "POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))";

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CurveWKBExportHonestyTest.class); }
  public CurveWKBExportHonestyTest(String name) { super(name); }

  private static Geometry read(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  /**
   * Low 8 bits of the WKB type word, endian-aware. EWKB Z/M/SRID flags
   * live in the high bits and must not change the geometry type code.
   */
  private static int typeCode(String hex) {
    byte[] wkb = WKBReader.hexToBytes(hex.trim());
    int b1 = wkb[1] & 0xff;
    int b2 = wkb[2] & 0xff;
    int b3 = wkb[3] & 0xff;
    int b4 = wkb[4] & 0xff;
    int type;
    if ((wkb[0] & 0xff) == WKBConstants.wkbNDR) {
      type = b1 | (b2 << 8) | (b3 << 16) | (b4 << 24);
    }
    else {
      type = (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }
    return type & 0xff;
  }

  private static void assertNotFlattened(String path, String hex, int expectedType) {
    int type = typeCode(hex);
    assertTrue(path + " must not emit LineString type 2, got " + type,
        type != WKBConstants.wkbLineString);
    assertTrue(path + " must not emit Polygon type 3, got " + type,
        type != WKBConstants.wkbPolygon);
    assertEquals(path + " WKB type", expectedType, type);
  }

  private static String jtsopWkb(Geometry g, int srid) {
    CommandOutput out = new CommandOutput(true);
    new GeometryOutput(out).write(g, srid, CommandOptions.FORMAT_WKB);
    return out.getOutput().trim();
  }

  private static String xmlCaseWkb(Geometry g) {
    TestCaseEdit tc = new TestCaseEdit(new Geometry[] { g, null }, "curve");
    String xml = new XMLTestWriter().getTestXML(tc, false);
    int start = xml.indexOf("<a>");
    int end = xml.indexOf("</a>");
    assertTrue("XMLTestWriter WKB case should contain <a>: " + xml,
        start >= 0 && end > start);
    return xml.substring(start + 3, end).trim();
  }

  public void testFileExportCircularStringIsNotType2() throws Exception {
    assertNotFlattened("IOUtil.toWKBHex CircularString",
        IOUtil.toWKBHex(read(CIRCULARSTRING)), WKBConstants.wkbCircularString);
  }

  public void testFileExportCompoundCurveIsNotType2() throws Exception {
    assertNotFlattened("IOUtil.toWKBHex CompoundCurve",
        IOUtil.toWKBHex(read(COMPOUNDCURVE)), WKBConstants.wkbCompoundCurve);
  }

  public void testFileExportCurvePolygonIsNotType3() throws Exception {
    assertNotFlattened("IOUtil.toWKBHex CurvePolygon",
        IOUtil.toWKBHex(read(CURVEPOLYGON)), WKBConstants.wkbCurvePolygon);
  }

  public void testFileExportMultiCurveIsNotType2Or3() throws Exception {
    assertNotFlattened("IOUtil.toWKBHex MultiCurve",
        IOUtil.toWKBHex(read(MULTICURVE)), WKBConstants.wkbMultiCurve);
  }

  public void testFileExportMultiSurfaceIsNotType2Or3() throws Exception {
    assertNotFlattened("IOUtil.toWKBHex MultiSurface",
        IOUtil.toWKBHex(read(MULTISURFACE)), WKBConstants.wkbMultiSurface);
  }

  public void testXmlTestWriterCircularStringIsNotType2() throws Exception {
    assertNotFlattened("XMLTestWriter CircularString",
        xmlCaseWkb(read(CIRCULARSTRING)), WKBConstants.wkbCircularString);
  }

  public void testXmlTestWriterCompoundCurveIsNotType2() throws Exception {
    assertNotFlattened("XMLTestWriter CompoundCurve",
        xmlCaseWkb(read(COMPOUNDCURVE)), WKBConstants.wkbCompoundCurve);
  }

  public void testXmlTestWriterCurvePolygonIsNotType3() throws Exception {
    assertNotFlattened("XMLTestWriter CurvePolygon",
        xmlCaseWkb(read(CURVEPOLYGON)), WKBConstants.wkbCurvePolygon);
  }

  public void testXmlTestWriterOpPayloadCurvePolygonIsNotType3() throws Exception {
    String xml = new XMLTestWriter().getTestXML(
        read(CURVEPOLYGON), "equals", new String[0], false);
    java.util.regex.Matcher m = java.util.regex.Pattern
        .compile("[0-9A-Fa-f]{10,}").matcher(xml);
    assertTrue("XMLTestWriter op WKB should contain hex: " + xml, m.find());
    assertNotFlattened("XMLTestWriter op CurvePolygon",
        m.group(), WKBConstants.wkbCurvePolygon);
  }

  public void testJtsopCircularStringIsNotType2() throws Exception {
    assertNotFlattened("jtsop GeometryOutput CircularString",
        jtsopWkb(read(CIRCULARSTRING), 0), WKBConstants.wkbCircularString);
  }

  public void testJtsopCompoundCurveIsNotType2() throws Exception {
    assertNotFlattened("jtsop GeometryOutput CompoundCurve",
        jtsopWkb(read(COMPOUNDCURVE), 0), WKBConstants.wkbCompoundCurve);
  }

  public void testJtsopCurvePolygonIsNotType3() throws Exception {
    assertNotFlattened("jtsop GeometryOutput CurvePolygon",
        jtsopWkb(read(CURVEPOLYGON), 0), WKBConstants.wkbCurvePolygon);
  }

  public void testJtsopSridCircularStringIsNotType2() throws Exception {
    assertNotFlattened("jtsop GeometryOutput SRID CircularString",
        jtsopWkb(read(CIRCULARSTRING), 4326), WKBConstants.wkbCircularString);
  }

  public void testFileExportPlainLineStringStillType2() throws Exception {
    assertEquals("plain LineString export stays type 2",
        WKBConstants.wkbLineString, typeCode(IOUtil.toWKBHex(read(LINESTRING))));
  }

  public void testFileExportPlainPolygonStillType3() throws Exception {
    assertEquals("plain Polygon export stays type 3",
        WKBConstants.wkbPolygon, typeCode(IOUtil.toWKBHex(read(POLYGON))));
  }
}
