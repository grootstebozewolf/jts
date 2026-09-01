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
 * R1.7: a CompoundCurve-shelled CurvePolygon (half-disc) clipped by a
 * circular disc or a plain polygon at two nodes. CAP / CUP / SUB / XOR
 * keep the surviving arc, exact, and JTS-class with the chord overlay.
 * Two CompoundCurve shells walk at 0 / 1 / 2 nodes, or an even 4+
 * alternating n-span. Two crossings plus a tangent is the same
 * n-span with the touch as a zero-length span. A same-outer
 * hole-inside pair is the holed cell. A different-outer hole
 * composes when it sits strictly inside or outside a certified
 * outer CAP. A hole that straddles the other shell shares the
 * clip edge: if that new edge is a subset of the other shell
 * it is a bite, not an interior punch. A hole that does not
 * cross but whose ring overlaps the other shell is the same
 * bite. Two holes that cross
 * on the same outer are the two-hole arrangement. A
 * three-point LineString is not an arc. A 0-node mixed shell
 * strictly inside a circular disc is the nest punch
 * ({@code CC-NEST-ANNULUS}: P2.3 cousin, not a noder, not D4).
 * Collinear overlap and mixed labels stay {@code null} so OverlayNGCurve can
 * take R2 without paying this path first.
 */
public class CompoundCurveShellOverlayTest extends GeometryTestCase {

  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  /** Axis-aligned cap cut: y = 2 through the upper half-disc. */
  private static final String SQUARE_CAP =
      "POLYGON ((-6 2, 6 2, 6 10, -6 10, -6 2))";
  /**
   * Same outline as {@link #HALF_DISC} but the "semicircle" is a
   * three-point LineString. That member is two chords, not an arc.
   */
  private static final String CHORD_SHELL =
      "CURVEPOLYGON (COMPOUNDCURVE ((-5 0, 0 5, 5 0), (5 0, -5 0)))";

