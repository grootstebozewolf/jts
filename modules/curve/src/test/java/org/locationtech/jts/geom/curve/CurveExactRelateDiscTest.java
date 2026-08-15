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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.IntersectionMatrix;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * R-PR-AA: DE-9IM of two circular discs. Matrices are JTS's spelling
 * for {@code area.relate(area)}, probed against two plain polygons
 * (or vertex-touch squares where that is the class). Circles cannot
 * share an edge, so an edge-sharing square is the wrong tangent probe.
 * Do not assert equals against {@code linearise(discA).relate(linearise(discB))}
 * for the crossing pair -- the inscribed diamonds miss the lens.
 */
public class CurveExactRelateDiscTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  private static final String CIRCLE_3 =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";
  private static final String CIRCLE_FAR =
      "CURVEPOLYGON (CIRCULARSTRING (100 0, 105 5, 110 0, 105 -5, 100 0))";
  private static final String CIRCLE_EXT_TAN =
      "CURVEPOLYGON (CIRCULARSTRING (5 0, 10 5, 15 0, 10 -5, 5 0))";
  private static final String CIRCLE_INT_TAN =
      "CURVEPOLYGON (CIRCULARSTRING (-1 0, 2 3, 5 0, 2 -3, -1 0))";
  private static final String CIRCLE_NESTED_OFFSET =
      "CURVEPOLYGON (CIRCULARSTRING (-1 0, 1 2, 3 0, 1 -2, -1 0))";
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";

  private static final String IM_OUT = CurveExact.IM_AREA_DISJOINT;
  private static final String IM_COVERS = CurveExact.IM_AREA_COVERS;
  private static final String IM_INSIDE = CurveExact.IM_AREA_COVEREDBY;
  private static final String IM_OVER = CurveExact.IM_AREA_OVERLAP;
  private static final String IM_EXT = CurveExact.IM_AREA_EXT_TANGENT;
  private static final String IM_INT = CurveExact.IM_AREA_INT_TANGENT;
  private static final String IM_EQ = CurveExact.IM_AREA_EQUAL;

  public static void main(String[] args) {
    TestRunner.run(CurveExactRelateDiscTest.class);
  }

  public CurveExactRelateDiscTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  /**
   * Lock the five location classes against plain polygons in jts-core.
   * Crossing / disjoint / nested use overlapping, far, and nested squares.
   * External tangent uses squares that share one vertex. Internal tangent
   * uses a square covering a diamond that meets the square at one boundary
   * point -- not an edge-sharing pair ({@code BB=1}).
   */
  public void testJtsSpellingLockedOnPlainPolygons() {
    Geometry overA = read("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))");
    Geometry overB = read("POLYGON ((2 2, 6 2, 6 6, 2 6, 2 2))");
    assertEquals(IM_OVER, overA.relate(overB).toString());
    assertEquals(IM_OVER, overB.relate(overA).toString());

    Geometry far = read("POLYGON ((20 20, 24 20, 24 24, 20 24, 20 20))");
    assertEquals(IM_OUT, overA.relate(far).toString());
    assertEquals(IM_OUT, far.relate(overA).toString());

    Geometry inner = read("POLYGON ((1 1, 2 1, 2 2, 1 2, 1 1))");
    assertEquals(IM_COVERS, overA.relate(inner).toString());
    assertEquals(IM_INSIDE, inner.relate(overA).toString());

    Geometry left = read("POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0))");
    Geometry right = read("POLYGON ((2 2, 4 2, 4 4, 2 4, 2 2))");
    assertEquals(IM_EXT, left.relate(right).toString());
    assertEquals(IM_EXT, right.relate(left).toString());

    Geometry outer = read("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))");
    Geometry touchIn = read("POLYGON ((2 0, 3 1, 2 2, 1 1, 2 0))");
    assertEquals(IM_INT, outer.relate(touchIn).toString());
    assertEquals(IntersectionMatrix.transpose(IM_INT),
        touchIn.relate(outer).toString());
  }

  public void testCrossingOverlaps() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_CROSSING);
    assertEquals(IM_OVER, CurveExact.relate(a, b).toString());
    assertEquals(IM_OVER, a.relate(b).toString());
    assertEquals(IM_OVER, b.relate(a).toString());
    assertTrue(a.overlaps(b));
    assertTrue(b.overlaps(a));
    assertTrue(a.intersects(b));
    assertFalse(a.contains(b));
    assertFalse(a.covers(b));
    Geometry diamondA = read("POLYGON ((-5 0, 0 5, 5 0, 0 -5, -5 0))");
    Geometry diamondB = read("POLYGON ((2 0, 7 5, 12 0, 7 -5, 2 0))");
    assertFalse("inscribed diamonds miss the lens", diamondA.overlaps(diamondB));
  }

  public void testDisjointEnvelopeMiss() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry far = readCurve(CIRCLE_FAR);
    assertEquals(IM_OUT, CurveExact.relate(a, far).toString());
    assertEquals(IM_OUT, a.relate(far).toString());
    assertEquals(IM_OUT, far.relate(a).toString());
    assertFalse(a.intersects(far));
    assertFalse(a.overlaps(far));
  }

  public void testNestedConcentricCovers() throws Exception {
    Geometry big = readCurve(CIRCLE_5);
    Geometry small = readCurve(CIRCLE_3);
    assertEquals(IM_COVERS, CurveExact.relate(big, small).toString());
    assertEquals(IM_COVERS, big.relate(small).toString());
    assertEquals(IM_INSIDE, small.relate(big).toString());
    assertTrue(big.contains(small));
    assertTrue(big.covers(small));
    assertTrue(small.within(big));
    assertTrue(small.coveredBy(big));
    assertFalse(big.overlaps(small));
  }

  public void testNestedOffsetCovers() throws Exception {
    Geometry big = readCurve(CIRCLE_5);
    Geometry inner = readCurve(CIRCLE_NESTED_OFFSET);
    assertEquals(IM_COVERS, big.relate(inner).toString());
    assertEquals(IM_INSIDE, inner.relate(big).toString());
    assertTrue(big.contains(inner));
    assertTrue(big.covers(inner));
  }

  public void testExternalTangent() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_EXT_TAN);
    assertEquals(IM_EXT, CurveExact.relate(a, b).toString());
    assertEquals(IM_EXT, a.relate(b).toString());
    assertEquals(IM_EXT, b.relate(a).toString());
    assertTrue(a.intersects(b));
    assertTrue(a.touches(b));
    assertFalse(a.overlaps(b));
    assertFalse(a.contains(b));
    assertFalse(a.covers(b));
  }

  public void testInternalTangent() throws Exception {
    Geometry big = readCurve(CIRCLE_5);
    Geometry small = readCurve(CIRCLE_INT_TAN);
    assertEquals(IM_INT, CurveExact.relate(big, small).toString());
    assertEquals(IM_INT, big.relate(small).toString());
    assertEquals(IntersectionMatrix.transpose(IM_INT),
        small.relate(big).toString());
    assertTrue(big.intersects(small));
    assertTrue(big.covers(small));
    assertFalse(big.contains(small));
    assertFalse(big.overlaps(small));
    assertTrue(small.coveredBy(big));
    assertFalse(small.covers(big));
  }

  public void testEqualDiscs() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_5);
    assertEquals(IM_EQ, CurveExact.relate(a, b).toString());
    assertEquals(IM_EQ, a.relate(b).toString());
    assertTrue(a.covers(b));
    assertTrue(a.coveredBy(b));
    assertFalse(a.overlaps(b));
  }

  public void testCrossingNodesAreTheLockedPair() {
    CircularArcDensifier.Circle a = new CircularArcDensifier.Circle(0, 0, 5);
    CircularArcDensifier.Circle b = new CircularArcDensifier.Circle(7, 0, 5);
    Coordinate[] pts = CircularArcDensifier.intersectCircles(a, b);
    assertEquals(2, pts.length);
    double y = Math.sqrt(12.75);
    boolean plus = false;
    boolean minus = false;
    for (int i = 0; i < pts.length; i++) {
      if (Math.abs(pts[i].x - 3.5) < 1.0e-12
          && Math.abs(pts[i].y - y) < 1.0e-12) {
        plus = true;
      }
      if (Math.abs(pts[i].x - 3.5) < 1.0e-12
          && Math.abs(pts[i].y + y) < 1.0e-12) {
        minus = true;
      }
    }
    assertTrue("node (3.5, +√12.75)", plus);
    assertTrue("node (3.5, -√12.75)", minus);
  }

  public void testHalfDiscReturnsNull() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_5);
    assertNull(CurveExact.relate(half, disc));
    assertNull(CurveExact.relate(disc, half));
  }
}
