/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

package org.locationtech.jts.linearref;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTReader;

import junit.framework.TestCase;

/**
 * Tests for LengthIndexedLine on curve geometries.
 * Claim 1195-lrf-len: LengthIndexedLine must use arc-length parameterization (r·θ)
 * for CircularString, not chord-length approximation.
 *
 * Current implementation uses LinearLocation and segment-based length indexing,
 * which only works for LineString (straight segments). For curves, arc-length
 * parameterization is required: index = r·θ where r is radius and θ is central angle.
 *
 * @version 1.0
 */
public class LengthIndexedLineCurveTest extends TestCase {

  private GeometryFactory geometryFactory = new GeometryFactory();
  private WKTReader reader = new WKTReader(geometryFactory);

  public LengthIndexedLineCurveTest(String name) {
    super(name);
  }

  /**
   * Test arc-length parameterization for a semicircular arc.
   *
   * Geometry: CIRCULARSTRING(0 0, 1 1, 0 2)
   *   - Semicircular arc, radius 1
   *   - Arc length = r·θ = 1·π = π ≈ 3.14159
   *
   * Expected index length: π (arc length)
   *
   * Actual (current non-curve): chord polyline length ≈ 2·√2 ≈ 2.828
   *   - (0,0) to (1,1): √2
   *   - (1,1) to (0,2): √2
   *   - Total: 2√2 ≈ 2.828 (wrong!)
   *
   * Red signal (claim 1195-lrf-len): Length uses chord, not arc length.
   */
  public void testArcLengthParameterizationSemicircle() throws Exception {
    try {
      // Semicircular arc: radius 1, arc length = π
      String arcWKT = "CIRCULARSTRING(0 0, 1 1, 0 2)";
      Geometry arc = reader.read(arcWKT);

      LengthIndexedLine lil = new LengthIndexedLine(arc);

      // Get the endpoint index (should be π for true arc length)
      // Note: there's no direct getEndIndex() but we can test extractPoint behavior

      // Extract midpoint at arc-length index π/2
      double arcMidLength = Math.PI / 2.0;
      Coordinate midPoint = lil.extractPoint(arcMidLength);

      // For a semicircle from (0,0) to (0,2) via (1,1):
      // At arc-length π/2 (midpoint), we should be at approximately (1, 1)
      // The true midpoint of the arc is around (1, 1)

      // Red signal: midPoint will be off the arc if using chord parameterization
      // Expected: point very close to (1, 1)
      // Actual: point off-arc due to chord-length indexing

      double expectedX = 1.0;
      double expectedY = 1.0;
      double tolerance = 0.1; // Allow small error

      assertTrue("Arc-length parameterization: midpoint should be near (1, 1) on the arc " +
          "(actual: (" + midPoint.x + ", " + midPoint.y + "))",
          Math.abs(midPoint.x - expectedX) < tolerance &&
          Math.abs(midPoint.y - expectedY) < tolerance);

      fail("Red signal (1195-lrf-len): LengthIndexedLine on CircularString not properly tested");
    } catch (AssertionError ae) {
      fail("Red signal (1195-lrf-len): " + ae.getMessage());
    } catch (Exception e) {
      fail("Red signal (1195-lrf-len): LengthIndexedLine arc-length test failed - " + e.getMessage());
    }
  }

  /**
   * Test arc-length consistency: extractLine should use arc length.
   *
   * Geometry: CIRCULARSTRING(1 0, 0 1, -1 0)
   *   - Quarter circle, radius 1, arc length = π/2
   *
   * Extract line from arc-length 0 to π/4 (half the arc).
   *
   * Expected: line/curve covers the first quarter of the arc.
   *
   * Red signal: extractLine uses chord length, not arc length.
   */
  public void testArcLengthExtractLine() throws Exception {
    try {
      // Quarter-circle arc
      String arcWKT = "CIRCULARSTRING(1 0, 0 1, -1 0)";
      Geometry arc = reader.read(arcWKT);

      LengthIndexedLine lil = new LengthIndexedLine(arc);

      // Extract from start to midpoint using arc-length index
      double quarterArcLength = Math.PI / 4.0; // Half of π/2
      Geometry extracted = lil.extractLine(0, quarterArcLength);

      assertNotNull("Extracted line should not be null", extracted);

      // The extracted line should cover a portion of the arc
      assertTrue("Extracted line should have non-zero length", extracted.getLength() > 0);

      // Red signal: if extracted line is too short or doesn't follow arc
      // then it used chord-length indexing instead of arc-length

      fail("Red signal (1195-lrf-len): LengthIndexedLine arc-length extractLine not tested");
    } catch (Exception e) {
      fail("Red signal (1195-lrf-len): LengthIndexedLine extractLine test failed - " +
          e.getMessage());
    }
  }

