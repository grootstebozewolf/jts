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
package org.locationtech.jtstest.geomfunction;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jts.geom.curved.Linearizable;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * APP-LIN: TestBuilder must offer a way to linearise a curve at a tolerance the
 * user chooses.
 * <p>
 * Everything else about curves is reachable from the UI -- they can be drawn
 * ({@code CircularStringTool}), parsed ({@code CurvedWKTReader} in
 * {@code TestBuilderModel}), written ({@code CurvedWKTWriter}) and rendered as
 * true arcs ({@code CurvedShapeWriter} in {@code GeometryPainter}). The one
 * missing piece is {@code Linearizable.toLinear}, the operation that turns an
 * arc into the polyline every legacy consumer actually receives.
 * <p>
 * Its only call site in the app is private:
 * {@code BufferFunctions.linearizeRecurse}, reached solely from
 * {@code bufferCurveWithParams}, at a tolerance derived from the buffer
 * distance. So there is no way to ask "what polyline does this arc become at
 * tolerance X" -- which is precisely the question when diagnosing whether a
 * downstream chord-based result is a densification artefact or a real defect.
 * <p>
 * This is a gap, not a regression: {@code toLinear} is new, so TestBuilder
 * never had a reason to expose it before.
 */
public class CurveFunctionRegistryTest extends TestCase {

  private static final String SEMICIRCLE = "CIRCULARSTRING (2 0, 0 2, -2 0)";
  private static final String CIRCLE =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 0 2, -2 0, 0 -2, 2 0))";

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CurveFunctionRegistryTest.class); }
  public CurveFunctionRegistryTest(String name) { super(name); }

  private static GeometryFunctionRegistry registry() {
    return GeometryFunctionRegistry.createTestBuilderRegistry();
  }

  /**
   * Fails with a readable message rather than letting every behavioural test
   * below die on a null dereference.
   */
  private static GeometryFunction toLinearFunc() {
    GeometryFunction f = registry().find("toLinear", 1);
    assertNotNull("the TestBuilder registry exposes no toLinear(Geometry, tolerance) function", f);
    return f;
  }

  private static Geometry read(String wkt) throws Exception {
    return new CurvedWKTReader(new CurvedGeometryFactory()).read(wkt);
  }

  private static Geometry apply(String wkt, double tolerance) throws Exception {
    return (Geometry) toLinearFunc().invoke(read(wkt), new Object[] { tolerance });
  }

  /** The function is registered at all. */
  public void testRegistryExposesToLinear() {
    assertNotNull("toLinear should be findable by name", registry().find("toLinear"));
  }

  /** It appears under its own Curve category, so it is discoverable in the tree. */
  public void testToLinearIsInCurveCategory() {
    assertNotNull("toLinear should sit in the Curve category",
        registry().find("Curve", "toLinear"));
  }

  /** It takes exactly one scalar argument, the tolerance. */
  public void testToLinearTakesATolerance() {
    GeometryFunction f = toLinearFunc();
    assertEquals("one scalar parameter", 1, f.getParameterTypes().length);
    assertEquals("Tolerance", f.getParameterNames()[0]);
    assertTrue("returns a Geometry so it lands in the geometry-function tree",
        GeometryFunctionRegistry.hasGeometryResult(f));
  }

  /**
   * A 3-control-point arc becomes a polyline whose vertices lie on the true
   * circle -- the actual content of "densified", and what distinguishes this
   * from core's {@code Densifier}, which subdivides the chords and so walks
   * away from the arc.
   * <p>
   * The count is asserted against the sagitta bound rather than a round number:
   * a chord subtending angle t deviates by r(1 - cos(t/2)), so tolerance tol
   * admits at most t = 2*acos(1 - tol/r) per segment. For the radius-2
   * semicircle at tol 0.01 that is 16 segments, 17 vertices -- so a threshold
   * like "more than 20" would be wrong, not merely loose.
   */
  public void testToLinearDensifiesAnArc() throws Exception {
    double r = 2.0;
    double tol = 0.01;
    Geometry linear = apply(SEMICIRCLE, tol);

    int minSegments = (int) Math.ceil(Math.PI / (2.0 * Math.acos(1.0 - tol / r)));
    assertTrue("semicircle needs at least " + minSegments + " segments at tolerance "
        + tol + ", got " + (linear.getNumPoints() - 1),
        linear.getNumPoints() - 1 >= minSegments);

    for (Coordinate c : linear.getCoordinates()) {
      double dev = Math.abs(Math.hypot(c.x, c.y) - r);
      assertTrue("vertex " + c + " is " + dev + " off the true circle", dev < 1.0e-9);
    }
  }

  /** Tightening the tolerance adds vertices -- the whole point of exposing it. */
  public void testToLinearHonoursTolerance() throws Exception {
    int coarse = apply(SEMICIRCLE, 0.5).getNumPoints();
    int fine = apply(SEMICIRCLE, 0.001).getNumPoints();
    assertTrue("tolerance 0.001 (" + fine + " pts) should be finer than 0.5 ("
        + coarse + " pts)", fine > coarse);
  }

  /** The result is a plain geometry, no longer a curve. */
  public void testToLinearResultIsNoLongerACurve() throws Exception {
    Geometry linear = apply(SEMICIRCLE, 0.01);
    assertFalse("result should not still be Linearizable: " + linear.getClass().getName(),
        linear instanceof Linearizable);
  }

  /** A CurvePolygon linearises to a Polygon with a densified shell. */
  public void testToLinearOfCurvePolygon() throws Exception {
    Geometry linear = apply(CIRCLE, 0.01);
    assertTrue("should be a Polygon, was " + linear.getClass().getName(),
        linear instanceof Polygon);
    assertTrue("shell should be densified, got "
        + ((Polygon) linear).getExteriorRing().getNumPoints(),
        ((Polygon) linear).getExteriorRing().getNumPoints() > 20);
  }

  /** Curves nested in a collection are linearised too. */
  public void testToLinearRecursesIntoCollections() throws Exception {
    Geometry linear = apply("GEOMETRYCOLLECTION ("
        + "LINESTRING (0 0, 10 0), " + SEMICIRCLE + ")", 0.01);
    assertEquals(2, linear.getNumGeometries());
    assertFalse("nested arc should be linearised",
        linear.getGeometryN(1) instanceof Linearizable);
  }

  /** Guard: a geometry with no curve in it comes back unchanged. */
  public void testLinearGeometryUnchanged() throws Exception {
    Geometry line = read("LINESTRING (0 0, 4 0, 4 3)");
    assertTrue("a plain LineString should pass through untouched",
        line.equalsExact(apply("LINESTRING (0 0, 4 0, 4 3)", 0.01)));
  }

  /** Guard: an empty curve linearises to something empty, not to a failure. */
  public void testEmptyCurve() throws Exception {
    assertTrue(apply("CIRCULARSTRING EMPTY", 0.01).isEmpty());
  }

  /** Guard: the densified arc still tracks the true arc's length. */
  public void testDensifiedLengthApproachesArcLength() throws Exception {
    Geometry arc = read(SEMICIRCLE);
    double exact = arc.getLength();
    double densified = apply(SEMICIRCLE, 0.001).getLength();
    assertTrue("densified length " + densified + " should approach the exact arc length "
        + exact, densified < exact && densified > exact * 0.999);
  }
}
