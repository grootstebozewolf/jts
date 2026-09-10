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
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * V-CS / #86: {@code CIRCULARSTRING(A,B,C,A)} is a valid geometry.
 * A pair of those rings is a valid annulus (CurvePolygon hole).
 * Not #114 paint. Not H-CC area.
 */
public class CircularStringValidTest extends GeometryTestCase {

  /** #87 three-click close: circumcircle, no complementary mid stored. */
  private static final String RING_5 =
      "CIRCULARSTRING (-5 0, 0 5, 5 0, -5 0)";
  private static final String RING_3 =
      "CIRCULARSTRING (-3 0, 0 3, 3 0, -3 0)";
  private static final String ANNULUS =
      "CURVEPOLYGON (" + RING_5 + ", " + RING_3 + ")";
  private static final String ODD_CLOSED =
      "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)";

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CircularStringValidTest.class); }
  public CircularStringValidTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader().read(wkt);
  }

  public void testClosedFourPointCircularStringIsValid() throws Exception {
    Geometry g = readCurve(RING_5);
    assertTrue(g instanceof CircularString);
    assertEquals(4, g.getNumPoints());
    assertTrue(((LineString) g).isClosed());
    assertTrue("CIRCULARSTRING(A,B,C,A) is a valid geometry", g.isValid());
    assertTrue(CircularString.isValidControlCount(
        ((CircularString) g).getCoordinateSequence()));
  }

  public void testOpenFourPointControlIsInvalid() {
    GeometryFactory gf = new CurveGeometryFactory();
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
    Geometry g = readCurve(ODD_CLOSED);
    assertTrue(g.isValid());
  }

  /** Two concentric CIRCULARSTRING(A,B,C,A) rings: valid annulus. */
  public void testFourPointAnnulusCurvePolygonIsValid() throws Exception {
    Geometry g = readCurve(ANNULUS);
    assertTrue(g instanceof CurvePolygon);
    CurvePolygon cp = (CurvePolygon) g;
    assertEquals(1, cp.getNumInteriorRing());
    assertTrue(cp.getExteriorCurve() instanceof CircularString);
    assertTrue(cp.getInteriorCurveN(0) instanceof CircularString);
    assertEquals(4, cp.getExteriorCurve().getNumPoints());
    assertEquals(4, cp.getInteriorCurveN(0).getNumPoints());
    assertTrue("4-pt + 4-pt CurvePolygon annulus is a valid geometry", g.isValid());
  }
}
