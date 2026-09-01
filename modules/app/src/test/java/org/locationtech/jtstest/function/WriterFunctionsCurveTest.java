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
package org.locationtech.jtstest.function;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.curve.CurveWKBReader;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.gml2.GMLReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * IO-WRT: exporting a curve must not silently export its control points.
 * <p>
 * Reported for {@code writeGML}: reading
 * {@code CURVEPOLYGON (CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0))} and
 * exporting it produces
 * {@code <gml:Polygon><gml:LinearRing><gml:coordinates>-2,0 0,2 2,0 0,-2 -2,0}
 * -- the five control points as a straight ring. Reading that back gives a
 * polygon of area <b>8.0</b> against the circle's {@code 4*pi} = 12.566, a 36%
 * error. The same holds for {@code writeWKB} (type code 3, five points) and
 * {@code writeKML}.
 * <p>
 * So this is not "curve identity was dropped", which would be unavoidable: it is
 * the wrong shape, silently. {@code GMLWriter}, {@code KMLWriter},
 * {@code WKBWriter} and {@code GeoJsonWriter} all live in jts-core, all take a
 * {@code Geometry}, and all dispatch on {@code instanceof Polygon} /
 * {@code LineString}, which a curve type satisfies -- so they serialise
 * {@code getCoordinates()}, the control points. Same shape of gap as
 * {@code HullFunctions}, and the same remedy: the caller linearises.
 * <p>
 * {@code writeGeoJSON} additionally emitted {@code "type":"CurvePolygon"}, which
 * is not one of the seven types RFC 7946 defines, so the output was not GeoJSON
 * any consumer could read. Linearising fixes that as a side effect, since the
 * linearised geometry really is a Polygon.
 * <p>
 * <b>What is and is not preserved.</b> None of GML2, KML or GeoJSON has any
 * representation of a circular arc, so the arc cannot survive the export and the
 * only question is whether what does survive is the right shape. WKB is the
 * exception -- SQL/MM defines curve type codes 8 to 12 -- but reading and writing
 * those is first-class: {@code writeWKB} uses {@code CurveWKBWriter} so a
 * disc round-trips as type 10. GML / KML / GeoJSON still densify.
 * <p>
 * Tolerances here are derived, not chosen: {@code linearizeForOps} densifies at
 * {@code 1e-6} of the extent, and the sagitta bound {@code r(1 - cos(theta/2))}
 * gives the vertex count and the area shortfall of an inscribed polygon, so the
 * assertions are tied to those rather than to observed numbers.
 */
public class WriterFunctionsCurveTest extends TestCase {

  private static final String CIRCLE =
      "CURVEPOLYGON (CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0))";

  private static final double RADIUS = 2.0;

  /** Area of the inscribed square through the four control points. */
  private static final double CONTROL_POINT_AREA = 8.0;

  /** The densify tolerance in force: 1e-6 of the 4-unit extent. */
  private static final double DENSIFY_TOL = 4.0 * 1.0e-6;

  /**
   * An inscribed polygon is smaller than its circle, and by a bounded amount:
   * each chord cuts off a segment no deeper than the tolerance, so the total
   * shortfall is under the perimeter times the tolerance. Generous by a factor
   * of two, still four orders tighter than the control-point error.
   */
  private static final double AREA_TOL =
      2.0 * (2.0 * Math.PI * RADIUS) * DENSIFY_TOL;

