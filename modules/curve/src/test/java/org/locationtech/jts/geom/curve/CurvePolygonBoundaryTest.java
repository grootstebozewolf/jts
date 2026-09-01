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
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * CRV-BND: the boundary of a {@link CurvePolygon} is made of its curve rings.
 * <p>
 * CurvePolygon inherits {@code Polygon.getBoundary()}, which builds from the
 * flat {@code getExteriorRing()} view, so the boundary of a circle comes back
 * as {@code LINEARRING (2 0, 0 2, -2 0, 0 -2, 2 0)} -- the inscribed
 * quadrilateral. The arc is gone, and the boundary no longer describes the
 * geometry it bounds: its length is the chord perimeter, and it does not
 * even lie on the polygon's edge.
 * <p>
 * The curve boundary of the line types is already right: an open
 * CircularString gives its two endpoints, a closed one gives MULTIPOINT EMPTY,
 * and a CompoundCurve gives the ends of the chain. Only the surface case is
 * wrong, so that is all this covers.
 */
public class CurvePolygonBoundaryTest extends GeometryTestCase {

  /** Radius-2 circle as two semicircular arcs. */
  private static final String CIRCLE_R2 =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 0 2, -2 0, 0 -2, 2 0))";

  public static void main(String[] args) {
    TestRunner.run(CurvePolygonBoundaryTest.class);
  }

  public CurvePolygonBoundaryTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader().read(wkt);
  }

  /** A single arc ring yields that arc, not a linearised ring. */
  public void testArcShellBoundaryKeepsArcType() throws Exception {
    Geometry b = readCurve(CIRCLE_R2).getBoundary();
    assertEquals("boundary of an arc shell should stay an arc",
        "CircularString", b.getGeometryType());
  }

  /** The boundary measures the arc perimeter, not the chord perimeter. */
  public void testArcShellBoundaryLength() throws Exception {
    Geometry b = readCurve(CIRCLE_R2).getBoundary();
    assertEquals("circumference of a radius-2 circle is 4*pi",
        4.0 * Math.PI, b.getLength(), 1.0e-6);
  }

  /** With a hole the boundary is a MultiCurve of both curve rings. */
  public void testBoundaryWithArcHoleIsMultiCurve() throws Exception {
    Geometry b = readCurve("CURVEPOLYGON ("
        + "CIRCULARSTRING (4 0, 0 4, -4 0, 0 -4, 4 0), "
        + "CIRCULARSTRING (1 0, 0 1, -1 0, 0 -1, 1 0))").getBoundary();
    assertEquals("MultiCurve", b.getGeometryType());
    assertEquals("shell plus one hole", 2, b.getNumGeometries());
    assertEquals("CircularString", b.getGeometryN(0).getGeometryType());
    assertEquals("CircularString", b.getGeometryN(1).getGeometryType());
    assertEquals("8*pi shell plus 2*pi hole",
        8.0 * Math.PI + 2.0 * Math.PI, b.getLength(), 1.0e-6);
  }

  /** A CompoundCurve ring is preserved as a CompoundCurve. */
  public void testCompoundCurveShellBoundary() throws Exception {
    Geometry b = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 0, 1 1, 2 0), (2 0, 0 0)))")
        .getBoundary();
    assertEquals("CompoundCurve", b.getGeometryType());
  }

  /** Guard: an all-linear CurvePolygon keeps the inherited boundary. */
  public void testLinearShellBoundaryUnchanged() throws Exception {
    Geometry b = readCurve("CURVEPOLYGON ((0 0, 4 0, 4 3, 0 3, 0 0))").getBoundary();
    assertEquals("LinearRing", b.getGeometryType());
    assertEquals(14.0, b.getLength(), 1.0e-9);
  }

  /** Guard: an empty CurvePolygon has an empty boundary. */
  public void testEmptyBoundary() throws Exception {
    assertTrue(readCurve("CURVEPOLYGON EMPTY").getBoundary().isEmpty());
  }

  /** Guard: boundary dimension is 1 for a surface. */
  public void testBoundaryDimension() throws Exception {
    assertEquals(1, readCurve(CIRCLE_R2).getBoundary().getDimension());
  }
}
