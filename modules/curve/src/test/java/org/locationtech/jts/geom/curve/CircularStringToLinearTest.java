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
import java.util.Collections;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Tests for {@link CircularString#toLinear(double)} and the
 * {@code mustInclude} overload — the Phase-3 sagitta-based densification.
 */
public class CircularStringToLinearTest extends GeometryTestCase {

  private static final double EPS = 1e-9;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CircularStringToLinearTest.class); }
  public CircularStringToLinearTest(String name) { super(name); }

  // --- Sagitta / segment-count behaviour ----------------------------------

  /** A 90° arc on a radius-10 circle, tolerance 1.0 (10% of radius).
   *  Sagitta formula: θ_max = 2·acos(1 − 1/10) ≈ 0.902 rad. Sweep π/2 ≈ 1.571.
   *  segments = ceil(1.571 / 0.902) = 2. So 3 vertices total: start, mid-chord, end. */
  public void test90DegreeArcChordCount() throws Exception {
    Geometry g = new CurveWKTReader().read("CIRCULARSTRING (10 0, 7.071068 7.071068, 0 10)");
    LineString line = (LineString) ((CircularString) g).toLinear(1.0);
    assertEquals(3, line.getNumPoints());
  }

  /** Tighter tolerance: same 90° arc, tolerance 0.05 should densify more. */
  public void testTighterToleranceMoreSegments() throws Exception {
    Geometry g = new CurveWKTReader().read("CIRCULARSTRING (10 0, 7.071068 7.071068, 0 10)");
    LineString line = (LineString) ((CircularString) g).toLinear(0.05);
    // θ_max = 2·acos(1 − 0.05/10) = 2·acos(0.995) ≈ 0.2003 rad.
    // segments = ceil(π/2 / 0.2003) = ceil(7.84) = 8. Vertices = 9.
    assertEquals(9, line.getNumPoints());
  }

  /** Every emitted vertex must lie within tolerance of the true circle. */
  public void testAllVerticesWithinTolerance() throws Exception {
    Geometry g = new CurveWKTReader().read("CIRCULARSTRING (10 0, 7.071068 7.071068, 0 10)");
    double tol = 0.1;
    LineString line = (LineString) ((CircularString) g).toLinear(tol);
    Coordinate[] coords = line.getCoordinates();
    double maxDev = 0.0;
    for (Coordinate c : coords) {
      double dev = Math.abs(Math.hypot(c.x, c.y) - 10.0);
      if (dev > maxDev) maxDev = dev;
    }
    // Vertices are exactly on the circle (center at origin, radius 10),
    // so this is effectively a sanity check that the chord endpoints
    // we emit haven't drifted.
    assertTrue("max vertex deviation from circle: " + maxDev, maxDev < 1e-6);
  }

  /** Default tolerance (0.0) selects radius/100. For r=10 -> 0.1. */
  public void testDefaultToleranceUsesOnePercentOfRadius() throws Exception {
    Geometry g = new CurveWKTReader().read("CIRCULARSTRING (10 0, 7.071068 7.071068, 0 10)");
    LineString withDefault = (LineString) ((CircularString) g).toLinear(0.0);
    LineString withExplicit = (LineString) ((CircularString) g).toLinear(0.1);
    assertEquals(withExplicit.getNumPoints(), withDefault.getNumPoints());
  }

  /** Negative tolerance is reserved -> IllegalArgumentException. */
  public void testNegativeToleranceThrows() throws Exception {
    Geometry g = new CurveWKTReader().read("CIRCULARSTRING (10 0, 7.071068 7.071068, 0 10)");
    try {
      ((CircularString) g).toLinear(-1.0);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }

  /** Multi-arc CIRCULARSTRING: each (start, mid, end) triple is densified
   *  independently and the polylines are stitched together (no duplicate
   *  shared vertex between consecutive arcs). */
  public void testMultiArcConcatenation() throws Exception {
    // Closed full circle: 4 arcs share vertices. 5 control points, 2 arcs.
    Geometry g = new CurveWKTReader().read("CIRCULARSTRING (10 0, 0 10, -10 0, 0 -10, 10 0)");
    LineString line = (LineString) ((CircularString) g).toLinear(0.5);
    Coordinate[] coords = line.getCoordinates();
    // First and last points are the same (closed circle).
    assertEquals(coords[0].x, coords[coords.length - 1].x, EPS);
    assertEquals(coords[0].y, coords[coords.length - 1].y, EPS);
    // No two consecutive coordinates are duplicates (would indicate a
    // stitching bug).
    for (int i = 1; i < coords.length; i++) {
      assertFalse("duplicate consecutive point at " + i,
          coords[i].equals2D(coords[i - 1]));
    }
  }

  // --- Degenerate input ----------------------------------------------------

  public void testColinearTripleFallsThroughToChord() throws Exception {
    Geometry g = new CurveWKTReader().read("CIRCULARSTRING (0 0, 5 0, 10 0)");
    LineString line = (LineString) ((CircularString) g).toLinear(0.1);
    // Colinear input: densifier returns just [start, end].
    Coordinate[] coords = line.getCoordinates();
    assertEquals(2, coords.length);
    assertEquals(0.0, coords[0].x, EPS);
    assertEquals(10.0, coords[1].x, EPS);
  }

  public void testEmptyReturnsEmptyLineString() throws Exception {
    Geometry g = new CurveWKTReader().read("CIRCULARSTRING EMPTY");
    Geometry linear = ((CircularString) g).toLinear(0.1);
    assertTrue(linear.isEmpty());
    assertEquals("LineString", linear.getGeometryType());
  }

  // --- mustInclude semantics -----------------------------------------------

  /** A coordinate that lies exactly on the arc must appear in the
   *  densified polyline. */
  public void testMustIncludeOnArcInserted() throws Exception {
    // Quarter circle on radius 10 from (10,0) through (~7.07, ~7.07) to (0,10).
    Geometry g = new CurveWKTReader().read("CIRCULARSTRING (10 0, 7.071068 7.071068, 0 10)");
    // 30° point on the arc: (10·cos(π/6), 10·sin(π/6)) = (8.6603, 5.0)
    Coordinate onArc = new Coordinate(10.0 * Math.cos(Math.PI / 6.0),
                                       10.0 * Math.sin(Math.PI / 6.0));
    LineString line = (LineString) ((CircularString) g)
        .toLinear(0.5, Collections.singletonList(onArc));

    boolean found = false;
    for (Coordinate c : line.getCoordinates()) {
      if (Math.hypot(c.x - onArc.x, c.y - onArc.y) < 1e-6) {
        found = true; break;
      }
    }
    assertTrue("must-include point should appear in output", found);
  }

  /** A coordinate well off the arc (well beyond tolerance) is silently
   *  dropped — the polyline is the same as without mustInclude. */
  public void testMustIncludeOffArcDropped() throws Exception {
    Geometry g = new CurveWKTReader().read("CIRCULARSTRING (10 0, 7.071068 7.071068, 0 10)");
    LineString plain = (LineString) ((CircularString) g).toLinear(0.5);
    LineString withFar = (LineString) ((CircularString) g)
        .toLinear(0.5, Collections.singletonList(new Coordinate(100, 100)));
    assertEquals(plain.getNumPoints(), withFar.getNumPoints());
  }

  /**
   * A coordinate that is OFF the arc but within tolerance is inserted AS
   * SUPPLIED, not as its projection onto the circle.
   * <p>
   * <b>Superseded contract (DENS-ANCHOR).</b> This test originally asserted the
   * opposite: that the anchor's projection appears. That semantics turned exact
   * control points into cos/sin noise -- visual QA found {@code (0, 1)} coming
   * back as {@code (6.1e-17, 1)} -- because projecting a point that is already
   * on the arc reconstructs it through atan2 and cos/sin. mustInclude now means
   * what it says: the caller's exact coordinate appears in the output. The
   * radial filter still rejects anchors further than the tolerance from the
   * arc, so the polyline's deviation bound is unchanged.
   */
  public void testMustIncludeNearArcOriginalInserted() throws Exception {
    Geometry g = new CurveWKTReader().read("CIRCULARSTRING (10 0, 7.071068 7.071068, 0 10)");
    // (8.6, 5.0) is within ~0.06 of a radius-10 arc (true radius at angle 30°
    // would be 10; this is at distance hypot(8.6, 5) ≈ 9.94).
    Coordinate nearArc = new Coordinate(8.6, 5.0);
    double radial = Math.hypot(nearArc.x, nearArc.y);
    double radialError = Math.abs(radial - 10.0);
    assertTrue("test fixture: nearArc must be within 0.5 of radius 10, got "
        + radialError, radialError < 0.5);

    LineString line = (LineString) ((CircularString) g)
        .toLinear(0.5, Collections.singletonList(nearArc));
    boolean foundOriginal = false;
    for (Coordinate c : line.getCoordinates()) {
      if (c.equals2D(nearArc)) {
        foundOriginal = true; break;
      }
    }
    assertTrue("the caller's exact coordinate must appear in the output",
        foundOriginal);
  }

  /** Multiple must-include points inserted in correct sweep order. */
  public void testMultipleMustIncludeOrdered() throws Exception {
    Geometry g = new CurveWKTReader().read("CIRCULARSTRING (10 0, 7.071068 7.071068, 0 10)");
    Coordinate at30 = new Coordinate(10.0 * Math.cos(Math.PI / 6.0),
                                      10.0 * Math.sin(Math.PI / 6.0));
    Coordinate at60 = new Coordinate(10.0 * Math.cos(Math.PI / 3.0),
                                      10.0 * Math.sin(Math.PI / 3.0));
    // Pass them in REVERSE order; densifier must sort by sweep-angle.
    List<Coordinate> mi = Arrays.asList(at60, at30);
    LineString line = (LineString) ((CircularString) g).toLinear(0.5, mi);

    int idx30 = -1, idx60 = -1;
    Coordinate[] coords = line.getCoordinates();
    for (int i = 0; i < coords.length; i++) {
      if (Math.hypot(coords[i].x - at30.x, coords[i].y - at30.y) < 1e-6) idx30 = i;
      if (Math.hypot(coords[i].x - at60.x, coords[i].y - at60.y) < 1e-6) idx60 = i;
    }
    assertTrue("30° point inserted", idx30 >= 0);
    assertTrue("60° point inserted", idx60 >= 0);
    assertTrue("30° point should come before 60° in arc order",
        idx30 < idx60);
  }
}
