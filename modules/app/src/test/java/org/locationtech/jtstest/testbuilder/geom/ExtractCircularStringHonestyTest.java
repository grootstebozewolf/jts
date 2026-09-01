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
package org.locationtech.jtstest.testbuilder.geom;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Extract Elements/Segments to Case must not flatten a circular arc
 * to {@code LINESTRING} (issue #78). Same paths as
 * {@code ExtractComponentTool}: {@link GeometryElementLocater} (drag)
 * and {@link SegmentExtracter} (Ctrl-drag).
 */
public class ExtractCircularStringHonestyTest extends TestCase {

  private static final String ARC =
      "CIRCULARSTRING (0 0, 1 1, 2 0)";
  private static final String TWO_ARC =
      "CIRCULARSTRING (0 0, 1 1, 2 0, 3 -1, 4 0)";
  private static final String CP =
      "CURVEPOLYGON (CIRCULARSTRING (0 0, 1 1, 2 0, 1 -1, 0 0))";

  public ExtractCircularStringHonestyTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(ExtractCircularStringHonestyTest.class);
  }

  public void testExtractElementsKeepsCircularString() throws ParseException {
    Geometry g = read(ARC);
    Geometry aoi = box(-10, -10, 10, 10);
    Geometry extracted = GeometryElementLocater.extractElements(g, aoi);
    assertCircularString(extracted, "elements");
  }

  public void testExtractSegmentsKeepsCircularString() throws ParseException {
    Geometry g = read(ARC);
    Geometry aoi = box(-10, -10, 10, 10);
    Geometry extracted = SegmentExtracter.extract(g, aoi);
    assertCircularString(extracted, "segments");
  }

  public void testExtractSegmentsTwoArcsStayCircularString() throws ParseException {
    Geometry g = read(TWO_ARC);
    Geometry aoi = box(-10, -10, 10, 10);
    Geometry extracted = SegmentExtracter.extract(g, aoi);
    assertCircularString(extracted, "segments two-arc");
    assertEquals(5, extracted.getNumPoints());
  }

  public void testExtractSegmentsOfCurvePolygonKeepsCircularStringShell()
      throws ParseException {
    Geometry g = read(CP);
    Geometry aoi = box(-10, -10, 10, 10);
    Geometry extracted = SegmentExtracter.extract(g, aoi);
    assertFalse("segments of CURVEPOLYGON must not be LINESTRING, got "
        + extracted.getClass().getName() + " " + write(extracted),
        extracted.getClass().equals(LineString.class));
    assertTrue("got " + write(extracted),
        extracted instanceof CircularString
            || extracted instanceof CurvePolygon);
    String wkt = write(extracted);
    assertFalse(wkt.startsWith("LINESTRING"));
    assertFalse(wkt.startsWith("MULTILINESTRING"));
  }

  public void testExtractElementsOfCurvePolygonDoesNotLinearize()
      throws ParseException {
    Geometry g = read(CP);
    Geometry aoi = box(-10, -10, 10, 10);
    Geometry extracted = GeometryElementLocater.extractElements(g, aoi);
    assertNotNull(extracted);
    assertFalse("elements of CURVEPOLYGON must not be LINESTRING, got "
        + extracted.getClass().getName() + " " + write(extracted),
        extracted.getClass().equals(LineString.class));
    String wkt = write(extracted);
    assertFalse(wkt.startsWith("LINESTRING"));
  }

  private static void assertCircularString(Geometry g, String path) {
    assertNotNull(path + " extracted nothing", g);
    assertTrue(path + " flattened to " + g.getClass().getName() + " " + write(g),
        g instanceof CircularString);
    assertFalse(g.getClass().equals(LineString.class));
    String wkt = write(g);
    assertTrue(path + " WKT " + wkt, wkt.startsWith("CIRCULARSTRING"));
    assertFalse(wkt.startsWith("LINESTRING"));
  }

  private static Geometry read(String wkt) throws ParseException {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static String write(Geometry g) {
    return new CurveWKTWriter().write(g);
  }

  private static Geometry box(double minx, double miny, double maxx, double maxy) {
    return new CurveGeometryFactory().toGeometry(new Envelope(minx, maxx, miny, maxy));
  }
}
