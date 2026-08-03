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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * CRV-OPS: the inherited spatial operations must see the arc, not the chords
 * through its control points.
 * <p>
 * {@code convexHull()}, {@code distance()} and {@code buffer()} all walk
 * {@code getCoordinates()}, which for a curve is only the control points. The
 * epic filed these separately -- arc extrema missing from
 * ConvexHull/ConcaveHull, LinearComponentExtracter skipping curves in
 * DistanceOp, buffer offsetting chords instead of arcs -- but they share one
 * cause and one fix: linearise first, then delegate.
 * <p>
 * jts-core cannot do this itself; it has no visibility of the curve types
 * (jts-curve depends on core, not the reverse). The curve types are where the
 * arc geometry is known, so that is where the override belongs.
 * <p>
 * The 270-degree unit arc below passes the top (0,1) and the left (-1,0), and
 * neither is a control point -- so anything driven off control points misses
 * both.
 */
public class CurveOperationsTest extends GeometryTestCase {

  private static final double R = Math.sqrt(0.5);

  /** 270-degree unit arc: 0 -> 135 -> 270 degrees, anticlockwise. */
  private static final String ARC_270 =
      "CIRCULARSTRING (1 0, " + (-R) + " " + R + ", 0 -1)";

  /** Radius-2 circle as two semicircular arcs. */
  private static final String CIRCLE_R2 =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 0 2, -2 0, 0 -2, 2 0))";

  public static void main(String[] args) {
    TestRunner.run(CurveOperationsTest.class);
  }

  public CurveOperationsTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader().read(wkt);
  }

  private Geometry point(double x, double y) {
    return new org.locationtech.jts.geom.GeometryFactory()
        .createPoint(new Coordinate(x, y));
  }

  /**
   * The hull reaches the arc's extrema, to within the densification tolerance.
   * <p>
   * It cannot strictly <em>cover</em> them: a densified arc is an inscribed
   * polyline, so it lies just inside the true arc and the exact extremum sits
   * marginally outside the hull. This differs from the envelope, which must
   * over-cover and so uses the exact axis extremes. The failing behaviour it
   * replaces was not marginal -- the hull was the control-point triangle, a
   * whole radius short.
   */
  public void testConvexHullReachesArcExtrema() throws Exception {
    Geometry hull = readCurve(ARC_270).convexHull();
    assertTrue("hull should reach the arc top (0,1), gap was "
        + hull.distance(point(0, 1)),
        hull.distance(point(0, 1)) < 1.0e-4);
    assertTrue("hull should reach the arc left (-1,0), gap was "
        + hull.distance(point(-1, 0)),
        hull.distance(point(-1, 0)) < 1.0e-4);
  }

  /** The hull of a 270-degree unit arc approaches the circle's area. */
  public void testConvexHullAreaApproachesCircle() throws Exception {
    double hullArea = readCurve(ARC_270).convexHull().getArea();
    // The hull of a 270-degree arc plus its chord: strictly less than the full
    // disc (pi) but far more than the control-point triangle (0.85).
    assertTrue("hull area " + hullArea + " should exceed 2.5",
        hullArea > 2.5);
    assertTrue("hull area " + hullArea + " cannot exceed the unit disc",
        hullArea <= Math.PI + 1.0e-9);
  }

  /** Distance to a point above the arc is measured to the arc. */
  public void testDistanceMeasuredToArc() throws Exception {
    // (0,2) is 1.0 above the arc's top point (0,1).
    double d = readCurve(ARC_270).distance(point(0, 2));
    assertEquals("distance should be to the arc top, not a chord",
        1.0, d, 1.0e-3);
  }

  /** Distance between two curves sees both arcs. */
  public void testDistanceBetweenTwoCurves() throws Exception {
    // Two unit semicircles, the second translated 4 to the right. The first
    // bulges up to (1,1) and ends at (2,0); the second starts at (4,0).
    Geometry a = readCurve("CIRCULARSTRING (0 0, 1 1, 2 0)");
    Geometry b = readCurve("CIRCULARSTRING (4 0, 5 1, 6 0)");
    assertEquals("gap between the arc endpoints is 2",
        2.0, a.distance(b), 1.0e-3);
  }

  /**
   * Known limitation of the opt-in module design: asking a jts-core geometry
   * for its distance to a curve still measures to the chords, because
   * {@code Point.distance()} lives in jts-core and cannot see the curve types.
   * Curve-aware callers must put the curve on the left, or linearise first.
   * <p>
   * Locked deliberately so the asymmetry is a recorded decision rather than a
   * surprise, and so it fails loudly if the two sides are ever reconciled.
   */
  public void testCoreSideDistanceRemainsChordBased() throws Exception {
    Geometry arc = readCurve(ARC_270);
    Geometry p = point(0, 2);
    assertEquals("curve on the left sees the arc", 1.0, arc.distance(p), 1.0e-3);
    assertTrue("core geometry on the left still sees chords: " + p.distance(arc),
        p.distance(arc) > 1.4);
  }

  /** Buffering a circular polygon grows its radius, not its inscribed quad. */
  public void testBufferOfCircularPolygon() throws Exception {
    double area = readCurve(CIRCLE_R2).buffer(1.0).getArea();
    // Radius 2 grown by 1 -> radius 3; buffer approximates arcs so allow 1%.
    assertEquals("buffered circle should have area pi*3^2",
        Math.PI * 9.0, area, Math.PI * 9.0 * 0.01);
  }

  /** Buffering an arc yields a corridor about the arc's true length. */
  public void testBufferOfArcTracksArcLength() throws Exception {
    Geometry arc = readCurve(ARC_270);
    double w = 0.05;
    double area = arc.buffer(w).getArea();
    // A thin corridor about a curve of length L has area ~ 2*w*L + pi*w^2.
    double expected = 2.0 * w * arc.getLength() + Math.PI * w * w;
    assertEquals("buffer area should track the arc length", expected, area, expected * 0.05);
  }

  /** Guard: a plain LineString's hull and distance are unaffected. */
  public void testLineStringOperationsUnchanged() throws Exception {
    Geometry line = readCurve("LINESTRING (0 0, 4 0, 4 3)");
    assertEquals(3.0, line.distance(point(0, 3)), 1.0e-9);
    assertEquals(6.0, line.convexHull().getArea(), 1.0e-9);
  }

  /** Guard: colinear control points behave like the straight segment. */
  public void testColinearArcOperationsUnchanged() throws Exception {
    Geometry degenerate = readCurve("CIRCULARSTRING (0 0, 1 0, 2 0)");
    assertEquals(1.0, degenerate.distance(point(1, 1)), 1.0e-9);
  }

  /** Guard: an empty arc has zero-distance semantics unchanged. */
  public void testEmptyArcHull() throws Exception {
    assertTrue(readCurve("CIRCULARSTRING EMPTY").convexHull().isEmpty());
  }
}
