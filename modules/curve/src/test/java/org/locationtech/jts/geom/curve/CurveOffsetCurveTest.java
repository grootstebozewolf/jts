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
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * OFF (#1195): {@link CurveOffsetCurve#getCurve} on a single-arc
 * {@link CircularString} returns an analytically-offset {@code CircularString}
 * (R±d, same centre, same sweep) instead of a densified polyline.
 */
public class CurveOffsetCurveTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(CurveOffsetCurveTest.class); }
  public CurveOffsetCurveTest(String name) { super(name); }

  private static final double EPS = 1e-10;

  // Upper semicircle, centre (0,0), R=5: (5,0) arc-through (0,5) to (-5,0).
  private static final String SEMI_R5 = "CIRCULARSTRING (5 0, 0 5, -5 0)";

  /** Outward offset by 1 → CircularString of R=6, same centre. */
  public void testOutwardOffsetReturnsCircularString() throws Exception {
    Geometry cs = new CurveWKTReader().read(SEMI_R5);
    Geometry result = CurveOffsetCurve.getCurve(cs, 1.0);
    assertTrue("result should be CircularString", result instanceof CircularString);
    assertRadius((CircularString) result, 0, 0, 6);
  }

  /** Inward offset by -1 → CircularString of R=4. */
  public void testInwardOffsetReturnsCircularString() throws Exception {
    Geometry cs = new CurveWKTReader().read(SEMI_R5);
    Geometry result = CurveOffsetCurve.getCurve(cs, -1.0);
    assertTrue("result should be CircularString", result instanceof CircularString);
    assertRadius((CircularString) result, 0, 0, 4);
  }

  /** Offset that exactly reaches centre (d = -R) → empty. */
  public void testCollapseAtCentreReturnsEmpty() throws Exception {
    Geometry cs = new CurveWKTReader().read(SEMI_R5);
    Geometry result = CurveOffsetCurve.getCurve(cs, -5.0);
    assertTrue("collapse should return empty", result.isEmpty());
  }

  /** Offset beyond centre (d < -R) → empty. */
  public void testCollapseBeyondCentreReturnsEmpty() throws Exception {
    Geometry cs = new CurveWKTReader().read(SEMI_R5);
    Geometry result = CurveOffsetCurve.getCurve(cs, -7.0);
    assertTrue("over-collapse should return empty", result.isEmpty());
  }

  /** Multi-arc CircularString delegates to core (returns LineString/MultiLineString). */
  public void testMultiArcDelegatesToCore() throws Exception {
    Geometry cs = new CurveWKTReader().read("CIRCULARSTRING (1 0, 0 1, -1 0, 0 -1, 1 0)");
    Geometry result = CurveOffsetCurve.getCurve(cs, 0.5);
    assertFalse("multi-arc delegates → not a CircularString", result instanceof CircularString);
  }

  /** Plain LineString delegates to core (not a CircularString). */
  public void testPlainLineStringDelegatesToCore() throws Exception {
    Geometry ls = new CurveWKTReader().read("LINESTRING (0 0, 1 0, 2 1)");
    Geometry result = CurveOffsetCurve.getCurve(ls, 0.5);
    assertFalse("LineString delegates → not a CircularString", result instanceof CircularString);
    assertFalse("result should not be empty", result.isEmpty());
  }

  /** All three control points of the offset arc sit on the expected circle. */
  public void testControlPointsOnNewCircle() throws Exception {
    Geometry cs = new CurveWKTReader().read(SEMI_R5);
    Geometry result = CurveOffsetCurve.getCurve(cs, 2.0);
    assertTrue(result instanceof CircularString);
    Coordinate[] coords = result.getCoordinates();
    assertEquals(3, coords.length);
    for (Coordinate c : coords) {
      double dist = Math.hypot(c.x, c.y);
      assertEquals("control point should be on R=7 circle", 7.0, dist, EPS);
    }
  }

  /** Outward-offset arc endpoints match the radially scaled originals. */
  public void testEndpointPositions() throws Exception {
    Geometry cs = new CurveWKTReader().read(SEMI_R5);
    Geometry result = CurveOffsetCurve.getCurve(cs, 1.0);
    Coordinate[] c = result.getCoordinates();
    assertEquals( 6.0, c[0].x, EPS); assertEquals(0.0, c[0].y, EPS);
    assertEquals( 0.0, c[1].x, EPS); assertEquals(6.0, c[1].y, EPS);
    assertEquals(-6.0, c[2].x, EPS); assertEquals(0.0, c[2].y, EPS);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private static void assertRadius(CircularString cs, double cx, double cy, double r) {
    Coordinate[] c = cs.getCoordinates();
    assertEquals(3, c.length);
    for (Coordinate p : c) {
      double dist = Math.hypot(p.x - cx, p.y - cy);
      assertEquals("control point at wrong radius", r, dist, EPS);
    }
  }
}
