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

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Green tests for C-LIN: {@link CircularString#getCentroid()} returns the
 * arc-length-weighted centroid using the exact per-arc centroid (on the arc
 * bisector at {@code R*sin(alpha)/alpha} from the centre) rather than the
 * centroid of the flat control-point chord polyline.
 *
 * <p>Verified against closed forms: a half-arc's centroid is {@code 2R/pi}
 * from the centre, a quarter-arc's is {@code 2/pi} per axis, and a full
 * circle's is the centre.
 */
public class CircularStringCentroidProofTest extends GeometryTestCase {

  private static final double TOL = 1e-9;
  private static final GeometryFactory FACT = new GeometryFactory();

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CircularStringCentroidProofTest.class); }
  public CircularStringCentroidProofTest(String name) { super(name); }

  private static CircularString cs(double... xy) {
    Coordinate[] cc = new Coordinate[xy.length / 2];
    for (int i = 0; i < cc.length; i++) {
      cc[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
    }
    return new CircularString(FACT.getCoordinateSequenceFactory().create(cc), FACT);
  }

  /** Half-arc R=5 about origin: centroid at (0, 2R/pi). */
  public void testHalfArcCentroidIs2RoverPi() {
    Coordinate c = cs(-5, 0, 0, 5, 5, 0).getCentroid().getCoordinate();
    assertEquals(0.0, c.x, TOL);
    assertEquals(2.0 * 5.0 / Math.PI, c.y, TOL);
  }

  /** Quarter-arc R=1 from (1,0) to (0,1): centroid at (2/pi, 2/pi). */
  public void testQuarterArcCentroid() {
    double h = Math.sqrt(0.5);
    Coordinate c = cs(1, 0, h, h, 0, 1).getCentroid().getCoordinate();
    assertEquals(2.0 / Math.PI, c.x, TOL);
    assertEquals(2.0 / Math.PI, c.y, TOL);
  }

  /** Full circle as two semicircles: centroid is the centre, by symmetry. */
  public void testFullCircleCentroidAtCentre() {
    Coordinate c = cs(-10, 0, 0, 10, 10, 0, 0, -10, -10, 0).getCentroid().getCoordinate();
    assertEquals(0.0, c.x, TOL);
    assertEquals(0.0, c.y, TOL);
  }

  /** Centroid bulges to the convex side -- strictly off the chord. */
  public void testCentroidIsOffTheChord() {
    Coordinate c = cs(-5, 0, 0, 5, 5, 0).getCentroid().getCoordinate();
    assertTrue("centroid must lie off the (y=0) chord", c.y > 1.0);
  }

  /** Collinear controls: centroid is the chord midpoint. */
  public void testCollinearControlsCentroidIsChordMidpoint() {
    Coordinate c = cs(0, 0, 1, 0, 2, 0).getCentroid().getCoordinate();
    assertEquals(1.0, c.x, TOL);
    assertEquals(0.0, c.y, TOL);
  }
}
