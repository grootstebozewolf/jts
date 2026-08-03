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
 * D-HF: the Hausdorff / Frechet / nearest-point family must sample the arc.
 * <p>
 * These entry points call core statics ({@code DiscreteHausdorffDistance},
 * {@code DirectedHausdorffDistance}, {@code DiscreteFrechetDistance},
 * {@code DistanceOp}, {@code IndexedFacetDistance}) that read coordinates, so a
 * curve is measured by its control polyline. The epic spec's witness
 * ({@code CurveAwarenessSpecTest.test_D_HF_hausdorffFrechetCurveAware}) makes the
 * failure exact:
 * <pre>
 *   A = CIRCULARSTRING (0 0, 2 3, 10 0)
 *   B = LINESTRING (0 0, 10 0)
 * </pre>
 * The circle through A's control points has centre {@code (5, -7/6)} and radius
 * {@code sqrt(949)/6 = 5.13431}, so the arc's apex is at
 * {@code y = 3.96764} -- the continuous directed Hausdorff distance to the
 * baseline. The control-point reading attains only the mid control's height,
 * <b>3</b>. And the spec's sharpest observation: the chord-fraction densify knob
 * on {@code orientedDistanceLine} cannot close the gap, because it densifies the
 * straight control chords, which lie <em>inside</em> the arc and never reach the
 * apex. Densifying harder walks the wrong geometry more finely.
 * <p>
 * <b>Second witness, for the nearest-point family.</b> The concentric-circle
 * inputs from the earlier sweep cannot reveal this defect -- their control points
 * lie exactly at the true nearest points. Against {@code POINT (5 6)}, above the
 * apex: true distance {@code 6 - 3.96764 = 2.03236}; the chord reading finds its
 * nearest point on the chord {@code (2 3)-(10 0)} at distance <b>3.860</b>. An
 * 90% overestimate, from the family whose one job is measuring distance.
 * <p>
 * Remedy is the established caller-side shim: linearise both operands at the
 * entry point ({@code linearizeForOps}, 1e-5 here for the 10-unit extent), so the
 * discrete algorithms sample points ON the arc. {@code distance} and
 * {@code isWithinDistance} delegate to instance methods that CRV-OPS already
 * fixed, asserted as guards.
 */
public class DistanceFunctionsCurveTest extends TestCase {

  private static final String ARC = "CIRCULARSTRING (0 0, 2 3, 10 0)";
  private static final String BASELINE = "LINESTRING (0 0, 10 0)";

  /** Apex height of the circle through the arc's control points. */
  private static final double APEX = Math.sqrt(949.0) / 6.0 - 7.0 / 6.0;

  /** The control-polyline answer, for failure messages. */
  private static final double CHORD_ANSWER = 3.0;

  /** Densify tolerance is 1e-5 of the 10-unit extent; 1e-3 is generous. */
  private static final double TOL = 1.0e-3;

