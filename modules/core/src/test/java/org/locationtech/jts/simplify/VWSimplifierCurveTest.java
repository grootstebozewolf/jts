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

package org.locationtech.jts.simplify;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTReader;

import junit.framework.TestCase;

/**
 * Tests for VWSimplifier on curve geometries.
 * Claim 1195-s-vw: VWSimplifier must preserve CircularString identity under appropriate tolerance.
 *
 * Current implementation uses GeometryTransformer and coordinate extraction,
 * which linearizes curves (extracts control points) and returns linear simplifications.
 * The result type becomes LineString instead of preserving CircularString.
 *
 * For arc-aware simplification, either:
 * 1. CircularString remains CircularString when arc control points satisfy VW tolerance, OR
 * 2. The API explicitly documents that curves are densified to lines before simplification
 *
 * @version 1.0
 */
public class VWSimplifierCurveTest extends TestCase {

  private GeometryFactory geometryFactory = new GeometryFactory();
  private WKTReader reader = new WKTReader(geometryFactory);

  public VWSimplifierCurveTest(String name) {
    super(name);
  }

  /**
   * Test VW simplification of a single circular arc.
   *
   * Geometry: CIRCULARSTRING(0 0, 1 1, 2 0)
   *   - Control points: (0,0), (1,1), (2,0)
   *   - Arc bulges upward (true peak above y=1)
   *
   * Simplification tolerance: 0.01 (small, below meaningful VW area for arc control points)
   *
   * Expected behavior (arc-aware):
   *   - Result should be CIRCULARSTRING(0 0, 1 1, 2 0) (unchanged, same type and control structure)
   *   - Arc identity is preserved
   *
   * Current (Red signal): Either WKT parsing fails (prerequisite 1195-d)
   *   or result is LINESTRING(0 0, 1 1, 2 0) (type erasure)
   *
   * Red signal (claim 1195-s-vw): VWSimplifier doesn't preserve arc type identity.
   */
  public void testVWSimplifyCircularStringIdentity() throws Exception {
    try {
      // Single arc
      String arcWKT = "CIRCULARSTRING(0 0, 1 1, 2 0)";
      Geometry arc = reader.read(arcWKT);

      // Simplify with small tolerance (below arc significance)
      Geometry simplified = VWSimplifier.simplify(arc, 0.01);

      assertNotNull("Simplified geometry should not be null", simplified);

      // Check type preservation
      String resultType = simplified.getGeometryType();

      // Arc-aware simplifier should preserve CircularString type
      // Red signal: resultType is "LineString" (type erasure)
      if ("LineString".equals(resultType)) {
        fail("Red signal (1195-s-vw): VWSimplifier erases CircularString to LineString " +
            "(input: CIRCULARSTRING, output: " + resultType + ")");
      }

      // If type is somehow preserved but coordinates wrong, catch that too
      fail("Red signal (1195-s-vw): VWSimplifier on CircularString not properly tested");
    } catch (Exception e) {
      // Red signal: Either WKT parsing fails (prerequisite 1195-d)
      // or simplification fails
      fail("Red signal (1195-s-vw): VWSimplifier fails on CircularString - " + e.getMessage());
    }
  }

  /**
   * Test VW simplification of a multi-segment circular arc.
   *
   * Geometry: CIRCULARSTRING(0 0, 2 2, 4 0, 6 2, 8 0)
   *   - Five control points forming a wave pattern of arcs
   *   - Multiple arc segments chained together
   *
   * Expected (arc-aware): Remain CIRCULARSTRING with arc control points preserved.
   *
   * Red signal: Type erasure or missing arc handling.
   */
  public void testVWSimplifyMultiArcCircularString() throws Exception {
    try {
      // Multi-arc
      String arcWKT = "CIRCULARSTRING(0 0, 2 2, 4 0, 6 2, 8 0)";
      Geometry arc = reader.read(arcWKT);

      Geometry simplified = VWSimplifier.simplify(arc, 0.1);

      assertNotNull("Simplified geometry should not be null", simplified);

      // Check type
      String resultType = simplified.getGeometryType();
      assertEquals("Simplified multi-arc should remain CircularString",
          "CircularString", resultType);

      fail("Red signal (1195-s-vw): VWSimplifier on multi-arc CircularString not verified");
    } catch (AssertionError ae) {
      // Type mismatch: expected CircularString, got something else
      fail("Red signal (1195-s-vw): " + ae.getMessage());
    } catch (Exception e) {
      fail("Red signal (1195-s-vw): VWSimplifier fails on multi-arc - " + e.getMessage());
    }
  }

  /**
   * Test VW simplification with tolerance that justifies removing a control point.
   *
   * Geometry: CIRCULARSTRING(0 0, 0.05 0.05, 1 0)
   *   - Three control points, middle one nearly collinear with endpoints
   *   - VW area criterion might allow removing middle point under large tolerance
   *
   * Expected: If middle point is removed, result is a 2-point arc (degenerate) or Line.
   *           Type change is acceptable if VW tolerance justifies geometry reduction.
   *           But type should be documented / explicit, not silent erasure.
   *
   * Red signal: Silent type erasure without explicit arc-aware simplification contract.
   */
  public void testVWSimplifyArcWithToleranceAllowsReduction() throws Exception {
    try {
      // Nearly-collinear arc (middle point close to chord)
      String arcWKT = "CIRCULARSTRING(0 0, 0.05 0.05, 1 0)";
      Geometry arc = reader.read(arcWKT);

      // Large tolerance: allows VW to consider removing middle point
      Geometry simplified = VWSimplifier.simplify(arc, 0.1);

      assertNotNull("Simplified geometry should not be null", simplified);

      // If all three points are removed/reduced, result might be a Line or Point
      // But VWSimplifier should document this behavior for curves
      // Red signal: no explicit contract; type silently changes

      fail("Red signal (1195-s-vw): VWSimplifier arc reduction contract is undocumented");
    } catch (Exception e) {
      fail("Red signal (1195-s-vw): VWSimplifier arc reduction fails - " + e.getMessage());
    }
  }

