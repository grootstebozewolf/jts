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
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * ADD-HOLE: {@code Geometry > addHoles} and {@code Edit > addHole} on curve input.
 * <p>
 * Two separate reports, with two different answers.
 * <p>
 * <b>1. The reported exceptions are correct refusals, not curve defects.</b>
 * Given {@code A = CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)},
 * {@code addHoles} throws {@code ClassCastException: CircularString cannot be
 * cast to Polygon} and {@code addHole} throws
 * {@code IllegalArgumentException: A is not a polygon}. A CircularString
 * <em>is</em> a LineString, and a hole cannot be added to a line, so refusing is
 * right. Nothing about this is curve-specific: a plain
 * {@code LINESTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)} produces the identical
 * exception from each function. The only defect is diagnostics --
 * {@code addHoles} casts without checking and leaks a raw ClassCastException
 * where its sibling gives a usable message. Fixed by making the two agree.
 * <p>
 * <b>2. The curve defect is the case that does not throw.</b> With
 * {@code A = CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))} and
 * {@code B} the r=3 equivalent, the cast succeeds -- CurvePolygon extends
 * Polygon -- and both functions return
 * {@code POLYGON ((-5 0, 0 5, 5 0, 0 -5, -5 0), (-3 0, 0 3, 3 0, 0 -3, -3 0))}:
 * the control-point rings, area <b>32</b> against the true annulus
 * {@code 25*pi - 9*pi} = 50.265. Silently the wrong shape, the same gap as
 * IO-WRT and DIFF-SEG, because {@code getExteriorRing()} is the flat
 * control-point view.
 * <p>
 * <b>Superseded in part by ADD-HOLE-CURVE.</b> This claim was first made green by
 * densifying both inputs, on the assumption that the output had to be a plain
 * Polygon assembled from LinearRings. That assumption was wrong: adding a hole is
 * a structural operation and {@code CurvePolygon}'s Option-A constructor can hold
 * the arcs, so the exact answer was available all along. The area assertions here
 * still hold -- and now hold exactly rather than to a tolerance -- but the
 * vertex-count assertion that expected a densified hole has been removed, because
 * a densified hole is no longer the desired outcome. See
 * {@code AddHoleCurveStructureTest}, which asserts the structural result.
 */
public class AddHoleCurveTest extends TestCase {

  private static final String CIRCLE_LINE_A = "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)";
  private static final String PLAIN_LINE_A = "LINESTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)";
  private static final String CIRCLE_LINE_B = "CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0)";

  private static final String CURVE_POLY_A =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CURVE_POLY_B =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";

  /** The annulus between r=5 and r=3. */
  private static final double TRUE_AREA = Math.PI * (25.0 - 9.0);

  /** What the control-point rings give: the two inscribed squares, 50 - 18. */
  private static final double CONTROL_POINT_AREA = 32.0;

