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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * F-RD: {@link CurveShapeWriter} must render arc rings of a
 * {@code CurvePolygon}, and curved members of a {@code MultiSurface}, as
 * cubic-bezier segments.
 * <p>
 * {@code toShapeOther} handles CircularString, CompoundCurve and MultiCurve.
 * CurvePolygon and MultiSurface fall through, and because CurvePolygon extends
 * Polygon the inherited {@link org.locationtech.jts.awt.ShapeWriter} renders it
 * happily -- as straight {@code lineTo} chords through the control points. So
 * the failure is silent: a plausible-looking polygon that is visibly not the
 * circle it describes.
 * <p>
 * An earlier attempt at these tests was reverted for casting CircularString to
 * LinearRing, which does not compile -- CircularString extends LineString.
 * That cast was the symptom of the real gap, now closed by FCP-S / FCP-H:
 * arc rings are reachable via {@code getExteriorCurve()} and
 * {@code getInteriorCurveN(n)}.
 */
public class CurveShapeWriterCurvePolygonTest extends GeometryTestCase {

  private static final String ARC_SHELL =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 0 2, -2 0, 0 -2, 2 0))";

  public static void main(String[] args) {
    TestRunner.run(CurveShapeWriterCurvePolygonTest.class);
  }

  public CurveShapeWriterCurvePolygonTest(String name) { super(name); }

  private static SegCounts shapeOf(String wkt) throws Exception {
    Geometry g = new CurveWKTReader().read(wkt);
    return SegCounts.of(new CurveShapeWriter().toShape(g));
  }

  /** An arc shell renders as bezier curves, not straight chords. */
  public void testArcShellRendersAsBezier() throws Exception {
    SegCounts c = shapeOf(ARC_SHELL);
    assertTrue("arc shell should emit cubic segments, got "
        + c.cubicTo + " cubicTo and " + c.lineTo + " lineTo", c.cubicTo >= 1);
  }

  /** The shell contributes exactly one subpath. */
  public void testArcShellIsOneSubpath() throws Exception {
    assertEquals("one MOVETO for the shell", 1, shapeOf(ARC_SHELL).moveTo);
  }

  /** An arc hole renders as bezier curves too, in its own subpath. */
  public void testArcHoleRendersAsBezier() throws Exception {
    SegCounts c = shapeOf("CURVEPOLYGON ("
        + "CIRCULARSTRING (4 0, 0 4, -4 0, 0 -4, 4 0), "
        + "CIRCULARSTRING (1 0, 0 1, -1 0, 0 -1, 1 0))");
    assertEquals("one MOVETO per ring", 2, c.moveTo);
    assertTrue("both rings should emit cubic segments, got " + c.cubicTo,
        c.cubicTo >= 2);
  }

  /** A CompoundCurve shell renders its arc members as bezier segments. */
  public void testCompoundCurveShellRendersArcMember() throws Exception {
    SegCounts c = shapeOf(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 0, 1 1, 2 0), (2 0, 0 0)))");
    assertTrue("arc member should emit cubic segments, got " + c.cubicTo,
        c.cubicTo >= 1);
    assertTrue("straight member should emit a lineTo, got " + c.lineTo,
        c.lineTo >= 1);
  }

  /** Each curved MultiSurface member renders as its own arc subpath. */
  public void testMultiSurfaceRendersCurveMembers() throws Exception {
    SegCounts c = shapeOf("MULTISURFACE ("
        + "CURVEPOLYGON (CIRCULARSTRING (2 0, 0 2, -2 0, 0 -2, 2 0)), "
        + "CURVEPOLYGON (CIRCULARSTRING (12 0, 10 2, 8 0, 10 -2, 12 0)))");
    assertEquals("one MOVETO per member", 2, c.moveTo);
    assertTrue("both members should emit cubic segments, got " + c.cubicTo,
        c.cubicTo >= 2);
  }

  /** Guard: an all-linear CurvePolygon still renders as straight edges. */
  public void testLinearPolygonRendersAsLines() throws Exception {
    SegCounts c = shapeOf("CURVEPOLYGON ((0 0, 4 0, 4 3, 0 3, 0 0))");
    assertEquals("no bezier segments for a linear ring", 0, c.cubicTo);
    assertTrue("should emit straight edges, got " + c.lineTo, c.lineTo >= 3);
  }

  /** Guard: an empty CurvePolygon renders nothing. */
  public void testEmptyRendersNothing() throws Exception {
    SegCounts c = shapeOf("CURVEPOLYGON EMPTY");
    assertEquals(0, c.moveTo);
    assertEquals(0, c.cubicTo);
  }

  // ---------------------------------------------------------------

  private static final class SegCounts {
    int moveTo;
    int lineTo;
    int cubicTo;

    static SegCounts of(Shape s) {
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
  }
}
