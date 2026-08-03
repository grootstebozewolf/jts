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
 * OVL-STATIC: the static overlay entry points must see the arc too.
 * <p>
 * OVL-OPS fixed the instance methods, so {@code Overlay.*} is arc-aware. Every
 * {@code OverlayNG*} family calls a static core method taking a
 * {@link Geometry}, so no override can intercept it, and they all still ran on
 * the control-point chords -- or failed outright:
 * {@code OverlayNG.difference} and {@code union},
 * {@code OverlayNGSnapping.difference} and {@code union}, and
 * {@code OverlayNGStrict.difference} and {@code union} all threw
 * {@code TopologyException: Result area inconsistent with overlay operation},
 * because a CurvePolygon reported an arc-aware area of 78.54 for linework
 * enclosing 50 and core's own cross-check caught the contradiction.
 * <p>
 * {@code Overlay.unionUsingGeometryCollection} is in the same position for a
 * different reason: it wraps both operands in a GeometryCollection, which is not
 * a curve type, so the OVL-OPS override never fires.
 * <p>
 * <b>Two families are deliberately excluded, and guards pin that down.</b>
 * <ul>
 * <li>{@code OverlayNGOpt.*} is <em>already exact</em>: its prepared-geometry
 *     short-circuit finds that A covers B and returns B untouched, so
 *     {@code intersection} gives a CurvePolygon of area exactly {@code 9*pi}.
 *     Linearising would replace an exact answer with a 1573-point approximation
 *     -- the mistake ADD-HOLE-CURVE corrected. It is fragile rather than robust,
 *     because the {@code covers} test behind it is itself chord-based, so it can
 *     short-circuit wrongly on inputs where chord and arc containment disagree.
 *     Making it properly exact means an arc-aware predicate, not densification.</li>
 * <li>{@code OverlayNGTest.*} reports core's internals -- noded edge sets,
 *     intersection edges. Densifying its input would change what it says about
 *     core rather than fix anything, and the point of those functions is to show
 *     what core actually sees.</li>
 * </ul>
 * <p>
 * Snap-rounding families are asserted differently from the floating-precision
 * ones. Quantising a densified circle to a 0.1 grid moves vertices by up to half
 * a cell, which perturbs the area far more than densification does, so those are
 * asserted to land nearer the arc answer than the chord answer rather than
 * within a tight band. Pretending to a precision the operation does not have
 * would make the test lie.
 */
public class OverlayStaticCurveTest extends TestCase {

  private static final String A = "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String B = "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";

  private static final double AREA_A = 25.0 * Math.PI;
  private static final double AREA_B = 9.0 * Math.PI;
  private static final double ANNULUS = AREA_A - AREA_B;

  /** Chord answers, for messages and for the snap-rounding comparisons. */
  private static final double CHORD_A = 50.0, CHORD_B = 18.0, CHORD_ANNULUS = 32.0;

  /** See CurveOverlayTest: inscribed-polygon shortfall is under 2.1e-4 here. */
  private static final double AREA_TOL = 1.0e-3;

