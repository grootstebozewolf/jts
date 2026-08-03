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

import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * FCP-S: a {@link CurvePolygon} read from an arc shell keeps that arc
 * available structurally.
 * <p>
 * Follows Option A of {@code SPEC_F_CP.md}: {@code Polygon.getExteriorRing()}
 * keeps returning a linearised {@link org.locationtech.jts.geom.LinearRing}
 * so existing jts-core callers are unaffected, and curve-aware callers reach
 * the real shell through {@link CurvePolygon#getExteriorCurve()}.
 * <p>
 * Scope is the shell only. Interior-ring symmetry (FCP-H, a parallel
 * {@code getInteriorCurveN}) and the writer emitting the arc tag (FCP-WKT)
 * are separate sub-issues and are deliberately not asserted here.
 * <p>
 * Before this behaviour exists, {@code WKTCurvePolygonTest} does not catch
 * the gap: it asserts only type name, dimension and emptiness, and its
 * round-trip check passes vacuously because a linearised ring round-trips
 * to an equal linearised ring.
 */
public class CurvePolygonArcRingTest extends GeometryTestCase {

  private static final String ARC_SHELL =
      "CURVEPOLYGON (CIRCULARSTRING (0 0, 4 0, 4 4, 0 4, 0 0))";

  public static void main(String[] args) {
    TestRunner.run(CurvePolygonArcRingTest.class);
  }

  public CurvePolygonArcRingTest(String name) { super(name); }

  /** Option A: the arc shell is reachable structurally. */
  public void testExteriorCurveKeepsArcType() throws Exception {
    CurvePolygon g = (CurvePolygon) new CurvedWKTReader().read(ARC_SHELL);
    assertEquals("shell of " + ARC_SHELL + " should stay an arc",
        "CircularString", g.getExteriorCurve().getGeometryType());
  }

  /** A CompoundCurve shell is preserved as a CompoundCurve. */
  public void testExteriorCurveKeepsCompoundCurveType() throws Exception {
    CurvePolygon g = (CurvePolygon) new CurvedWKTReader().read(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 0, 1 1, 2 0), (2 0, 0 0)))");
    assertEquals("compound shell should stay a CompoundCurve",
        "CompoundCurve", g.getExteriorCurve().getGeometryType());
  }

  /**
   * Option A's compatibility half: legacy callers still see a flat
   * LinearRing, with the arc's coordinates.
   */
  public void testExteriorRingStaysLinearForLegacyCallers() throws Exception {
    Polygon g = (Polygon) new CurvedWKTReader().read(ARC_SHELL);
    assertEquals("legacy view must remain a LinearRing",
        "LinearRing", g.getExteriorRing().getGeometryType());
    assertTrue("linearised shell should still be closed",
        g.getExteriorRing().isClosed());
  }

  /** FCP-H: an arc hole keeps its arc identity, symmetrically with the shell. */
  public void testInteriorCurveKeepsArcType() throws Exception {
    CurvePolygon g = (CurvePolygon) new CurvedWKTReader().read(
        "CURVEPOLYGON ("
        + "CIRCULARSTRING (0 0, 8 0, 8 8, 0 8, 0 0), "
        + "CIRCULARSTRING (2 2, 4 2, 4 4, 2 4, 2 2))");
    assertEquals("expected exactly one hole", 1, g.getNumInteriorRing());
    assertEquals("hole should stay an arc",
        "CircularString", g.getInteriorCurveN(0).getGeometryType());
  }

  /** FCP-H: the legacy hole view stays a LinearRing. */
  public void testInteriorRingStaysLinearForLegacyCallers() throws Exception {
    Polygon g = (Polygon) new CurvedWKTReader().read(
        "CURVEPOLYGON ("
        + "CIRCULARSTRING (0 0, 8 0, 8 8, 0 8, 0 0), "
        + "CIRCULARSTRING (2 2, 4 2, 4 4, 2 4, 2 2))");
    assertEquals("legacy hole view must remain a LinearRing",
        "LinearRing", g.getInteriorRingN(0).getGeometryType());
  }

  /**
   * A plain linear shell is not relabelled as an arc -- guards the fix from
   * over-reaching.
   * <p>
   * {@code getExteriorCurve()} is contractually allowed to hand back a plain
   * {@link org.locationtech.jts.geom.LineString} here rather than a
   * {@link org.locationtech.jts.geom.LinearRing}: the accessor returns the
   * member as parsed, and {@code readCurveMember} does not promote a closed
   * linear member to a ring. So this asserts linearity, not the exact class.
   */
  public void testLinearShellIsNotRelabelledAsArc() throws Exception {
    CurvePolygon g = (CurvePolygon) new CurvedWKTReader().read(
        "CURVEPOLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))");
    String structural = g.getExteriorCurve().getGeometryType();
    assertTrue("plain ring must stay linear, was " + structural,
        "LinearRing".equals(structural) || "LineString".equals(structural));
    assertEquals("legacy view is still a LinearRing",
        "LinearRing", g.getExteriorRing().getGeometryType());
  }
}
