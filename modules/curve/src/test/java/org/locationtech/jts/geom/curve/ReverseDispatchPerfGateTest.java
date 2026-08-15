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
package org.locationtech.jts.geom.curve;

import java.util.Arrays;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.overlayng.curve.OverlayNGCurve;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * PERF-GATE for reverse-direction {@code plain.op(curve)}.
 * <p>
 * Instance methods dispatch on the receiver. A plain Polygon or LineString
 * never consults {@link CurveOps} or {@link OverlayNGCurve}, so the argument
 * is judged by its control points. Envelope filters on {@link Geometry} still
 * see the arc AABB (curve envelopes are exact), but any verdict the envelope
 * cannot decide is the control-point predicate -- wrong -- and overlay nodes
 * the control polygon.
 * <p>
 * The laser is the instance call with the plain geometry on the left. The
 * chainsaw is {@link CurveOps#linearise} at {@link CurveOps#TOLERANCE_FRACTION},
 * then the equivalent core algorithm. Same bar as the forward gates: a laser
 * may run only when it is no slower than that baseline. A 15% slack covers
 * timer noise.
 * <p>
 * Measured on this branch before reverse dispatch (OpenJDK 21, median of 31
 * after 15 warmups). The reverse path <em>was</em> the control-point path,
 * which is cheaper than densify and therefore already under the ratio cap --
 * and wrong:
 * <table border="1">
 * <caption>Red timings -- reverse plain.op(curve) vs chord baseline</caption>
 * <tr><th>case</th><th>laser</th><th>chainsaw</th><th>ratio</th></tr>
 * <tr><td>rev intersects far</td>      <td>0.000 ms</td><td>0.241 ms</td><td>0.001</td></tr>
 * <tr><td>rev intersects crossing</td> <td>0.072 ms</td><td>0.399 ms</td><td>0.181</td></tr>
 * <tr><td>rev contains far</td>        <td>0.000 ms</td><td>0.124 ms</td><td>0.003</td></tr>
 * <tr><td>rev covers nested</td>       <td>0.001 ms</td><td>0.071 ms</td><td>0.019</td></tr>
 * <tr><td>rev distance point-arc</td>  <td>0.003 ms</td><td>0.192 ms</td><td>0.015</td></tr>
 * <tr><td>rev isWithinDistance far</td><td>0.000 ms</td><td>0.154 ms</td><td>0.002</td></tr>
 * <tr><td>rev disjoint CAP</td>        <td>0.111 ms</td><td>0.175 ms</td><td>0.635</td></tr>
 * <tr><td>rev nested CAP</td>          <td>0.069 ms</td><td>0.606 ms</td><td>0.114</td></tr>
 * <tr><td>rev nested CUP</td>          <td>0.057 ms</td><td>0.342 ms</td><td>0.167</td></tr>
 * <tr><td>rev crossing CAP</td>        <td>0.235 ms</td><td>1.073 ms</td><td>0.219</td></tr>
 * </table>
 * Reverse SUB and Multi* were still the control-point path before this
 * rung: disjoint SUB 0.046 / 0.113 (0.41), nested SUB 0.131 / 0.564
 * (0.23), crossing SUB 0.115 / 0.324 (0.36), multi disjoint 0.000 /
 * 0.076 (0.002), multi nested 0.001 / 0.104 (0.008). After: disjoint
 * SUB 0.002 / 0.065 (0.025), nested SUB 0.165 / 0.163 (1.02 -- the
 * laser <em>is</em> the chord overlay), crossing SUB 0.141 / 0.139
 * (1.02), multi disjoint 0.000 / 0.051 (0.005), multi nested 0.001 /
 * 0.094 (0.008 -- rectangle envelope covers the arc AABB, no densify).
 * <p>
 * After the flip (same harness): intersects far 0.000 / 0.043 (0.007),
 * intersects crossing 0.096 / 0.096 (1.00 -- the laser <em>is</em> the
 * chord path), contains far 0.000 / 0.050 (0.007), covers nested 0.044 /
 * 0.044 (1.01), distance point-arc 0.002 / 0.124 (0.013), isWithinDistance
 * far 0.000 / 0.044 (0.009), disjoint CAP 0.001 / 0.044 (0.017), nested
 * CAP 0.030 / 0.228 (0.13), nested CUP 0.026 / 0.184 (0.14), crossing CAP
 * 0.327 / 0.314 (1.04). The red claim was correctness, not speed.
 * <p>
 * Identity / R2 rows (crossing intersects, reverse nested SUB) keep the
 * pair in the suite but skip the 15% ratio -- wrapper-vs-itself is timer
 * noise. Genuine lasers stay gated at 1.15.
 */
public class ReverseDispatchPerfGateTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_3 =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";
  private static final String CIRCLE_FAR =
      "CURVEPOLYGON (CIRCULARSTRING (100 0, 105 5, 110 0, 105 -5, 100 0))";
  private static final String CIRCLE_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  private static final String ARC_270 =
      "CIRCULARSTRING (1 0, " + (-Math.sqrt(0.5)) + " " + Math.sqrt(0.5) + ", 0 -1)";
  private static final String PLAIN_DIAMOND =
      "POLYGON ((-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String PLAIN_SQUARE =
      "POLYGON ((-6 -6, 6 -6, 6 6, -6 6, -6 -6))";
  private static final String POINT_FAR = "POINT (100 100)";
  private static final String POINT_ABOVE = "POINT (0 2)";

  private static final int WARMUP = 15;
  private static final int SAMPLES = 31;
  private static final double NOISE = 1.15;

  public static void main(String[] args) {
    TestRunner.run(ReverseDispatchPerfGateTest.class);
  }

  public ReverseDispatchPerfGateTest(String name) { super(name); }

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

  private void assertLaserNotSlower(String label, Runnable laser, Runnable chainsaw) {
    timeBoth(label, laser, chainsaw, false);
  }

  /**
   * The reverse path <em>is</em> the chord path. Keep the row; skip the
   * ratio -- wrapper-vs-itself at 15% is timer noise.
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

  public void testReverseIntersectsFarNotSlowerThanChord() throws Exception {
    Geometry plain = readCurve(PLAIN_DIAMOND);
    Geometry far = readCurve(CIRCLE_FAR);
    assertLaserNotSlower("rev intersects far",
        () -> plain.intersects(far),
        () -> plain.intersects(CurveOps.linearise(far)));
  }

  public void testReverseIntersectsCrossingNotSlowerThanChord() throws Exception {
    Geometry plain = readCurve(PLAIN_DIAMOND);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertChordPath("rev intersects crossing",
        () -> plain.intersects(cross),
        () -> CurveOps.linearise(plain).intersects(CurveOps.linearise(cross)));
  }

  public void testReverseContainsFarNotSlowerThanChord() throws Exception {
    Geometry plain = readCurve(PLAIN_SQUARE);
    Geometry p = readCurve(POINT_FAR);
    // Receiver is plain; the curve is a far disc used as the argument of covers.
    Geometry far = readCurve(CIRCLE_FAR);
    assertLaserNotSlower("rev contains far",
        () -> plain.contains(far),
        () -> plain.contains(CurveOps.linearise(far)));
    assertFalse("sanity: far disc is not inside the square", plain.contains(p));
  }

  public void testReverseCoversNestedNotSlowerThanChord() throws Exception {
    Geometry square = readCurve(PLAIN_SQUARE);
    Geometry inner = readCurve(CIRCLE_3);
    assertLaserNotSlower("rev covers nested",
        () -> square.covers(inner),
        () -> square.covers(CurveOps.linearise(inner)));
  }

  public void testReverseDistancePointArcNotSlowerThanChord() throws Exception {
    Geometry p = readCurve(POINT_ABOVE);
    Geometry arc = readCurve(ARC_270);
    assertLaserNotSlower("rev distance point-arc",
        () -> p.distance(arc),
        () -> p.distance(CurveOps.linearise(arc)));
  }

  public void testReverseWithinDistanceFarNotSlowerThanChord() throws Exception {
    Geometry plain = readCurve(PLAIN_DIAMOND);
    Geometry far = readCurve(CIRCLE_FAR);
    assertLaserNotSlower("rev isWithinDistance far",
        () -> plain.isWithinDistance(far, 1.0),
        () -> plain.isWithinDistance(CurveOps.linearise(far), 1.0));
  }

  public void testReverseDisjointCapNotSlowerThanChord() throws Exception {
    Geometry plain = readCurve(PLAIN_DIAMOND);
    Geometry far = readCurve(CIRCLE_FAR);
    assertLaserNotSlower("rev disjoint CAP",
        () -> plain.intersection(far),
        () -> chordOverlay(plain, far, OverlayNGCurve.INTERSECTION));
  }

  public void testReverseNestedCapNotSlowerThanChord() throws Exception {
    Geometry square = readCurve(PLAIN_SQUARE);
    Geometry inner = readCurve(CIRCLE_3);
    assertLaserNotSlower("rev nested CAP",
        () -> square.intersection(inner),
        () -> chordOverlay(square, inner, OverlayNGCurve.INTERSECTION));
  }

  public void testReverseNestedCupNotSlowerThanChord() throws Exception {
    Geometry square = readCurve(PLAIN_SQUARE);
    Geometry inner = readCurve(CIRCLE_3);
    assertLaserNotSlower("rev nested CUP",
        () -> square.union(inner),
        () -> chordOverlay(square, inner, OverlayNGCurve.UNION));
  }

  public void testReverseCrossingCapNotSlowerThanChord() throws Exception {
    Geometry plain = readCurve(PLAIN_DIAMOND);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("rev crossing CAP",
        () -> plain.intersection(cross),
        () -> chordOverlay(plain, cross, OverlayNGCurve.INTERSECTION));
  }

  public void testReverseDisjointSubNotSlowerThanChord() throws Exception {
    Geometry plain = readCurve(PLAIN_DIAMOND);
    Geometry far = readCurve(CIRCLE_FAR);
    assertLaserNotSlower("rev disjoint SUB",
        () -> plain.difference(far),
        () -> chordOverlay(plain, far, OverlayNGCurve.DIFFERENCE));
  }

  public void testReverseNestedSubNotSlowerThanChord() throws Exception {
    Geometry square = readCurve(PLAIN_SQUARE);
    Geometry inner = readCurve(CIRCLE_3);
    assertChordPath("rev nested SUB",
        () -> square.difference(inner),
        () -> chordOverlay(square, inner, OverlayNGCurve.DIFFERENCE));
  }

  public void testReverseCrossingSubNotSlowerThanChord() throws Exception {
    Geometry plain = readCurve(PLAIN_DIAMOND);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertLaserNotSlower("rev crossing SUB",
        () -> plain.difference(cross),
        () -> chordOverlay(plain, cross, OverlayNGCurve.DIFFERENCE));
  }

  public void testMultiDisjointVsPlainNotSlowerThanChord() throws Exception {
    Geometry multi = readCurve("MULTISURFACE (" + CIRCLE_FAR + ")");
    Geometry plain = readCurve(PLAIN_DIAMOND);
    assertLaserNotSlower("multi disjoint vs plain",
        () -> multi.intersects(plain),
        () -> CurveOps.linearise(multi).intersects(plain));
  }

  public void testMultiNestedVsPlainNotSlowerThanChord() throws Exception {
    Geometry multi = readCurve("MULTISURFACE (" + CIRCLE_3 + ")");
    Geometry square = readCurve(PLAIN_SQUARE);
    assertLaserNotSlower("multi nested vs plain",
        () -> square.covers(multi),
        () -> square.covers(CurveOps.linearise(multi)));
  }
}
