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
package org.locationtech.jts.algorithm.orientable;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.curve.ArcOrientableSegment;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Small correctness pins for Proofs Option B carriers (not the 1M suite).
 */
public class OrientableSegmentTest extends GeometryTestCase {

  public static void main(String[] args) {
    TestRunner.run(OrientableSegmentTest.class);
  }

  public OrientableSegmentTest(String name) {
    super(name);
  }

  public void testStraightMatchesOrientation() {
    Coordinate a = new Coordinate(0, 0);
    Coordinate b = new Coordinate(10, 0);
    OrientableSegment s = OrientableSegments.straight(a, b);
    assertEquals(Orientation.COUNTERCLOCKWISE,
        s.orientationIndex(new Coordinate(5, 1)));
    assertEquals(Orientation.CLOCKWISE,
        s.orientationIndex(new Coordinate(5, -1)));
    assertEquals(Orientation.COLLINEAR,
        s.orientationIndex(new Coordinate(5, 0)));
  }

  public void testStraightCrossing() {
    OrientableSegment a = OrientableSegments.straight(
        new Coordinate(0, 0), new Coordinate(10, 10));
    OrientableSegment b = OrientableSegments.straight(
        new Coordinate(0, 10), new Coordinate(10, 0));
    assertTrue(a.intersects(b));
  }

  public void testArcIsNotChordLie() {
    ArcOrientableSegment arc = new ArcOrientableSegment(
        new Coordinate(0, 0), new Coordinate(2, 3), new Coordinate(10, 0));
    assertTrue(arc.isArc());
    // Apex region is left of directed arc; chord mid-control side differs
    // from a naïve chord-only story for some queries — densify agrees with arc.
    Coordinate q = new Coordinate(5, 8);
    assertEquals(arc.densifyOrientationIndex(q, 64), arc.orientationIndex(q));
  }
}
