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
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * H-CC: the ConcaveHull family must see the arc, not the control points.
 * <p>
 * Reported as issue #6 from the visual-QA sweep. For
 * {@code COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 10 10))},
 * {@code concaveHullPointsWithHolesByLenRatio} returns
 * {@code POLYGON ((0 0, 5 5, 10 10, 10 0, 0 0))} -- area exactly 50.0, the
 * convex hull of the four control points, with the arc's bulge discarded.
 * <p>
 * This is <em>not</em> the same defect CRV-OPS fixed. {@code convexHull()} is an
 * instance method, so {@code CompoundCurve} overrides it and it is arc-aware
 * (area 61.59 for this input). {@code ConcaveHull.concaveHullByLengthRatio} is
 * <em>static</em> and takes a {@code Geometry}: there is no virtual dispatch to
 * hook, and jts-core cannot see the curve types, so no override in jts-curved
 * can reach it. The caller must linearise.
 * <p>
 * Two tells that this is degeneracy rather than a slightly-wrong hull:
 * <ul>
 * <li>The result is identical for every length ratio from 0.0 to 1.0. Four input
 *     points leave the triangulation no interior edges to erode, so every
 *     parameter collapses to the same answer.</li>
 * <li>The vertex (5 5) survives. ConcaveHull does not strip collinear vertices,
 *     whereas ConvexHull does -- the control-point convex hull of the same input
 *     is {@code POLYGON ((0 0, 10 10, 10 0, 0 0))}.</li>
 * </ul>
 */
public class HullFunctionsCurveTest extends TestCase {

