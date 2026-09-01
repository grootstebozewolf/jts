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
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * PERF-GATE: public {@link DiscreteFrechetDistance} on the two
 * certified pairs may run only when it is no slower than the
 * control-point discrete path ({@code getCoordinates()} clone).
 * Slack is 15% ({@code 1.15}); do not loosen. Identity and
 * “same as chords” rows skip the ratio.
 */
public class DiscreteFrechetDistancePerfGateTest extends GeometryTestCase {

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
    TestRunner.run(DiscreteFrechetDistancePerfGateTest.class);
  }

  public DiscreteFrechetDistancePerfGateTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  /** Today's control-point discrete path: {@code getCoordinates()} as LineStrings. */
  private static double controlPointDistance(Geometry a, Geometry b) {
    Geometry ca = a.getFactory().createLineString(a.getCoordinates());
    Geometry cb = b.getFactory().createLineString(b.getCoordinates());
    return DiscreteFrechetDistance.distance(ca, cb);
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

  public void testDiscDiscNotSlowerThanControlPoints() throws Exception {
    Geometry a = readCurve(DISC_5);
    Geometry b = readCurve(DISC_CROSSING);
    assertLaserNotSlower("Frechet two discs",
        () -> DiscreteFrechetDistance.distance(a, b),
        () -> controlPointDistance(a, b));
  }

  public void testArcSegmentWitnessNotSlowerThanControlPoints() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry seg = readCurve(BASELINE);
    assertLaserNotSlower("Frechet arc-segment witness",
        () -> DiscreteFrechetDistance.distance(arc, seg),
        () -> controlPointDistance(arc, seg));
  }

  public void testEqualDiscsAreIdentityPath() throws Exception {
    Geometry a = readCurve(DISC_5);
    assertChordPath("equal discs Frechet",
        () -> DiscreteFrechetDistance.distance(a, a),
        () -> DiscreteFrechetDistance.distance(a, a));
  }

  public void testPlainLineIsChordPath() throws Exception {
    Geometry line = readCurve(LINE);
    Geometry seg = readCurve(BASELINE);
    assertChordPath("plain LineString Frechet",
        () -> DiscreteFrechetDistance.distance(line, seg),
        () -> DiscreteFrechetDistance.distance(line, seg));
  }

  /** M.5: concentric rings continuous closed form. */
  public void testConcentricRingsNotSlowerThanControlPoints() throws Exception {
    Geometry outer = readCurve(
        "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)");
    Geometry inner = readCurve(
        "CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0)");
    assertLaserNotSlower("Frechet concentric rings",
        () -> DiscreteFrechetDistance.distance(outer, inner),
        () -> controlPointDistance(outer, inner));
  }
}