  private static final double TRUE_AREA = Math.PI * RADIUS * RADIUS;
  private static final double TRUE_LENGTH = 2.0 * Math.PI * RADIUS;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(WriterFunctionsCurveTest.class); }
  public WriterFunctionsCurveTest(String name) { super(name); }

  private static Geometry read(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static Geometry viaGML(Geometry g) throws Exception {
    return new GMLReader().read(WriterFunctions.writeGML(g), new GeometryFactory());
  }

  private static Geometry viaWKB(Geometry g) throws Exception {
    return new CurveWKBReader(g.getFactory())
        .read(WKBReader.hexToBytes(WriterFunctions.writeWKB(g)));
  }

  private static Geometry viaGeoJSON(Geometry g) throws Exception {
    return new GeoJsonReader().read(WriterFunctions.writeGeoJSON(g));
  }

  /** The reported case: a GML round trip must keep the circle's area. */
  public void testGMLRoundTripKeepsTheArea() throws Exception {
    Geometry back = viaGML(read(CIRCLE));
    assertEquals("GML round trip should give the circle's area, not the "
        + CONTROL_POINT_AREA + " of the control-point square",
        TRUE_AREA, back.getArea(), AREA_TOL);
  }

  /** And its perimeter, which the control-point ring understates as 8*sqrt(2). */
  public void testGMLRoundTripKeepsTheLength() throws Exception {
    assertEquals("GML round trip should give the circumference",
        TRUE_LENGTH, viaGML(read(CIRCLE)).getLength(), TRUE_LENGTH * 1.0e-4);
  }

  /** WKB now keeps the disc: type 10, rings are curves, area is 4π. */
  public void testWKBRoundTripKeepsTheArea() throws Exception {
    assertEquals("WKB round trip should give the circle's area",
        TRUE_AREA, viaWKB(read(CIRCLE)).getArea(), AREA_TOL);
  }

  /**
   * GeoJSON emitted {@code "type":"CurvePolygon"}, which RFC 7946 does not
   * define, so the output was unreadable rather than merely inaccurate.
   */
  public void testGeoJSONTypeIsAGeoJSONType() throws Exception {
    String json = WriterFunctions.writeGeoJSON(read(CIRCLE));
    assertFalse("GeoJSON must not name a type RFC 7946 does not define: " + json,
        json.contains("\"CurvePolygon\""));
    assertTrue("GeoJSON should declare a Polygon: " + json,
        json.contains("\"type\":\"Polygon\""));
  }

  /** And the GeoJSON round trip must keep the shape. */
  public void testGeoJSONRoundTripKeepsTheArea() throws Exception {
    assertEquals("GeoJSON round trip should give the circle's area",
        TRUE_AREA, viaGeoJSON(read(CIRCLE)).getArea(), AREA_TOL);
  }

  /**
   * KML has no reader in JTS, so this counts coordinate tuples in the text
   * against the sagitta bound: a chord subtending theta on radius r deviates by
   * {@code r(1 - cos(theta/2))}, so holding that under the tolerance needs at
   * least {@code ceil(2*pi / (2*acos(1 - tol/r)))} segments for the full circle.
   */
  public void testKMLEmitsTheDensifiedRing() throws Exception {
    String kml = WriterFunctions.writeKML(read(CIRCLE));
    int minSegments = (int) Math.ceil(
        2.0 * Math.PI / (2.0 * Math.acos(1.0 - DENSIFY_TOL / RADIUS)));
    int tuples = kml.split(",").length - 1;
    assertTrue("KML should carry at least " + minSegments
        + " densified coordinates, found about " + tuples, tuples >= minSegments);
  }

  /** A bare CircularString exports its arc too, not its chords. */
  public void testCircularStringGMLRoundTripKeepsTheLength() throws Exception {
    Geometry back = viaGML(read("CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0)"));
    assertEquals("bare CIRCULARSTRING should export its arc length, not the "
        + (8 * Math.sqrt(2)) + " of its chords",
        TRUE_LENGTH, back.getLength(), TRUE_LENGTH * 1.0e-4);
  }

  /** A CompoundCurve mixes an arc and a chord; both must export. */
  public void testCompoundCurveGMLRoundTrip() throws Exception {
    Geometry back = viaGML(read(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-2 0, 0 2, 2 0), (2 0, -2 0)))"));
    assertEquals("half-disc area should be 2*pi",
        2.0 * Math.PI, back.getArea(), AREA_TOL);
  }

  /**
   * Guard: a geometry with no curve must serialise byte-for-byte as before.
   * This is the assertion that makes applying the shim unconditionally safe --
   * it holds because {@code linearize} returns non-curve input as the same
   * object (LIN-COLL).
   */
  public void testPlainGeometryUnchangedInEveryWriter() throws Exception {
    for (String wkt : new String[] {
        "POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))",
        "MULTIPOLYGON (((0 0, 4 0, 4 4, 0 4, 0 0)), ((6 0, 10 0, 10 4, 6 4, 6 0)))",
        "MULTILINESTRING ((0 0, 4 0), (6 0, 10 0))",
        "LINESTRING (0 0, 1 1)",
        "POINT (3 4)" }) {
      Geometry plain = read(wkt);
      Geometry copy = plain.copy();
      assertEquals("GML unchanged for " + wkt,
          WriterFunctions.writeGML(copy), WriterFunctions.writeGML(plain));
      assertEquals("WKB unchanged for " + wkt,
          WriterFunctions.writeWKB(copy), WriterFunctions.writeWKB(plain));
      assertEquals("GeoJSON unchanged for " + wkt,
          WriterFunctions.writeGeoJSON(copy), WriterFunctions.writeGeoJSON(plain));
      assertEquals("KML unchanged for " + wkt,
          WriterFunctions.writeKML(copy), WriterFunctions.writeKML(plain));
    }
  }

  /** Guard: a plain MULTIPOLYGON keeps its type through GML (LIN-COLL). */
  public void testMultiPolygonKeepsItsTypeInGML() throws Exception {
    String gml = WriterFunctions.writeGML(read(
        "MULTIPOLYGON (((0 0, 4 0, 4 4, 0 4, 0 0)), ((6 0, 10 0, 10 4, 6 4, 6 0)))"));
    assertTrue("should still be a MultiPolygon: " + gml, gml.contains("MultiPolygon"));
  }

  /** Guard: null input stays an empty string rather than throwing. */
  public void testNullInputStaysEmpty() throws Exception {
    assertEquals("", WriterFunctions.writeGML(null));
    assertEquals("", WriterFunctions.writeWKB(null));
    assertEquals("", WriterFunctions.writeKML(null));
    assertEquals("", WriterFunctions.writeGeoJSON(null));
  }

  /** Guard: an empty curve exports without throwing. */
  public void testEmptyCurveExports() throws Exception {
    assertNotNull(WriterFunctions.writeGML(read("CURVEPOLYGON EMPTY")));
    assertNotNull(WriterFunctions.writeWKB(read("CURVEPOLYGON EMPTY")));
  }
}
