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

/** Pins for the thin OrientableSegment adapter (Bible §3). */
public class OrientableSegmentTest extends GeometryTestCase {

  public static void main(String[] args) {
    TestRunner.run(OrientableSegmentTest.class);
  }

  public OrientableSegmentTest(String name) {
    super(name);
  }

  public void testStraightParity() {
    OrientableSegment s = OrientableSegments.straight(
        new Coordinate(0, 0), new Coordinate(10, 0));
    assertEquals(Orientation.COUNTERCLOCKWISE,
        s.orientationIndex(new Coordinate(5, 1)));
    assertEquals(10.0, s.length(), 0.0);
  }

  public void testComposesExactCircularArc() {
    ExactCircularArc exact = new ExactCircularArc(
        new Coordinate(-5, 0), new Coordinate(0, 5), new Coordinate(5, 0));
    OrientableSegment arc = OrientableSegments.arc(exact);
    assertEquals(exact.length(), arc.length(), 0.0);
    assertSame(exact.getStart(), arc.getStart());
    OrientableSegment chord = OrientableSegments.straight(
        new Coordinate(0, -1), new Coordinate(0, 6));
    assertTrue(arc.intersects(chord));
  }
}
