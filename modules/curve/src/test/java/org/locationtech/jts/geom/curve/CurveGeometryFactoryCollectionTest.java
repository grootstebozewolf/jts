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

import java.util.Arrays;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * FCT-COLL: collections built by the curve factory must keep curve identity.
 * <p>
 * Reported from visual QA: drawing an arc onto existing geometry in TestBuilder
 * produced a {@code MULTILINESTRING} instead of a {@code MULTICURVE}. The
 * combiner is innocent -- it hands the pieces to
 * {@code GeometryFactory.buildGeometry}, and the erasure happens in the factory:
 * <ul>
 * <li>{@code createMultiLineString} boxes {@code CircularString} members into a
 *     plain {@code MultiLineString}, so the arcs render as chords and write as
 *     flattened control points from that point on.</li>
 * <li>{@code buildGeometry} compares exact classes to decide homogeneity, so a
 *     {@code CircularString} next to a plain {@code LineString} -- both lineal,
 *     exactly what {@code MULTICURVE} exists to hold -- degrades all the way to
 *     {@code GEOMETRYCOLLECTION}.</li>
 * <li>{@code createMultiPolygon} likewise erases a {@code CurvePolygon} member
 *     into a plain {@code MultiPolygon} where {@code MULTISURFACE} is the type
 *     for it.</li>
 * </ul>
 * {@code MultiCurve extends MultiLineString} and
 * {@code MultiSurface extends MultiPolygon}, so returning the curve-aware type
 * from the inherited creators is signature-compatible and invisible to callers
 * that only wanted the supertype.
 * <p>
 * Guards pin the other side: input with no curve anywhere must keep producing
 * the plain types, bit for bit, and genuinely mixed-dimension input must keep
 * degrading to {@code GEOMETRYCOLLECTION} -- a {@code MULTICURVE} cannot hold a
 * point or a polygon, and pretending otherwise would trade one wrong type for
 * another.
 */
public class CurveGeometryFactoryCollectionTest extends GeometryTestCase {

  private static final CurveGeometryFactory F = new CurveGeometryFactory();

  public static void main(String[] args) {
    TestRunner.run(CurveGeometryFactoryCollectionTest.class);
  }

  public CurveGeometryFactoryCollectionTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(F).read(wkt);
  }

  private static LineString line(String wkt) throws Exception {
    return (LineString) readCurve(wkt);
  }

  private static Polygon poly(String wkt) throws Exception {
    return (Polygon) readCurve(wkt);
  }

  // -- createMultiLineString -------------------------------------------------

  /** The reported case: two arcs must make a MultiCurve, not a MultiLineString. */
  public void testTwoArcsMakeAMultiCurve() throws Exception {
    Geometry mc = F.createMultiLineString(new LineString[] {
        line("CIRCULARSTRING (0 0, 2 3, 10 0)"),
        line("CIRCULARSTRING (20 0, 25 5, 30 0)") });
    assertEquals("MultiCurve", mc.getGeometryType());
    assertTrue(mc instanceof MultiCurve);
  }

  /** Mixed arc and straight is exactly what MULTICURVE exists for. */
  public void testArcAndLineMakeAMultiCurve() throws Exception {
    Geometry mc = F.createMultiLineString(new LineString[] {
        line("CIRCULARSTRING (0 0, 2 3, 10 0)"),
        line("LINESTRING (20 0, 30 0)") });
    assertEquals("MultiCurve", mc.getGeometryType());
  }

  /** Guard: all-straight members keep the plain type. */
  public void testPlainLinesStayMultiLineString() throws Exception {
    Geometry mls = F.createMultiLineString(new LineString[] {
        line("LINESTRING (0 0, 10 0)"), line("LINESTRING (20 0, 30 0)") });
    assertEquals("MultiLineString", mls.getGeometryType());
    assertFalse("must not be silently upgraded", mls instanceof MultiCurve);
  }

  // -- createMultiPolygon ------------------------------------------------------

  public void testCurvePolygonMemberMakesAMultiSurface() throws Exception {
    Geometry ms = F.createMultiPolygon(new Polygon[] {
        poly("CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))"),
        poly("POLYGON ((20 0, 30 0, 30 10, 20 10, 20 0))") });
    assertEquals("MultiSurface", ms.getGeometryType());
    assertEquals("and the arc member keeps its area", 25.0 * Math.PI + 100.0,
        ms.getArea(), 1.0e-9);
  }

  /** Guard: all-plain polygons keep the plain type. */
  public void testPlainPolygonsStayMultiPolygon() throws Exception {
    Geometry mp = F.createMultiPolygon(new Polygon[] {
        poly("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"),
        poly("POLYGON ((20 0, 30 0, 30 10, 20 10, 20 0))") });
    assertEquals("MultiPolygon", mp.getGeometryType());
  }

  // -- buildGeometry -----------------------------------------------------------

  /** buildGeometry is the path the TestBuilder combiner takes. */
  public void testBuildGeometryTwoArcs() throws Exception {
    Geometry g = F.buildGeometry(Arrays.asList(
        readCurve("CIRCULARSTRING (0 0, 2 3, 10 0)"),
        readCurve("CIRCULARSTRING (20 0, 25 5, 30 0)")));
    assertEquals("MultiCurve", g.getGeometryType());
  }

  /**
   * Core's exact-class homogeneity check degraded this to GEOMETRYCOLLECTION;
   * both members are lineal, so the answer is a MultiCurve.
   */
  public void testBuildGeometryArcAndLine() throws Exception {
    Geometry g = F.buildGeometry(Arrays.asList(
        readCurve("CIRCULARSTRING (0 0, 2 3, 10 0)"),
        readCurve("LINESTRING (20 0, 30 0)")));
    assertEquals("MultiCurve", g.getGeometryType());
  }

  public void testBuildGeometryCurveAndPlainPolygons() throws Exception {
    Geometry g = F.buildGeometry(Arrays.asList(
        readCurve("CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))"),
        readCurve("POLYGON ((20 0, 30 0, 30 10, 20 10, 20 0))")));
    assertEquals("MultiSurface", g.getGeometryType());
  }

  /** Guard: a surface next to a bare curve has no multi type; GC is honest. */
  public void testBuildGeometryPolygonAndArcStaysCollection() throws Exception {
    Geometry g = F.buildGeometry(Arrays.asList(
        readCurve("CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))"),
        readCurve("CIRCULARSTRING (20 0, 25 5, 30 0)")));
    assertEquals("GeometryCollection", g.getGeometryType());
  }

  /** Guard: plain homogeneous input builds exactly what core always built. */
  public void testBuildGeometryPlainUnchanged() throws Exception {
    assertEquals("MultiLineString", F.buildGeometry(Arrays.asList(
        readCurve("LINESTRING (0 0, 10 0)"),
        readCurve("LINESTRING (20 0, 30 0)"))).getGeometryType());
    assertEquals("MultiPoint", F.buildGeometry(Arrays.asList(
        readCurve("POINT (0 0)"), readCurve("POINT (1 1)"))).getGeometryType());
    assertEquals("single geometry passes through", "LineString",
        F.buildGeometry(Arrays.asList(readCurve("LINESTRING (0 0, 10 0)")))
            .getGeometryType());
  }

  /** Guard: the WKT identity survives the round trip that visual QA watches. */
  public void testMultiCurveSurvivesWkt() throws Exception {
    Geometry mc = F.createMultiLineString(new LineString[] {
        line("CIRCULARSTRING (0 0, 2 3, 10 0)"),
        line("LINESTRING (20 0, 30 0)") });
    String wkt = new org.locationtech.jts.io.curve.CurveWKTWriter().write(mc);
    assertTrue("writes as MULTICURVE, got: " + wkt, wkt.startsWith("MULTICURVE"));
    assertTrue("and keeps the arc: " + wkt, wkt.contains("CIRCULARSTRING"));
  }
}
