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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * ClothoidSegment per antlr/grammars-v4 WKT #4847 / #4848.
 * Non-leading COMPOUNDCURVE member only; not a top-level JTS type.
 */
public class ClothoidSegmentTest extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(ClothoidSegmentTest.class); }
  public ClothoidSegmentTest(String name) { super(name); }

  private CurveGeometryFactory cgf() { return new CurveGeometryFactory(); }

  public void testEndTangentMatchesAverageCurvatureTimesLength() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    assertEquals(0.5 * (0.0 + 0.005) * 80.0, cs.getEndTangent(), 1e-9);
  }

  public void testRejectsConstantCurvature() {
    try {
      new ClothoidSegment(new Coordinate(0, 0), 0.0, 0.005, 0.005, 80.0, cgf());
      fail("expected IllegalArgumentException for k0 = k1");
    }
    catch (IllegalArgumentException ok) {
      // expected
    }
  }

  public void testRejectsNonPositiveLength() {
    try {
      new ClothoidSegment(new Coordinate(0, 0), 0.0, 0.0, 0.005, 0.0, cgf());
      fail("expected IllegalArgumentException for L = 0");
    }
    catch (IllegalArgumentException ok) {
      // expected
    }
  }

  public void testFromAandLength() {
    ClothoidSegment cs = ClothoidSegment.fromAandLength(
        new Coordinate(0, 0), 0.0, 0.0, Math.sqrt(80.0 / 0.005), 80.0, cgf());
    assertEquals(0.0, cs.getStartKappa(), 0.0);
    assertEquals(0.005, cs.getEndKappa(), 1e-12);
    assertEquals(80.0, cs.getLength(), 0.0);
  }

  public void testFlattenedCoordinateSequenceIsStartAndEndOnly() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, 0.0, 0.01, 50.0, cgf());
    assertEquals(2, cs.getCoordinates().length);
  }

  public void testToLinearAnchorsStartAndEnd() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    Coordinate[] dense = cs.toLinear(0.5).getCoordinates();
    assertTrue(dense.length >= 16);
    assertEquals(100.0, dense[0].x, 0.0);
    assertEquals(cs.getEndCoordinate().x, dense[dense.length - 1].x, 1e-9);
  }

  public void testReverseNegatesKappas() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    ClothoidSegment rev = (ClothoidSegment) cs.reverse();
    assertEquals(-0.005, rev.getStartKappa(), 0.0);
    assertEquals(-0.0, rev.getEndKappa(), 0.0);
    assertEquals(80.0, rev.getLength(), 0.0);
    assertEquals(cs.getEndCoordinate().x, rev.getStartCoordinate().x, 1e-9);
    assertEquals(cs.getEndCoordinate().y, rev.getStartCoordinate().y, 1e-9);
  }

  public void testAsNonLeadingCompoundCurveMember() {
    CurveGeometryFactory f = cgf();
    LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(100, 0)
    });
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, f);
    CompoundCurve cc = f.createCompoundCurve(new LineString[] { line, cs });
    assertEquals(2, cc.getNumMembers());
    assertFalse(cc.getMemberN(0) instanceof ClothoidSegment);
    assertTrue(cc.getMemberN(1) instanceof ClothoidSegment);
    Geometry linear = cc.toLinear(0.5);
    assertTrue(linear.getNumPoints() > 16);
  }

  public void testEqualsExactRequiresClothoidType() {
    CurveGeometryFactory f = cgf();
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, 0.0, 0.005, 80.0, f);
    LineString chord = f.createLineString(cs.getCoordinates());
    assertFalse(cs.equalsExact(chord));
  }
}
