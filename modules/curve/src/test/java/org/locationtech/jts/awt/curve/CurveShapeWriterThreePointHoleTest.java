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
package org.locationtech.jts.awt.curve;

import java.awt.Shape;
import java.awt.geom.PathIterator;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTWriter;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * #114 / tb-h-1: a 3-point curve hole stays a curve and the canvas
 * paints the arc(s), not the control-point chord triangle.
 * <p>
 * No UX ring was attached. Fixtures are factory-built. ISO/IEC 13249-3:
 * an open {@code CIRCULARSTRING} is odd and at least three controls; a
 * closed CircularString ring is five tokens first=last. This class does
 * not treat four-token {@code (A,B,C,A)} as a valid CircularString ring.
 * The complementary close is paint-only and is not written into WKT.
 * <p>
 * Not FCP-H (type already stays CircularString). Not #86 3-click shell.
 */
public class CurveShapeWriterThreePointHoleTest extends GeometryTestCase {

  private final CurveGeometryFactory factory = new CurveGeometryFactory();

  public static void main(String[] args) {
    TestRunner.run(CurveShapeWriterThreePointHoleTest.class);
  }

  public CurveShapeWriterThreePointHoleTest(String name) { super(name); }

  /** ISO/IEC 13249-3 closed CircularString ring: five tokens first=last. */
  private CircularString fiveTokenCircle(double r) {
    return (CircularString) factory.createCircularString(
        factory.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(-r, 0), new Coordinate(0, r), new Coordinate(r, 0),
            new Coordinate(0, -r), new Coordinate(-r, 0)
        }));
  }

  /** Open CircularString: odd ≥ 3. Three distinct controls, not closed. */
  private CircularString threePointOpenArc(double r) {
    return (CircularString) factory.createCircularString(
        factory.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(-r, 0), new Coordinate(0, r), new Coordinate(r, 0)
        }));
  }

  private CurvePolygon annulusWithThreePointHole() {
    return factory.createCurvePolygon(fiveTokenCircle(10),
        new LineString[] { threePointOpenArc(3) });
  }

  private static SegCounts segs(Shape s) {
    SegCounts c = new SegCounts();
    double[] coords = new double[6];
    PathIterator it = s.getPathIterator(null);
    while (!it.isDone()) {
      switch (it.currentSegment(coords)) {
        case PathIterator.SEG_MOVETO:  c.moveTo++; break;
        case PathIterator.SEG_LINETO:  c.lineTo++; break;
        case PathIterator.SEG_CUBICTO: c.cubicTo++; break;
        default: break;
      }
      it.next();
    }
    return c;
  }

  /** Type lock: the hole stays CIRCULARSTRING (FCP-H already green). */
  public void testThreePointHoleStaysCircularString() {
    CurvePolygon cp = annulusWithThreePointHole();
    assertEquals(1, cp.getNumInteriorRing());
    LineString hole = cp.getInteriorCurveN(0);
    assertTrue("hole must stay CircularString, got " + hole.getGeometryType(),
        hole instanceof CircularString);
    assertEquals("open CS is three controls; do not invent a 4th/5th",
        3, hole.getNumPoints());
  }

  /**
   * WKT of the hole stays the open triple. Paint must not write a
   * complementary mid back into the geometry (do not invent WKT).
   */
  public void testThreePointHoleWktStaysOpenTriple() {
    LineString hole = annulusWithThreePointHole().getInteriorCurveN(0);
    String wkt = new CurveWKTWriter().write(hole);
    assertTrue("hole WKT must stay CIRCULARSTRING, got " + wkt,
        wkt.toUpperCase().startsWith("CIRCULARSTRING"));
    assertEquals(3, hole.getNumPoints());
  }

  /** Canvas: bezier arcs, not the 3-point chord triangle (lineTo only). */
  public void testThreePointHoleRendersArcsNotChordTriangle() {
    SegCounts c = segs(new CurveShapeWriter().toShape(annulusWithThreePointHole()));
    assertEquals("shell + hole", 2, c.moveTo);
    assertTrue("hole must emit cubic arcs, got cubic=" + c.cubicTo
        + " line=" + c.lineTo, c.cubicTo >= 2);
    assertEquals("chord triangle would be lineTo through the three controls",
        0, c.lineTo);
  }

  /**
   * Fill is the circumcircle hole, not the chord triangle and not the
   * one-arc + closePath segment. Complementary-side interior is punched.
   */
  public void testThreePointHolePunchesCircumcircleNotTriangle() {
    Shape s = new CurveShapeWriter().toShape(annulusWithThreePointHole());
    assertFalse("hole centre must not be filled", s.contains(0, 0));
    assertFalse("complementary side is the other arc, not a chord: (0,-1.5)",
        s.contains(0, -1.5));
    assertFalse("specified-arc side is still a hole", s.contains(0, 1.5));
    assertTrue("annulus body stays filled", s.contains(6, 0));
  }

  /** A single-member CompoundCurve 3-point hole takes the same paint path. */
  public void testCompoundCurveThreePointHoleRendersArcs() {
    CompoundCurve hole = factory.createCompoundCurve(new LineString[] {
        threePointOpenArc(3)
    });
    CurvePolygon cp = factory.createCurvePolygon(fiveTokenCircle(10),
        new LineString[] { hole });
    assertTrue(cp.getInteriorCurveN(0) instanceof CompoundCurve);
    assertEquals(3, cp.getInteriorCurveN(0).getNumPoints());
    Shape s = new CurveShapeWriter().toShape(cp);
    SegCounts c = segs(s);
    assertTrue("compound 3-point hole must emit arcs, cubic=" + c.cubicTo,
        c.cubicTo >= 2);
    assertEquals(0, c.lineTo);
    assertFalse(s.contains(0, 0));
    assertFalse(s.contains(0, -1.5));
    assertTrue(s.contains(6, 0));
  }

  /**
   * Guard: a 13249-3 five-token closed CS hole is already two arcs.
   * Complementary paint close must not fire on that ring.
   */
  public void testFiveTokenClosedHoleUnchanged() {
    CurvePolygon cp = factory.createCurvePolygon(fiveTokenCircle(10),
        new LineString[] { fiveTokenCircle(3) });
    assertEquals(5, cp.getInteriorCurveN(0).getNumPoints());
    Shape s = new CurveShapeWriter().toShape(cp);
    assertFalse(s.contains(0, 0));
    assertFalse(s.contains(0, -1.5));
    assertTrue(s.contains(6, 0));
    SegCounts c = segs(s);
    assertTrue(c.cubicTo >= 2);
    assertEquals(0, c.lineTo);
  }

  /** Guard: a standalone open 3-point CS is one arc, not a full disc. */
  public void testStandaloneThreePointCircularStringIsOpenArc() {
    Shape s = new CurveShapeWriter().toShape(threePointOpenArc(3));
    SegCounts c = segs(s);
    assertTrue("open CS still has an arc", c.cubicTo >= 1);
    assertEquals("no ring close on a standalone CS", 1, c.moveTo);
  }

  private static final class SegCounts {
    int moveTo;
    int lineTo;
    int cubicTo;
  }
}
