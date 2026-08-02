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

package org.locationtech.jts.operation.polygonize;

import java.util.Collection;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTReader;

import junit.framework.TestCase;

/**
 * Tests for Polygonizer on curve geometries.
 * Claim 1195-plg: Polygonizer must accept CompoundCurve edges and emit CurvePolygon faces.
 *
 * Current implementation only handles linear edges (LineString).
 * CompoundCurve and CircularString edges are not recognized.
 * Result faces are always Polygon, never CurvePolygon.
 *
 * @version 1.0
 */
public class PolygonizerCurveTest extends TestCase {

  private GeometryFactory geometryFactory = new GeometryFactory();
  private WKTReader reader = new WKTReader(geometryFactory);

  public PolygonizerCurveTest(String name) {
    super(name);
  }

  /**
   * Test Polygonizer with CompoundCurve edges forming a closed ring.
   *
   * Geometry: Closed CompoundCurve with line and arc segments
   *   COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1 1, 0 1), (0 1, 0 0))
   *   - Line segment from (0,0) to (1,0)
   *   - Arc from (1,0) to (0,1) via (1,1)
   *   - Line segment from (0,1) to (0,0)
   *   Forms a closed ring
   *
   * Expected behavior (arc-aware):
   *   - Polygonizer accepts the CompoundCurve edges
   *   - Result is a CurvePolygon with arc component in exterior ring
   *
   * Current (Red signal): Either CompoundCurve not recognized or result is
   *   empty/invalid or emits Polygon instead of CurvePolygon.
   *
   * Red signal (claim 1195-plg): Polygonizer not arc-aware.
   */
  public void testPolygonizerCompoundCurveClosedRing() throws Exception {
    try {
      // Closed CompoundCurve ring with line + arc
      String ccWKT = "COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1 1, 0 1), (0 1, 0 0))";
      Geometry cc = reader.read(ccWKT);

      Polygonizer polygonizer = new Polygonizer();
      polygonizer.add(cc);

      // Get the resulting polygons
      Collection<Geometry> polygons = polygonizer.getPolygons();

      assertNotNull("Polygonizer should return polygons", polygons);
      assertTrue("Should have at least one polygon", polygons.size() > 0);

      // Check the type of the polygon
      Geometry polygon = polygons.iterator().next();
      String geomType = polygon.getGeometryType();

      // Red signal: result is Polygon instead of CurvePolygon
      // Or result is empty
      if (polygon.isEmpty()) {
        fail("Red signal (1195-plg): Polygonizer produces empty result for CompoundCurve");
      }

      if ("Polygon".equals(geomType)) {
        fail("Red signal (1195-plg): Polygonizer emits Polygon instead of CurvePolygon " +
            "(input: CompoundCurve, output: " + geomType + ")");
      }

      fail("Red signal (1195-plg): Polygonizer CompoundCurve test not completed");
    } catch (Exception e) {
      fail("Red signal (1195-plg): Polygonizer fails on CompoundCurve - " + e.getMessage());
    }
  }

  /**
   * Test Polygonizer with CircularString edges.
   *
   * Geometry: Four circular arcs forming a closed ring
   *   (Multiple CIRCULARSTRING pieces that form a closed loop)
   *
   * Expected: Polygonizer accepts arc edges and emits CurvePolygon.
   *
   * Red signal: Arcs not recognized or wrong result type.
   */
  public void testPolygonizerCircularStringEdges() throws Exception {
    try {
      // Collection of arc edges forming a closed polygon
      String cc1 = "CIRCULARSTRING(0 0, 1 1, 0 2)";
      String cc2 = "CIRCULARSTRING(0 2, -1 1, 0 0)";

      Geometry arc1 = reader.read(cc1);
      Geometry arc2 = reader.read(cc2);

      Polygonizer polygonizer = new Polygonizer();
      polygonizer.add(arc1);
      polygonizer.add(arc2);

      Collection<Geometry> polygons = polygonizer.getPolygons();

      assertNotNull("Polygonizer should process arc edges", polygons);

      if (polygons.isEmpty()) {
        fail("Red signal (1195-plg): Polygonizer produces no polygons from arc edges");
      }

      // Check if result is arc-aware
      for (Geometry poly : polygons) {
        if ("Polygon".equals(poly.getGeometryType())) {
          org.locationtech.jts.geom.Polygon polygon = (org.locationtech.jts.geom.Polygon) poly;
          if (!polygon.getExteriorRing().isEmpty() &&
              "LinearRing".equals(polygon.getExteriorRing().getGeometryType())) {
            fail("Red signal (1195-plg): Polygonizer emits linear rings instead of preserving arcs");
          }
        }
      }

      fail("Red signal (1195-plg): Polygonizer arc edges test not completed");
    } catch (Exception e) {
      fail("Red signal (1195-plg): Polygonizer fails on CircularString edges - " + e.getMessage());
    }
  }

