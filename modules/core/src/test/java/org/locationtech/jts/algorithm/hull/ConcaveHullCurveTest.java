/*
 * Copyright (c) 2021 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

package org.locationtech.jts.algorithm.hull;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.WKTReader;

import junit.framework.TestCase;

/**
 * Tests for ConcaveHull on curve geometries.
 * Claim 1195-h-cc: Concave hull must handle arc-aware input and preserve curve coverage.
 *
 * Current implementation extracts coordinates via geometry.getCoordinates(),
 * which for CircularString returns only control points. The concave hull may
 * underestimate the true boundary if arc extrema are outside the control polyline.
 *
 * For a proper arc-aware concave hull, either:
 * 1. The hull must be computed with arc extrema included in the input point set, OR
 * 2. The API must accept curves natively and compute extrema internally
 *
 * @version 1.0
 */
public class ConcaveHullCurveTest extends TestCase {

  private PrecisionModel precisionModel = new PrecisionModel(1000);
  private GeometryFactory geometryFactory = new GeometryFactory(precisionModel, 0);
  private WKTReader reader = new WKTReader(geometryFactory);

  public ConcaveHullCurveTest(String name) {
    super(name);
  }

  /**
   * Test concave hull of a single circular arc.
   *
   * Geometry: CIRCULARSTRING(0 0, 1 1, 2 0)
   *   - Control points: (0,0), (1,1), (2,0)
   *   - Arc bulges upward (true peak above y=1)
   *
   * Expected behavior:
   *   ConcaveHull.concaveHullByLengthRatio(arc, 0.5) should accept the curve
   *   and return a hull that covers the arc extent (not just the control polyline).
   *
   * Current (Red signal): Either WKT parsing fails (prerequisite 1195-d)
   *   or concave hull accepts it but gives a hull that doesn't cover the arc bulge.
   *
   * Red signal (claim 1195-h-cc): ConcaveHull doesn't handle curves properly.
   */
  public void testConcaveHullOfCircularStringArc() throws Exception {
    try {
      // Arc from (0,0) to (2,0), bulging up through (1,1)
      String arcWKT = "CIRCULARSTRING(0 0, 1 1, 2 0)";
      Geometry arc = reader.read(arcWKT);

      // Compute concave hull with moderate lengthRatio
      Geometry hull = ConcaveHull.concaveHullByLengthRatio(arc, 0.5);

      assertNotNull("Concave hull should be computed", hull);

      // The hull should cover all points on the arc
      // For a point on the arc, it should be either on the hull boundary or inside
      Geometry testPt = geometryFactory.createPoint(new org.locationtech.jts.geom.Coordinate(1, 1.1)); // Point near arc peak
      assertTrue("Arc interior points should be covered by hull",
          hull.contains(testPt) || hull.touches(testPt));

      fail("Red signal (1195-h-cc): ConcaveHull on CircularString not properly implemented");
    } catch (Exception e) {
      // Red signal: Either WKT parsing fails (prerequisite 1195-d)
      // or concave hull computation fails
      fail("Red signal (1195-h-cc): ConcaveHull fails on CircularString - " + e.getMessage());
    }
  }

  /**
   * Test concave hull of multiple circular arcs forming a concave boundary.
   *
   * Geometry: MULTIGEOMETRY with two arcs:
   *   Arc 1: CIRCULARSTRING(0 0, 0.5 0.5, 1 0)
   *   Arc 2: CIRCULARSTRING(1 0, 1.5 -0.5, 2 0)
   *   Together: form a wave-like concave chain
   *
   * Expected: Concave hull should follow or respect the curve topology.
   *
   * Red signal: Hull computed from linearized endpoints only, missing arc shape.
   */
  public void testConcaveHullOfMultipleArcs() throws Exception {
    try {
      // Two arcs forming a concave pattern
      String multiArcWKT = "GEOMETRYCOLLECTION(CIRCULARSTRING(0 0, 0.5 0.5, 1 0), " +
                          "CIRCULARSTRING(1 0, 1.5 -0.5, 2 0))";
      Geometry multiArc = reader.read(multiArcWKT);

      Geometry hull = ConcaveHull.concaveHullByLengthRatio(multiArc, 0.3);

      assertNotNull("Concave hull should be computed", hull);

      // The hull should enclose both arc sets
      double maxDistance = 0;
      for (org.locationtech.jts.geom.Coordinate coord : multiArc.getCoordinates()) {
        double dist = hull.distance(geometryFactory.createPoint(coord));
        maxDistance = Math.max(maxDistance, dist);
      }

      // If hull doesn't cover arcs, max distance will be > 0 (outside or on boundary is ok)
      // But if hull is computed only from control points, it may be too tight

      fail("Red signal (1195-h-cc): ConcaveHull on multi-arc geometry not tested");
    } catch (Exception e) {
      fail("Red signal (1195-h-cc): ConcaveHull fails on multi-arc - " + e.getMessage());
    }
  }

