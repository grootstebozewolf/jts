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
package org.locationtech.jts.io.curve;

import java.util.Arrays;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurveOps;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;
import test.jts.perf.LaserRatchetSink;

/**
 * PERF-GATE: first-class curve WKB (write+read) may run only when it is
 * no slower than densify-then-WKB. Slack is 15% ({@code 1.15}); do not
 * loosen. Identity rows (plain LineString through the subclass) skip
 * the ratio.
 */
public class CurveWKBPerfGateTest extends GeometryTestCase {

  private static final String DISC =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String LINE = "LINESTRING (0 0, 1 1, 2 0)";

  private static final int WARMUP = 15;
  private static final int SAMPLES = 31;
  private static final double NOISE = 1.15;

  public static void main(String[] args) {
    TestRunner.run(CurveWKBPerfGateTest.class);
  }

  public CurveWKBPerfGateTest(String name) { super(name); }

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
    LaserRatchetSink.recordOperation("CurveWKBPerfGateTest",
        "io/curve", label, lm, cm, samePath);
    if (cm == 0 || samePath) return;
    double ratio = (double) lm / (double) cm;
    if (ratio > NOISE) {
      fail(label + ": laser " + (lm / 1.0e6) + " ms > chainsaw "
          + (cm / 1.0e6) + " ms (ratio " + ratio + " > " + NOISE + ")");
    }
  }

  public void testDiscWriteReadNotSlowerThanDensifyThenWkb() throws Exception {
    Geometry disc = readCurve(DISC);
    CurveWKBWriter cw = new CurveWKBWriter();
    CurveWKBReader cr = new CurveWKBReader(disc.getFactory());
    WKBWriter ww = new WKBWriter();
    WKBReader wr = new WKBReader();
    assertLaserNotSlower("disc WKB write+read",
        () -> {
          try {
            cr.read(cw.write(disc));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        },
        () -> {
          try {
            wr.read(ww.write(CurveOps.linearise(disc)));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  public void testPlainLineIsChordPath() throws Exception {
    Geometry line = readCurve(LINE);
    CurveWKBWriter cw = new CurveWKBWriter();
    WKBWriter ww = new WKBWriter();
    assertChordPath("plain LineString WKB",
        () -> cw.write(line),
        () -> ww.write(line));
  }
}
