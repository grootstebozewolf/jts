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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.overlayng.OverlayNG;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * R-AA: two CircularStrings (or a lineal CompoundCurve vs a
 * CircularString) noded at circle–circle hits that lie on both sweeps.
 * CAP is those Point(s). CUP / SUB keep CircularString pieces.
 * <p>
 * Named H-* tests are waypoints: each is a green assert that a failed
 * hypothesis stays refused.
 */
public class CircularArcOverlayTest extends GeometryTestCase {

  /**
   * Circle centre (5, −7/6), r = √949 / 6. Crossing partner meets this
   * arc at (2, 3) and (8, 3); the control polylines miss.
   */
  private static final String ARC_A =
      "CIRCULARSTRING (0 0, 2 3, 10 0)";
  /** Circle centre (5, 7), r = 5. */
  private static final String ARC_B =
      "CIRCULARSTRING (1 4, 5 2, 9 4)";
  /**
   * Nested circles: A is (3, −4) r=5, B is (3, −2) r=2. No
   * circle–circle nodes. The start–end chord of A meets B at (3, 0).
   */
  private static final String ARC_CHORD_ONLY_A =
      "CIRCULARSTRING (0 0, 3 1, 6 0)";
  private static final String ARC_CHORD_ONLY_B =
      "CIRCULARSTRING (1 -2, 3 0, 5 -2)";
  /** Same circle r=5 at the origin; they overlap on a quarter-arc. */
  private static final String ARC_SAME_Q1 =
      "CIRCULARSTRING (-5 0, 0 5, 5 0)";
  private static final String ARC_SAME_Q2 =
      "CIRCULARSTRING (0 5, 5 0, 0 -5)";
  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  /** Four line–circle nodes on CIRCLE_5 (y = ±1). */
  private static final String BAND_FOUR =
      "POLYGON ((-8 -1, 8 -1, 8 1, -8 1, -8 -1))";
  private static final String HALF_UPPER =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String HALF_LOWER =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 -5, 5 0), (5 0, -5 0)))";
  private static final String CHORD_ARC =
      "LINESTRING (0 0, 2 3, 10 0)";
  private static final String COMPOUND_A =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 2 3, 10 0))";

  private static final double EXACT = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(CircularArcOverlayTest.class);
  }

  public CircularArcOverlayTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testCrossingArcsCapIsExactCircleCircleNodes() throws Exception {
    Geometry a = readCurve(ARC_A);
    Geometry b = readCurve(ARC_B);
    OverlayNGCurve op = new OverlayNGCurve(a, b);
    Geometry laser = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("R-AA arc ∩ arc is exact", op.isApproximate());
    assertEquals("two nodes", 2, laser.getNumPoints());
    assertCrossingNodes(laser);
    assertFalse("lineal CAP is points, not a lens",
        laser instanceof CurvePolygon);

    Geometry viaInstance = a.intersection(b);
    assertEquals("Geometry.intersection routes (arc, arc)",
        2, viaInstance.getNumPoints());
    assertCrossingNodes(viaInstance);
  }

  public void testReverseOrderHitsTheSameLaser() throws Exception {
    Geometry a = readCurve(ARC_A);
    Geometry b = readCurve(ARC_B);
    OverlayNGCurve op = new OverlayNGCurve(b, a);
    Geometry laser = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("arc ∩ arc reverse is exact", op.isApproximate());
    assertEquals(2, laser.getNumPoints());
    assertCrossingNodes(laser);

    Geometry viaInstance = b.intersection(a);
    assertEquals(2, viaInstance.getNumPoints());
    assertCrossingNodes(viaInstance);
  }

  public void testUnionAndDifferenceKeepCircularStringPieces() throws Exception {
    Geometry a = readCurve(ARC_A);
    Geometry b = readCurve(ARC_B);
    double aLen = a.getLength();
    double bLen = b.getLength();

    OverlayNGCurve cup = new OverlayNGCurve(a, b);
    Geometry u = cup.getResult(OverlayNG.UNION);
    assertFalse("R-AA ∪ is exact", cup.isApproximate());
    assertTrue("union keeps an arc", hasCircularString(u));
    assertEquals("every piece is a CircularString",
        u.getNumGeometries(), countCircularStrings(u));
    assertEquals("union length is both (nodes have no measure)",
        aLen + bLen, u.getLength(), EXACT);

    OverlayNGCurve sub = new OverlayNGCurve(a, b);
    Geometry bite = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("R-AA arc \\ arc is exact", sub.isApproximate());
    assertTrue("difference keeps an arc", hasCircularString(bite));
    assertEquals("noding a point does not shorten the arc",
        aLen, bite.getLength(), EXACT);

    OverlayNGCurve rev = new OverlayNGCurve(b, a);
    Geometry other = rev.getResult(OverlayNG.DIFFERENCE);
    assertFalse("reverse difference is exact", rev.isApproximate());
    assertTrue(hasCircularString(other));
    assertEquals(bLen, other.getLength(), EXACT);
  }

  public void testCompoundCurveVsCircularString() throws Exception {
    Geometry cc = readCurve(COMPOUND_A);
    Geometry b = readCurve(ARC_B);
    OverlayNGCurve cap = new OverlayNGCurve(cc, b);
    Geometry nodes = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("R-AA compound ∩ arc is exact", cap.isApproximate());
    assertEquals(2, nodes.getNumPoints());
    assertCrossingNodes(nodes);
  }

  public void testLineStringOfThreePointsIsNotThisClass() throws Exception {
    Geometry chords = readCurve(CHORD_ARC);
    Geometry arc = readCurve(ARC_B);
    assertNull("a LineString of three points is not an arc",
        CircularArcOverlay.overlay(chords, arc, OverlayNG.INTERSECTION));
    assertNotNull("that pair stays on R-LL",
        CircularLineOverlay.overlay(chords, arc, OverlayNG.INTERSECTION));
  }

  /**
   * H-DISC: treating two CircularStrings as two filled discs (R1.5) is
   * false -- lineal CAP is points, not a lens.
   */
  public void testHDiscTwoArcsAreNotFilledDiscs() throws Exception {
    Geometry a = readCurve(ARC_A);
    Geometry b = readCurve(ARC_B);
    assertNull("H-DISC: CircularDiscOverlay does not fill two arcs",
        CircularDiscOverlay.overlay(a, b, OverlayNG.INTERSECTION));
    Geometry cap = CircularArcOverlay.overlay(a, b, OverlayNG.INTERSECTION);
    assertNotNull(cap);
    assertFalse("H-DISC: lineal CAP is not a CurvePolygon lens",
        cap instanceof CurvePolygon);
    assertEquals(2, cap.getNumPoints());
  }

  /**
   * H-CHORD: control-polyline nodes are not the arc nodes.
   */
  public void testHChordControlPolylineIsNotTheArc() throws Exception {
    Geometry missArcs = readCurve(ARC_CHORD_ONLY_A);
    Geometry missChords = readCurve(ARC_CHORD_ONLY_B);
    Geometry empty = CircularArcOverlay.overlay(
        missArcs, missChords, OverlayNG.INTERSECTION);
    assertNotNull("zero nodes is an answer, not a miss", empty);
    assertTrue("H-CHORD: chords meet, arcs miss → empty CAP", empty.isEmpty());

    OverlayNGCurve publicEmpty = new OverlayNGCurve(missArcs, missChords);
    Geometry viaPublic = publicEmpty.getResult(OverlayNG.INTERSECTION);
    assertFalse("chord-only miss is exact", publicEmpty.isApproximate());
    assertTrue(viaPublic.isEmpty());

    Geometry a = readCurve(ARC_A);
    Geometry b = readCurve(ARC_B);
    Geometry nodes = CircularArcOverlay.overlay(a, b, OverlayNG.INTERSECTION);
    assertNotNull(nodes);
    assertEquals("H-CHORD: arcs meet, chords miss → circle–circle points",
        2, nodes.getNumPoints());
    assertCrossingNodes(nodes);
  }

  /**
   * H-SAME-CIRCLE: two overlapping arcs of the same circle are not a
   * two-node clip. The null is the waypoint toward a same-circle
   * overlap laser; do not densify and call it closed-form.
   */
  public void testHSameCircleOverlapIsNotATwoNodeClip() throws Exception {
    Geometry a = readCurve(ARC_SAME_Q1);
    Geometry b = readCurve(ARC_SAME_Q2);
    assertNull("H-SAME-CIRCLE: overlapping same-circle arcs return null",
        CircularArcOverlay.overlay(a, b, OverlayNG.INTERSECTION));
    assertNull(CircularArcOverlay.overlay(a, b, OverlayNG.UNION));
  }

  /**
   * H-FOUR: 4+ areal cuts stay refused this slice.
   */
  public void testHFourArealCutsStayRefused() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry band = readCurve(BAND_FOUR);
    assertNull("H-FOUR: R1.6 refuses four line–circle nodes",
        CircularDiscPolygonOverlay.overlay(disc, band, OverlayNG.INTERSECTION));
    assertNull("H-FOUR: R1.7 refuses the same pair",
        CompoundCurveShellOverlay.overlay(disc, band, OverlayNG.INTERSECTION));
    assertNull("H-FOUR: R-AA is lineal",
        CircularArcOverlay.overlay(disc, band, OverlayNG.INTERSECTION));
  }

  /**
   * H-SHELL: two CompoundCurve shells stay refused this slice.
   */
  public void testHShellTwoCompoundCurveShellsStayRefused() throws Exception {
    Geometry upper = readCurve(HALF_UPPER);
    Geometry lower = readCurve(HALF_LOWER);
    assertNull("H-SHELL: two CompoundCurve shells are not R1.7",
        CompoundCurveShellOverlay.overlay(upper, lower, OverlayNG.INTERSECTION));
    assertNull("H-SHELL: R-AA is lineal",
        CircularArcOverlay.overlay(upper, lower, OverlayNG.INTERSECTION));
  }

  public void testRllStillRefusesTwoArcs() throws Exception {
    Geometry a = readCurve(ARC_A);
    Geometry b = readCurve(ARC_B);
    assertNull("R-LL's two-arc refusal is unchanged",
        CircularLineOverlay.overlay(a, b, OverlayNG.INTERSECTION));
  }

  private static void assertCrossingNodes(Geometry g) {
    assertTrue("left node (2, 3)", hasPointNear(g, 2.0, 3.0));
    assertTrue("right node (8, 3)", hasPointNear(g, 8.0, 3.0));
  }

  private static boolean hasPointNear(Geometry g, double x, double y) {
    Coordinate want = new Coordinate(x, y);
    for (int i = 0; i < g.getNumGeometries(); i++) {
      Geometry p = g.getGeometryN(i);
      if (p.getNumPoints() == 1 && p.getCoordinate().distance(want) <= EXACT) {
        return true;
      }
      Coordinate[] c = p.getCoordinates();
      for (int k = 0; k < c.length; k++) {
        if (c[k].distance(want) <= EXACT) return true;
      }
    }
    return false;
  }

  private static boolean hasCircularString(Geometry g) {
    return countCircularStrings(g) > 0;
  }

  private static int countCircularStrings(Geometry g) {
    int n = 0;
    for (int i = 0; i < g.getNumGeometries(); i++) {
      Geometry m = g.getGeometryN(i);
      if (m instanceof CircularString) n++;
      if (m instanceof CompoundCurve) {
        CompoundCurve cc = (CompoundCurve) m;
        for (int k = 0; k < cc.getNumMembers(); k++) {
          if (cc.getMemberN(k) instanceof CircularString) n++;
        }
      }
    }
    return n;
  }
}
