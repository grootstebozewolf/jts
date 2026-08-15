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
package org.locationtech.jts.algorithm.construct;

import java.util.Arrays;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * PERF-GATE: public {@link LargestEmptyCircle} on a certified disc
 * may run only when it is no slower than LEC on the n-gon of control
 * points (n=4 locked). Slack is 15% ({@code 1.15}); do not loosen.
 * Identity / “same as chords” rows skip the ratio.
 */
public class LargestEmptyCirclePerfGateTest extends GeometryTestCase {

  private static final String DISC_2 =
      "CURVEPOLYGON (CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0))";
  private static final String RING_2 =
      "CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0)";
  private static final String SQUARE =
      "POLYGON ((-2 0, 0 2, 2 0, 0 -2, -2 0))";
  private static final String SQUARE_RING =
      "LINESTRING (-2 0, 0 2, 2 0, 0 -2, -2 0)";

  private static final int WARMUP = 15;
  private static final int SAMPLES = 31;
  private static final double NOISE = 1.15;
  private static final double TOL = 0.01;

  public static void main(String[] args) {
    TestRunner.run(LargestEmptyCirclePerfGateTest.class);
  }

  public LargestEmptyCirclePerfGateTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static Geometry nGon(Geometry ring, int n) {
    Coordinate[] c = ring.getCoordinates();
    if (n == 4) {
      GeometryFactory f = ring.getFactory();
      return f.createLineString(c);
    }
    double cx = 0.0;
    double cy = 0.0;
    double r = 2.0;
    Coordinate[] pts = new Coordinate[n + 1];
    for (int i = 0; i < n; i++) {
      double a = -Math.PI / 2.0 + i * 2.0 * Math.PI / n;
      pts[i] = new Coordinate(cx + r * Math.cos(a), cy + r * Math.sin(a));
    }
    pts[n] = new Coordinate(pts[0]);
    return ring.getFactory().createLineString(pts);
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

  public void testDiscNotSlowerThanFourChordNgon() throws Exception {
    Geometry disc = readCurve(DISC_2);
    Geometry ring = readCurve(RING_2);
    Geometry chords = nGon(ring, 4);
    Geometry square = readCurve(SQUARE);
    assertLaserNotSlower("LEC disc vs n=4 chords",
        () -> LargestEmptyCircle.getCenter(ring, disc, TOL),
        () -> LargestEmptyCircle.getCenter(chords, square, TOL));
  }

  public void testCircularStringNotSlowerThanFourChordNgon() throws Exception {
    Geometry ring = readCurve(RING_2);
    Geometry chords = nGon(ring, 4);
    Geometry square = readCurve(SQUARE);
    assertLaserNotSlower("LEC CircularString vs n=4 chords",
        () -> LargestEmptyCircle.getCenter(ring, null, TOL),
        () -> LargestEmptyCircle.getCenter(chords, square, TOL));
  }

  public void testPlainSquareIsChordPath() throws Exception {
    Geometry square = readCurve(SQUARE);
    Geometry squareRing = readCurve(SQUARE_RING);
    assertChordPath("plain square LEC",
        () -> LargestEmptyCircle.getCenter(squareRing, square, TOL),
        () -> LargestEmptyCircle.getCenter(squareRing, square, TOL));
  }
}
