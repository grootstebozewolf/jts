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
import org.locationtech.jts.geom.curve.CurveOps;
import org.locationtech.jts.geom.curve.Linearizable;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;
import test.jts.perf.LaserRatchetSink;

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
  private static final String ARC =
      "CIRCULARSTRING (0 0, 2 3, 10 0)";
  private static final String COMPOUND =
      "COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 12 3, 20 0))";
  private static final String TWO_DISCS =
      "MULTISURFACE ("
          + "CURVEPOLYGON (CIRCULARSTRING (1 5, 2 6, 3 5, 2 4, 1 5)), "
          + "CURVEPOLYGON (CIRCULARSTRING (7 5, 8 6, 9 5, 8 4, 7 5)))";
  private static final String TWO_DISC_SQUARE =
      "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))";
  private static final String POINTS =
      "MULTIPOINT ((0 0), (10 0), (10 10), (0 10))";
  private static final String PLAIN_POLY =
      "POLYGON ((0 0, 8 0, 8 8, 0 8, 0 0))";
  private static final String ARC_BOX =
      "POLYGON ((0 3.5, 10 3.5, 10 5.5, 0 5.5, 0 3.5))";
  private static final String COMPOUND_BOX =
      "POLYGON ((10 3.5, 20 3.5, 20 5.5, 10 5.5, 10 3.5))";

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
    LaserRatchetSink.recordOperation("LargestEmptyCirclePerfGateTest",
        "algorithm/construct", label, lm, cm, samePath);
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

  public void testPointsOnlyIsChordPath() throws Exception {
    Geometry pts = readCurve(POINTS);
    assertChordPath("points-only LEC",
        () -> LargestEmptyCircle.getCenter(pts, null, TOL),
        () -> LargestEmptyCircle.getCenter(pts, null, TOL));
  }

  public void testPlainPolygonIsChordPath() throws Exception {
    Geometry poly = readCurve(PLAIN_POLY);
    assertChordPath("plain-polygon LEC",
        () -> LargestEmptyCircle.getCenter(poly, null, TOL),
        () -> LargestEmptyCircle.getCenter(poly, null, TOL));
  }

  public void testArcNotSlowerThanControlChord() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry chords = linearise(arc);
    Geometry box = readCurve(ARC_BOX);
    assertLaserNotSlower("LEC CircularString arc vs toLinear",
        () -> LargestEmptyCircle.getCenter(arc, box, TOL),
        () -> LargestEmptyCircle.getCenter(chords, box, TOL));
  }

  public void testCompoundCurveNotSlowerThanControlPolyline() throws Exception {
    Geometry cc = readCurve(COMPOUND);
    Geometry chords = linearise(cc);
    Geometry box = readCurve(COMPOUND_BOX);
    assertLaserNotSlower("LEC CompoundCurve vs toLinear",
        () -> LargestEmptyCircle.getCenter(cc, box, TOL),
        () -> LargestEmptyCircle.getCenter(chords, box, TOL));
  }

  public void testTwoDiscsNotSlowerThanControlNgons() throws Exception {
    Geometry discs = readCurve(TWO_DISCS);
    Geometry chords = linearise(discs);
    Geometry square = readCurve(TWO_DISC_SQUARE);
    assertLaserNotSlower("LEC two discs vs toLinear n-gons",
        () -> LargestEmptyCircle.getCenter(discs, square, TOL),
        () -> LargestEmptyCircle.getCenter(chords, square, TOL));
  }

  /**
   * Chainsaw: {@code toLinear} at {@link CurveOps#TOLERANCE_FRACTION}
   * (densify-then-core). Collections are flattened first.
   */
  private static Geometry linearise(Geometry g) {
    if (g instanceof Linearizable) {
      return ((Linearizable) g).toLinear(CurveOps.tolerance(g));
    }
    if (g.getNumGeometries() > 1
        || "GeometryCollection".equals(g.getGeometryType())
        || "MultiSurface".equals(g.getGeometryType())
        || "MultiCurve".equals(g.getGeometryType())) {
      Geometry[] parts = new Geometry[g.getNumGeometries()];
      for (int i = 0; i < parts.length; i++) {
        parts[i] = linearise(g.getGeometryN(i));
      }
      return g.getFactory().createGeometryCollection(parts);
    }
    return g;
  }
}
