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
package org.locationtech.jts.geom.curved;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Smoke tests for the playground {@link ClothoidSegment} per the
 * proposal at <a href="https://github.com/antlr/grammars-v4/discussions/4847">grammars-v4 #4847</a>.
 *
 * <p>Targets v1 form: clothoid as a non-leading {@link CompoundCurve}
 * member, parametrised by {@code (κ₀, κ₁, L)} with start state
 * inherited from the preceding member.
 */
public class ClothoidSegmentTest extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(ClothoidSegmentTest.class); }
  public ClothoidSegmentTest(String name) { super(name); }

  private CurvedGeometryFactory cgf() { return new CurvedGeometryFactory(); }

  // ---- core math ----------------------------------------------------

  /** Average κ × L = total turn angle. */
  public void testEndTangentMatchesAverageCurvatureTimesLength() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    double expected = 0.5 * (0.0 + 0.005) * 80.0;       // = 0.2 rad
    assertEquals(expected, cs.getEndTangent(), 1e-9);
  }

  /** A degenerate κ₀ = κ₁ MUST be rejected (use CIRCULARSTRING / line). */
  public void testRejectsConstantCurvature() {
    try {
      new ClothoidSegment(new Coordinate(0, 0), 0.0, 0.005, 0.005, 80.0, cgf());
      fail("expected IllegalArgumentException for κ₀ = κ₁");
    } catch (IllegalArgumentException ok) { /* expected */ }
  }

  /** Negative or zero length must be rejected. */
  public void testRejectsNonPositiveLength() {
    try {
      new ClothoidSegment(new Coordinate(0, 0), 0.0, 0.0, 0.005, 0.0, cgf());
      fail("expected IllegalArgumentException for L = 0");
    } catch (IllegalArgumentException ok) { /* expected */ }
    try {
      new ClothoidSegment(new Coordinate(0, 0), 0.0, 0.0, 0.005, -1.0, cgf());
      fail("expected IllegalArgumentException for L < 0");
    } catch (IllegalArgumentException ok) { /* expected */ }
  }

  /** Clothoid constant A = √(L / |κ₁ − κ₀|). */
  public void testClothoidConstantA() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    double expected = Math.sqrt(80.0 / 0.005);          // = √16000 ≈ 126.49
    assertEquals(expected, cs.getClothoidConstantA(), 1e-9);
  }

  // ---- densification ------------------------------------------------

  public void testToLinearAnchorsStartAndEndExactly() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, cgf());
    Coordinate[] dense = cs.toLinear(0.5).getCoordinates();
    assertTrue(dense.length >= 16);
    assertEquals(100.0, dense[0].x, 0.0);
    assertEquals(0.0,   dense[0].y, 0.0);
    assertEquals(cs.getEndCoordinate().x, dense[dense.length - 1].x, 1e-9);
    assertEquals(cs.getEndCoordinate().y, dense[dense.length - 1].y, 1e-9);
  }

  public void testFlattenedCoordinateSequenceCarriesOnlyStartAndEnd() {
    // Per §3.6 of the proposal: the parent LineString's coord seq is
    // [start, end] only -- the interior is *not* in the flat sequence.
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, 0.0, 0.01, 50.0, cgf());
    assertEquals(2, cs.getCoordinates().length);
  }

  // ---- as a CompoundCurve member -----------------------------------

  public void testAsCompoundCurveMemberPreservesIdentity() {
    CurvedGeometryFactory f = cgf();
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, f);
    org.locationtech.jts.geom.LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(100, 0)
    });
    CompoundCurve cc = new CompoundCurve(
        new org.locationtech.jts.geom.LineString[] { line, cs }, f);
    assertEquals(2, cc.getNumMembers());
    assertTrue(cc.getMemberN(1) instanceof ClothoidSegment);

    // CompoundCurve.toLinear walks members; clothoid contributes its
    // densified chord polyline.
    Geometry linear = cc.toLinear(0.5);
    assertTrue("expected dense polyline > 16 chord points, got " + linear.getNumPoints(),
        linear.getNumPoints() > 16);
  }

  // ---- copy ---------------------------------------------------------

  public void testCopyPreservesParameters() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.5, -0.001, 0.003, 50.0, cgf());
    ClothoidSegment copy = (ClothoidSegment) cs.copy();
    assertEquals(cs.getStartKappa(), copy.getStartKappa(), 0.0);
    assertEquals(cs.getEndKappa(),   copy.getEndKappa(),   0.0);
    assertEquals(cs.getLength(),     copy.getLength(),     0.0);
    assertEquals(cs.getStartTangent(), copy.getStartTangent(), 0.0);
    assertEquals(cs.getEndTangent(),   copy.getEndTangent(),   1e-12);
  }
}
