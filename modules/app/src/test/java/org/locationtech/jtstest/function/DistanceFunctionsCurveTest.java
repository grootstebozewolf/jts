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

  // -- multi-arc visual QA (TestBuilder, clipped directed Hausdorff) ------------

  /**
   * Multi-arc CircularString pair from TestBuilder visual QA:
   * <pre>
   *   A = CIRCULARSTRING (120 260, 310 370, 606 306, 970 330, 1000 410)   // 2 arcs
   *   B = CIRCULARSTRING (160 230, 290 320, 495 329, 650 230, 900 210,
   *                       940 210, 1000 300)                              // 3 arcs
   * </pre>
   * Two different directed-Hausdorff readings, both correct for their contract:
   * <ul>
   *   <li><b>clipped</b> {@code clippedDirectedHausdorffLine}: project A onto B,
   *       then DHD of the projection — mid-course gap
   *       {@code LINESTRING (927.71 279.65, 973.79 227.98)} length ≈ 69.23.</li>
   *   <li><b>full</b> {@code directedHausdorffLine}: max-min over the entire
   *       curves. A's free end {@code (1000 410)} is 110 from B's free end
   *       {@code (1000 300)}; continuous sampling agrees. That is not a bug —
   *       free ends dominate full DHD; use clipped for path-to-path matching.</li>
   * </ul>
   */
  public void testClippedDirectedHausdorffMultiArcVisualQA() throws Exception {
    Geometry a = multiArcA();
    Geometry b = multiArcB();
    Geometry line = DistanceFunctions.clippedDirectedHausdorffLine(a, b);

    assertEquals("clipped DHD is a 2-point realizing segment", 2, line.getNumPoints());
    // TestBuilder pin (25 ms run): length of the attaining pair.
    final double expectedClippedLen = 69.23113704161247;
    assertEquals("clipped directed Hausdorff length on multi-arc pair",
        expectedClippedLen, line.getLength(), 0.05);

    // Endpoints of the realizing segment (A-side sample, nearest on B).
    org.locationtech.jts.geom.Coordinate[] pts = line.getCoordinates();
    assertEquals(927.7146567580749, pts[0].x, 0.5);
    assertEquals(279.65144471735493, pts[0].y, 0.5);
    assertEquals(973.7932297797964, pts[1].x, 0.5);
    assertEquals(227.98215553745234, pts[1].y, 0.5);
  }

  /**
   * Full {@code directedHausdorffLine} on the multi-arc pair realises the free
   * ends {@code LINESTRING (1000 410, 1000 300)} of length 110 — continuous
   * directed Hausdorff agrees (radial foot from A's end onto B's last circle
   * is outside the arc, so nearest is B's endpoint). TestBuilder visual QA
   * flagged this as "wrong" when expecting path-to-path; it is correct full
   * DHD. Use {@link DistanceFunctions#clippedDirectedHausdorffLine} for the
   * mid-course ~69 reading.
   */
  public void testDirectedHausdorffLineMultiArcFreeEndDominates() throws Exception {
    Geometry a = multiArcA();
    Geometry b = multiArcB();
    Geometry line = DistanceFunctions.directedHausdorffLine(a, b);

    assertEquals(2, line.getNumPoints());
    assertEquals("full directed Hausdorff = free-end gap 110",
        110.0, line.getLength(), 0.05);

    org.locationtech.jts.geom.Coordinate[] pts = line.getCoordinates();
    assertEquals(1000.0, pts[0].x, 0.5);
    assertEquals(410.0, pts[0].y, 0.5);
    assertEquals(1000.0, pts[1].x, 0.5);
    assertEquals(300.0, pts[1].y, 0.5);

    // Full reading strictly larger than clipped path-to-path reading.
    double clipped = DistanceFunctions.clippedDirectedHausdorffLine(a, b).getLength();
    assertTrue("full DHD " + line.getLength() + " > clipped " + clipped,
        line.getLength() > clipped + 1.0);
  }

  private static Geometry multiArcA() throws Exception {
    return read("CIRCULARSTRING (120 260, 310 370, 606 306, 970 330, 1000 410)");
  }

  private static Geometry multiArcB() throws Exception {
    return read(
        "CIRCULARSTRING (160 230, 290 320, 495 329, 650 230, 900 210, 940 210, 1000 300)");
  }

  /**
   * {@code directedHausdorffLineTol(A,B,10)}: {@code distTol} is approximation
   * accuracy in map units, not a free-end clip. Full DHD still realises the free
   * ends at length 110 (continuous). A large tol must not densify at 1e-6 of
   * extent — linearise at the tol so the call stays cheap. Path-to-path mid-course
   * gap remains {@link DistanceFunctions#clippedDirectedHausdorffLine}.
   */
  public void testDirectedHausdorffLineTolMultiArcAccuracyNotClip() throws Exception {
    Geometry a = multiArcA();
    Geometry b = multiArcB();
    Geometry line = DistanceFunctions.directedHausdorffLineTol(a, b, 10.0);

    assertEquals(2, line.getNumPoints());
    assertEquals("tol form still reports full free-end DHD = 110 (not clipped ~69)",
        110.0, line.getLength(), 0.5);
    org.locationtech.jts.geom.Coordinate[] pts = line.getCoordinates();
    assertEquals(1000.0, pts[0].x, 1.0);
    assertEquals(410.0, pts[0].y, 1.0);
    assertEquals(1000.0, pts[1].x, 1.0);
    assertEquals(300.0, pts[1].y, 1.0);

    // Linearise-for-tol must be much coarser than ops densify on this ~880-unit
    // pair (ops would be ~1e-6 * extent vertices; tol=10 is a handful of chords).
    Geometry dense = CurveFunctions.linearizeForOps(a);
    Geometry coarse = CurveFunctions.linearizeForDistanceTol(a, 10.0);
    assertTrue("tol=10 linearise must be coarser than ops linearise: ops="
        + dense.getNumPoints() + " tol10=" + coarse.getNumPoints(),
        coarse.getNumPoints() < dense.getNumPoints() / 10
            || coarse.getNumPoints() < 50);
  }

  /** Negative accuracy is refused with a message that names the contract. */
  public void testDirectedHausdorffLineTolRefusesNegative() throws Exception {
    try {
      DistanceFunctions.directedHausdorffLineTol(multiArcA(), multiArcB(), -1.0);
      fail("negative accuracy must be refused");
    }
    catch (IllegalArgumentException e) {
      assertTrue("message names accuracy, not densify: " + e.getMessage(),
          e.getMessage().toLowerCase().contains("tolerance")
              || e.getMessage().toLowerCase().contains("non-negative"));
    }
  }

  // -- Fréchet free-end lower bound (TestBuilder visual QA) -------------------

  /**
   * Multi-arc path pair from TestBuilder (7 control points each = 3 arcs):
   * <pre>
   *   A = CIRCULARSTRING (120 330, 240 340, 280 410, 245 494, 344 496, 452 445, 520 460)
   *   B = CIRCULARSTRING (120 310, 250 330, 295 412, 330 450, 380 450, 440 420, 522 365)
   * </pre>
   * Two correct readings of the same pair:
   * <ul>
   *   <li><b>Fréchet</b> realises free ends {@code (520 460)–(522 365)} length ≈ 95.02
   *       (monotone coupling must finish at the ends).</li>
   *   <li><b>Clipped directed Hausdorff</b> realises mid-course
   *       {@code LINESTRING (258.99 510.45, 315.17 441.04)} length ≈ 89.30
   *       (project A onto B, then DHD of the projection).</li>
   * </ul>
   */
  public void testFrechetDistanceLineMultiArcFreeEndsDominate() throws Exception {
    Geometry a = multiArcPathA();
    Geometry b = multiArcPathB();

    double endEnd = a.getCoordinates()[a.getNumPoints() - 1]
        .distance(b.getCoordinates()[b.getNumPoints() - 1]);
    assertEquals(95.02105029939419, endEnd, 1e-6);

    Geometry leash = DistanceFunctions.frechetDistanceLine(a, b);
    assertEquals(2, leash.getNumPoints());
    // Fréchet ≥ end-end; on this pair the max leash is exactly the free ends.
    assertEquals("Fréchet leash length = free-end gap", endEnd, leash.getLength(), 0.5);

    org.locationtech.jts.geom.Coordinate[] pts = leash.getCoordinates();
    assertEquals(520.0, pts[0].x, 1.0);
    assertEquals(460.0, pts[0].y, 1.0);
    assertEquals(522.0, pts[1].x, 1.0);
    assertEquals(365.0, pts[1].y, 1.0);

    double frechet = DistanceFunctions.frechetDistance(a, b);
    assertEquals(endEnd, frechet, 0.5);
    assertTrue("Fréchet " + frechet + " must be >= end-end " + endEnd,
        frechet + 1e-6 >= endEnd);
  }

  /**
   * Same multi-arc path pair: {@code clippedDirectedHausdorffLine} mid-course
   * pin from TestBuilder (29 ms). Realising segment
   * {@code LINESTRING (258.987 510.451, 315.169 441.041)} length ≈ 89.298 —
   * the path-to-path gap Fréchet free-ends do not report.
   */
  public void testClippedDirectedHausdorffMultiArcPathVisualQA() throws Exception {
    Geometry a = multiArcPathA();
    Geometry b = multiArcPathB();
    Geometry line = DistanceFunctions.clippedDirectedHausdorffLine(a, b);

    assertEquals(2, line.getNumPoints());
    final double expectedLen = 89.2982753626349;
    assertEquals("clipped directed Hausdorff mid-course gap",
        expectedLen, line.getLength(), 0.5);

    org.locationtech.jts.geom.Coordinate[] pts = line.getCoordinates();
    assertEquals(258.98693364714956, pts[0].x, 1.0);
    assertEquals(510.45121476129685, pts[0].y, 1.0);
    assertEquals(315.1689111441109, pts[1].x, 1.0);
    assertEquals(441.0410758241654, pts[1].y, 1.0);

    // Distinct from Fréchet free-end pair (ends of A/B).
    assertTrue("clipped realizing point is not A's free end",
        pts[0].distance(a.getCoordinates()[a.getNumPoints() - 1]) > 50.0);
  }

  private static Geometry multiArcPathA() throws Exception {
    return read(
        "CIRCULARSTRING (120 330, 240 340, 280 410, 245 494, 344 496, 452 445, 520 460)");
  }

  private static Geometry multiArcPathB() throws Exception {
    return read(
        "CIRCULARSTRING (120 310, 250 330, 295 412, 330 450, 380 450, 440 420, 522 365)");
  }
}
