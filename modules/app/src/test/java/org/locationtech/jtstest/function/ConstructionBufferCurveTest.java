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
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Visual-QA sweep on {@code A = CIRCULARSTRING (1 0, 1 1, 0 1)}: the buffer
 * validator and the construction functions against curve input.
 * <p>
 * <b>bufferValidated: an inconsistency between two halves of one function.</b>
 * {@code g.buffer(distance)} is an instance method, so CRV-OPS makes it buffer
 * the densified arc -- but the function then handed the RAW curve to
 * {@code BufferResultValidator}, which measures against the control-point
 * chords. A's arc (centre {@code (0.5, 0.5)}, r = 0.7071) bulges 0.207 outside
 * its chords, so a correct 0.1 buffer of the arc is 0.066 from the chord and
 * the validator rejected a right answer:
 * {@code "Distance between buffer curve and input is too small"}. The arc-aware
 * half exposed the chord-based half. Both now see the same linearised geometry.
 * <p>
 * <b>Largest Empty Circle: the obstacles are chord-read.</b> Core's
 * {@code LargestEmptyCircle} measures obstacle distances from coordinates, so a
 * curve obstacle repels the circle from its chords rather than its arc -- the
 * circle can overlap the bulge. The "Boundary must be polygonal" refusals in
 * the sweep were core being right (an open arc cannot bound an area); the shim
 * fixes the case where the input IS legal. Same for MaximumInscribedCircle on a
 * CurvePolygon.
 * <p>
 * <b>circleByRadiusLine: a degenerate ring from a parameter.</b>
 * {@code nPts = 1} built a 2-point ring and died in LinearRing's constructor,
 * naming neither the parameter nor the bound.
 */
public class ConstructionBufferCurveTest extends TestCase {

  /** Semicircular arc, centre (0.5, 0.5), radius sqrt(0.5). */
  private static final String ARC = "CIRCULARSTRING (1 0, 1 1, 0 1)";

  /** On the arc at 90 degrees: 0.207 outside the chord from (1 1) to (0 1). */
  private static final String ARC_APEX_POINT = "POINT (0.5 1.20710678)";

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(ConstructionBufferCurveTest.class); }
  public ConstructionBufferCurveTest(String name) { super(name); }

  private static Geometry read(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  // -- bufferValidated -------------------------------------------------------

  /** The reported crash: a correct buffer rejected by a chord-based validator. */
  public void testBufferValidatedAcceptsItsOwnBuffer() throws Exception {
    Geometry buf = BufferFunctions.bufferValidated(read(ARC), 0.1);
    assertTrue("the validated buffer must be valid", buf.isValid());
    assertTrue("and must cover the arc's apex, which lies 0.207 outside the chords",
        buf.covers(read(ARC_APEX_POINT)));
  }

  public void testBufferValidatedGeomDoesNotThrow() throws Exception {
    // Returns the error indicator; the point is that it evaluates consistently.
    BufferFunctions.bufferValidatedGeom(read(ARC), 0.1);
  }

  /** Guard: plain input validates exactly as before. */
  public void testBufferValidatedPlainUnchanged() throws Exception {
    Geometry buf = BufferFunctions.bufferValidated(
        read("LINESTRING (0 0, 10 0)"), 1.0);
    assertTrue(buf.isValid());
    // 0.05 tolerance: the endcaps are 8-segment quadrant approximations, so
    // the area is slightly under the exact 20 + pi. The first version of this
    // guard demanded 0.01 and failed against core's own discretisation.
    assertEquals("capsule area: rectangle plus two half-discs",
        20.0 + Math.PI, buf.getArea(), 0.05);
  }

  // -- largestEmptyCircle family ----------------------------------------------

  /**
   * A witness where chord and arc clearance differ by construction: the
   * obstacle is a full circle of radius 5 around a centred 8x8 boundary box, so
   * every candidate centre's nearest obstacle point is mid-arc, never a control
   * point. From the box centre the true clearance is 5; the control-point
   * diamond admits only 5/sqrt(2) = 3.54. The first version of this test used
   * an arc whose nearest points to the empty circle were its own control points
   * -- which lie ON the arc -- and discriminated nothing; it passed pre-fix and
   * was replaced rather than kept as a false witness.
   */
  public void testLargestEmptyCircleRespectsTheArc() throws Exception {
    Geometry obstacles = read("CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)");
    Geometry boundary = read("POLYGON ((-4 -4, 4 -4, 4 4, -4 4, -4 -4))");
    double tol = 0.01;
    Geometry radiusLine = ConstructionFunctions.largestEmptyCircleRadius(
        obstacles, boundary, tol);
    assertEquals("clearance at the centre is the arc's radius 5, not the "
        + "diamond's 3.54", 5.0, radiusLine.getLength(), 0.05);
  }

  /** Guard: an open arc still cannot bound an area; the refusal stands. */
  public void testLargestEmptyCircleStillRefusesLinealBoundary() throws Exception {
    try {
      ConstructionFunctions.largestEmptyCircle(read(ARC), read(ARC), 1.0);
      fail("an open arc is not a polygonal boundary");
    }
    catch (IllegalArgumentException e) {
      assertTrue("core's refusal is the right one: " + e.getMessage(),
          e.getMessage().contains("polygonal"));
    }
  }

  /** MaximumInscribedCircle on a CurvePolygon must fit inside the arc, not the chords. */
  public void testMaximumInscribedCircleOnACurvePolygon() throws Exception {
    Geometry disc = read("CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))");
    double radius = ConstructionFunctions.maxInscribedCircleRadiusLen(disc, 0.01);
    assertEquals("the largest circle inside a radius-5 disc has radius 5; the "
        + "chord diamond would only admit 3.54", 5.0, radius, 0.05);
  }

  // -- circleByRadiusLine -------------------------------------------------------

  /** nPts = 1 died inside LinearRing; the refusal must name the parameter. */
  public void testCircleByRadiusLineRefusesDegenerateCount() throws Exception {
    try {
      ConstructionFunctions.circleByRadiusLine(read("LINESTRING (0 0, 1 0)"), 1);
      fail("one vertex cannot form a ring");
    }
    catch (IllegalArgumentException e) {
      assertTrue("message should name the parameter and the bound: " + e.getMessage(),
          e.getMessage().contains("vertices") && e.getMessage().contains("3"));
    }
  }

  /** Guard: a sensible count still works, from a curve radius line too. */
  public void testCircleByRadiusLineStillWorks() throws Exception {
    Geometry circle = ConstructionFunctions.circleByRadiusLine(
        read("LINESTRING (0 0, 1 0)"), 60);
    assertEquals("unit circle area", Math.PI, circle.getArea(), 0.01);
  }
}
