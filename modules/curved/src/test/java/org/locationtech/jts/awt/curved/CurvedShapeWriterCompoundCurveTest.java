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
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.CompoundCurve;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;

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
