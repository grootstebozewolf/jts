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
 * FCP-TL: {@link CurvePolygon#toLinear(double)} must walk the structural
 * rings and densify them, honouring the tolerance.
 * <p>
 * The implementation copies {@code getExteriorRing()} and
 * {@code getInteriorRingN(i)} -- the flat control-point view -- and ignores
 * its {@code tolerance} argument entirely. So linearising an arc polygon
 * yields the chord polygon through the control points at every tolerance,
 * which is both a poor approximation and silently insensitive to the
 * accuracy the caller asked for.
 * <p>
 * {@code CircularString.toLinear(tolerance)} already densifies correctly;
 * FCP-S and FCP-H make the structural rings reachable, so CurvePolygon can
 * delegate per ring.
 */
public class CurvePolygonToLinearTest extends GeometryTestCase {

  /** Semicircle-pair shell: 5 control points describing two arcs. */
  private static final String ARC_SHELL =
      "CURVEPOLYGON (CIRCULARSTRING (0 0, 4 0, 4 4, 0 4, 0 0))";

  public static void main(String[] args) {
    TestRunner.run(CurvePolygonToLinearTest.class);
  }

  public CurvePolygonToLinearTest(String name) { super(name); }

  private static CurvePolygon readCP(String wkt) throws Exception {
    return (CurvePolygon) new CurveWKTReader().read(wkt);
  }

  /** Linearising an arc shell densifies well beyond the control points. */
  public void testArcShellIsDensified() throws Exception {
    CurvePolygon g = readCP(ARC_SHELL);
    Geometry flat = g.toLinear(0.01);
    assertTrue("expected densification beyond the 5 control points, got "
        + flat.getNumPoints() + " points", flat.getNumPoints() > 8);
  }

  /** A tighter tolerance must yield a finer approximation. */
  public void testToleranceIsHonoured() throws Exception {
    int coarse = readCP(ARC_SHELL).toLinear(1.0).getNumPoints();
    int fine = readCP(ARC_SHELL).toLinear(0.001).getNumPoints();
    assertTrue("tolerance 0.001 should densify more than 1.0, got "
        + fine + " vs " + coarse, fine > coarse);
  }

  /** Arc holes are densified too, not just the shell. */
  public void testArcHoleIsDensified() throws Exception {
    CurvePolygon g = readCP("CURVEPOLYGON ("
        + "CIRCULARSTRING (0 0, 8 0, 8 8, 0 8, 0 0), "
        + "CIRCULARSTRING (2 2, 4 2, 4 4, 2 4, 2 2))");
    org.locationtech.jts.geom.Polygon flat =
        (org.locationtech.jts.geom.Polygon) g.toLinear(0.01);
    assertEquals("hole should be preserved", 1, flat.getNumInteriorRing());
    assertTrue("hole should be densified, got "
        + flat.getInteriorRingN(0).getNumPoints() + " points",
        flat.getInteriorRingN(0).getNumPoints() > 8);
  }

  /** The result is a plain Polygon -- linearising drops curve identity. */
  public void testResultIsPlainPolygon() throws Exception {
    Geometry flat = readCP(ARC_SHELL).toLinear(0.01);
    assertEquals("Polygon", flat.getGeometryType());
  }

  /**
   * A densified arc shell is sandwiched between the chord polygon through its
   * control points and the exact arc area: densifying an outward-bulging arc
   * recovers area the chords miss, but an inscribed polygon can never reach
   * the true arc area.
   */
  public void testDensifiedAreaLiesBetweenChordAndExact() throws Exception {
    CurvePolygon g = readCP(ARC_SHELL);
    // getExteriorRing() is the flat control-point view, so this is the chord
    // polygon; getArea() is the exact arc area (CRV-AREA).
    double chordArea = g.getFactory().createPolygon(g.getExteriorRing()).getArea();
    double exactArea = g.getArea();
    double flatArea = g.toLinear(0.01).getArea();
    assertTrue("densified area " + flatArea
        + " should exceed the chord area " + chordArea,
        flatArea > chordArea);
    assertTrue("densified area " + flatArea
        + " cannot exceed the exact arc area " + exactArea,
        flatArea <= exactArea + 1.0e-9);
  }

  /** An all-linear CurvePolygon linearises to the same ring, unchanged. */
  public void testLinearPolygonUnchanged() throws Exception {
    CurvePolygon g = readCP("CURVEPOLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))");
    Geometry flat = g.toLinear(0.01);
    assertEquals("Polygon", flat.getGeometryType());
    assertEquals("linear ring should be untouched", 5, flat.getNumPoints());
  }
}
