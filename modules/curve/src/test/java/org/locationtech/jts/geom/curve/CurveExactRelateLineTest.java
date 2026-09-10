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
 * R-PR second cell: DE-9IM of a circular disc vs a LineString (or a
 * single-member MultiLineString). Matrices are JTS's spelling for
 * {@code area.relate(line)}, probed against the inscribed diamond of
 * {@code CIRCLE_5}; the reverse is the transpose.
 */
public class CurveExactRelateLineTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String DIAMOND =
      "POLYGON ((-5 0, 0 5, 5 0, 0 -5, -5 0))";

  private static final String CROSS = "LINESTRING (-10 0, 10 0)";
  private static final String TANGENT = "LINESTRING (-10 5, 10 5)";
  private static final String MISS = "LINESTRING (-10 6, 10 6)";
  private static final String END_IN = "LINESTRING (0 0, 10 0)";

  private static final String IM_CROSS = CurveExact.IM_LINE_CROSS;
  private static final String IM_TAN = CurveExact.IM_LINE_TANGENT;
  private static final String IM_OUT = CurveExact.IM_LINE_EXTERIOR;
  private static final String IM_END = CurveExact.IM_LINE_END_INTERIOR;

  public static void main(String[] args) {
    TestRunner.run(CurveExactRelateLineTest.class);
  }

  public CurveExactRelateLineTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testCrossingMatrixMatchesJtsDiamond() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry line = readCurve(CROSS);
    Geometry diamond = readCurve(DIAMOND);
    assertEquals(IM_CROSS, diamond.relate(line).toString());
    assertEquals(IM_CROSS, CurveExact.relate(disc, line).toString());
    assertEquals(IM_CROSS, disc.relate(line).toString());
    assertTrue(disc.intersects(line));
    assertTrue(disc.relate(line).isCrosses(2, 1));
  }

  public void testSecantSameAsCrossing() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry line = readCurve("LINESTRING (-8 3, 8 3)");
    assertEquals(IM_CROSS, disc.relate(line).toString());
    assertTrue(disc.intersects(line));
  }

  public void testTangentMatrixMatchesJtsDiamond() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry line = readCurve(TANGENT);
    Geometry diamond = readCurve(DIAMOND);
    assertEquals(IM_TAN, diamond.relate(line).toString());
    assertEquals(IM_TAN, disc.relate(line).toString());
    assertTrue(disc.intersects(line));
    assertTrue(disc.relate(line).isTouches(2, 1));
    assertFalse(disc.relate(line).isCrosses(2, 1));
  }

  public void testMissMatrixMatchesJtsDiamond() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry line = readCurve(MISS);
    Geometry diamond = readCurve(DIAMOND);
    assertEquals(IM_OUT, diamond.relate(line).toString());
    assertEquals(IM_OUT, disc.relate(line).toString());
    assertFalse(disc.intersects(line));
    assertTrue(disc.relate(line).isDisjoint());
  }

  public void testEnvelopeMiss() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry line = readCurve("LINESTRING (100 100, 110 100)");
    assertEquals(IM_OUT, disc.relate(line).toString());
    assertFalse(disc.intersects(line));
  }

  public void testEndpointInteriorMatrixMatchesJtsDiamond() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry line = readCurve(END_IN);
    Geometry diamond = readCurve(DIAMOND);
    assertEquals(IM_END, diamond.relate(line).toString());
    assertEquals(IM_END, disc.relate(line).toString());
    assertTrue(disc.intersects(line));
  }

  public void testReverseMatchesJtsTranspose() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CROSS);
    Geometry tan = readCurve(TANGENT);
    Geometry miss = readCurve(MISS);
    Geometry endIn = readCurve(END_IN);
    assertEquals(IntersectionMatrix.transpose(IM_CROSS),
        cross.relate(disc).toString());
    assertEquals(IntersectionMatrix.transpose(IM_TAN),
        tan.relate(disc).toString());
    assertEquals(IntersectionMatrix.transpose(IM_OUT),
        miss.relate(disc).toString());
    assertEquals(IntersectionMatrix.transpose(IM_END),
        endIn.relate(disc).toString());
    assertEquals("101FF0212", cross.relate(disc).toString());
    assertEquals("F01FF0212", tan.relate(disc).toString());
    assertEquals("FF1FF0212", miss.relate(disc).toString());
    assertTrue(cross.intersects(disc));
    assertTrue(tan.intersects(disc));
    assertFalse(miss.intersects(disc));
  }

  public void testSingleMemberMultiLineString() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry multi = readCurve("MULTILINESTRING ((-10 0, 10 0))");
    assertEquals(IM_CROSS, CurveExact.relate(disc, multi).toString());
    assertEquals(IM_CROSS, disc.relate(multi).toString());
    assertTrue(disc.intersects(multi));
  }

  public void testMultiMemberMultiLineStringReturnsNull() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry multi = readCurve("MULTILINESTRING ((-10 0, 10 0), (0 -10, 0 10))");
    assertNull("two members are not this cell", CurveExact.relate(disc, multi));
  }

  public void testHalfDiscLineDiameterExtentIsBoundaryRun() throws Exception {
    // CROSS lies on the diameter line — BI=1 run, not the disc secant II=1.
    Geometry half = readCurve(HALF_DISC);
    Geometry line = readCurve(CROSS);
    assertEquals("FF21F1102", CurveExact.relate(half, line).toString());
  }

  /**
   * 45° tangent hits the bulge: the control diamond misses, and so does
   * an inscribed linearise. Do not assert equals against either.
   */
  public void testBulgeTangentIsNotTheDiamond() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry diamond = readCurve(DIAMOND);
    double t = 5.0 * Math.sqrt(2.0);
    Geometry line = readCurve("LINESTRING (0 " + t + ", " + t + " 0)");
    assertEquals(IM_TAN, disc.relate(line).toString());
    assertTrue(disc.intersects(line));
    assertFalse("control diamond is the lie at 45°", diamond.intersects(line));
    assertFalse("inscribed chords miss the true tangent",
        CurveOps.linearise(disc).intersects(line));
  }

  public void testDiameterAndInteriorStayOnThePrimitive() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry diamond = readCurve(DIAMOND);
    Geometry diameter = readCurve("LINESTRING (-5 0, 5 0)");
    Geometry inside = readCurve("LINESTRING (-1 0, 1 0)");
    assertEquals(diamond.relate(diameter).toString(),
        disc.relate(diameter).toString());
    assertEquals(diamond.relate(inside).toString(),
        disc.relate(inside).toString());
    assertEquals("1F2F01FF2", disc.relate(diameter).toString());
    assertEquals("102FF1FF2", disc.relate(inside).toString());
  }

  /**
   * Deep crossing and a far miss agree with densify-then-core. The bulge
   * tangent is not compared to linearise.
   */
  public void testParityWhereChainsawAgrees() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CROSS);
    Geometry miss = readCurve("LINESTRING (100 100, 110 100)");
    Geometry chord = CurveOps.linearise(disc);
    assertEquals(chord.relate(cross).toString(), disc.relate(cross).toString());
    assertEquals(chord.relate(miss).toString(), disc.relate(miss).toString());
  }
}
