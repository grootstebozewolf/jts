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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * DIFF-SEG: the segments of a curve are its arc's segments, not its chords.
 * <p>
 * Reported for {@code diffSegments} on
 * {@code A = CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)} against
 * {@code B = CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0)}, which returns
 * {@code MULTILINESTRING ((-5 0, 0 5), (0 5, 5 0), (0 -5, 5 0), (-5 0, 0 -5))}.
 * <p>
 * The set logic in that answer is right: the two circles are concentric with
 * different radii, so they share no segment at all and every segment of A is
 * reported. What is wrong is the segments. Each is a chord of the inscribed
 * square, side {@code 5*sqrt(2)}, and its midpoint sits
 * {@code r(1 - cos(45deg))} = <b>1.4645</b> away from the circle it claims to
 * describe -- 29% of the radius. The four together total
 * {@code 20*sqrt(2)} = 28.284 against a circumference of {@code 10*pi} = 31.416.
 * <p>
 * {@code extractSegmentsNorm} builds from {@code LinearComponentExtracter} plus
 * {@code getCoordinates()}, which for a curve is its control points, so the
 * operation describes the control polygon rather than the curve. Same gap as
 * IO-WRT and the hull family: a static entry point taking a {@code Geometry},
 * with no virtual call for a curve type to intercept.
 * <p>
 * <b>Scope: the vertex functions are deliberately untouched.</b>
 * {@code diffVertices} reads the same control points, but every control point of
 * a CircularString lies exactly <em>on</em> the circle, so it returns an
 * incomplete subset rather than wrong geometry -- unlike a chord, which lies off
 * the curve entirely. {@link #testDiffVerticesStillReportsControlPoints()} locks
 * that decision in place so it is visible rather than an omission.
 * <p>
 * <b>Measured limitation.</b> The densify tolerance is a fraction of each
 * geometry's own extent, so two curves are only recognised as sharing segments
 * when they densify to the same vertices. A and its upper half both have extent
 * 10, so the shared half is recognised exactly -- 786 of 1572 segments. A
 * quarter arc has extent 5, a different tolerance, and shares nothing. That is
 * not a regression: the chord version shares nothing there either, because the
 * quarter arc's chords differ from A's. Independent sampling has this property
 * at any tolerance.
 */
public class DiffFunctionsCurveTest extends TestCase {

  private static final String A = "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)";
  private static final String B = "CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0)";
  private static final String UPPER_HALF_OF_A = "CIRCULARSTRING (-5 0, 0 5, 5 0)";

  private static final double RADIUS = 5.0;
  private static final double CIRCUMFERENCE = 2.0 * Math.PI * RADIUS;

  /** The densify tolerance in force: 1e-6 of the 10-unit extent. */
  private static final double DENSIFY_TOL = 10.0 * 1.0e-6;

  /** Deviation of the reported chords from the arc, for the failure messages. */
  private static final double CHORD_SAGITTA = RADIUS * (1.0 - Math.cos(Math.PI / 4));

  /**
   * An inscribed n-gon is shorter than its circle by a relative
   * {@code (pi/n)^2/6}; at the ~1572 segments this tolerance implies that is
   * under 1e-6, so 1e-4 absolute is loose by two orders and still four orders
   * tighter than the 3.13 the chords are out by.
   */
  private static final double LENGTH_TOL = 1.0e-4;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(DiffFunctionsCurveTest.class); }
  public DiffFunctionsCurveTest(String name) { super(name); }

  private static Geometry read(String wkt) throws Exception {
    return new CurvedWKTReader(new CurvedGeometryFactory()).read(wkt);
  }

  /**
   * The deviation of the worst segment midpoint from the true circle. A segment
   * of an inscribed polyline is off by at most the densify tolerance; a chord of
   * the control polygon is off by the sagitta.
   */
  private static double worstMidpointDeviation(Geometry segments) {
    double worst = 0.0;
    for (int i = 0; i < segments.getNumGeometries(); i++) {
      Coordinate[] c = segments.getGeometryN(i).getCoordinates();
      double mx = 0.5 * (c[0].x + c[c.length - 1].x);
      double my = 0.5 * (c[0].y + c[c.length - 1].y);
      worst = Math.max(worst, Math.abs(Math.hypot(mx, my) - RADIUS));
    }
    return worst;
  }

  /** The core claim: a reported segment must lie on the curve it came from. */
  public void testDiffSegmentsLieOnTheCurve() throws Exception {
    Geometry diff = DiffFunctions.diffSegments(read(A), read(B));
    double dev = worstMidpointDeviation(diff);
    assertTrue("every segment should lie on the circle to within the "
        + DENSIFY_TOL + " densify tolerance; worst midpoint was " + dev
        + " off, and the control-point chords are " + CHORD_SAGITTA + " off",
        dev < 2.0 * DENSIFY_TOL);
  }

  /** Concentric circles share nothing, so the diff is all of A's arc. */
  public void testDiffSegmentsTotalLengthIsTheCircumference() throws Exception {
    assertEquals("the whole of A should come back, i.e. its circumference, not the "
        + (20 * Math.sqrt(2)) + " of its chords",
        CIRCUMFERENCE, DiffFunctions.diffSegments(read(A), read(B)).getLength(),
        LENGTH_TOL);
  }

  /** A shared sub-arc at the same extent must be recognised as shared. */
  public void testSharedHalfArcIsRecognised() throws Exception {
    Geometry diff = DiffFunctions.diffSegments(read(A), read(UPPER_HALF_OF_A));
    assertEquals("half the circumference should remain once the shared upper half "
        + "is removed", CIRCUMFERENCE / 2.0, diff.getLength(), LENGTH_TOL);
  }

  /** Both directions of the diff must see the arc. */
  public void testDiffSegmentsBothSeesTheArc() throws Exception {
    Geometry both = DiffFunctions.diffSegmentsBoth(read(A), read(B));
    assertEquals("A-not-B plus B-not-A should be both circumferences",
        CIRCUMFERENCE + 2.0 * Math.PI * 3.0, both.getLength(), LENGTH_TOL);
  }

  /** singleSegments reports every segment of A once, so it is the whole arc. */
  public void testSingleSegmentsSeesTheArc() throws Exception {
    Geometry single = DiffFunctions.singleSegments(read(A));
    assertEquals("every segment of the circle occurs once, totalling the circumference",
        CIRCUMFERENCE, single.getLength(), LENGTH_TOL);
    assertTrue("and each must lie on the circle",
        worstMidpointDeviation(single) < 2.0 * DENSIFY_TOL);
  }

  /** duplicateSegments finds the arc's repeats, of which a simple circle has none. */
  public void testDuplicateSegmentsOnACircleIsEmpty() throws Exception {
    assertEquals("a simple circle repeats no segment", 0,
        DiffFunctions.duplicateSegments(read(A)).getNumGeometries());
  }

  /** Guard: the contract that already held. Identical input, empty diff. */
  public void testDiffOfIdenticalCurvesIsEmpty() throws Exception {
    assertEquals("identical curves differ nowhere", 0,
        DiffFunctions.diffSegments(read(A), read(A)).getNumGeometries());
  }

  /**
   * Guard, and a recorded decision rather than an oversight: diffVertices keeps
   * reporting control points. They lie exactly on the circle, so unlike the
   * chords they are not wrong, only a subset -- see the class comment.
   */
  public void testDiffVerticesStillReportsControlPoints() throws Exception {
    Geometry diff = DiffFunctions.diffVertices(read(A), read(B));
    assertTrue("expected the handful of control points, got " + diff.getNumPoints(),
        diff.getNumPoints() <= 5);
    for (Coordinate c : diff.getCoordinates()) {
      assertEquals("and each control point lies on the circle",
          RADIUS, Math.hypot(c.x, c.y), 1.0e-9);
    }
  }

  /** Guard: a plain LineString is untouched, segment for segment. */
  public void testPlainLineStringUnchanged() throws Exception {
    Geometry diff = DiffFunctions.diffSegments(
        read("LINESTRING (0 0, 10 0, 10 10)"), read("LINESTRING (0 0, 10 0)"));
    assertEquals("one segment should remain", 1, diff.getNumGeometries());
    assertEquals("of length 10", 10.0, diff.getLength(), 0.0);
  }

  /** Guard: plain polygons are untouched. */
  public void testPlainPolygonsUnchanged() throws Exception {
    Geometry diff = DiffFunctions.diffSegments(
        read("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))"),
        read("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))"));
    assertEquals("identical polygons differ nowhere", 0, diff.getNumGeometries());
  }

  /** Guard: an empty input does not throw. */
  public void testEmptyInput() throws Exception {
    assertEquals(0, DiffFunctions.diffSegments(
        read("CIRCULARSTRING EMPTY"), read(B)).getNumGeometries());
  }
}
