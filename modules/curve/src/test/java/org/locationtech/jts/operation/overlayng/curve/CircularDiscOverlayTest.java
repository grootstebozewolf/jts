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
import org.locationtech.jts.geom.Coordinate;
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
 * the annulus (the two-disc 7/8 · 6/8 remainder), including a
 * CompoundCurve of two semicircle arcs that certifies as a disc.
 * An internal tangent nest ({@code H-ANNULUS-TANGENT}) is not
 * strictly inside and stays {@code null}. A mixed CompoundCurve
 * nest ({@code CC-NEST-ANNULUS}: stadium in a disc) is not two
 * discs: D4 stays {@code null}; R1.7 punches it. Disjoint and
 * non-disc pairs stay {@code null} so OverlayNGCurve can take R2
 * without paying this path first.
 */
public class CircularDiscOverlayTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  private static final String CIRCLE_3 =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";
  /**
   * Horizontal stadium |x|≤2, |y|≤1, strictly inside CIRCLE_5.
   * Mixed CompoundCurve (arcs + segments). Not a disc. 0 nodes.
   * Not H-ANNULUS-TANGENT, not CIRCLE_3.
   */
  private static final String STADIUM_NEST =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, -2 0, -1 1), (-1 1, 1 1), CIRCULARSTRING (1 1, 2 0, 1 -1), (1 -1, -1 -1)))";
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

  /**
   * Overlay/UX 4-control disc kit. Not a SQL/MM CircularString
   * (ISO/IEC 13249-3 odd count); constructed, not parsed from WKT.
   */
  private static CurvePolygon fourControlDisc(double ax, double ay,
      double bx, double by, double cx, double cy) {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    CircularString cs = new CircularString(
        gf.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(ax, ay), new Coordinate(bx, by),
            new Coordinate(cx, cy), new Coordinate(ax, ay)
        }), gf);
    return new CurvePolygon(cs, (LineString[]) null, gf);
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
   * Three-click circle {@code (A,B,C,A)} is a disc kit: laser area /
   * circumference, and nested overlay with a concentric disc is exact
   * (not chainsaw).
   */
  public void testThreePointCircleIsExactDiscKit() throws Exception {
    Geometry g = fourControlDisc(-5, 0, 0, 5, 5, 0);
    OverlayNGCurve op = new OverlayNGCurve(g, readCurve(CIRCLE_3));
    Geometry laser = op.getResult(OverlayNG.DIFFERENCE);
    assertFalse("4-control disc nested overlay is exact", op.isApproximate());
    assertFalse(laser.isEmpty());
    // SQL/MM getArea() of a 4-control shell is not 16π (no invented 5th
    // control). Disc-kit exactness is the isApproximate flag.
  }

  public void testUxThreePointCircleIsExactDisc() throws Exception {
    Geometry g = fourControlDisc(210, 560, 560, 700, 460, 410);
    OverlayNGCurve uxCap = new OverlayNGCurve(g, g);
    Geometry self = uxCap.getResult(OverlayNG.INTERSECTION);
    assertFalse("UX 4-control circle must be a disc kit, not chainsaw",
        uxCap.isApproximate());
    assertFalse(self.isEmpty());
  }

  /**
   * Mixed CompoundCurve nest (stadium in a CircularString disc) is
   * not two certified discs. D4 stays null. R1.7 punches it
   * after R1.5 / R1.6 miss: CAP 4+π, CUP 25π, SUB / XOR 24π−4,
   * reverse SUB empty. Do not re-encode the stadium as a two-arc
   * disc.
   */
  public void testMixedCompoundCurveNestIsPunchNotD4() throws Exception {
    Geometry outer = readCurve(CIRCLE_5);
    Geometry stadium = readCurve(STADIUM_NEST);
    assertNull("inner stadium is not a disc",
        CircularDiscOverlay.centreRadius(stadium));
    // D4 punches only certified discs. A stadium hole is not that
    // closed form; do not invent a CompoundCurve annulus noder.
    assertNull("CC-NEST-ANNULUS: mixed nest is not two discs; D4 stays null",
        CircularDiscOverlay.overlay(outer, stadium, OverlayNG.DIFFERENCE));
    assertNull("CC-NEST-ANNULUS: reverse nest is the same D4 miss",
        CircularDiscOverlay.overlay(stadium, outer, OverlayNG.DIFFERENCE));

    assertNotNull("CC-NEST-ANNULUS: R1.7 punches the 0-node mixed nest",
        CompoundCurveShellOverlay.overlay(outer, stadium, OverlayNG.DIFFERENCE));
    assertNotNull("CC-NEST-ANNULUS: reverse CAP is the inner stadium",
        CompoundCurveShellOverlay.overlay(stadium, outer, OverlayNG.INTERSECTION));

    OverlayNGCurve cap = new OverlayNGCurve(outer, stadium);
    Geometry common = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("mixed nest CAP is exact", cap.isApproximate());
    assertEquals("CAP is the stadium, 4+π", 4.0 + Math.PI, common.getArea(),
        EXACT);

    OverlayNGCurve cup = new OverlayNGCurve(outer, stadium);
    Geometry cover = cup.getResult(OverlayNG.UNION);
    assertFalse("mixed nest CUP is exact", cup.isApproximate());
    assertEquals("CUP is CIRCLE_5, 25π", 25.0 * Math.PI, cover.getArea(), EXACT);

    OverlayNGCurve rev = new OverlayNGCurve(stadium, outer);
    Geometry empty = rev.getResult(OverlayNG.DIFFERENCE);
    assertFalse("stadium \\ disc is exact", rev.isApproximate());
    assertTrue(empty.isEmpty());

    OverlayNGCurve sub = new OverlayNGCurve(outer, stadium);
    Geometry punched = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("CC-NEST-ANNULUS: public SUB is the laser, not a chordsaw",
        sub.isApproximate());
    assertEquals("CurvePolygon", punched.getGeometryType());
    CurvePolygon cp = (CurvePolygon) punched;
    assertEquals("one hole", 1, cp.getNumInteriorRing());
    assertTrue("outer stays a CircularString disc ring",
        cp.getExteriorCurve() instanceof CircularString);
    assertTrue("hole stays the CompoundCurve stadium, not a densified n-gon",
        cp.getInteriorCurveN(0) instanceof CompoundCurve);
    assertEquals("SUB is 24π − 4", 24.0 * Math.PI - 4.0, punched.getArea(),
        EXACT);
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
