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
import org.locationtech.jts.geom.curve.CurveOps;
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
 * <tr><td>covers</td>     <td>exact</td><td>exact</td><td>approx</td><td>approx</td></tr>
 * <tr><td>coveredBy</td>  <td>exact</td><td>exact</td><td>exact 0</td><td>approx</td></tr>
 * <tr><td>crossing</td>   <td>approx</td><td>approx</td><td>approx</td><td>approx</td></tr>
 * </table>
 * <p>
 * Exact cells per operation: CAP 7 of 8, CUP 7, SUB 6, XOR 5. The two operations
 * that cannot do better are the ones whose remaining cases produce a
 * <em>new</em> geometry rather than an operand -- SUB and XOR of a nested pair are
 * an annulus, which no short-circuit can return.
 */
public class OverlayNGCurveRatchetTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_3 =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";
  private static final String CIRCLE_FAR =
      "CURVEPOLYGON (CIRCULARSTRING (100 0, 105 5, 110 0, 105 -5, 100 0))";
  private static final String CIRCLE_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  private static final String EMPTY = "CURVEPOLYGON EMPTY";

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
    assertRow("covers", CIRCLE_5, CIRCLE_3, "EEaa");
  }

  public void testMatrix_coveredBy() throws Exception {
    assertRow("coveredBy", CIRCLE_3, CIRCLE_5, "EE0a");
  }

  public void testMatrix_crossing() throws Exception {
    assertRow("crossing", CIRCLE_5, CIRCLE_CROSSING, "aaaa");
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
   * The security case. Two circles separated by less than the summed
   * densification tolerance must <em>not</em> be retained on a disjoint verdict,
   * because the verdict is made on inscribed copies and could be wrong.
   * <p>
   * This matters asymmetrically. A wrong disjoint verdict makes SUB return
   * {@code a} unchanged -- it fails to erase, and the answer looks entirely
   * plausible, so nothing downstream detects it. For CUP and XOR it would be
   * worse still: two operands that truly touch have a single connected union, so
   * a two-member MultiSurface would be wrong in topology, not merely in area.
   * <p>
   * The gap here is 1e-5 against a margin of 2e-5 (1e-6 of each 10-unit extent,
   * summed). The closest points are control points, which the densifier pins
   * exactly, so the densified gap equals the true gap and the pair really does
   * sit inside the undecidable band.
   */
  public void testNearlyTouchingIsNotRetained() throws Exception {
    String nearlyTouching =
        "CURVEPOLYGON (CIRCULARSTRING (5.00001 0, 10.00001 5, 15.00001 0, 10.00001 -5, 5.00001 0))";
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(nearlyTouching);
    double margin = CurveOps.tolerance(a) + CurveOps.tolerance(b);
    assertTrue("test premise: the gap must be inside the margin, gap 1e-5 vs margin "
        + margin, 1.0e-5 < margin);

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
}
