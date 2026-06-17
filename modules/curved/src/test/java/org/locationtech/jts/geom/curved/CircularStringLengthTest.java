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
package org.locationtech.jts.geom.curved;

import java.util.List;
import java.util.Random;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.curved.adversarial.CurveRefRunner;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * M-LEN-CS (#1195): {@link CircularString#getLength()} must return the analytical
 * circular arc length (sum of r&middot;&theta; over consecutive control-point
 * triples), not the chord-polyline length.
 * <p>
 * Verified against the NetTopologySuite.Proofs extracted oracle: the committed
 * {@code curve_arc_length_vectors.txt} are exact {@code ARC_LENGTH} outputs of
 * the Rocq/Coq development, and an independent in-Java oracle
 * ({@link CurveRefRunner#exactCircularArcLength}) is used for a wide bounded sweep.
 */
public class CircularStringLengthTest extends TestCase {

  private static final String VECTORS =
      "/org/locationtech/jts/geom/curved/rocqref/curve_arc_length_vectors.txt";

  public static void main(String args[]) {
    TestRunner.run(CircularStringLengthTest.class);
  }

  public CircularStringLengthTest(String name) { super(name); }

  private final CurvedGeometryFactory gf = new CurvedGeometryFactory();

  private CircularString arc(double... xy) {
    Coordinate[] pts = new Coordinate[xy.length / 2];
    for (int i = 0; i < pts.length; i++)
      pts[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
    return gf.createCircularString(
        new org.locationtech.jts.geom.impl.CoordinateArraySequence(pts));
  }

  /** Each committed Rocq oracle vector: getLength() must equal the exact arc length. */
  public void testLengthMatchesOracleVectors() throws Exception {
    List<CurveRefRunner.ArcLengthCase> cases = CurveRefRunner.loadArcLengthCases(VECTORS);
    assertTrue("vectors loaded", cases.size() >= 5);
    for (CurveRefRunner.ArcLengthCase c : cases) {
      CircularString cs = arc(c.sx, c.sy, c.mx, c.my, c.ex, c.ey);
      double len = cs.getLength();
      assertEquals("arc length for " + c, c.expectedLength, len,
          1e-9 * Math.max(1.0, Math.abs(c.expectedLength)));
    }
  }

  /** A multi-arc CircularString length is the sum of its arc lengths. */
  public void testMultiArcLengthIsSumOfArcs() {
    // two stacked semicircles of radius 5 (a full circle described as 5 points)
    CircularString twoArcs = arc(5,0,  0,5,  -5,0,  0,-5,  5,0);
    double single = arc(5,0, 0,5, -5,0).getLength();   // one semicircle = 5*pi
    assertEquals(2 * single, twoArcs.getLength(), 1e-9 * (2 * single));
    assertEquals(2 * Math.PI * 5, twoArcs.getLength(), 1e-7);
  }

  /** Production length agrees with the independent in-Java oracle on a wide bounded sweep. */
  public void testLengthAgreesWithIndependentOracleOnBoundedArcs() {
    Random rnd = new Random(42);
    int checked = 0;
    for (int i = 0; i < 5000; i++) {
      double cx = rnd.nextDouble() * 100 - 50;
      double cy = rnd.nextDouble() * 100 - 50;
      double r = 0.5 + rnd.nextDouble() * 49.5;          // [0.5, 50]
      double a0 = rnd.nextDouble() * 2 * Math.PI;
      double sweep = (0.05 + rnd.nextDouble() * 0.90) * Math.PI; // (0, ~0.95*pi] minor arc
      double dir = rnd.nextBoolean() ? 1 : -1;
      double am = a0 + dir * sweep / 2, a1 = a0 + dir * sweep;
      CircularString cs = arc(
          cx + r * Math.cos(a0), cy + r * Math.sin(a0),
          cx + r * Math.cos(am), cy + r * Math.sin(am),
          cx + r * Math.cos(a1), cy + r * Math.sin(a1));
      double exact = CurveRefRunner.exactCircularArcLength(
          cs.getCoordinateN(0).x, cs.getCoordinateN(0).y,
          cs.getCoordinateN(1).x, cs.getCoordinateN(1).y,
          cs.getCoordinateN(2).x, cs.getCoordinateN(2).y);
      assertEquals("bounded arc " + i, exact, cs.getLength(),
          1e-7 * Math.max(1.0, Math.abs(exact)));
      checked++;
    }
    assertEquals(5000, checked);
  }
}