  /**
   * Test concave hull of a CompoundCurve with line and arc components.
   *
   * Geometry: COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1.5 0.5, 2 0))
   *   - Line segment from (0,0) to (1,0)
   *   - Circular arc from (1,0) to (2,0) via (1.5, 0.5)
   *
   * Expected: Concave hull handles mixed geometry types with arc awareness.
   *
   * Red signal: WKT parsing fails or hull ignores arc component.
   */
  public void testConcaveHullOfCompoundCurve() throws Exception {
    try {
      String compoundWKT = "COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1.5 0.5, 2 0))";
      Geometry compound = reader.read(compoundWKT);

      Geometry hull = ConcaveHull.concaveHullByLengthRatio(compound, 0.4);

      assertNotNull("Concave hull should be computed", hull);

      fail("Red signal (1195-h-cc): ConcaveHull on CompoundCurve not properly handled");
    } catch (Exception e) {
      fail("Red signal (1195-h-cc): ConcaveHull fails on CompoundCurve - " + e.getMessage());
    }
  }

  /**
   * Test edge length ratio parameter with curved geometry.
   *
   * For curves, the lengthRatio parameter determines concaveness.
   * At ratio=1, result should be convex hull.
   * At ratio=0, result should be maximum concaveness.
   *
   * Red signal: Parameter doesn't work correctly with curves or WKT parsing fails.
   */
  public void testConcaveHullWithLengthRatioOnArc() throws Exception {
    try {
      String arcWKT = "CIRCULARSTRING(0 0, 2 2, 4 0)";
      Geometry arc = reader.read(arcWKT);

      // Test with different ratios
      Geometry hull_tight = ConcaveHull.concaveHullByLengthRatio(arc, 0.1); // Concave
      Geometry hull_loose = ConcaveHull.concaveHullByLengthRatio(arc, 0.9); // Convex

      assertNotNull("Tight concave hull should be computed", hull_tight);
      assertNotNull("Loose concave hull should be computed", hull_loose);

      // Loose hull should be larger or equal to tight hull
      assertTrue("Loose hull area should be >= tight hull area",
          hull_loose.getArea() >= hull_tight.getArea() - 0.01);

      fail("Red signal (1195-h-cc): LengthRatio parameter with curves not verified");
    } catch (Exception e) {
      fail("Red signal (1195-h-cc): ConcaveHull with lengthRatio fails on curves - " +
          e.getMessage());
    }
  }

  /**
   * Test that arc extrema are included in concave hull computation.
   *
   * For a semicircular arc, the rightmost point (if it's an extreme) must be included
   * in the hull or the hull must be computed with extrema awareness.
   *
   * Red signal: No mechanism to ensure arc extrema are considered.
   */
  public void testConcaveHullIncludesArcExtrema() throws Exception {
    try {
      // Semicircular arc from (0,0) to (4,0), peak at (2,2)
      String semicircleWKT = "CIRCULARSTRING(0 0, 2 2, 4 0)";
      Geometry semicircle = reader.read(semicircleWKT);

      Geometry hull = ConcaveHull.concaveHullByLengthRatio(semicircle, 0.5);

      assertNotNull("Concave hull should be computed", hull);

      // The peak at (2,2) should be inside or on the hull boundary
      Geometry peakPt = geometryFactory.createPoint(new org.locationtech.jts.geom.Coordinate(2.0, 2.0));
      boolean peakCovered = hull.contains(peakPt) || hull.touches(peakPt);

      assertTrue("Arc peak should be inside or on hull boundary (arc extrema must be included)",
          peakCovered);

      fail("Red signal (1195-h-cc): Concave hull on arc doesn't guarantee extrema coverage");
    } catch (Exception e) {
      fail("Red signal (1195-h-cc): Arc extrema inclusion test failed - " + e.getMessage());
    }
  }
}
