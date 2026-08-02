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
 * Tests for TopologyPreservingSimplifier on curve geometries.
 * Claim 1195-s-tp: TopologyPreservingSimplifier must support arc-aware simplification
 * while preserving topology (component count, dimension, relationships).
 *
 * Current implementation uses LineStringMapBuilderFilter and LineStringTransformer,
 * which only handle LineString/LinearRing. Curves are either rejected or degraded
 * to linear approximations without explicit arc-aware topology preservation.
 *
 * @version 1.0
 */
public class TopologyPreservingSimplifierCurveTest extends TestCase {

  private GeometryFactory geometryFactory = new GeometryFactory();
  private WKTReader reader = new WKTReader(geometryFactory);

  public TopologyPreservingSimplifierCurveTest(String name) {
    super(name);
  }

  /**
   * Test topology-preserving simplification of a single circular arc.
   *
   * Geometry: CIRCULARSTRING(0 0, 1 1, 2 0)
   *   - Control points: (0,0), (1,1), (2,0)
   *   - Arc bulges upward
   *
   * Simplification tolerance: 0.5 (moderate)
   *
   * Expected behavior (arc-aware):
   *   - Result is a simplified arc or valid linear approximation
   *   - Result is valid (>=2 vertices)
   *   - Topology of arc is preserved (arc extent, shape)
   *
   * Current (Red signal): Either WKT parsing fails (prerequisite 1195-d)
   *   or simplifier doesn't handle curves.
   *
   * Red signal (claim 1195-s-tp): TopologyPreservingSimplifier not arc-aware.
   */
  public void testTopologyPreservingSimplifyCircularString() throws Exception {
    try {
      String arcWKT = "CIRCULARSTRING(0 0, 1 1, 2 0)";
      Geometry arc = reader.read(arcWKT);

      Geometry simplified = TopologyPreservingSimplifier.simplify(arc, 0.5);

      assertNotNull("Simplified geometry should not be null", simplified);

      // Topology preservation: result should be valid
      assertTrue("Simplified geometry should be valid (non-empty)", !simplified.isEmpty());

      // Dimension preservation: result should match input dimension
      assertEquals("Simplified should preserve dimension",
          arc.getDimension(), simplified.getDimension());

      fail("Red signal (1195-s-tp): TopologyPreservingSimplifier on CircularString not tested");
    } catch (Exception e) {
      fail("Red signal (1195-s-tp): TopologyPreservingSimplifier fails on CircularString - " +
          e.getMessage());
    }
  }

  /**
   * Test topology preservation on a CompoundCurve.
   *
   * Geometry: COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1.5 0.5, 2 0))
   *   - Line segment + arc segment
   *   - Shared vertex at (1,0)
   *
   * Expected: Simplified result maintains shared vertices and component linkage.
   *
   * Red signal: Components unlinked or arc handling broken.
   */
  public void testTopologyPreservingSimplifyCompoundCurve() throws Exception {
    try {
      String compoundWKT = "COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1.5 0.5, 2 0))";
      Geometry compound = reader.read(compoundWKT);

      Geometry simplified = TopologyPreservingSimplifier.simplify(compound, 0.1);

      assertNotNull("Simplified CompoundCurve should not be null", simplified);

      // Topology check: component count preserved (still 2 components, line + arc)
      int originalComponents = compound.getNumGeometries();
      int simplifiedComponents = simplified.getNumGeometries();

      // If the simplifier handles it properly, component structure is preserved
      // Red signal: if components are lost or merged incorrectly
      fail("Red signal (1195-s-tp): TopologyPreservingSimplifier on CompoundCurve not tested");
    } catch (Exception e) {
      fail("Red signal (1195-s-tp): TopologyPreservingSimplifier fails on CompoundCurve - " +
          e.getMessage());
    }
  }

  /**
   * Test topology preservation on a closed CurvePolygon.
   *
   * Geometry: CURVEPOLYGON(CIRCULARSTRING(0 0, 0 1, 1 1, 1 0, 0 0))
   *   - Circular exterior ring (closed)
   *   - 5 control points forming a closed curve
   *
   * Expected: Simplified result remains closed and topologically valid polygon.
   *
   * Red signal: Ring opening, topology break, or arc erasure.
   */
  public void testTopologyPreservingSimplifyClosedCurvePolygon() throws Exception {
    try {
      // Closed curve polygon
      String curvePolyWKT = "CURVEPOLYGON(CIRCULARSTRING(0 0, 0 1, 1 1, 1 0, 0 0))";
      Geometry curvePoly = reader.read(curvePolyWKT);

      Geometry simplified = TopologyPreservingSimplifier.simplify(curvePoly, 0.1);

      assertNotNull("Simplified CurvePolygon should not be null", simplified);

      // Topology check: result should be a valid polygon (closed ring)
      assertTrue("Simplified polygon should be valid",
          simplified.getGeometryType().equals("Polygon") ||
          simplified.getGeometryType().equals("CurvePolygon"));

      // Closure check: ring must be closed (first and last coordinates equal)
      if (simplified.getCoordinates().length > 0) {
        int n = simplified.getCoordinates().length;
        assertTrue("Simplified ring should be closed (endpoints equal)",
            simplified.getCoordinates()[0].equals(simplified.getCoordinates()[n - 1]));
      }

      fail("Red signal (1195-s-tp): TopologyPreservingSimplifier on CurvePolygon not tested");
    } catch (Exception e) {
      fail("Red signal (1195-s-tp): TopologyPreservingSimplifier fails on CurvePolygon - " +
          e.getMessage());
    }
  }

