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

/**
 * Closed-form cells for Proofs Option A (exact arc front-end).
 */
public class ExactCircularArcTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(ExactCircularArcTest.class);
  }

  public ExactCircularArcTest(String name) {
    super(name);
  }

  public void testSemicircleLengthPiR() {
    ExactCircularArc a = new ExactCircularArc(
        new Coordinate(5, 0), new Coordinate(0, 5), new Coordinate(-5, 0));
    assertTrue(a.isArc());
    assertEquals(5.0, a.radius(), 1.0e-12);
    assertEquals(Math.PI, a.sweep(), 1.0e-12);
    assertEquals(5.0 * Math.PI, a.length(), 1.0e-12);
    assertTrue(a.chordLeArc());
    assertEquals(10.0, a.chordLength(), 1.0e-12);
  }

  public void testFullCircleTwoWindows() {
    ExactCircularArc a = new ExactCircularArc(
        new Coordinate(5, 0), new Coordinate(0, 5), new Coordinate(-5, 0));
    ExactCircularArc b = new ExactCircularArc(
        new Coordinate(-5, 0), new Coordinate(0, -5), new Coordinate(5, 0));
    assertEquals(2.0 * Math.PI * 5.0, a.length() + b.length(), 1.0e-12);
  }

  public void testColinearIsChord() {
    ExactCircularArc a = new ExactCircularArc(
        new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(3, 0));
    assertFalse(a.isArc());
    assertEquals(3.0, a.length(), 1.0e-12);
    assertEquals(a.chordLength(), a.length(), 0.0);
    assertTrue(a.chordLeArc());
    assertEquals(0.0, a.circularSegmentArea(), 0.0);
  }

  public void testInArcEndsAndMid() {
    ExactCircularArc a = new ExactCircularArc(
        new Coordinate(5, 0), new Coordinate(0, 5), new Coordinate(-5, 0));
    assertTrue(a.inArc(new Coordinate(5, 0), 1.0e-9));
    assertTrue(a.inArc(new Coordinate(0, 5), 1.0e-9));
    assertTrue(a.inArc(new Coordinate(-5, 0), 1.0e-9));
    assertFalse(a.inArc(new Coordinate(0, -5), 1.0e-9));
    assertFalse(a.inArc(new Coordinate(0, 0), 1.0e-9));
  }

  public void testCircularSegmentAreaHalfDisc() {
    ExactCircularArc a = new ExactCircularArc(
        new Coordinate(5, 0), new Coordinate(0, 5), new Coordinate(-5, 0));
    // Half-disc segment = half disc minus triangle = 12.5π − 25
    double expected = 0.5 * 25.0 * (Math.PI - 0.0) - 0.0;
    // θ=π, sinπ=0 → r²/2 · π = 12.5π
    assertEquals(12.5 * Math.PI, a.circularSegmentArea(), 1.0e-12);
    assertEquals(expected, a.circularSegmentArea(), 1.0e-12);
  }

  public void testStaticLengthMatchesInstance() {
    Coordinate s = new Coordinate(1, 0);
    Coordinate m = new Coordinate(0, 1);
    Coordinate e = new Coordinate(-1, 0);
    assertEquals(new ExactCircularArc(s, m, e).length(),
        ExactCircularArc.length(s, m, e), 0.0);
  }

  public void testArcLengthCentroidSemicircle() {
    ExactCircularArc a = new ExactCircularArc(
        new Coordinate(5, 0), new Coordinate(0, 5), new Coordinate(-5, 0));
    Coordinate c = a.arcLengthCentroid();
    assertEquals(0.0, c.x, 1.0e-12);
    assertEquals(10.0 / Math.PI, c.y, 1.0e-12);
  }

  public void testCwCentroidMirrorsCcw() {
    ExactCircularArc ccw = new ExactCircularArc(
        new Coordinate(5, 0), new Coordinate(0, 5), new Coordinate(-5, 0));
    ExactCircularArc cw = new ExactCircularArc(
        new Coordinate(5, 0), new Coordinate(0, -5), new Coordinate(-5, 0));
    assertFalse(cw.isCcw());
    assertEquals(ccw.length(), cw.length(), 0.0);
    assertEquals(ccw.arcLengthCentroid().x, cw.arcLengthCentroid().x, 1.0e-12);
    assertEquals(-ccw.arcLengthCentroid().y, cw.arcLengthCentroid().y, 1.0e-12);
  }
}
