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
package org.locationtech.jts.geom.curved;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Dimension;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * M-DIM (#1195): the extended SQL/MM geometry types report the correct
 * topological dimension. The curve/surface types inherit dimension from their
 * JTS supertypes (CircularString/CompoundCurve/MultiCurve are lineal = 1;
 * CurvePolygon/MultiSurface/PolyhedralSurface/Triangle/Tin are areal = 2);
 * this test pins that mapping so a future refactor can't regress it.
 * <p>
 * Oracle note: M-DIM is a topological-dimension API contract (a small integer
 * per type), not a geometric quantity the NetTopologySuite.Proofs oracle
 * computes, so there is no oracle vector pin for this TAG -- it is verified by
 * the type-to-dimension assertions below. The oracle backs the metric/decidable
 * TAGs (length, area, distance, intersection, orientation, snap), not type
 * metadata.
 */
public class CurveDimensionTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CurveDimensionTest.class);
  }

  public CurveDimensionTest(String name) { super(name); }

  private final CurvedGeometryFactory gf = new CurvedGeometryFactory();

  private CoordinateArraySequence seq(double... xy) {
    Coordinate[] pts = new Coordinate[xy.length / 2];
    for (int i = 0; i < pts.length; i++)
      pts[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
    return new CoordinateArraySequence(pts);
  }

  private LinearRing triRing() {
    return gf.createLinearRing(seq(0,0, 2,0, 1,2, 0,0));
  }

  private Polygon poly() {
    return gf.createPolygon(triRing());
  }

  public void testLinealTypesHaveDimension1() {
    assertEquals(Dimension.L, gf.createCircularString(seq(0,0, 1,1, 2,0)).getDimension());
    assertEquals(Dimension.L, gf.createCompoundCurve(seq(0,0, 1,1, 2,0)).getDimension());
    assertEquals(Dimension.L, gf.createMultiCurve(
        new LineString[]{ gf.createCircularString(seq(0,0, 1,1, 2,0)) }).getDimension());
  }

  public void testArealTypesHaveDimension2() {
    assertEquals(Dimension.A, gf.createCurvePolygon(triRing()).getDimension());
    assertEquals(Dimension.A, gf.createMultiSurface(new Polygon[]{ poly() }).getDimension());
    assertEquals(Dimension.A, gf.createPolyhedralSurface(new Polygon[]{ poly() }).getDimension());
    assertEquals(Dimension.A, gf.createTriangle(triRing()).getDimension());
    assertEquals(Dimension.A, gf.createTin(new Polygon[]{ poly() }).getDimension());
  }

  /** Areal types have a 1-dimensional (curve) boundary. */
  public void testArealBoundaryDimensionIs1() {
    assertEquals(Dimension.L, gf.createCurvePolygon(triRing()).getBoundaryDimension());
    assertEquals(Dimension.L, gf.createMultiSurface(new Polygon[]{ poly() }).getBoundaryDimension());
    assertEquals(Dimension.L, gf.createTriangle(triRing()).getBoundaryDimension());
  }

  /** Empty curve/surface geometries still report their type's topological dimension. */
  public void testEmptyCurveDimensions() {
    assertEquals(Dimension.L, gf.createCircularString(seq()).getDimension());
    assertEquals(Dimension.L, gf.createCompoundCurve(seq()).getDimension());
    assertEquals(Dimension.L, gf.createMultiCurve(new LineString[0]).getDimension());
    assertEquals(Dimension.A, gf.createCurvePolygon().getDimension());
    assertEquals(Dimension.A, gf.createMultiSurface(new Polygon[0]).getDimension());
    assertEquals(Dimension.A, gf.createTriangle().getDimension());
    assertTrue(gf.createCircularString(seq()).isEmpty());
    assertTrue(gf.createCurvePolygon().isEmpty());
  }

  /** Curve types preserve the coordinate dimension (Z / M) of their input sequence. */
  public void testCoordinateDimensionPreserved() {
    // XYZ
    CircularString xyz = gf.createCircularString(new CoordinateArraySequence(
        new Coordinate[]{ new Coordinate(0,0,1), new Coordinate(1,1,2), new Coordinate(2,0,3) }, 3, 0));
    assertEquals(3, xyz.getCoordinateSequence().getDimension());
    assertEquals(0, xyz.getCoordinateSequence().getMeasures());
    // XYM
    CircularString xym = gf.createCircularString(new CoordinateArraySequence(3, 3, 1));
    assertEquals(3, xym.getCoordinateSequence().getDimension());
    assertEquals(1, xym.getCoordinateSequence().getMeasures());
    // XYZM
    CircularString xyzm = gf.createCircularString(new CoordinateArraySequence(3, 4, 1));
    assertEquals(4, xyzm.getCoordinateSequence().getDimension());
    assertEquals(1, xyzm.getCoordinateSequence().getMeasures());
  }
}
