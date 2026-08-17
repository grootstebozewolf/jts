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
package org.locationtech.jts.operation.overlayng.curve;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.overlayng.OverlayNG;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * P2.0 B/C/D honesty locks:
 * <ul>
 * <li>B H-SHELL-N-MIXED — public OverlayNGCurve exact via OverlayNGCircle</li>
 * <li>C H-ANNULUS-TANGENT — kit null; public chordsaw (named refuse)</li>
 * <li>D H-DISC route — closed CircularString discs take R1.5</li>
 * </ul>
 */
public class OverlayNGCurveSeamsBCDTest extends GeometryTestCase {

  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String ON_DIAMETER =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 1, 0 2, 1 1), (1 1, 1 0), (1 0, -1 0), (-1 0, -1 1)))";
  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  /** Internally tangent nest: centres (0,0)/(2,0), r=5/3 → d+r = R. */
  private static final String CIRCLE_INT_TAN =
      "CURVEPOLYGON (CIRCULARSTRING (-1 0, 2 3, 5 0, 2 -3, -1 0))";
  private static final String CS_DISC_5 =
      "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)";
  private static final String CS_DISC_CROSS =
      "CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0)";

  public static void main(String[] args) {
    TestRunner.run(OverlayNGCurveSeamsBCDTest.class);
  }

  public OverlayNGCurveSeamsBCDTest(String name) {
    super(name);
  }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  /** B: R1.7 kit refuses; public OverlayNGCurve lasers via OverlayNGCircle. */
  public void testMixedPublicOverlayIsExactViaOverlayNGCircle() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry mixed = readCurve(ON_DIAMETER);
    assertNull("R1.7 kit refuses MIXED",
        CompoundCurveShellOverlay.overlay(half, mixed, OverlayNG.INTERSECTION));
    OverlayNGCurve cap = new OverlayNGCurve(half, mixed);
    Geometry r = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-N-MIXED public CAP is Option B laser", cap.isApproximate());
    assertTrue(r instanceof CurvePolygon);
    assertFalse(r.isEmpty());
  }

  /**
   * C: internal tangent nest is not a strict nest — kit null; public SUB
   * falls to chordsaw (named refuse, not a fake exact annulus).
   */
  public void testAnnulusTangentPublicIsApproximateRefuse() throws Exception {
    Geometry outer = readCurve(CIRCLE_5);
    Geometry tan = readCurve(CIRCLE_INT_TAN);
    assertNull("H-ANNULUS-TANGENT: disc kit does not punch",
        CircularDiscOverlay.overlay(outer, tan, OverlayNG.DIFFERENCE));
    OverlayNGCurve sub = new OverlayNGCurve(outer, tan);
    Geometry r = sub.getResult(OverlayNG.DIFFERENCE);
    assertTrue("H-ANNULUS-TANGENT: public overlay stays chordsaw",
        sub.isApproximate());
    assertNotNull(r);
  }

  /** D: closed CircularString discs route to CircularDiscOverlay. */
  public void testClosedCircularStringDiscsAreExactLens() throws Exception {
    Geometry a = readCurve(CS_DISC_5);
    Geometry b = readCurve(CS_DISC_CROSS);
    Geometry kit = CircularDiscOverlay.overlay(a, b, OverlayNG.INTERSECTION);
    assertNotNull(kit);
    assertTrue(kit instanceof CurvePolygon);
    OverlayNGCurve pub = new OverlayNGCurve(a, b);
    Geometry via = pub.getResult(OverlayNG.INTERSECTION);
    assertFalse(pub.isApproximate());
    assertTrue(via instanceof CurvePolygon);
  }
}
