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
/*
 * AI Disclosure (Eclipse Foundation GenAI Guidelines):
 * AI-generated portions are dedicated to CC0-1.0; human-reviewed.
 *
 * SPDX-License-Identifier: (EPL-2.0 OR EDL-1.0) AND CC0-1.0
 * Assisted-by: xAI Grok (grok-4.3)
 * Assisted-by: Claude (Opus-4.7)
 */
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Tests for §3.8 reverse semantics on the curve geometry types:
 * {@link ClothoidSegment} (sign-flip), {@link CircularString} (type
 * preservation), {@link CompoundCurve} (member-list walk).
 */
public class ReverseSemanticsTest extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(ReverseSemanticsTest.class); }
  public ReverseSemanticsTest(String name) { super(name); }

  private CurveGeometryFactory cgf() { return new CurveGeometryFactory(); }

  // ---- ClothoidSegment §3.8 -------------------------------------

  public void testClothoidReverseSwapsAndNegatesKappas() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    ClothoidSegment rev = (ClothoidSegment) cs.reverse();
    assertEquals(-cs.getEndKappa(),   rev.getStartKappa(), 1e-15);
    assertEquals(-cs.getStartKappa(), rev.getEndKappa(),   1e-15);
    assertEquals(cs.getLength(),      rev.getLength(),     0.0);
  }

  public void testClothoidReverseStartsAtOldEnd() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    ClothoidSegment rev = (ClothoidSegment) cs.reverse();
    assertEquals(cs.getEndCoordinate().x, rev.getStartCoordinate().x, 1e-9);
    assertEquals(cs.getEndCoordinate().y, rev.getStartCoordinate().y, 1e-9);
  }

  public void testClothoidReverseEndsAtOldStart() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    ClothoidSegment rev = (ClothoidSegment) cs.reverse();
    // The reversed segment integrates back through the same physical curve;
    // its analytical end should land on the original start within float noise.
    assertEquals(cs.getStartCoordinate().x, rev.getEndCoordinate().x, 1e-6);
    assertEquals(cs.getStartCoordinate().y, rev.getEndCoordinate().y, 1e-6);
  }

  public void testClothoidReverseTangentRotated180() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    ClothoidSegment rev = (ClothoidSegment) cs.reverse();
    double expected = normalise(cs.getEndTangent() + Math.PI);
    assertEquals(expected, rev.getStartTangent(), 1e-12);
  }

  public void testClothoidReverseTwiceIsIdentity() {
    // Asymmetric case: nonzero start tangent, both κ nonzero, opposite signs.
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(50, 25), 0.7, -0.002, 0.004, 60.0, cgf());
    ClothoidSegment back = (ClothoidSegment) cs.reverse().reverse();
    assertEquals(cs.getStartCoordinate().x, back.getStartCoordinate().x, 1e-9);
    assertEquals(cs.getStartCoordinate().y, back.getStartCoordinate().y, 1e-9);
    assertEquals(cs.getStartTangent(), back.getStartTangent(), 1e-12);
    assertEquals(cs.getStartKappa(),   back.getStartKappa(),   1e-15);
    assertEquals(cs.getEndKappa(),     back.getEndKappa(),     1e-15);
    assertEquals(cs.getLength(),       back.getLength(),       0.0);
  }

  // ---- CircularString type preservation -------------------------

  public void testCircularStringReverseStaysCircularString() {
    CurveGeometryFactory f = cgf();
    CircularString arc = f.createCircularString(f.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(5, 5), new Coordinate(10, 0)
    }));
    Geometry rev = arc.reverse();
    assertTrue("reversed CircularString must stay a CircularString, was "
        + rev.getGeometryType(), rev instanceof CircularString);
    Coordinate[] cc = rev.getCoordinates();
    assertEquals(10.0, cc[0].x, 0.0);
    assertEquals(0.0,  cc[2].x, 0.0);
    assertEquals(5.0,  cc[1].x, 0.0); // mid stays in place
  }

  // ---- CompoundCurve member-list walk ---------------------------

  public void testCompoundCurveReverseWalksMembersBackwardPreservingTypes() {
    CurveGeometryFactory f = cgf();
    LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    CircularString arc = f.createCircularString(f.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(10, 0), new Coordinate(15, 5), new Coordinate(20, 0)
    }));
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(20, 0), 0.0, 0.0, 0.005, 30.0, f);
    CompoundCurve cc = new CompoundCurve(new LineString[] { line, arc, cs }, f);

    CompoundCurve rev = (CompoundCurve) cc.reverse();

    assertEquals(3, rev.getNumMembers());
    // Order reversed: was [line, arc, clothoid] -> now [clothoid, arc, line]
    assertTrue(rev.getMemberN(0) instanceof ClothoidSegment);
    assertTrue(rev.getMemberN(1) instanceof CircularString);
    assertTrue(rev.getMemberN(2) instanceof LineString);
    assertFalse("first member must be the reversed clothoid, not a plain LineString",
        rev.getMemberN(0).getClass().equals(LineString.class));
    assertFalse("second member must be the reversed arc, not a plain LineString",
        rev.getMemberN(1).getClass().equals(LineString.class));
  }

  public void testCompoundCurveReverseTwiceIsStructurallyEqual() {
    CurveGeometryFactory f = cgf();
    LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    CircularString arc = f.createCircularString(f.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(10, 0), new Coordinate(15, 5), new Coordinate(20, 0)
    }));
    CompoundCurve cc = new CompoundCurve(new LineString[] { line, arc }, f);
    CompoundCurve back = (CompoundCurve) cc.reverse().reverse();
    assertEquals(2, back.getNumMembers());
    assertTrue(back.getMemberN(0) instanceof LineString);
    assertTrue(back.getMemberN(1) instanceof CircularString);
    // Coordinates round-trip
    assertEquals(0.0,  back.getMemberN(0).getCoordinates()[0].x, 0.0);
    assertEquals(20.0, back.getMemberN(1).getCoordinates()[2].x, 0.0);
  }

  public void testEmptyCompoundCurveReverse() {
    CurveGeometryFactory f = cgf();
    CompoundCurve cc = new CompoundCurve(new LineString[0], f);
    CompoundCurve rev = (CompoundCurve) cc.reverse();
    assertEquals(0, rev.getNumMembers());
    assertTrue(rev.isEmpty());
  }

  // ---- helpers ---------------------------------------------------

  private static double normalise(double theta) {
    while (theta >  Math.PI) theta -= 2.0 * Math.PI;
    while (theta <= -Math.PI) theta += 2.0 * Math.PI;
    return theta;
  }
}
