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
package org.locationtech.jts.operation.buffer;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTReader;

import junit.framework.TestCase;

/**
 * Tests for VariableBuffer (variable-radius buffer) on curves with arc-length parameterization.
 * Claim 1195-vbf: Arc-length interpolation for variable buffer radius on curved geometries.
 *
 * VariableBuffer allows per-vertex buffer radius specification. Current implementation
 * assumes LineString input and uses coordinate-index-based radius interpolation.
 *
 * For curves (CircularString, CompoundCurve), radius interpolation must respect arc-length,
 * not chord-length or coordinate indexing:
 *   - Arc-length parameterization: map radius to arc length via s = r·θ
 *   - Radius function: r(s) where s is arc-length position
 *   - Non-degeneracy guard: all radius values must be positive (no signed inversions)
 *
 * @version 1.0
 */
public class VariableBufferCurveTest extends TestCase {

  private GeometryFactory factory = new GeometryFactory();
  private WKTReader reader = new WKTReader(factory);

  public VariableBufferCurveTest(String name) {
    super(name);
  }

  /**
   * Test variable buffer on CircularString with arc-length parameterized radius.
   *
   * Geometry: Quarter-circle arc from (1, 0) via (0, 1) to (-1, 0)
   *   - Control points: 3 (start, control, end)
   *   - Arc length: π/2 ≈ 1.5708
   *
   * Radius function: r(s) = 0.2 + 0.3*(s / arclen)
   *   where s is arc-length position along the curve
   *   At s=0 (start): r = 0.2
   *   At s=π/4 (mid): r ≈ 0.375
   *   At s=π/2 (end): r = 0.5
   *
   * Non-degeneracy: r(s) > 0 ∀ s ∈ [0, arclen]  ✓ Satisfied (0.2 ≤ r ≤ 0.5)
   *
   * Red signal: VariableBuffer either:
   *   1. Rejects CircularString type (not supported), OR
   *   2. Uses linear interpolation instead of arc-length parameterization
   *
   * Root cause: VariableBuffer only accepts LineString. Documentation explicitly states
   * "Only single linestrings are supported as input."
   */
  public void testVariableBufferCircularStringArcLength() throws Exception {
    try {
      String arcWKT = "CIRCULARSTRING(1 0, 0 1, -1 0)";
      Geometry arc = reader.read(arcWKT);

      // Radius at each control point (approximates arc-length interpolation)
      double[] distances = new double[] { 0.2, 0.35, 0.5 };

      VariableBuffer varBuffer = new VariableBuffer(arc, distances);
      Geometry result = varBuffer.getResult();

      assertNotNull("Variable buffer should produce result", result);
      assertTrue("Result should be a valid polygon", result.getGeometryType().equals("Polygon"));

      fail("Red signal (1195-vbf): VariableBuffer produces result for CircularString " +
          "(expected to reject unsupported type or linearize incorrectly)");
    } catch (ClassCastException e) {
      // Expected Red signal: Geometry is not a LineString
      fail("Red signal (1195-vbf): VariableBuffer rejects CircularString - " + e.getMessage());
    } catch (Exception e) {
      fail("Red signal (1195-vbf): VariableBuffer fails on CircularString - " + e.getMessage());
    }
  }

  /**
   * Test variable buffer on CompoundCurve with mixed line and arc members.
   *
   * Geometry: COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1.5 0.5, 2 0))
   *   - Member 0: LineString from (0,0) to (1,0), length = 1.0
   *   - Member 1: CircularString arc from (1,0) to (2,0) via (1.5,0.5), arc-length ≈ ?
   *
   * Radius function: should vary smoothly across both members
   *   - At member 0 start: r = 0.2
   *   - At member 0 end / member 1 start: r = 0.35
   *   - At member 1 end: r = 0.5
   *
   * Red signal: CompoundCurve not recognized or members not handled independently.
   */
  public void testVariableBufferCompoundCurveArcLength() throws Exception {
    try {
      String compoundWKT = "COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1.5 0.5, 2 0))";
      Geometry compound = reader.read(compoundWKT);

      // Radius specification needs to account for both line and arc components
      double[] distances = new double[] { 0.2, 0.35, 0.5 };

      VariableBuffer varBuffer = new VariableBuffer(compound, distances);
      Geometry result = varBuffer.getResult();

      assertTrue("Variable buffer on CompoundCurve should produce result", result != null);
      fail("Red signal (1195-vbf): VariableBuffer produces result for CompoundCurve " +
          "(expected type rejection)");
    } catch (ClassCastException e) {
      fail("Red signal (1195-vbf): VariableBuffer rejects CompoundCurve - " + e.getMessage());
    } catch (Exception e) {
      fail("Red signal (1195-vbf): VariableBuffer fails on CompoundCurve - " + e.getMessage());
    }
  }

