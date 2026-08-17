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

import org.locationtech.jts.geom.Coordinate;

import junit.framework.TestCase;
import junit.textui.TestRunner;

public class AngleBetweenTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(AngleBetweenTest.class);
  }

  public AngleBetweenTest(String name) {
    super(name);
  }

  public void testSignedShortQuarter() {
    assertEquals(Math.PI / 2.0, AngleBetween.signedShort(1, 0, 0, 1), 1.0e-15);
    assertEquals(-Math.PI / 2.0, AngleBetween.signedShort(1, 0, 0, -1), 1.0e-15);
  }

  public void testDirectedSemicircle() {
    double s = AngleBetween.directedSweep(0, 0,
        new Coordinate(5, 0), new Coordinate(0, 5), new Coordinate(-5, 0));
    assertEquals(Math.PI, s, 1.0e-12);
  }

  public void testDirectedLongWay() {
    // Mid on the lower half → the long CCW (or CW) turn is 3π/2? 
    // start (5,0), mid (0,-5), end (-5,0) is the lower semicircle, sweep π.
    double s = AngleBetween.directedSweep(0, 0,
        new Coordinate(5, 0), new Coordinate(0, -5), new Coordinate(-5, 0));
    assertEquals(Math.PI, s, 1.0e-12);
  }

  public void testNormalizePositive() {
    assertEquals(0.0, AngleBetween.normalizePositive(0.0), 0.0);
    assertEquals(Math.PI, AngleBetween.normalizePositive(-Math.PI), 1.0e-15);
  }

  public void testDirectedThreeQuarter() {
    // start → north → south is the long CCW 3π/2, not the short CW π/2
    AngleBetween.DirectedSweep sw = AngleBetween.through(0, 0,
        new Coordinate(1, 0), new Coordinate(0, 1), new Coordinate(0, -1));
    assertTrue(sw.isCcw());
    assertEquals(1.5 * Math.PI, sw.radians(), 1.0e-12);
    assertEquals(1.5 * Math.PI, sw.signed(), 1.0e-12);
  }

  public void testBranchCutIsNotAFullTurn() {
    // start/end straddle atan2's ±π cut. Subtracting two atan2s
    // collapses that to 0 ≡ 2π; atan2(cross,dot) keeps the tiny gap.
    Coordinate start = new Coordinate(-1.0, 1.0e-15);
    Coordinate end = new Coordinate(-1.0, -1.0e-15);
    Coordinate mid = new Coordinate(0.0, 1.0);
    AngleBetween.DirectedSweep sw = AngleBetween.through(0, 0, start, mid, end);
    assertFalse(sw.isCcw());
    assertTrue(sw.radians() < AngleBetween.TWO_PI);
    assertTrue(sw.radians() > AngleBetween.TWO_PI - 1.0e-12);
    double a0 = Math.atan2(start.y, start.x);
    double a1 = Math.atan2(end.y, end.x);
    double collapsed = AngleBetween.normalizePositive(a1 - a0);
    // Subtracting two atan2s near ±π yields ~0 (or ~2π), not the true
    // complementary long arc that through() returns.
    assertTrue(collapsed < 1.0e-12
        || Math.abs(collapsed - AngleBetween.TWO_PI) < 1.0e-12);
    assertTrue(Math.abs(sw.radians() - collapsed) > 6.0);
  }

  public void testThroughMatchesDirectedSweep() {
    Coordinate s = new Coordinate(3, 1);
    Coordinate m = new Coordinate(0, 4);
    Coordinate e = new Coordinate(-2, 0);
    AngleBetween.DirectedSweep sw = AngleBetween.through(0, 1, s, m, e);
    assertEquals(sw.radians(), AngleBetween.directedSweep(0, 1, s, m, e), 0.0);
    assertEquals(sw.isCcw(), AngleBetween.isCcw(0, 1, s, m, e));
  }
}
