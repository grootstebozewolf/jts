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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Green tests for D-PT: {@link CircularString#distance(org.locationtech.jts.geom.Geometry)}
 * against a puntal geometry uses the analytical point-to-arc distance (clamped
 * to each arc's sweep) rather than the chord-polyline distance.
 *
 * <p>For a point whose projection onto the circle lies on the arc the distance
 * is {@code |d - R|}; for a point at the centre it is {@code R}; otherwise it
 * is the distance to the nearer arc endpoint.
 */
public class CircularStringDistanceProofTest extends GeometryTestCase {

  private static final double TOL = 1e-9;
  private static final GeometryFactory FACT = new GeometryFactory();

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CircularStringDistanceProofTest.class); }
  public CircularStringDistanceProofTest(String name) { super(name); }

  /** Upper half-circle R=5 about the origin. */
  private static CircularString halfArcR5() {
    return new CircularString(FACT.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(-5, 0), new Coordinate(0, 5), new Coordinate(5, 0)
    }), FACT);
  }

  private static Point pt(double x, double y) {
    return FACT.createPoint(new Coordinate(x, y));
  }

  /** External point above the centre, foot on the arc -> |10 - 5| = 5. */
  public void testExternalPointWithinSweep() {
    assertEquals(5.0, halfArcR5().distance(pt(0, 10)), TOL);
  }

  /** Internal point, foot on the arc -> |3 - 5| = 2. */
  public void testInternalPointWithinSweep() {
    assertEquals(2.0, halfArcR5().distance(pt(0, 3)), TOL);
  }

  /** Point at the centre -> R (every arc point is R away). */
  public void testPointAtCentreIsRadius() {
    assertEquals(5.0, halfArcR5().distance(pt(0, 0)), TOL);
  }

  /** Point whose foot is off the (upper) arc -> distance to nearer endpoint. */
  public void testPointOutsideSweepUsesEndpoint() {
    // (0,-10): foot direction points down, off the upper arc; nearer endpoint
    // is (-5,0) or (5,0) at sqrt(125).
    assertEquals(Math.sqrt(125.0), halfArcR5().distance(pt(0, -10)), TOL);
  }

  /** MultiPoint distance is the minimum over the points. */
  public void testMultiPointMinimum() {
    Point[] ps = { pt(0, 10), pt(0, 3) };
    org.locationtech.jts.geom.MultiPoint mp = FACT.createMultiPoint(ps);
    assertEquals(2.0, halfArcR5().distance(mp), TOL);
  }

  /** Degenerate (collinear) controls fall back to point-to-chord distance. */
  public void testCollinearControlsUsePointToSegment() {
    CircularString line = new CircularString(
        FACT.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(0, 0), new Coordinate(5, 0), new Coordinate(10, 0)
        }), FACT);
    // Point above the middle: perpendicular distance 4 to the segment y=0.
    assertEquals(4.0, line.distance(pt(5, 4)), TOL);
  }
}