  /**
   * Test non-degeneracy guard: negative radius rejection on LineString baseline.
   *
   * Even on LinearString (non-curve), VariableBuffer should guard against
   * negative/zero radius values that would produce invalid buffer geometry.
   *
   * Geometry: LINESTRING(0 0, 2 0, 4 0)
   * Radius array: [0.5, 0.2, -0.1]  ← DEGENERATE: negative value at end
   *
   * Non-degeneracy constraint: all distances > 0
   *   ✗ VIOLATED: distances[2] = -0.1 is negative (signed-radius inversion)
   *
   * Expected: throw IllegalArgumentException or similar
   * Red signal: Implementation doesn't guard against degenerate radius values
   */
  public void testVariableBufferDegeneracyGuardNegativeRadius() throws Exception {
    try {
      Coordinate[] coords = new Coordinate[] {
          new Coordinate(0, 0),
          new Coordinate(2, 0),
          new Coordinate(4, 0)
      };
      Geometry line = factory.createLineString(coords);

      // Distance array with negative (signed-radius inversion)
      double[] distances = new double[] { 0.5, 0.2, -0.1 };

      VariableBuffer varBuffer = new VariableBuffer(line, distances);
      Geometry result = varBuffer.getResult();

      fail("Red signal (1195-vbf): VariableBuffer accepts negative radius " +
          "(should reject degeneracy)");
    } catch (IllegalArgumentException e) {
      // Correct: degeneracy guard works
      assertTrue("Guard message should mention negative/invalid distance",
          e.getMessage().contains("distance") ||
          e.getMessage().contains("negative") ||
          e.getMessage().toLowerCase().contains("invalid"));
    } catch (Exception e) {
      fail("Red signal (1195-vbf): Unexpected error - " + e.getMessage());
    }
  }

  /**
   * Test radius interpolation along arc-length for accurate buffer geometry.
   *
   * For curves, the buffer radius should be interpolated along arc-length,
   * not along the chord or control-point sequence.
   *
   * Geometry: CIRCULARSTRING(0 0, 1 1, 2 0)
   *   - Arc length: depends on circle center and angle
   *   - Control points: 3 (not uniform in arc-length)
   *
   * Radius function: r(s) should vary with arc-length position, not control-point index.
   *
   * Red signal: Radius interpolation doesn't account for arc parameterization.
   */
  public void testVariableBufferArcLengthInterpolation() throws Exception {
    try {
      String arcWKT = "CIRCULARSTRING(0 0, 1 1, 2 0)";
      Geometry arc = reader.read(arcWKT);

      // Radius values at control points
      double[] distances = new double[] { 0.1, 0.2, 0.3 };

      VariableBuffer varBuffer = new VariableBuffer(arc, distances);
      Geometry result = varBuffer.getResult();

      // The buffer result polygon should have:
      // - Outer boundary that respects arc-length parameterization
      // - Radius increasing smoothly along the arc (not just at control points)

      assertNotNull("Buffer result should not be null", result);
      assertTrue("Buffer should be a valid geometry", result.isValid());

      fail("Red signal (1195-vbf): Arc-length interpolation not verified for curves");
    } catch (Exception e) {
      fail("Red signal (1195-vbf): VariableBuffer arc-length test failed - " + e.getMessage());
    }
  }

  /**
   * Test zero-radius handling at a point on a curved geometry.
   *
   * Zero-radius creates a degenerate buffer at that location (sharp point).
   * For curves, this should be handled similarly to LinearString.
   *
   * Red signal: Zero-radius on curves not properly handled.
   */
  public void testVariableBufferZeroRadiusOnArc() throws Exception {
    try {
      String arcWKT = "CIRCULARSTRING(1 0, 0 1, -1 0)";
      Geometry arc = reader.read(arcWKT);

      // Zero radius at middle control point
      double[] distances = new double[] { 0.3, 0.0, 0.3 };

      VariableBuffer varBuffer = new VariableBuffer(arc, distances);
      Geometry result = varBuffer.getResult();

      assertTrue("Buffer with zero-radius should produce valid geometry", result != null);
      fail("Red signal (1195-vbf): Zero-radius handling on curves not tested");
    } catch (Exception e) {
      fail("Red signal (1195-vbf): Zero-radius test failed - " + e.getMessage());
    }
  }
}