  /**
   * Test VW simplification of a CompoundCurve with arc component.
   *
   * Geometry: COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1.5 0.5, 2 0))
   *   - Line segment + arc segment
   *   - Mixed type geometry
   *
   * Expected: CompoundCurve identity preserved with arc components.
   *
   * Red signal: WKT parsing fails or type erasure to LineString.
   */
  public void testVWSimplifyCompoundCurveIdentity() throws Exception {
    try {
      String compoundWKT = "COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1.5 0.5, 2 0))";
      Geometry compound = reader.read(compoundWKT);

      Geometry simplified = VWSimplifier.simplify(compound, 0.01);

      assertNotNull("Simplified CompoundCurve should not be null", simplified);

      String resultType = simplified.getGeometryType();

      // Should remain CompoundCurve or at least preserve arc type in result
      if ("LineString".equals(resultType) || "MultiLineString".equals(resultType)) {
        fail("Red signal (1195-s-vw): VWSimplifier erases CompoundCurve with arc to linear type " +
            "(output: " + resultType + ")");
      }

      fail("Red signal (1195-s-vw): VWSimplifier on CompoundCurve not tested");
    } catch (Exception e) {
      fail("Red signal (1195-s-vw): VWSimplifier fails on CompoundCurve - " + e.getMessage());
    }
  }

  /**
   * Test that arc-aware simplification extracts extrema points if needed.
   *
   * For a CircularString with arc bulge, if simplification removes control points,
   * the arc extrema must still be preserved (or explicitly excluded by documented contract).
   *
   * Geometry: CIRCULARSTRING(0 0, 1 1, 2 0)
   *   - Arc bulges to rightmost point at approximately (1.4, 0.7) (exact depends on circle)
   *   - If middle control point (1,1) is removed by VW, the arc extremum might be lost
   *
   * Red signal: Simplified result misses arc interior extrema.
   */
  public void testVWSimplifyArcExtremaSafety() throws Exception {
    try {
      String arcWKT = "CIRCULARSTRING(0 0, 1 1, 2 0)";
      Geometry arc = reader.read(arcWKT);

      // Very large tolerance that might justify removing middle control point
      Geometry simplified = VWSimplifier.simplify(arc, 1.0);

      assertNotNull("Simplified geometry should not be null", simplified);

      // Get the bounding box of simplified result
      double simplifiedMinX = simplified.getEnvelopeInternal().getMinX();
      double simplifiedMaxX = simplified.getEnvelopeInternal().getMaxX();

      // Original arc bounding box should be preserved
      double arcMinX = arc.getEnvelopeInternal().getMinX();
      double arcMaxX = arc.getEnvelopeInternal().getMaxX();

      // Simplified should not lose extrema (or explicitly document why it does)
      assertTrue("Simplified arc should preserve horizontal extent " +
          "(arc maxX=" + arcMaxX + ", simplified maxX=" + simplifiedMaxX + ")",
          simplifiedMaxX >= arcMaxX - 0.01); // Small epsilon for rounding

      fail("Red signal (1195-s-vw): VWSimplifier arc extrema preservation not verified");
    } catch (AssertionError ae) {
      fail("Red signal (1195-s-vw): " + ae.getMessage());
    } catch (Exception e) {
      fail("Red signal (1195-s-vw): VWSimplifier arc extrema test failed - " + e.getMessage());
    }
  }

  /**
   * Test zero tolerance (identity simplification) on CircularString.
   *
   * With tolerance 0, simplification should not change anything.
   * Result should be geometrically and typologically identical to input.
   *
   * Red signal: Even at zero tolerance, arc type is erased.
   */
  public void testVWSimplifyZeroToleranceCircularString() throws Exception {
    try {
      String arcWKT = "CIRCULARSTRING(0 0, 1 1, 2 0)";
      Geometry arc = reader.read(arcWKT);

      // Zero tolerance = no simplification
      Geometry simplified = VWSimplifier.simplify(arc, 0.0);

      assertNotNull("Zero-tolerance simplification should return a result", simplified);

      // Type should match input exactly
      assertEquals("Zero-tolerance simplification should preserve type",
          arc.getGeometryType(), simplified.getGeometryType());

      // Coordinates should match
      assertTrue("Zero-tolerance simplification should preserve coordinates",
          arc.getCoordinates().length == simplified.getCoordinates().length);

      fail("Red signal (1195-s-vw): Zero-tolerance arc identity not verified");
    } catch (AssertionError ae) {
      fail("Red signal (1195-s-vw): " + ae.getMessage());
    } catch (Exception e) {
      fail("Red signal (1195-s-vw): Zero-tolerance simplification fails - " + e.getMessage());
    }
  }
}
