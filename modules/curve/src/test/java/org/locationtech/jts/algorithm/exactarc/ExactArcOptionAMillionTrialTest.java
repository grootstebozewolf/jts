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
package org.locationtech.jts.algorithm.exactarc;

import java.util.List;
import java.util.Random;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.curve.CircularArcDensifier;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * 1M-trial handover for Proofs Option A. Leftovers are named
 * (huge-r / near-full), not a blanket relative slack.
 */
public class ExactArcOptionAMillionTrialTest extends TestCase {

  private static final long SEED = 0xa7ea0001L;
  private static final int N = 1_000_000;
  private static final double BOX = 100.0;
  private static final int N_CHORD = 64;
  private static final double HUGE_R = 1.0e8;
  private static final double NEAR_FULL = AngleBetween.TWO_PI - 1.0e-4;

  public static void main(String[] args) {
    TestRunner.run(ExactArcOptionAMillionTrialTest.class);
  }

  public ExactArcOptionAMillionTrialTest(String name) {
    super(name);
  }

  public void testL1LengthGeDensifyChords() {
    Trial t = runLengthVsDensify();
    System.out.println("L1 " + t);
    assertEquals(N, t.tried);
    assertEquals("L1 hard (unnamed densify > exact)", 0, t.hard);
    assertTrue("L1 agree rate " + t.agreeRate(), t.agreeRate() >= 0.99);
  }

  public void testL2ChordLeArc() {
    Trial t = runChordLeArc();
    System.out.println("L2 " + t);
    assertEquals(N, t.tried);
    assertEquals("L2 chord ≤ arc hard", 0, t.hard);
    assertEquals(1.0, t.agreeRate(), 0.0);
  }

  public void testP1LengthFasterThanDensify() {
    Random rnd = new Random(SEED ^ 0x51);
    Coordinate[][] sample = new Coordinate[50_000][3];
    int n = 0;
    while (n < sample.length) {
      Coordinate[] w = triple(rnd);
      if (new ExactCircularArc(w[0], w[1], w[2]).isArc()) {
        sample[n++] = w;
      }
    }
    long aNs = timeLength(sample);
    long dNs = timeDensify(sample);
    double ratio = (double) aNs / (double) dNs;
    System.out.println("P1 A_ns=" + aNs + " densify_ns=" + dNs + " ratio=" + ratio);
    assertTrue("P1 A/densify " + ratio + " > 1.15", ratio <= 1.15);
  }

  private static Trial runLengthVsDensify() {
    Random rnd = new Random(SEED);
    Trial t = new Trial();
    long t0 = System.nanoTime();
    for (int i = 0; i < N; i++) {
      Coordinate[] w = triple(rnd);
      ExactCircularArc a = new ExactCircularArc(w[0], w[1], w[2]);
      t.tried++;
      double exact = a.length();
      double chords = densifyLength(w[0], w[1], w[2]);
      if (chords <= exact + Math.ulp(Math.max(exact, 1.0))) {
        if (Math.abs(chords - exact) <= Math.ulp(Math.max(exact, 1.0))) {
          t.soft++;
        }
        continue;
      }
      if (a.isArc() && a.radius() > HUGE_R) {
        t.hugeR++;
        continue;
      }
      if (a.isArc() && a.sweep() > NEAR_FULL) {
        t.nearFull++;
        continue;
      }
      t.hard++;
    }
    t.wallNs = System.nanoTime() - t0;
    return t;
  }

  private static Trial runChordLeArc() {
    Random rnd = new Random(SEED ^ 1);
    Trial t = new Trial();
    long t0 = System.nanoTime();
    for (int i = 0; i < N; i++) {
      ExactCircularArc a = new ExactCircularArc(pt(rnd), pt(rnd), pt(rnd));
      t.tried++;
      if (!a.chordLeArc()) {
        t.hard++;
      }
    }
    t.wallNs = System.nanoTime() - t0;
    return t;
  }

  private static double densifyLength(Coordinate s, Coordinate m, Coordinate e) {
    List<Coordinate> pts = CircularArcDensifier.densifyArcUniform(s, m, e, N_CHORD);
    double tot = 0.0;
    for (int i = 1; i < pts.size(); i++) {
      tot += pts.get(i - 1).distance(pts.get(i));
    }
    return tot;
  }

  private static long timeLength(Coordinate[][] sample) {
    long t0 = System.nanoTime();
    double acc = 0.0;
    for (int i = 0; i < sample.length; i++) {
      acc += ExactCircularArc.length(sample[i][0], sample[i][1], sample[i][2]);
    }
    long dt = System.nanoTime() - t0;
    if (acc == Double.NEGATIVE_INFINITY) {
      throw new AssertionError();
    }
    return dt;
  }

  private static long timeDensify(Coordinate[][] sample) {
    long t0 = System.nanoTime();
    double acc = 0.0;
    for (int i = 0; i < sample.length; i++) {
      acc += densifyLength(sample[i][0], sample[i][1], sample[i][2]);
    }
    long dt = System.nanoTime() - t0;
    if (acc == Double.NEGATIVE_INFINITY) {
      throw new AssertionError();
    }
    return dt;
  }

  private static Coordinate[] triple(Random rnd) {
    return new Coordinate[] { pt(rnd), pt(rnd), pt(rnd) };
  }

  private static Coordinate pt(Random rnd) {
    return new Coordinate(BOX * (2.0 * rnd.nextDouble() - 1.0),
        BOX * (2.0 * rnd.nextDouble() - 1.0));
  }

  private static final class Trial {
    int tried;
    int hard;
    int soft;
    int hugeR;
    int nearFull;
    long wallNs;

    double agreeRate() {
      return tried == 0 ? 1.0 : 1.0 - (double) hard / (double) tried;
    }

    public String toString() {
      return "tried=" + tried + " hard=" + hard + " soft=" + soft
          + " hugeR=" + hugeR + " nearFull=" + nearFull
          + " agree=" + agreeRate() + " wallNs=" + wallNs;
    }
  }
}
