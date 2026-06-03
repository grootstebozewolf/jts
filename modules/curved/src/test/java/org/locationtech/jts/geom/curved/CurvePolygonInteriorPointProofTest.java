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
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Green tests for C-IP: {@link CurvePolygon#getInteriorPoint()} returns a point
 * provably inside the curved region. Each returned point is independently
 * cross-checked against a finely-densified flattening of the same polygon
 * ({@code contains}), including the thin-crescent case the densified-ring
 * InteriorPointArea gets wrong.
 */
public class CurvePolygonInteriorPointProofTest extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CurvePolygonInteriorPointProofTest.class); }
  public CurvePolygonInteriorPointProofTest(String name) { super(name); }

  private static CurvePolygon readCP(String wkt) {
    try {
      return (CurvePolygon) new CurvedWKTReader().read(wkt);
    } catch (ParseException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  /** The interior point must lie inside a fine flattening of the same polygon. */
  private static void assertInteriorPointInside(CurvePolygon cp) {
    Point ip = cp.getInteriorPoint();
    assertFalse("interior point must not be empty", ip.isEmpty());
    Geometry flat = cp.toLinear(0.02);
    assertTrue("interior point " + ip + " must be inside the curved region",
        flat.contains(ip));
  }

  public void testDiskInteriorPointInside() {
    assertInteriorPointInside(
        readCP("CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0))"));
  }

  /** Annulus: the point must be in the ring, not in the hole. */
  public void testAnnulusInteriorPointInRingNotHole() {
    CurvePolygon cp = readCP(
        "CURVEPOLYGON ("
        + "CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0), "
        + "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))");
    Point ip = cp.getInteriorPoint();
    assertTrue("must be inside the annulus", cp.toLinear(0.02).contains(ip));
    double d = ip.getCoordinate().distance(new Coordinate(0, 0));
    assertTrue("interior point radius " + d + " must lie between hole and shell",
        d > 5.0 && d < 10.0);
  }

  /**
   * Crescent of two arcs (upper boundary peaks at y=8, lower at y=5). The
   * widest interior interval is the high band around y=6.5; the point must land
   * inside the curved crescent.
   */
  public void testCrescentInteriorPointInside() {
    CurvePolygon cp = readCP(
        "CURVEPOLYGON (COMPOUNDCURVE ("
        + "CIRCULARSTRING (-10 0, 0 8, 10 0), "
        + "CIRCULARSTRING (10 0, 0 5, -10 0)))");
    Point ip = cp.getInteriorPoint();
    assertTrue("interior point " + ip + " must be inside the crescent",
        cp.toLinear(0.01).contains(ip));
    // Sanity: it is in the upper band between the two arcs.
    assertTrue("y should be in the crescent's high band", ip.getCoordinate().y > 5.0);
  }

  /** Straight (non-curved) ring uses the parent behaviour and stays inside. */
  public void testStraightRingInteriorPointInside() {
    CurvePolygon cp = readCP("CURVEPOLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    assertTrue(cp.toLinear(0.02).contains(cp.getInteriorPoint()));
  }
}
