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
package org.locationtech.jts.geom.curved;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * OVL-OPS: the overlay operations must see the arc.
 * <p>
 * For two concentric circles {@code A} of radius 5 and {@code B} of radius 3,
 * every overlay answer came from the inscribed squares:
 * <pre>
 * A.intersection(B)   18  should be  9*pi = 28.274
 * A.union(B)          50  should be 25*pi = 78.540
 * A.difference(B)     32  should be 16*pi = 50.265
 * A.symDifference(B)  32  should be 16*pi = 50.265
 * </pre>
 * These are instance methods, so the remedy is the CRV-OPS one that
 * {@code convexHull}, {@code distance} and {@code buffer} already use: override
 * and densify both operands. They are not structural like {@code addHole} --
 * overlay has to node the linework, and noding arcs exactly would need arc-arc
 * intersection and arc splitting, a subsystem rather than a shim. So the results
 * here are plain densified polygons, and deliberately so; the alternative is not
 * a tighter tolerance but an arc-aware noder.
 * <p>
 * <b>Why this was worse than a wrong number.</b> {@code OverlayNG.overlay} for
 * UNION and DIFFERENCE did not return a wrong answer, it threw
 * {@code TopologyException: Result area inconsistent with overlay operation}.
 * That check compares the result's area against the operands', and CRV-OPS had
 * already made {@code getArea()} arc-aware while leaving {@code getCoordinates()}
 * chord-based -- so a CurvePolygon reported an area of 78.54 for linework
 * enclosing 50. Core's own invariant caught the contradiction. Densifying at the
 * boundary removes it, because the operand core sees then has one area.
 * <p>
 * Guarded here as well: {@code A.union()} with no argument was already exact,
 * returning the CurvePolygon itself with area {@code 25*pi}. Overriding the
 * no-argument form too would have replaced an exact answer with a densified one,
 * so it is deliberately left alone and locked by
 * {@link #testUnaryUnionStaysExact()}.
 */
public class CurveOverlayTest extends GeometryTestCase {

  private static final String A = "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String B = "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";

  private static final double AREA_A = 25.0 * Math.PI;
  private static final double AREA_B = 9.0 * Math.PI;
  private static final double ANNULUS = AREA_A - AREA_B;

  /**
   * An inscribed polygon understates its circle's area by
   * {@code pi*r^2*theta^2/6}; at the ~1570 segments the 1e-6 tolerance implies,
   * that is 2.1e-4 for r=5 and 7.5e-5 for r=3. 1e-3 is loose by an order and
   * still four orders inside the errors this test is meant to catch, which run
   * from 10.3 to 28.5.
   */
  private static final double AREA_TOL = 1.0e-3;

  public static void main(String[] args) { TestRunner.run(CurveOverlayTest.class); }

  public CurveOverlayTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurvedWKTReader(new CurvedGeometryFactory()).read(wkt);
  }

  public void testIntersectionIsTheInnerCircle() throws Exception {
    assertEquals("A and B intersect in B, area 9*pi, not the square's 18",
        AREA_B, readCurve(A).intersection(readCurve(B)).getArea(), AREA_TOL);
  }

  public void testUnionIsTheOuterCircle() throws Exception {
    assertEquals("A and B union to A, area 25*pi, not the square's 50",
        AREA_A, readCurve(A).union(readCurve(B)).getArea(), AREA_TOL);
  }

  public void testDifferenceIsTheAnnulus() throws Exception {
    assertEquals("A less B is the annulus, area 16*pi, not the squares' 32",
        ANNULUS, readCurve(A).difference(readCurve(B)).getArea(), AREA_TOL);
  }

  public void testSymDifferenceIsTheAnnulus() throws Exception {
    assertEquals("B is inside A, so the symmetric difference is the annulus",
        ANNULUS, readCurve(A).symDifference(readCurve(B)).getArea(), AREA_TOL);
  }

  /**
   * The invariant OverlayNG checks and that CRV-OPS was breaking: the parts must
   * add up. Independent of what the true areas are.
   */
  public void testOverlayResultsAreSelfConsistent() throws Exception {
    Geometry a = readCurve(A), b = readCurve(B);
    double inter = a.intersection(b).getArea();
    double diff = a.difference(b).getArea();
    double union = a.union(b).getArea();
    assertEquals("intersection + difference should be A", a.getArea(),
        inter + diff, AREA_TOL);
    assertEquals("union should be A + B - intersection",
        a.getArea() + b.getArea() - inter, union, AREA_TOL);
  }

  /** A curve against a plain polygon: a quarter disc. */
  public void testOverlayWithAPlainPolygon() throws Exception {
    Geometry quarter = readCurve(A).intersection(
        readCurve("POLYGON ((0 0, 5 0, 5 5, 0 5, 0 0))"));
    assertEquals("the first-quadrant quarter of a radius-5 disc",
        AREA_A / 4.0, quarter.getArea(), AREA_TOL);
  }

  /** A CircularString operand must be densified too, not just a CurvePolygon. */
  public void testCircularStringOverlay() throws Exception {
    Geometry arc = readCurve("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    Geometry clipped = arc.intersection(
        readCurve("POLYGON ((-6 -1, 6 -1, 6 1, -6 1, -6 -1))"));
    assertTrue("the arc should meet the band in more than the two chord endpoints, got "
        + clipped.getNumPoints() + " points", clipped.getNumPoints() > 4);
  }

  /** Guard: B is wholly inside A, so B less A is empty. Already correct. */
  public void testDifferenceBAIsEmpty() throws Exception {
    assertTrue("B is inside A", readCurve(B).difference(readCurve(A)).isEmpty());
  }

  /**
   * Guard: the no-argument union was already exact and must stay exact. It
   * returns the CurvePolygon itself, so densifying it would be a regression.
   */
  public void testUnaryUnionStaysExact() throws Exception {
    Geometry unioned = readCurve(A).union();
    assertEquals("unary union of one curve polygon should be exact",
        AREA_A, unioned.getArea(), 1.0e-9);
  }

  /** Guard: plain polygons overlay exactly as before, to the last bit. */
  public void testPlainPolygonsUnchanged() throws Exception {
    Geometry p = readCurve("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    Geometry q = readCurve("POLYGON ((5 5, 15 5, 15 15, 5 15, 5 5))");
    assertEquals("intersection", 25.0, p.intersection(q).getArea(), 0.0);
    assertEquals("union", 175.0, p.union(q).getArea(), 0.0);
    assertEquals("difference", 75.0, p.difference(q).getArea(), 0.0);
    assertEquals("symDifference", 150.0, p.symDifference(q).getArea(), 0.0);
  }

  /** Guard: an all-linear CurvePolygon behaves like the plain polygon it is. */
  public void testAllLinearCurvePolygonUnchanged() throws Exception {
    Geometry p = readCurve("CURVEPOLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    Geometry q = readCurve("CURVEPOLYGON ((5 5, 15 5, 15 15, 5 15, 5 5))");
    assertEquals("intersection", 25.0, p.intersection(q).getArea(), 0.0);
    assertEquals("union", 175.0, p.union(q).getArea(), 0.0);
  }

  /** Guard: empty operands do not throw. */
  public void testEmptyOperand() throws Exception {
    Geometry a = readCurve(A);
    assertEquals("A less nothing is A", a.getArea(),
        a.difference(readCurve("CURVEPOLYGON EMPTY")).getArea(), AREA_TOL);
  }
}
