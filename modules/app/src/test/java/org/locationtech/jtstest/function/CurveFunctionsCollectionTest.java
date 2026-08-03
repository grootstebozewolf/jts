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
package org.locationtech.jtstest.function;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * LIN-COLL: linearising a collection must not change its type.
 * <p>
 * {@code CurveFunctions.linearize} walks collections recursively so a curve
 * nested inside one is still found. It rebuilt every collection with
 * {@code createGeometryCollection}, though, which downgrades the type whether or
 * not anything was curved: {@code MULTIPOLYGON}, {@code MULTILINESTRING} and
 * {@code MULTIPOINT} all came back as {@code GEOMETRYCOLLECTION}.
 * <p>
 * <b>This is a regression, not a latent wart.</b> Since {@code HullFunctions}
 * began routing every static entry point through {@code arcAware} (the H-CC
 * green), {@code concaveHullPolygons} and {@code concaveFill} throw
 * {@code IllegalArgumentException: Input must be polygonal} on a plain
 * {@code MULTIPOLYGON} -- input with no curve in it at all, which worked before.
 * {@code ConcaveHullOfPolygons} checks for polygonal input and a
 * GeometryCollection does not qualify. Those two tests are the regression;
 * the type-preservation tests are the cause.
 * <p>
 * {@code MultiSurface} and {@code MultiCurve} are unaffected because they
 * implement {@code Linearizable} and so never reach the collection branch; they
 * are asserted here as guards.
 * <p>
 * The strongest form of the fix is identity, not merely type equality: a
 * geometry with no curve anywhere in it should come back as the same object, so
 * every writer and every static entry point is provably handed exactly what the
 * caller passed. {@link #testNonCurveCollectionIsTheSameObject()} asserts that.
 */
public class CurveFunctionsCollectionTest extends TestCase {

  private static final String MULTIPOLYGON =
      "MULTIPOLYGON (((0 0, 4 0, 4 4, 0 4, 0 0)), ((6 0, 10 0, 10 4, 6 4, 6 0)))";

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CurveFunctionsCollectionTest.class); }
  public CurveFunctionsCollectionTest(String name) { super(name); }

  private static Geometry read(String wkt) throws Exception {
    return new CurvedWKTReader(new CurvedGeometryFactory()).read(wkt);
  }

  private static Geometry linearized(String wkt) throws Exception {
    return CurveFunctions.linearizeForOps(read(wkt));
  }

  public void testMultiPolygonTypePreserved() throws Exception {
    assertEquals("MultiPolygon", linearized(MULTIPOLYGON).getGeometryType());
  }

  public void testMultiLineStringTypePreserved() throws Exception {
    assertEquals("MultiLineString",
        linearized("MULTILINESTRING ((0 0, 4 0), (6 0, 10 0))").getGeometryType());
  }

  public void testMultiPointTypePreserved() throws Exception {
    assertEquals("MultiPoint",
        linearized("MULTIPOINT ((0 0), (1 1))").getGeometryType());
  }

  public void testGeometryCollectionStaysGeometryCollection() throws Exception {
    assertEquals("GeometryCollection",
        linearized("GEOMETRYCOLLECTION (POINT (0 0), LINESTRING (1 1, 2 2))")
            .getGeometryType());
  }

  /** Nothing curved anywhere means nothing to do, so hand back the input. */
  public void testNonCurveCollectionIsTheSameObject() throws Exception {
    Geometry in = read(MULTIPOLYGON);
    assertSame("a curve-free collection should be returned unchanged",
        in, CurveFunctions.linearizeForOps(in));
  }

  /** The user-visible regression: plain polygonal input must still be accepted. */
  public void testConcaveHullPolygonsAcceptsMultiPolygon() throws Exception {
    Geometry hull = HullFunctions.concaveHullPolygons(read(MULTIPOLYGON), 5.0);
    assertTrue("hull of two 4x4 squares should have area of at least their 32, got "
        + hull.getArea(), hull.getArea() >= 32.0);
  }

  /**
   * The same regression on the fill entry point.
   * <p>
   * {@code concaveFillByLength} returns the fill <em>between</em> the polygons,
   * not the polygons plus the fill, so the expected area is the gap alone: the
   * two 4x4 squares sit at x 0-4 and 6-10, leaving a 2 wide by 4 tall gap.
   */
  public void testConcaveFillAcceptsMultiPolygon() throws Exception {
    Geometry fill = HullFunctions.concaveFill(read(MULTIPOLYGON), 5.0);
    assertEquals("fill should be the 2x4 gap between the squares",
        8.0, fill.getArea(), 1.0e-9);
  }

  /** A curve inside a collection must still be linearised. */
  public void testCurvedMemberInCollectionIsLinearised() throws Exception {
    Geometry g = linearized("GEOMETRYCOLLECTION (POINT (0 0), "
        + "CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0))");
    assertEquals("collection type is kept", "GeometryCollection", g.getGeometryType());
    assertTrue("the arc member should be densified, got " + g.getNumPoints() + " points",
        g.getNumPoints() > 100);
    assertEquals("and its length should be the circumference 4*pi",
        4 * Math.PI, g.getLength(), 1.0e-3);
  }

  /** Guard: MultiSurface is Linearizable, so it converts rather than degrades. */
  public void testMultiSurfaceStillBecomesMultiPolygon() throws Exception {
    assertEquals("MultiPolygon",
        linearized("MULTISURFACE (CURVEPOLYGON (CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0)))")
            .getGeometryType());
  }

  /** Guard: MultiCurve likewise. */
  public void testMultiCurveStillBecomesMultiLineString() throws Exception {
    assertEquals("MultiLineString",
        linearized("MULTICURVE (CIRCULARSTRING (-2 0, 0 2, 2 0), (0 0, 1 1))")
            .getGeometryType());
  }

  /** Guard: a bare curve still linearises to a plain type. */
  public void testBareCurvePolygonBecomesPolygon() throws Exception {
    assertEquals("Polygon",
        linearized("CURVEPOLYGON (CIRCULARSTRING (-2 0, 0 2, 2 0, 0 -2, -2 0))")
            .getGeometryType());
  }

  /** Guard: an empty collection is returned as-is rather than rebuilt. */
  public void testEmptyCollectionUnchanged() throws Exception {
    Geometry in = read("GEOMETRYCOLLECTION EMPTY");
    assertSame(in, CurveFunctions.linearizeForOps(in));
  }
}
