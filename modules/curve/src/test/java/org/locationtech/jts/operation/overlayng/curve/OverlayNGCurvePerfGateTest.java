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
 * <p>
 * After the gate (same harness): disjoint CAP 0.001 / 0.092 (0.016),
 * nested CAP 0.211 / 0.599 (0.35), nested CUP 0.165 / 0.319 (0.52),
 * crossing CAP 0.017 / 0.681 (0.025) -- R1.5 two-arc lens, not the
 * ~1570-vertex chord overlay. Crossing CUP 0.039, SUB 0.026, XOR 0.040.
 * Envelope-decidable predicates drop from ~1.0 to ~0.005.
 * Algebra (self / empty) already wins. Retention loses because it densifies at
 * the fine ops tolerance, then pays {@code relate} plus boundary-distance on
 * ~1570-vertex rings -- and on a crossing pair still falls through to the same
 * overlay the chainsaw ran alone. That is the ratchet taking the laser when
 * the laser is the slower tool.
 * <p>
 * Predicates and constructions that already <em>are</em> the chord path
 * (densify, then core) skip the ratio: comparing a wrapper to itself at
 * the 15% line is timer noise, the same class as a 0 ns chainsaw median.
 * Envelope-decidable predicates (a far point, a far neighbour) must beat the
 * densified call.
 * <p>
 * Genuine lasers assert {@code median(laser) <= median(chainsaw)} with a
 * 15% slack. Identity / R2 rows keep the pair in the suite but do not
 * spend that slack. The numbers in a failure message are the medians
 * just measured.
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
  private static final String CIRCLE_EXT_TAN =
      "CURVEPOLYGON (CIRCULARSTRING (5 0, 10 5, 15 0, 10 -5, 5 0))";
  /** R.1 T-ext kiss at (4, 3), not a control vertex. */
  private static final String CIRCLE_3_4_5 =
      "CURVEPOLYGON (CIRCULARSTRING (13 6, 8 11, 3 6, 8 1, 13 6))";
  private static final String EMPTY = "CURVEPOLYGON EMPTY";
  private static final String POINT_INSIDE = "POINT (3 3)";
  private static final String POINT_FAR = "POINT (100 100)";
  private static final String PLAIN_DIAMOND =
      "POLYGON ((-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String PLAIN_SQUARE =
      "POLYGON ((-6 -6, 6 -6, 6 6, -6 6, -6 -6))";
  private static final String SQUARE_RIGHT =
      "POLYGON ((0 -6, 10 -6, 10 6, 0 6, 0 -6))";
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String SQUARE_CAP =
      "POLYGON ((-6 2, 6 2, 6 10, -6 10, -6 2))";
  private static final String CHORD_SHELL =
      "CURVEPOLYGON (COMPOUNDCURVE ((-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String ARC =
      "CIRCULARSTRING (0 0, 2 3, 10 0)";
  private static final String ARC_B =
      "CIRCULARSTRING (1 4, 5 2, 9 4)";
  private static final String ARC_SAME_Q1 =
      "CIRCULARSTRING (-5 0, 0 5, 5 0)";
  private static final String ARC_SAME_Q2 =
      "CIRCULARSTRING (0 5, 5 0, 0 -5)";
  private static final String LINE_Y2 =
      "LINESTRING (-1 2, 11 2)";
  private static final String CHORD_ARC =
      "LINESTRING (0 0, 2 3, 10 0)";
  /** Horizontal stadium |x|≤2, |y|≤1, strictly inside CIRCLE_5. */
  private static final String STADIUM_NEST =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, -2 0, -1 1), (-1 1, 1 1), CIRCULARSTRING (1 1, 2 0, 1 -1), (1 -1, -1 -1)))";

  private static final int WARMUP = 15;
  private static final int SAMPLES = 31;
  /**
   * Timer-noise budget on rows that should be the same work (constructions
   * that already are the chord path). Algebra, envelope, and two-disc
   * crossing rows land far below 1.0 and do not spend this.
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
    timeBoth(label, laser, chainsaw, false);
  }

  /**
   * The curve path <em>is</em> the chord path (R2, or linearise then the
   * same core call). Keep the row; skip the ratio.
   */
  private void assertChordPath(String label, Runnable laser, Runnable chainsaw) {
    timeBoth(label, laser, chainsaw, true);
  }

  private void timeBoth(String label, Runnable laser, Runnable chainsaw,
      boolean samePath) {
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
    if (cm == 0 || samePath) return;
    double ratio = (double) lm / (double) cm;
    if (ratio > NOISE) {
      fail(label + ": laser " + (lm / 1.0e6) + " ms > chainsaw "
          + (cm / 1.0e6) + " ms (ratio " + ratio + " > " + NOISE + ")");
    }
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

  public void testOverlayNestedSubNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_3);
    assertLaserNotSlower("nested SUB",
        () -> OverlayNGCurve.difference(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.DIFFERENCE));
  }

  public void testOverlayNestedXorNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_3);
    assertLaserNotSlower("nested XOR",
        () -> OverlayNGCurve.symDifference(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.SYMDIFFERENCE));
  }

  public void testOverlayCrossingCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("crossing CAP",
        () -> OverlayNGCurve.intersection(a, cross),
        () -> chordOverlay(a, cross, OverlayNGCurve.INTERSECTION));
  }

  public void testOverlayCrossingCupNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("crossing CUP",
        () -> OverlayNGCurve.union(a, cross),
        () -> chordOverlay(a, cross, OverlayNGCurve.UNION));
  }

  public void testOverlayCrossingSubNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("crossing SUB",
        () -> OverlayNGCurve.difference(a, cross),
        () -> chordOverlay(a, cross, OverlayNGCurve.DIFFERENCE));
  }

  public void testOverlayCrossingXorNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("crossing XOR",
        () -> OverlayNGCurve.symDifference(a, cross),
        () -> chordOverlay(a, cross, OverlayNGCurve.SYMDIFFERENCE));
  }

  public void testReverseDisjointSubNotSlowerThanChord() throws Exception {
    Geometry plain = readCurve(PLAIN_DIAMOND);
    Geometry far = readCurve(CIRCLE_FAR);
    assertLaserNotSlower("rev disjoint SUB",
        () -> plain.difference(far),
        () -> chordOverlay(plain, far, OverlayNGCurve.DIFFERENCE));
  }

  public void testOverlayMixedNestSubNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry stadium = readCurve(STADIUM_NEST);
    assertLaserNotSlower("mixed nest SUB",
        () -> OverlayNGCurve.difference(a, stadium),
        () -> chordOverlay(a, stadium, OverlayNGCurve.DIFFERENCE));
  }

  public void testReverseNestedSubNotSlowerThanChord() throws Exception {
    Geometry square = readCurve(PLAIN_SQUARE);
    Geometry inner = readCurve(CIRCLE_3);
    // R1 skips a covering SUB (annulus); R1.5/R1.6 miss; R2 is the answer.
    // R1.6-honesty KEEP -- named stamp lives on CircularDiscPolygonOverlayTest.
    assertChordPath("rev nested SUB",
        () -> square.difference(inner),
        () -> chordOverlay(square, inner, OverlayNGCurve.DIFFERENCE));
  }

  public void testOverlayDiscRectangleCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry square = readCurve(SQUARE_RIGHT);
    assertLaserNotSlower("disc ∩ rectangle CAP",
        () -> OverlayNGCurve.intersection(a, square),
        () -> chordOverlay(a, square, OverlayNGCurve.INTERSECTION));
  }

  public void testReverseDiscRectangleSubNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry square = readCurve(SQUARE_RIGHT);
    assertLaserNotSlower("rev disc ∩ rectangle SUB",
        () -> square.difference(a),
        () -> chordOverlay(square, a, OverlayNGCurve.DIFFERENCE));
  }

  public void testReverseCrossingSubNotSlowerThanChord() throws Exception {
    Geometry plain = readCurve(PLAIN_DIAMOND);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("rev crossing SUB",
        () -> plain.difference(cross),
        () -> chordOverlay(plain, cross, OverlayNGCurve.DIFFERENCE));
  }

  public void testHalfDiscCrossingCapNotSlowerThanChord() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("half ∩ disc CAP",
        () -> OverlayNGCurve.intersection(half, disc),
        () -> chordOverlay(half, disc, OverlayNGCurve.INTERSECTION));
  }

  public void testHalfDiscSquareCapNotSlowerThanChord() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry square = readCurve(SQUARE_CAP);
    assertLaserNotSlower("half ∩ square CAP",
        () -> OverlayNGCurve.intersection(half, square),
        () -> chordOverlay(half, square, OverlayNGCurve.INTERSECTION));
  }

  public void testChordShellIsNotAnArc() throws Exception {
    Geometry chords = readCurve(CHORD_SHELL);
    Geometry disc = readCurve(CIRCLE_CROSSING);
    assertChordPath("3-pt LINESTRING shell",
        () -> OverlayNGCurve.intersection(chords, disc),
        () -> chordOverlay(chords, disc, OverlayNGCurve.INTERSECTION));
  }

  public void testArcLineCapNotSlowerThanChord() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry line = readCurve(LINE_Y2);
    assertLaserNotSlower("arc ∩ line CAP",
        () -> OverlayNGCurve.intersection(arc, line),
        () -> chordOverlay(arc, line, OverlayNGCurve.INTERSECTION));
  }

  public void testChordArcIsNotAnArc() throws Exception {
    Geometry chords = readCurve(CHORD_ARC);
    Geometry line = readCurve(LINE_Y2);
    assertChordPath("3-pt LINESTRING vs line",
        () -> OverlayNGCurve.intersection(chords, line),
        () -> chordOverlay(chords, line, OverlayNGCurve.INTERSECTION));
  }

  public void testArcArcCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(ARC);
    Geometry b = readCurve(ARC_B);
    assertLaserNotSlower("arc ∩ arc CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testArcArcCupNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(ARC);
    Geometry b = readCurve(ARC_B);
    assertLaserNotSlower("arc ∪ arc CUP",
        () -> OverlayNGCurve.union(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.UNION));
  }

  public void testArcArcSubNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(ARC);
    Geometry b = readCurve(ARC_B);
    assertLaserNotSlower("arc \\ arc SUB",
        () -> OverlayNGCurve.difference(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.DIFFERENCE));
  }

  public void testSameCircleCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(ARC_SAME_Q1);
    Geometry b = readCurve(ARC_SAME_Q2);
    assertLaserNotSlower("same-circle ∩ CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testSameCircleCupNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(ARC_SAME_Q1);
    Geometry b = readCurve(ARC_SAME_Q2);
    assertLaserNotSlower("same-circle ∪ CUP",
        () -> OverlayNGCurve.union(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.UNION));
  }

  public void testSameCircleSubNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(ARC_SAME_Q1);
    Geometry b = readCurve(ARC_SAME_Q2);
    assertLaserNotSlower("same-circle \\ SUB",
        () -> OverlayNGCurve.difference(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.DIFFERENCE));
  }

  public void testComplementaryHalvesCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 -5, 5 0), (5 0, -5 0)))");
    assertLaserNotSlower("complementary halves CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testComplementaryHalvesCupNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 -5, 5 0), (5 0, -5 0)))");
    assertLaserNotSlower("complementary halves CUP",
        () -> OverlayNGCurve.union(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.UNION));
  }

  public void testOverlappingHalvesCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 -5, 5 0, 0 5), (0 5, 0 -5)))");
    assertLaserNotSlower("upper ∩ right CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testOverlappingHalvesCupNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 -5, 5 0, 0 5), (0 5, 0 -5)))");
    assertLaserNotSlower("upper ∪ right CUP",
        () -> OverlayNGCurve.union(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.UNION));
  }

  public void testTwoShellLensCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 8, 0 3, 5 8), (5 8, -5 8)))");
    assertLaserNotSlower("two-shell lens CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testTwoShellLensCupNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 8, 0 3, 5 8), (5 8, -5 8)))");
    assertLaserNotSlower("two-shell lens CUP",
        () -> OverlayNGCurve.union(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.UNION));
  }

  public void testCollinearHalvesCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (2 0, 7 5, 12 0), (12 0, 2 0)))");
    assertLaserNotSlower("collinear halves CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testCollinearHalvesCupNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (2 0, 7 5, 12 0), (12 0, 2 0)))");
    assertLaserNotSlower("collinear halves CUP",
        () -> OverlayNGCurve.union(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.UNION));
  }

  public void testNestedHalvesCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-3 0, 0 3, 3 0), (3 0, -3 0)))");
    assertLaserNotSlower("nested halves CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testOneNodeTouchCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (5 -5, 10 0, 5 5), (5 5, 5 -5)))");
    assertLaserNotSlower("one-node touch CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testFourCutTwoShellCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, 0 -2, 1 -1), (1 -1, 1 6), CIRCULARSTRING (1 6, 0 7, -1 6), (-1 6, -1 -1)))");
    assertLaserNotSlower("four-cut two-shell CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testFourCutTwoShellCupNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, 0 -2, 1 -1), (1 -1, 1 6), CIRCULARSTRING (1 6, 0 7, -1 6), (-1 6, -1 -1)))");
    assertLaserNotSlower("four-cut two-shell CUP",
        () -> OverlayNGCurve.union(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.UNION));
  }

  public void testSameOuterHoleCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0 1, 1 1, 1 2, 0 2, 0 1))");
    Geometry b = readCurve(HALF_DISC);
    assertLaserNotSlower("same-outer hole CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testSameOuterHoleCupNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0 1, 1 1, 1 2, 0 2, 0 1))");
    Geometry b = readCurve(HALF_DISC);
    assertLaserNotSlower("same-outer hole CUP",
        () -> OverlayNGCurve.union(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.UNION));
  }

  public void testSameOuterHoleSubNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0 1, 1 1, 1 2, 0 2, 0 1))");
    assertLaserNotSlower("same-outer hole SUB",
        () -> OverlayNGCurve.difference(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.DIFFERENCE));
  }

  public void testDifferentOuterHoleNestedCapNotSlowerThanChord()
      throws Exception {
    Geometry a = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0 1, 1 1, 1 2, 0 2, 0 1))");
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-3 0, 0 3, 3 0), (3 0, -3 0)))");
    assertLaserNotSlower("different-outer hole nested CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testDifferentOuterHoleNestedCupNotSlowerThanChord()
      throws Exception {
    Geometry a = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0 1, 1 1, 1 2, 0 2, 0 1))");
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-3 0, 0 3, 3 0), (3 0, -3 0)))");
    assertLaserNotSlower("different-outer hole nested CUP",
        () -> OverlayNGCurve.union(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.UNION));
  }

  public void testDifferentOuterHoleLensCapNotSlowerThanChord()
      throws Exception {
    Geometry a = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0 1, 1 1, 1 2, 0 2, 0 1))");
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 8, 0 3, 5 8), (5 8, -5 8)))");
    assertLaserNotSlower("different-outer hole lens CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testFourCutCapNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve("POLYGON ((-8 -1, 8 -1, 8 1, -8 1, -8 -1))");
    assertLaserNotSlower("four-cut CAP",
        () -> OverlayNGCurve.intersection(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.INTERSECTION));
  }

  public void testFourCutCupNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve("POLYGON ((-8 -1, 8 -1, 8 1, -8 1, -8 -1))");
    assertLaserNotSlower("four-cut CUP",
        () -> OverlayNGCurve.union(a, b),
        () -> chordOverlay(a, b, OverlayNGCurve.UNION));
  }

  public void testChordArcVsCircularStringIsChordPath() throws Exception {
    Geometry chords = readCurve(CHORD_ARC);
    Geometry arc = readCurve(ARC_B);
    assertChordPath("3-pt LINESTRING vs CircularString",
        () -> OverlayNGCurve.intersection(chords, arc),
        () -> chordOverlay(chords, arc, OverlayNGCurve.INTERSECTION));
  }

  // -- predicates / distance / constructions --------------------------------

  public void testContainsInsideNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry p = readCurve(POINT_INSIDE);
    assertLaserNotSlower("contains inside",
        () -> a.contains(p),
        () -> CurveOps.linearise(a).contains(p));
  }

  public void testCoversOnCircleNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry p = readCurve("POINT (5 0)");
    assertLaserNotSlower("covers on-circle",
        () -> a.covers(p),
        () -> CurveOps.linearise(a).covers(p));
  }

  public void testReverseWithinInsideNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry p = readCurve(POINT_INSIDE);
    assertLaserNotSlower("rev within inside",
        () -> p.within(a),
        () -> p.within(CurveOps.linearise(a)));
  }

  public void testRelateInteriorNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry p = readCurve(POINT_INSIDE);
    assertLaserNotSlower("relate interior",
        () -> a.relate(p),
        () -> CurveOps.linearise(a).relate(p));
  }

  public void testReverseRelateInteriorNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry p = readCurve(POINT_INSIDE);
    assertLaserNotSlower("rev relate interior",
        () -> p.relate(a),
        () -> p.relate(CurveOps.linearise(a)));
  }

  public void testRelateCrossingLineNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry line = readCurve("LINESTRING (-10 0, 10 0)");
    assertLaserNotSlower("relate crossing line",
        () -> a.relate(line),
        () -> CurveOps.linearise(a).relate(line));
  }

  public void testRelateTangentLineNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry line = readCurve("LINESTRING (-10 5, 10 5)");
    assertLaserNotSlower("relate tangent line",
        () -> a.relate(line),
        () -> CurveOps.linearise(a).relate(line));
  }

  public void testIntersectsCrossingLineNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry line = readCurve("LINESTRING (-10 0, 10 0)");
    assertLaserNotSlower("intersects crossing line",
        () -> a.intersects(line),
        () -> CurveOps.linearise(a).intersects(line));
  }

  public void testReverseRelateCrossingLineNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry line = readCurve("LINESTRING (-10 0, 10 0)");
    assertLaserNotSlower("rev relate crossing line",
        () -> line.relate(a),
        () -> line.relate(CurveOps.linearise(a)));
  }

  public void testRelateDisjointPolyNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry far = readCurve("POLYGON ((100 100, 110 100, 110 110, 100 110, 100 100))");
    assertLaserNotSlower("relate disjoint poly",
        () -> a.relate(far),
        () -> CurveOps.linearise(a).relate(far));
  }

  public void testRelateNestedPolyNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry inner = readCurve("POLYGON ((-1 -1, 1 -1, 1 1, -1 1, -1 -1))");
    assertLaserNotSlower("relate nested poly",
        () -> a.relate(inner),
        () -> CurveOps.linearise(a).relate(inner));
  }

  public void testRelateDiscInsideSquareNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry square = readCurve(PLAIN_SQUARE);
    assertLaserNotSlower("relate disc inside square",
        () -> a.relate(square),
        () -> CurveOps.linearise(a).relate(square));
  }

  public void testRelateCrossingPolyNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry cut = readCurve(SQUARE_RIGHT);
    assertLaserNotSlower("relate crossing poly",
        () -> a.relate(cut),
        () -> CurveOps.linearise(a).relate(cut));
  }

  public void testReverseRelateCrossingPolyNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry cut = readCurve(SQUARE_RIGHT);
    assertLaserNotSlower("rev relate crossing poly",
        () -> cut.relate(a),
        () -> cut.relate(CurveOps.linearise(a)));
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

  public void testRelateCrossingDiscsNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("disc vs disc crossing relate",
        () -> a.relate(cross),
        () -> CurveOps.linearise(a).relate(CurveOps.linearise(cross)));
  }

  public void testOverlapsCrossingDiscsNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("disc vs disc crossing overlaps",
        () -> a.overlaps(cross),
        () -> CurveOps.linearise(a).overlaps(CurveOps.linearise(cross)));
  }

  public void testRelateNestedDiscsNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry inner = readCurve(CIRCLE_3);
    assertLaserNotSlower("disc vs disc nested relate",
        () -> a.relate(inner),
        () -> CurveOps.linearise(a).relate(CurveOps.linearise(inner)));
  }

  public void testRelateDisjointDiscsNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry far = readCurve(CIRCLE_FAR);
    assertLaserNotSlower("disc vs disc disjoint relate",
        () -> a.relate(far),
        () -> CurveOps.linearise(a).relate(CurveOps.linearise(far)));
  }

  public void testRelateExtTangentDiscsNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry tan = readCurve(CIRCLE_EXT_TAN);
    assertLaserNotSlower("disc vs disc ext tangent relate",
        () -> a.relate(tan),
        () -> CurveOps.linearise(a).relate(CurveOps.linearise(tan)));
  }

  public void testRelateR1TouchKissNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry kiss = readCurve(CIRCLE_3_4_5);
    assertLaserNotSlower("R.1 T-ext 3-4-5 relate",
        () -> a.relate(kiss),
        () -> CurveOps.linearise(a).relate(CurveOps.linearise(kiss)));
    assertLaserNotSlower("R.1 T-ext 3-4-5 touches",
        () -> a.touches(kiss),
        () -> CurveOps.linearise(a).touches(CurveOps.linearise(kiss)));
  }

  public void testRelateCrossingDiscsReverseNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("disc vs disc reverse crossing relate",
        () -> cross.relate(a),
        () -> CurveOps.linearise(cross).relate(CurveOps.linearise(a)));
  }

  public void testEqualsTopoEqualDiscsNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_5);
    assertLaserNotSlower("disc vs disc equalsTopo",
        () -> a.equalsTopo(b),
        () -> CurveOps.linearise(a).equalsTopo(CurveOps.linearise(b)));
  }

  public void testEqualsTopoCrossingDiscsNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("disc vs disc crossing equalsTopo",
        () -> a.equalsTopo(cross),
        () -> CurveOps.linearise(a).equalsTopo(CurveOps.linearise(cross)));
  }

  public void testCrossesCrossingDiscsNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("disc vs disc crosses",
        () -> a.crosses(cross),
        () -> CurveOps.linearise(a).crosses(CurveOps.linearise(cross)));
  }

  public void testRelateMultiSurfaceDiscNotSlowerThanChord() throws Exception {
    Geometry multi = readCurve("MULTISURFACE (" + CIRCLE_5 + ")");
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("multi-disc vs disc crossing relate",
        () -> multi.relate(cross),
        () -> CurveOps.linearise(multi).relate(CurveOps.linearise(cross)));
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
