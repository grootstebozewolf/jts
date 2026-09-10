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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * PERF-GATE for Clothoid G¹ Hermite L-solve: Halley may run only when
 * {@code t_Halley ≤ 1.15 × t_Newton} (ticket 110 / map 103). Chainsaw
 * is Newton, not densify.
 * <p>
 * Fixtures are JTS-owned COMPOUNDCURVE+CLOTHOID members, including the
 * grammars-v4 #4847 ProRail worked example. Not the 9,058 golden-vector
 * dump. Solver cite: DOI 10.5281/zenodo.22059577 (v1.1.1, EUPL-1.2).
 * <p>
 * Harness matches other {@code *PerfGateTest} classes: 15 warmups,
 * median of 31 {@code nanoTime} samples, 15% slack. Each sample runs
 * many solves so the median is above timer resolution.
 */
public class ClothoidHalleyPerfGateTest extends GeometryTestCase {

  /** Highway-entry example from WKTClothoidTest / #4847. */
  private static final String HIGHWAY =
      "COMPOUNDCURVE ((0 0, 100 0), CLOTHOID (0, 0.005, 80))";

  /**
   * ProRail track 823_12V_4.3 from CurveExampleFunctions.clothoidRailBend
   * (grammars-v4 discussion #4847).
   */
  private static final String PRORAIL_RAIL_BEND =
      "COMPOUNDCURVE ("
      + "(116414.353 411964.758, 116410.740 411976.388), "
      + "CLOTHOID (0, 0.005, 48), "
      + "CIRCULARSTRING (116394.687 412021.591, 116284.527 412126.266, 116132.940 412123.450), "
      + "CLOTHOID (0.005, 0, 42), "
      + "(116095.653 412104.165, 115842.170 411961.603)"
      + ")";

  private static final int WARMUP = 15;
  private static final int SAMPLES = 31;
  private static final double NOISE = 1.15;
  private static final int INNER = 4000;
  private static final double L_AGREE = 1e-9;

  public static void main(String[] args) {
    TestRunner.run(ClothoidHalleyPerfGateTest.class);
  }

  public ClothoidHalleyPerfGateTest(String name) {
    super(name);
  }

  public void testHighwayHalleyRecoversWktLength() throws Exception {
    Hermite h = clothoids(HIGHWAY).get(0);
    ClothoidHalleySolver.Result r = ClothoidHalleySolver.solveHalleyL(
        h.p0, h.p1, h.k0, h.k1);
    assertEquals("highway Halley L", 80.0, r.getL(), L_AGREE);
  }

  public void testHighwayNewtonRecoversWktLength() throws Exception {
    Hermite h = clothoids(HIGHWAY).get(0);
    ClothoidHalleySolver.Result r = ClothoidHalleySolver.solveNewtonL(
        h.p0, h.p1, h.k0, h.k1);
    assertEquals("highway Newton L", 80.0, r.getL(), L_AGREE);
  }

  public void testProrailClothoidsRecoverWktLength() throws Exception {
    List<Hermite> hs = clothoids(PRORAIL_RAIL_BEND);
    assertEquals("two CLOTHOID members", 2, hs.size());
    assertEquals("entry L", 48.0, hs.get(0).wktL, 0.0);
    assertEquals("exit L", 42.0, hs.get(1).wktL, 0.0);
    for (int i = 0; i < hs.size(); i++) {
      Hermite h = hs.get(i);
      double halley = ClothoidHalleySolver.solveHalleyL(h.p0, h.p1, h.k0, h.k1).getL();
      double newton = ClothoidHalleySolver.solveNewtonL(h.p0, h.p1, h.k0, h.k1).getL();
      assertEquals("ProRail Halley member " + i, h.wktL, halley, L_AGREE);
      assertEquals("ProRail Newton member " + i, h.wktL, newton, L_AGREE);
    }
  }

  public void testHalleyNotSlowerThanNewtonOnFixtures() throws Exception {
    final List<Hermite> hs = new ArrayList<Hermite>();
    hs.addAll(clothoids(HIGHWAY));
    hs.addAll(clothoids(PRORAIL_RAIL_BEND));
    assertEquals(3, hs.size());
    assertLaserNotSlower("clothoid Halley vs Newton",
        new Runnable() {
          @Override
          public void run() {
            for (int n = 0; n < INNER; n++) {
              for (int i = 0; i < hs.size(); i++) {
                Hermite h = hs.get(i);
                ClothoidHalleySolver.solveHalleyL(h.p0, h.p1, h.k0, h.k1);
              }
            }
          }
        },
        new Runnable() {
          @Override
          public void run() {
            for (int n = 0; n < INNER; n++) {
              for (int i = 0; i < hs.size(); i++) {
                Hermite h = hs.get(i);
                ClothoidHalleySolver.solveNewtonL(h.p0, h.p1, h.k0, h.k1);
              }
            }
          }
        });
  }

  private static List<Hermite> clothoids(String wkt) throws Exception {
    Geometry g = new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
    CompoundCurve cc = (CompoundCurve) g;
    List<Hermite> out = new ArrayList<Hermite>();
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      if (!(m instanceof ClothoidSegment)) {
        continue;
      }
      ClothoidSegment cl = (ClothoidSegment) m;
      Coordinate a = cl.getStartCoordinate();
      Coordinate b = cl.getEndCoordinate();
      out.add(new Hermite(
          new double[] { a.x, a.y },
          new double[] { b.x, b.y },
          cl.getStartKappa(),
          cl.getEndKappa(),
          cl.getLength()));
    }
    return out;
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
    if (cm == 0) {
      return;
    }
    double ratio = (double) lm / (double) cm;
    if (ratio > NOISE) {
      fail(label + ": laser " + (lm / 1.0e6) + " ms > chainsaw "
          + (cm / 1.0e6) + " ms (ratio " + ratio + " > " + NOISE + ")");
    }
  }

  private static final class Hermite {
    final double[] p0;
    final double[] p1;
    final double k0;
    final double k1;
    final double wktL;

    Hermite(double[] p0, double[] p1, double k0, double k1, double wktL) {
      this.p0 = p0;
      this.p1 = p1;
      this.k0 = k0;
      this.k1 = k1;
      this.wktL = wktL;
    }
  }
}
