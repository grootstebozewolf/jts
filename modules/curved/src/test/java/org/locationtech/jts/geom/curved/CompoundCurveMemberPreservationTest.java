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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * FCP-MEM: {@link CompoundCurve} must keep its member structure across the
 * standard {@link Geometry} operations.
 * <p>
 * {@code copyInternal()} and {@code toLinear(double)} already walk the members.
 * {@code reverse()} does not: CompoundCurve inherits
 * {@code LineString.reverseInternal()}, which rebuilds a plain
 * {@code LineString} from the concatenated coordinate sequence. So reversing a
 * compound curve silently downgrades it -- the type is lost, and with it the
 * segment structure and each member's own arc/line identity.
 */
public class CompoundCurveMemberPreservationTest extends GeometryTestCase {

  /** Arc then straight: 2 members, the first circular. */
  private static final String LINE_THEN_ARC =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 1 1, 2 0), (2 0, 4 0))";

  public static void main(String[] args) {
    TestRunner.run(CompoundCurveMemberPreservationTest.class);
  }

  public CompoundCurveMemberPreservationTest(String name) { super(name); }

  private static CompoundCurve readCC(String wkt) throws Exception {
    return (CompoundCurve) new CurvedWKTReader().read(wkt);
  }

  /** Reversing keeps the CompoundCurve type. */
  public void testReverseKeepsType() throws Exception {
    Geometry reversed = readCC(LINE_THEN_ARC).reverse();
    assertEquals("reverse() must not downgrade the type",
        "CompoundCurve", reversed.getGeometryType());
  }

  /** Reversing keeps the members, in reverse order. */
  public void testReverseKeepsMembersInReverseOrder() throws Exception {
    CompoundCurve reversed = (CompoundCurve) readCC(LINE_THEN_ARC).reverse();
    assertEquals("member count must survive", 2, reversed.getNumMembers());
    assertEquals("the straight member should now come first",
        "LineString", reversed.getMemberN(0).getGeometryType());
    assertEquals("the arc member should now come second",
        "CircularString", reversed.getMemberN(1).getGeometryType());
  }

  /** Reversing walks the coordinates end-to-start, as for any LineString. */
  public void testReverseReversesCoordinates() throws Exception {
    CompoundCurve original = readCC(LINE_THEN_ARC);
    Geometry reversed = original.reverse();
    assertEquals("first point of the reverse is the last of the original",
        original.getCoordinateN(original.getNumPoints() - 1),
        reversed.getCoordinates()[0]);
    assertEquals("point count is unchanged",
        original.getNumPoints(), reversed.getNumPoints());
  }

  /** Reversing twice returns the original structure. */
  public void testDoubleReverseRoundTrips() throws Exception {
    CompoundCurve original = readCC(LINE_THEN_ARC);
    CompoundCurve twice = (CompoundCurve) original.reverse().reverse();
    assertEquals(original.getNumMembers(), twice.getNumMembers());
    assertEquals("CircularString", twice.getMemberN(0).getGeometryType());
    checkEqual(original, twice);
  }

  /**
   * Precondition for member reversal: CircularString has no reverseInternal
   * override either, so on its own it also downgrades to a plain LineString.
   */
  public void testCircularStringReverseKeepsType() throws Exception {
    Geometry reversed = new CurvedWKTReader()
        .read("CIRCULARSTRING (0 0, 1 1, 2 0)").reverse();
    assertEquals("CircularString.reverse() must not downgrade the type",
        "CircularString", reversed.getGeometryType());
    assertEquals("arc control points should reverse",
        3, reversed.getNumPoints());
  }

  /** Guard: copy() already preserves members and must keep doing so. */
  public void testCopyKeepsMembers() throws Exception {
    CompoundCurve copy = (CompoundCurve) readCC(LINE_THEN_ARC).copy();
    assertEquals(2, copy.getNumMembers());
    assertEquals("CircularString", copy.getMemberN(0).getGeometryType());
  }
}
