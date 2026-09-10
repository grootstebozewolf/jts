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
 * R.2: DE-9IM for a certified half-disc and for a single open
 * CircularString vs Point / Line / same-circle disc. Not a stadium;
 * not a noder. Full R-PR TAG methods stay red elsewhere.
 */
public class CurveExactRelateHalfDiscTest extends GeometryTestCase {

  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String ARC =
      "CIRCULARSTRING (-5 0, 0 5, 5 0)";

  private static final String IM_IN = CurveExact.IM_POINT_INTERIOR;
  private static final String IM_ON = CurveExact.IM_POINT_BOUNDARY;
  private static final String IM_OUT = CurveExact.IM_POINT_EXTERIOR;

  public static void main(String[] args) {
    TestRunner.run(CurveExactRelateHalfDiscTest.class);
  }

  public CurveExactRelateHalfDiscTest(String name) {
    super(name);
  }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testHalfDiscPointInteriorBoundaryExterior() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    assertEquals(IM_IN, CurveExact.relate(half, readCurve("POINT (0 2)")).toString());
    assertEquals(IM_IN, half.relate(readCurve("POINT (3 3)")).toString());
    assertEquals(IM_ON, half.relate(readCurve("POINT (0 5)")).toString());
    assertEquals(IM_ON, half.relate(readCurve("POINT (0 0)")).toString());
    assertEquals(IM_ON, half.relate(readCurve("POINT (5 0)")).toString());
    assertEquals(IM_OUT, half.relate(readCurve("POINT (0 -2)")).toString());
    assertEquals(IM_OUT, half.relate(readCurve("POINT (6 0)")).toString());
  }

  public void testHalfDiscLineCrossingMissDiameter() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    assertEquals("1F20F1102",
        half.relate(readCurve("LINESTRING (0 -2, 0 6)")).toString());
    assertEquals("1F20F1102",
        half.relate(readCurve("LINESTRING (-6 2, 6 2)")).toString());
    assertEquals("FF2FF1102",
        half.relate(readCurve("LINESTRING (0 -1, 0 -2)")).toString());
    assertEquals("FF2101FF2",
        half.relate(readCurve("LINESTRING (-5 0, 5 0)")).toString());
    assertEquals("FF21F1102",
        half.relate(readCurve("LINESTRING (-10 0, 10 0)")).toString());
  }

  public void testHalfDiscSameCircleDisc() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_5);
    assertEquals("2FF11F212", CurveExact.relate(half, disc).toString());
    assertEquals("212F11FF2", CurveExact.relate(disc, half).toString());
    assertEquals("2FF11F212", half.relate(disc).toString());
  }

  public void testOpenArcPointOnOffEnd() throws Exception {
    Geometry arc = readCurve(ARC);
    assertEquals("0F1FF0FF2",
        CurveExact.relate(arc, readCurve("POINT (0 5)")).toString());
    assertEquals("FF10F0FF2",
        CurveExact.relate(arc, readCurve("POINT (-5 0)")).toString());
    assertEquals("FF1FF00F2",
        CurveExact.relate(arc, readCurve("POINT (0 2)")).toString());
    assertEquals("0F1FF0FF2", arc.relate(readCurve("POINT (0 5)")).toString());
  }

  public void testNotAHalfDiscStillNullForStadiumOddity() throws Exception {
    // Full disc still uses the disc cell, not half.
    Geometry disc = readCurve(CIRCLE_5);
    assertEquals(IM_IN, CurveExact.relate(disc, readCurve("POINT (0 0)")).toString());
    assertNull("crossing discs of different centres are not the same-circle cell",
        CurveExact.relate(readCurve(HALF_DISC),
            readCurve(
                "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))")));
  }
}
