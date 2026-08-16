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
 * ML.1: certified stadium MIC. Radius is the cap radius; centre is
 * the midpoint of the two cap centres. Disc MIC (ML.0) is tried first
 * and stays bit-identical. {@code HALF_DISC} is two members -- a named
 * miss, not a half-disc diamond claimed exact.
 */
public class CurveExactStadiumMicTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  /** Caps r=1 at (0,-1) and (0,6), sides x=±1. Midpoint (0, 2.5). */
  private static final String STADIUM_FOUR =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, 0 -2, 1 -1), (1 -1, 1 6), CIRCULARSTRING (1 6, 0 7, -1 6), (-1 6, -1 -1)))";
  /** Caps r=1 at (0,4) and (0,-1), sides x=±1. Midpoint (0, 1.5). CW. */
  private static final String STADIUM_ODD =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 4, 0 5, 1 4), (1 4, 1 -1), CIRCULARSTRING (1 -1, 0 -2, -1 -1), (-1 -1, -1 4)))";
  /** Horizontal stadium from the overlay fixtures: caps at (±1, 2). */
  private static final String STADIUM_IN =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 1, -2 2, -1 3), (-1 3, 1 3), CIRCULARSTRING (1 3, 2 2, 1 1), (1 1, -1 1)))";

  public static void main(String[] args) {
    TestRunner.run(CurveExactStadiumMicTest.class);
  }

  public CurveExactStadiumMicTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testCircle5MicIsDiscItselfBitIdentical() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    CircularArcDensifier.Circle expected = CurveExact.circularDisc(disc);
    CircularArcDensifier.Circle mic = CurveExact.mic(disc);
    assertNotNull("CIRCLE_5 is ML.0", expected);
    assertNotNull(mic);
    assertEquals("mic() must be the disc cell, not a later cell",
        Double.doubleToRawLongBits(expected.cx), Double.doubleToRawLongBits(mic.cx));
    assertEquals(Double.doubleToRawLongBits(expected.cy),
        Double.doubleToRawLongBits(mic.cy));
    assertEquals(Double.doubleToRawLongBits(expected.r),
        Double.doubleToRawLongBits(mic.r));
    assertEquals(0.0, mic.cx, 0.0);
    assertEquals(0.0, mic.cy, 0.0);
    assertEquals(5.0, mic.r, 0.0);
    assertEquals(0x4014000000000000L, Double.doubleToRawLongBits(mic.r));
    assertNull("a full disc is not a stadium", CurveExact.stadiumMic(disc));
  }

  public void testStadiumFourClosedForm() throws Exception {
    CircularArcDensifier.Circle mic = CurveExact.stadiumMic(readCurve(STADIUM_FOUR));
    assertNotNull(mic);
    assertEquals(0.0, mic.cx, 0.0);
    assertEquals(2.5, mic.cy, 0.0);
    assertEquals(1.0, mic.r, 0.0);
    CircularArcDensifier.Circle viaMic = CurveExact.mic(readCurve(STADIUM_FOUR));
    assertEquals(mic.cx, viaMic.cx, 0.0);
    assertEquals(mic.cy, viaMic.cy, 0.0);
    assertEquals(mic.r, viaMic.r, 0.0);
  }

  public void testStadiumOddClosedForm() throws Exception {
    CircularArcDensifier.Circle mic = CurveExact.stadiumMic(readCurve(STADIUM_ODD));
    assertNotNull("STADIUM_ODD is two r=1 caps + parallel sides; overlay tangent is HP",
        mic);
    assertEquals(0.0, mic.cx, 0.0);
    assertEquals(1.5, mic.cy, 0.0);
    assertEquals(1.0, mic.r, 0.0);
  }

  public void testStadiumInHorizontalAlsoCertifies() throws Exception {
    CircularArcDensifier.Circle mic = CurveExact.stadiumMic(readCurve(STADIUM_IN));
    assertNotNull(mic);
    assertEquals(0.0, mic.cx, 0.0);
    assertEquals(2.0, mic.cy, 0.0);
    assertEquals(1.0, mic.r, 0.0);
  }

  public void testHalfDiscIsNotAStadium() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    assertNull("HALF_DISC is one semicircle + diameter",
        CurveExact.stadiumMic(half));
    assertNull("HALF_DISC is not a disc either -- chordsaw, not a diamond laser",
        CurveExact.mic(half));
  }

  public void testHoledStadiumStamps() throws Exception {
    Geometry holed = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, 0 -2, 1 -1), (1 -1, 1 6), CIRCULARSTRING (1 6, 0 7, -1 6), (-1 6, -1 -1)), (0 1, 1 1, 1 2, 0 2, 0 1))");
    assertNull(CurveExact.stadiumMic(holed));
  }
}
