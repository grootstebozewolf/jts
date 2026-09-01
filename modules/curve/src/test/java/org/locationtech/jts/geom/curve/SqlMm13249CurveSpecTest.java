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

import org.locationtech.jts.algorithm.exactcurve.ExactCircularArc;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTConstants;
import org.locationtech.jts.io.curve.CurveWKBReader;
import org.locationtech.jts.io.curve.CurveWKBWriter;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * ISO/IEC 13249-3 SQL/MM Spatial (Part 3: Spatial) pins for the five
 * curve types on {@code feature/zoo}: CircularString, CompoundCurve,
 * CurvePolygon, MultiCurve, MultiSurface.
 * <p>
 * Spec wins over PostGIS / SQL Server product quirks. Year-2
 * {@code Exact*} zoo types are not SQL/MM geometry types and are not
 * constructed here.
 */
public class SqlMm13249CurveSpecTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)";
  private static final String ARC_3 =
      "CIRCULARSTRING (0 0, 5 5, 10 0)";
  private static final String COLLINEAR =
      "CIRCULARSTRING (0 0, 1 0, 2 0)";
  private static final String ABA =
      "CIRCULARSTRING (0 0, 1 1, 0 0)";

  public static void main(String[] args) {
    TestRunner.run(suite());
  }

  public static Test suite() {
    return new TestSuite(SqlMm13249CurveSpecTest.class);
  }

  public SqlMm13249CurveSpecTest(String name) {
    super(name);
  }

  private static Geometry read(String wkt) throws Exception {
    return new CurveWKTReader().read(wkt);
  }

  private static String write(Geometry g) {
    return new CurveWKTWriter().write(g);
  }

  private static Geometry wkbRoundTrip(Geometry g) throws Exception {
    byte[] wkb = new CurveWKBWriter().write(g);
    return new CurveWKBReader(g.getFactory()).read(wkb);
  }

  // -- CircularString control count (ISO/IEC 13249-3) --------------------

  public void testEmptyCircularStringIsValid() throws Exception {
    Geometry g = read("CIRCULARSTRING EMPTY");
    assertTrue(g instanceof CircularString);
    assertTrue(g.isEmpty());
    assertTrue("ISO/IEC 13249-3 empty CircularString is valid", g.isValid());
    assertEquals(0, g.getNumPoints());
  }

  public void testOddThreeControlCircularStringIsValid() throws Exception {
    Geometry g = read(ARC_3);
    assertTrue(g instanceof CircularString);
    assertEquals(3, g.getNumPoints());
    assertTrue(g.isValid());
    assertTrue(CircularString.isValidControlCount(
        ((CircularString) g).getCoordinateSequence()));
  }

  public void testFiveControlCircleIsTheSqlMmFullCircle() throws Exception {
    Geometry g = read(CIRCLE_5);
    assertTrue(g instanceof CircularString);
    assertEquals("ISO/IEC 13249-3 complete circle is two arcs (5 controls)",
        5, g.getNumPoints());
    assertTrue(((LineString) g).isClosed());
    assertTrue(g.isValid());
    assertEquals(10.0 * Math.PI, g.getLength(), 1.0e-9);
    String emitted = write(g);
    assertTrue(emitted.toUpperCase().contains(WKTConstants.CIRCULARSTRING));
    assertEquals(5, read(emitted).getNumPoints());
  }

  public void testFourControlClosedCircleIsRejected() throws Exception {
    try {
      read("CIRCULARSTRING (-5 0, 0 5, 5 0, -5 0)");
      fail("ISO/IEC 13249-3: CIRCULARSTRING(A,B,C,A) is even and must not parse");
    } catch (ParseException e) {
      assertTrue(e.getMessage(), e.getMessage().indexOf("13249-3") >= 0);
    }
    CurveGeometryFactory gf = new CurveGeometryFactory();
    CircularString four = new CircularString(
        gf.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(-5, 0), new Coordinate(0, 5),
            new Coordinate(5, 0), new Coordinate(-5, 0)
        }), gf);
    assertEquals("do not invent a fifth control on the stored sequence",
        4, four.getNumPoints());
    assertFalse("4-control CIRCULARSTRING(A,B,C,A) is not a SQL/MM CircularString",
        four.isValid());
    assertFalse(CircularString.isValidControlCount(four.getCoordinateSequence()));
    assertEquals("stored arcs only; complementary close is not invented",
        ExactCircularArc.length(new Coordinate(-5, 0), new Coordinate(0, 5),
            new Coordinate(5, 0)),
        four.getLength(), 1.0e-12);
  }

  public void testCircularStringAbaIsNotAFullCircle() throws Exception {
    Geometry g = read(ABA);
    assertTrue(g instanceof CircularString);
    assertEquals(3, g.getNumPoints());
    assertTrue(((LineString) g).isClosed());
    assertEquals("CIRCULARSTRING(A,B,A) is a degenerate window, not 2πr",
        0.0, g.getLength(), 0.0);
    String emitted = write(g);
    assertEquals(3, read(emitted).getNumPoints());
    assertFalse("must not rewrite ABA as a 5-control circle",
        emitted.replaceAll("\\s+", "").matches("(?i).*0 0,1 1,0 0,.*0 0.*"));
  }

  public void testEvenOpenControlCountIsRejected() throws Exception {
    try {
      read("CIRCULARSTRING (0 0, 1 1, 2 0, 3 1)");
      fail("even leftover CircularString must not parse");
    } catch (ParseException e) {
      assertTrue(e.getMessage(), e.getMessage().indexOf("13249-3") >= 0);
    }
  }

  public void testTwoPointCircularStringIsRejected() throws Exception {
    try {
      read("CIRCULARSTRING (0 0, 1 1)");
      fail("n=2 is not n>1 odd");
    } catch (ParseException e) {
      // expected
    }
  }

  public void testCollinearTripleIsExactChordNotFakeArc() throws Exception {
    Geometry g = read(COLLINEAR);
    assertTrue(g instanceof CircularString);
    assertTrue(g.isValid());
    assertEquals("collinear 3-controls are the chord length",
        2.0, g.getLength(), 0.0);
    ExactCircularArc chord = new ExactCircularArc(
        new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(2, 0));
    assertFalse("colinear triple is an exact chord, not a circular arc",
        chord.isArc());
    assertTrue(chord.isExact());
    Geometry lin = ((Linearizable) g).toLinear(0.01);
    assertEquals("LineString", lin.getGeometryType());
    assertEquals(2, lin.getNumPoints());
    assertEquals(0.0, lin.getCoordinates()[0].x, 0.0);
    assertEquals(2.0, lin.getCoordinates()[1].x, 0.0);
  }

  // -- CompoundCurve -----------------------------------------------------

  public void testCompoundCurveContiguousMembersAreValid() throws Exception {
    Geometry g = read(
        "COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0))");
    assertTrue(g instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) g;
    assertEquals(2, cc.getNumMembers());
    assertTrue(cc.getMemberN(0).getClass() == LineString.class);
    assertTrue(cc.getMemberN(1) instanceof CircularString);
    assertTrue(CompoundCurve.areMembersContiguous(cc.getMembers()));
    assertTrue(g.isValid());
  }

  public void testCompoundCurveRejectsDisconnectedMembers() throws Exception {
    try {
      read("COMPOUNDCURVE ((0 0, 1 1), (2 2, 3 3))");
      fail("ISO/IEC 13249-3 CompoundCurve must be contiguous");
    } catch (ParseException e) {
      assertTrue(e.getMessage(), e.getMessage().indexOf("contiguous") >= 0);
    }
    CurveGeometryFactory gf = new CurveGeometryFactory();
    CompoundCurve gap = new CompoundCurve(new LineString[] {
        gf.createLineString(new Coordinate[] {
            new Coordinate(0, 0), new Coordinate(1, 1) }),
        gf.createLineString(new Coordinate[] {
            new Coordinate(2, 2), new Coordinate(3, 3) })
    }, gf);
    assertFalse(gap.isValid());
    assertFalse(CompoundCurve.areMembersContiguous(gap.getMembers()));
  }

  public void testCompoundCurveRejectsNestedCompoundCurve() throws Exception {
    try {
      read("COMPOUNDCURVE (COMPOUNDCURVE ((0 0, 1 0)), (1 0, 2 0))");
      fail("nested CompoundCurve is not a SQL/MM SimpleCurve member");
    } catch (ParseException e) {
      assertTrue(e.getMessage(), e.getMessage().indexOf("SimpleCurve") >= 0
          || e.getMessage().indexOf("13249-3") >= 0);
    }
  }

  public void testEmptyCompoundCurveIsValid() throws Exception {
    Geometry g = read("COMPOUNDCURVE EMPTY");
    assertTrue(g instanceof CompoundCurve);
    assertTrue(g.isEmpty());
    assertTrue(g.isValid());
  }

  // -- CurvePolygon rings ------------------------------------------------

  public void testCurvePolygonAcceptsLineCircularAndCompoundRings()
      throws Exception {
    Geometry g = read(
        "CURVEPOLYGON (CIRCULARSTRING (0 0, 4 0, 4 4, 0 4, 0 0), "
            + "(1 1, 3 1, 3 3, 1 3, 1 1))");
    assertTrue(g instanceof CurvePolygon);
    CurvePolygon cp = (CurvePolygon) g;
    assertTrue(CurvePolygon.isSqlMmRing(cp.getExteriorCurve()));
    assertTrue(CurvePolygon.isSqlMmRing(cp.getInteriorCurveN(0)));
    assertTrue(g.isValid());

    Geometry compoundRing = read(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 0, 1 1, 2 0), (2 0, 0 0)))");
    assertTrue(compoundRing instanceof CurvePolygon);
    assertTrue(((CurvePolygon) compoundRing).getExteriorCurve() instanceof CompoundCurve);
    assertTrue(compoundRing.isValid());
  }

  public void testCurvePolygonRejectsUnclosedRing() throws Exception {
    try {
      read("CURVEPOLYGON ((0 0, 1 0, 1 1, 0 1))");
      fail("ISO/IEC 13249-3 CurvePolygon ring must be closed");
    } catch (Throwable e) {
      // ParseException or LinearRing construction
    }
    try {
      read("CURVEPOLYGON (CIRCULARSTRING (0 0, 4 0, 4 4))");
      fail("open CircularString is not a closed CurvePolygon ring");
    } catch (ParseException e) {
      assertTrue(e.getMessage(), e.getMessage().indexOf("closed") >= 0
          || e.getMessage().indexOf("13249-3") >= 0);
    }
  }

  public void testEmptyCurvePolygonIsValid() throws Exception {
    Geometry g = read("CURVEPOLYGON EMPTY");
    assertTrue(g instanceof CurvePolygon);
    assertTrue(g.isEmpty());
    assertTrue(g.isValid());
  }

  // -- MultiCurve / MultiSurface ----------------------------------------

  public void testMultiCurveHoldsLineCircularAndCompound() throws Exception {
    Geometry g = read(
        "MULTICURVE ((0 0, 1 1), CIRCULARSTRING (0 0, 1 1, 2 0), "
            + "COMPOUNDCURVE ((2 0, 3 0), CIRCULARSTRING (3 0, 4 1, 5 0)))");
    assertTrue(g instanceof MultiCurve);
    assertEquals(3, g.getNumGeometries());
    assertTrue(g.getGeometryN(0) instanceof LineString);
    assertTrue(g.getGeometryN(1) instanceof CircularString);
    assertTrue(g.getGeometryN(2) instanceof CompoundCurve);
    assertTrue(g.isValid());
  }

  public void testNoMultiCircularStringOrMultiCompoundStringType()
      throws Exception {
    try {
      read("MULTICIRCULARSTRING ((0 0, 1 1, 2 0))");
      fail("ISO/IEC 13249-3 has no MultiCircularString");
    } catch (ParseException e) {
      // unknown type
    }
    try {
      read("MULTICOMPOUNDCURVE (((0 0, 1 0)))");
      fail("ISO/IEC 13249-3 has no MultiCompoundString");
    } catch (ParseException e) {
      // unknown type
    }
  }

  public void testMultiSurfaceHoldsPolygonAndCurvePolygon() throws Exception {
    Geometry g = read(
        "MULTISURFACE (((10 10, 12 10, 12 12, 10 12, 10 10)), "
            + "CURVEPOLYGON (CIRCULARSTRING (0 0, 4 0, 4 4, 0 4, 0 0)))");
    assertTrue(g instanceof MultiSurface);
    assertEquals(2, g.getNumGeometries());
    assertEquals("Polygon", g.getGeometryN(0).getGeometryType());
    assertTrue(g.getGeometryN(1) instanceof CurvePolygon);
    assertTrue(g.isValid());
  }

  public void testEmptyMultiCurveAndMultiSurfaceAreValid() throws Exception {
    Geometry mc = read("MULTICURVE EMPTY");
    Geometry ms = read("MULTISURFACE EMPTY");
    assertTrue(mc instanceof MultiCurve);
    assertTrue(ms instanceof MultiSurface);
    assertTrue(mc.isEmpty());
    assertTrue(ms.isEmpty());
    assertTrue(mc.isValid());
    assertTrue(ms.isValid());
  }

  // -- Z / M / ZM and type-preserving I/O --------------------------------

  public void testCircularStringZmRoundTripKeepsTypeAndOrdinates()
      throws Exception {
    Geometry g = read("CIRCULARSTRING ZM (1 2 3 4, 5 6 7 8, 9 10 11 12)");
    assertTrue(g instanceof CircularString);
    assertEquals(3.0, g.getCoordinates()[0].getZ(), 0.0);
    assertEquals(4.0, g.getCoordinates()[0].getM(), 0.0);
    String wkt = new CurveWKTWriter(4).write(g);
    assertTrue(wkt.toUpperCase().contains("CIRCULARSTRING"));
    assertTrue(wkt.toUpperCase().contains("ZM"));
    Geometry backWkt = read(wkt);
    assertTrue(backWkt instanceof CircularString);
    Geometry backWkb = wkbRoundTrip(g);
    assertTrue(backWkb instanceof CircularString);
    assertEquals(3.0, backWkb.getCoordinates()[0].getZ(), 0.0);
    assertEquals(4.0, backWkb.getCoordinates()[0].getM(), 0.0);
  }

  public void testWktWkbRoundTripKeepsSqlMmTypeKeywords() throws Exception {
    String[] wkts = {
        ARC_3,
        "COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0))",
        "CURVEPOLYGON (CIRCULARSTRING (0 0, 4 0, 4 4, 0 4, 0 0))",
        "MULTICURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (1 1, 2 2))",
        "MULTISURFACE (CURVEPOLYGON (CIRCULARSTRING (0 0, 4 0, 4 4, 0 4, 0 0)))",
        "CIRCULARSTRING EMPTY",
        "COMPOUNDCURVE EMPTY",
        "CURVEPOLYGON EMPTY",
        "MULTICURVE EMPTY",
        "MULTISURFACE EMPTY"
    };
    String[] types = {
        "CircularString", "CompoundCurve", "CurvePolygon",
        "MultiCurve", "MultiSurface",
        "CircularString", "CompoundCurve", "CurvePolygon",
        "MultiCurve", "MultiSurface"
    };
    for (int i = 0; i < wkts.length; i++) {
      Geometry g = read(wkts[i]);
      assertEquals(types[i], g.getGeometryType());
      String emitted = write(g);
      assertTrue(emitted + " lost " + types[i],
          emitted.toUpperCase().contains(types[i].toUpperCase()));
      assertFalse(emitted + " flattened to LINESTRING",
          types[i].equals("CircularString")
              && emitted.toUpperCase().startsWith("LINESTRING"));
      assertFalse(emitted + " flattened to POLYGON",
          types[i].equals("CurvePolygon")
              && emitted.toUpperCase().startsWith("POLYGON"));
      Geometry g2 = read(emitted);
      assertEquals(types[i], g2.getGeometryType());
      Geometry wkb = wkbRoundTrip(g);
      assertEquals(types[i], wkb.getGeometryType());
    }
  }

  public void testWkbRejectsFourControlCircularString() throws Exception {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    CircularString four = new CircularString(
        gf.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(0, 0), new Coordinate(1, 1),
            new Coordinate(2, 0), new Coordinate(0, 0)
        }), gf);
    byte[] wkb = new CurveWKBWriter().write(four);
    try {
      new CurveWKBReader(gf).read(wkb);
      fail("WKB of 4-control CircularString must not parse as SQL/MM");
    } catch (ParseException e) {
      assertTrue(e.getMessage(), e.getMessage().indexOf("13249-3") >= 0);
    }
  }

  public void testLinearizationIsToLinearOnly() throws Exception {
    Geometry g = read(ARC_3);
    Geometry lin = ((Linearizable) g).toLinear(0.1);
    assertEquals("LineString", lin.getGeometryType());
    assertTrue(lin.getNumPoints() >= 2);
    String stillCurve = write(g);
    assertTrue(stillCurve.toUpperCase().contains("CIRCULARSTRING"));
  }

  public void testNoYear2ExactZooTypesOnThisWorkstream() {
    assertNull(classOrNull("org.locationtech.jts.algorithm.exactcurve.ExactEllipticalArc"));
    assertNull(classOrNull("org.locationtech.jts.algorithm.exactcurve.ExactCubicBezier"));
    assertNull(classOrNull("org.locationtech.jts.algorithm.exactcurve.ExactClothoid"));
    assertNull(classOrNull("org.locationtech.jts.algorithm.exactcurve.ExactNurbsSegment"));
  }

  private static Class<?> classOrNull(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }
}
