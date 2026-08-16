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
package org.locationtech.jts.operation.overlayng.curve;

import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurveOps;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * R1.5: two crossing circular discs become lens / blob / crescents, exact,
 * and JTS-class with the chord overlay. Nested concentric discs become
 * the annulus (the two-disc 7/8 · 6/8 remainder). An internal tangent
 * nest ({@code H-ANNULUS-TANGENT}) is not strictly inside and stays
 * {@code null}. Disjoint and non-disc pairs stay {@code null} so
 * OverlayNGCurve can take R2 without paying this path first.
 */
public class CircularDiscOverlayTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  private static final String CIRCLE_3 =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";
  /** r=3 at (2,0); internally tangent to CIRCLE_5 at (5,0). */
  private static final String CIRCLE_INT_TAN =
      "CURVEPOLYGON (CIRCULARSTRING (-1 0, 2 3, 5 0, 2 -3, -1 0))";
  private static final String CIRCLE_FAR =
      "CURVEPOLYGON (CIRCULARSTRING (100 0, 105 5, 110 0, 105 -5, 100 0))";
  private static final String PLAIN_SQUARE =
      "POLYGON ((-6 -6, 6 -6, 6 6, -6 6, -6 -6))";

  /** r=5, d=7: 2 r² acos(d/2r) − 0.5 d √(4r²−d²). */
  private static final double LENS =
      50.0 * Math.acos(0.7) - 0.5 * 7.0 * Math.sqrt(51.0);
  private static final double DISC = 25.0 * Math.PI;
  private static final double AREA_TOL = 1.0e-3;
  private static final double EXACT = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(CircularDiscOverlayTest.class);
  }

  public CircularDiscOverlayTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testCrossingCapIsExactLens() throws Exception {
    assertDiscOp(OverlayNG.INTERSECTION, LENS, 1);
  }

  public void testCrossingCupIsExactBlob() throws Exception {
    assertDiscOp(OverlayNG.UNION, 2.0 * DISC - LENS, 1);
  }

  public void testCrossingSubIsExactCrescent() throws Exception {
    assertDiscOp(OverlayNG.DIFFERENCE, DISC - LENS, 1);
  }

  public void testCrossingXorIsBothCrescents() throws Exception {
    assertDiscOp(OverlayNG.SYMDIFFERENCE, 2.0 * (DISC - LENS), 2);
  }

  public void testNotBothDiscsReturnsNull() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry square = readCurve(PLAIN_SQUARE);
    assertNull("plain square is not a disc",
        CircularDiscOverlay.overlay(disc, square, OverlayNG.INTERSECTION));
    assertNull("and the other way",
        CircularDiscOverlay.overlay(square, disc, OverlayNG.INTERSECTION));
  }

  public void testZeroOrOneIntersectionReturnsNull() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    assertNull("disjoint, 0 nodes",
        CircularDiscOverlay.overlay(a, readCurve(CIRCLE_FAR), OverlayNG.UNION));
  }

  /**
   * Internal tangent is not strictly inside: 1 node, d+r = R.
   * nestedAnnulus already refuses it. Named miss, not a laser.
   */
  public void testInternalTangentNestIsNamedMiss() throws Exception {
    Geometry outer = readCurve(CIRCLE_5);
    Geometry inner = readCurve(CIRCLE_INT_TAN);
    assertNull("H-ANNULUS-TANGENT: not strictly inside; 1 node / d+r = R",
        CircularDiscOverlay.overlay(outer, inner, OverlayNG.DIFFERENCE));
    assertNull("H-ANNULUS-TANGENT: reverse nest is the same miss",
        CircularDiscOverlay.overlay(inner, outer, OverlayNG.DIFFERENCE));
  }

  /**
   * 7/8 remainder: large \\ small of concentric discs is the annulus,
   * exact, not densified.
   */
  public void testNestedConcentricSubIsExactAnnulus() throws Exception {
    assertAnnulus(OverlayNG.DIFFERENCE, CIRCLE_5, CIRCLE_3,
        16.0 * Math.PI);
  }

  /**
   * 6/8 remainder: XOR of concentric discs is the same annulus.
   */
  public void testNestedConcentricXorIsExactAnnulus() throws Exception {
    assertAnnulus(OverlayNG.SYMDIFFERENCE, CIRCLE_5, CIRCLE_3,
        16.0 * Math.PI);
    assertAnnulus(OverlayNG.SYMDIFFERENCE, CIRCLE_3, CIRCLE_5,
        16.0 * Math.PI);
  }

  public void testNestedCoveredBySubIsExactEmpty() throws Exception {
    Geometry a = readCurve(CIRCLE_3);
    Geometry b = readCurve(CIRCLE_5);
    OverlayNGCurve op = new OverlayNGCurve(a, b);
    Geometry sub = op.getResult(OverlayNG.DIFFERENCE);
    assertFalse("small \\ large is exact", op.isApproximate());
    assertTrue(sub.isEmpty());
  }

  /**
   * The laser result is two CircularString members, not a densified ring,
   * and matches the chord overlay's topology and area.
   */
  private void assertDiscOp(int opCode, double exactArea, int parts)
      throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_CROSSING);
    OverlayNGCurve op = new OverlayNGCurve(a, b);
    Geometry laser = op.getResult(opCode);
    assertFalse("R1.5 is exact", op.isApproximate());
    assertEquals("exact closed-form area", exactArea, laser.getArea(), EXACT);
    assertEquals("member count", parts, laser.getNumGeometries());
    assertTwoArcShell(laser.getGeometryN(0));
    if (parts == 2) assertTwoArcShell(laser.getGeometryN(1));

    Geometry chord = OverlayNGRobust.overlay(
        CurveOps.linearise(a), CurveOps.linearise(b), opCode);
    assertEquals("area vs chord overlay", chord.getArea(), laser.getArea(),
        AREA_TOL);
    // equalsTopo is the wrong ask: the laser is the true arcs, the chord
    // overlay is two inscribed rings. Hausdorff stays inside the densify
    // budget, which is the JTS-class claim.
    double hd = DiscreteHausdorffDistance.distance(
        CurveOps.linearise(laser), chord);
    assertTrue("Hausdorff vs chord overlay " + hd + " > " + AREA_TOL,
        hd <= AREA_TOL);
  }

  private void assertAnnulus(int opCode, String wktA, String wktB,
      double exactArea) throws Exception {
    Geometry a = readCurve(wktA);
    Geometry b = readCurve(wktB);
    OverlayNGCurve op = new OverlayNGCurve(a, b);
    Geometry laser = op.getResult(opCode);
    assertFalse("nested annulus is exact", op.isApproximate());
    assertEquals("CurvePolygon", laser.getGeometryType());
    CurvePolygon cp = (CurvePolygon) laser;
    assertEquals("one hole", 1, cp.getNumInteriorRing());
    assertTrue("outer is a CircularString",
        cp.getExteriorCurve() instanceof CircularString);
    assertTrue("hole is a CircularString",
        cp.getInteriorCurveN(0) instanceof CircularString);
    assertEquals("ten control points, not a densified ring",
        10, laser.getNumPoints());
    assertEquals("exact closed-form area", exactArea, laser.getArea(), EXACT);

    Geometry chord = OverlayNGRobust.overlay(
        CurveOps.linearise(a), CurveOps.linearise(b), opCode);
    assertEquals("area vs chord overlay", chord.getArea(), laser.getArea(),
        AREA_TOL);
    double hd = DiscreteHausdorffDistance.distance(
        CurveOps.linearise(laser), chord);
    assertTrue("Hausdorff vs chord overlay " + hd + " > " + AREA_TOL,
        hd <= AREA_TOL);
  }

  private static void assertTwoArcShell(Geometry g) {
    assertEquals("CurvePolygon", g.getGeometryType());
    LineString shell = ((CurvePolygon) g).getExteriorCurve();
    assertTrue("shell is a CompoundCurve of two arcs, got " + shell.getGeometryType(),
        shell instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) shell;
    assertEquals(2, cc.getNumMembers());
    assertTrue(cc.getMemberN(0) instanceof CircularString);
    assertTrue(cc.getMemberN(1) instanceof CircularString);
    assertEquals("five control points, not a densified ring", 5, g.getNumPoints());
  }
}
