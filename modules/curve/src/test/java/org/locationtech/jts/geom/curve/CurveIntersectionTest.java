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

import org.locationtech.jts.geom.Coordinate;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * N-AA / N-AL public utilities.
 */
public class CurveIntersectionTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(CurveIntersectionTest.class);
  }

  public CurveIntersectionTest(String name) {
    super(name);
  }

  public void testArcArcCrossing() {
    // Upper halves of discs (0,0)r=5 and (7,0)r=5 — one node in both sweeps.
    Coordinate[] xs = CurveIntersection.arcArc(
        new Coordinate(-5, 0), new Coordinate(0, 5), new Coordinate(5, 0),
        new Coordinate(2, 0), new Coordinate(7, 5), new Coordinate(12, 0));
    assertEquals(1, xs.length);
    assertEquals(3.5, xs[0].x, 1.0e-6);
  }

  public void testArcLineDiameter() {
    Coordinate[] xs = CurveIntersection.arcLine(
        new Coordinate(-5, 0), new Coordinate(0, 5), new Coordinate(5, 0),
        new Coordinate(0, -10), new Coordinate(0, 10));
    assertEquals(1, xs.length);
    assertEquals(0.0, xs[0].x, 1.0e-9);
    assertEquals(5.0, xs[0].y, 1.0e-9);
  }

  public void testArcLineMiss() {
    Coordinate[] xs = CurveIntersection.arcLine(
        new Coordinate(-5, 0), new Coordinate(0, 5), new Coordinate(5, 0),
        new Coordinate(10, 0), new Coordinate(20, 0));
    assertEquals(0, xs.length);
  }
}
