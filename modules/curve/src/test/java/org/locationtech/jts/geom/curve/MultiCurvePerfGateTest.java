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

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;
import test.jts.perf.LaserRatchetSink;

/**
 * PERF-GATE for MultiCurve of CircularString members (ticket 111 / map 103).
 * One cell: {@code getLength()} (sum of ExactCircularArc member lengths)
 * vs {@link CurveOps#linearise} then core length. Same harness as other
 * {@code *PerfGateTest} classes: 15 warmups, median of 31 {@code nanoTime}
 * samples, 15% slack.
 */
public class MultiCurvePerfGateTest extends GeometryTestCase {

  /**
   * Two semicircles of radius 10 (control triples (0,0)/(10,10)/(20,0)
   * and (20,0)/(30,-10)/(40,0)). Closed-form length {@code 20π}.
   */
  private static final String TWO_ARCS =
      "MULTICURVE (CIRCULARSTRING (0 0, 10 10, 20 0), CIRCULARSTRING (20 0, 30 -10, 40 0))";

  private static final int WARMUP = 15;
  private static final int SAMPLES = 31;
  private static final double NOISE = 1.15;

  public static void main(String[] args) {
    TestRunner.run(MultiCurvePerfGateTest.class);
  }

  public MultiCurvePerfGateTest(String name) {
    super(name);
  }

  public void testLengthIsSumOfCircularArcs() throws Exception {
    Geometry g = readCurve(TWO_ARCS);
    assertEquals("MultiCurve", g.getGeometryType());
    assertEquals(2, g.getNumGeometries());
    assertEquals(20.0 * Math.PI, g.getLength(), 1.0e-9);
  }

  public void testLengthNotSlowerThanLinearise() throws Exception {
    Geometry g = readCurve(TWO_ARCS);
    assertLaserNotSlower("MultiCurve length vs linearise",
        () -> g.getLength(),
        () -> CurveOps.linearise(g).getLength());
  }

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
    LaserRatchetSink.recordOperation("MultiCurvePerfGateTest",
        "geom/curve", label, lm, cm, false);
    if (cm == 0) {
      return;
    }
    double ratio = (double) lm / (double) cm;
    if (ratio > NOISE) {
      fail(label + ": laser " + (lm / 1.0e6) + " ms > chainsaw "
          + (cm / 1.0e6) + " ms (ratio " + ratio + " > " + NOISE + ")");
    }
  }
}
