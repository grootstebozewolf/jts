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

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * The circle-circle radical-axis enumerator lifted from
 * {@code CircularArcDensifier.circlesIntersectOnBothSweeps}, which used to
 * throw the points away.
 */
public class CircularIntersectionTest extends GeometryTestCase {

  private static final double EPS = 1.0e-12;

  public static void main(String[] args) {
    TestRunner.run(CircularIntersectionTest.class);
  }

  public CircularIntersectionTest(String name) { super(name); }

  /**
   * Locked pair: centres (0,0) and (7,0), r=5. Radical axis x=3.5,
   * y=±√(25−12.25)=±√12.75. (3.5, ±3.5) is not on either circle.
   */
  public void testCrossingDiscsReturnBothNodes() {
    CircularArcDensifier.Circle a = new CircularArcDensifier.Circle(0, 0, 5);
    CircularArcDensifier.Circle b = new CircularArcDensifier.Circle(7, 0, 5);
    Coordinate[] pts = CircularArcDensifier.intersectCircles(a, b);
    assertEquals("two proper crossings", 2, pts.length);
    double y = Math.sqrt(12.75);
    assertTrue("one node is (3.5, +√12.75)", hasPoint(pts, 3.5, y));
    assertTrue("one node is (3.5, -√12.75)", hasPoint(pts, 3.5, -y));
  }

  public void testDisjointCirclesReturnNone() {
    CircularArcDensifier.Circle a = new CircularArcDensifier.Circle(0, 0, 5);
    CircularArcDensifier.Circle b = new CircularArcDensifier.Circle(20, 0, 5);
    assertEquals(0, CircularArcDensifier.intersectCircles(a, b).length);
  }

  public void testNestedCirclesReturnNone() {
    CircularArcDensifier.Circle a = new CircularArcDensifier.Circle(0, 0, 5);
    CircularArcDensifier.Circle b = new CircularArcDensifier.Circle(0, 0, 3);
    assertEquals("coincident centres", 0,
        CircularArcDensifier.intersectCircles(a, b).length);
    CircularArcDensifier.Circle inner = new CircularArcDensifier.Circle(1, 0, 2);
    assertEquals("strictly inside", 0,
        CircularArcDensifier.intersectCircles(a, inner).length);
  }

  public void testTangentCirclesReturnOne() {
    CircularArcDensifier.Circle a = new CircularArcDensifier.Circle(0, 0, 5);
    CircularArcDensifier.Circle b = new CircularArcDensifier.Circle(10, 0, 5);
    Coordinate[] pts = CircularArcDensifier.intersectCircles(a, b);
    assertEquals("external tangent", 1, pts.length);
    assertEquals(5.0, pts[0].x, EPS);
    assertEquals(0.0, pts[0].y, EPS);
  }

  /**
   * Upper semicircle of each locked disc: both nodes lie on both sweeps.
   */
  public void testIntersectArcsKeepsSweepHits() {
    Coordinate[] pts = CircularArcDensifier.intersectArcs(
        new Coordinate(-5, 0), new Coordinate(0, 5), new Coordinate(5, 0),
        new Coordinate(2, 0), new Coordinate(7, 5), new Coordinate(12, 0));
    assertEquals(1, pts.length);
    assertEquals(3.5, pts[0].x, EPS);
    assertEquals(Math.sqrt(12.75), pts[0].y, EPS);
  }

  public void testIntersectArcsDropsOffSweep() {
    Coordinate[] pts = CircularArcDensifier.intersectArcs(
        new Coordinate(-5, 0), new Coordinate(0, 5), new Coordinate(5, 0),
        new Coordinate(2, 0), new Coordinate(7, -5), new Coordinate(12, 0));
    assertEquals("upper A vs lower B share no sweep point", 0, pts.length);
  }

  private static boolean hasPoint(Coordinate[] pts, double x, double y) {
    for (int i = 0; i < pts.length; i++) {
      if (Math.abs(pts[i].x - x) <= EPS && Math.abs(pts[i].y - y) <= EPS) {
        return true;
      }
    }
    return false;
  }
}
