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
package org.locationtech.jts.algorithm.distance;

import java.util.Arrays;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurveOps;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * PERF-GATE: public {@link DiscreteHausdorffDistance} on the two
 * certified D-HF pairs may run only when it is no slower than the
 * chord path (linearise, then the same class). Slack is 15%
 * ({@code 1.15}); do not loosen. Identity rows skip the ratio.
 */
public class DiscreteHausdorffDistancePerfGateTest extends GeometryTestCase {

  private static final String ARC = "CIRCULARSTRING (0 0, 2 3, 10 0)";
  private static final String BASELINE = "LINESTRING (0 0, 10 0)";
  private static final String DISC_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String DISC_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  private static final String LINE = "LINESTRING (0 0, 2 3, 10 0)";

  private static final int WARMUP = 15;
  private static final int SAMPLES = 31;
  private static final double NOISE = 1.15;

  public static void main(String[] args) {
    TestRunner.run(DiscreteHausdorffDistancePerfGateTest.class);
  }

  public DiscreteHausdorffDistancePerfGateTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private void assertLaserNotSlower(String label, Runnable laser, Runnable chainsaw) {
    timeBoth(label, laser, chainsaw, false);
  }

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
    long[] ls = L.clone();
    long[] cs = C.clone();
    Arrays.sort(ls);
    Arrays.sort(cs);
    long lm = ls[ls.length / 2];
    long cm = cs[cs.length / 2];
    if (cm == 0 || samePath) return;
    double ratio = (double) lm / (double) cm;
    if (ratio > NOISE) {
      fail(label + ": laser " + (lm / 1.0e6) + " ms > chainsaw "
          + (cm / 1.0e6) + " ms (ratio " + ratio + " > " + NOISE + ")");
    }
  }

  public void testDiscDiscNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(DISC_5);
    Geometry b = readCurve(DISC_CROSSING);
    assertLaserNotSlower("Hausdorff two discs",
        () -> DiscreteHausdorffDistance.distance(a, b),
        () -> DiscreteHausdorffDistance.distance(
            CurveOps.linearise(a), CurveOps.linearise(b)));
  }

  public void testArcSegmentWitnessNotSlowerThanChord() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry seg = readCurve(BASELINE);
    assertLaserNotSlower("Hausdorff arc-segment witness",
        () -> DiscreteHausdorffDistance.orientedDistance(arc, seg),
        () -> DiscreteHausdorffDistance.orientedDistance(
            CurveOps.linearise(arc), seg));
  }

  public void testPlainLineIsChordPath() throws Exception {
    Geometry line = readCurve(LINE);
    Geometry seg = readCurve(BASELINE);
    assertChordPath("plain LineString Hausdorff",
        () -> DiscreteHausdorffDistance.orientedDistance(line, seg),
        () -> DiscreteHausdorffDistance.orientedDistance(line, seg));
  }
}