  /**
   * Densify tolerance is 1e-6 of each extent, and an inscribed polygon's area
   * shortfall is bounded by its perimeter times that tolerance. Generous by an
   * order, still four orders inside the 18.3 the control points are out by.
   */
  private static final double AREA_TOL = 1.0e-3;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(AddHoleCurveTest.class); }
  public AddHoleCurveTest(String name) { super(name); }

  private static Geometry read(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  // -- 2. the curve defect -------------------------------------------------

  /** The reported CURVEPOLYGON case, on the Geometry function. */
  public void testAddHolesOnCurvePolygonKeepsTheArc() throws Exception {
    Geometry result = GeometryFunctions.addHoles(read(CURVE_POLY_A), read(CURVE_POLY_B));
    assertEquals("annulus area should be 16*pi, not the " + CONTROL_POINT_AREA
        + " of the control-point rings", TRUE_AREA, result.getArea(), AREA_TOL);
  }

  /** And on the Edit function, which has the same flat-ring problem. */
  public void testAddHoleOnCurvePolygonKeepsTheArc() throws Exception {
    Geometry result = EditFunctions.addHole(read(CURVE_POLY_A), read(CURVE_POLY_B));
    assertEquals("annulus area should be 16*pi", TRUE_AREA, result.getArea(), AREA_TOL);
  }

  /**
   * The hole must be present as a hole. How it is represented is
   * ADD-HOLE-CURVE's claim, not this one -- an earlier version of this test
   * required a densified ring, which is exactly the outcome that turned out to be
   * avoidable.
   */
  public void testTheHoleIsAdded() throws Exception {
    Geometry result = GeometryFunctions.addHoles(read(CURVE_POLY_A), read(CURVE_POLY_B));
    assertEquals("one hole", 1,
        ((org.locationtech.jts.geom.Polygon) result).getNumInteriorRing());
  }

  /** Whatever the sampling, the result must be usable. */
  public void testResultIsValid() throws Exception {
    assertTrue("addHoles result should be valid",
        GeometryFunctions.addHoles(read(CURVE_POLY_A), read(CURVE_POLY_B)).isValid());
    assertTrue("addHole result should be valid",
        EditFunctions.addHole(read(CURVE_POLY_A), read(CURVE_POLY_B)).isValid());
  }

  /** A curve shell with a plain square hole still works. */
  public void testCurveShellWithPlainHole() throws Exception {
    Geometry result = GeometryFunctions.addHoles(read(CURVE_POLY_A),
        read("POLYGON ((-1 -1, 1 -1, 1 1, -1 1, -1 -1))"));
    assertEquals("circle area less the 4 of the square",
        Math.PI * 25.0 - 4.0, result.getArea(), AREA_TOL);
  }

  // -- 1. diagnostics parity, not a curve defect ---------------------------

  /**
   * The reported ClassCastException must become the message its sibling gives.
   * Asserted for the CircularString and for a plain LineString, because the
   * behaviour must be identical -- that is what shows this is about polygonal
   * input, not about curves.
   */
  public void testNonPolygonalInputIsReportedClearly() throws Exception {
    for (String wkt : new String[] { CIRCLE_LINE_A, PLAIN_LINE_A }) {
      try {
        GeometryFunctions.addHoles(read(wkt), read(CIRCLE_LINE_B));
        fail("addHoles should refuse non-polygonal A: " + wkt);
      }
      catch (IllegalArgumentException e) {
        assertTrue("message should name the problem, got: " + e.getMessage(),
            e.getMessage().contains("not a polygon"));
      }
      catch (ClassCastException e) {
        fail("addHoles leaked a ClassCastException for " + wkt
            + " instead of reporting non-polygonal input");
      }
    }
  }

  /** The two functions must refuse the same input with the same message. */
  public void testTheTwoFunctionsAgreeOnRefusal() throws Exception {
    String fromAddHoles = refusalOf(true);
    String fromAddHole = refusalOf(false);
    assertEquals("addHoles and addHole should report identically",
        fromAddHole, fromAddHoles);
  }

  private String refusalOf(boolean useAddHoles) throws Exception {
    try {
      Geometry a = read(CIRCLE_LINE_A);
      Geometry b = read(CIRCLE_LINE_B);
      if (useAddHoles) GeometryFunctions.addHoles(a, b); else EditFunctions.addHole(a, b);
      return "(no exception)";
    }
    catch (IllegalArgumentException e) { return e.getMessage(); }
    catch (RuntimeException e) { return e.getClass().getSimpleName(); }
  }

  /** A non-polygonal hole must also be reported rather than cast blindly. */
  public void testNonPolygonalHoleIsReportedClearly() throws Exception {
    try {
      GeometryFunctions.addHoles(read("POLYGON ((-5 -5, 5 -5, 5 5, -5 5, -5 -5))"),
          read("LINESTRING (0 0, 1 1)"));
      fail("addHoles should refuse a non-polygonal hole");
    }
    catch (IllegalArgumentException e) {
      assertNotNull("message should be present", e.getMessage());
    }
    catch (ClassCastException e) {
      fail("addHoles leaked a ClassCastException for a non-polygonal hole");
    }
  }

  // -- guards --------------------------------------------------------------

  /** Guard: plain polygons behave exactly as before, both functions. */
  public void testPlainPolygonsUnchanged() throws Exception {
    String shell = "POLYGON ((-5 -5, 5 -5, 5 5, -5 5, -5 -5))";
    String hole = "POLYGON ((-1 -1, 1 -1, 1 1, -1 1, -1 -1))";
    Geometry viaHoles = GeometryFunctions.addHoles(read(shell), read(hole));
    Geometry viaHole = EditFunctions.addHole(read(shell), read(hole));
    assertEquals("100 less the 4 of the hole", 96.0, viaHoles.getArea(), 0.0);
    assertEquals("100 less the 4 of the hole", 96.0, viaHole.getArea(), 0.0);
    assertEquals("exactly the ten input vertices", 10, viaHoles.getNumPoints());
    assertEquals("exactly the ten input vertices", 10, viaHole.getNumPoints());
  }

  /** Guard: an existing hole on a plain polygon is preserved. */
  public void testExistingHolePreserved() throws Exception {
    Geometry result = GeometryFunctions.addHoles(
        read("POLYGON ((-5 -5, 5 -5, 5 5, -5 5, -5 -5), (-4 -4, -3 -4, -3 -3, -4 -3, -4 -4))"),
        read("POLYGON ((-1 -1, 1 -1, 1 1, -1 1, -1 -1))"));
    assertEquals("both holes should be present", 2,
        ((org.locationtech.jts.geom.Polygon) result).getNumInteriorRing());
    assertEquals("100 less 1 less 4", 95.0, result.getArea(), 0.0);
  }
}