  /** Arc centre (5,0) radius 5, then a straight member up to (10,10). */
  private static final String COMPOUND =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 10 10))";

  /** Area of the convex hull of the four control points. */
  private static final double CONTROL_POINT_AREA = 50.0;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(HullFunctionsCurveTest.class); }
  public HullFunctionsCurveTest(String name) { super(name); }

  private static Geometry read(String wkt) throws Exception {
    return new CurvedWKTReader(new CurvedGeometryFactory()).read(wkt);
  }

  /**
   * The arc point at 135 degrees, which bulges left of the line y = x and so
   * lies outside the control-point hull. Nothing driven off control points can
   * reach it.
   */
  private static Geometry bulgePoint() {
    return new GeometryFactory().createPoint(new Coordinate(
        5 + 5 * Math.cos(Math.toRadians(135)),
        5 * Math.sin(Math.toRadians(135))));
  }

  /** The reported call: the hull must not be the control-point hull. */
  public void testWithHolesByLenRatioSeesTheArc() throws Exception {
    Geometry hull = HullFunctions.concaveHullPointsWithHolesByLenRatio(read(COMPOUND), 1.0);
    assertTrue("hull should not collapse to the control-point hull (area "
        + CONTROL_POINT_AREA + "), got area " + hull.getArea()
        + " with " + hull.getNumPoints() + " vertices",
        Math.abs(hull.getArea() - CONTROL_POINT_AREA) > 1.0);
  }

  /**
   * The length ratio must actually change the result. Identical output across
   * the whole parameter range is the signature of a degenerate 4-point input.
   */
  public void testLenRatioChangesTheResult() throws Exception {
    double tight = HullFunctions.concaveHullPointsWithHolesByLenRatio(read(COMPOUND), 0.0).getArea();
    double loose = HullFunctions.concaveHullPointsWithHolesByLenRatio(read(COMPOUND), 1.0).getArea();
    assertTrue("ratio 1.0 (area " + loose + ") should be materially looser than ratio 0.0 (area "
        + tight + ")", loose > tight * 1.5);
  }

  /**
   * At the loosest ratio the hull must reach the arc's bulge.
   * <p>
   * The bound is the densification tolerance, not a round number: the hull is
   * built from an inscribed polyline, so the exact arc extremum sits at most one
   * chord-deviation outside it. That deviation is
   * {@code HULL_TOLERANCE_FRACTION} (1e-4) of the 10-unit extent, i.e. 0.001, and
   * the observed gap is 0.00074. Doubling it leaves margin without letting a
   * genuine regression -- the control-point hull was 1.464 short -- slip through.
   */
  public void testHullReachesTheArcBulge() throws Exception {
    double densifyTolerance = 10.0 * 1.0e-4;
    Geometry hull = HullFunctions.concaveHullPointsWithHolesByLenRatio(read(COMPOUND), 1.0);
    double gap = hull.distance(bulgePoint());
    assertTrue("hull should reach the arc bulge at 135 degrees to within the "
        + densifyTolerance + " densification tolerance, gap was " + gap,
        gap < 2.0 * densifyTolerance);
  }

  /** The hull must have far more vertices than the five of the control-point hull. */
  public void testHullFollowsTheArcWithManyVertices() throws Exception {
    Geometry hull = HullFunctions.concaveHullPointsWithHolesByLenRatio(read(COMPOUND), 0.0);
    assertTrue("hull should follow the arc with many vertices, got " + hull.getNumPoints(),
        hull.getNumPoints() > 10);
  }

  /** The same gap affects the by-length entry point. */
  public void testConcaveHullByLengthSeesTheArc() throws Exception {
    Geometry hull = HullFunctions.concaveHullPoints(read(COMPOUND), 5.0);
    assertTrue("by-length hull should not be the control-point hull, got area "
        + hull.getArea(), Math.abs(hull.getArea() - CONTROL_POINT_AREA) > 1.0);
  }

  /** And the alpha-shape entry point. */
  public void testAlphaShapeSeesTheArc() throws Exception {
    Geometry hull = HullFunctions.alphaShape(read(COMPOUND), 5.0);
    assertTrue("alpha shape should not be the control-point hull, got area "
        + hull.getArea(), Math.abs(hull.getArea() - CONTROL_POINT_AREA) > 1.0);
  }

  /** The length guess must reflect the arc, not the chords. */
  public void testLenGuessSeesTheArc() throws Exception {
    Geometry curve = read(COMPOUND);
    double guess = HullFunctions.concaveHullLenGuess(curve);
    // Derived from the vertex count, so 4 control points give a coarse guess.
    assertTrue("length guess " + guess + " should be finer than the 4-point estimate 3.924",
        guess < 3.9);
  }

  /** Guard: convexHull was already arc-aware via CRV-OPS and must stay so. */
  public void testConvexHullStillArcAware() throws Exception {
    double area = HullFunctions.convexHull(read(COMPOUND)).getArea();
    assertTrue("convex hull should exceed the control-point hull, got " + area,
        area > CONTROL_POINT_AREA + 10.0);
  }

  /**
   * The narrowest span between two ring vertices that are far apart along the
   * ring -- a pinch shows up as a tiny value where the boundary doubles back on
   * itself. Neighbours are skipped so ordinary short edges do not count.
   */
  private static double waistOf(Geometry hull) {
    Coordinate[] c = ((org.locationtech.jts.geom.Polygon) hull)
        .getExteriorRing().getCoordinates();
    int n = c.length - 1;
    double waist = Double.MAX_VALUE;
    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        if (Math.min(j - i, n - (j - i)) < 5) continue;
        waist = Math.min(waist, c[i].distance(c[j]));
      }
    }
    return waist;
  }

  /**
   * The hull must not collapse to a hairline ribbon.
   * <p>
   * ConcaveHull is a point-set operation, so sampling a 1-D curve ever more
   * finely does not refine the answer -- the hull erodes down onto the curve and
   * the result becomes a ribbon whose width is one sampling step. Densifying at
   * {@code CurveOps}' 1e-6 of extent gave a waist of 0.0200 on a 10-unit
   * geometry, which renders as a bowtie.
   * <p>
   * Nothing here is arc-specific: core's {@code Densifier} on a plain
   * {@code LINESTRING (0 0, 5 5, 10 0, 10 10)} pinches identically, 5.30 down to
   * 0.0674 as the step shrinks. The tolerance for this family therefore has to
   * match what the hull can resolve rather than what a distance predicate wants.
   * A pinch remains intrinsic at sufficient zoom; the requirement is only that
   * the default not be degenerate.
   */
  public void testHullDoesNotCollapseToAHairline() throws Exception {
    Geometry hull = HullFunctions.concaveHullPointsWithHolesByLenRatio(read(COMPOUND), 0.5);
    double waist = waistOf(hull);
    assertTrue("hull waist " + waist + " should stay above 1% of the 10-unit extent",
        waist > 0.1);
  }

  /** Whatever the sampling, the result must be a usable polygon. */
  public void testHullIsValidAndSimple() throws Exception {
    for (double ratio : new double[] { 0.0, 0.3, 0.5, 0.7, 1.0 }) {
      Geometry hull = HullFunctions.concaveHullPointsWithHolesByLenRatio(read(COMPOUND), ratio);
      assertTrue("ratio " + ratio + " should be valid", hull.isValid());
      assertTrue("ratio " + ratio + " should be simple", hull.isSimple());
    }
  }

  /** Guard: a plain LineString is unaffected by any linearisation. */
  public void testPlainLineStringUnchanged() throws Exception {
    Geometry line = read("LINESTRING (0 0, 10 0, 10 10, 0 10, 0 0)");
    Geometry hull = HullFunctions.concaveHullPointsWithHolesByLenRatio(line, 1.0);
    assertEquals("square hull area", 100.0, hull.getArea(), 1.0e-9);
  }

  /** Guard: an all-straight CompoundCurve is unaffected. */
  public void testStraightCompoundCurveUnchanged() throws Exception {
    Geometry g = read("COMPOUNDCURVE ((0 0, 10 0), (10 0, 10 10), (10 10, 0 0))");
    Geometry hull = HullFunctions.concaveHullPointsWithHolesByLenRatio(g, 1.0);
    assertEquals("right triangle area", 50.0, hull.getArea(), 1.0e-9);
  }
}
