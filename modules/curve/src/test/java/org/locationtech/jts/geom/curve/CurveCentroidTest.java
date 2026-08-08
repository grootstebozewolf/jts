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
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * CRV-CTR: {@code getCentroid()} and {@code getInteriorPoint()} on a curve
 * must weigh the arc, not the chords through its control points.
 * <p>
 * The curve types inherit both from jts-core, where {@code Centroid} and
 * {@code InteriorPoint} walk {@code getCoordinates()} -- the control polygon.
 * For the centroid that mis-weights every arc (a half-arc's centroid sits at
 * {@code 2R/pi} above the centre, its control triangle's at {@code R/2}); for
 * the interior point it is worse than imprecise: {@code InteriorPointArea}
 * scanning the flat control ring of a thin crescent can return a point that
 * is not in the curved region at all, violating the method's one contract.
 * <p>
 * Expected values are the analytical arc answers; the assertion tolerance
 * (1e-3) leaves room for a tolerance-bounded implementation while staying far
 * below the chord-vs-arc error (order 0.5 in these fixtures).
 */
public class CurveCentroidTest extends GeometryTestCase {

  private static final double TOL = 1.0e-3;

  public static void main(String[] args) {
    TestRunner.run(CurveCentroidTest.class);
  }

  public CurveCentroidTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader().read(wkt);
  }

  /**
   * Half-arc of radius 5 about the origin. The arc centroid is at
   * {@code (0, 2R/pi) ~= (0, 3.183)}; the control-polygon centroid is at
   * {@code (0, 2.5)}.
   */
  public void testCircularStringHalfArcCentroid() throws Exception {
    Geometry arc = readCurve("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    Point c = arc.getCentroid();
    assertEquals("half-arc centroid x", 0.0, c.getX(), TOL);
    assertEquals("half-arc centroid y is 2R/pi",
        2.0 * 5.0 / Math.PI, c.getY(), TOL);
  }

  /**
   * Straight run of 10 plus a half-arc of radius 5: members weighted by true
   * length (10 and 5*pi), arc centroid at (15, 10/pi). Expected combined
   * centroid ((50 + 75*pi)/(10 + 5*pi), 50/(10 + 5*pi)) ~= (11.110, 1.945);
   * the chord walk yields ~(10.858, 1.464).
   */
  public void testCompoundCurveCentroidWeightsMembersByArcLength() throws Exception {
    Geometry cc = readCurve(
        "COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0))");
    Point c = cc.getCentroid();
    double total = 10.0 + 5.0 * Math.PI;
    assertEquals("compound centroid x", (50.0 + 75.0 * Math.PI) / total, c.getX(), TOL);
    assertEquals("compound centroid y", 50.0 / total, c.getY(), TOL);
  }

  /**
   * Half-disk of radius 5 (arc over the top, chord along the x-axis). The
   * region centroid is at {@code (0, 4R/(3*pi)) ~= (0, 2.122)}; the control
   * triangle's centroid is at {@code (0, 5/3)}.
   */
  public void testCurvePolygonHalfDiskCentroid() throws Exception {
    Geometry halfDisk = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))");
    Point c = halfDisk.getCentroid();
    assertEquals("half-disk centroid x", 0.0, c.getX(), TOL);
    assertEquals("half-disk centroid y is 4R/(3*pi)",
        4.0 * 5.0 / (3.0 * Math.PI), c.getY(), TOL);
  }

  /** A MultiCurve member's arc weighs into the collection centroid. */
  public void testMultiCurveCentroidSeesTheArc() throws Exception {
    Geometry mc = readCurve("MULTICURVE (CIRCULARSTRING (-5 0, 0 5, 5 0))");
    Point c = mc.getCentroid();
    assertEquals("multicurve centroid x", 0.0, c.getX(), TOL);
    assertEquals("multicurve centroid y is 2R/pi",
        2.0 * 5.0 / Math.PI, c.getY(), TOL);
  }

  /** A MultiSurface member's curved region weighs into the collection centroid. */
  public void testMultiSurfaceCentroidSeesTheArc() throws Exception {
    Geometry ms = readCurve(
        "MULTISURFACE (CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0))))");
    Point c = ms.getCentroid();
    assertEquals("multisurface centroid x", 0.0, c.getX(), TOL);
    assertEquals("multisurface centroid y is 4R/(3*pi)",
        4.0 * 5.0 / (3.0 * Math.PI), c.getY(), TOL);
  }

  /**
   * Thin crescent between an outer arc (centre (0,0), R=5) and an inner arc
   * (centre (0,-1.125), R=5.125). The interior point must lie in the curved
   * region: inside the outer circle, outside the inner. Scanning the flat
   * control quad puts it at ~(2.19, 2.5), which is 0.89 deep inside the
   * inner circle -- outside the crescent.
   */
  public void testInteriorPointOfCrescentLiesInCurvedRegion() throws Exception {
    Geometry crescent = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), CIRCULARSTRING (5 0, 0 4, -5 0)))");
    checkInteriorPointInCrescent(crescent);
  }

  /** Same contract for the crescent as a MultiSurface member. */
  public void testInteriorPointOfMultiSurfaceCrescentLiesInCurvedRegion() throws Exception {
    Geometry ms = readCurve(
        "MULTISURFACE (CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), CIRCULARSTRING (5 0, 0 4, -5 0))))");
    checkInteriorPointInCrescent(ms);
  }

  /** The crescent region analytically: inside the outer circle, outside the inner. */
  private static void checkInteriorPointInCrescent(Geometry crescent) {
    Coordinate ip = crescent.getInteriorPoint().getCoordinate();
    assertTrue("interior point " + ip + " must lie inside the outer arc (R=5)",
        ip.distance(new Coordinate(0, 0)) <= 5.0 + TOL);
    assertTrue("interior point " + ip + " must lie outside the inner arc "
        + "(centre (0,-1.125), R=5.125)",
        ip.distance(new Coordinate(0, -1.125)) >= 5.125 - TOL);
  }

  /** Empty curves keep the inherited empty-result behaviour. */
  public void testEmptyCurveCentroidIsEmpty() throws Exception {
    Geometry empty = readCurve("CIRCULARSTRING EMPTY");
    assertTrue("centroid of an empty curve is empty",
        empty.getCentroid().isEmpty());
  }
}
