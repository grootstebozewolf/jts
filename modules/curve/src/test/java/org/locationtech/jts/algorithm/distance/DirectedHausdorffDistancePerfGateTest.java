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
 * M.1 PERF-GATE: {@link DirectedHausdorffDistance} on the certified
 * Curve* pairs may run only when no slower than the chord path.
 * Slack 15% ({@code 1.15}).
 */
public class DirectedHausdorffDistancePerfGateTest extends GeometryTestCase {

  private static final String ARC = "CIRCULARSTRING (0 0, 2 3, 10 0)";
  private static final String BASELINE = "LINESTRING (0 0, 10 0)";
  private static final String DISC_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String DISC_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";

  private static final int WARMUP = 15;
  private static final int SAMPLES = 31;
  private static final double NOISE = 1.15;

  public static void main(String[] args) {
    TestRunner.run(DirectedHausdorffDistancePerfGateTest.class);
  }

  public DirectedHausdorffDistancePerfGateTest(String name) {
    super(name);
  }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testDiscDiscNotSlowerThanChord() throws Exception {
    Geometry a = readCurve(DISC_5);
    Geometry b = readCurve(DISC_CROSSING);
    assertLaserNotSlower("DHD two discs",
        () -> DirectedHausdorffDistance.distance(a, b),
        () -> DirectedHausdorffDistance.distance(
            CurveOps.linearise(a), CurveOps.linearise(b)));
  }

  public void testArcSegmentNotSlowerThanChord() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry seg = readCurve(BASELINE);
    assertLaserNotSlower("DHD arc-segment",
        () -> DirectedHausdorffDistance.distance(arc, seg),
        () -> DirectedHausdorffDistance.distance(
            CurveOps.linearise(arc), seg));
  }

  /** M.2: tall bulge closed form also holds the gate. */
  public void testTallBulgeArcNotSlowerThanChord() throws Exception {
    Geometry arc = readCurve("CIRCULARSTRING (0 0, 2 5, 10 0)");
    Geometry seg = readCurve(BASELINE);
    assertLaserNotSlower("DHD tall bulge arc-segment",
        () -> DirectedHausdorffDistance.distance(arc, seg),
        () -> DirectedHausdorffDistance.distance(
            CurveOps.linearise(arc), seg));
  }

  /** M.3: IWD on the certified arc→segment pair. */
  public void testIwdArcNotSlowerThanChord() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry seg = readCurve(BASELINE);
    double lim = DirectedHausdorffDistance.distance(arc, seg) + 0.01;
    assertLaserNotSlower("DHD IWD arc-segment",
        () -> DirectedHausdorffDistance.isFullyWithinDistance(arc, seg, lim),
        () -> DirectedHausdorffDistance.isFullyWithinDistance(
            CurveOps.linearise(arc), seg, lim));
  }

  private void assertLaserNotSlower(String label, Runnable laser,
      Runnable chainsaw) {
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
    Arrays.sort(L);
    Arrays.sort(C);
    long lm = L[L.length / 2];
    long cm = C[C.length / 2];
    if (cm == 0) return;
    double ratio = (double) lm / (double) cm;
    if (ratio > NOISE) {
      fail(label + ": laser " + (lm / 1.0e6) + " ms > chainsaw "
          + (cm / 1.0e6) + " ms (ratio " + ratio + " > " + NOISE + ")");
    }
  }
}
