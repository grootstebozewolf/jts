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
package org.locationtech.jts.geom.curved;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * FCP-LEN: the length of a CurvePolygon is the length of its arc rings.
 * <p>
 * Found by inspecting {@code CURVEPOLYGON (CIRCULARSTRING (-2 0, 0 2, 2 0,
 * 0 -2, -2 0))} in TestBuilder, which reports
 * {@code Len: 11.313708498984761  Area: 12.566370614359172}. The area is exact
 * -- it is the closest double to 4*pi -- but the length is 8*sqrt(2), the
 * perimeter of the inscribed square through the four control points. It should
 * also be 4*pi.
 * <p>
 * {@link CurvePolygon} overrides {@code getArea()} but not {@code getLength()},
 * so length falls through to {@code Polygon.getLength()}, which sums the flat
 * {@code getExteriorRing()} / {@code getInteriorRingN()} control-point view.
 * The pieces to do better already exist and are already arc-aware:
 * {@code CircularString.getLength()} and {@code CompoundCurve.getLength()} both
 * integrate the arc, and the same circle as a bare {@code CIRCULARSTRING}
 * reports 12.566370614359172 correctly.
 * <p>
 * <b>Why radius 2 is a trap.</b> At r=2 the circumference {@code 2*pi*r} and the
 * area {@code pi*r^2} are both {@code 4*pi}, so a reader comparing the two
 * numbers sees the correct one twice over and the wrong one not at all. Several
 * tests here therefore use r=3, where circumference (6*pi) and area (9*pi)
 * differ, so nothing can pass by coincidence.
 * <p>
 * The oracle is not a magic constant: {@code getBoundary()} is already arc-aware
 * (see {@code CurvePolygon.getBoundary()}), and by the OGC definition a
 * surface's length is the length of its boundary. So
 * {@code getLength() == getBoundary().getLength()} is the invariant, checked
 * directly in {@link #testLengthEqualsBoundaryLength()}.
 */
public class CurvePolygonLengthTest extends GeometryTestCase {

  private static final String CIRCLE_R2 =
      "CURVEPOLYGON (CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0))";

  private static final String CIRCLE_R3 =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";

  /** Shell r=10, hole r=3. */
  private static final String ANNULUS =
      "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0), "
      + "CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";

  /** Upper half of an r=2 circle, closed by a diameter chord. */
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-2 0, 0 2, 2 0), (2 0, -2 0)))";

  /** Arc lengths are closed-form, so the only error is floating-point. */
  private static final double TOL = 1.0e-9;

  public static void main(String[] args) { TestRunner.run(CurvePolygonLengthTest.class); }

  public CurvePolygonLengthTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurvedWKTReader().read(wkt);
  }

  /** The reported case: the length of a circle is its circumference. */
  public void testCircleLengthIsCircumference() throws Exception {
    assertEquals("r=2 circle perimeter should be 4*pi, not the inscribed square's "
        + (8 * Math.sqrt(2)), 4 * Math.PI, readCurve(CIRCLE_R2).getLength(), TOL);
  }

  /**
   * r=3, where circumference and area no longer coincide -- so this cannot pass
   * by picking up the area, and cannot pass on the control-point perimeter
   * (12*sqrt(2) = 16.97) either.
   */
  public void testRadiusThreeSeparatesLengthFromArea() throws Exception {
    Geometry circle = readCurve(CIRCLE_R3);
    assertEquals("r=3 circle perimeter should be 6*pi",
        6 * Math.PI, circle.getLength(), TOL);
    assertEquals("r=3 circle area should be 9*pi",
        9 * Math.PI, circle.getArea(), TOL);
  }

  /** The invariant: a surface's length is the length of its boundary. */
  public void testLengthEqualsBoundaryLength() throws Exception {
    for (String wkt : new String[] { CIRCLE_R2, CIRCLE_R3, ANNULUS, HALF_DISC }) {
      Geometry g = readCurve(wkt);
      assertEquals("length should equal boundary length for " + wkt,
          g.getBoundary().getLength(), g.getLength(), TOL);
    }
  }

  /** Holes contribute their own arc length, as they do for a linear polygon. */
  public void testAnnulusLengthSumsBothRings() throws Exception {
    assertEquals("annulus perimeter should be 20*pi + 6*pi",
        26 * Math.PI, readCurve(ANNULUS).getLength(), TOL);
  }

  /** A CompoundCurve ring mixes an arc and a chord; both must count. */
  public void testCompoundCurveRingLength() throws Exception {
    assertEquals("half-disc perimeter should be the 2*pi arc plus the 4-unit chord",
        2 * Math.PI + 4.0, readCurve(HALF_DISC).getLength(), TOL);
  }

  /** MultiSurface sums its members, so it inherits whatever CurvePolygon reports. */
  public void testMultiSurfaceLengthSumsMembers() throws Exception {
    assertEquals("MULTISURFACE of one r=2 circle should be 4*pi",
        4 * Math.PI, readCurve("MULTISURFACE (" + CIRCLE_R2 + ")").getLength(), TOL);
  }

  /** Guard: an all-linear CurvePolygon must stay bit-for-bit as it was. */
  public void testLinearPolygonLengthUnchanged() throws Exception {
    Geometry g = readCurve("CURVEPOLYGON ((0 0, 4 0, 4 3, 0 3, 0 0))");
    assertEquals("linear perimeter must be exactly 14", 14.0, g.getLength(), 0.0);
    assertEquals("linear area must be exactly 12", 12.0, g.getArea(), 0.0);
  }

  /** Guard: a linear polygon with a hole still sums both rings exactly. */
  public void testLinearPolygonWithHoleLengthUnchanged() throws Exception {
    Geometry g = readCurve("CURVEPOLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), "
        + "(2 2, 4 2, 4 4, 2 4, 2 2))");
    assertEquals("outer 40 plus hole 8", 48.0, g.getLength(), 0.0);
  }

  /** Guard: empty stays zero rather than throwing on a null shell. */
  public void testEmptyLengthIsZero() throws Exception {
    assertEquals(0.0, readCurve("CURVEPOLYGON EMPTY").getLength(), 0.0);
  }

  /** Guard: the bare ring was already arc-aware and must remain so. */
  public void testBareCircularStringUnchanged() throws Exception {
    assertEquals("CIRCULARSTRING length was already correct",
        4 * Math.PI, readCurve("CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0)").getLength(), TOL);
  }
}
