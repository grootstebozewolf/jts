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
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateArrays;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * DENS-ANCHOR: control points must survive {@code toLinear} exactly, even when
 * a computed vertex lands on top of them.
 * <p>
 * Reported from visual QA on {@code CIRCULARSTRING (1 0, 0 1, -1 0)} at
 * tolerance 0.01: the endpoints round-trip exactly, but the middle control
 * point comes back as {@code (6.123233995736766e-17, 1)} -- {@code cos(pi/2)}
 * in floating point -- instead of the {@code (0, 1)} that was put in.
 * <p>
 * The mechanism is a lost tie. Twelve segments cover the semicircle, so the
 * chord walk's sixth vertex falls at the same sweep angle as the projected
 * anchor for the middle control point. The sweep loop inserts anchors strictly
 * <em>before</em> each computed vertex ({@code sweepAngle < sweepEnd}), so on a
 * tie the computed vertex is emitted first, and {@code addUnique} then drops
 * the exact anchor as a near-duplicate. The coincidence-suppression from
 * DENS-DUP is doing its job on the wrong point: when an anchor and a computed
 * vertex coincide, the anchor is the one that carries information.
 * <p>
 * Everything else about the reported output was already right, asserted here as
 * guards so this cycle cannot regress it: 13 points is exactly the sagitta
 * bound for tolerance 0.01, and the length 3.132628613281238 is exactly twelve
 * inscribed unit-semicircle chords -- the deficit from pi is inherent to
 * inscribed sampling and within tolerance.
 */
public class DensifierAnchorPinningTest extends GeometryTestCase {

  private static final String SEMI = "CIRCULARSTRING (1 0, 0 1, -1 0)";
  private static final Coordinate APEX = new Coordinate(0, 1);

  public static void main(String[] args) {
    TestRunner.run(DensifierAnchorPinningTest.class);
  }

  public DensifierAnchorPinningTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static Coordinate[] toLinear(String wkt, double tol) throws Exception {
    return ((Linearizable) readCurve(wkt)).toLinear(tol).getCoordinates();
  }

  private static void assertContainsExactly(Coordinate[] pts, Coordinate anchor) {
    Coordinate nearest = null;
    double best = Double.MAX_VALUE;
    for (Coordinate p : pts) {
      double d = p.distance(anchor);
      if (d < best) { best = d; nearest = p; }
    }
    assertTrue("the control point " + anchor + " must survive exactly; nearest "
        + "output vertex is " + nearest + ", off by " + best,
        nearest != null && nearest.equals2D(anchor) && best == 0.0);
  }

  /**
   * The reported case: 12 segments, so the anchor ties with a computed vertex
   * and must win the tie.
   */
  public void testMidControlPointExactOnTie() throws Exception {
    assertContainsExactly(toLinear(SEMI, 0.01), APEX);
  }

  /**
   * An odd segment count (11, from tolerance 0.012), so the anchor falls
   * between computed vertices and is inserted rather than tied. This path
   * already worked; pinned so the fix cannot break it.
   */
  public void testMidControlPointExactBetweenVertices() throws Exception {
    assertContainsExactly(toLinear(SEMI, 0.012), APEX);
  }

  /** Coarse tolerance, few segments; the anchor must still be there. */
  public void testMidControlPointExactAtCoarseTolerance() throws Exception {
    assertContainsExactly(toLinear(SEMI, 0.2), APEX);
  }

  /** Every control point of a multi-arc string survives exactly. */
  public void testAllControlPointsOfTwoArcs() throws Exception {
    Coordinate[] pts = toLinear("CIRCULARSTRING (1 0, 0 1, -1 0, -2 -1, -3 0)", 0.01);
    assertContainsExactly(pts, new Coordinate(1, 0));
    assertContainsExactly(pts, new Coordinate(0, 1));
    assertContainsExactly(pts, new Coordinate(-1, 0));
    assertContainsExactly(pts, new Coordinate(-2, -1));
    assertContainsExactly(pts, new Coordinate(-3, 0));
  }

  // -- guards: everything the reported output already got right --------------

  /** Endpoints were already pinned exactly. */
  public void testEndpointsExact() throws Exception {
    Coordinate[] pts = toLinear(SEMI, 0.01);
    assertTrue("start exact", pts[0].equals2D(new Coordinate(1, 0)));
    assertTrue("end exact", pts[pts.length - 1].equals2D(new Coordinate(-1, 0)));
  }

  /** 13 points is the sagitta bound for 0.01 on a unit semicircle; keep it. */
  public void testVertexCountStaysAtTheBound() throws Exception {
    assertEquals("12 segments for tolerance 0.01", 13, toLinear(SEMI, 0.01).length);
  }

  /** DENS-DUP's guarantee must survive the anchor preference. */
  public void testNoRepeatedPoints() throws Exception {
    for (double tol : new double[] { 0.01, 0.012, 0.2, 0.001 }) {
      Coordinate[] pts = toLinear(SEMI, tol);
      assertFalse("no repeated points at tolerance " + tol,
          CoordinateArrays.hasRepeatedPoints(pts));
      for (int i = 1; i < pts.length; i++) {
        assertTrue("no near-coincident points at tolerance " + tol,
            pts[i - 1].distance(pts[i]) > 1.0e-9);
      }
    }
  }

  /** Every output vertex still lies on the true circle. */
  public void testVerticesStayOnTheArc() throws Exception {
    for (Coordinate p : toLinear(SEMI, 0.01)) {
      assertEquals("vertex " + p + " must lie on the unit circle",
          1.0, Math.hypot(p.x, p.y), 1.0e-12);
    }
  }
}
