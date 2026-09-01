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
 * Option B: {@link SegmentString} names {@code [i, i+1]} explicitly.
 * Default earth is linearized. OverlayNG may consume exact arcs;
 * everyone else stays on the default.
 */
public class SegmentStringContractTest extends GeometryTestCase {

  public static void main(String[] args) {
    TestRunner.run(SegmentStringContractTest.class);
  }

  public SegmentStringContractTest(String name) {
    super(name);
  }

  public void testDefaultIsLinearized() {
    Coordinate[] pts = new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(1, 0)
    };
    SegmentString basic = new BasicSegmentString(pts, null);
    SegmentString noded = new NodedSegmentString(pts, null);
    assertEquals(SegmentKind.LINEARIZED, basic.getSegmentKind(0));
    assertEquals(SegmentKind.LINEARIZED, noded.getSegmentKind(0));
    assertTrue(basic.isLinearized(0));
    assertTrue(noded.isLinearized(0));
    assertFalse(basic.isExact(0));
    assertFalse(noded.isExact(0));
    assertTrue(basic.mayCollapseToChord(0));
    assertTrue(noded.mayCollapseToChord(0));
    assertNull(basic.getArcMidpoint(0));
    assertNull(noded.getArcMidpoint(0));
  }

  public void testArcIsExactAndMustNotCollapse() {
    CircularNodedSegmentString arc = CircularNodedSegmentString.arc(
        new Coordinate(-5, 0), new Coordinate(0, 5),
        new Coordinate(5, 0), null);
    assertEquals(SegmentKind.ARC, arc.getSegmentKind(0));
    assertTrue(arc.isExact(0));
    assertFalse(arc.isLinearized(0));
    assertFalse(arc.mayCollapseToChord(0));
    assertTrue(arc.getArcMidpoint(0).equals2D(new Coordinate(0, 5)));
  }

  public void testCertifiedLineIsExactAndMustNotCollapse() {
    CircularNodedSegmentString line = CircularNodedSegmentString.certified(
        new Coordinate(5, 0), new Coordinate(-5, 0), null);
    assertEquals(SegmentKind.CERTIFIED, line.getSegmentKind(0));
    assertTrue(line.isExact(0));
    assertFalse(line.isLinearized(0));
    assertFalse(line.mayCollapseToChord(0));
    assertNull(line.getArcMidpoint(0));
  }

  public void testLinearizedFactoryIsExplicitFallback() {
    CircularNodedSegmentString lin = CircularNodedSegmentString.linearized(
        new Coordinate[] { new Coordinate(0, 0), new Coordinate(1, 0) },
        null);
    assertEquals(SegmentKind.LINEARIZED, lin.getSegmentKind(0));
    assertTrue(lin.mayCollapseToChord(0));
    assertFalse(lin.isExact(0));
  }

  /**
   * Option B allowed lie: PrecisionModel may snap coordinate values for
   * index / HotPixel queries, but {@link SegmentKind} stays ARC — the
   * index envelope may expand under PM scale without renaming the segment.
   */
  public void testPrecisionModelSnapDoesNotChangeSegmentKind() {
    Coordinate start = new Coordinate(0.12, 0.08);
    Coordinate mid = new Coordinate(5.07, 5.03);
    Coordinate end = new Coordinate(10.11, 0.09);
    CircularNodedSegmentString arc = CircularNodedSegmentString.arc(
        start, mid, end, null);
    assertEquals(SegmentKind.ARC, arc.getSegmentKind(0));
    assertFalse(arc.mayCollapseToChord(0));

    org.locationtech.jts.geom.PrecisionModel pm =
        new org.locationtech.jts.geom.PrecisionModel(1.0);
    Coordinate[] ends = arc.getCoordinates();
    for (int i = 0; i < ends.length; i++) {
      pm.makePrecise(ends[i]);
    }
    Coordinate snappedMid = mid.copy();
    pm.makePrecise(snappedMid);

    // Kind is metadata on the segment string, not derived from coordinates.
    assertEquals(SegmentKind.ARC, arc.getSegmentKind(0));
    assertFalse(arc.mayCollapseToChord(0));
    assertTrue(arc.isExact(0));
    // Midpoint metadata is still the original arc mid (not collapsed to chord).
    assertNotNull(arc.getArcMidpoint(0));
  }
}
