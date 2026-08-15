/*
 * Copyright (c) 2026 grootstebozewolf
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
    10| * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jtstest.function;

import java.util.Arrays;

import org.locationtech.jts.algorithm.construct.LargestEmptyCircle;
import org.locationtech.jts.algorithm.construct.MaximumInscribedCircle;
import org.locationtech.jts.algorithm.distance.DiscreteFrechetDistance;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.distance.DistanceOp;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * PERF-GATE for the TestBuilder distance family and MIC/LEC: a curve-aware
 * path may run only when it is no slower than linearise-at-ops-tolerance,
 * then the equivalent core static.
 * <p>
 * These entry points are not OverlayNGCurve and not {@code CurveOps}. The
 * gate lives on the caller-side shims that already linearise
 * ({@link DistanceFunctions}, {@link ConstructionFunctions}).
 * <p>
 * Measured on this branch before the lasers (OpenJDK 21, median of 31 after
 * 15 warmups). Every row <em>is</em> the chord path:
 * <table border="1">
 * <caption>Red timings -- TestBuilder statics vs chord baseline</caption>
 * <tr><th>case</th><th>laser</th><th>chainsaw</th><th>ratio</th></tr>
 * <tr><td>Hausdorff two discs</td>   <td>29.215 ms</td><td>29.212 ms</td><td>1.00</td></tr>
 * <tr><td>Hausdorff arc-baseline</td><td>0.049 ms</td><td>0.049 ms</td><td>1.00</td></tr>
 * <tr><td>nearest arc-point</td>     <td>0.016 ms</td><td>0.012 ms</td><td>1.29</td></tr>
 * <tr><td>MIC disc</td>              <td>0.738 ms</td><td>0.386 ms</td><td>1.91</td></tr>
 * <tr><td>LEC circle-in-box</td>     <td>0.363 ms</td><td>0.255 ms</td><td>1.42</td></tr>
 * <tr><td>Frechet arc-baseline</td>  <td>0.518 ms</td><td>0.441 ms</td><td>1.17</td></tr>
 * </table>
 * Frechet and LEC stay on the chords -- no closed form that beats the
 * sampled path was measured. The other rows must drop below 1.0 once a
 * laser is wired, and must never sit more than 15% above the chainsaw.
 */
public class DistanceConstructionPerfGateTest extends TestCase {

  private static final String DISC_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String DISC_3 =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";
  private static final String ARC = "CIRCULARSTRING (0 0, 2 3, 10 0)";
  private static final String BASELINE = "LINESTRING (0 0, 10 0)";
  private static final String APEX_POINT = "POINT (5 6)";
  private static final String OBSTACLES =
      "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)";
  private static final String BOX =
      "POLYGON ((-4 -4, 4 -4, 4 4, -4 4, -4 -4))";

  private static final int WARMUP = 15;
  private static final int SAMPLES = 31;
  private static final double NOISE = 1.15;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(DistanceConstructionPerfGateTest.class); }
  public DistanceConstructionPerfGateTest(String name) { super(name); }

  private static Geometry read(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static long median(long[] ns) {
    long[] copy = ns.clone();
    Arrays.sort(copy);
    return copy[copy.length / 2];
  }

  private void assertLaserNotSlower(String label, Runnable laser, Runnable chainsaw) {
    for (int i = 0; i < WARMUP; i++) {
      laser.run();
      chainsaw.run();
    }
    long[] L = new long[SAMPLES];
    long[] C = new long[SAMPLES];
    for (int i = 0; i < SAMPLES; i++) {
      long t0 = System.nanoTime();
      laser.run();
      L[i] = System.nanoTime() - t0;
      long t1 = System.nanoTime();
      chainsaw.run();
      C[i] = System.nanoTime() - t1;
    }
    long lm = median(L);
    long cm = median(C);
    double ratio = cm == 0 ? (lm == 0 ? 0.0 : Double.POSITIVE_INFINITY)
        : (double) lm / (double) cm;
    assertTrue(label + ": laser " + (lm / 1.0e6) + " ms > chainsaw "
        + (cm / 1.0e6) + " ms (ratio " + ratio + " > " + NOISE + ")",
        ratio <= NOISE);
  }

  public void testHausdorffTwoDiscsNotSlowerThanChord() throws Exception {
    Geometry a = read(DISC_5);
    Geometry b = read(DISC_3);
    assertLaserNotSlower("Hausdorff two discs",
        () -> DistanceFunctions.orientedDiscreteHausdorffDistance(a, b),
        () -> DiscreteHausdorffDistance.orientedDistance(
            CurveFunctions.linearizeForOps(a), CurveFunctions.linearizeForOps(b)));
  }

  public void testHausdorffArcBaselineNotSlowerThanChord() throws Exception {
    Geometry a = read(ARC);
    Geometry b = read(BASELINE);
    assertLaserNotSlower("Hausdorff arc-baseline",
        () -> DistanceFunctions.orientedDiscreteHausdorffDistance(a, b),
        () -> DiscreteHausdorffDistance.orientedDistance(
            CurveFunctions.linearizeForOps(a), CurveFunctions.linearizeForOps(b)));
  }

  public void testNearestArcPointNotSlowerThanChord() throws Exception {
    Geometry a = read(ARC);
    Geometry p = read(APEX_POINT);
    assertLaserNotSlower("nearest arc-point",
        () -> DistanceFunctions.nearestPoints(a, p),
        () -> DistanceOp.nearestPoints(CurveFunctions.linearizeForOps(a), p));
  }

  public void testMicDiscNotSlowerThanChord() throws Exception {
    Geometry a = read(DISC_5);
    assertLaserNotSlower("MIC disc",
        () -> ConstructionFunctions.maxInscribedCircleRadiusLen(a, 0.01),
        () -> MaximumInscribedCircle.getRadiusLine(
            CurveFunctions.linearizeForOps(a), 0.01).getLength());
  }

  public void testLecStaysOnChordPath() throws Exception {
    Geometry obstacles = read(OBSTACLES);
    Geometry boundary = read(BOX);
    assertLaserNotSlower("LEC circle-in-box",
        () -> ConstructionFunctions.largestEmptyCircleRadius(obstacles, boundary, 0.01),
        () -> LargestEmptyCircle.getRadiusLine(
            CurveFunctions.linearizeForOps(obstacles), boundary, 0.01));
  }

  public void testFrechetStaysOnChordPath() throws Exception {
    Geometry a = read(ARC);
    Geometry b = read(BASELINE);
    assertLaserNotSlower("Frechet arc-baseline",
        () -> DistanceFunctions.frechetDistance(a, b),
        () -> DiscreteFrechetDistance.distance(
            CurveFunctions.linearizeForQuadratic(a),
            CurveFunctions.linearizeForQuadratic(b)));
  }

  /** MIC of a radius-5 disc is 5, exactly -- not the grid approximation. */
  public void testMicDiscRadiusIsExact() throws Exception {
    assertEquals("largest circle inside a radius-5 disc",
        5.0, ConstructionFunctions.maxInscribedCircleRadiusLen(read(DISC_5), 0.01),
        1.0e-12);
  }
}
