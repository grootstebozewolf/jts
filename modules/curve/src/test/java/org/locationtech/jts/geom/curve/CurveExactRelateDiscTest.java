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
  private static final String CIRCLE_5_ROTATED =
      "CURVEPOLYGON (CIRCULARSTRING (0 5, 5 0, 0 -5, -5 0, 0 5))";
  private static final String MULTI_5 = "MULTISURFACE (" + CIRCLE_5 + ")";
  private static final String MULTI_CROSS = "MULTISURFACE (" + CIRCLE_CROSSING + ")";
  private static final String MULTI_FAR = "MULTISURFACE (" + CIRCLE_FAR + ")";
  private static final String MULTI_3 = "MULTISURFACE (" + CIRCLE_3 + ")";
  private static final String MULTI_EXT = "MULTISURFACE (" + CIRCLE_EXT_TAN + ")";
  private static final String MULTI_INT = "MULTISURFACE (" + CIRCLE_INT_TAN + ")";
  private static final String MULTI_TWO =
      "MULTISURFACE (" + CIRCLE_5 + ", " + CIRCLE_FAR + ")";

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
    assertSfs(a, b, true, false, true, false, false, false);
    assertSfs(b, a, true, false, true, false, false, false);
    // Do not assert equals against linearise(a).relate(linearise(b)):
    // a coarser inscription can miss the lens even when the discs overlap.
  }

  public void testDisjointEnvelopeMiss() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry far = readCurve(CIRCLE_FAR);
    assertEquals(IM_OUT, CurveExact.relate(a, far).toString());
    assertEquals(IM_OUT, a.relate(far).toString());
    assertEquals(IM_OUT, far.relate(a).toString());
    assertSfs(a, far, false, false, false, false, false, false);
    assertSfs(far, a, false, false, false, false, false, false);
  }

  public void testNestedConcentricCovers() throws Exception {
    Geometry big = readCurve(CIRCLE_5);
    Geometry small = readCurve(CIRCLE_3);
    assertEquals(IM_COVERS, CurveExact.relate(big, small).toString());
    assertEquals(IM_COVERS, big.relate(small).toString());
    assertEquals(IM_INSIDE, small.relate(big).toString());
    assertSfs(big, small, true, false, false, true, true, false);
    assertSfs(small, big, true, false, false, false, false, false);
    assertTrue(small.within(big));
    assertTrue(small.coveredBy(big));
    assertFalse(big.within(small));
  }

  public void testNestedOffsetCovers() throws Exception {
    Geometry big = readCurve(CIRCLE_5);
    Geometry inner = readCurve(CIRCLE_NESTED_OFFSET);
    assertEquals(IM_COVERS, big.relate(inner).toString());
    assertEquals(IM_INSIDE, inner.relate(big).toString());
    assertSfs(big, inner, true, false, false, true, true, false);
    assertTrue(inner.within(big));
    assertTrue(inner.coveredBy(big));
  }

  /**
   * Axis-aligned pair kisses at the shared control {@code (5, 0)}.
   * R.1 ({@link CurveExactRelateTouchTest}) pins the same matrix on a
   * 3-4-5 pair whose kiss is not a control vertex.
   */
  public void testExternalTangent() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_EXT_TAN);
    assertEquals(IM_EXT, CurveExact.relate(a, b).toString());
    assertEquals(IM_EXT, a.relate(b).toString());
    assertEquals(IM_EXT, b.relate(a).toString());
    assertSfs(a, b, true, true, false, false, false, false);
    assertSfs(b, a, true, true, false, false, false, false);
  }

  public void testInternalTangent() throws Exception {
    Geometry big = readCurve(CIRCLE_5);
    Geometry small = readCurve(CIRCLE_INT_TAN);
    assertEquals(IM_INT, CurveExact.relate(big, small).toString());
    assertEquals(IM_INT, big.relate(small).toString());
    assertEquals(IntersectionMatrix.transpose(IM_INT),
        small.relate(big).toString());
    assertSfs(big, small, true, false, false, true, true, false);
    assertSfs(small, big, true, false, false, false, false, false);
    assertTrue(small.coveredBy(big));
    assertFalse(small.covers(big));
  }

  public void testEqualDiscs() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_5);
    assertEquals(IM_EQ, CurveExact.relate(a, b).toString());
    assertEquals(IM_EQ, a.relate(b).toString());
    assertSfs(a, b, true, false, false, true, true, true);
    assertSfs(b, a, true, false, false, true, true, true);
    assertTrue(a.coveredBy(b));
    assertTrue(b.covers(a));
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

  public void testHalfDiscSameCircleIsCoveredByForm() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_5);
    assertEquals("2FF11F212", CurveExact.relate(half, disc).toString());
    assertEquals("212F11FF2", CurveExact.relate(disc, half).toString());
  }

  /**
   * Same circle, controls rotated by one vertex. {@code circularDisc}
   * recovers the same centre and radius; relate is equal, not a
   * linearise of two different inscriptions.
   */
  public void testRotatedControlsAreEqual() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_5_ROTATED);
    assertEquals(IM_EQ, CurveExact.relate(a, b).toString());
    assertEquals(IM_EQ, a.relate(b).toString());
    assertEquals(IM_EQ, b.relate(a).toString());
    assertSfs(a, b, true, false, false, true, true, true);
    assertSfs(b, a, true, false, false, true, true, true);
  }

  /**
   * {@code circularDisc} unwraps a single-member MultiSurface. The five
   * location classes are the same matrices already locked above.
   */
  public void testSingleMemberMultiSurfaceSameFiveMatrices() throws Exception {
    lockMulti(MULTI_5, CIRCLE_CROSSING, MULTI_CROSS, IM_OVER);
    lockMulti(MULTI_5, CIRCLE_FAR, MULTI_FAR, IM_OUT);
    lockMulti(MULTI_5, CIRCLE_3, MULTI_3, IM_COVERS);
    lockMulti(MULTI_5, CIRCLE_EXT_TAN, MULTI_EXT, IM_EXT);
    lockMulti(MULTI_5, CIRCLE_INT_TAN, MULTI_INT, IM_INT);
  }

  public void testMultiMemberMultiSurfaceReturnsNull() throws Exception {
    Geometry two = readCurve(MULTI_TWO);
    Geometry disc = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertNull(CurveExact.relate(two, disc));
    assertNull(CurveExact.relate(disc, two));
    assertNull(CurveExact.relate(two, cross));
  }

  public void testTwoAreasNeverCross() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry[] others = {
        readCurve(CIRCLE_CROSSING),
        readCurve(CIRCLE_FAR),
        readCurve(CIRCLE_3),
        readCurve(CIRCLE_EXT_TAN),
        readCurve(CIRCLE_INT_TAN),
        readCurve(CIRCLE_5),
        readCurve(CIRCLE_5_ROTATED),
        readCurve(MULTI_5)
    };
    for (int i = 0; i < others.length; i++) {
      assertFalse("two areas never cross: " + others[i].getGeometryType(),
          a.crosses(others[i]));
      assertFalse(others[i].crosses(a));
    }
  }

  private void lockMulti(String multiA, String bWkt, String multiB, String im)
      throws Exception {
    Geometry ma = readCurve(multiA);
    Geometry b = readCurve(bWkt);
    Geometry mb = readCurve(multiB);
    assertEquals(im, CurveExact.relate(ma, b).toString());
    assertEquals(im, ma.relate(b).toString());
    assertEquals(im, CurveExact.relate(ma, mb).toString());
    assertEquals(im, ma.relate(mb).toString());
  }

  /**
   * SFS predicates from the locked matrix. {@code crosses} of two areas
   * is always false. {@code equals} is {@code equalsTopo}.
   */
  private void assertSfs(Geometry a, Geometry b, boolean intersects,
      boolean touches, boolean overlaps, boolean contains, boolean covers,
      boolean equals) {
    assertEquals("intersects", intersects, a.intersects(b));
    assertEquals("touches", touches, a.touches(b));
    assertEquals("overlaps", overlaps, a.overlaps(b));
    assertEquals("contains", contains, a.contains(b));
    assertEquals("covers", covers, a.covers(b));
    assertEquals("equalsTopo", equals, a.equalsTopo(b));
    assertFalse("crosses of two areas", a.crosses(b));
  }
}