  private static final String APEX_POINT = "POINT (5 6)";
  private static final double APEX_POINT_DIST = 6.0 - APEX;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(DistanceFunctionsCurveTest.class); }
  public DistanceFunctionsCurveTest(String name) { super(name); }

  private static Geometry read(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  // -- the spec witness: directed Hausdorff ---------------------------------

  public void testOrientedDiscreteHausdorff() throws Exception {
    assertEquals("h(arc, baseline) is the apex height " + APEX + ", not the mid "
        + "control's " + CHORD_ANSWER, APEX,
        DistanceFunctions.orientedDiscreteHausdorffDistance(read(ARC), read(BASELINE)),
        TOL);
  }

  /**
   * The spec's sharpest case: the densify fraction cannot rescue the chord
   * reading, because it subdivides the chords, not the arc.
   */
  public void testOrientedHausdorffLineDensify() throws Exception {
    Geometry line = DistanceFunctions.orientedDiscreteHausdorffLineDensify(
        read(ARC), read(BASELINE), 0.05);
    assertEquals("the realizing segment's length is the apex height even with "
        + "chord-fraction densify in play", APEX, line.getLength(), TOL);
  }

  public void testDirectedHausdorffLine() throws Exception {
    assertEquals("directed Hausdorff line length", APEX,
        DistanceFunctions.directedHausdorffLine(read(ARC), read(BASELINE)).getLength(),
        TOL);
  }

  public void testDirectedHausdorffLineTol() throws Exception {
    assertEquals("directed Hausdorff (tolerance form) line length", APEX,
        DistanceFunctions.directedHausdorffLineTol(read(ARC), read(BASELINE), 0.001)
            .getLength(), TOL);
  }

  public void testDirectedHausdorffDistance() throws Exception {
    assertEquals("directed Hausdorff distance", APEX,
        DistanceFunctions.directedHausdorffDistance(read(ARC), read(BASELINE), 0.001),
        TOL);
  }

  /**
   * The symmetric Hausdorff agrees here: h(B, A) is also realized near the apex
   * -- the baseline midpoint (5, 0) is 7/6 from the circle's centre, so its
   * nearest arc point is radially above at distance r - 7/6 = the apex height.
   */
  public void testHausdorffLine() throws Exception {
    assertEquals("symmetric Hausdorff line length", APEX,
        DistanceFunctions.hausdorffLine(read(ARC), read(BASELINE)).getLength(), TOL);
  }

  // -- Frechet ---------------------------------------------------------------

  /**
   * Frechet dominates directed Hausdorff, so it must be at least the apex
   * height. The control-point coupling attains only 3.606 (mid control against
   * an endpoint), which is BELOW the true lower bound -- a Frechet smaller than
   * the Hausdorff it dominates is internally impossible, not just imprecise.
   */
  public void testFrechetDominatesHausdorff() throws Exception {
    double frechet = DistanceFunctions.frechetDistance(read(ARC), read(BASELINE));
    assertTrue("Frechet " + frechet + " must dominate directed Hausdorff " + APEX,
        frechet >= APEX - TOL);
  }

  public void testFrechetLineDominatesHausdorff() throws Exception {
    double len = DistanceFunctions.frechetDistanceLine(read(ARC), read(BASELINE))
        .getLength();
    assertTrue("Frechet line length " + len + " must dominate " + APEX,
        len >= APEX - TOL);
  }

  // -- nearest points: the witness the concentric sweep couldn't provide ------

  public void testDistanceIndexed() throws Exception {
    assertEquals("distance from the arc to a point above its apex is "
        + APEX_POINT_DIST + ", not the chord's 3.860", APEX_POINT_DIST,
        DistanceFunctions.distanceIndexed(read(ARC), read(APEX_POINT)), TOL);
  }

  public void testNearestPoints() throws Exception {
    assertEquals("nearest-points segment length", APEX_POINT_DIST,
        DistanceFunctions.nearestPoints(read(ARC), read(APEX_POINT)).getLength(), TOL);
  }

  public void testNearestPointsIndexed() throws Exception {
    assertEquals("indexed nearest-points segment length", APEX_POINT_DIST,
        DistanceFunctions.nearestPointsIndexed(read(ARC), read(APEX_POINT)).getLength(),
        TOL);
  }

  public void testNearestPointsIndexedEachB() throws Exception {
    Geometry lines = DistanceFunctions.nearestPointsIndexedEachB(
        read(ARC), read("MULTIPOINT ((5 6))"));
    assertEquals("per-member nearest segment length", APEX_POINT_DIST,
        lines.getLength(), TOL);
  }

  public void testIsWithinDistanceIndexed() throws Exception {
    assertTrue("the arc apex is 2.032 from the point, well within 2.5",
        DistanceFunctions.isWithinDistanceIndexed(read(ARC), read(APEX_POINT), 2.5));
  }

  // -- guards ------------------------------------------------------------------

  /** Guard: the instance-method entries were fixed by CRV-OPS and must agree. */
  public void testInstanceMethodEntriesAlreadyAgree() throws Exception {
    assertEquals("distance() is arc-aware via CRV-OPS", APEX_POINT_DIST,
        DistanceFunctions.distance(read(ARC), read(APEX_POINT)), TOL);
    assertTrue("isWithinDistance() likewise",
        DistanceFunctions.isWithinDistance(read(ARC), read(APEX_POINT), 2.5));
  }

  /** Guard: plain geometries answer bit-for-bit as before. */
  public void testPlainGeometriesUnchanged() throws Exception {
    Geometry p = read("LINESTRING (0 0, 2 3, 10 0)");
    Geometry q = read(BASELINE);
    assertEquals("plain polyline Hausdorff is the mid control's height, exactly",
        3.0, DistanceFunctions.orientedDiscreteHausdorffDistance(p, q), 0.0);
    assertEquals("plain nearest points", 3.0,
        DistanceFunctions.nearestPoints(p, read("POINT (2 6)")).getLength(), 0.0);
  }

  /**
   * A visual-QA session passed 10.0 as the densify fraction, reading the knob as
   * a distance like the tolerances on the neighbouring functions, and got core's
   * "Fraction is not in range (0.0 - 1.0]" -- correct, but naming neither the
   * parameter nor its meaning. The refusal must say what the knob is.
   */
  public void testDensifyFractionRefusalNamesTheContract() throws Exception {
    try {
      DistanceFunctions.orientedDiscreteHausdorffLineDensify(
          read(ARC), read(BASELINE), 10.0);
      fail("a fraction of 10.0 must be refused");
    }
    catch (IllegalArgumentException e) {
      assertTrue("message should say it is a fraction, not a distance: "
          + e.getMessage(),
          e.getMessage().contains("FRACTION") && e.getMessage().contains("10.0"));
    }
  }

  /**
   * Performance canary for the quadratic algorithm, asserted by vertex count
   * rather than wall clock so it cannot flake. At the operations tolerance a
   * radius-5 circle is ~1570 vertices and a curve-to-curve Frechet ran 20
   * seconds in visual QA; the quadratic sampler must keep the DP small. The
   * accuracy price is bounded by the sampling step (~0.2 here) and the
   * dominance assertions above still hold under it.
   */
  public void testQuadraticSamplerKeepsFrechetTractable() throws Exception {
    Geometry circle = read("CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)");
    int nOps = CurveFunctions.linearizeForOps(circle).getNumPoints();
    int nQuad = CurveFunctions.linearizeForQuadratic(circle).getNumPoints();
    assertTrue("ops sampling is dense by design, got " + nOps, nOps > 1000);
    assertTrue("quadratic sampling must stay tractable, got " + nQuad,
        nQuad < 400);
  }

  /** The projectOnLine cast died as a raw CCE on collection input; refuse clearly. */
  public void testClippedHausdorffRefusesNonLines() throws Exception {
    try {
      DistanceFunctions.clippedDirectedHausdorffLine(
          read("GEOMETRYCOLLECTION (POINT (0 0), LINESTRING (1 1, 2 2))"),
          read(BASELINE));
      fail("a GeometryCollection must be refused, not ClassCastException'd");
    }
    catch (IllegalArgumentException e) {
      assertTrue("message names the contract: " + e.getMessage(),
          e.getMessage().contains("LineString")
              && e.getMessage().contains("GeometryCollection"));
    }
  }

  /** Guard: identical curves are at distance zero however measured. */
  public void testIdenticalCurvesAtZero() throws Exception {
    assertEquals(0.0, DistanceFunctions.orientedDiscreteHausdorffDistance(
        read(ARC), read(ARC)), 1.0e-9);
    assertEquals(0.0, DistanceFunctions.frechetDistance(read(ARC), read(ARC)), 1.0e-9);
  }
}