  /** Upper half of the two-disc lens (r=5, d=7). */
  private static final double HALF_LENS =
      25.0 * Math.acos(0.7) - 0.25 * 7.0 * Math.sqrt(51.0);
  /** Circular cap of CIRCLE_5 above y=2: 25 acos(0.4) − 2√21. */
  private static final double CAP =
      25.0 * Math.acos(0.4) - 2.0 * Math.sqrt(21.0);
  private static final double HALF = 12.5 * Math.PI;
  private static final double DISC = 25.0 * Math.PI;
  private static final double SQUARE = 96.0;
  private static final double AREA_TOL = 1.0e-3;
  private static final double EXACT = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(CompoundCurveShellOverlayTest.class);
  }

  public CompoundCurveShellOverlayTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testHalfDiscCapVsCrossingDiscKeepsTheArc() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_CROSSING);
    OverlayNGCurve op = new OverlayNGCurve(half, disc);
    Geometry laser = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("R1.7 half ∩ disc is exact", op.isApproximate());
    assertEquals("upper half-lens", HALF_LENS, laser.getArea(), EXACT);
    assertArcAndLineShell(laser);
    assertParity(half, disc, OverlayNG.INTERSECTION, laser);

    Geometry viaInstance = half.intersection(disc);
    assertEquals("Geometry.intersection routes (half, disc)",
        HALF_LENS, viaInstance.getArea(), EXACT);
    assertArcAndLineShell(viaInstance);
  }

  public void testHalfDiscCapVsSquareIsCompoundCurveNotNGon() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry square = readCurve(SQUARE_CAP);
    OverlayNGCurve op = new OverlayNGCurve(half, square);
    Geometry laser = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("R1.7 half ∩ square is exact", op.isApproximate());
    assertEquals("circular cap above y=2", CAP, laser.getArea(), EXACT);
    assertArcAndLineShell(laser);
    assertParity(half, square, OverlayNG.INTERSECTION, laser);
  }

  public void testReverseOrderHitsTheSameLaser() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_CROSSING);
    Geometry square = readCurve(SQUARE_CAP);

    OverlayNGCurve vsDisc = new OverlayNGCurve(disc, half);
    Geometry capDisc = vsDisc.getResult(OverlayNG.INTERSECTION);
    assertFalse("disc ∩ half is exact", vsDisc.isApproximate());
    assertEquals("reverse half ∩ disc", HALF_LENS, capDisc.getArea(), EXACT);
    assertArcAndLineShell(capDisc);

    OverlayNGCurve vsSquare = new OverlayNGCurve(square, half);
    Geometry capSq = vsSquare.getResult(OverlayNG.INTERSECTION);
    assertFalse("square ∩ half is exact", vsSquare.isApproximate());
    assertEquals("reverse half ∩ square", CAP, capSq.getArea(), EXACT);
    assertArcAndLineShell(capSq);
  }

  public void testHalfDiscCupVsCrossingDisc() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_CROSSING);
    OverlayNGCurve op = new OverlayNGCurve(half, disc);
    Geometry u = op.getResult(OverlayNG.UNION);
    assertFalse("R1.7 half ∪ disc is exact", op.isApproximate());
    assertEquals("half plus disc minus upper half-lens",
        HALF + DISC - HALF_LENS, u.getArea(), EXACT);
    assertArcShell(u);
    assertParity(half, disc, OverlayNG.UNION, u);
  }

  public void testHalfDiscSubBiteVsCrossingDisc() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_CROSSING);
    OverlayNGCurve op = new OverlayNGCurve(half, disc);
    Geometry bite = op.getResult(OverlayNG.DIFFERENCE);
    assertFalse("R1.7 half \\ disc is exact", op.isApproximate());
    assertEquals("half-disc minus upper half-lens", HALF - HALF_LENS,
        bite.getArea(), EXACT);
    assertArcAndLineShell(bite);
    assertParity(half, disc, OverlayNG.DIFFERENCE, bite);

    OverlayNGCurve rev = new OverlayNGCurve(disc, half);
    Geometry otherBite = rev.getResult(OverlayNG.DIFFERENCE);
    assertFalse("disc \\ half is exact", rev.isApproximate());
    assertEquals("crossing disc minus upper half-lens", DISC - HALF_LENS,
        otherBite.getArea(), EXACT);
    assertArcShell(otherBite);
  }

  public void testHalfDiscSubBiteVsSquare() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry square = readCurve(SQUARE_CAP);
    OverlayNGCurve op = new OverlayNGCurve(half, square);
    Geometry bite = op.getResult(OverlayNG.DIFFERENCE);
    assertFalse("R1.7 half \\ square is exact", op.isApproximate());
    assertEquals("half-disc below y=2", HALF - CAP, bite.getArea(), EXACT);
    assertArcAndLineShell(bite);
    assertParity(half, square, OverlayNG.DIFFERENCE, bite);
  }

  public void testHalfDiscCupAndXorVsSquare() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry square = readCurve(SQUARE_CAP);
    OverlayNGCurve cup = new OverlayNGCurve(half, square);
    Geometry u = cup.getResult(OverlayNG.UNION);
    assertFalse("R1.7 half ∪ square is exact", cup.isApproximate());
    assertEquals("square plus the band below y=2", SQUARE + HALF - CAP,
        u.getArea(), EXACT);
    assertArcAndLineShell(u);

    OverlayNGCurve xor = new OverlayNGCurve(half, square);
    Geometry x = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("R1.7 half XOR square is exact", xor.isApproximate());
    assertEquals("both bites", SQUARE + HALF - 2.0 * CAP, x.getArea(), EXACT);
    assertEquals("two members", 2, x.getNumGeometries());
    assertArcAndLineShell(x.getGeometryN(0));
    assertArcAndLineShell(x.getGeometryN(1));
  }

  public void testLineStringOfThreePointsIsNotAnArc() throws Exception {
    Geometry chords = readCurve(CHORD_SHELL);
    Geometry disc = readCurve(CIRCLE_CROSSING);
    assertNull("a LineString member is not an arc",
        CompoundCurveShellOverlay.overlay(chords, disc, OverlayNG.INTERSECTION));
    OverlayNGCurve op = new OverlayNGCurve(chords, disc);
    Geometry r = op.getResult(OverlayNG.INTERSECTION);
    assertTrue("three-point LINESTRING shell falls to R2", op.isApproximate());
    assertFalse("chord overlay is still non-empty", r.isEmpty());
  }

  /**
   * Lower half of the circle at (0, 8), r=5. Meets {@link #HALF_DISC}
   * at (±3, 4) only -- two proper nodes, not the complementary pair.
   */
  private static final String HALF_HANGING =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 8, 0 3, 5 8), (5 8, -5 8)))";
  /** Two circles r=5, d=8: lens = 50 acos(0.8) − 24. */
  private static final double LENS = 50.0 * Math.acos(0.8) - 24.0;
  private static final String HALF_RIGHT =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 -5, 5 0, 0 5), (0 5, 0 -5)))";
  /** Upper half of CIRCLE_CROSSING -- collinear diameters with HALF_DISC. */
  private static final String HALF_CROSSING_UPPER =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (2 0, 7 5, 12 0), (12 0, 2 0)))";
  /** Nested same-side half: r=3 at the origin, diameter inside HALF_DISC. */
  private static final String HALF_SMALL =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-3 0, 0 3, 3 0), (3 0, -3 0)))";
  /** External point-touch at (5, 0): right half of the circle at (5, 0). */
  private static final String HALF_TOUCH =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (5 -5, 10 0, 5 5), (5 5, 5 -5)))";
  /** Stadium strictly inside HALF_DISC -- 0 nodes, containment. */
  private static final String STADIUM_IN =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 1, -2 2, -1 3), (-1 3, 1 3), CIRCULARSTRING (1 3, 2 2, 1 1), (1 1, -1 1)))";
  /** Vertical stadium: 4 line–circle nodes on HALF_DISC. */
  private static final String STADIUM_FOUR =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, 0 -2, 1 -1), (1 -1, 1 6), CIRCULARSTRING (1 6, 0 7, -1 6), (-1 6, -1 -1)))";
  /**
   * Vertical stadium whose top cap is tangent to HALF_DISC at (0, 5)
   * and whose sides cross the diameter at (±1, 0): 2 crossings + 1
   * tangent.
   */
  private static final String STADIUM_ODD =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 4, 0 5, 1 4), (1 4, 1 -1), CIRCULARSTRING (1 -1, 0 -2, -1 -1), (-1 -1, -1 4)))";
  private static final String HALF_HOLED =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0 1, 1 1, 1 2, 0 2, 0 1))";
  private static final double SMALL_HALF = 4.5 * Math.PI;
  private static final double STADIUM_AREA = 4.0 + Math.PI;
  /** HALF_DISC ∩ |x|≤1, y≥0: 25 asin(0.2) + 2√6. */
  private static final double FOUR_CAP =
      25.0 * Math.asin(0.2) + 2.0 * Math.sqrt(6.0);
  /** Vertical stadium r=1, length 7 plus two caps. */
  private static final double STADIUM_FOUR_AREA = 14.0 + Math.PI;
  /** Vertical stadium r=1, straight length 5 plus two caps. */
  private static final double STADIUM_ODD_AREA = 10.0 + Math.PI;
  /** HALF_DISC ∩ STADIUM_ODD: rectangle |x|≤1, 0≤y≤4 plus the top cap. */
  private static final double ODD_CAP = 8.0 + 0.5 * Math.PI;

  public void testOverlappingSameCircleHalvesAreSectors() throws Exception {
    Geometry upper = readCurve(HALF_DISC);
    Geometry right = readCurve(HALF_RIGHT);
    OverlayNGCurve cap = new OverlayNGCurve(upper, right);
    Geometry q = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("upper ∩ right is exact", cap.isApproximate());
    assertEquals("quarter-disc", 6.25 * Math.PI, q.getArea(), EXACT);
    assertArcAndLineShell(q);
    assertParity(upper, right, OverlayNG.INTERSECTION, q);

    OverlayNGCurve cup = new OverlayNGCurve(upper, right);
    Geometry u = cup.getResult(OverlayNG.UNION);
    assertFalse("upper ∪ right is exact", cup.isApproximate());
    assertEquals("three-quarter disc", 18.75 * Math.PI, u.getArea(), EXACT);
    assertArcAndLineShell(u);

    OverlayNGCurve sub = new OverlayNGCurve(upper, right);
    Geometry bite = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("upper \\ right is exact", sub.isApproximate());
    assertEquals("Q2 quarter", 6.25 * Math.PI, bite.getArea(), EXACT);

    OverlayNGCurve xor = new OverlayNGCurve(upper, right);
    Geometry x = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("upper XOR right is exact", xor.isApproximate());
    assertEquals("Q2 ∪ Q4", 12.5 * Math.PI, x.getArea(), EXACT);
    assertEquals("two members", 2, x.getNumGeometries());
  }

  public void testOverlappingHalvesReverseOrder() throws Exception {
    Geometry upper = readCurve(HALF_DISC);
    Geometry right = readCurve(HALF_RIGHT);
    OverlayNGCurve cap = new OverlayNGCurve(right, upper);
    Geometry q = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("right ∩ upper is exact", cap.isApproximate());
    assertEquals("quarter-disc reverse", 6.25 * Math.PI, q.getArea(), EXACT);
    assertArcAndLineShell(q);

    OverlayNGCurve sub = new OverlayNGCurve(right, upper);
    Geometry bite = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("right \\ upper is exact", sub.isApproximate());
    assertEquals("Q4 quarter", 6.25 * Math.PI, bite.getArea(), EXACT);
  }

  public void testTwoShellLensKeepsArcs() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(HALF_HANGING);
    OverlayNGCurve cap = new OverlayNGCurve(a, b);
    Geometry lens = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("two-shell CAP is exact", cap.isApproximate());
    assertEquals("lens of r=5, d=8", LENS, lens.getArea(), EXACT);
    assertArcShell(lens);
    assertParity(a, b, OverlayNG.INTERSECTION, lens);

    OverlayNGCurve cup = new OverlayNGCurve(a, b);
    Geometry u = cup.getResult(OverlayNG.UNION);
    assertFalse("two-shell CUP is exact", cup.isApproximate());
    assertEquals("two halves minus lens", 2.0 * HALF - LENS, u.getArea(), EXACT);
    assertArcAndLineShell(u);
    assertTrue("CUP keeps an arc, not a densified n-gon",
        u.getNumPoints() < 20);

    OverlayNGCurve sub = new OverlayNGCurve(a, b);
    Geometry bite = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("two-shell SUB is exact", sub.isApproximate());
    assertEquals("half minus lens", HALF - LENS, bite.getArea(), EXACT);
    assertArcAndLineShell(bite);
  }

  public void testTwoShellLensReverseOrder() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(HALF_HANGING);
    OverlayNGCurve cap = new OverlayNGCurve(b, a);
    Geometry lens = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("reverse two-shell CAP is exact", cap.isApproximate());
    assertEquals("lens reverse", LENS, lens.getArea(), EXACT);
    assertArcShell(lens);

    OverlayNGCurve sub = new OverlayNGCurve(b, a);
    Geometry bite = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("reverse two-shell SUB is exact", sub.isApproximate());
    assertEquals("hanging half minus lens", HALF - LENS, bite.getArea(), EXACT);
  }

  public void testCollinearCrossingHalvesAreTheHalfLens() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(HALF_CROSSING_UPPER);
    OverlayNGCurve cap = new OverlayNGCurve(a, b);
    Geometry lens = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("collinear CAP is exact", cap.isApproximate());
    assertEquals("upper half-lens r=5 d=7", HALF_LENS, lens.getArea(), EXACT);
    assertArcAndLineShell(lens);
    assertParity(a, b, OverlayNG.INTERSECTION, lens);

    OverlayNGCurve cup = new OverlayNGCurve(a, b);
    Geometry u = cup.getResult(OverlayNG.UNION);
    assertFalse("collinear CUP is exact", cup.isApproximate());
    assertEquals("two halves minus half-lens", 2.0 * HALF - HALF_LENS,
        u.getArea(), EXACT);
    assertArcAndLineShell(u);

    OverlayNGCurve sub = new OverlayNGCurve(a, b);
    Geometry bite = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("collinear SUB is exact", sub.isApproximate());
    assertEquals("half minus half-lens", HALF - HALF_LENS, bite.getArea(),
        EXACT);
  }

  public void testCollinearCrossingReverseOrder() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(HALF_CROSSING_UPPER);
    OverlayNGCurve cap = new OverlayNGCurve(b, a);
    Geometry lens = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("collinear reverse CAP is exact", cap.isApproximate());
    assertEquals("half-lens reverse", HALF_LENS, lens.getArea(), EXACT);
  }

  public void testNestedSameSideHalves() throws Exception {
    Geometry large = readCurve(HALF_DISC);
    Geometry small = readCurve(HALF_SMALL);
    OverlayNGCurve cap = new OverlayNGCurve(large, small);
    Geometry inner = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("nested CAP is exact", cap.isApproximate());
    assertEquals("smaller half", SMALL_HALF, inner.getArea(), EXACT);

    OverlayNGCurve cup = new OverlayNGCurve(large, small);
    Geometry outer = cup.getResult(OverlayNG.UNION);
    assertFalse("nested CUP is exact", cup.isApproximate());
    assertEquals("larger half", HALF, outer.getArea(), EXACT);

    OverlayNGCurve sub = new OverlayNGCurve(large, small);
    Geometry ring = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("nested SUB is exact", sub.isApproximate());
    assertEquals("half-annulus", HALF - SMALL_HALF, ring.getArea(), EXACT);
    assertEquals("one hole", 1, ((CurvePolygon) ring).getNumInteriorRing());

    OverlayNGCurve rev = new OverlayNGCurve(small, large);
    Geometry empty = rev.getResult(OverlayNG.DIFFERENCE);
    assertFalse("small \\ large is exact", rev.isApproximate());
    assertTrue(empty.isEmpty());
  }

  public void testOneNodeTouchIsDisjointInteriors() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(HALF_TOUCH);
    OverlayNGCurve cap = new OverlayNGCurve(a, b);
    Geometry empty = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-TOUCH CAP is exact", cap.isApproximate());
    assertTrue("interiors are disjoint", empty.isEmpty());

    OverlayNGCurve cup = new OverlayNGCurve(a, b);
    Geometry both = cup.getResult(OverlayNG.UNION);
    assertFalse("touch CUP is exact", cup.isApproximate());
    assertEquals("two members", 2, both.getNumGeometries());
    assertEquals("both halves", 2.0 * HALF, both.getArea(), EXACT);

    OverlayNGCurve sub = new OverlayNGCurve(a, b);
    Geometry first = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("touch SUB is exact", sub.isApproximate());
    assertEquals(HALF, first.getArea(), EXACT);
  }

  public void testZeroNodeContainmentKeepsTheInnerShell() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry stadium = readCurve(STADIUM_IN);
    OverlayNGCurve cap = new OverlayNGCurve(half, stadium);
    Geometry inner = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("0-node CAP is exact", cap.isApproximate());
    assertEquals("inner stadium", STADIUM_AREA, inner.getArea(), EXACT);
    assertArcAndLineShell(inner);

    OverlayNGCurve cup = new OverlayNGCurve(half, stadium);
    Geometry outer = cup.getResult(OverlayNG.UNION);
    assertFalse("0-node CUP is exact", cup.isApproximate());
    assertEquals("outer half", HALF, outer.getArea(), EXACT);
  }

  public void testFourNodeStadiumIsNSpanAssemble() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry stadium = readCurve(STADIUM_FOUR);
    OverlayNGCurve cap = new OverlayNGCurve(half, stadium);
    Geometry clip = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-N CAP is exact", cap.isApproximate());
    assertEquals("strip of the half-disc |x|≤1", FOUR_CAP, clip.getArea(),
        EXACT);
    assertArcAndLineShell(clip);
    assertParity(half, stadium, OverlayNG.INTERSECTION, clip);

    OverlayNGCurve cup = new OverlayNGCurve(half, stadium);
    Geometry blob = cup.getResult(OverlayNG.UNION);
    assertFalse("H-SHELL-N CUP is exact", cup.isApproximate());
    assertEquals("half plus stadium minus strip",
        HALF + STADIUM_FOUR_AREA - FOUR_CAP, blob.getArea(), EXACT);
    assertTrue("CUP keeps an arc", blob.getNumPoints() < 30);

    OverlayNGCurve rev = new OverlayNGCurve(stadium, half);
    Geometry revCap = rev.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-N reverse CAP is exact", rev.isApproximate());
    assertEquals("strip reverse", FOUR_CAP, revCap.getArea(), EXACT);

    OverlayNGCurve sub = new OverlayNGCurve(half, stadium);
    Geometry ears = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("H-SHELL-N SUB is exact", sub.isApproximate());
    assertEquals("two circular ears", HALF - FOUR_CAP, ears.getArea(), EXACT);
    assertEquals("two members", 2, ears.getNumGeometries());

    OverlayNGCurve xor = new OverlayNGCurve(half, stadium);
    Geometry x = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("H-SHELL-N XOR is exact", xor.isApproximate());
    assertEquals("ears plus stadium bites",
        HALF + STADIUM_FOUR_AREA - 2.0 * FOUR_CAP, x.getArea(), EXACT);
  }

  public void testOddNodeStadiumIsTangentDegenerateSpan() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry stadium = readCurve(STADIUM_ODD);
    OverlayNGCurve cap = new OverlayNGCurve(half, stadium);
    Geometry clip = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-N-ODD CAP is exact", cap.isApproximate());
    assertEquals("stadium above the diameter", ODD_CAP, clip.getArea(), EXACT);
    assertArcAndLineShell(clip);
    assertParity(half, stadium, OverlayNG.INTERSECTION, clip);

    OverlayNGCurve cup = new OverlayNGCurve(half, stadium);
    Geometry blob = cup.getResult(OverlayNG.UNION);
    assertFalse("H-SHELL-N-ODD CUP is exact", cup.isApproximate());
    assertEquals("half plus stadium minus the cap",
        HALF + STADIUM_ODD_AREA - ODD_CAP, blob.getArea(), EXACT);
    assertTrue("CUP keeps an arc", blob.getNumPoints() < 30);

    OverlayNGCurve rev = new OverlayNGCurve(stadium, half);
    Geometry revCap = rev.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-N-ODD reverse CAP is exact", rev.isApproximate());
    assertEquals("cap reverse", ODD_CAP, revCap.getArea(), EXACT);

    OverlayNGCurve sub = new OverlayNGCurve(half, stadium);
    Geometry ears = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("H-SHELL-N-ODD SUB is exact", sub.isApproximate());
    assertEquals("two circular ears", HALF - ODD_CAP, ears.getArea(), EXACT);
    assertEquals("two members", 2, ears.getNumGeometries());

    OverlayNGCurve bite = new OverlayNGCurve(stadium, half);
    Geometry bottom = bite.getResult(OverlayNG.DIFFERENCE);
    assertFalse("H-SHELL-N-ODD reverse SUB is exact", bite.isApproximate());
    assertEquals("stadium below the diameter",
        STADIUM_ODD_AREA - ODD_CAP, bottom.getArea(), EXACT);

    OverlayNGCurve xor = new OverlayNGCurve(half, stadium);
    Geometry x = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("H-SHELL-N-ODD XOR is exact", xor.isApproximate());
    assertEquals("ears plus the bottom bite",
        HALF + STADIUM_ODD_AREA - 2.0 * ODD_CAP, x.getArea(), EXACT);
  }

  public void testSameOuterHoleIsTheHoledHalf() throws Exception {
    Geometry holed = readCurve(HALF_HOLED);
    Geometry half = readCurve(HALF_DISC);
    OverlayNGCurve cap = new OverlayNGCurve(holed, half);
    Geometry inner = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-HOLE CAP is exact", cap.isApproximate());
    assertEquals("holed half", HALF - 1.0, inner.getArea(), EXACT);
    assertEquals("keeps the hole", 1,
        ((CurvePolygon) inner).getNumInteriorRing());

    OverlayNGCurve cup = new OverlayNGCurve(holed, half);
    Geometry outer = cup.getResult(OverlayNG.UNION);
    assertFalse("H-SHELL-HOLE CUP is exact", cup.isApproximate());
    assertEquals("unholed half", HALF, outer.getArea(), EXACT);

    OverlayNGCurve sub = new OverlayNGCurve(holed, half);
    Geometry empty = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("holed \\ half is exact", sub.isApproximate());
    assertTrue(empty.isEmpty());

    OverlayNGCurve bite = new OverlayNGCurve(half, holed);
    Geometry rect = bite.getResult(OverlayNG.DIFFERENCE);
    assertFalse("half \\ holed is exact", bite.isApproximate());
    assertEquals("the rectangular hole", 1.0, rect.getArea(), EXACT);

    OverlayNGCurve xor = new OverlayNGCurve(holed, half);
    Geometry hole = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("H-SHELL-HOLE XOR is exact", xor.isApproximate());
    assertEquals("XOR is the hole", 1.0, hole.getArea(), EXACT);
  }

  public void testDifferentOuterHoleInsideCapIsPunched() throws Exception {
    Geometry holed = readCurve(HALF_HOLED);
    Geometry small = readCurve(HALF_SMALL);
    OverlayNGCurve cap = new OverlayNGCurve(holed, small);
    Geometry inner = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-HOLE-OUTER inside CAP is exact", cap.isApproximate());
    assertEquals("small half minus the rectangle", SMALL_HALF - 1.0,
        inner.getArea(), EXACT);
    assertEquals("keeps the hole", 1,
        ((CurvePolygon) inner).getNumInteriorRing());

    OverlayNGCurve cup = new OverlayNGCurve(holed, small);
    Geometry outer = cup.getResult(OverlayNG.UNION);
    assertFalse("inside-CAP CUP is exact", cup.isApproximate());
    assertEquals("large half, hole filled", HALF, outer.getArea(), EXACT);

    OverlayNGCurve bite = new OverlayNGCurve(small, holed);
    Geometry rect = bite.getResult(OverlayNG.DIFFERENCE);
    assertFalse("small \\ holed is exact", bite.isApproximate());
    assertEquals("the rectangular hole", 1.0, rect.getArea(), EXACT);

    OverlayNGCurve xor = new OverlayNGCurve(holed, small);
    Geometry x = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("inside-CAP XOR is exact", xor.isApproximate());
    assertEquals("annulus plus the rectangle", HALF - SMALL_HALF + 1.0,
        x.getArea(), EXACT);
  }

  public void testDifferentOuterHoleOutsideCapIsIgnoredOnCap() throws Exception {
    Geometry holed = readCurve(HALF_HOLED);
    Geometry hanging = readCurve(HALF_HANGING);
    OverlayNGCurve cap = new OverlayNGCurve(holed, hanging);
    Geometry lens = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-HOLE-OUTER outside CAP is exact", cap.isApproximate());
    assertEquals("lens ignores the far hole", LENS, lens.getArea(), EXACT);

    OverlayNGCurve cup = new OverlayNGCurve(holed, hanging);
    Geometry blob = cup.getResult(OverlayNG.UNION);
    assertFalse("outside-CAP CUP is exact", cup.isApproximate());
    assertEquals("two halves minus lens minus hole",
        2.0 * HALF - LENS - 1.0, blob.getArea(), EXACT);
    assertEquals("keeps the hole", 1,
        ((CurvePolygon) blob).getNumInteriorRing());

    OverlayNGCurve xor = new OverlayNGCurve(holed, hanging);
    Geometry x = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("outside-CAP XOR is exact", xor.isApproximate());
    assertEquals("two bites minus the far hole",
        2.0 * HALF - 2.0 * LENS - 1.0, x.getArea(), EXACT);
    assertEquals("two members", 2, x.getNumGeometries());
  }

  public void testDifferentOuterHoleComplementaryIsDiscMinusHole()
      throws Exception {
    Geometry holed = readCurve(HALF_HOLED);
    Geometry lower = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 -5, 5 0), (5 0, -5 0)))");
    OverlayNGCurve cap = new OverlayNGCurve(holed, lower);
    Geometry empty = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("holed complementary CAP is exact", cap.isApproximate());
    assertTrue(empty.isEmpty());

    OverlayNGCurve cup = new OverlayNGCurve(holed, lower);
    Geometry disc = cup.getResult(OverlayNG.UNION);
    assertFalse("holed complementary CUP is exact", cup.isApproximate());
    assertEquals("disc minus the rectangle", DISC - 1.0, disc.getArea(), EXACT);

    OverlayNGCurve xor = new OverlayNGCurve(holed, lower);
    Geometry both = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("holed complementary XOR is exact", xor.isApproximate());
    assertEquals("disc minus the rectangle", DISC - 1.0, both.getArea(), EXACT);
  }

  /**
   * H-SHELL-HOLE-CROSS: the hole straddles the other shell. Even-n
   * hole ∩ other shares the clip edge (0,1)–(0,2) on the vertical
   * diameter. That new edge ⊂ other.shell, so the walk says bite,
   * not a punched hole.
   */
  public void testHoleStraddleIsABiteNotAHole() throws Exception {
    Geometry straddle = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (-1 1, 1 1, 1 2, -1 2, -1 1))");
    Geometry right = readCurve(HALF_RIGHT);
    assertEquals("new edge ⊂ other.shell is a bite", BiteVsHole.BITE,
        BiteVsHole.decide(straddle, right));
    CurveSegmentString clip = BiteVsHole.clipEdge(straddle, right);
    assertNotNull("clip edge is the shared diameter run", clip);
    assertFalse(clip.isArc());
    assertFalse(clip.isDegenerate());
    assertEquals(1.0, clip.length(), EXACT);
    assertTrue("clip edge (0 1)–(0 2)",
        (clip.getStart().distance(new Coordinate(0, 1)) <= EXACT
            && clip.getEnd().distance(new Coordinate(0, 2)) <= EXACT)
        || (clip.getStart().distance(new Coordinate(0, 2)) <= EXACT
            && clip.getEnd().distance(new Coordinate(0, 1)) <= EXACT));

    OverlayNGCurve cap = new OverlayNGCurve(straddle, right);
    Geometry q = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-HOLE-CROSS CAP is exact (bite, not hole)",
        cap.isApproximate());
    assertEquals("Q1 minus the right half-rectangle", 6.25 * Math.PI - 1.0,
        q.getArea(), EXACT);
    assertEquals("bite is a shell, not an interior ring", 0,
        ((CurvePolygon) q).getNumInteriorRing());
    assertArcAndLineShell(q);
    assertParity(straddle, right, OverlayNG.INTERSECTION, q);

    OverlayNGCurve cup = new OverlayNGCurve(straddle, right);
    Geometry u = cup.getResult(OverlayNG.UNION);
    assertFalse("H-SHELL-HOLE-CROSS CUP is exact", cup.isApproximate());
    assertEquals("three-quarter minus the leftover hole",
        18.75 * Math.PI - 1.0, u.getArea(), EXACT);
    assertEquals("leftover is a hole (new edge not on the CUP shell)",
        1, ((CurvePolygon) u).getNumInteriorRing());

    OverlayNGCurve sub = new OverlayNGCurve(straddle, right);
    Geometry ears = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("H-SHELL-HOLE-CROSS SUB is exact", sub.isApproximate());
    assertEquals("Q2 minus the left half-rectangle", 6.25 * Math.PI - 1.0,
        ears.getArea(), EXACT);
    assertEquals("SUB bite is a shell", 0,
        ((CurvePolygon) ears).getNumInteriorRing());

    OverlayNGCurve rev = new OverlayNGCurve(right, straddle);
    Geometry other = rev.getResult(OverlayNG.DIFFERENCE);
    assertFalse("H-SHELL-HOLE-CROSS reverse SUB is exact",
        rev.isApproximate());
    assertEquals("Q4 plus the right half-rectangle", 6.25 * Math.PI + 1.0,
        other.getArea(), EXACT);

    OverlayNGCurve xor = new OverlayNGCurve(straddle, right);
    Geometry x = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("H-SHELL-HOLE-CROSS XOR is exact", xor.isApproximate());
    assertEquals("both bites", 12.5 * Math.PI, x.getArea(), EXACT);
  }

  /**
   * H-SHELL-HOLE-X: same HALF_DISC outer, two axis-aligned unit
   * squares that overlap in [0.5,1] × [1,1.5]. The noder names
   * the two hole–hole nodes; the walk assembles the hole faces.
   * A = HALF_DISC \ holeA, B = HALF_DISC \ holeB.
   */
  public void testTwoHolesThatCrossAreAnArrangement() throws Exception {
    Geometry holed = readCurve(HALF_HOLED);
    Geometry holeX = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0.5 0.5, 1.5 0.5, 1.5 1.5, 0.5 1.5, 0.5 0.5))");
    assertEquals("two hole rings that cross", TwoHoleOverlay.CROSS,
        TwoHoleOverlay.decide(holed, holeX));
    Coordinate[] nodes = TwoHoleOverlay.clipNodes(holed, holeX);
    assertNotNull("clip pair is the two hole–hole nodes", nodes);
    assertEquals(2, nodes.length);
    assertTrue("node (0.5 1)",
        nodes[0].distance(new Coordinate(0.5, 1)) <= EXACT
            || nodes[1].distance(new Coordinate(0.5, 1)) <= EXACT);
    assertTrue("node (1 1.5)",
        nodes[0].distance(new Coordinate(1, 1.5)) <= EXACT
            || nodes[1].distance(new Coordinate(1, 1.5)) <= EXACT);

    OverlayNGCurve cap = new OverlayNGCurve(holed, holeX);
    Geometry bothHoles = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-HOLE-X CAP is exact", cap.isApproximate());
    assertEquals("HALF_DISC minus holeA ∪ holeB", HALF - 1.75,
        bothHoles.getArea(), EXACT);
    assertEquals("union of the holes is one punch", 1,
        ((CurvePolygon) bothHoles).getNumInteriorRing());
    assertArcAndLineShell(bothHoles);
    assertParity(holed, holeX, OverlayNG.INTERSECTION, bothHoles);

    OverlayNGCurve cup = new OverlayNGCurve(holed, holeX);
    Geometry overlapHole = cup.getResult(OverlayNG.UNION);
    assertFalse("H-SHELL-HOLE-X CUP is exact", cup.isApproximate());
    assertEquals("HALF_DISC minus holeA ∩ holeB", HALF - 0.25,
        overlapHole.getArea(), EXACT);
    assertEquals("overlap rectangle is the leftover hole", 1,
        ((CurvePolygon) overlapHole).getNumInteriorRing());
    assertParity(holed, holeX, OverlayNG.UNION, overlapHole);

    OverlayNGCurve sub = new OverlayNGCurve(holed, holeX);
    Geometry onlyB = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("H-SHELL-HOLE-X SUB is exact", sub.isApproximate());
    assertEquals("holeB \\ holeA", 0.75, onlyB.getArea(), EXACT);
    assertParity(holed, holeX, OverlayNG.DIFFERENCE, onlyB);

    OverlayNGCurve rev = new OverlayNGCurve(holeX, holed);
    Geometry onlyA = rev.getResult(OverlayNG.DIFFERENCE);
    assertFalse("H-SHELL-HOLE-X reverse SUB is exact", rev.isApproximate());
    assertEquals("holeA \\ holeB", 0.75, onlyA.getArea(), EXACT);

    OverlayNGCurve xor = new OverlayNGCurve(holed, holeX);
    Geometry x = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("H-SHELL-HOLE-X XOR is exact", xor.isApproximate());
    assertEquals("holeA △ holeB", 1.5, x.getArea(), EXACT);
    assertEquals("two hole-difference faces", 2, x.getNumGeometries());
    assertParity(holed, holeX, OverlayNG.SYMDIFFERENCE, x);
  }

  /**
   * H-SHELL-HOLE-OUTER: the hole sits entirely in the other solid
   * and its ring overlaps the other diameter. Not two crossing
   * nodes -- P2.2 names (0 1)–(0 2) as an edge. Same bite
   * predicate as HOLE-CROSS (hole-edge ⊂ other.shell). CUP is
   * the three-quarter: the solid fills the hole.
   */
  public void testHoleMeetsTheOtherDiameterIsABite() throws Exception {
    Geometry holed = readCurve(HALF_HOLED);
    Geometry right = readCurve(HALF_RIGHT);
    assertEquals("hole-edge ⊂ other.shell is a bite", BiteVsHole.BITE,
        BiteVsHole.decide(holed, right));
    CurveSegmentString clip = BiteVsHole.clipEdge(holed, right);
    assertNotNull("clip edge is the shared diameter run", clip);
    assertFalse(clip.isArc());
    assertFalse(clip.isDegenerate());
    assertEquals(1.0, clip.length(), EXACT);
    assertTrue("clip edge (0 1)–(0 2)",
        (clip.getStart().distance(new Coordinate(0, 1)) <= EXACT
            && clip.getEnd().distance(new Coordinate(0, 2)) <= EXACT)
        || (clip.getStart().distance(new Coordinate(0, 2)) <= EXACT
            && clip.getEnd().distance(new Coordinate(0, 1)) <= EXACT));

    OverlayNGCurve cap = new OverlayNGCurve(holed, right);
    Geometry q = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-HOLE-OUTER hole-on-diameter CAP is exact",
        cap.isApproximate());
    assertEquals("Q1 minus the rectangle", 6.25 * Math.PI - 1.0,
        q.getArea(), EXACT);
    assertEquals("bite is a shell, not an interior ring", 0,
        ((CurvePolygon) q).getNumInteriorRing());
    assertArcAndLineShell(q);
    assertParity(holed, right, OverlayNG.INTERSECTION, q);

    OverlayNGCurve cup = new OverlayNGCurve(holed, right);
    Geometry u = cup.getResult(OverlayNG.UNION);
    assertFalse("H-SHELL-HOLE-OUTER hole-on-diameter CUP is exact",
        cup.isApproximate());
    assertEquals("three-quarter; the solid fills the hole",
        18.75 * Math.PI, u.getArea(), EXACT);
    assertEquals("CUP has no leftover hole", 0,
        ((CurvePolygon) u).getNumInteriorRing());
    assertParity(holed, right, OverlayNG.UNION, u);

    OverlayNGCurve sub = new OverlayNGCurve(holed, right);
    Geometry ears = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("H-SHELL-HOLE-OUTER hole-on-diameter SUB is exact",
        sub.isApproximate());
    assertEquals("Q2; the hole was entirely in the other",
        6.25 * Math.PI, ears.getArea(), EXACT);
    assertEquals("SUB is a shell", 0,
        ((CurvePolygon) ears).getNumInteriorRing());
    assertParity(holed, right, OverlayNG.DIFFERENCE, ears);

    OverlayNGCurve rev = new OverlayNGCurve(right, holed);
    Geometry other = rev.getResult(OverlayNG.DIFFERENCE);
    assertFalse("H-SHELL-HOLE-OUTER hole-on-diameter reverse SUB is exact",
        rev.isApproximate());
    assertEquals("Q4 plus the rectangle", 6.25 * Math.PI + 1.0,
        other.getArea(), EXACT);
    assertParity(right, holed, OverlayNG.DIFFERENCE, other);

    OverlayNGCurve xor = new OverlayNGCurve(holed, right);
    Geometry x = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("H-SHELL-HOLE-OUTER hole-on-diameter XOR is exact",
        xor.isApproximate());
    assertEquals("both ears plus the rectangle", 12.5 * Math.PI + 1.0,
        x.getArea(), EXACT);
    assertParity(holed, right, OverlayNG.SYMDIFFERENCE, x);
  }

  public void testNotThisCellReturnsNull() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_5);
    Geometry other = readCurve(CIRCLE_CROSSING);
    Geometry square = readCurve(SQUARE_CAP);
    Geometry chords = readCurve(CHORD_SHELL);
    Geometry onDiameter = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 1, 0 2, 1 1), (1 1, 1 0), (1 0, -1 0), (-1 0, -1 1)))");
    assertNull("two discs stay on R1.5",
        CompoundCurveShellOverlay.overlay(disc, other, OverlayNG.INTERSECTION));
    assertNull("plain vs plain",
        CompoundCurveShellOverlay.overlay(square, square, OverlayNG.UNION));
    // Collinear overlap: R1.7 kit refuses; public OverlayNGCurve takes
    // OverlayNGCircle (R-OV / P2.5.5 Option B first slice).
    assertNull("H-SHELL-N-MIXED: R1.7 kit stays refused",
        CompoundCurveShellOverlay.overlay(half, onDiameter, OverlayNG.INTERSECTION));
    assertNull("line-only shell",
        CompoundCurveShellOverlay.overlay(chords, square, OverlayNG.INTERSECTION));
    Geometry stadiumNest = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, -2 0, -1 1), (-1 1, 1 1), CIRCULARSTRING (1 1, 2 0, 1 -1), (1 -1, -1 -1)))");
    // Mixed stadium strictly inside CIRCLE_5: not two discs (D4
    // stays null). R1.7 punches the 0-node nest; see
    // testMixedNestPunchIsExactAnnulus.
    assertNull("CC-NEST-ANNULUS: mixed nest is not two discs",
        CircularDiscOverlay.overlay(disc, stadiumNest, OverlayNG.DIFFERENCE));
    assertNotNull("CC-NEST-ANNULUS: 0-node mixed-vs-disc is the nest punch",
        CompoundCurveShellOverlay.overlay(disc, stadiumNest, OverlayNG.DIFFERENCE));
  }

  /**
   * P2.3 cousin — mixed nest punch, not a noder. Horizontal
   * stadium |x|≤2, |y|≤1 strictly inside CIRCLE_5: 0 crossing
   * nodes, both shells already representable. CAP the stadium,
   * CUP the disc, SUB / XOR CurvePolygon(disc, [stadium]).
   * Product: CAP 4+π, CUP 25π, SUB / XOR 24π−4, reverse SUB empty.
   */
  public void testMixedNestPunchIsExactAnnulus() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry stadium = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, -2 0, -1 1), (-1 1, 1 1), CIRCULARSTRING (1 1, 2 0, 1 -1), (1 -1, -1 -1)))");
    assertEquals("fixture stadium is 4 + π", 4.0 + Math.PI, stadium.getArea(),
        EXACT);

    OverlayNGCurve cap = new OverlayNGCurve(disc, stadium);
    Geometry inner = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("mixed nest CAP is exact", cap.isApproximate());
    assertEquals("CAP is the stadium, 4+π", 4.0 + Math.PI, inner.getArea(),
        EXACT);
    assertArcAndLineShell(inner);

    OverlayNGCurve cup = new OverlayNGCurve(disc, stadium);
    Geometry outer = cup.getResult(OverlayNG.UNION);
    assertFalse("mixed nest CUP is exact", cup.isApproximate());
    assertEquals("CUP is CIRCLE_5, 25π", DISC, outer.getArea(), EXACT);
    assertArcShell(outer);

    OverlayNGCurve rev = new OverlayNGCurve(stadium, disc);
    Geometry empty = rev.getResult(OverlayNG.DIFFERENCE);
    assertFalse("stadium \\ disc is exact", rev.isApproximate());
    assertTrue(empty.isEmpty());

    OverlayNGCurve sub = new OverlayNGCurve(disc, stadium);
    Geometry punched = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("mixed nest SUB is exact", sub.isApproximate());
    assertEquals("CurvePolygon", punched.getGeometryType());
    CurvePolygon cp = (CurvePolygon) punched;
    assertEquals("one hole", 1, cp.getNumInteriorRing());
    assertTrue("outer is the CircularString disc",
        cp.getExteriorCurve() instanceof CircularString);
    assertTrue("hole is the CompoundCurve stadium, not a densified n-gon",
        cp.getInteriorCurveN(0) instanceof CompoundCurve);
    assertEquals("SUB is 24π − 4", 24.0 * Math.PI - 4.0, punched.getArea(),
        EXACT);
    assertParity(disc, stadium, OverlayNG.DIFFERENCE, punched);

    OverlayNGCurve xor = new OverlayNGCurve(disc, stadium);
    Geometry both = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("mixed nest XOR is exact", xor.isApproximate());
    assertEquals("XOR is the same holed disc, 24π − 4",
        24.0 * Math.PI - 4.0, both.getArea(), EXACT);
  }

  private static void assertParity(Geometry a, Geometry b, int opCode,
      Geometry laser) {
    Geometry chord = OverlayNGRobust.overlay(
        CurveOps.linearise(a), CurveOps.linearise(b), opCode);
    assertEquals("area vs chord overlay", chord.getArea(), laser.getArea(),
        AREA_TOL);
    double hd = DiscreteHausdorffDistance.distance(
        CurveOps.linearise(laser), chord);
    assertTrue("Hausdorff vs chord overlay " + hd + " > " + AREA_TOL,
        hd <= AREA_TOL);
  }

  private static void assertArcAndLineShell(Geometry g) {
    assertEquals("CurvePolygon", g.getGeometryType());
    LineString shell = ((CurvePolygon) g).getExteriorCurve();
    assertTrue("shell is a CompoundCurve, got " + shell.getGeometryType(),
        shell instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) shell;
    boolean hasArc = false;
    boolean hasLine = false;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      if (m instanceof CircularString) hasArc = true;
      else hasLine = true;
    }
    assertTrue("CompoundCurve shell has a CircularString", hasArc);
    assertTrue("CompoundCurve shell has a LineString (not a densified n-gon)",
        hasLine);
  }

  private static void assertArcShell(Geometry g) {
    assertEquals("CurvePolygon", g.getGeometryType());
    LineString shell = ((CurvePolygon) g).getExteriorCurve();
    assertTrue("shell keeps a circular arc, got " + shell.getGeometryType(),
        shell instanceof CompoundCurve || shell instanceof CircularString);
    if (shell instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) shell;
      boolean hasArc = false;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        if (cc.getMemberN(i) instanceof CircularString) hasArc = true;
      }
      assertTrue("CompoundCurve shell has a CircularString", hasArc);
    }
  }
}
