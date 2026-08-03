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
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * ADD-HOLE-CURVE: adding an arc ring as a hole must keep it an arc.
 * <p>
 * ADD-HOLE fixed the wrong <em>shape</em> -- the control-point rings that gave
 * area 32 instead of {@code 16*pi} -- by densifying both inputs, which produced
 * {@code POLYGON(3146)} with area 50.2653486214479. That is right to within the
 * densify tolerance, and the residual matches the inscribed-polygon closed form
 * to 13 decimal places, so nothing about it is inaccurate.
 * <p>
 * It is still the wrong answer. Adding a hole is a <em>structural</em> operation:
 * it takes B's ring and hangs it on A. Nothing about it needs to evaluate the
 * arc, so nothing about it needs to approximate one. For
 * {@code A = CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))} and B
 * the r=3 equivalent, the exact answer exists and is expressible:
 * <pre>
 * CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0),
 *               CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))
 * </pre>
 * Ten control points, area exactly {@code 16*pi}, and the arc still an arc for
 * whatever comes next. The Option-A constructor
 * {@code CurvePolygon(LineString shell, LineString[] holes, GeometryFactory)}
 * has been able to build this all along; densifying threw away information the
 * output type could carry.
 * <p>
 * So the tolerance-based shim was over-applied here. It is the right remedy when
 * the consumer genuinely cannot represent a curve -- GML, KML, GeoJSON, the
 * triangulation hulls -- and the wrong one when the operation is structural and
 * the result type is a curve type. Densification is not the fallback of choice;
 * it is the fallback of last resort.
 * <p>
 * The all-linear path is deliberately untouched: a plain polygon plus a plain
 * hole still returns a plain {@link Polygon}, so nothing that never involved a
 * curve changes type.
 */
public class AddHoleCurveStructureTest extends TestCase {

  private static final String CURVE_POLY_A =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CURVE_POLY_B =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";
  private static final String CIRCULAR_RING_B =
      "CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0)";

