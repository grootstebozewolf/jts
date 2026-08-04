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
import org.locationtech.jts.geom.LineString;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * §3.7 — {@code equalsExact} semantics on the curve geometry types:
 * type identity is required (a ClothoidSegment is not equal to a
 * plain LineString with the same start/end coords), and structural
 * equality compares parameters / member structure rather than the
 * parent's flat coord sequence.
 */
public class EqualsExactSemanticsTest extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(EqualsExactSemanticsTest.class); }
  public EqualsExactSemanticsTest(String name) { super(name); }

  private CurveGeometryFactory cgf() { return new CurveGeometryFactory(); }

  // ---- ClothoidSegment ------------------------------------------

  public void testClothoidEqualsItself() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    assertTrue(cs.equalsExact(cs));
  }

  public void testClothoidEqualsClothoidWithSameParameters() {
    ClothoidSegment a = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    ClothoidSegment b = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    assertTrue(a.equalsExact(b));
  }

  public void testClothoidNotEqualsDifferentStartKappa() {
    ClothoidSegment a = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    ClothoidSegment b = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.001, 0.005, 80.0, cgf());
    assertFalse(a.equalsExact(b));
  }

  public void testClothoidNotEqualsDifferentEndKappa() {
    ClothoidSegment a = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    ClothoidSegment b = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.006, 80.0, cgf());
    assertFalse(a.equalsExact(b));
  }

  public void testClothoidNotEqualsDifferentLength() {
    ClothoidSegment a = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    ClothoidSegment b = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.001, cgf());
    assertFalse(a.equalsExact(b));
  }

  public void testClothoidNotEqualsDifferentStartPoint() {
    ClothoidSegment a = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    ClothoidSegment b = new ClothoidSegment(
        new Coordinate(101, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    assertFalse(a.equalsExact(b));
  }

  public void testClothoidNotEqualsDifferentStartTangent() {
    ClothoidSegment a = new ClothoidSegment(
        new Coordinate(100, 0), 0.0,  0.0, 0.005, 80.0, cgf());
    ClothoidSegment b = new ClothoidSegment(
        new Coordinate(100, 0), 0.01, 0.0, 0.005, 80.0, cgf());
    assertFalse(a.equalsExact(b));
  }

  /** The smoking-gun bug §3.7 fixes: a plain LineString with the same
   *  start/end coords used to compare equal to a ClothoidSegment because
   *  the inherited LineString.isEquivalentClass accepted any LineString
   *  subclass. The override fixes the curve-side direction. The inverse
   *  direction (plain.equalsExact(clothoid)) cannot be fixed without
   *  modifying jts-core; documented as an asymmetry on the override. */
  public void testClothoidNotEqualsPlainLineStringWithSameEndpoints() {
    CurveGeometryFactory f = cgf();
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, f);
    LineString chord = f.createLineString(new Coordinate[] {
        cs.getStartCoordinate(), cs.getEndCoordinate()
    });
    assertFalse("ClothoidSegment must not equal a plain LineString chord",
        cs.equalsExact(chord));
  }

  public void testClothoidEqualsExactWithTolerance() {
    ClothoidSegment a = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005000, 80.0, cgf());
    ClothoidSegment b = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005001, 80.0, cgf());
    assertTrue("κ difference within tolerance should compare equal",
        a.equalsExact(b, 1e-3));
    assertFalse("…but not below the tolerance",
        a.equalsExact(b, 1e-7));
  }

  // ---- CircularString -------------------------------------------

  public void testCircularStringNotEqualsPlainLineStringWithSameCoords() {
    CurveGeometryFactory f = cgf();
    Coordinate[] pts = new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(5, 5), new Coordinate(10, 0)
    };
    CircularString arc = f.createCircularString(f.getCoordinateSequenceFactory().create(pts));
    LineString chord = f.createLineString(pts);
    // Curve-side direction enforced; inverse asymmetry as documented.
    assertFalse("CircularString != polyline through same 3 points",
        arc.equalsExact(chord));
  }

  public void testCircularStringEqualsAnotherCircularString() {
    CurveGeometryFactory f = cgf();
    Coordinate[] pts = new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(5, 5), new Coordinate(10, 0)
    };
    CircularString a = f.createCircularString(f.getCoordinateSequenceFactory().create(pts));
    CircularString b = f.createCircularString(f.getCoordinateSequenceFactory().create(pts));
    assertTrue(a.equalsExact(b));
  }

  // ---- CompoundCurve --------------------------------------------

  public void testCompoundCurveEqualsItself() {
    CurveGeometryFactory f = cgf();
    CircularString arc = f.createCircularString(f.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(5, 5), new Coordinate(10, 0)
    }));
    LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(10, 0), new Coordinate(20, 0)
    });
    CompoundCurve cc = new CompoundCurve(new LineString[] { arc, line }, f);
    assertTrue(cc.equalsExact(cc));
  }

  public void testCompoundCurvesWithStructurallyDifferentMembersAreNotEqual() {
    CurveGeometryFactory f = cgf();
    Coordinate[] arcPts = new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(5, 5), new Coordinate(10, 0)
    };
    // a) one CircularString member
    CircularString arcMember = f.createCircularString(f.getCoordinateSequenceFactory().create(arcPts));
    CompoundCurve aWithArc = new CompoundCurve(new LineString[] { arcMember }, f);
    // b) one plain LineString member with the same coord sequence
    LineString lineMember = f.createLineString(arcPts);
    CompoundCurve bWithLine = new CompoundCurve(new LineString[] { lineMember }, f);
    assertFalse("CompoundCurve(CircularString) must not equal CompoundCurve(LineString) "
        + "even with identical flat coord seq",
        aWithArc.equalsExact(bWithLine));
  }

  public void testCompoundCurveNotEqualsPlainLineStringWithSameFlatCoordSeq() {
    CurveGeometryFactory f = cgf();
    LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    CompoundCurve cc = new CompoundCurve(new LineString[] { line }, f);
    LineString plain = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    // Curve-side direction enforced; inverse asymmetry as documented.
    assertFalse(cc.equalsExact(plain));
  }

  public void testCompoundCurveDifferentMemberCountNotEqual() {
    CurveGeometryFactory f = cgf();
    LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    CompoundCurve a = new CompoundCurve(new LineString[] { line }, f);
    CompoundCurve b = new CompoundCurve(new LineString[] { line, line }, f);
    assertFalse(a.equalsExact(b));
  }
}
