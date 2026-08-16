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
package org.locationtech.jts.noding;

import org.locationtech.jts.geom.Coordinate;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * OverlayNG-for-circles: a core {@link NodedSegmentString} may carry
 * an arc on {@code [i, i+1]}. The midpoint is metadata, not a vertex.
 */
public class CircularNodedSegmentStringTest extends GeometryTestCase {

  public static void main(String[] args) {
    TestRunner.run(CircularNodedSegmentStringTest.class);
  }

  public CircularNodedSegmentStringTest(String name) {
    super(name);
  }

  public void testArcSegmentIsNotTheChordVertices() {
    Coordinate start = new Coordinate(-5, 0);
    Coordinate mid = new Coordinate(0, 5);
    Coordinate end = new Coordinate(5, 0);
    CircularNodedSegmentString ss = new CircularNodedSegmentString(
        start, mid, end, "src");
    assertEquals(2, ss.size());
    assertTrue(ss.getCoordinate(0).equals2D(start));
    assertTrue(ss.getCoordinate(1).equals2D(end));
    assertTrue(ss.isCircularArc(0));
    assertEquals(SegmentKind.ARC, ss.getSegmentKind(0));
    assertTrue(ss.isExact(0));
    assertFalse(ss.mayCollapseToChord(0));
    assertTrue(ss.getArcMidpoint(0).equals2D(mid));
    assertEquals("src", ss.getData());
  }

  public void testChordConstructorIsLinear() {
    Coordinate[] pts = new Coordinate[] {
        new Coordinate(5, 0), new Coordinate(-5, 0)
    };
    CircularNodedSegmentString ss = new CircularNodedSegmentString(pts, null);
    assertEquals(2, ss.size());
    assertFalse(ss.isCircularArc(0));
    assertEquals(SegmentKind.LINEARIZED, ss.getSegmentKind(0));
    assertTrue(ss.mayCollapseToChord(0));
    assertNull(ss.getArcMidpoint(0));
  }

  public void testAddIntersectionKeepsArcMetadata() {
    CircularNodedSegmentString ss = new CircularNodedSegmentString(
        new Coordinate(-5, 0), new Coordinate(0, 5),
        new Coordinate(5, 0), null);
    ss.addIntersection(new Coordinate(0, 5), 0);
    assertTrue(ss.hasNodes());
    assertTrue(ss.isCircularArc(0));
  }
}
