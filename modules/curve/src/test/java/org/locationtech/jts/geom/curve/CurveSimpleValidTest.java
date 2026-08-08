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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * CRV-SV: {@code isSimple()} and {@code isValid()} on a curve must judge the
 * arc, not the chords through its control points.
 * <p>
 * Both are inherited from jts-core, where {@code IsSimpleOp} and
 * {@code IsValidOp} node {@code getCoordinates()} -- the control polygon.
 * The error is two-sided. A segment can pierce an arc's bulge while staying
 * clear of every chord, so a self-crossing curve reports {@code simple}. And
 * the band between a chord and its arc lies inside the true region but
 * outside the flat control ring, so a hole there fails the flat
 * hole-outside-shell check and a genuinely valid CurvePolygon reports
 * {@code invalid}.
 * <p>
 * Fixtures use the R=5 semicircle about the origin (control points (-5 0),
 * (0 5), (5 0)). The band point set is {@code y > x + 5, x^2 + y^2 < 25}
 * (inside the arc, left of the chord); the piercing segment runs from
 * (-4, 3.2) (outside the circle: 26.24 > 25) to (-2.8, 3.7) (inside:
 * 21.53 < 25) with both endpoints on the band side of both chords, so it
 * crosses the arc exactly once at about (-3.75, 3.30) and crosses no chord.
 */
public class CurveSimpleValidTest extends GeometryTestCase {

  public static void main(String[] args) {
    TestRunner.run(CurveSimpleValidTest.class);
  }

  public CurveSimpleValidTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader().read(wkt);
  }

  /**
   * The straight tail loops around the outside of the semicircle (via
   * (7 8) and (-7 6), both legs clear of the circle and crossing no chord
   * within their spans) and its final segment (-4 3.2)-(-2.8 3.7) pierces
   * the arc's bulge. The control polyline is simple; the curve is not.
   */
  public void testCompoundCurveTailPiercingArcIsNotSimple() throws Exception {
    Geometry cc = readCurve(
        "COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), "
        + "(5 0, 7 8, -7 6, -4 3.2, -2.8 3.7))");
    assertTrue("a compound curve whose tail crosses its arc member is not simple",
        !cc.isSimple());
  }

  /** The same piercing segment as a detached MultiCurve member. */
  public void testMultiCurveMemberPiercingArcIsNotSimple() throws Exception {
    Geometry mc = readCurve(
        "MULTICURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (-4 3.2, -2.8 3.7))");
    assertTrue("a member crossing another member's arc makes the collection not simple",
        !mc.isSimple());
  }

  /**
   * A triangular hole in the bulge band: every vertex satisfies
   * {@code y > x + 5} (left of the chord, so outside the flat control
   * triangle) and {@code x^2 + y^2 < 25} (inside the arc, so inside the
   * true region). The CurvePolygon is valid; the flat check says the hole
   * lies outside the shell.
   */
  public void testCurvePolygonHoleInBulgeBandIsValid() throws Exception {
    Geometry cp = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), "
        + "(-3 2.5, -2.5 3, -3.3 2.9, -3 2.5))");
    assertTrue("a hole in the band between chord and arc is inside the region",
        cp.isValid());
  }

  /** Same contract with the polygon as a MultiSurface member. */
  public void testMultiSurfaceMemberHoleInBulgeBandIsValid() throws Exception {
    Geometry ms = readCurve(
        "MULTISURFACE (CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), "
        + "(-3 2.5, -2.5 3, -3.3 2.9, -3 2.5)))");
    assertTrue("member validity must be judged against the curved shell",
        ms.isValid());
  }

  /** A plain semicircle is simple -- the routing must not break the easy case. */
  public void testPlainArcIsSimple() throws Exception {
    Geometry arc = readCurve("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    assertTrue("a plain semicircular arc is simple", arc.isSimple());
  }

  /** A half-disk with no hole is valid -- the routing must not break the easy case. */
  public void testPlainHalfDiskIsValid() throws Exception {
    Geometry cp = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))");
    assertTrue("a plain half-disk is valid", cp.isValid());
  }
}
