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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Locks the ratchet's exactness matrix: which operation resolves exactly, in
 * which topological case.
 * <p>
 * {@code isApproximate()} is a promise to the caller, and nothing else in the
 * suite stops a future change from silently turning an exact cell approximate --
 * every other test asserts an area, and an approximate answer has very nearly the
 * right area. The regression this catches is a quiet loss of exactness, which is
 * invisible to a tolerance-based assertion by construction.
 * <table border="1">
 * <caption>Measured matrix</caption>
 * <tr><th>case</th><th>CAP</th><th>CUP</th><th>SUB</th><th>XOR</th></tr>
 * <tr><td>self</td>       <td>exact</td><td>exact</td><td>exact 0</td><td>exact 0</td></tr>
 * <tr><td>empty partner</td><td>exact 0</td><td>exact</td><td>exact</td><td>exact</td></tr>
 * <tr><td>disjoint</td>   <td>exact 0</td><td>exact</td><td>exact</td><td>exact</td></tr>
 * <tr><td>covers</td>     <td>exact</td><td>exact</td><td>exact</td><td>exact</td></tr>
 * <tr><td>coveredBy</td>  <td>exact</td><td>exact</td><td>exact 0</td><td>exact</td></tr>
 * <tr><td>crossing</td>   <td>exact</td><td>exact</td><td>exact</td><td>exact</td></tr>
 * </table>
 * <p>
 * Exact cells per operation: CAP 8 of 8, CUP 8, SUB 8, XOR 8 on the
 * two-disc matrix. Crossing discs are two-arc CurvePolygons (R1.5).
 * Nested discs are the annulus (R1.5): SUB the outer with the inner
 * as a hole, XOR the same, including a two-arc CompoundCurve disc.
 * A disc clipped by a plain rectangle (R1.6)
 * is EEEE in both operand orders. A half-disc CompoundCurve shell vs
 * a crossing disc or a cutting square (R1.7) is EEEE in both operand
 * orders. Two crossing CircularStrings (R-AA) are EEEE in both operand
 * orders. Same-circle overlapping arcs are EEEE (interval overlay).
 * Complementary half-discs are 0EEE. Perpendicular same-circle
 * half-discs, a two-node two-shell clip, collinear same-side halves,
 * nested halves, and a 1-node touch are exact. A four-cut two-shell
 * n-span, a same-outer hole-inside pair, and a different-outer hole
 * that sits strictly inside or outside a certified outer CAP are
 * exact. A four-cut disc vs a band is EEEE.
 */
public class OverlayNGCurveRatchetTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_3 =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";
  /** Same disc as CIRCLE_3, two semicircle CircularStrings. */
  private static final String CIRCLE_3_CC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-3 0, 0 3, 3 0), CIRCULARSTRING (3 0, 0 -3, -3 0)))";
  private static final String CIRCLE_FAR =
      "CURVEPOLYGON (CIRCULARSTRING (100 0, 105 5, 110 0, 105 -5, 100 0))";
  private static final String CIRCLE_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  private static final String EMPTY = "CURVEPOLYGON EMPTY";
  private static final String SQUARE_RIGHT =
      "POLYGON ((0 -6, 10 -6, 10 6, 0 6, 0 -6))";
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String SQUARE_CAP =
      "POLYGON ((-6 2, 6 2, 6 10, -6 10, -6 2))";
  private static final String CHORD_SHELL =
      "CURVEPOLYGON (COMPOUNDCURVE ((-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String ARC =
      "CIRCULARSTRING (0 0, 2 3, 10 0)";
  private static final String ARC_B =
      "CIRCULARSTRING (1 4, 5 2, 9 4)";
  private static final String ARC_SAME_Q1 =
      "CIRCULARSTRING (-5 0, 0 5, 5 0)";
  private static final String ARC_SAME_Q2 =
      "CIRCULARSTRING (0 5, 5 0, 0 -5)";
  private static final String LINE_Y2 =
      "LINESTRING (-1 2, 11 2)";
  private static final String CHORD_ARC =
      "LINESTRING (0 0, 2 3, 10 0)";
  private static final String HALF_LOWER =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 -5, 5 0), (5 0, -5 0)))";
  private static final String HALF_RIGHT =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 -5, 5 0, 0 5), (0 5, 0 -5)))";
  private static final String HALF_HANGING =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 8, 0 3, 5 8), (5 8, -5 8)))";
  private static final String HALF_CROSSING_UPPER =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (2 0, 7 5, 12 0), (12 0, 2 0)))";
  private static final String HALF_SMALL =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-3 0, 0 3, 3 0), (3 0, -3 0)))";
  private static final String HALF_TOUCH =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (5 -5, 10 0, 5 5), (5 5, 5 -5)))";
  private static final String BAND_FOUR =
      "POLYGON ((-8 -1, 8 -1, 8 1, -8 1, -8 -1))";
  private static final String STADIUM_FOUR =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, 0 -2, 1 -1), (1 -1, 1 6), CIRCULARSTRING (1 6, 0 7, -1 6), (-1 6, -1 -1)))";
  private static final String HALF_HOLED =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0 1, 1 1, 1 2, 0 2, 0 1))";

  private static final int[] OPS = { OverlayNGCurve.INTERSECTION, OverlayNGCurve.UNION,
      OverlayNGCurve.DIFFERENCE, OverlayNGCurve.SYMDIFFERENCE };
  private static final String[] OP_NAMES = { "CAP", "CUP", "SUB", "XOR" };

  /** Expected cell values, in OPS order. */
  private static final char EXACT = 'E', EXACT_EMPTY = '0', APPROX = 'a';

  public static void main(String[] args) {
    TestRunner.run(OverlayNGCurveRatchetTest.class);
  }

  public OverlayNGCurveRatchetTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  /**
   * @param expected one char per op in OPS order: E exact non-empty, 0 exact
   *                 empty, a approximate
   */
  private void assertRow(String label, String wktA, String wktB, String expected)
      throws Exception {
    assertEquals("test bug: need one expectation per op", OPS.length, expected.length());
    for (int i = 0; i < OPS.length; i++) {
      Geometry a = readCurve(wktA), b = readCurve(wktB);
      OverlayNGCurve op = new OverlayNGCurve(a, b);
      Geometry r = op.getResult(OPS[i]);
      String where = label + " / " + OP_NAMES[i];
      char want = expected.charAt(i);
      if (want == APPROX) {
        assertTrue(where + ": expected an approximate answer, got an exact one",
            op.isApproximate());
      }
      else {
        assertFalse(where + ": expected an exact answer, got an approximate one "
            + "with " + r.getNumPoints() + " vertices", op.isApproximate());
        if (want == EXACT_EMPTY) {
          assertTrue(where + ": expected exactly empty, got area " + r.getArea(),
              r.isEmpty());
        }
        else {
          assertFalse(where + ": expected a non-empty exact answer", r.isEmpty());
        }
      }
    }
  }

  public void testMatrix_self() throws Exception {
    assertRow("self", CIRCLE_5, CIRCLE_5, "EE00");
  }

  public void testMatrix_emptyPartnerB() throws Exception {
    assertRow("empty B", CIRCLE_5, EMPTY, "0EEE");
  }

  public void testMatrix_emptyPartnerA() throws Exception {
    assertRow("empty A", EMPTY, CIRCLE_5, "0E0E");
  }

  public void testMatrix_disjoint() throws Exception {
    assertRow("disjoint", CIRCLE_5, CIRCLE_FAR, "0EEE");
  }

  public void testMatrix_covers() throws Exception {
    assertRow("covers", CIRCLE_5, CIRCLE_3, "EEEE");
  }

  public void testMatrix_coversCompoundCurveDisc() throws Exception {
    assertRow("covers two-arc disc", CIRCLE_5, CIRCLE_3_CC, "EEEE");
  }

  public void testMatrix_coveredBy() throws Exception {
    assertRow("coveredBy", CIRCLE_3, CIRCLE_5, "EE0E");
  }

  public void testMatrix_crossing() throws Exception {
    assertRow("crossing", CIRCLE_5, CIRCLE_CROSSING, "EEEE");
  }

  public void testMatrix_discRectangle() throws Exception {
    assertRow("disc ∩ square", CIRCLE_5, SQUARE_RIGHT, "EEEE");
  }

  public void testMatrix_rectangleDisc() throws Exception {
    assertRow("square ∩ disc", SQUARE_RIGHT, CIRCLE_5, "EEEE");
  }

  public void testMatrix_halfDiscCrossing() throws Exception {
    assertRow("half ∩ disc", HALF_DISC, CIRCLE_CROSSING, "EEEE");
  }

  public void testMatrix_crossingHalfDisc() throws Exception {
    assertRow("disc ∩ half", CIRCLE_CROSSING, HALF_DISC, "EEEE");
  }

  public void testMatrix_halfDiscSquare() throws Exception {
    assertRow("half ∩ square", HALF_DISC, SQUARE_CAP, "EEEE");
  }

  public void testMatrix_squareHalfDisc() throws Exception {
    assertRow("square ∩ half", SQUARE_CAP, HALF_DISC, "EEEE");
  }

  public void testMatrix_chordShellIsApproximate() throws Exception {
    assertRow("3-pt LINESTRING shell", CHORD_SHELL, CIRCLE_CROSSING, "aaaa");
  }

  public void testMatrix_arcLine() throws Exception {
    assertRow("arc ∩ line", ARC, LINE_Y2, "EEEE");
  }

  public void testMatrix_lineArc() throws Exception {
    assertRow("line ∩ arc", LINE_Y2, ARC, "EEEE");
  }

  public void testMatrix_chordArcIsExactR2() throws Exception {
    assertRow("3-pt LINESTRING vs line", CHORD_ARC, LINE_Y2, "EEEE");
  }

  public void testMatrix_arcArc() throws Exception {
    assertRow("arc ∩ arc", ARC, ARC_B, "EEEE");
  }

  public void testMatrix_arcArcReverse() throws Exception {
    assertRow("arc ∩ arc reverse", ARC_B, ARC, "EEEE");
  }

  public void testMatrix_sameCircle() throws Exception {
    assertRow("same-circle overlap", ARC_SAME_Q1, ARC_SAME_Q2, "EEEE");
  }

  public void testMatrix_complementaryHalves() throws Exception {
    assertRow("complementary halves", HALF_DISC, HALF_LOWER, "0EEE");
  }

  public void testMatrix_overlappingHalves() throws Exception {
    assertRow("upper ∩ right", HALF_DISC, HALF_RIGHT, "EEEE");
  }

  public void testMatrix_overlappingHalvesReverse() throws Exception {
    assertRow("right ∩ upper", HALF_RIGHT, HALF_DISC, "EEEE");
  }

  public void testMatrix_twoShellLens() throws Exception {
    assertRow("two-shell lens", HALF_DISC, HALF_HANGING, "EEEE");
  }

  public void testMatrix_twoShellLensReverse() throws Exception {
    assertRow("two-shell lens reverse", HALF_HANGING, HALF_DISC, "EEEE");
  }

  public void testMatrix_collinearHalves() throws Exception {
    assertRow("collinear halves", HALF_DISC, HALF_CROSSING_UPPER, "EEEE");
  }

  public void testMatrix_nestedHalves() throws Exception {
    assertRow("nested halves", HALF_DISC, HALF_SMALL, "EEEE");
  }

  public void testMatrix_oneNodeTouch() throws Exception {
    assertRow("one-node touch", HALF_DISC, HALF_TOUCH, "0EEE");
  }

  public void testMatrix_fourCutTwoShell() throws Exception {
    assertRow("four-cut two-shell", HALF_DISC, STADIUM_FOUR, "EEEE");
  }

  public void testMatrix_fourCutTwoShellReverse() throws Exception {
    assertRow("four-cut two-shell reverse", STADIUM_FOUR, HALF_DISC, "EEEE");
  }

  public void testMatrix_sameOuterHole() throws Exception {
    assertRow("same-outer hole", HALF_HOLED, HALF_DISC, "EE0E");
  }

  public void testMatrix_sameOuterHoleReverse() throws Exception {
    assertRow("same-outer hole reverse", HALF_DISC, HALF_HOLED, "EEEE");
  }

  public void testMatrix_differentOuterHoleNested() throws Exception {
    assertRow("different-outer hole nested", HALF_HOLED, HALF_SMALL, "EEEE");
  }

  public void testMatrix_differentOuterHoleNestedReverse() throws Exception {
    assertRow("different-outer hole nested reverse", HALF_SMALL, HALF_HOLED,
        "EEEE");
  }

  public void testMatrix_differentOuterHoleLens() throws Exception {
    assertRow("different-outer hole lens", HALF_HOLED, HALF_HANGING, "EEEE");
  }

  public void testMatrix_differentOuterHoleComplementary() throws Exception {
    assertRow("different-outer hole complementary", HALF_HOLED, HALF_LOWER,
        "0EEE");
  }

  public void testMatrix_fourCut() throws Exception {
    assertRow("four-cut disc ∩ band", CIRCLE_5, BAND_FOUR, "EEEE");
  }

  // -- the disjoint CUP/XOR result, not just its exactness -----------------

  /**
   * Disjoint CUP and XOR are both operands side by side. A MultiSurface holds
   * that exactly, so the area is the sum with no densification loss.
   */
  public void testDisjointUnionIsAnExactMultiSurface() throws Exception {
    double both = 2.0 * 25.0 * Math.PI;
    for (int opCode : new int[] { OverlayNGCurve.UNION, OverlayNGCurve.SYMDIFFERENCE }) {
      Geometry r = OverlayNGCurve.overlay(readCurve(CIRCLE_5), readCurve(CIRCLE_FAR), opCode);
      assertTrue("should be a MultiSurface, got " + r.getGeometryType(),
          r instanceof MultiSurface);
      assertEquals("two members", 2, r.getNumGeometries());
      assertEquals("area is both circles, exactly", both, r.getArea(), 1.0e-9);
      assertEquals("ten control points, not a densified ring", 10, r.getNumPoints());
    }
  }

  // -- the margin gate ----------------------------------------------------

  /**
   * Axis-aligned nearly-disjoint circles have envelopes that do not meet, so
   * R0 answers exactly without densifying. The 1e-5 gap is real: the true
   * arcs do not touch. Before the performance gate this pair paid a fine
   * relate and then approximated; the envelope stage makes the exact answer
   * the cheap one.
   */
  public void testAxisAlignedNearlyDisjointIsExact() throws Exception {
    String nearlyTouching =
        "CURVEPOLYGON (CIRCULARSTRING (5.00001 0, 10.00001 5, 15.00001 0, 10.00001 -5, 5.00001 0))";
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(nearlyTouching);
    assertFalse("envelopes must not meet -- that is R0's premise",
        a.getEnvelopeInternal().intersects(b.getEnvelopeInternal()));

    OverlayNGCurve sub = new OverlayNGCurve(a, b);
    Geometry r = sub.getResult(OverlayNGCurve.DIFFERENCE);
    assertFalse("R0 disjoint SUB is exact", sub.isApproximate());
    assertEquals("SUB is all of A", 25.0 * Math.PI, r.getArea(), 1.0e-9);

    OverlayNGCurve cup = new OverlayNGCurve(a, b);
    Geometry u = cup.getResult(OverlayNGCurve.UNION);
    assertFalse("R0 disjoint CUP is an exact MultiSurface", cup.isApproximate());
    assertTrue(u instanceof MultiSurface);
    assertEquals(2, u.getNumGeometries());
  }

  /**
   * The security case. Two circles that touch on a diagonal have overlapping
   * envelopes (R0 cannot decide) and a true gap of zero. Inscribed copies
   * open a gap of about the summed decide-tolerance, so a disjoint verdict
   * on those copies is exactly the false "they do not meet" the margin gate
   * exists to refuse.
   * <p>
   * A wrong disjoint verdict makes SUB return {@code a} unchanged -- it fails
   * to erase -- and makes CUP a two-member MultiSurface of operands that
   * truly touch.
   */
  public void testNearlyTouchingIsNotRetained() throws Exception {
    // Centres (0,0) and (5√2, 5√2), each r=5. Distance 10, they touch at 45°.
    double c = 5.0 * Math.sqrt(2.0);
    String diagonalTouch =
        "CURVEPOLYGON (CIRCULARSTRING ("
        + (c - 5) + " " + c + ", "
        + c + " " + (c + 5) + ", "
        + (c + 5) + " " + c + ", "
        + c + " " + (c - 5) + ", "
        + (c - 5) + " " + c + "))";
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(diagonalTouch);
    double margin = OverlayNGCurve.decideTolerance(a) + OverlayNGCurve.decideTolerance(b);
    assertTrue("test premise: envelopes must meet so R0 does not decide",
        a.getEnvelopeInternal().intersects(b.getEnvelopeInternal()));
    assertTrue("test premise: a touching pair sits inside any positive decide margin "
        + margin, margin > 0.0);

    OverlayNGCurve sub = new OverlayNGCurve(a, b);
    sub.getResult(OverlayNGCurve.DIFFERENCE);
    assertTrue("SUB must not claim an exact answer inside the undecidable band -- "
        + "returning A unchanged there is a silent failure to erase",
        sub.isApproximate());

    OverlayNGCurve cup = new OverlayNGCurve(a, b);
    cup.getResult(OverlayNGCurve.UNION);
    assertTrue("CUP must not build a two-member MultiSurface inside the band, "
        + "where the true union may be one connected polygon", cup.isApproximate());
  }

  /** Guard: comfortably separated curves are still retained exactly. */
  public void testComfortablySeparatedIsRetained() throws Exception {
    OverlayNGCurve sub = new OverlayNGCurve(readCurve(CIRCLE_5), readCurve(CIRCLE_FAR));
    Geometry r = sub.getResult(OverlayNGCurve.DIFFERENCE);
    assertFalse("a 90-unit gap is far outside any margin", sub.isApproximate());
    assertEquals("SUB returns A exactly", 25.0 * Math.PI, r.getArea(), 1.0e-9);
  }

  /**
   * Guard: plain operands have zero tolerance, so the gate is satisfied trivially
   * and non-curve behaviour is untouched -- including operands that actually touch.
   */
  public void testPlainOperandsAreNotGated() throws Exception {
    Geometry p = readCurve("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    Geometry q = readCurve("POLYGON ((20 0, 30 0, 30 10, 20 10, 20 0))");
    OverlayNGCurve sub = new OverlayNGCurve(p, q);
    Geometry r = sub.getResult(OverlayNGCurve.DIFFERENCE);
    assertFalse("plain disjoint polygons need no margin", sub.isApproximate());
    assertEquals("SUB is all of P", 100.0, r.getArea(), 0.0);

    Geometry touching = readCurve("POLYGON ((10 0, 20 0, 20 10, 10 10, 10 0))");
    OverlayNGCurve edge = new OverlayNGCurve(p, touching);
    Geometry er = edge.getResult(OverlayNGCurve.DIFFERENCE);
    assertFalse("edge-touching plain polygons are still decidable exactly",
        edge.isApproximate());
    assertEquals("touching removes nothing", 100.0, er.getArea(), 0.0);
  }

  /**
   * An arc-free CurvePolygon is Linearizable, but it has no arc, so the
   * decide margin is zero -- the same as a plain Polygon.
   */
  public void testDecideToleranceIsZeroForArcFreeCurvePolygon() throws Exception {
    Geometry plainRings = readCurve(
        "CURVEPOLYGON ((-6 -6, 6 -6, 6 6, -6 6, -6 -6))");
    assertEquals("CurvePolygon", plainRings.getGeometryType());
    assertEquals("no arc means no decide margin",
        0.0, OverlayNGCurve.decideTolerance(plainRings), 0.0);
  }
}
