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
package org.locationtech.jts.awt.curved;

import java.awt.Shape;
import java.awt.geom.PathIterator;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * FCP-H-WIND: an arc hole must render as a hole whichever way its ring winds.
 * <p>
 * Reported as issue #4 from the visual-QA sweep. For
 * {@code CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0),
 * CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))} the canvas shows two overlaid
 * filled circles instead of an annulus.
 * <p>
 * This is <em>not</em> a defect in the arc rendering, and not a gap in the
 * structural accessors FCP-H added: both rings are emitted, each as its own
 * closed bezier subpath, and {@code getInteriorCurveN} returns the arc. The
 * defect is the winding rule. {@link CurvedShapeWriter} builds its polygon paths
 * with {@code new GeneralPath()}, which defaults to
 * {@link PathIterator#WIND_NON_ZERO}, and under non-zero winding a hole only
 * cancels the shell when the two rings wind in <em>opposite</em> directions.
 * Both rings here run left-top-right-bottom, so the winding number inside the
 * inner circle is 2 rather than 0 and the hole fills in.
 * <p>
 * Every equivalent path in jts-core is orientation-independent by construction
 * and says so: {@code PolygonShape} builds with {@code WIND_EVEN_ODD}, and
 * {@code ShapeCollectionPathIterator.getWindingRule()} carries the comment
 * "WIND_NON_ZERO requires that the ring orientation be correct. So use
 * WIND_EVEN_ODD." Nothing normalises ring orientation on the way in -- WKT does
 * not constrain it and {@code CurvedWKTReader} preserves what it is given -- so
 * the curved writer must match core rather than rely on the input.
 * <p>
 * The tests probe {@link Shape#contains(double, double)}, which consults the
 * same winding rule {@code Graphics2D.fill} does, so they measure what the
 * canvas shows rather than the segment counts
 * {@code CurvedShapeWriterCurvePolygonTest} already covers.
 */
public class CurvedShapeWriterHoleWindingTest extends GeometryTestCase {

  /** The issue's input: shell r=10 and hole r=3, both wound the same way. */
  private static final String ANNULUS =
      "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0), "
      + "CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";

  /** The same annulus with the hole wound the other way. */
  private static final String ANNULUS_REVERSED_HOLE =
      "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0), "
      + "CIRCULARSTRING (-3 0, 0 -3, 3 0, 0 3, -3 0))";

  /** Origin: inside the hole, so outside the polygon. */
  private static final double HOLE_X = 0.0, HOLE_Y = 0.0;

  /** Midway between r=3 and r=10, so inside the filled body. */
  private static final double BODY_X = 6.0, BODY_Y = 0.0;

  public static void main(String[] args) {
    TestRunner.run(CurvedShapeWriterHoleWindingTest.class);
  }

  public CurvedShapeWriterHoleWindingTest(String name) { super(name); }

  private static Shape shapeOf(String wkt) throws Exception {
    Geometry g = new CurvedWKTReader().read(wkt);
    return new CurvedShapeWriter().toShape(g);
  }

  /** The reported symptom: the hole must not be filled. */
  public void testArcHoleIsNotFilled() throws Exception {
    assertFalse("the hole centre must not be part of the filled area",
        shapeOf(ANNULUS).contains(HOLE_X, HOLE_Y));
  }

  /**
   * The invariant the defect breaks: the hole is a hole either way round.
   * <p>
   * This is the assertion that distinguishes a fix from a coincidence. The
   * reversed-hole case already passed before the fix, because opposite winding
   * happens to cancel under the non-zero rule -- so only checking one
   * orientation cannot tell a rule change from a lucky input.
   */
  public void testHoleIsOrientationIndependent() throws Exception {
    assertFalse("hole wound with the shell must still be a hole",
        shapeOf(ANNULUS).contains(HOLE_X, HOLE_Y));
    assertFalse("hole wound against the shell must still be a hole",
        shapeOf(ANNULUS_REVERSED_HOLE).contains(HOLE_X, HOLE_Y));
  }

  /** Guard: the body between the rings stays filled, both orientations. */
  public void testAnnulusBodyStaysFilled() throws Exception {
    assertTrue("annulus body must be filled",
        shapeOf(ANNULUS).contains(BODY_X, BODY_Y));
    assertTrue("annulus body must be filled with the hole reversed",
        shapeOf(ANNULUS_REVERSED_HOLE).contains(BODY_X, BODY_Y));
  }

  /** Guard: a shell with no hole must not become hollow. */
  public void testShellOnlyRemainsFilled() throws Exception {
    Shape s = shapeOf("CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0))");
    assertTrue("disc centre must be filled", s.contains(HOLE_X, HOLE_Y));
    assertTrue("disc interior must be filled", s.contains(BODY_X, BODY_Y));
  }

  /** A CompoundCurve hole reaches the same path, so it needs the same rule. */
  public void testCompoundCurveHoleIsNotFilled() throws Exception {
    Shape s = shapeOf("CURVEPOLYGON ("
        + "COMPOUNDCURVE (CIRCULARSTRING (-10 0, 0 10, 10 0), (10 0, -10 0)), "
        + "COMPOUNDCURVE (CIRCULARSTRING (-3 0, 0 3, 3 0), (3 0, -3 0)))");
    assertFalse("compound-curve hole must not be filled", s.contains(HOLE_X, 1.0));
    assertTrue("compound-curve body must be filled", s.contains(BODY_X, 1.0));
  }

  /** A holed CurvePolygon inside a MultiSurface renders through the same path. */
  public void testMultiSurfaceMemberHoleIsNotFilled() throws Exception {
    Shape s = shapeOf("MULTISURFACE (" + ANNULUS + ")");
    assertFalse("member hole must not be filled", s.contains(HOLE_X, HOLE_Y));
    assertTrue("member body must be filled", s.contains(BODY_X, BODY_Y));
  }

  /** Guard: disjoint MultiSurface members are each filled. */
  public void testDisjointMultiSurfaceMembersBothFilled() throws Exception {
    Shape s = shapeOf("MULTISURFACE ("
        + "CURVEPOLYGON (CIRCULARSTRING (2 0, 0 2, -2 0, 0 -2, 2 0)), "
        + "CURVEPOLYGON (CIRCULARSTRING (12 0, 10 2, 8 0, 10 -2, 12 0)))");
    assertTrue("first member filled", s.contains(0.0, 0.0));
    assertTrue("second member filled", s.contains(10.0, 0.0));
  }

  /**
   * The mechanism, and the parity claim: the curved writer must report the same
   * winding rule core's polygon paths do, so anything reading the rule directly
   * rather than calling {@code contains} behaves identically.
   */
  public void testWindingRuleMatchesCore() throws Exception {
    assertEquals("curved polygon path should use core's WIND_EVEN_ODD",
        PathIterator.WIND_EVEN_ODD,
        shapeOf(ANNULUS).getPathIterator(null).getWindingRule());
  }

  /** Guard: an all-linear CurvePolygon still goes to core and still has its hole. */
  public void testLinearCurvePolygonHoleUnchanged() throws Exception {
    Shape s = shapeOf("CURVEPOLYGON ((-10 -10, 10 -10, 10 10, -10 10, -10 -10), "
        + "(-3 -3, 3 -3, 3 3, -3 3, -3 -3))");
    assertFalse("linear hole must not be filled", s.contains(HOLE_X, HOLE_Y));
    assertTrue("linear body must be filled", s.contains(BODY_X, BODY_Y));
  }
}
