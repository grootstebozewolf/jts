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

import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateArrays;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * DENS-DUP: linearising a curve must not emit repeated consecutive vertices.
 * <p>
 * {@code densifyArc} emits the chord vertices, then interleaves the projections
 * of any {@code mustInclude} anchors. When an anchor is one of the arc's own
 * control points -- which is exactly what {@code CompoundCurve.toLinear} passes
 * -- its projection coincides with a vertex already emitted, three ways:
 * <ul>
 * <li>{@code start} projects to sweep-angle 0 and is inserted immediately after
 *     the {@code start} added up front. Worse, the projection is recomputed as
 *     {@code (cx + r cos a0, cy + r sin a0)}, so for a semicircle where
 *     {@code a0 = pi} it lands on {@code (0, 6.1e-16)} rather than {@code (0,0)}
 *     -- a near-duplicate, not an exact one.</li>
 * <li>{@code mid} lands exactly on a chord vertex whenever the segment count is
 *     even, giving an exact duplicate.</li>
 * <li>{@code end} is appended after the chord loop, so the final
 *     {@code out.set(size-1, end)} snap overwrites the projected anchor instead
 *     of the last chord vertex, leaving both.</li>
 * </ul>
 * Repeated points are not merely untidy. They make a Delaunay triangulation
 * degenerate, which is why {@code ConcaveHull} throws
 * {@code IllegalStateException: Inconsistent adjacency - invalid triangulation}
 * on some densified arcs (issue #6) -- and does so unpredictably, failing at 84
 * and 791 vertices while succeeding at 254, so no choice of tolerance avoids it.
 */
public class DensifierRepeatedPointTest extends GeometryTestCase {

  private static final String COMPOUND =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 10 10))";

  public static void main(String[] args) {
    TestRunner.run(DensifierRepeatedPointTest.class);
  }

  public DensifierRepeatedPointTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  /** Reports the offending pair, so a failure names the vertex not just a count. */
  private static void assertNoRepeats(String label, Coordinate[] c) {
    for (int i = 1; i < c.length; i++) {
      double gap = c[i - 1].distance(c[i]);
      assertTrue(label + ": vertices " + (i - 1) + " and " + i
          + " are " + gap + " apart -- " + c[i - 1] + " then " + c[i],
          gap > 1.0e-9);
    }
    assertFalse(label + ": has exactly repeated points",
        CoordinateArrays.hasRepeatedPoints(c));
  }

  /** The direct densifier call, with the arc's own control points as anchors. */
  public void testDensifyArcWithOwnControlPointsAsAnchors() {
    Coordinate start = new Coordinate(0, 0);
    Coordinate mid = new Coordinate(5, 5);
    Coordinate end = new Coordinate(10, 0);
    List<Coordinate> anchors = Arrays.asList(start, mid, end);
    List<Coordinate> out =
        CircularArcDensifier.densifyArc(start, mid, end, 0.01, anchors);
    assertNoRepeats("densifyArc", out.toArray(new Coordinate[0]));
  }

  /** CompoundCurve.toLinear across the tolerances that break ConcaveHull. */
  public void testCompoundCurveToLinearHasNoRepeats() throws Exception {
    double[] tolerances = { 0.1, 0.05, 0.02, 0.01, 0.005, 0.002, 0.001, 0.0001, 0.00001 };
    for (double tol : tolerances) {
      Geometry lin = ((Linearizable) readCurve(COMPOUND)).toLinear(tol);
      assertNoRepeats("CompoundCurve.toLinear(" + tol + ")", lin.getCoordinates());
    }
  }

  /** A bare CircularString must be clean too. */
  public void testCircularStringToLinearHasNoRepeats() throws Exception {
    for (double tol : new double[] { 0.1, 0.01, 0.001, 0.00001 }) {
      Geometry lin = ((Linearizable) readCurve("CIRCULARSTRING (0 0, 5 5, 10 0)")).toLinear(tol);
      assertNoRepeats("CircularString.toLinear(" + tol + ")", lin.getCoordinates());
    }
  }

  /** A multi-arc CircularString, where arcs share transition points. */
  public void testMultiArcToLinearHasNoRepeats() throws Exception {
    Geometry lin = ((Linearizable) readCurve(
        "CIRCULARSTRING (2 0, 0 2, -2 0, 0 -2, 2 0)")).toLinear(0.001);
    assertNoRepeats("multi-arc toLinear", lin.getCoordinates());
  }

  /** A CurvePolygon shell, which must still close. */
  public void testCurvePolygonToLinearHasNoRepeats() throws Exception {
    Geometry lin = ((Linearizable) readCurve(
        "CURVEPOLYGON (CIRCULARSTRING (2 0, 0 2, -2 0, 0 -2, 2 0))")).toLinear(0.001);
    Coordinate[] shell =
        ((org.locationtech.jts.geom.Polygon) lin).getExteriorRing().getCoordinates();
    // A ring legitimately repeats its first point as its last; check the interior.
    assertNoRepeats("CurvePolygon shell",
        Arrays.copyOfRange(shell, 0, shell.length - 1));
    assertTrue("ring must still close", shell[0].equals2D(shell[shell.length - 1]));
  }

  /**
   * Guard: dedup must not defeat the reason anchors exist. Every control point
   * must still appear exactly in the output.
   */
  public void testControlPointsStillPresent() throws Exception {
    Geometry lin = ((Linearizable) readCurve(COMPOUND)).toLinear(0.01);
    Coordinate[] out = lin.getCoordinates();
    Coordinate[] wanted = {
        new Coordinate(0, 0), new Coordinate(5, 5),
        new Coordinate(10, 0), new Coordinate(10, 10) };
    for (Coordinate w : wanted) {
      boolean found = false;
      for (Coordinate o : out) {
        if (o.equals2D(w)) { found = true; break; }
      }
      assertTrue("control point " + w + " should survive linearisation", found);
    }
  }

  /** Guard: endpoints stay exact, and the arc is still densified. */
  public void testEndpointsExactAndStillDensified() throws Exception {
    Geometry lin = ((Linearizable) readCurve("CIRCULARSTRING (0 0, 5 5, 10 0)")).toLinear(0.01);
    Coordinate[] c = lin.getCoordinates();
    assertTrue("start exact", c[0].equals2D(new Coordinate(0, 0)));
    assertTrue("end exact", c[c.length - 1].equals2D(new Coordinate(10, 0)));
    assertTrue("still densified, got " + c.length, c.length > 20);
  }
}
