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
package org.locationtech.jts.operation.overlayng.curve;

import java.util.Arrays;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurveOps;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * PERF-GATE: the curve path (laser) may run only when it is no slower than
 * the locationtech/jts chord baseline (chainsaw): linearise at
 * {@link CurveOps#TOLERANCE_FRACTION}, then the equivalent core algorithm.
 * <p>
 * Measured on this branch before the gate (surefire, OpenJDK 21):
 * <table border="1">
 * <caption>Red timings -- OverlayNGCurve vs chord overlay</caption>
 * <tr><th>case</th><th>laser</th><th>chainsaw</th><th>ratio</th></tr>
 * <tr><td>disjoint CAP</td>  <td>4.169 ms</td><td>0.139 ms</td><td>30.0</td></tr>
 * <tr><td>nested CAP</td>    <td>7.894 ms</td><td>0.646 ms</td><td>12.2</td></tr>
 * <tr><td>nested CUP</td>    <td>7.914 ms</td><td>0.563 ms</td><td>14.1</td></tr>
 * <tr><td>crossing CAP</td>  <td>3.712 ms</td><td>0.339 ms</td><td>11.0</td></tr>
 * </table>
 * Algebra (self CAP, empty CUP) already won in the same run. The four
 * failures above are the claim.
 * Algebra (self / empty) already wins. Retention loses because it densifies at
 * the fine ops tolerance, then pays {@code relate} plus boundary-distance on
 * ~1570-vertex rings -- and on a crossing pair still falls through to the same
 * overlay the chainsaw ran alone. That is the ratchet taking the laser when
 * the laser is the slower tool.
 * <p>
 * Predicates and constructions that already <em>are</em> the chord path
 * (densify, then core) stay at ratio ~1; the gate must not add work there.
 * Envelope-decidable predicates (a far point, a far neighbour) must beat the
 * densified call.
 * <p>
 * Each row asserts {@code median(laser) <= median(chainsaw)}. A 15% slack
 * covers timer noise on the equal-cost rows; a ratio above that fails the
 * build. The numbers in the assertion message are the medians just measured.
 */
public class OverlayNGCurvePerfGateTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_3 =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";
  private static final String CIRCLE_FAR =
      "CURVEPOLYGON (CIRCULARSTRING (100 0, 105 5, 110 0, 105 -5, 100 0))";
  private static final String CIRCLE_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  private static final String EMPTY = "CURVEPOLYGON EMPTY";
  private static final String POINT_INSIDE = "POINT (3 3)";
  private static final String POINT_FAR = "POINT (100 100)";

  private static final int WARMUP = 15;
  private static final int SAMPLES = 31;
  /**
   * Timer-noise budget on rows that should be the same work (crossing overlay
   * after the gate refuses retention; constructions that already are the
   * chord path). Algebra and envelope rows land far below 1.0 and do not
   * spend this.
   */
  private static final double NOISE = 1.15;

  public static void main(String[] args) {
    TestRunner.run(OverlayNGCurvePerfGateTest.class);
  }

  public OverlayNGCurvePerfGateTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static Geometry chordOverlay(Geometry a, Geometry b, int opCode) {
    return OverlayNGRobust.overlay(CurveOps.linearise(a), CurveOps.linearise(b),
        opCode);
  }

  private static long median(long[] ns) {
    long[] copy = ns.clone();
    Arrays.sort(copy);
    return copy[copy.length / 2];
  }

  /**
   * Times both paths and fails if the laser's median exceeds the chainsaw's
   * by more than {@link #NOISE}.
   */
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

  // -- overlay: algebra already wins; retention is the red -----------------

  public void testOverlaySelfCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    assertLaserNotSlower("self CAP",
        () -> OverlayNGCurve.intersection(a, a),
        () -> chordOverlay(a, a, OverlayNGCurve.INTERSECTION));
  }

  public void testOverlayEmptyCupNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry empty = readCurve(EMPTY);
    assertLaserNotSlower("empty CUP",
        () -> OverlayNGCurve.union(a, empty),
        () -> chordOverlay(a, empty, OverlayNGCurve.UNION));
  }

  public void testOverlayDisjointCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry far = readCurve(CIRCLE_FAR);
    assertLaserNotSlower("disjoint CAP",
        () -> OverlayNGCurve.intersection(a, far),
        () -> chordOverlay(a, far, OverlayNGCurve.INTERSECTION));
  }

  public void testOverlayNestedCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_3);
    assertLaserNotSlower("nested CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testOverlayNestedCupNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_3);
    assertLaserNotSlower("nested CUP",
        () -> OverlayNGCurve.union(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.UNION));
  }

  public void testOverlayCrossingCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("crossing CAP",
        () -> OverlayNGCurve.intersection(a, cross),
        () -> chordOverlay(a, cross, OverlayNGCurve.INTERSECTION));
  }

  // -- predicates / distance / constructions --------------------------------

  public void testContainsInsideNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry p = readCurve(POINT_INSIDE);
    assertLaserNotSlower("contains inside",
        () -> a.contains(p),
        () -> CurveOps.linearise(a).contains(p));
  }

  public void testContainsFarNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry p = readCurve(POINT_FAR);
    assertLaserNotSlower("contains far",
        () -> a.contains(p),
        () -> CurveOps.linearise(a).contains(p));
  }

  public void testDisjointFarNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry far = readCurve(CIRCLE_FAR);
    assertLaserNotSlower("disjoint far",
        () -> a.disjoint(far),
        () -> CurveOps.linearise(a).disjoint(CurveOps.linearise(far)));
  }

  public void testIntersectsCrossingNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("intersects crossing",
        () -> a.intersects(cross),
        () -> CurveOps.linearise(a).intersects(CurveOps.linearise(cross)));
  }

  public void testDistanceFarNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry far = readCurve(CIRCLE_FAR);
    assertLaserNotSlower("distance far",
        () -> a.distance(far),
        () -> CurveOps.linearise(a).distance(CurveOps.linearise(far)));
  }

  public void testWithinDistanceFarNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry far = readCurve(CIRCLE_FAR);
    assertLaserNotSlower("isWithinDistance far",
        () -> a.isWithinDistance(far, 1.0),
        () -> CurveOps.linearise(a).isWithinDistance(CurveOps.linearise(far), 1.0));
  }

  public void testConvexHullNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    assertLaserNotSlower("convexHull",
        () -> a.convexHull(),
        () -> CurveOps.linearise(a).convexHull());
  }
}