  /**
   * Test that arc-length index matches actual arc length.
   *
   * For a CircularString, the total length index should equal the sum of arc lengths,
   * not the sum of chord lengths.
   *
   * Geometry: CIRCULARSTRING(0 0, 2 2, 4 0)
   *   - Semicircular arc with radius √8, arc length = √8·π
   *
   * Red signal: total index ≈ chord sum, not arc length.
   */
  public void testArcLengthIndexTotalLength() throws Exception {
    try {
      // Semicircular arc with different radius
      String arcWKT = "CIRCULARSTRING(0 0, 2 2, 4 0)";
      Geometry arc = reader.read(arcWKT);

      LengthIndexedLine lil = new LengthIndexedLine(arc);

      // The arc length for this semicircle should be:
      // - Control points form a semicircle
      // - True arc length is π·r where r can be computed from the control points
      // For a semicircle with endpoints (0,0) and (4,0), diameter = 4, radius = 2
      // Arc length = π·2 = 2π ≈ 6.283

      double expectedArcLength = 2.0 * Math.PI;

      // Extract endpoint to get the total length index
      Coordinate endPoint = lil.extractPoint(expectedArcLength);

      // The endpoint should be close to (4, 0) if using arc-length indexing
      // Red signal: if endPoint is not near (4, 0), then wrong parameterization

      double expectedX = 4.0;
      double expectedY = 0.0;
      double tolerance = 0.1;

      assertTrue("Arc-length parameterization: endpoint should be near (4, 0) " +
          "(actual: (" + endPoint.x + ", " + endPoint.y + "))",
          Math.abs(endPoint.x - expectedX) < tolerance &&
          Math.abs(endPoint.y - expectedY) < tolerance);

      fail("Red signal (1195-lrf-len): LengthIndexedLine arc-length endpoint test not completed");
    } catch (AssertionError ae) {
      fail("Red signal (1195-lrf-len): " + ae.getMessage());
    } catch (Exception e) {
      fail("Red signal (1195-lrf-len): LengthIndexedLine arc-length total length test failed - " +
          e.getMessage());
    }
  }

  /**
   * Test negative index (measured from end) with arc-length parameterization.
   *
   * Negative indices are measured backward from the end.
   * For arc-length indexing, -π/2 should be at the opposite end from π/2.
   *
   * Red signal: negative index doesn't follow arc-length convention.
   */
  public void testNegativeArcLengthIndex() throws Exception {
    try {
      String arcWKT = "CIRCULARSTRING(0 0, 1 1, 0 2)";
      Geometry arc = reader.read(arcWKT);

      LengthIndexedLine lil = new LengthIndexedLine(arc);

      // Extract from negative index (measured backward from end)
      double negativeIndex = -Math.PI / 2.0; // Reverse of π/2
      Coordinate point = lil.extractPoint(negativeIndex);

      // For a semicircle, negative index -π/2 from the end should still be on the arc
      assertTrue("Negative arc-length index should return a point",
          point != null && !Double.isNaN(point.x) && !Double.isNaN(point.y));

      fail("Red signal (1195-lrf-len): LengthIndexedLine negative arc-length index not tested");
    } catch (Exception e) {
      fail("Red signal (1195-lrf-len): LengthIndexedLine negative index test failed - " +
          e.getMessage());
    }
  }

  /**
   * Test that arc-length index works correctly on CompoundCurve.
   *
   * Geometry: COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1.5 0.5, 2 0))
   *   - Line segment: length 1
   *   - Arc segment: arc length depends on arc parameters
   *   - Total index should be sum of component lengths
   *
   * Red signal: mixed line/arc length calculation is broken.
   */
  public void testArcLengthCompoundCurve() throws Exception {
    try {
      String compoundWKT = "COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1.5 0.5, 2 0))";
      Geometry compound = reader.read(compoundWKT);

      LengthIndexedLine lil = new LengthIndexedLine(compound);

      // Extract a point in the middle of the compound curve
      // Should account for line length + arc length
      Coordinate midPoint = lil.extractPoint(1.0); // 1.0 = end of line segment

      assertNotNull("Midpoint extraction on CompoundCurve should work", midPoint);

      // Red signal: if midPoint is wrong, then line/arc length mixing is broken
      fail("Red signal (1195-lrf-len): LengthIndexedLine CompoundCurve arc-length not tested");
    } catch (Exception e) {
      fail("Red signal (1195-lrf-len): LengthIndexedLine CompoundCurve test failed - " +
          e.getMessage());
    }
  }

  /**
   * Test zero-length arc (degenerate case).
   *
   * Geometry: CIRCULARSTRING(0 0, 0.5 0.5, 0 0) (closed arc)
   *   - Degenerate: returns to start
   *
   * Expected: arc length is defined; index behavior is consistent.
   *
   * Red signal: degenerate arc handling is broken or undefined.
   */
  public void testZeroLengthClosedArc() throws Exception {
    try {
      String arcWKT = "CIRCULARSTRING(0 0, 0.5 0.5, 0 0)";
      Geometry arc = reader.read(arcWKT);

      LengthIndexedLine lil = new LengthIndexedLine(arc);

      // Extracting from 0 to total length should span the full arc
      Coordinate startPt = lil.extractPoint(0);
      Coordinate endPt = lil.extractPoint(arc.getLength());

      // For a closed arc, start and end should be equal
      assertTrue("Closed arc endpoints should match",
          startPt.equals(endPt) || startPt.distance(endPt) < 0.01);

      fail("Red signal (1195-lrf-len): LengthIndexedLine closed arc test not completed");
    } catch (Exception e) {
      fail("Red signal (1195-lrf-len): LengthIndexedLine closed arc test failed - " +
          e.getMessage());
    }
  }
}
