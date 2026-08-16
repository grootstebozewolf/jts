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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * PERF-GATE for the distance family and constructions: a curve-aware path
 * (laser) may run only when it is no slower than the locationtech/jts chord
 * baseline (chainsaw): {@link CurveOps#linearise} at
 * {@link CurveOps#TOLERANCE_FRACTION}, then the equivalent core algorithm.
 * <p>
 * The overlay ratchet is not a prescription. These ops live on
 * {@link CurveOps} -- the instance-method hub -- and are gated independently.
 * A closed form is taken only when a cheap shape check says it can answer
 * (circular disc, single arc, circular-plus-straight hull, point-vs-arc).
 * Anything else goes straight to the chords; trying and falling through
 * would be the overlay-retention mistake again.
 * <p>
 * Measured on this branch before the lasers (OpenJDK 21, median of 31 after
 * 15 warmups). Every row <em>was</em> the chord path:
 * <table border="1">
 * <caption>Red timings -- instance distance / constructions vs chord baseline</caption>
 * <tr><th>case</th><th>laser</th><th>chainsaw</th><th>ratio</th></tr>
 * <tr><td>distance far discs</td>   <td>4.735 ms</td><td>4.000 ms</td><td>1.18</td></tr>
 * <tr><td>distance arc-point</td>   <td>0.071 ms</td><td>0.069 ms</td><td>1.02</td></tr>
 * <tr><td>convexHull disc</td>      <td>1.031 ms</td><td>1.064 ms</td><td>0.97</td></tr>
 * <tr><td>convexHull half-arc</td>  <td>0.369 ms</td><td>0.356 ms</td><td>1.04</td></tr>
 * <tr><td>buffer disc +1</td>       <td>0.802 ms</td><td>0.342 ms</td><td>2.35</td></tr>
 * </table>
 * After the lasers (same harness): far discs 0.009 / 4.797 (0.002),
 * arc-point 0.001 / 0.097 (0.015), disc hull 0.003 / 1.141 (0.003),
 * half-arc hull 0.002 / 0.533 (0.004), disc buffer 0.001 / 0.807 (0.002).
 * CompoundCurve hull is now a closed form (exposed arcs + supporting
 * tangents). Open-arc buffer stays on the chords. Hausdorff / MIC live
 * in the app module.
 * <p>
 * Each row asserts {@code median(laser) <= median(chainsaw)}. A 15% slack
 * covers timer noise.
 */
public class CurveOpsDistConPerfGateTest extends GeometryTestCase {

  private static final String DISC_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String DISC_FAR =
      "CURVEPOLYGON (CIRCULARSTRING (100 0, 105 5, 110 0, 105 -5, 100 0))";
  private static final String HALF_ARC =
      "CIRCULARSTRING (-10 0, 0 10, 10 0)";
  private static final String ARC =
      "CIRCULARSTRING (0 0, 2 3, 10 0)";
  private static final String COMPOUND =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 10 10))";
  private static final String POINT_ABOVE = "POINT (5 6)";

  private static final int WARMUP = 15;
  private static final int SAMPLES = 31;
  private static final double NOISE = 1.15;

  public static void main(String[] args) {
    TestRunner.run(CurveOpsDistConPerfGateTest.class);
  }

  public CurveOpsDistConPerfGateTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
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

  // -- timings --------------------------------------------------------------

  public void testDistanceFarDiscsNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(DISC_5);
    Geometry far = readCurve(DISC_FAR);
    assertLaserNotSlower("distance far discs",
        () -> a.distance(far),
        () -> CurveOps.linearise(a).distance(CurveOps.linearise(far)));
  }

  public void testDistanceArcPointNotSlowerThanChord() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry p = readCurve(POINT_ABOVE);
    assertLaserNotSlower("distance arc-point",
        () -> arc.distance(p),
        () -> CurveOps.linearise(arc).distance(p));
  }

  public void testConvexHullDiscNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(DISC_5);
    assertLaserNotSlower("convexHull disc",
        () -> a.convexHull(),
        () -> CurveOps.linearise(a).convexHull());
  }

  public void testConvexHullHalfArcNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(HALF_ARC);
    assertLaserNotSlower("convexHull half-arc",
        () -> a.convexHull(),
        () -> CurveOps.linearise(a).convexHull());
  }

  public void testConvexHullCompoundNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(COMPOUND);
    assertLaserNotSlower("convexHull compound",
        () -> a.convexHull(),
        () -> CurveOps.linearise(a).convexHull());
  }

  public void testBufferDiscNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(DISC_5);
    assertLaserNotSlower("buffer disc +1",
        () -> a.buffer(1.0),
        () -> CurveOps.linearise(a).buffer(1.0));
  }

  // -- exactness the chord path cannot meet --------------------------------
  //
  // These are the Red claim for the lasers: a densified hull / buffer /
  // distance is only as good as the sagitta. A closed form must land on the
  // true value, not the inscribed polyline.

  /** Half-disc of radius 10: area 50*pi, not the ~157.079 of a 788-gon. */
  public void testHalfArcConvexHullAreaIsExact() throws Exception {
    Geometry hull = readCurve(HALF_ARC).convexHull();
    assertEquals("convex hull of a semicircle is the half-disc",
        50.0 * Math.PI, hull.getArea(), 1.0e-9);
  }

  /**
   * Two radius-5 discs: A centred at the origin, B at (105, 0) -- B's ring
   * is {@code CIRCULARSTRING (100 0, 105 5, 110 0, ...)}, so the left edge
   * is at 100 and the centre is 105. Gap |c1-c2| - r1 - r2 = 95.
   */
  public void testFarDiscDistanceIsExact() throws Exception {
    assertEquals("filled-disc gap is |c1-c2| - r1 - r2",
        95.0, readCurve(DISC_5).distance(readCurve(DISC_FAR)), 1.0e-12);
  }

  /** Buffering a radius-5 disc by 1 is a radius-6 disc, area 36*pi. */
  public void testDiscBufferAreaIsExact() throws Exception {
    assertEquals("buffered disc is a disc of radius 6",
        36.0 * Math.PI, readCurve(DISC_5).buffer(1.0).getArea(), 1.0e-9);
  }

  /**
   * Issue #6 theme: the convex hull of
   * {@code COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 10 10))}
   * must reach the arc's bulge at 135 degrees, not the control-point hull
   * of area 50 that keeps (5 5) and ignores the leftward sweep.
   */
  public void testCompoundConvexHullReachesBulge() throws Exception {
    Geometry hull = readCurve(COMPOUND).convexHull();
    Geometry bulge = getGeometryFactory().createPoint(new Coordinate(
        5.0 + 5.0 * Math.cos(Math.toRadians(135.0)),
        5.0 * Math.sin(Math.toRadians(135.0))));
    assertTrue("H-CC hull is a CurvePolygon, not a densified POLYGON",
        hull instanceof CurvePolygon);
    assertEquals("exact H-CC area is 50 + 12.5 acos(0.6)",
        50.0 + 12.5 * Math.acos(0.6), hull.getArea(), 1.0e-9);
    assertEquals("bulge sits on the laser arc",
        0.0, hull.distance(bulge), 1.0e-9);
    assertNotNull("closed form must answer; densify is not flagged exact",
        CurveExact.convexHull(readCurve(COMPOUND)));
  }
}
