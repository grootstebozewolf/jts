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
 * OVL-OPT: the predicate filter in front of overlay must be arc-aware.
 * <p>
 * OVL-STATIC excluded {@code OverlayNGOptFunctions} wholesale, on the evidence
 * that {@code intersection} returned a CurvePolygon of area exactly {@code 9*pi}
 * and so must not be densified. That exclusion was per-class where the evidence
 * was per-method: {@code OverlayNGOpt.difference} returned
 * {@code Polygon[14]} -- the two control-point rings -- because it does not
 * short-circuit and falls through to a static
 * {@code OverlayNGRobust.overlay}. An exclusion is a claim about every method in
 * the class, and this one was only ever tested on one.
 * <p>
 * The deeper problem is the filter itself. This class exists "to test using
 * spatial predicates as a filter in front of overlay operations", and both
 * filters are chord-based: {@code a.relate(b)}, and a {@code PreparedGeometry}
 * built from {@code a}'s coordinates. {@code intersection} was right by
 * coincidence -- for two concentric circles the chord square of the larger does
 * cover the chord square of the smaller, so the filter reached the same verdict
 * the arcs would.
 * <p>
 * <b>The case that separates them.</b> Take A as the radius-5 arc circle, whose
 * control points are the four axis extremes, so its chord ring is the diamond
 * {@code |x| + |y| <= 5}. Put a small square at (3, 3): every vertex is within
 * 4.39 of the origin, so the square lies wholly <em>inside the circle</em>, and
 * every vertex has {@code |x| + |y| >= 5.8}, so it lies wholly <em>outside the
 * diamond</em>. The arc answer is that A covers it, giving an intersection of the
 * square itself. The chord answer is that they are disjoint, giving an
 * <b>empty</b> intersection -- not an approximation of the right answer but the
 * opposite conclusion, reached confidently by a fast path.
 * <p>
 * The fix keeps what was right. Densifying is used for the <em>decision</em>, and
 * when the short-circuit fires the original operand is returned untouched, so
 * {@code intersection} of the concentric circles is still an exact CurvePolygon
 * of area {@code 9*pi}. That is the distinction ADD-HOLE-CURVE established:
 * densify to decide, not to answer.
 */
public class OverlayOptCurveTest extends TestCase {

  private static final String A = "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String B = "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";

  /**
   * Inside the radius-5 circle (all vertices within 4.39 of the origin), outside
   * the control-point diamond (all vertices have |x| + |y| >= 5.8).
   */
  private static final String CORNER_SQUARE =
      "POLYGON ((2.9 2.9, 3.1 2.9, 3.1 3.1, 2.9 3.1, 2.9 2.9))";

  private static final double CORNER_AREA = 0.2 * 0.2;

  private static final double AREA_A = 25.0 * Math.PI;
  private static final double AREA_B = 9.0 * Math.PI;
  private static final double ANNULUS = AREA_A - AREA_B;

