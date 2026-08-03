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
package org.locationtech.jts.awt.curved;

import java.awt.Shape;
import java.awt.geom.PathIterator;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.CompoundCurve;
import org.locationtech.jts.geom.curved.CurvePolygon;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jts.geom.curved.MultiSurface;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Smoke test that {@link CurvedShapeWriter} walks
 * {@link CompoundCurve} member-by-member: arc members emit
 * cubic-bezier segments via {@link CircularArcRenderer}; line
 * members emit straight {@code lineTo} segments; the path stays
 * connected across member boundaries (no spurious {@code moveTo}
 * at junctions).
 */
public class CurvedShapeWriterCompoundCurveTest extends GeometryTestCase {

  public static void main(String[] args) {
    TestRunner.run(CurvedShapeWriterCompoundCurveTest.class);
  }

  public CurvedShapeWriterCompoundCurveTest(String name) { super(name); }

  /**
   * Counts PathIterator segment kinds. The CompoundCurve
   * (LineString line, CircularString arc) should produce:
   *   1× SEG_MOVETO  (start of line)
   *   ≥1× SEG_LINETO (along the straight)
   *   ≥1× SEG_CUBICTO (arc bezier segments)
   * — and crucially, only the one MOVETO.
   */
  public void testLineThenArc() {
    CurvedGeometryFactory f = new CurvedGeometryFactory();
    LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    CircularString arc = f.createCircularString(f.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(10, 0), new Coordinate(15, 5), new Coordinate(20, 0)
    }));
    CompoundCurve cc = new CompoundCurve(new LineString[] { line, arc }, f);

    Shape shape = new CurvedShapeWriter().toShape(cc);
    SegCounts counts = SegCounts.of(shape);

    assertEquals("expected exactly one MOVETO at the start of the chain", 1, counts.moveTo);
    assertTrue("expected at least one LINETO for the straight member, got " + counts.lineTo,
        counts.lineTo >= 1);
    assertTrue("expected at least one CUBICTO for the arc bezier segments, got " + counts.cubicTo,
        counts.cubicTo >= 1);
  }

  /** Two arc members in a chain still emit only one MOVETO. */
  public void testArcThenArc() {
    CurvedGeometryFactory f = new CurvedGeometryFactory();
    CircularString arc1 = f.createCircularString(f.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(5, 5), new Coordinate(10, 0)
    }));
    CircularString arc2 = f.createCircularString(f.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(10, 0), new Coordinate(15, -5), new Coordinate(20, 0)
    }));
    CompoundCurve cc = new CompoundCurve(new LineString[] { arc1, arc2 }, f);

    Shape shape = new CurvedShapeWriter().toShape(cc);
    SegCounts counts = SegCounts.of(shape);

    assertEquals(1, counts.moveTo);
    assertEquals("two-arc chain should produce no straight LINETO segments", 0, counts.lineTo);
    assertTrue(counts.cubicTo >= 2);
  }

  public void testEmptyCompoundCurveProducesEmptyShape() {
    CurvedGeometryFactory f = new CurvedGeometryFactory();
    CompoundCurve cc = new CompoundCurve(new LineString[0], f);
    Shape shape = new CurvedShapeWriter().toShape(cc);
    SegCounts counts = SegCounts.of(shape);
    assertEquals(0, counts.moveTo);
    assertEquals(0, counts.lineTo);
    assertEquals(0, counts.cubicTo);
  }

  /**
   * Test CurvedShapeWriter rendering of CurvePolygon with arc exterior ring.
   *
   * Claim 1195-d-sw (CurvedShapeWriter CurvePolygon): CurvedShapeWriter must
   * accept CurvePolygon geometries and render rings with arc components via
   * cubic Bezier approximations.
   *
   * Current (Red signal): CurvePolygon not recognized in toShapeOther(),
   * returns null or throws exception.
   */
  public void testCurvePolygonExteriorArc() {
    try {
      CurvedGeometryFactory f = new CurvedGeometryFactory();

      // CurvePolygon with circular arc exterior ring
      CircularString arcRing = f.createCircularString(f.getCoordinateSequenceFactory().create(
          new Coordinate[] {
              new Coordinate(0, 0), new Coordinate(1, 1), new Coordinate(2, 0),
              new Coordinate(1, -1), new Coordinate(0, 0)
          }));

      CurvePolygon curvePoly = new CurvePolygon((LinearRing) arcRing, null, f);

      CurvedShapeWriter writer = new CurvedShapeWriter();
      Shape shape = writer.toShape(curvePoly);

      assertNotNull("CurvedShapeWriter should handle CurvePolygon exterior ring", shape);

      SegCounts counts = SegCounts.of(shape);
      assertTrue("CurvePolygon with arc ring should produce path segments " +
          "(got " + counts.moveTo + " moveTo, " + counts.cubicTo + " cubicTo)",
          counts.moveTo > 0 || counts.cubicTo > 0);

      fail("Red signal (1195-d-sw): CurvedShapeWriter CurvePolygon rendering not verified");
    } catch (Exception e) {
      fail("Red signal (1195-d-sw): CurvedShapeWriter fails on CurvePolygon - " +
          e.getMessage());
    }
  }

  /**
   * Test CurvePolygon with arc exterior and arc interior rings (hole).
   *
   * Expected (arc-aware): Exterior and interior rings rendered with cubic Bezier,
   * only one MOVETO at start of exterior, then MOVETO before hole.
   *
   * Red signal: Rings not rendered, wrong shape, or exception.
   */
  public void testCurvePolygonWithArcHole() {
    try {
      CurvedGeometryFactory f = new CurvedGeometryFactory();

      // Exterior ring: arc
      CircularString exterior = f.createCircularString(f.getCoordinateSequenceFactory().create(
          new Coordinate[] {
              new Coordinate(0, 0), new Coordinate(2, 2), new Coordinate(4, 0),
              new Coordinate(2, -2), new Coordinate(0, 0)
          }));

      // Interior ring (hole): arc
      CircularString interior = f.createCircularString(f.getCoordinateSequenceFactory().create(
          new Coordinate[] {
              new Coordinate(1, 0), new Coordinate(1.5, 1), new Coordinate(2, 0),
              new Coordinate(1.5, -1), new Coordinate(1, 0)
          }));

      CurvePolygon curvePoly = new CurvePolygon((LinearRing) exterior,
          new LinearRing[] { (LinearRing) interior }, f);

      CurvedShapeWriter writer = new CurvedShapeWriter();
      Shape shape = writer.toShape(curvePoly);

      assertNotNull("CurvedShapeWriter should render CurvePolygon with hole", shape);

      SegCounts counts = SegCounts.of(shape);
      // Expected: 2 MOVETO (exterior start, hole start) + arc segments
      assertTrue("Polygon with arc hole should produce at least 2 MOVETO and arc segments " +
          "(got " + counts.moveTo + " moveTo, " + counts.cubicTo + " cubicTo)",
          counts.moveTo >= 2 || counts.cubicTo > 0);

      fail("Red signal (1195-d-sw): CurvedShapeWriter CurvePolygon hole rendering not verified");
    } catch (Exception e) {
      fail("Red signal (1195-d-sw): CurvedShapeWriter fails on CurvePolygon with hole - " +
          e.getMessage());
    }
  }

  /**
   * Test MultiSurface (collection of CurvePolygon/Polygon with arcs).
   *
   * Expected: Each surface rendered as separate shape contribution, all appended.
   *
   * Red signal: MultiSurface not recognized or members dropped.
   */
  public void testMultiSurfaceWithCurvePolygons() {
    try {
      CurvedGeometryFactory f = new CurvedGeometryFactory();

      // Create two CurvePolygons with arc exterior rings
      CircularString arc1 = f.createCircularString(f.getCoordinateSequenceFactory().create(
          new Coordinate[] {
              new Coordinate(0, 0), new Coordinate(1, 1), new Coordinate(2, 0),
              new Coordinate(1, -1), new Coordinate(0, 0)
          }));
      CurvePolygon curvePoly1 = new CurvePolygon((LinearRing) arc1, null, f);

      CircularString arc2 = f.createCircularString(f.getCoordinateSequenceFactory().create(
          new Coordinate[] {
              new Coordinate(3, 0), new Coordinate(4, 1), new Coordinate(5, 0),
              new Coordinate(4, -1), new Coordinate(3, 0)
          }));
      CurvePolygon curvePoly2 = new CurvePolygon((LinearRing) arc2, null, f);

      Polygon[] members = { curvePoly1, curvePoly2 };
      MultiSurface multiSurf = new MultiSurface(members, f);

      CurvedShapeWriter writer = new CurvedShapeWriter();
      Shape shape = writer.toShape(multiSurf);

      assertNotNull("CurvedShapeWriter should handle MultiSurface", shape);

      SegCounts counts = SegCounts.of(shape);
      // Two surfaces = at least 2 MOVETO + arc segments for each
      assertTrue("MultiSurface should produce path segments for each member " +
          "(got " + counts.moveTo + " moveTo)",
          counts.moveTo >= 2 || counts.cubicTo > 0);

      fail("Red signal (1195-d-sw): CurvedShapeWriter MultiSurface rendering not verified");
    } catch (Exception e) {
      fail("Red signal (1195-d-sw): CurvedShapeWriter fails on MultiSurface - " +
          e.getMessage());
    }
  }

  /**
   * Test mixed MultiSurface with Polygon (linear) and CurvePolygon (arcs).
   *
   * Expected: Both rendered correctly, arcs as cubic Bezier, linear as lineTo.
   *
   * Red signal: Mixed geometry not handled or arcs dropped.
   */
  public void testMultiSurfaceMixedLinearAndCurve() {
    try {
      CurvedGeometryFactory f = new CurvedGeometryFactory();

      // Linear Polygon
      LinearRing linearRing = f.createLinearRing(new Coordinate[] {
          new Coordinate(0, 0), new Coordinate(1, 0),
          new Coordinate(1, 1), new Coordinate(0, 1),
          new Coordinate(0, 0)
      });
      Polygon linearPoly = f.createPolygon(linearRing);

      // CurvePolygon with arc
      CircularString arcRing = f.createCircularString(f.getCoordinateSequenceFactory().create(
          new Coordinate[] {
              new Coordinate(3, 0), new Coordinate(4, 1), new Coordinate(5, 0),
              new Coordinate(4, -1), new Coordinate(3, 0)
          }));
      Polygon curvePoly = new CurvePolygon((LinearRing) arcRing, null, f);

      // Create MultiSurface with mixed linear and curved members
      Polygon[] members = { linearPoly, curvePoly };
      MultiSurface multiSurf = new MultiSurface(members, f);

      CurvedShapeWriter writer = new CurvedShapeWriter();
      Shape shape = writer.toShape(multiSurf);

      assertNotNull("CurvedShapeWriter should handle mixed MultiSurface", shape);

      SegCounts counts = SegCounts.of(shape);
      // Linear + arc = mix of LINETO and CUBICTO
      assertTrue("Mixed MultiSurface should produce both linear and arc segments " +
          "(got " + counts.lineTo + " lineTo, " + counts.cubicTo + " cubicTo)",
          counts.lineTo > 0 || counts.cubicTo > 0);

      fail("Red signal (1195-d-sw): CurvedShapeWriter mixed MultiSurface not verified");
    } catch (Exception e) {
      fail("Red signal (1195-d-sw): CurvedShapeWriter fails on mixed MultiSurface - " +
          e.getMessage());
    }
  }

  // ---------------------------------------------------------------

  private static final class SegCounts {
    int moveTo;
    int lineTo;
    int cubicTo;
    int quadTo;

    static SegCounts of(Shape s) {
      SegCounts c = new SegCounts();
      double[] coords = new double[6];
      PathIterator it = s.getPathIterator(null);
      while (!it.isDone()) {
        int seg = it.currentSegment(coords);
        switch (seg) {
          case PathIterator.SEG_MOVETO:  c.moveTo++; break;
          case PathIterator.SEG_LINETO:  c.lineTo++; break;
          case PathIterator.SEG_CUBICTO: c.cubicTo++; break;
          case PathIterator.SEG_QUADTO:  c.quadTo++; break;
          default: break;
        }
        it.next();
      }
      return c;
    }
  }
}
