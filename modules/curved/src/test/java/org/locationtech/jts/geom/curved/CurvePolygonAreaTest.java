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
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Green tests for M-AREA-CP: {@link CurvePolygon#getArea()} uses the exact
 * circular-segment correction rather than the area of the flattened
 * control-point polygon.
 */
public class CurvePolygonAreaTest extends GeometryTestCase {

  /** Disks of radius 10 / 5 have area pi*R^2; checked to a generous epsilon. */
  private static final double TOL = 1e-6;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CurvePolygonAreaTest.class); }
  public CurvePolygonAreaTest(String name) { super(name); }

  private static Geometry readCurved(String wkt) {
    try {
      return new CurvedWKTReader().read(wkt);
    } catch (ParseException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  /** A full disk (R=10) as a single closed CircularString ring -> pi*R^2. */
  public void testFullDiskArea() {
    Geometry g = readCurved(
        "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0))");
    assertTrue(g instanceof CurvePolygon);
    assertEquals(Math.PI * 100.0, g.getArea(), TOL);
  }

  /** Bulge direction / traversal order must not change the (unsigned) area. */
  public void testAreaIsOrientationIndependent() {
    Geometry g = readCurved(
        "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 -10, 10 0, 0 10, -10 0))");
    assertEquals(Math.PI * 100.0, g.getArea(), TOL);
  }

  /** Half disk: a straight diameter plus a semicircular arc -> pi*R^2 / 2. */
  public void testHalfDiskFromCompoundCurveRing() {
    Geometry g = readCurved(
        "CURVEPOLYGON (COMPOUNDCURVE ((-10 0, 10 0), CIRCULARSTRING (10 0, 0 10, -10 0)))");
    assertEquals(Math.PI * 100.0 / 2.0, g.getArea(), TOL);
  }

  /** Disk (R=10) with a concentric circular hole (R=5) -> pi*(100 - 25). */
  public void testDiskWithCircularHole() {
    Geometry g = readCurved(
        "CURVEPOLYGON ("
        + "CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0), "
        + "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))");
    assertEquals(Math.PI * (100.0 - 25.0), g.getArea(), TOL);
  }

  /** A straight (non-curved) CurvePolygon ring matches the plain polygon area. */
  public void testStraightRingMatchesFlatPolygon() {
    Geometry g = readCurved("CURVEPOLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    assertEquals(100.0, g.getArea(), TOL);
  }

  /** An empty CurvePolygon has zero area. */
  public void testEmptyHasZeroArea() {
    Geometry g = readCurved("CURVEPOLYGON EMPTY");
    assertEquals(0.0, g.getArea(), 0.0);
  }

  /**
   * Without retained curve information the polygon must behave exactly like a
   * flat {@link org.locationtech.jts.geom.Polygon} (the analytical path is
   * opt-in, never a behaviour change for plain construction).
   */
  public void testFallsBackToFlatAreaWithoutCurveInfo() {
    GeometryFactory f = new GeometryFactory();
    LinearRing shell = f.createLinearRing(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0),
        new Coordinate(10, 10), new Coordinate(0, 10), new Coordinate(0, 0)
    });
    CurvePolygon cp = new CurvePolygon(shell, null, f);
    assertEquals(f.createPolygon(shell).getArea(), cp.getArea(), 0.0);
    assertEquals(100.0, cp.getArea(), 0.0);
  }
}