  /** What the user asked for, and what both functions must now produce. */
  private static final String EXPECTED =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0), "
      + "CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";

  private static final double TRUE_AREA = Math.PI * (25.0 - 9.0);
  private static final double TRUE_LENGTH = 2.0 * Math.PI * (5.0 + 3.0);

  /** Exact: no sampling is involved, so only floating-point error is allowed. */
  private static final double EXACT = 1.0e-9;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(AddHoleCurveStructureTest.class); }
  public AddHoleCurveStructureTest(String name) { super(name); }

  private static Geometry read(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  /**
   * Compares via a WKT round trip rather than string equality, so writer
   * formatting is not part of the claim.
   */
  private static void assertSameCurveGeometry(String message, Geometry actual)
      throws Exception {
    String wkt = new CurveWKTWriter().write(actual);
    Geometry reparsed = read(wkt);
    assertTrue(message + "\n  expected: " + EXPECTED + "\n  actual:   " + wkt,
        read(EXPECTED).equalsExact(reparsed));
  }

  public void testAddHolesProducesACurvePolygon() throws Exception {
    Geometry result = GeometryFunctions.addHoles(read(CURVE_POLY_A), read(CURVE_POLY_B));
    assertEquals("result should still be a CurvePolygon",
        "CurvePolygon", result.getGeometryType());
  }

  public void testAddHoleProducesACurvePolygon() throws Exception {
    Geometry result = EditFunctions.addHole(read(CURVE_POLY_A), read(CURVE_POLY_B));
    assertEquals("result should still be a CurvePolygon",
        "CurvePolygon", result.getGeometryType());
  }

  /** The whole expectation in one assertion, for both functions. */
  public void testResultIsTheExpectedCurvePolygon() throws Exception {
    assertSameCurveGeometry("addHoles should produce the arc annulus",
        GeometryFunctions.addHoles(read(CURVE_POLY_A), read(CURVE_POLY_B)));
    assertSameCurveGeometry("addHole should produce the arc annulus",
        EditFunctions.addHole(read(CURVE_POLY_A), read(CURVE_POLY_B)));
  }

  /** Ten control points, not the 3146 vertices densification produced. */
  public void testResultKeepsTheControlPoints() throws Exception {
    assertEquals("five control points per ring, not a densified ring",
        10, GeometryFunctions.addHoles(read(CURVE_POLY_A), read(CURVE_POLY_B))
            .getNumPoints());
  }

  /** Exactly 16*pi, not 50.2653486214479. */
  public void testAreaIsExact() throws Exception {
    assertEquals("annulus area should be exactly 16*pi", TRUE_AREA,
        GeometryFunctions.addHoles(read(CURVE_POLY_A), read(CURVE_POLY_B)).getArea(),
        EXACT);
  }

  /** And exactly 16*pi for the perimeter, which coincides at these radii. */
  public void testLengthIsExact() throws Exception {
    assertEquals("annulus perimeter should be exactly 16*pi", TRUE_LENGTH,
        GeometryFunctions.addHoles(read(CURVE_POLY_A), read(CURVE_POLY_B)).getLength(),
        EXACT);
  }

  /** The hole must be reachable as an arc, not just measure like one. */
  public void testTheHoleIsStillACircularString() throws Exception {
    Geometry result = GeometryFunctions.addHoles(read(CURVE_POLY_A), read(CURVE_POLY_B));
    assertTrue("result should be a CurvePolygon", result instanceof CurvePolygon);
    CurvePolygon cp = (CurvePolygon) result;
    assertEquals("one hole", 1, cp.getNumInteriorRing());
    assertTrue("the structural hole should be a CircularString, got "
        + cp.getInteriorCurveN(0).getGeometryType(),
        cp.getInteriorCurveN(0) instanceof CircularString);
    assertTrue("and the shell too", cp.getExteriorCurve() instanceof CircularString);
  }

  /** A bare closed CIRCULARSTRING is a legal hole for addHole, and stays an arc. */
  public void testCircularStringHoleStaysAnArc() throws Exception {
    Geometry result = EditFunctions.addHole(read(CURVE_POLY_A), read(CIRCULAR_RING_B));
    assertSameCurveGeometry("a CIRCULARSTRING hole should be kept as an arc", result);
  }

  /** A plain shell with an arc hole: the hole still must not be flattened. */
  public void testPlainShellWithArcHole() throws Exception {
    Geometry result = GeometryFunctions.addHoles(
        read("POLYGON ((-6 -6, 6 -6, 6 6, -6 6, -6 -6))"), read(CURVE_POLY_B));
    assertEquals("144 less the 9*pi of the arc hole",
        144.0 - 9.0 * Math.PI, result.getArea(), EXACT);
    assertTrue("should be a CurvePolygon", result instanceof CurvePolygon);
  }

  /** Guard: still valid. */
  public void testResultIsValid() throws Exception {
    assertTrue("the arc annulus should be valid",
        GeometryFunctions.addHoles(read(CURVE_POLY_A), read(CURVE_POLY_B)).isValid());
  }

  /** Guard: nothing that never involved a curve changes type or value. */
  public void testPlainPolygonsUnchanged() throws Exception {
    String shell = "POLYGON ((-5 -5, 5 -5, 5 5, -5 5, -5 -5))";
    String hole = "POLYGON ((-1 -1, 1 -1, 1 1, -1 1, -1 -1))";
    for (Geometry result : new Geometry[] {
        GeometryFunctions.addHoles(read(shell), read(hole)),
        EditFunctions.addHole(read(shell), read(hole)) }) {
      assertEquals("must stay a plain Polygon", "Polygon", result.getGeometryType());
      assertEquals("100 less 4", 96.0, result.getArea(), 0.0);
      assertEquals("ten vertices", 10, result.getNumPoints());
    }
  }

  /** Guard: an all-linear CurvePolygon has no arc to keep, so it stays plain. */
  public void testAllLinearCurvePolygonStaysPlain() throws Exception {
    Geometry result = GeometryFunctions.addHoles(
        read("CURVEPOLYGON ((-5 -5, 5 -5, 5 5, -5 5, -5 -5))"),
        read("CURVEPOLYGON ((-1 -1, 1 -1, 1 1, -1 1, -1 -1))"));
    assertEquals("no arc anywhere, so no CurvePolygon needed",
        "Polygon", result.getGeometryType());
    assertEquals(96.0, result.getArea(), 0.0);
  }

  /** Guard: a line is still refused, with the message ADD-HOLE established. */
  public void testNonPolygonalStillRefused() throws Exception {
    try {
      GeometryFunctions.addHoles(read("CIRCULARSTRING (-5 0, 0 5, 5 0)"),
          read(CURVE_POLY_B));
      fail("a non-closed CircularString is not a polygon");
    }
    catch (IllegalArgumentException e) {
      assertTrue("expected the polygon message, got: " + e.getMessage(),
          e.getMessage().contains("not a polygon"));
    }
  }

  /** Guard: an existing arc hole is preserved when another is added. */
  public void testExistingArcHolePreserved() throws Exception {
    Geometry withOne = GeometryFunctions.addHoles(read(CURVE_POLY_A), read(CURVE_POLY_B));
    Geometry withTwo = GeometryFunctions.addHoles(withOne,
        read("CURVEPOLYGON ((3.5 -0.5, 4.5 -0.5, 4.5 0.5, 3.5 0.5, 3.5 -0.5))"));
    assertEquals("both holes present", 2, ((Polygon) withTwo).getNumInteriorRing());
    assertEquals("16*pi less the unit square", TRUE_AREA - 1.0,
        withTwo.getArea(), EXACT);
    assertTrue("the arc hole must survive the second add",
        ((CurvePolygon) withTwo).getInteriorCurveN(0) instanceof CircularString);
  }
}
