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

import java.util.Arrays;

import org.locationtech.jts.algorithm.construct.MaximumInscribedCircle;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
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
 * 15 warmups). Every row <em>was</em> the chord path:
 * <table border="1">
 * <caption>Red timings -- TestBuilder statics vs chord baseline</caption>
 * <tr><th>case</th><th>laser</th><th>chainsaw</th><th>ratio</th></tr>
 * <tr><td>Hausdorff two discs</td>   <td>29.215 ms</td><td>29.212 ms</td><td>1.00</td></tr>
 * <tr><td>Hausdorff arc-baseline</td><td>0.049 ms</td><td>0.049 ms</td><td>1.00</td></tr>
 * <tr><td>nearest arc-point</td>     <td>0.016 ms</td><td>0.012 ms</td><td>1.29</td></tr>
 * <tr><td>MIC disc</td>              <td>0.738 ms</td><td>0.386 ms</td><td>1.91</td></tr>
 * </table>
 * After the lasers (same harness): two discs 0.007 / 15.5 (0.000),
 * arc-baseline 0.009 / 0.063 (0.14), nearest 0.001 / 0.105 (0.01),
 * MIC disc 0.001 / 0.971 (0.001). Stadium MIC is the same 15% gate
 * against linearise-then-{@link MaximumInscribedCircle}. Fréchet and
 * LEC certified cells are gated next to the public classes.
 * <p>
 * Each row asserts {@code median(laser) <= median(chainsaw)}. A 15% slack
 * covers timer noise.
 */
public class DistanceConstructionPerfGateTest extends TestCase {

  private static final String DISC_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String DISC_3 =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";
  private static final String ARC = "CIRCULARSTRING (0 0, 2 3, 10 0)";
  private static final String BASELINE = "LINESTRING (0 0, 10 0)";
  private static final String APEX_POINT = "POINT (5 6)";
  private static final String STADIUM_FOUR =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, 0 -2, 1 -1), (1 -1, 1 6), CIRCULARSTRING (1 6, 0 7, -1 6), (-1 6, -1 -1)))";
  private static final String STADIUM_ODD =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 4, 0 5, 1 4), (1 4, 1 -1), CIRCULARSTRING (1 -1, 0 -2, -1 -1), (-1 -1, -1 4)))";
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";

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
    // A 0 ns chainsaw median is timer resolution, not a laser loss.
    if (cm == 0) return;
    double ratio = (double) lm / (double) cm;
    if (ratio > NOISE) {
      fail(label + ": laser " + (lm / 1.0e6) + " ms > chainsaw "
          + (cm / 1.0e6) + " ms (ratio " + ratio + " > " + NOISE + ")");
    }
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

  /** MIC of a radius-5 disc is 5, exactly -- not the grid approximation. */
  public void testMicDiscRadiusIsExact() throws Exception {
    Geometry disc = read(DISC_5);
    double r = ConstructionFunctions.maxInscribedCircleRadiusLen(disc, 0.01);
    assertEquals("largest circle inside a radius-5 disc", 5.0, r, 0.0);
    assertEquals(0x4014000000000000L, Double.doubleToRawLongBits(r));
    Point c = (Point) ConstructionFunctions.maxInscribedCircleCenter(disc, 0.01);
    assertEquals(0x0000000000000000L, Double.doubleToRawLongBits(c.getX()));
    assertEquals(0x0000000000000000L, Double.doubleToRawLongBits(c.getY()));
  }

  public void testMicStadiumNotSlowerThanChord() throws Exception {
    Geometry a = read(STADIUM_FOUR);
    assertLaserNotSlower("MIC stadium",
        () -> ConstructionFunctions.maxInscribedCircleRadiusLen(a, 0.01),
        () -> MaximumInscribedCircle.getRadiusLine(
            CurveFunctions.linearizeForOps(a), 0.01).getLength());
  }

  public void testMicStadiumFourIsClosedFormNotGrid() throws Exception {
    Geometry g = read(STADIUM_FOUR);
    assertEquals(1.0,
        ConstructionFunctions.maxInscribedCircleRadiusLen(g, 0.01), 0.0);
    Point c = (Point) ConstructionFunctions.maxInscribedCircleCenter(g, 0.01);
    assertEquals(0.0, c.getX(), 0.0);
    assertEquals(2.5, c.getY(), 0.0);
    Coordinate laser = CurveExactFns.micCenter(g);
    assertNotNull("stadium takes the closed form, not a densified grid", laser);
    double chordR = MaximumInscribedCircle.getRadiusLine(
        CurveFunctions.linearizeForOps(g), 0.01).getLength();
    assertTrue("chord MIC may be close; the laser is exactly 1",
        Math.abs(chordR - 1.0) < 0.05);
    assertEquals(1.0, laser.distance(new Coordinate(1.0, 2.5)), 0.0);
  }

  public void testMicStadiumOddIsClosedForm() throws Exception {
    Geometry g = read(STADIUM_ODD);
    assertEquals(1.0,
        ConstructionFunctions.maxInscribedCircleRadiusLen(g, 0.01), 0.0);
    Point c = (Point) ConstructionFunctions.maxInscribedCircleCenter(g, 0.01);
    assertEquals(0.0, c.getX(), 0.0);
    assertEquals(1.5, c.getY(), 0.0);
  }

  public void testMicHalfDiscIsNotAStadiumLaser() throws Exception {
    Geometry half = read(HALF_DISC);
    assertNull("HALF_DISC stamps -- chordsaw, not a claimed half-disc diamond",
        CurveExactFns.micRadius(half));
    assertNull(CurveExactFns.micCenter(half));
    assertNull(CurveExactFns.stadiumMic(half));
  }
}
