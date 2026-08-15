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
 * R-PR third cell: DE-9IM of a circular disc vs a plain Polygon.
 * Matrices are JTS's spelling for {@code area.relate(poly)}, probed
 * against the inscribed diamond of {@code CIRCLE_5}; the reverse is
 * the transpose.
 */
public class CurveExactRelatePolyTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String DIAMOND =
      "POLYGON ((-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String FAR =
      "POLYGON ((100 100, 110 100, 110 110, 100 110, 100 100))";
  private static final String NEAR_MISS =
      "POLYGON ((6 -1, 10 -1, 10 1, 6 1, 6 -1))";
  private static final String NESTED =
      "POLYGON ((-1 -1, 1 -1, 1 1, -1 1, -1 -1))";
  private static final String BIG_SQUARE =
      "POLYGON ((-6 -6, 6 -6, 6 6, -6 6, -6 -6))";
  private static final String HALF_PLANE =
      "POLYGON ((0 -6, 10 -6, 10 6, 0 6, 0 -6))";

  private static final String IM_OUT = CurveExact.IM_AREA_DISJOINT;
  private static final String IM_COVERS = CurveExact.IM_AREA_COVERS;
  private static final String IM_INSIDE = CurveExact.IM_AREA_COVEREDBY;
  private static final String IM_OVER = CurveExact.IM_AREA_OVERLAP;

  public static void main(String[] args) {
    TestRunner.run(CurveExactRelatePolyTest.class);
  }

  public CurveExactRelatePolyTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testDisjointEnvelopeMiss() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry far = readCurve(FAR);
    Geometry diamond = readCurve(DIAMOND);
    assertEquals(IM_OUT, diamond.relate(far).toString());
    assertEquals(IM_OUT, CurveExact.relate(disc, far).toString());
    assertEquals(IM_OUT, disc.relate(far).toString());
    assertFalse(disc.intersects(far));
  }

  public void testDisjointNearMiss() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry near = readCurve(NEAR_MISS);
    Geometry diamond = readCurve(DIAMOND);
    assertEquals(IM_OUT, diamond.relate(near).toString());
    assertEquals(IM_OUT, disc.relate(near).toString());
    assertFalse(disc.intersects(near));
  }

  public void testNestedCoversMatchesJtsDiamond() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry inner = readCurve(NESTED);
    Geometry diamond = readCurve(DIAMOND);
    assertEquals(IM_COVERS, diamond.relate(inner).toString());
    assertEquals(IM_COVERS, CurveExact.relate(disc, inner).toString());
    assertEquals(IM_COVERS, disc.relate(inner).toString());
    assertTrue(disc.contains(inner));
    assertTrue(disc.covers(inner));
    assertTrue(disc.relate(inner).isContains());
    assertTrue(disc.relate(inner).isCovers());
  }

  public void testDiscInsideSquareMatchesJts() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry square = readCurve(BIG_SQUARE);
    Geometry diamond = readCurve(DIAMOND);
    assertEquals(IM_INSIDE, diamond.relate(square).toString());
    assertEquals(IM_INSIDE, disc.relate(square).toString());
    assertTrue(disc.within(square));
    assertTrue(disc.coveredBy(square));
    assertTrue(square.contains(disc));
    assertTrue(square.covers(disc));
  }

  public void testCrossingHalfPlaneMatchesJts() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry cut = readCurve(HALF_PLANE);
    Geometry diamond = readCurve(DIAMOND);
    assertEquals(IM_OVER, diamond.relate(cut).toString());
    assertEquals(IM_OVER, CurveExact.relate(disc, cut).toString());
    assertEquals(IM_OVER, disc.relate(cut).toString());
    assertTrue(disc.intersects(cut));
    assertTrue(disc.overlaps(cut));
    assertFalse(disc.contains(cut));
    assertFalse(disc.covers(cut));
  }

  public void testReverseMatchesJtsTranspose() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry far = readCurve(FAR);
    Geometry inner = readCurve(NESTED);
    Geometry square = readCurve(BIG_SQUARE);
    Geometry cut = readCurve(HALF_PLANE);
    assertEquals(IntersectionMatrix.transpose(IM_OUT), far.relate(disc).toString());
    assertEquals(IntersectionMatrix.transpose(IM_COVERS),
        inner.relate(disc).toString());
    assertEquals(IntersectionMatrix.transpose(IM_INSIDE),
        square.relate(disc).toString());
    assertEquals(IntersectionMatrix.transpose(IM_OVER),
        cut.relate(disc).toString());
    assertEquals("2FF1FF212", inner.relate(disc).toString());
    assertEquals("212FF1FF2", square.relate(disc).toString());
    assertEquals("212101212", cut.relate(disc).toString());
  }

  public void testHoledPolygonReturnsNull() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry holed = readCurve(
        "POLYGON ((-6 -6, 6 -6, 6 6, -6 6, -6 -6), (-1 -1, -1 1, 1 1, 1 -1, -1 -1))");
    assertNull("holes are not this cell", CurveExact.relate(disc, holed));
  }

  public void testNotADiscReturnsNull() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry inner = readCurve(NESTED);
    assertNull(CurveExact.relate(half, inner));
  }

  /**
   * A triangle that lives in the bulge (outside the control diamond,
   * inside the true disc) and then escapes. The diamond is disjoint;
   * do not assert equals against linearise.
   */
  public void testBulgeCrossingIsNotTheDiamond() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry diamond = readCurve(DIAMOND);
    Geometry bulge = readCurve("POLYGON ((3.3 3.3, 5 3.3, 5 5, 3.3 3.3))");
    assertEquals(IM_OVER, disc.relate(bulge).toString());
    assertTrue(disc.intersects(bulge));
    assertEquals(IM_OUT, diamond.relate(bulge).toString());
    assertFalse("control diamond is the lie in the bulge",
        diamond.intersects(bulge));
  }

  public void testParityWhereChainsawAgrees() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry inner = readCurve(NESTED);
    Geometry far = readCurve(FAR);
    Geometry chord = CurveOps.linearise(disc);
    assertEquals(chord.relate(inner).toString(), disc.relate(inner).toString());
    assertEquals(chord.relate(far).toString(), disc.relate(far).toString());
  }
}