  /**
   * Test Polygonizer with CompoundCurve including a hole.
   *
   * Geometry: COMPOUNDCURVE exterior ring with COMPOUNDCURVE hole
   *   - Exterior: line + arc forming outer boundary
   *   - Interior: line + arc forming hole boundary
   *
   * Expected: CurvePolygon with exterior and interior curve rings.
   *
   * Red signal: Hole not handled or type not CurvePolygon.
   */
  public void testPolygonizerCompoundCurveWithHole() throws Exception {
    try {
      // Outer ring: CompoundCurve
      String outerWKT = "COMPOUNDCURVE((0 0, 2 0), CIRCULARSTRING(2 0, 2 2, 0 2), (0 2, 0 0))";
      // Inner hole: CompoundCurve
      String innerWKT = "COMPOUNDCURVE((0.5 0.5, 1.5 0.5), CIRCULARSTRING(1.5 0.5, 1.5 1.5, 0.5 1.5), (0.5 1.5, 0.5 0.5))";

      Geometry outer = reader.read(outerWKT);
      Geometry inner = reader.read(innerWKT);

      Polygonizer polygonizer = new Polygonizer();
      polygonizer.add(outer);
      polygonizer.add(inner);

      Collection<Geometry> polygons = polygonizer.getPolygons();

      assertNotNull("Polygonizer should handle hole boundaries", polygons);
      assertTrue("Should produce polygon with hole", polygons.size() > 0);

      Geometry poly = polygons.iterator().next();

      // Check if hole is present
      if ("Polygon".equals(poly.getGeometryType())) {
        org.locationtech.jts.geom.Polygon polygon = (org.locationtech.jts.geom.Polygon) poly;
        int numInteriorRings = polygon.getNumInteriorRing();
        assertTrue("Polygon should have at least one interior ring (hole)",
            numInteriorRings > 0);

        // Red signal: if interior rings are linear instead of preserving curves
        if (numInteriorRings > 0) {
          Geometry hole = polygon.getInteriorRingN(0);
          if ("LinearRing".equals(hole.getGeometryType())) {
            fail("Red signal (1195-plg): Polygonizer linearizes hole instead of preserving arc");
          }
        }
      }

      fail("Red signal (1195-plg): Polygonizer with hole test not completed");
    } catch (Exception e) {
      fail("Red signal (1195-plg): Polygonizer fails with hole - " + e.getMessage());
    }
  }

  /**
   * Test that dangling/invalid segments from CompoundCurve are reported correctly.
   *
   * If CompoundCurve edges don't form a closed ring, they should be identified
   * as dangling or invalid edges, not silently dropped.
   *
   * Red signal: CompoundCurve edges not properly validated or categorized.
   */
  public void testPolygonizerCompoundCurveDangling() throws Exception {
    try {
      // CompoundCurve that doesn't close (missing segment)
      String ccWKT = "COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1 1, 0 1))";
      Geometry cc = reader.read(ccWKT);

      Polygonizer polygonizer = new Polygonizer();
      polygonizer.add(cc);

      Collection<Geometry> polygons = polygonizer.getPolygons();
      Collection<Geometry> dangles = polygonizer.getDangles();
      Collection<Geometry> cutEdges = polygonizer.getCutEdges();

      // Red signal: if no dangling edges are identified
      if (polygons.isEmpty() && dangles.isEmpty() && cutEdges.isEmpty()) {
        fail("Red signal (1195-plg): Polygonizer doesn't validate CompoundCurve closure");
      }

      fail("Red signal (1195-plg): Polygonizer dangling detection test not completed");
    } catch (Exception e) {
      fail("Red signal (1195-plg): Polygonizer dangling test failed - " + e.getMessage());
    }
  }

  /**
   * Test GeometryCollection of CompoundCurve edges.
   *
   * Polygonizer.add(Geometry) should accept collections of curve edges.
   *
   * Red signal: GeometryCollection not properly handled or curves ignored.
   */
  public void testPolygonizerGeometryCollectionCurves() throws Exception {
    try {
      String cc1 = "COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1 1, 0 1))";
      String cc2 = "LINESTRING(0 1, 0 0)";
      String gcWKT = "GEOMETRYCOLLECTION(" + cc1 + ", " + cc2 + ")";

      Geometry gc = reader.read(gcWKT);

      Polygonizer polygonizer = new Polygonizer();
      polygonizer.add(gc);

      Collection<Geometry> polygons = polygonizer.getPolygons();

      assertNotNull("Polygonizer should handle GeometryCollection", polygons);

      if (polygons.isEmpty()) {
        fail("Red signal (1195-plg): Polygonizer doesn't process curve edges in GeometryCollection");
      }

      fail("Red signal (1195-plg): GeometryCollection curve test not completed");
    } catch (Exception e) {
      fail("Red signal (1195-plg): Polygonizer GeometryCollection test failed - " + e.getMessage());
    }
  }
}