  /**
   * Test that simplified geometry covers the original arc.
   *
   * For topology preservation, the simplified result must not shrink below the original.
   * Simplified envelope should contain or equal original envelope (within tolerance).
   *
   * Red signal: Simplified result is smaller than original arc (missing bulge).
   */
  public void testTopologyPreservingSimplifyArcCoverage() throws Exception {
    try {
      // Arc with significant bulge
      String arcWKT = "CIRCULARSTRING(0 0, 2 3, 4 0)";
      Geometry arc = reader.read(arcWKT);

      Geometry simplified = TopologyPreservingSimplifier.simplify(arc, 0.5);

      assertNotNull("Simplified geometry should not be null", simplified);

      // Coverage check: simplified should have same or larger envelope
      double originalArea = arc.getEnvelope().getArea();
      double simplifiedArea = simplified.getEnvelope().getArea();

      // Simplified may be smaller (straight line), but should be proportional
      // Red signal: if dramatically smaller (missing arc extent)
      assertTrue("Simplified arc should maintain coverage of original " +
          "(original envelope area=" + originalArea + ", simplified=" + simplifiedArea + ")",
          simplifiedArea >= originalArea * 0.5); // Allow up to 50% envelope reduction

      fail("Red signal (1195-s-tp): TopologyPreservingSimplifier arc coverage not verified");
    } catch (AssertionError ae) {
      fail("Red signal (1195-s-tp): " + ae.getMessage());
    } catch (Exception e) {
      fail("Red signal (1195-s-tp): TopologyPreservingSimplifier arc coverage test failed - " +
          e.getMessage());
    }
  }

  /**
   * Test zero-tolerance (identity) on CircularString.
   *
   * With tolerance 0, simplification should be a no-op or return geometrically equivalent result.
   *
   * Red signal: Even zero tolerance breaks arc identity.
   */
  public void testTopologyPreservingSimplifyZeroToleranceArc() throws Exception {
    try {
      String arcWKT = "CIRCULARSTRING(0 0, 1 1, 2 0)";
      Geometry arc = reader.read(arcWKT);

      Geometry simplified = TopologyPreservingSimplifier.simplify(arc, 0.0);

      assertNotNull("Zero-tolerance simplification should return a result", simplified);

      // With zero tolerance, result should be valid
      assertTrue("Zero-tolerance result should be valid", !simplified.isEmpty());

      // Dimension should match
      assertEquals("Zero-tolerance should preserve dimension",
          arc.getDimension(), simplified.getDimension());

      fail("Red signal (1195-s-tp): Zero-tolerance arc simplification not tested");
    } catch (AssertionError ae) {
      fail("Red signal (1195-s-tp): " + ae.getMessage());
    } catch (Exception e) {
      fail("Red signal (1195-s-tp): Zero-tolerance arc simplification fails - " +
          e.getMessage());
    }
  }

  /**
   * Test that component count is preserved on mixed-geometry input.
   *
   * Geometry: GEOMETRYCOLLECTION with multiple curve types
   *   - Line and arc should remain separate components
   *
   * Red signal: Components merged or lost.
   */
  public void testTopologyPreservingSimplifyComponentCount() throws Exception {
    try {
      // MultiGeometry with line and arc
      String multiWKT = "GEOMETRYCOLLECTION(LINESTRING(0 0, 1 0), CIRCULARSTRING(1 0, 1.5 0.5, 2 0))";
      Geometry multi = reader.read(multiWKT);

      Geometry simplified = TopologyPreservingSimplifier.simplify(multi, 0.1);

      assertNotNull("Simplified multi-geometry should not be null", simplified);

      // Component count preservation
      assertEquals("Simplified should preserve component count",
          multi.getNumGeometries(), simplified.getNumGeometries());

      fail("Red signal (1195-s-tp): TopologyPreservingSimplifier component count not verified");
    } catch (AssertionError ae) {
      fail("Red signal (1195-s-tp): " + ae.getMessage());
    } catch (Exception e) {
      fail("Red signal (1195-s-tp): TopologyPreservingSimplifier component count test failed - " +
          e.getMessage());
    }
  }
}
