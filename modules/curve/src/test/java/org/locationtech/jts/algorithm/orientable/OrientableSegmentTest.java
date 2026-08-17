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
import org.locationtech.jts.algorithm.exactcurve.ExactCircularArc;
import org.locationtech.jts.geom.Coordinate;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Pins for optional OrientableSegment adapters that compose
 * {@link ExactCircularArc} (Bible §3).
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

  public void testWrapsExactCircularArc() {
    ExactCircularArc exact = new ExactCircularArc(
        new Coordinate(0, 0), new Coordinate(2, 3), new Coordinate(10, 0));
    ArcOrientableSegment arc = new ArcOrientableSegment(exact);
    assertTrue(arc.isCircular());
    assertSame(exact, arc.exactArc());
    Coordinate q = new Coordinate(5, 8);
    assertEquals(
        OrientableDensifyReference.orientationIndex(arc, q, 64),
        arc.orientationIndex(q));
  }

  public void testArcSegmentHit() {
    ExactCircularArc exact = new ExactCircularArc(
        new Coordinate(-5, 0), new Coordinate(0, 5), new Coordinate(5, 0));
    OrientableSegment arc = OrientableSegments.arc(exact);
    OrientableSegment chord = OrientableSegments.straight(
        new Coordinate(0, -1), new Coordinate(0, 6));
    assertTrue(arc.intersects(chord));
  }
}
