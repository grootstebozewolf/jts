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
 * CRV-MEASURE: {@code getLength()} on a curve must be the arc length, not the
 * sum of the control-point chords.
 * <p>
 * {@link CircularString} and {@link CompoundCurve} inherit
 * {@code LineString.getLength()}, which walks the coordinate sequence as
 * straight segments. For an arc that is the inscribed chord length, which is
 * always an underestimate -- for a semicircle it is off by about 10%:
 * {@code 2r√2} instead of {@code πr}.
 * <p>
 * This is the substance behind the epic's "arc-length parameterization"
 * claim, at the smallest place it can be observed.
 */
public class CurveMeasureTest extends GeometryTestCase {

  private static final double TOL = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(CurveMeasureTest.class);
  }

  public CurveMeasureTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader().read(wkt);
  }

  /**
   * Unit semicircle centred at (0,1): control points (0,0), (1,1), (0,2).
   * Arc length is pi*r = pi; the chord sum is 2*sqrt(2) ~= 2.828.
   */
  public void testUnitSemicircleLength() throws Exception {
    Geometry arc = readCurve("CIRCULARSTRING (0 0, 1 1, 0 2)");
    assertEquals("semicircle of radius 1 has arc length pi",
        Math.PI, arc.getLength(), 1.0e-6);
  }

  /**
   * Radius-2 semicircle: (2,0), (0,2), (-2,0) about the origin.
   * Arc length is 2*pi ~= 6.283; the chord sum is 4*sqrt(2) ~= 5.657.
   */
  public void testRadius2SemicircleLength() throws Exception {
    Geometry arc = readCurve("CIRCULARSTRING (2 0, 0 2, -2 0)");
    assertEquals("semicircle of radius 2 has arc length 2*pi",
        2.0 * Math.PI, arc.getLength(), 1.0e-6);
  }

  /** Arc length always exceeds the chord it subtends. */
  public void testArcLengthExceedsChordLength() throws Exception {
    Geometry arc = readCurve("CIRCULARSTRING (0 0, 1 1, 0 2)");
    double chordSum = 2.0 * Math.sqrt(2.0);
    assertTrue("arc length " + arc.getLength()
        + " must exceed the chord sum " + chordSum,
        arc.getLength() > chordSum + 1.0e-9);
  }

  /** A CompoundCurve's length is the sum of its members' lengths. */
  public void testCompoundCurveLengthSumsMembers() throws Exception {
    Geometry cc = readCurve(
        "COMPOUNDCURVE (CIRCULARSTRING (0 0, 1 1, 0 2), (0 2, 0 5))");
    assertEquals("semicircle (pi) plus a straight run of 3",
        Math.PI + 3.0, cc.getLength(), 1.0e-6);
  }

  /**
   * Collinear control points describe no arc, so the length degrades to the
   * straight-line distance.
   */
  public void testCollinearControlPointsFallBackToChord() throws Exception {
    Geometry degenerate = readCurve("CIRCULARSTRING (0 0, 1 0, 2 0)");
    assertEquals("collinear points are a straight segment of length 2",
        2.0, degenerate.getLength(), TOL);
  }

  /** Guard: a plain LineString's length is unaffected. */
  public void testLineStringLengthUnchanged() throws Exception {
    Geometry line = readCurve("LINESTRING (0 0, 3 4)");
    assertEquals(5.0, line.getLength(), TOL);
  }

  /** Guard: an empty CircularString has zero length. */
  public void testEmptyArcHasZeroLength() throws Exception {
    Geometry empty = readCurve("CIRCULARSTRING EMPTY");
    assertEquals(0.0, empty.getLength(), TOL);
  }
}
