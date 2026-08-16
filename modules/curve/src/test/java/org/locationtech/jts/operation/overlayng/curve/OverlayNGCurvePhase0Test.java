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
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * OverlayNGCurve Phase 0: the algebra, the gates, and the honesty flag.
 * <p>
 * Named after the ops mnemonics -- CAP is intersection, CUP is union, SUB is
 * difference, XOR is symmetric difference. CAP and CUP keep me, SUB and XOR empty
 * me, and the empty partner is the fifth guard.
 * <p>
 * <b>Why these are not redundant with CurveOverlayTest.</b> That suite asserts
 * that the overlay <em>answers</em> are arc-aware to within the densification
 * tolerance, which the current implementation satisfies. This suite asserts that
 * the answers which need no densification are not densified at all. Measured
 * before implementing: {@code A CAP A} returned {@code Polygon[1573]} with area
 * 78.5396072210 where {@code A} itself is {@code CurvePolygon[5]} with area
 * exactly {@code 25*pi} = 78.5398163397. Right to six figures, and still the
 * wrong object -- a self-intersection had thrown away the arc and spent a noding
 * pass to do it.
 * <p>
 * <b>The trap in G1/G2.</b> "Same geometry" cannot be decided with
 * {@code equalsExact}. Two {@code CurvePolygon}s -- one whose ring is a
 * {@code CIRCULARSTRING}, one whose ring is straight through the same control
 * points -- have identical flat rings, so {@code equalsExact} reports them equal
 * while one is a circle of area {@code 25*pi} and the other its inscribed diamond
 * of area 50. Treating that pair as a self-operation would return the circle as
 * the intersection of a circle with a diamond.
 * {@link #testCircleIsNotItsInscribedDiamond()} pins it.
 * <p>
 * A {@code CurvePolygon} against a plain {@code Polygon} needs no guard of ours:
 * {@code Geometry.equalsExact} begins with {@code isEquivalentClass}, an exact
 * class-name comparison, so core already separates them. That was worth checking
 * rather than assuming -- the first version of this suite asserted the pair
 * <em>was</em> {@code equalsExact} and failed. See
 * {@link #testPlainPolygonIsDiscriminatedByClass()}.
 */
public class OverlayNGCurvePhase0Test extends GeometryTestCase {

  /** Radius 5 arc circle; control points are the four axis extremes. */
  private static final String A =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  /** Radius 3, concentric, so wholly inside A. */
  private static final String B =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";
  /**
   * A's control points read as a straight ring, still inside a CurvePolygon: the
   * inscribed diamond. Same class as A and {@code equalsExact} to it, because
   * Polygon equality compares the flat rings -- but area 50, not {@code 25*pi}.
   */
  private static final String A_DIAMOND_CURVE =
      "CURVEPOLYGON ((-5 0, 0 5, 5 0, 0 -5, -5 0))";
  /** The same ring as a plain Polygon, which core discriminates by class. */
  private static final String A_DIAMOND_PLAIN = "POLYGON ((-5 0, 0 5, 5 0, 0 -5, -5 0))";

  private static final String EMPTY_CURVE = "CURVEPOLYGON EMPTY";

  private static final double AREA_A = 25.0 * Math.PI;
  private static final double AREA_B = 9.0 * Math.PI;
  private static final double DIAMOND_AREA = 50.0;

  /** Exact means exact: only floating-point slack. */
  private static final double EXACT = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(OverlayNGCurvePhase0Test.class);
  }

  public OverlayNGCurvePhase0Test(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  /** The answer must be the curve itself, not a densified stand-in. */
  private static void assertIsCurveA(String id, Geometry result) {
    assertEquals(id + ": must stay a CurvePolygon", "CurvePolygon",
        result.getGeometryType());
    assertEquals(id + ": must keep the five control points", 5,
        result.getNumPoints());
    assertEquals(id + ": area must be exactly 25*pi", AREA_A, result.getArea(), EXACT);
    assertTrue(id + ": the ring must still be an arc",
        ((CurvePolygon) result).getExteriorCurve()
            instanceof org.locationtech.jts.geom.curve.CircularString);
  }

  // -- G1..G4: self-operations, same instance -----------------------------

  /** G1 CAP self -- I meet myself, so I get me. */
  public void testG1_capSelf() throws Exception {
    Geometry a = readCurve(A);
    assertIsCurveA("G1", OverlayNGCurve.intersection(a, a));
  }

  /** G2 CUP self -- double pour, same cup. */
  public void testG2_cupSelf() throws Exception {
    Geometry a = readCurve(A);
    assertIsCurveA("G2", OverlayNGCurve.union(a, a));
  }

  /** G3 SUB self -- erase myself, nothing left. */
  public void testG3_subSelf() throws Exception {
    Geometry a = readCurve(A);
    assertTrue("G3: A SUB A must be empty",
        OverlayNGCurve.difference(a, a).isEmpty());
  }

  /** G4 XOR self -- mirror cancel. */
  public void testG4_xorSelf() throws Exception {
    Geometry a = readCurve(A);
    assertTrue("G4: A XOR A must be empty",
        OverlayNGCurve.symDifference(a, a).isEmpty());
  }

  // -- G1..G4 again, with an equal but distinct instance -------------------

  /**
   * The identity short-circuit must not be reference equality alone: a geometry
   * read twice from the same WKT is the same geometry.
   */
  public void testG1_G4_onADistinctButEqualCurve() throws Exception {
    Geometry a = readCurve(A);
    Geometry copy = readCurve(A);
    assertNotSame("the test needs two distinct objects", a, copy);
    assertIsCurveA("G1'", OverlayNGCurve.intersection(a, copy));
    assertIsCurveA("G2'", OverlayNGCurve.union(a, copy));
    assertTrue("G3'", OverlayNGCurve.difference(a, copy).isEmpty());
    assertTrue("G4'", OverlayNGCurve.symDifference(a, copy).isEmpty());
  }

  /**
   * The trap: two {@code CurvePolygon}s, one with a {@code CIRCULARSTRING} ring and
   * one with a straight ring through the same control points, are
   * {@code equalsExact} -- Polygon equality compares the flat rings, and a
   * CurvePolygon presents its control points as that ring. They are not the same
   * geometry, {@code 25*pi} against 50, and must not be taken for a self-operation,
   * which would return the circle as the intersection of a circle with a diamond.
   * <p>
   * Note what does <em>not</em> need guarding: a CurvePolygon against a plain
   * Polygon. {@code Geometry.equalsExact} begins with {@code isEquivalentClass},
   * an exact class-name comparison, so core discriminates that pair already. The
   * structural ring check is what earns its keep, not the class check.
   * <p>
   * Asserted against the guard itself rather than through an overlay. The pair is
   * degenerate for any noder -- the diamond's vertices lie exactly on the true
   * circle, while the densified circle is inscribed and so passes within 1.6e-18
   * of them -- and core throws {@code TopologyException: found non-noded
   * intersection} on it. That is worth knowing (see the class comment) but it is
   * not what this test is about, and routing the claim through a path that can
   * fail for an unrelated reason would make the test say less, not more.
   */
  public void testCircleIsNotItsInscribedDiamond() throws Exception {
    Geometry circle = readCurve(A);
    Geometry diamond = readCurve(A_DIAMOND_CURVE);
    assertEquals("the diamond really is the inscribed one",
        DIAMOND_AREA, diamond.getArea(), EXACT);
    assertEquals("and the circle really is the circle", AREA_A, circle.getArea(), EXACT);
    assertEquals("both are CurvePolygons, so class is no help",
        circle.getGeometryType(), diamond.getGeometryType());
    assertTrue("the trap is real: equalsExact compares the flat rings",
        circle.equalsExact(diamond));
    assertFalse("but they are not the same geometry, so no self-op short-circuit",
        OverlayNGCurve.isSameGeometry(circle, diamond));
    assertFalse("and not in the other direction either",
        OverlayNGCurve.isSameGeometry(diamond, circle));
  }

  /** Core already discriminates a CurvePolygon from a plain Polygon by class. */
  public void testPlainPolygonIsDiscriminatedByClass() throws Exception {
    Geometry circle = readCurve(A);
    Geometry plain = readCurve(A_DIAMOND_PLAIN);
    assertFalse("equalsExact starts with an exact class-name check",
        circle.equalsExact(plain));
    assertFalse("so isSameGeometry rejects it too",
        OverlayNGCurve.isSameGeometry(circle, plain));
  }

  /**
   * R1 with a plain operand: a diamond strictly inside the circle is covered, so
   * CAP must return it exactly rather than a noded approximation of it.
   * <p>
   * Scaled to 0.9 of the inscribed one so its vertices are strictly interior --
   * see {@link #testCircleIsNotItsInscribedDiamond()} for why vertices lying
   * exactly on the arc are a different problem.
   */
  public void testR1_coveredPlainPolygonReturnedExactly() throws Exception {
    Geometry circle = readCurve(A);
    Geometry inner = readCurve("POLYGON ((-4.5 0, 0 4.5, 4.5 0, 0 -4.5, -4.5 0))");
    double innerArea = 2.0 * 4.5 * 4.5;
    assertEquals("the inner diamond's area", innerArea, inner.getArea(), EXACT);
    Geometry cap = OverlayNGCurve.intersection(circle, inner);
    assertEquals("CAP must be the covered diamond, exactly",
        innerArea, cap.getArea(), EXACT);
    assertEquals("with its own five vertices, not a noded ring",
        5, cap.getNumPoints());
    Geometry cup = OverlayNGCurve.union(circle, inner);
    assertEquals("CUP must be the circle, exactly", AREA_A, cup.getArea(), EXACT);
    assertEquals("and still the curve", "CurvePolygon", cup.getGeometryType());
  }

  // -- G5: empty partner ---------------------------------------------------

  /** G5 -- nothing in the room. */
  public void testG5_emptyPartner() throws Exception {
    Geometry a = readCurve(A);
    Geometry empty = readCurve(EMPTY_CURVE);
    assertTrue("G5a: A CAP empty must be empty",
        OverlayNGCurve.intersection(a, empty).isEmpty());
    assertIsCurveA("G5b: A CUP empty", OverlayNGCurve.union(a, empty));
    assertIsCurveA("G5c: A SUB empty", OverlayNGCurve.difference(a, empty));
    assertTrue("G5d: empty SUB A must be empty",
        OverlayNGCurve.difference(empty, a).isEmpty());
  }

  /** G5 with XOR: the symmetric difference against nothing is everything. */
  public void testG5_xorAgainstEmpty() throws Exception {
    assertIsCurveA("G5e: A XOR empty",
        OverlayNGCurve.symDifference(readCurve(A), readCurve(EMPTY_CURVE)));
  }

  // -- R1: retention when representable ------------------------------------

  /**
   * R1 -- both operands are curve polygons and the answer is one of them, so the
   * answer must be a curve polygon rather than a densified copy.
   */
  public void testR1_nestedPairKeepsTheCurve() throws Exception {
    Geometry a = readCurve(A);
    Geometry b = readCurve(B);
    Geometry cap = OverlayNGCurve.intersection(a, b);
    assertEquals("R1a: CAP of a nested pair is B, exactly",
        AREA_B, cap.getArea(), EXACT);
    assertEquals("R1a: and B's five control points", 5, cap.getNumPoints());
    Geometry cup = OverlayNGCurve.union(a, b);
    assertEquals("R1b: CUP of a nested pair is A, exactly",
        AREA_A, cup.getArea(), EXACT);
    assertEquals("R1b: and A's five control points", 5, cup.getNumPoints());
    assertTrue("R1c: B SUB A is empty", OverlayNGCurve.difference(b, a).isEmpty());
  }

  // -- R2: honest approximation -------------------------------------------

  /** R2 -- an exact answer must not claim to be approximate. */
  public void testR2_exactAnswersAreNotFlagged() throws Exception {
    Geometry a = readCurve(A);
    OverlayNGCurve self = new OverlayNGCurve(a, a);
    self.getResult(OverlayNGCurve.INTERSECTION);
    assertFalse("R2: a self-CAP is exact, so it must not be flagged approximate",
        self.isApproximate());

    OverlayNGCurve nested = new OverlayNGCurve(a, readCurve(B));
    nested.getResult(OverlayNGCurve.INTERSECTION);
    assertFalse("R2: a nested CAP returns an operand, so it is exact too",
        nested.isApproximate());
  }

  /**
   * Honesty lock for the two-disc remainder: nested DIFFERENCE is the
   * exact annulus (outer r=5, inner r=3), not a densified R2 flag.
   */
  public void testR2_densifiedAnswersAreFlagged() throws Exception {
    OverlayNGCurve annulus = new OverlayNGCurve(readCurve(A), readCurve(B));
    Geometry result = annulus.getResult(OverlayNGCurve.DIFFERENCE);
    assertFalse("the annulus is not empty", result.isEmpty());
    assertFalse("R1.5: nested DIFFERENCE is exact and must not be flagged",
        annulus.isApproximate());
    assertEquals("annulus area 16π", AREA_A - AREA_B, result.getArea(), EXACT);
    assertEquals("one interior ring", 1,
        ((CurvePolygon) result).getNumInteriorRing());
  }

  // -- F1: fast before fat -------------------------------------------------

  /**
   * F1 -- the algebra must run before densification, not after.
   * <p>
   * Asserted structurally rather than by timing: if densification had run, the
   * result would carry ~1570 vertices. Five vertices out of a self-operation is
   * only possible if the algebra answered first. A timing assertion would be
   * flaky; this one cannot be.
   */
  public void testF1_algebraRunsBeforeDensify() throws Exception {
    Geometry a = readCurve(A);
    for (int opCode : new int[] { OverlayNGCurve.INTERSECTION, OverlayNGCurve.UNION }) {
      Geometry result = OverlayNGCurve.overlay(a, a, opCode);
      assertEquals("F1: opCode " + opCode + " must not densify a self-operation",
          5, result.getNumPoints());
    }
  }

  // -- V3: type gate -------------------------------------------------------

  /**
   * V3 -- plain input must behave exactly as stock OverlayNG, so routing
   * everything through this class is safe.
   */
  public void testV3_plainInputMatchesStockOverlayNG() throws Exception {
    Geometry p = readCurve("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    Geometry q = readCurve("POLYGON ((5 5, 15 5, 15 15, 5 15, 5 5))");
    assertEquals("CAP", 25.0, OverlayNGCurve.intersection(p, q).getArea(), 0.0);
    assertEquals("CUP", 175.0, OverlayNGCurve.union(p, q).getArea(), 0.0);
    assertEquals("SUB", 75.0, OverlayNGCurve.difference(p, q).getArea(), 0.0);
    assertEquals("XOR", 150.0, OverlayNGCurve.symDifference(p, q).getArea(), 0.0);
  }

  /** V3 -- a plain self-operation gets the algebra too, exactly. */
  public void testV3_plainSelfOperations() throws Exception {
    Geometry p = readCurve("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    assertEquals("plain CAP self", 100.0,
        OverlayNGCurve.intersection(p, p).getArea(), 0.0);
    assertEquals("plain CAP self keeps its own vertices", 5,
        OverlayNGCurve.intersection(p, p).getNumPoints());
    assertTrue("plain SUB self", OverlayNGCurve.difference(p, p).isEmpty());
  }

  // -- guards --------------------------------------------------------------

  /** Guard: a genuinely crossing pair still gets a real noded answer. */
  public void testCrossingPairStillOverlays() throws Exception {
    Geometry a = readCurve(A);
    Geometry shifted = readCurve(
        "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))");
    OverlayNGCurve op = new OverlayNGCurve(a, shifted);
    Geometry cap = op.getResult(OverlayNGCurve.INTERSECTION);
    assertFalse("two overlapping circles must intersect", cap.isEmpty());
    assertFalse("R1.5 crossing CAP is exact", op.isApproximate());
    assertEquals("the lens is two arcs, not a densified ring",
        "CurvePolygon", cap.getGeometryType());
    assertTrue("and the lens must be smaller than either disc, got " + cap.getArea(),
        cap.getArea() < AREA_A - 1.0);
    assertTrue("and larger than nothing", cap.getArea() > 0.0);
  }

  /** Guard: disjoint curves give an empty CAP and give back A for SUB. */
  public void testDisjointPair() throws Exception {
    Geometry a = readCurve(A);
    Geometry far = readCurve(
        "CURVEPOLYGON (CIRCULARSTRING (100 0, 105 5, 110 0, 105 -5, 100 0))");
    assertTrue("disjoint CAP is empty", OverlayNGCurve.intersection(a, far).isEmpty());
    assertEquals("disjoint SUB keeps all of A", AREA_A,
        OverlayNGCurve.difference(a, far).getArea(), EXACT);
  }

  /** Guard: both empty is empty, for all four ops. */
  public void testBothEmpty() throws Exception {
    Geometry e1 = readCurve(EMPTY_CURVE);
    Geometry e2 = readCurve(EMPTY_CURVE);
    assertTrue(OverlayNGCurve.intersection(e1, e2).isEmpty());
    assertTrue(OverlayNGCurve.union(e1, e2).isEmpty());
    assertTrue(OverlayNGCurve.difference(e1, e2).isEmpty());
    assertTrue(OverlayNGCurve.symDifference(e1, e2).isEmpty());
  }
}
