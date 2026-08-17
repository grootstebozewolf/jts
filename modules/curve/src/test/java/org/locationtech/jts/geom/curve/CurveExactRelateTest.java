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
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.IntersectionMatrix;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * R-PR first cell: DE-9IM of a circular disc vs a Point (or a MultiPoint
 * whose members share one location class). Matrices are JTS's spelling
 * for {@code area.relate(point)}, probed against a plain square; the
 * reverse is the transpose.
 */
public class CurveExactRelateTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String SQUARE =
      "POLYGON ((-5 -5, 5 -5, 5 5, -5 5, -5 -5))";

  /** JTS {@code polygon.relate(point)} -- not the reverse. */
  private static final String IM_IN = CurveExact.IM_POINT_INTERIOR;
  private static final String IM_ON = CurveExact.IM_POINT_BOUNDARY;
  private static final String IM_OUT = CurveExact.IM_POINT_EXTERIOR;

  public static void main(String[] args) {
    TestRunner.run(CurveExactRelateTest.class);
  }

  public CurveExactRelateTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testInteriorMatrix() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry p = readCurve("POINT (3 3)");
    Geometry diamond = readCurve("POLYGON ((-5 0, 0 5, 5 0, 0 -5, -5 0))");
    assertEquals(IM_IN, CurveExact.relate(disc, p).toString());
    assertEquals(IM_IN, disc.relate(p).toString());
    assertTrue(disc.relate(p).isContains());
    assertTrue(disc.relate(p, "T*****FF*"));
    assertTrue(disc.contains(p));
    // Control diamond is the lie: (3 3) is exterior to |x|+|y|<=5.
    assertEquals(IM_OUT, diamond.relate(p).toString());
  }

  public void testBoundaryMatrix() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry p = readCurve("POINT (5 0)");
    assertEquals(IM_ON, CurveExact.relate(disc, p).toString());
    assertEquals(IM_ON, disc.relate(p).toString());
    assertFalse(disc.relate(p).isContains());
    assertTrue(disc.relate(p).isCovers());
    assertTrue(disc.relate(p, IM_ON));
  }

  public void testExteriorMatrix() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry p = readCurve("POINT (6 0)");
    assertEquals(IM_OUT, CurveExact.relate(disc, p).toString());
    assertEquals(IM_OUT, disc.relate(p).toString());
    assertTrue(disc.relate(p).isDisjoint());
    assertFalse(disc.relate(p).isIntersects());
  }

  public void testReverseMatchesJtsTranspose() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry in = readCurve("POINT (3 3)");
    Geometry on = readCurve("POINT (5 0)");
    Geometry out = readCurve("POINT (6 0)");
    assertEquals(IntersectionMatrix.transpose(IM_IN), in.relate(disc).toString());
    assertEquals(IntersectionMatrix.transpose(IM_ON), on.relate(disc).toString());
    assertEquals(IntersectionMatrix.transpose(IM_OUT), out.relate(disc).toString());
    // JTS spelling of the reverse, locked against a plain square.
    Geometry square = readCurve(SQUARE);
    assertEquals(square.relate(readCurve("POINT (0 0)")).transpose().toString(),
        in.relate(disc).toString());
    assertEquals("0FFFFF212", in.relate(disc).toString());
    assertEquals("F0FFFF212", on.relate(disc).toString());
    assertEquals("FF0FFF212", out.relate(disc).toString());
  }

  public void testUniformMultiPoint() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    assertEquals(IM_IN, disc.relate(readCurve("MULTIPOINT ((3 3), (0 0))")).toString());
    assertEquals(IM_ON, disc.relate(readCurve("MULTIPOINT ((5 0), (0 5))")).toString());
    assertEquals(IM_OUT, disc.relate(readCurve("MULTIPOINT ((6 0), (10 10))")).toString());
  }

  public void testMixedMultiPointReturnsNull() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry mixed = readCurve("MULTIPOINT ((3 3), (5 0))");
    assertNull("mixed interior+boundary is not this cell",
        CurveExact.relate(disc, mixed));
  }

  public void testHalfDiscPointIsR2NotNull() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry p = readCurve("POINT (3 3)");
    assertEquals(IM_IN, CurveExact.relate(half, p).toString());
  }

  /**
   * Deep interior and far exterior agree with densify-then-core.
   * The bulge point is not compared to linearise: the control diamond
   * is a known false-negative.
   */
  public void testParityWhereChainsawAgrees() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry centre = readCurve("POINT (0 0)");
    Geometry far = readCurve("POINT (100 100)");
    Geometry chord = CurveOps.linearise(disc);
    assertEquals(chord.relate(centre).toString(), disc.relate(centre).toString());
    assertEquals(chord.relate(far).toString(), disc.relate(far).toString());
  }
}
