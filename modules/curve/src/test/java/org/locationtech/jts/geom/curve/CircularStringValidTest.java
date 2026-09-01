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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * ISO/IEC 13249-3 CircularString validity. Empty or odd control count
 * {@code n > 1}. A complete circle is five controls. Four-control
 * {@code CIRCULARSTRING(A,B,C,A)} is not a SQL/MM CircularString.
 */
public class CircularStringValidTest extends GeometryTestCase {

  private static final String RING_5 =
      "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)";
  private static final String RING_3 =
      "CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0)";
  private static final String ANNULUS =
      "CURVEPOLYGON (" + RING_5 + ", " + RING_3 + ")";

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CircularStringValidTest.class); }
  public CircularStringValidTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader().read(wkt);
  }

  public void testFiveControlCircleIsValid() throws Exception {
    Geometry g = readCurve(RING_5);
    assertTrue(g instanceof CircularString);
    assertEquals(5, g.getNumPoints());
    assertTrue(((LineString) g).isClosed());
    assertTrue("ISO/IEC 13249-3 complete circle is 5 controls", g.isValid());
  }

  public void testFourPointClosedCircleIsNotSqlMmValid() {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    Coordinate[] pts = {
        new Coordinate(-5, 0),
        new Coordinate(0, 5),
        new Coordinate(5, 0),
        new Coordinate(-5, 0)
    };
    CircularString closedEven = new CircularString(
        gf.getCoordinateSequenceFactory().create(pts), gf);
    assertEquals(4, closedEven.getNumPoints());
    assertTrue(closedEven.isClosed());
    assertFalse("CIRCULARSTRING(A,B,C,A) is even; not ISO/IEC 13249-3",
        closedEven.isValid());
    assertFalse(CircularString.isValidControlCount(closedEven.getCoordinateSequence()));
  }

  public void testFourPointWktIsRejected() throws Exception {
    try {
      readCurve("CIRCULARSTRING (-5 0, 0 5, 5 0, -5 0)");
      fail("4-control CIRCULARSTRING must not parse");
    } catch (ParseException e) {
      assertTrue(e.getMessage(), e.getMessage().indexOf("13249-3") >= 0);
    }
  }

  public void testOpenFourPointControlIsInvalid() {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    Coordinate[] pts = {
        new Coordinate(0, 0),
        new Coordinate(1, 1),
        new Coordinate(2, 0),
        new Coordinate(3, 1)
    };
    CircularString open = new CircularString(gf.getCoordinateSequenceFactory().create(pts), gf);
    assertFalse("open even leftover is not a valid CircularString", open.isValid());
    assertFalse(CircularString.isValidControlCount(open.getCoordinateSequence()));
  }

  public void testOddClosedCircularStringStillValid() throws Exception {
    Geometry g = readCurve(RING_5);
    assertTrue(g.isValid());
  }

  /** Two concentric 5-control rings: valid SQL/MM annulus. */
  public void testFivePointAnnulusCurvePolygonIsValid() throws Exception {
    Geometry g = readCurve(ANNULUS);
    assertTrue(g instanceof CurvePolygon);
    CurvePolygon cp = (CurvePolygon) g;
    assertEquals(1, cp.getNumInteriorRing());
    assertTrue(cp.getExteriorCurve() instanceof CircularString);
    assertTrue(cp.getInteriorCurveN(0) instanceof CircularString);
    assertEquals(5, cp.getExteriorCurve().getNumPoints());
    assertEquals(5, cp.getInteriorCurveN(0).getNumPoints());
    assertTrue("5-pt + 5-pt CurvePolygon annulus is a valid geometry", g.isValid());
  }
}