  /** Snap rounding at scale 10 quantises to a 0.1 grid. */
  private static final double SR_SCALE = 10.0;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(OverlayStaticCurveTest.class); }
  public OverlayStaticCurveTest(String name) { super(name); }

  private static Geometry read(String wkt) throws Exception {
    return new CurvedWKTReader(new CurvedGeometryFactory()).read(wkt);
  }

  private static Geometry a() throws Exception { return read(A); }
  private static Geometry b() throws Exception { return read(B); }

  /** Nearer the arc answer than the chord answer -- for quantising operations. */
  private static void assertNearerTheArc(String what, double expectedArc,
      double chordAnswer, double actual) {
    double toArc = Math.abs(actual - expectedArc);
    double toChord = Math.abs(actual - chordAnswer);
    assertTrue(what + ": area " + actual + " should be nearer the arc answer "
        + expectedArc + " (off by " + toArc + ") than the chord answer "
        + chordAnswer + " (off by " + toChord + ")", toArc < toChord);
  }

  // -- OverlayNG (floating precision) --------------------------------------

  public void testOverlayNGIntersection() throws Exception {
    assertEquals("OverlayNG intersection should be 9*pi not " + CHORD_B,
        AREA_B, OverlayNGFunctions.intersection(a(), b()).getArea(), AREA_TOL);
  }

  /** Threw TopologyException before, so this is a crash fix as much as a value fix. */
  public void testOverlayNGUnionNoLongerThrows() throws Exception {
    assertEquals("OverlayNG union should be 25*pi not " + CHORD_A,
        AREA_A, OverlayNGFunctions.union(a(), b()).getArea(), AREA_TOL);
  }

  public void testOverlayNGDifferenceNoLongerThrows() throws Exception {
    assertEquals("OverlayNG difference should be 16*pi not " + CHORD_ANNULUS,
        ANNULUS, OverlayNGFunctions.difference(a(), b()).getArea(), AREA_TOL);
  }

  public void testOverlayNGSymDifference() throws Exception {
    assertEquals("OverlayNG symDifference should be 16*pi",
        ANNULUS, OverlayNGFunctions.symDifference(a(), b()).getArea(), AREA_TOL);
  }

  public void testOverlayNGUnaryUnion() throws Exception {
    assertEquals("unary union of the arc circle should be 25*pi",
        AREA_A, OverlayNGFunctions.unaryUnion(a()).getArea(), AREA_TOL);
  }

  // -- OverlayNGRobust, OverlayNGStrict, OverlayNoSnap ---------------------

  public void testOverlayNGRobust() throws Exception {
    assertEquals("robust intersection", AREA_B,
        OverlayNGRobustFunctions.intersection(a(), b()).getArea(), AREA_TOL);
    assertEquals("robust union", AREA_A,
        OverlayNGRobustFunctions.union(a(), b()).getArea(), AREA_TOL);
    assertEquals("robust difference", ANNULUS,
        OverlayNGRobustFunctions.difference(a(), b()).getArea(), AREA_TOL);
  }

  public void testOverlayNGStrict() throws Exception {
    assertEquals("strict intersection", AREA_B,
        OverlayNGStrictFunctions.intersection(a(), b()).getArea(), AREA_TOL);
    assertEquals("strict union", AREA_A,
        OverlayNGStrictFunctions.union(a(), b()).getArea(), AREA_TOL);
    assertEquals("strict difference", ANNULUS,
        OverlayNGStrictFunctions.difference(a(), b()).getArea(), AREA_TOL);
  }

  public void testOverlayNoSnap() throws Exception {
    assertEquals("no-snap intersection", AREA_B,
        OverlayNoSnapFunctions.intersection(a(), b()).getArea(), AREA_TOL);
    assertEquals("no-snap union", AREA_A,
        OverlayNoSnapFunctions.union(a(), b()).getArea(), AREA_TOL);
    assertEquals("no-snap difference", ANNULUS,
        OverlayNoSnapFunctions.difference(a(), b()).getArea(), AREA_TOL);
  }

  // -- quantising families ------------------------------------------------

  public void testOverlayNGSnapRounding() throws Exception {
    assertNearerTheArc("SR intersection", AREA_B, CHORD_B,
        OverlayNGSRFunctions.intersection(a(), b(), SR_SCALE).getArea());
    assertNearerTheArc("SR union", AREA_A, CHORD_A,
        OverlayNGSRFunctions.union(a(), b(), SR_SCALE).getArea());
    assertNearerTheArc("SR difference", ANNULUS, CHORD_ANNULUS,
        OverlayNGSRFunctions.difference(a(), b(), SR_SCALE).getArea());
  }

  public void testOverlayNGSnapping() throws Exception {
    assertNearerTheArc("snapping intersection", AREA_B, CHORD_B,
        OverlayNGSnappingFunctions.intersection(a(), b(), 0.01).getArea());
    assertNearerTheArc("snapping union", AREA_A, CHORD_A,
        OverlayNGSnappingFunctions.union(a(), b(), 0.01).getArea());
    assertNearerTheArc("snapping difference", ANNULUS, CHORD_ANNULUS,
        OverlayNGSnappingFunctions.difference(a(), b(), 0.01).getArea());
  }

  // -- the GeometryCollection wrapper -------------------------------------

  public void testUnionUsingGeometryCollection() throws Exception {
    assertEquals("wrapping in a GeometryCollection must not lose the arc",
        AREA_A, OverlayFunctions.unionUsingGeometryCollection(a(), b()).getArea(),
        AREA_TOL);
  }

  // -- guards -------------------------------------------------------------

  /**
   * Guard on a deliberate exclusion: OverlayNGOpt is already exact via its
   * prepared-geometry short-circuit, and must not be densified.
   */
  public void testOverlayNGOptStaysExact() throws Exception {
    Geometry result = OverlayNGOptFunctions.intersection(a(), b());
    assertEquals("the short-circuit returns B itself, so this must be exact",
        AREA_B, result.getArea(), 1.0e-9);
    assertEquals("and it must still be the curve, not a densified copy",
        5, result.getNumPoints());
  }

  /**
   * Guard on the other deliberate exclusion: OverlayNGTest reports what core
   * sees, so its input stays raw. Ten control points in, twenty edge points out.
   */
  public void testOverlayNGTestStaysRaw() throws Exception {
    Geometry edges = OverlayNGTestFunctions.edgesNoded(a(), b(), SR_SCALE);
    assertEquals("noded edges should still be built from the control points",
        20, edges.getNumPoints());
  }

  /** Guard: plain polygons come through every family bit-for-bit. */
  public void testPlainPolygonsUnchanged() throws Exception {
    Geometry p = read("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    Geometry q = read("POLYGON ((5 5, 15 5, 15 15, 5 15, 5 5))");
    assertEquals("OverlayNG", 25.0,
        OverlayNGFunctions.intersection(p, q).getArea(), 0.0);
    assertEquals("Robust", 25.0,
        OverlayNGRobustFunctions.intersection(p, q).getArea(), 0.0);
    assertEquals("Strict", 25.0,
        OverlayNGStrictFunctions.intersection(p, q).getArea(), 0.0);
    assertEquals("NoSnap", 25.0,
        OverlayNoSnapFunctions.intersection(p, q).getArea(), 0.0);
    assertEquals("union via GeometryCollection", 175.0,
        OverlayFunctions.unionUsingGeometryCollection(p, q).getArea(), 0.0);
  }

  /** Guard: B inside A, so B less A is empty in every family. */
  public void testDifferenceBAEmpty() throws Exception {
    assertTrue("OverlayNG", OverlayNGFunctions.differenceBA(a(), b()).isEmpty());
    assertTrue("Robust", OverlayNGRobustFunctions.differenceBA(a(), b()).isEmpty());
    assertTrue("Strict", OverlayNGStrictFunctions.differenceBA(a(), b()).isEmpty());
  }
}
