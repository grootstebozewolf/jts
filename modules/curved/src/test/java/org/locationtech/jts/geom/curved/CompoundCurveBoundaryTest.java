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

import java.util.HashSet;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Dimension;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * B-CC (#1195): the boundary of a {@link CompoundCurve} is its two endpoints
 * when open, and empty when closed (start point == end point) — the Mod-2
 * boundary rule for a single lineal curve. CompoundCurve is the phase-1 flat
 * stand-in (control points collapsed to one sequence), so the inherited
 * {@link org.locationtech.jts.geom.LineString} / {@code BoundaryOp} behaviour
 * already satisfies the spec; this test pins the contract (including arc-shaped
 * control points, where the boundary must be the curve endpoints, never the arc
 * mid points) so a future member-structured CompoundCurve can't regress it.
 */
public class CompoundCurveBoundaryTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CompoundCurveBoundaryTest.class);
  }

  public CompoundCurveBoundaryTest(String name) { super(name); }

  private final CurvedGeometryFactory gf = new CurvedGeometryFactory();

  private CompoundCurve cc(double... xy) {
    Coordinate[] pts = new Coordinate[xy.length / 2];
    for (int i = 0; i < pts.length; i++)
      pts[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
    return gf.createCompoundCurve(new CoordinateArraySequence(pts));
  }

  /** Open curve: boundary is exactly the first and last control points. */
  public void testOpenBoundaryIsTwoEndpoints() {
    Geometry b = cc(0,0, 1,0, 2,1).getBoundary();
    assertFalse("open curve has a non-empty boundary", b.isEmpty());
    assertEquals("two boundary points", 2, b.getNumPoints());
    assertEquals(Dimension.P, b.getDimension());
    assertBoundaryPoints(b, new Coordinate(0,0), new Coordinate(2,1));
  }

  /** Closed curve (first == last): empty boundary. */
  public void testClosedBoundaryIsEmpty() {
    CompoundCurve closed = cc(0,0, 2,0, 2,2, 0,2, 0,0);
    assertTrue("closed curve is closed", closed.isClosed());
    assertTrue("closed curve has empty boundary", closed.getBoundary().isEmpty());
  }

  /** Arc-shaped control points: the boundary is the endpoints, not the arc mid point. */
  public void testOpenArcBoundaryIgnoresMidPoint() {
    // semicircle control points (5,0)-(0,5)-(-5,0): mid (0,5) must not be a boundary point
    Geometry b = cc(5,0, 0,5, -5,0).getBoundary();
    assertEquals(2, b.getNumPoints());
    assertBoundaryPoints(b, new Coordinate(5,0), new Coordinate(-5,0));
  }

  /** A closed loop of arc-shaped control points (full circle) has an empty boundary. */
  public void testClosedArcLoopBoundaryIsEmpty() {
    CompoundCurve circle = cc(5,0, 0,5, -5,0, 0,-5, 5,0);
    assertTrue(circle.isClosed());
    assertTrue(circle.getBoundary().isEmpty());
  }

  /**
   * Boundary dimension follows the Mod-2 contract: an open curve has a 0-D
   * (point) boundary, a closed curve has an empty (FALSE) boundary.
   */
  public void testBoundaryDimension() {
    assertEquals(Dimension.P, cc(0,0, 1,0, 2,1).getBoundaryDimension());
    assertEquals(Dimension.FALSE, cc(0,0, 2,0, 0,0).getBoundaryDimension());
  }

  /** Empty compound curve: empty boundary. */
  public void testEmptyBoundaryIsEmpty() {
    assertTrue(gf.createCompoundCurve(new CoordinateArraySequence(new Coordinate[0]))
        .getBoundary().isEmpty());
  }

  private static void assertBoundaryPoints(Geometry boundary, Coordinate... expected) {
    Set<Coordinate> got = new HashSet<Coordinate>();
    for (Coordinate c : boundary.getCoordinates()) got.add(c);
    Set<Coordinate> exp = new HashSet<Coordinate>();
    for (Coordinate c : expected) exp.add(c);
    assertEquals("boundary point set", exp, got);
  }
}