  private static final double AREA_TOL = 1.0e-3;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(OverlayOptCurveTest.class); }
  public OverlayOptCurveTest(String name) { super(name); }

  private static Geometry read(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static Geometry a() throws Exception { return read(A); }
  private static Geometry b() throws Exception { return read(B); }
  private static Geometry corner() throws Exception { return read(CORNER_SQUARE); }

  // -- the reported miss ---------------------------------------------------

  public void testOptDifferenceIsTheAnnulus() throws Exception {
    assertEquals("OverlayNGOpt difference should be 16*pi, not the control rings' 32",
        ANNULUS, OverlayNGOptFunctions.difference(a(), b()).getArea(), AREA_TOL);
  }

  // -- the filter reaching the opposite conclusion -------------------------

  /**
   * The chord filter calls these disjoint and returns empty. The arc answer is
   * that A covers the square, so the intersection is the square.
   */
  public void testFilterDoesNotCallACoveredSquareDisjoint() throws Exception {
    assertEquals("A contains the corner square, so the intersection is the square",
        CORNER_AREA, OverlayNGOptFunctions.intersection(a(), corner()).getArea(),
        1.0e-9);
  }

  public void testFilterIsArcAwareInEveryIntersectionVariant() throws Exception {
    assertEquals("intersectionOrigClassic", CORNER_AREA,
        OverlayNGOptFunctions.intersectionOrigClassic(a(), corner()).getArea(), 1.0e-9);
    assertEquals("intersectionPrep", CORNER_AREA,
        OverlayNGOptFunctions.intersectionPrep(a(), corner()).getArea(), 1.0e-9);
    assertEquals("intersectionPrepNoCache", CORNER_AREA,
        OverlayNGOptFunctions.intersectionPrepNoCache(a(), corner()).getArea(), 1.0e-9);
    assertEquals("intersectionOrigPrep", CORNER_AREA,
        OverlayNGOptFunctions.intersectionOrigPrep(a(), corner()).getArea(), 1.0e-9);
    assertEquals("intersectionOrigPrepNoCache", CORNER_AREA,
        OverlayNGOptFunctions.intersectionOrigPrepNoCache(a(), corner()).getArea(), 1.0e-9);
  }

  /** The difference filter reaches the wrong conclusion on the same input. */
  public void testDifferenceOfACoveredSquare() throws Exception {
    assertEquals("A less the corner square should lose the square's area",
        AREA_A - CORNER_AREA,
        OverlayNGOptFunctions.difference(a(), corner()).getArea(), AREA_TOL);
  }

  // -- what must stay exact ------------------------------------------------

  /**
   * The whole reason this class was excluded: when the short-circuit fires, the
   * operand is returned untouched, which is exact. Densifying to decide must not
   * turn into densifying to answer.
   */
  public void testShortCircuitAnswersStayExact() throws Exception {
    Geometry result = OverlayNGOptFunctions.intersection(a(), b());
    assertEquals("still exactly 9*pi", AREA_B, result.getArea(), 1.0e-9);
    assertEquals("still the curve's five control points", 5, result.getNumPoints());
    assertEquals("still a CurvePolygon", "CurvePolygon", result.getGeometryType());
  }

  public void testPreparedShortCircuitsStayExact() throws Exception {
    for (Geometry result : new Geometry[] {
        OverlayNGOptFunctions.intersectionPrep(a(), b()),
        OverlayNGOptFunctions.intersectionPrepNoCache(a(), b()),
        OverlayNGOptFunctions.intersectionOrigPrep(a(), b()),
        OverlayNGOptFunctions.intersectionOrigPrepNoCache(a(), b()),
        OverlayNGOptFunctions.intersectionOrigClassic(a(), b()) }) {
      assertEquals("prepared short-circuit must stay exact",
          AREA_B, result.getArea(), 1.0e-9);
      assertEquals("and must not densify", 5, result.getNumPoints());
    }
  }

  // -- guards --------------------------------------------------------------

  /** Guard: genuinely disjoint input still yields an empty intersection. */
  public void testTrulyDisjointIsStillEmpty() throws Exception {
    Geometry far = read("POLYGON ((100 100, 101 100, 101 101, 100 101, 100 100))");
    assertTrue("a square 100 units away cannot intersect",
        OverlayNGOptFunctions.intersection(a(), far).isEmpty());
    assertEquals("and the difference is all of A", AREA_A,
        OverlayNGOptFunctions.difference(a(), far).getArea(), AREA_TOL);
  }

  /** Guard: plain polygons are unaffected, values and vertex counts alike. */
  public void testPlainPolygonsUnchanged() throws Exception {
    Geometry p = read("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    Geometry q = read("POLYGON ((5 5, 15 5, 15 15, 5 15, 5 5))");
    assertEquals("intersection", 25.0,
        OverlayNGOptFunctions.intersection(p, q).getArea(), 0.0);
    assertEquals("difference", 75.0,
        OverlayNGOptFunctions.difference(p, q).getArea(), 0.0);
    Geometry inner = read("POLYGON ((2 2, 4 2, 4 4, 2 4, 2 2))");
    Geometry covered = OverlayNGOptFunctions.intersection(p, inner);
    assertEquals("a covered plain square is returned as itself", 4.0,
        covered.getArea(), 0.0);
    assertEquals("with its own five vertices", 5, covered.getNumPoints());
  }

  /** Guard: the prepared-geometry cache still keys on operand identity. */
  public void testPreparedCacheStillHits() throws Exception {
    Geometry sameA = a();
    Geometry first = OverlayNGOptFunctions.intersectionPrep(sameA, b());
    Geometry second = OverlayNGOptFunctions.intersectionPrep(sameA, b());
    assertEquals("repeated calls with the same operand must agree",
        first.getArea(), second.getArea(), 0.0);
  }
}
