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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Green tests for C-AREA: {@link CurvePolygon#getCentroid()} returns the area
 * centroid computed analytically (Green's-theorem moments combining the
 * chord-polygon centroid with each circular segment's), not the centroid of
 * the densified ring.
 *
 * <p>Verified against closed forms: a disk centres on its centre, a half-disk
 * on {@code 4R/(3*pi)} from the diameter, a symmetric disk-with-hole on the
 * centre, and a straight ring matches the plain polygon centroid.
 */
public class CurvePolygonCentroidProofTest extends GeometryTestCase {

  private static final double TOL = 1e-6;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CurvePolygonCentroidProofTest.class); }
  public CurvePolygonCentroidProofTest(String name) { super(name); }

  private static Coordinate centroidOf(String wkt) {
    try {
      Geometry g = new CurvedWKTReader().read(wkt);
      return g.getCentroid().getCoordinate();
    } catch (ParseException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  /** Full disk R=10 at the origin -> centroid at the centre. */
  public void testDiskCentroidIsCentre() {
    Coordinate c = centroidOf(
        "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0))");
    assertEquals(0.0, c.x, TOL);
    assertEquals(0.0, c.y, TOL);
  }

  /** Disk R=10 centred at (3,-2) -> centroid at (3,-2) (frame-shift robustness). */
  public void testOffsetDiskCentroidIsItsCentre() {
    Coordinate c = centroidOf(
        "CURVEPOLYGON (CIRCULARSTRING (-7 -2, 3 8, 13 -2, 3 -12, -7 -2))");
    assertEquals(3.0, c.x, TOL);
    assertEquals(-2.0, c.y, TOL);
  }

  /** Upper half-disk R=10 -> centroid at (0, 4R/(3*pi)). */
  public void testHalfDiskCentroid() {
    Coordinate c = centroidOf(
        "CURVEPOLYGON (COMPOUNDCURVE ((-10 0, 10 0), CIRCULARSTRING (10 0, 0 10, -10 0)))");
    assertEquals(0.0, c.x, TOL);
    assertEquals(4.0 * 10.0 / (3.0 * Math.PI), c.y, TOL);
  }

  /** Disk (R=10) with a concentric circular hole (R=5) -> centroid at centre. */
  public void testDiskWithConcentricHoleCentroidIsCentre() {
    Coordinate c = centroidOf(
        "CURVEPOLYGON ("
        + "CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0), "
        + "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))");
    assertEquals(0.0, c.x, TOL);
    assertEquals(0.0, c.y, TOL);
  }

  /** Straight (non-curved) ring matches the plain polygon centroid. */
  public void testStraightRingMatchesFlatPolygon() {
    Coordinate c = centroidOf("CURVEPOLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    assertEquals(5.0, c.x, TOL);
    assertEquals(5.0, c.y, TOL);
  }
}
