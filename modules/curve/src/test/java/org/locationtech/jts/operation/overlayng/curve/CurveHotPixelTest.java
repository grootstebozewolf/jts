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

import org.locationtech.jts.geom.Coordinate;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * HP.2 / HP.3: {@link CurveHotPixel} arc ∩ pixel and
 * {@link CurveHotPixelSnap} grid headings (folded from pin stack).
 */
public class CurveHotPixelTest extends TestCase {

  private static final double HP2_SCALE = 10.0;
  private static final double LEAVE_ANGLE_EPS = 1.0e-8;

  public static void main(String[] args) {
    TestRunner.run(CurveHotPixelTest.class);
  }

  public CurveHotPixelTest(String name) {
    super(name);
  }

  public void testCurveHotPixelHitsLeaveArcsAtTangentNode() {
    CurveHotPixel pixel = new CurveHotPixel(c(0, 5), HP2_SCALE);
    assertTrue(pixel.intersects(c(0, 5)));
    assertTrue(pixel.intersects(halfDiscArc()));
    assertTrue(pixel.intersects(stadiumOddCap()));
  }

  public void testCurveHotPixelMissesHangingArc() {
    CurveHotPixel pixel = new CurveHotPixel(c(0, 5), HP2_SCALE);
    assertFalse(pixel.intersects(hangingArc()));
  }

  public void testCurveHotPixelIsNotChordFake() {
    CurveHotPixel pixel = new CurveHotPixel(c(0, 5), HP2_SCALE);
    assertFalse(pixel.intersects(CurveSegmentString.segment(c(-5, 0), c(5, 0))));
    assertFalse(pixel.intersects(CurveSegmentString.segment(c(-1, 4), c(1, 4))));
    assertTrue(pixel.intersects(halfDiscArc()));
  }

  public void testSnappedHeadingsShareRayAtScale10() {
    CurveHotPixel pixel = new CurveHotPixel(c(0, 5), HP2_SCALE);
    CurveSegmentString halfLeave = leaveFrom(halfDiscArc(), c(0, 5), c(5, 0));
    CurveSegmentString stadiumLeave = leaveFrom(stadiumOddCap(), c(0, 5),
        c(1, 4));
    CurveHotPixelSnap.Heading halfH = CurveHotPixelSnap.heading(pixel,
        halfLeave);
    CurveHotPixelSnap.Heading stadiumH = CurveHotPixelSnap.heading(pixel,
        stadiumLeave);
    assertNotNull(halfH);
    assertNotNull(stadiumH);
    assertEquals(halfH, stadiumH);
    assertEquals(CurveHotPixelSnap.SHARED_SNAPPED_RAY,
        CurveHotPixelSnap.sharedRayOrNull(halfH, stadiumH));
  }

  public void testHangingHasNoHeadingAtTangentPixel() {
    CurveHotPixel pixel = new CurveHotPixel(c(0, 5), HP2_SCALE);
    assertNull(CurveHotPixelSnap.heading(pixel, hangingArc()));
  }

  public void testUnsnappedLeavesStillCoincident() {
    CurveSegmentString halfLeave = leaveFrom(halfDiscArc(), c(0, 5), c(5, 0));
    CurveSegmentString stadiumLeave = leaveFrom(stadiumOddCap(), c(0, 5),
        c(1, 4));
    assertEquals(0.0, leaveAngle(halfLeave), LEAVE_ANGLE_EPS);
    assertEquals(0.0, leaveAngle(stadiumLeave), LEAVE_ANGLE_EPS);
  }

  private static Coordinate c(double x, double y) {
    return new Coordinate(x, y);
  }

  private static CurveSegmentString halfDiscArc() {
    return CurveSegmentString.arc(c(-5, 0), c(0, 5), c(5, 0));
  }

  private static CurveSegmentString stadiumOddCap() {
    return CurveSegmentString.arc(c(-1, 4), c(0, 5), c(1, 4));
  }

  private static CurveSegmentString hangingArc() {
    return CurveSegmentString.arc(c(-5, 8), c(0, 3), c(5, 8));
  }

  private static CurveSegmentString leaveFrom(CurveSegmentString full,
      Coordinate pinch, Coordinate toward) {
    Coordinate mid = TwoNodeClip.midOnSweep(pinch, toward, full.asEdge());
    return CurveSegmentString.arc(pinch, mid, toward);
  }

  private static double leaveAngle(CurveSegmentString s) {
    Coordinate from = s.getStart();
    if (!s.isArc()) {
      return Math.atan2(s.getEnd().y - from.y, s.getEnd().x - from.x);
    }
    TwoNodeClip.Edge e = s.asEdge();
    double rx = from.x - e.circle[0];
    double ry = from.y - e.circle[1];
    double a0 = Math.atan2(e.a.y - e.circle[1], e.a.x - e.circle[0]);
    double aM = Math.atan2(e.mid.y - e.circle[1], e.mid.x - e.circle[0]);
    double a1 = Math.atan2(e.b.y - e.circle[1], e.b.x - e.circle[0]);
    boolean ccw = TwoNodeClip.normPos(aM - a0) < TwoNodeClip.normPos(a1 - a0);
    return ccw ? Math.atan2(rx, -ry) : Math.atan2(-rx, ry);
  }

  /** HP.4: faces path stamps shared snapped ray — not a walk. */
  public void testFacesAfterSnapStampsSharedRay() throws Exception {
    org.locationtech.jts.io.curve.CurveWKTReader r =
        new org.locationtech.jts.io.curve.CurveWKTReader(
            new org.locationtech.jts.geom.curve.CurveGeometryFactory());
    org.locationtech.jts.geom.Geometry[] geoms =
        new org.locationtech.jts.geom.Geometry[] {
            r.read("CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))"),
            r.read("CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 8, 0 3, 5 8), (5 8, -5 8)))"),
            r.read("CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 4, 0 5, 1 4), (1 4, 1 -1), CIRCULARSTRING (1 -1, 0 -2, -1 -1), (-1 -1, -1 4)))")
        };
    assertNull(CurveSegmentFaces.faces(geoms));
    assertEquals(CurveSegmentFaces.SHARED_SNAPPED_RAY,
        CurveSegmentFaces.missReason());
  }
}
