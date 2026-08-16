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
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Laser convex hull: a disc, a single arc, and circular-plus-straight
 * members (H-CC, a stadium) return a {@link CurvePolygon} whose shell
 * keeps the exposed arcs. A clothoid or an all-straight CompoundCurve
 * is a named null -- {@link CurveOps} takes the chords, and that path
 * is never flagged exact.
 */
public class CurveExactHullTest extends GeometryTestCase {

  private static final String DISC_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String HALF_ARC =
      "CIRCULARSTRING (-10 0, 0 10, 10 0)";
  /** Issue #6 / H-CC visual pin. */
  private static final String H_CC =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 10 10))";
  private static final String STADIUM =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 1, -2 2, -1 3), (-1 3, 1 3), CIRCULARSTRING (1 3, 2 2, 1 1), (1 1, -1 1)))";
  private static final String TWO_CAP =
      "MULTICURVE (CIRCULARSTRING (-1 1, -2 2, -1 3), CIRCULARSTRING (1 3, 2 2, 1 1))";
  private static final String STRAIGHT =
      "COMPOUNDCURVE ((0 0, 10 0), (10 0, 10 10), (10 10, 0 0))";
  private static final String CLOTHOID =
      "COMPOUNDCURVE ((0 0, 100 0), CLOTHOID (0, 0.005, 80))";

  /** Control-point hull of H-CC: POLYGON ((0 0, 5 5, 10 10, 10 0, 0 0)). */
  private static final double CONTROL_POINT_AREA = 50.0;
  /**
   * Exact H-CC hull: trapezoid (0,0)-(10,0)-(10,10)-(2,4) plus the
   * circular segment from (2,4) to (0,0) on the r=5 circle.
   */
  private static final double H_CC_AREA = 50.0 + 12.5 * Math.acos(0.6);

  public static void main(String[] args) {
    TestRunner.run(CurveExactHullTest.class);
  }

  public CurveExactHullTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testDiscHullIsTheDisc() throws Exception {
    Geometry disc = readCurve(DISC_5);
    Geometry exact = CurveExact.convexHull(disc);
    assertNotNull("disc has a closed form", exact);
    assertTrue(exact instanceof CurvePolygon);
    assertEquals(25.0 * Math.PI, exact.getArea(), 1.0e-9);
    assertEquals(25.0 * Math.PI, disc.convexHull().getArea(), 1.0e-9);
    assertFalse("laser hull is not a densified POLYGON",
        exact.getClass() == Polygon.class);
  }

  public void testSingleArcHullKeepsTheArc() throws Exception {
    Geometry arc = readCurve(HALF_ARC);
    Geometry exact = CurveExact.convexHull(arc);
    assertNotNull(exact);
    assertTrue(exact instanceof CurvePolygon);
    assertEquals(50.0 * Math.PI, exact.getArea(), 1.0e-9);
    assertTrue("half-disc shell keeps a CircularString",
        hasCircularMember(exact));
  }

  /**
   * Issue #6 pin: the hull of
   * {@code COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 10 10))}
   * is a CurvePolygon that keeps the exposed left arc, not the area-50
   * control-point polygon and not a densified POLYGON.
   */
  public void testHccHullKeepsTheBulgeAsAnArc() throws Exception {
    Geometry g = readCurve(H_CC);
    Geometry exact = CurveExact.convexHull(g);
    assertNotNull("H-CC has a closed form", exact);
    assertTrue("H-CC hull is a CurvePolygon, not a densified POLYGON",
        exact instanceof CurvePolygon);
    assertTrue("exposed arc stays a CircularString", hasCircularMember(exact));
    assertEquals(H_CC_AREA, exact.getArea(), 1.0e-9);
    assertTrue("area must beat the control-point 50",
        exact.getArea() > CONTROL_POINT_AREA + 10.0);

    Geometry bulge = getGeometryFactory().createPoint(new Coordinate(
        5.0 + 5.0 * Math.cos(Math.toRadians(135.0)),
        5.0 * Math.sin(Math.toRadians(135.0))));
    assertEquals("bulge sits on the laser arc, not a chord",
        0.0, exact.distance(bulge), 1.0e-9);
    assertTrue("the bulge is on an arc member, not a straight chord",
        bulgeOnCircularMember(exact, bulge.getCoordinate()));

    Geometry viaOps = g.convexHull();
    assertTrue(viaOps instanceof CurvePolygon);
    assertEquals(H_CC_AREA, viaOps.getArea(), 1.0e-9);
  }

  public void testStadiumHullIsTheStadium() throws Exception {
    Geometry stadium = readCurve(STADIUM);
    Geometry exact = CurveExact.convexHull(stadium);
    assertNotNull("stadium has a closed form", exact);
    assertTrue(exact instanceof CurvePolygon);
    assertEquals(4.0 + Math.PI, exact.getArea(), 1.0e-9);
    assertTrue("stadium keeps both caps", circularMemberCount(exact) >= 2);
    assertEquals(4.0 + Math.PI, stadium.convexHull().getArea(), 1.0e-9);
  }

  public void testTwoCapMultiCurveHullIsTheStadium() throws Exception {
    Geometry caps = readCurve(TWO_CAP);
    Geometry exact = CurveExact.convexHull(caps);
    assertNotNull("two caps have a closed form", exact);
    assertTrue(exact instanceof CurvePolygon);
    assertEquals(4.0 + Math.PI, exact.getArea(), 1.0e-9);
    assertTrue(hasCircularMember(exact));
  }

  public void testStraightCompoundCurveFallsThrough() throws Exception {
    Geometry g = readCurve(STRAIGHT);
    assertNull("all-straight is a named null; core hull is exact",
        CurveExact.convexHull(g));
    Geometry hull = g.convexHull();
    assertFalse(hull instanceof CurvePolygon);
    assertEquals(50.0, hull.getArea(), 1.0e-9);
  }

  public void testClothoidMemberFallsThrough() throws Exception {
    Geometry g = readCurve(CLOTHOID);
    assertNull("clothoid mix is a named null, not densify-flagged-exact",
        CurveExact.convexHull(g));
    Geometry hull = g.convexHull();
    assertFalse("fallback is a linear hull, not a CurvePolygon flagged exact",
        hull instanceof CurvePolygon);
  }

  private static boolean hasCircularMember(Geometry hull) {
    return circularMemberCount(hull) > 0;
  }

  private static int circularMemberCount(Geometry hull) {
    if (!(hull instanceof CurvePolygon)) return 0;
    LineString shell = ((CurvePolygon) hull).getExteriorCurve();
    if (shell instanceof CircularString) return 1;
    if (!(shell instanceof CompoundCurve)) return 0;
    CompoundCurve cc = (CompoundCurve) shell;
    int n = 0;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      if (cc.getMemberN(i) instanceof CircularString) n++;
    }
    return n;
  }

  private static boolean bulgeOnCircularMember(Geometry hull, Coordinate p) {
    if (!(hull instanceof CurvePolygon)) return false;
    LineString shell = ((CurvePolygon) hull).getExteriorCurve();
    if (shell instanceof CircularString) {
      return pointOnCircular((CircularString) shell, p);
    }
    if (!(shell instanceof CompoundCurve)) return false;
    CompoundCurve cc = (CompoundCurve) shell;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      if (m instanceof CircularString && pointOnCircular((CircularString) m, p)) {
        return true;
      }
    }
    return false;
  }

  private static boolean pointOnCircular(CircularString cs, Coordinate p) {
    if (cs.getNumPoints() < 3) return false;
    CircularArcDensifier.Circle c = CircularArcDensifier.Circle.fromThreePoints(
        cs.getCoordinateN(0), cs.getCoordinateN(1), cs.getCoordinateN(2));
    if (c == null) return false;
    return Math.abs(p.distance(new Coordinate(c.cx, c.cy)) - c.r) < 1.0e-8;
  }
}
