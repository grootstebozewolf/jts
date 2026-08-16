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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.overlayng.OverlayNG;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * R-AA: two CircularStrings (or a lineal CompoundCurve vs a
 * CircularString) noded at circle–circle hits that lie on both sweeps.
 * CAP is those Point(s). CUP / SUB keep CircularString pieces.
 * <p>
 * Named H-* tests are waypoints: each is a green assert that a failed
 * hypothesis stays refused.
 */
public class CircularArcOverlayTest extends GeometryTestCase {

  /**
   * Circle centre (5, −7/6), r = √949 / 6. Crossing partner meets this
   * arc at (2, 3) and (8, 3); the control polylines miss.
   */
  private static final String ARC_A =
      "CIRCULARSTRING (0 0, 2 3, 10 0)";
  /** Circle centre (5, 7), r = 5. */
  private static final String ARC_B =
      "CIRCULARSTRING (1 4, 5 2, 9 4)";
  /**
   * Nested circles: A is (3, −4) r=5, B is (3, −2) r=2. No
   * circle–circle nodes. The start–end chord of A meets B at (3, 0).
   */
  private static final String ARC_CHORD_ONLY_A =
      "CIRCULARSTRING (0 0, 3 1, 6 0)";
  private static final String ARC_CHORD_ONLY_B =
      "CIRCULARSTRING (1 -2, 3 0, 5 -2)";
  /** Same circle r=5 at the origin; they overlap on a quarter-arc. */
  private static final String ARC_SAME_Q1 =
      "CIRCULARSTRING (-5 0, 0 5, 5 0)";
  private static final String ARC_SAME_Q2 =
      "CIRCULARSTRING (0 5, 5 0, 0 -5)";
  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  /** Four line–circle nodes on CIRCLE_5 (y = ±1). */
  private static final String BAND_FOUR =
      "POLYGON ((-8 -1, 8 -1, 8 1, -8 1, -8 -1))";
  private static final String HALF_UPPER =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String HALF_LOWER =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 -5, 5 0), (5 0, -5 0)))";
  /** Same circle, vertical diameter -- not complementary to HALF_UPPER. */
  private static final String HALF_RIGHT =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 -5, 5 0, 0 5), (0 5, 0 -5)))";
  private static final String CHORD_ARC =
      "LINESTRING (0 0, 2 3, 10 0)";
  private static final String COMPOUND_A =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 2 3, 10 0))";
  private static final String ARC_LOWER =
      "CIRCULARSTRING (5 0, 0 -5, -5 0)";

  private static final double EXACT = 1.0e-9;
  private static final double QUARTER = 2.5 * Math.PI;
  private static final double THREE_Q = 7.5 * Math.PI;
  private static final double HALF = 12.5 * Math.PI;
  private static final double DISC = 25.0 * Math.PI;
  private static final double BAND = 32.0;
  /** Two circular segments of CIRCLE_5 outside |y|≤1. */
  private static final double TWO_CAPS =
      2.0 * (25.0 * Math.acos(0.2) - 2.0 * Math.sqrt(6.0));
  private static final double AREA_TOL = 1.0e-3;

  public static void main(String[] args) {
    TestRunner.run(CircularArcOverlayTest.class);
  }

  public CircularArcOverlayTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testCrossingArcsCapIsExactCircleCircleNodes() throws Exception {
    Geometry a = readCurve(ARC_A);
    Geometry b = readCurve(ARC_B);
    OverlayNGCurve op = new OverlayNGCurve(a, b);
    Geometry laser = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("R-AA arc ∩ arc is exact", op.isApproximate());
    assertEquals("two nodes", 2, laser.getNumPoints());
    assertCrossingNodes(laser);
    assertFalse("lineal CAP is points, not a lens",
        laser instanceof CurvePolygon);

    Geometry viaInstance = a.intersection(b);
    assertEquals("Geometry.intersection routes (arc, arc)",
        2, viaInstance.getNumPoints());
    assertCrossingNodes(viaInstance);
  }

  public void testReverseOrderHitsTheSameLaser() throws Exception {
    Geometry a = readCurve(ARC_A);
    Geometry b = readCurve(ARC_B);
    OverlayNGCurve op = new OverlayNGCurve(b, a);
    Geometry laser = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("arc ∩ arc reverse is exact", op.isApproximate());
    assertEquals(2, laser.getNumPoints());
    assertCrossingNodes(laser);

    Geometry viaInstance = b.intersection(a);
    assertEquals(2, viaInstance.getNumPoints());
    assertCrossingNodes(viaInstance);
  }

  public void testUnionAndDifferenceKeepCircularStringPieces() throws Exception {
    Geometry a = readCurve(ARC_A);
    Geometry b = readCurve(ARC_B);
    double aLen = a.getLength();
    double bLen = b.getLength();

    OverlayNGCurve cup = new OverlayNGCurve(a, b);
    Geometry u = cup.getResult(OverlayNG.UNION);
    assertFalse("R-AA ∪ is exact", cup.isApproximate());
    assertTrue("union keeps an arc", hasCircularString(u));
    assertEquals("every piece is a CircularString",
        u.getNumGeometries(), countCircularStrings(u));
    assertEquals("union length is both (nodes have no measure)",
        aLen + bLen, u.getLength(), EXACT);

    OverlayNGCurve sub = new OverlayNGCurve(a, b);
    Geometry bite = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("R-AA arc \\ arc is exact", sub.isApproximate());
    assertTrue("difference keeps an arc", hasCircularString(bite));
    assertEquals("noding a point does not shorten the arc",
        aLen, bite.getLength(), EXACT);

    OverlayNGCurve rev = new OverlayNGCurve(b, a);
    Geometry other = rev.getResult(OverlayNG.DIFFERENCE);
    assertFalse("reverse difference is exact", rev.isApproximate());
    assertTrue(hasCircularString(other));
    assertEquals(bLen, other.getLength(), EXACT);
  }

  public void testCompoundCurveVsCircularString() throws Exception {
    Geometry cc = readCurve(COMPOUND_A);
    Geometry b = readCurve(ARC_B);
    OverlayNGCurve cap = new OverlayNGCurve(cc, b);
    Geometry nodes = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("R-AA compound ∩ arc is exact", cap.isApproximate());
    assertEquals(2, nodes.getNumPoints());
    assertCrossingNodes(nodes);
  }

  public void testLineStringOfThreePointsIsNotThisClass() throws Exception {
    Geometry chords = readCurve(CHORD_ARC);
    Geometry arc = readCurve(ARC_B);
    assertNull("a LineString of three points is not an arc",
        CircularArcOverlay.overlay(chords, arc, OverlayNG.INTERSECTION));
    assertNotNull("that pair stays on R-LL",
        CircularLineOverlay.overlay(chords, arc, OverlayNG.INTERSECTION));
  }

  /**
   * H-DISC: treating two CircularStrings as two filled discs (R1.5) is
   * false -- lineal CAP is points, not a lens.
   */
  public void testHDiscTwoArcsAreNotFilledDiscs() throws Exception {
    Geometry a = readCurve(ARC_A);
    Geometry b = readCurve(ARC_B);
    assertNull("H-DISC: CircularDiscOverlay does not fill two arcs",
        CircularDiscOverlay.overlay(a, b, OverlayNG.INTERSECTION));
    Geometry cap = CircularArcOverlay.overlay(a, b, OverlayNG.INTERSECTION);
    assertNotNull(cap);
    assertFalse("H-DISC: lineal CAP is not a CurvePolygon lens",
        cap instanceof CurvePolygon);
    assertEquals(2, cap.getNumPoints());
  }

  /**
   * H-CHORD: control-polyline nodes are not the arc nodes.
   */
  public void testHChordControlPolylineIsNotTheArc() throws Exception {
    Geometry missArcs = readCurve(ARC_CHORD_ONLY_A);
    Geometry missChords = readCurve(ARC_CHORD_ONLY_B);
    Geometry empty = CircularArcOverlay.overlay(
        missArcs, missChords, OverlayNG.INTERSECTION);
    assertNotNull("zero nodes is an answer, not a miss", empty);
    assertTrue("H-CHORD: chords meet, arcs miss → empty CAP", empty.isEmpty());

    OverlayNGCurve publicEmpty = new OverlayNGCurve(missArcs, missChords);
    Geometry viaPublic = publicEmpty.getResult(OverlayNG.INTERSECTION);
    assertFalse("chord-only miss is exact", publicEmpty.isApproximate());
    assertTrue(viaPublic.isEmpty());

    Geometry a = readCurve(ARC_A);
    Geometry b = readCurve(ARC_B);
    Geometry nodes = CircularArcOverlay.overlay(a, b, OverlayNG.INTERSECTION);
    assertNotNull(nodes);
    assertEquals("H-CHORD: arcs meet, chords miss → circle–circle points",
        2, nodes.getNumPoints());
    assertCrossingNodes(nodes);
  }

  /**
   * H-SAME-CIRCLE: two overlapping arcs of the same circle are not a
   * two-node clip -- they are angular-interval overlay on that circle.
   * CAP is the shared quarter; CUP the three-quarter; SUB the leftover
   * of Q1.
   */
  public void testHSameCircleOverlapIsIntervalLaser() throws Exception {
    Geometry a = readCurve(ARC_SAME_Q1);
    Geometry b = readCurve(ARC_SAME_Q2);
    OverlayNGCurve cap = new OverlayNGCurve(a, b);
    Geometry q = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SAME-CIRCLE CAP is exact", cap.isApproximate());
    assertTrue(q instanceof CircularString);
    assertEquals("shared quarter (0 5)→(5 0)", QUARTER, q.getLength(), EXACT);
    assertTrue(hasPointNear(q, 0.0, 5.0));
    assertTrue(hasPointNear(q, 5.0, 0.0));

    OverlayNGCurve cup = new OverlayNGCurve(a, b);
    Geometry u = cup.getResult(OverlayNG.UNION);
    assertFalse("H-SAME-CIRCLE CUP is exact", cup.isApproximate());
    assertTrue(hasCircularString(u));
    assertEquals("three-quarter", THREE_Q, u.getLength(), EXACT);

    OverlayNGCurve sub = new OverlayNGCurve(a, b);
    Geometry bite = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("H-SAME-CIRCLE SUB is exact", sub.isApproximate());
    assertEquals("leftover of Q1", QUARTER, bite.getLength(), EXACT);

    Geometry lower = readCurve(ARC_LOWER);
    OverlayNGCurve disjoint = new OverlayNGCurve(a, lower);
    Geometry pts = disjoint.getResult(OverlayNG.INTERSECTION);
    assertFalse("disjoint same-circle is exact", disjoint.isApproximate());
    assertEquals("shared endpoints only", 2, pts.getNumPoints());
  }

  /**
   * H-FOUR: four line–circle nodes of a disc vs a band assemble as
   * CompoundCurve arcs + segments, not a densified n-gon. R1.7 / R-AA
   * still refuse the pair (not their cell).
   */
  public void testHFourArealCutsAreNNodeAssemble() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry band = readCurve(BAND_FOUR);
    OverlayNGCurve cap = new OverlayNGCurve(disc, band);
    Geometry clip = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-FOUR CAP is exact", cap.isApproximate());
    assertTrue(clip instanceof CurvePolygon);
    assertTrue("shell keeps an arc", hasCircularString(clip));
    assertEquals(DISC - TWO_CAPS, clip.getArea(), AREA_TOL);

    OverlayNGCurve cup = new OverlayNGCurve(disc, band);
    Geometry blob = cup.getResult(OverlayNG.UNION);
    assertFalse("H-FOUR CUP is exact", cup.isApproximate());
    assertEquals(BAND + TWO_CAPS, blob.getArea(), AREA_TOL);

    assertNull("H-FOUR: R1.7 is not this pair",
        CompoundCurveShellOverlay.overlay(disc, band, OverlayNG.INTERSECTION));
    assertNull("H-FOUR: R-AA is lineal",
        CircularArcOverlay.overlay(disc, band, OverlayNG.INTERSECTION));
  }

  /**
   * H-SHELL: complementary half-discs of the same circle are CAP empty
   * / CUP the disc / SUB the first half. Perpendicular same-circle
   * halves assemble as sectors. Two hole-free shells with exactly two
   * proper nodes walk the surviving arcs. Collinear same-side halves
   * are the half-lens. An even 4+ alternating cut is the n-span
   * assemble. Two crossings plus a tangent is the same assemble
   * with the touch as a zero-length span. A same-outer hole-inside
   * pair is the holed cell. A different-outer hole composes when
   * it sits strictly inside or outside a certified outer CAP.
   * Collinear overlap, mixed labels, and a hole that meets the
   * other diameter stay refused. A hole that straddles the other
   * shell is a bite when the new edge ⊂ that shell.
   */
  public void testHShellComplementaryHalfDiscsAreTheDisc() throws Exception {
    Geometry upper = readCurve(HALF_UPPER);
    Geometry lower = readCurve(HALF_LOWER);
    OverlayNGCurve cap = new OverlayNGCurve(upper, lower);
    Geometry empty = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL CAP is exact", cap.isApproximate());
    assertTrue("SFS interiors are disjoint", empty.isEmpty());

    OverlayNGCurve cup = new OverlayNGCurve(upper, lower);
    Geometry disc = cup.getResult(OverlayNG.UNION);
    assertFalse("H-SHELL CUP is exact", cup.isApproximate());
    assertTrue(disc instanceof CurvePolygon);
    assertEquals(DISC, disc.getArea(), EXACT);
    assertNotNull("CUP is the supporting disc",
        CircularDiscOverlay.centreRadius(disc));

    OverlayNGCurve sub = new OverlayNGCurve(upper, lower);
    Geometry half = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("H-SHELL SUB is exact", sub.isApproximate());
    assertEquals(HALF, half.getArea(), EXACT);

    OverlayNGCurve xor = new OverlayNGCurve(upper, lower);
    Geometry both = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("H-SHELL XOR is exact", xor.isApproximate());
    assertEquals(DISC, both.getArea(), EXACT);

    assertNull("H-SHELL: R-AA is lineal",
        CircularArcOverlay.overlay(upper, lower, OverlayNG.INTERSECTION));

    Geometry right = readCurve(HALF_RIGHT);
    OverlayNGCurve qCap = new OverlayNGCurve(upper, right);
    Geometry quarter = qCap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL upper ∩ right is exact", qCap.isApproximate());
    assertEquals("quarter-disc", 6.25 * Math.PI, quarter.getArea(), EXACT);
    assertTrue(hasCircularString(quarter));

    OverlayNGCurve qCup = new OverlayNGCurve(upper, right);
    Geometry threeQ = qCup.getResult(OverlayNG.UNION);
    assertFalse("H-SHELL upper ∪ right is exact", qCup.isApproximate());
    assertEquals("three-quarter disc", 18.75 * Math.PI, threeQ.getArea(), EXACT);

    Geometry hanging = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 8, 0 3, 5 8), (5 8, -5 8)))");
    OverlayNGCurve lensCap = new OverlayNGCurve(upper, hanging);
    Geometry lens = lensCap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL two-shell CAP is exact", lensCap.isApproximate());
    assertEquals("lens of r=5, d=8", 50.0 * Math.acos(0.8) - 24.0,
        lens.getArea(), EXACT);
    assertTrue("two-shell CAP keeps an arc", hasCircularString(lens));

    Geometry collinear = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (2 0, 7 5, 12 0), (12 0, 2 0)))");
    OverlayNGCurve colCap = new OverlayNGCurve(upper, collinear);
    Geometry halfLens = colCap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-COLLINEAR CAP is exact", colCap.isApproximate());
    assertEquals("upper half-lens r=5 d=7",
        25.0 * Math.acos(0.7) - 0.25 * 7.0 * Math.sqrt(51.0),
        halfLens.getArea(), EXACT);
    assertTrue("collinear CAP keeps an arc", hasCircularString(halfLens));

    Geometry fourCut = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, 0 -2, 1 -1), (1 -1, 1 6), CIRCULARSTRING (1 6, 0 7, -1 6), (-1 6, -1 -1)))");
    OverlayNGCurve nCap = new OverlayNGCurve(upper, fourCut);
    Geometry strip = nCap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-N CAP is exact", nCap.isApproximate());
    assertEquals("strip |x|≤1 of the half-disc",
        25.0 * Math.asin(0.2) + 2.0 * Math.sqrt(6.0), strip.getArea(), EXACT);
    assertTrue("n-span CAP keeps an arc", hasCircularString(strip));

    Geometry holed = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0 1, 1 1, 1 2, 0 2, 0 1))");
    OverlayNGCurve holeCap = new OverlayNGCurve(holed, upper);
    Geometry holedHalf = holeCap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-HOLE CAP is exact", holeCap.isApproximate());
    assertEquals("holed half", HALF - 1.0, holedHalf.getArea(), EXACT);

    Geometry small = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-3 0, 0 3, 3 0), (3 0, -3 0)))");
    OverlayNGCurve nestedCap = new OverlayNGCurve(holed, small);
    Geometry punched = nestedCap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-HOLE-OUTER inside CAP is exact",
        nestedCap.isApproximate());
    assertEquals("small half minus hole", 4.5 * Math.PI - 1.0,
        punched.getArea(), EXACT);

    OverlayNGCurve diameterCap = new OverlayNGCurve(holed, right);
    Geometry diameterBite = diameterCap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-HOLE-OUTER: hole meets the other diameter",
        diameterCap.isApproximate());
    assertEquals("Q1 minus the rectangle", 6.25 * Math.PI - 1.0,
        diameterBite.getArea(), EXACT);
    Geometry straddle = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (-1 1, 1 1, 1 2, -1 2, -1 1))");
    OverlayNGCurve crossCap = new OverlayNGCurve(straddle, right);
    Geometry bite = crossCap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-HOLE-CROSS: new edge ⊂ other.shell is a bite",
        crossCap.isApproximate());
    assertEquals("Q1 minus the right half-rectangle", 6.25 * Math.PI - 1.0,
        bite.getArea(), EXACT);
    Geometry holeX = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0.5 0.5, 1.5 0.5, 1.5 1.5, 0.5 1.5, 0.5 0.5))");
    OverlayNGCurve holeXCap = new OverlayNGCurve(holed, holeX);
    Geometry twoHoles = holeXCap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-HOLE-X CAP is exact", holeXCap.isApproximate());
    assertEquals("HALF_DISC minus holeA ∪ holeB", HALF - 1.75,
        twoHoles.getArea(), EXACT);
    Geometry onDiameter = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 1, 0 2, 1 1), (1 1, 1 0), (1 0, -1 0), (-1 0, -1 1)))");
    // Collinear overlap is not a discrete node set; no cheap closed
    // form without a noder.
    assertNull("H-SHELL-N-MIXED: collinear overlap stays refused",
        CompoundCurveShellOverlay.overlay(upper, onDiameter, OverlayNG.INTERSECTION));
    Geometry stadiumNest = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, -2 0, -1 1), (-1 1, 1 1), CIRCULARSTRING (1 1, 2 0, 1 -1), (1 -1, -1 -1)))");
    Geometry circle5 = readCurve(CIRCLE_5);
    // Mixed stadium in CIRCLE_5 is not two discs. D4 / R1.7 stay
    // null; do not punch a non-disc hole.
    assertNull("CC-NEST-ANNULUS: mixed nest is not two discs",
        CircularDiscOverlay.overlay(circle5, stadiumNest, OverlayNG.DIFFERENCE));
    assertNull("CC-NEST-ANNULUS: R1.7 is two-node, not a 0-node punch",
        CompoundCurveShellOverlay.overlay(circle5, stadiumNest, OverlayNG.DIFFERENCE));
    Geometry oddStadium = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 4, 0 5, 1 4), (1 4, 1 -1), CIRCULARSTRING (1 -1, 0 -2, -1 -1), (-1 -1, -1 4)))");
    OverlayNGCurve oddCap = new OverlayNGCurve(upper, oddStadium);
    Geometry oddClip = oddCap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-N-ODD CAP is exact", oddCap.isApproximate());
    assertEquals("stadium above the diameter", 8.0 + 0.5 * Math.PI,
        oddClip.getArea(), EXACT);
    assertTrue("odd-n CAP keeps an arc", hasCircularString(oddClip));
  }

  public void testRllStillRefusesTwoArcs() throws Exception {
    Geometry a = readCurve(ARC_A);
    Geometry b = readCurve(ARC_B);
    assertNull("R-LL's two-arc refusal is unchanged",
        CircularLineOverlay.overlay(a, b, OverlayNG.INTERSECTION));
  }

  private static void assertCrossingNodes(Geometry g) {
    assertTrue("left node (2, 3)", hasPointNear(g, 2.0, 3.0));
    assertTrue("right node (8, 3)", hasPointNear(g, 8.0, 3.0));
  }

  private static boolean hasPointNear(Geometry g, double x, double y) {
    Coordinate want = new Coordinate(x, y);
    for (int i = 0; i < g.getNumGeometries(); i++) {
      Geometry p = g.getGeometryN(i);
      if (p.getNumPoints() == 1 && p.getCoordinate().distance(want) <= EXACT) {
        return true;
      }
      Coordinate[] c = p.getCoordinates();
      for (int k = 0; k < c.length; k++) {
        if (c[k].distance(want) <= EXACT) return true;
      }
    }
    return false;
  }

  private static boolean hasCircularString(Geometry g) {
    return countCircularStrings(g) > 0;
  }

  private static int countCircularStrings(Geometry g) {
    int n = 0;
    for (int i = 0; i < g.getNumGeometries(); i++) {
      n += countIn(g.getGeometryN(i));
    }
    return n;
  }

  private static int countIn(Geometry m) {
    if (m instanceof CircularString) return 1;
    if (m instanceof CompoundCurve) {
      int n = 0;
      CompoundCurve cc = (CompoundCurve) m;
      for (int k = 0; k < cc.getNumMembers(); k++) {
        if (cc.getMemberN(k) instanceof CircularString) n++;
      }
      return n;
    }
    if (m instanceof CurvePolygon) {
      return countIn(((CurvePolygon) m).getExteriorCurve());
    }
    return 0;
  }
}
