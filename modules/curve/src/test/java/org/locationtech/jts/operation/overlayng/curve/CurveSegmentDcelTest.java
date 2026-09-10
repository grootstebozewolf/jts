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

import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * P2.5.7 curve DCEL. Pins half-edge / twin / next / prev / incident
 * face on arrangements this stack already names: two-disc crossing,
 * MIXED shared-edge, STADIUM_FOUR N=3 (nine bounded faces). Stamps
 * coincident leave-angle and MIXED-hides-crossing. Not a noder,
 * not OverlayNG-for-circles, not a Geometry assembler.
 */
public class CurveSegmentDcelTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String HALF_HANGING =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 8, 0 3, 5 8), (5 8, -5 8)))";
  private static final String STADIUM_FOUR =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, 0 -2, 1 -1), (1 -1, 1 6), CIRCULARSTRING (1 6, 0 7, -1 6), (-1 6, -1 -1)))";
  private static final String STADIUM_ODD =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 4, 0 5, 1 4), (1 4, 1 -1), CIRCULARSTRING (1 -1, 0 -2, -1 -1), (-1 -1, -1 4)))";
  private static final String ON_DIAMETER =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 1, 0 2, 1 1), (1 1, 1 0), (1 0, -1 0), (-1 0, -1 1)))";
  private static final String HALF_CROSSING_UPPER =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (2 0, 7 5, 12 0), (12 0, 2 0)))";
  private static final String CIRCLE_INT_TAN =
      "CURVEPOLYGON (CIRCULARSTRING (-1 0, 2 3, 5 0, 2 -3, -1 0))";
  private static final String UNIT_DISC =
      "CURVEPOLYGON (CIRCULARSTRING (-1 0, 0 1, 1 0, 0 -1, -1 0))";
  private static final String UNIT_DISC_TOUCH =
      "CURVEPOLYGON (CIRCULARSTRING (1 0, 2 1, 3 0, 2 -1, 1 0))";
  private static final String HALF_HOLED =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0 1, 1 1, 1 2, 0 2, 0 1))";
  private static final String HOLE_X =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0.5 0.5, 1.5 0.5, 1.5 1.5, 0.5 1.5, 0.5 0.5))";

  private static final double EXACT = 1.0e-12;
  private static final double SQRT_12_75 = Math.sqrt(12.75);
  private static final double HALF = 12.5 * Math.PI;
  private static final double LENS = 50.0 * Math.acos(0.8) - 24.0;
  private static final double FOUR_CAP = 25.0 * Math.asin(0.2) + 2.0 * Math.sqrt(6.0);
  private static final double N3_TRIPLE = 2.0 * FOUR_CAP - 16.0;
  private static final double N3_LENS_SIDE = 0.5 * (LENS - N3_TRIPLE);
  private static final double N3_AC_NOT_B = 16.0 - FOUR_CAP;
  private static final double N3_BC_NOT_A = 12.0 + 0.5 * Math.PI - FOUR_CAP;
  private static final double N3_C_BOTTOM = 2.0 + 0.5 * Math.PI;
  private static final double N3_A_EAR = 0.5 * (HALF - LENS - N3_AC_NOT_B);
  private static final double N3_B_REST = HALF - LENS - N3_BC_NOT_A;
  private static final double N3_UNION = 25.5 * Math.PI - LENS + 2.0;

  public static void main(String[] args) {
    TestRunner.run(CurveSegmentDcelTest.class);
  }

  public CurveSegmentDcelTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  /**
   * R1.5 two-disc: sewn at the radical-axis pair. Twins reverse the
   * same arc; next/prev close; every half has a left face. Three
   * bounded cells (lens + two crescents) plus the exterior.
   */
  public void testTwoDiscCrossingPinsTwinsAndCycles() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_CROSSING);
    CurveSegmentDcel dcel = CurveSegmentDcel.of(new Geometry[] { a, b });
    assertNotNull("R1.5 DCEL", dcel);
    assertNull(CurveSegmentDcel.missReason());
    assertLinks(dcel);
    assertEquals("three bounded faces", 3, dcel.boundedFaces().size());
    assertTrue("plus the unbounded exterior", dcel.faces().size() >= 4);

    CurveSegmentDcel.Half alongA = findHalfNear(dcel, 3.5, SQRT_12_75,
        5.0, 0.0);
    assertNotNull("half from the upper node along CIRCLE_5", alongA);
    assertTrue("member stays an arc", alongA.isArc());
    assertTwinReverses(alongA);
    assertEquals("upper node is degree 4", 4,
        vertexAt(dcel, 3.5, SQRT_12_75).degree());
    assertEquals("lower node is degree 4", 4,
        vertexAt(dcel, 3.5, -SQRT_12_75).degree());

    List<List<CurveSegmentString>> groups = Arrays.asList(
        CurveSegmentString.of(a), CurveSegmentString.of(b));
    CurveSegmentDcel viaStrings = CurveSegmentDcel.of(groups, 12.0);
    assertNotNull(viaStrings);
    assertLinks(viaStrings);
    assertEquals(3, viaStrings.boundedFaces().size());
  }

  /**
   * H-SHELL-N-MIXED: nodes stay null (the overlap is an interval).
   * The named diameter is a shared edge. T-junctions at (±1, 0)
   * are degree 3. Inner + bite are the bounded cells.
   */
  public void testMixedSharedEdgePinsTwinsAndCycles() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry on = readCurve(ON_DIAMETER);
    assertNull("MIXED nodes stay null", CurveSegmentNoder.nodes(half, on));
    CurveSegmentDcel dcel = CurveSegmentDcel.of(new Geometry[] { half, on });
    assertNotNull("MIXED DCEL from the named interval", dcel);
    assertNull(CurveSegmentDcel.missReason());
    assertLinks(dcel);
    assertEquals("inner + bite", 2, dcel.boundedFaces().size());
    assertHasBoundedArea(dcel, 2.0 + 0.5 * Math.PI);
    assertHasBoundedArea(dcel, HALF - 2.0 - 0.5 * Math.PI);

    CurveSegmentDcel.Vertex left = vertexAt(dcel, -1.0, 0.0);
    CurveSegmentDcel.Vertex right = vertexAt(dcel, 1.0, 0.0);
    assertEquals("T-junction at (-1 0)", 3, left.degree());
    assertEquals("T-junction at (1 0)", 3, right.degree());

    CurveSegmentDcel.Half shared = findChordHalf(dcel, -1.0, 0.0, 1.0, 0.0);
    assertNotNull("shared diameter half", shared);
    assertFalse("shared run stays a chord", shared.isArc());
    assertTwinReverses(shared);
    assertTrue("twin faces differ across the shared edge",
        shared.face() != shared.twin().face());
  }

  /**
   * HALF_DISC × HALF_HANGING × STADIUM_FOUR. Faces already names
   * nine bounded cells. The DCEL is that walk with twins and
   * cycle links, not another Geometry assembler.
   */
  public void testStadiumFourN3PinsNineFaces() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(HALF_HANGING);
    Geometry c = readCurve(STADIUM_FOUR);
    CurveSegmentDcel dcel = CurveSegmentDcel.of(new Geometry[] { a, b, c });
    assertNotNull("N=3 DCEL", dcel);
    assertNull(CurveSegmentDcel.missReason());
    assertLinks(dcel);
    assertEquals("nine bounded faces", 9, dcel.boundedFaces().size());
    assertHasBoundedArea(dcel, N3_TRIPLE);
    assertHasBoundedArea(dcel, N3_LENS_SIDE);
    assertHasBoundedArea(dcel, N3_AC_NOT_B);
    assertHasBoundedArea(dcel, N3_BC_NOT_A);
    assertHasBoundedArea(dcel, N3_C_BOTTOM);
    assertHasBoundedArea(dcel, N3_A_EAR);
    assertHasBoundedArea(dcel, N3_B_REST);
    double bounded = 0.0;
    List<CurveSegmentDcel.Face> cells = dcel.boundedFaces();
    for (int i = 0; i < cells.size(); i++) {
      bounded += cells.get(i).signedArea();
    }
    assertEquals("bounded cells fill the union", N3_UNION, bounded, 1.0e-8);

    List<List<CurveSegmentString>> groups = Arrays.asList(
        CurveSegmentString.of(a), CurveSegmentString.of(b),
        CurveSegmentString.of(c));
    CurveSegmentDcel viaStrings = CurveSegmentDcel.of(groups, 16.0);
    assertNotNull(viaStrings);
    assertLinks(viaStrings);
    assertEquals(9, viaStrings.boundedFaces().size());
  }

  /**
   * HALF_DISC × STADIUM_FOUR is a sewn 4-node pair. Members stay
   * arc or chord; twins and cycles close.
   */
  public void testStadiumFourPairPinsTwins() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry stadium = readCurve(STADIUM_FOUR);
    CurveSegmentDcel dcel = CurveSegmentDcel.of(
        new Geometry[] { half, stadium });
    assertNotNull(dcel);
    assertLinks(dcel);
    assertTrue("CAP + XOR cells", dcel.boundedFaces().size() >= 3);
    boolean sawArc = false;
    boolean sawChord = false;
    List<CurveSegmentDcel.Half> halves = dcel.halves();
    for (int i = 0; i < halves.size(); i++) {
      if (halves.get(i).isArc()) {
        sawArc = true;
      }
      else {
        sawChord = true;
      }
    }
    assertTrue("arc member survives", sawArc);
    assertTrue("chord member survives", sawChord);
  }

  /**
   * HALF_DISC × HALF_HANGING × STADIUM_ODD: coincident leave-angle
   * at the tangent. Snap-rounding, not a HotPixel. Named stamp.
   */
  public void testTangentLeaveAngleStampsNull() throws Exception {
    CurveSegmentDcel dcel = CurveSegmentDcel.of(new Geometry[] {
        readCurve(HALF_DISC), readCurve(HALF_HANGING),
        readCurve(STADIUM_ODD) });
    assertNull("N≥3 near-tangent is P2.5.4, not a DCEL", dcel);
    assertEquals("snap-rounding: coincident leave-angle",
        CurveSegmentDcel.TANGENT_LEAVE_ANGLE,
        CurveSegmentDcel.missReason());
  }

  /**
   * Locationtech #1224 / #1226 on this walk, not on core
   * HalfEdge / Quadrant. Subtracted leave-vectors can make
   * (1 1)→(0 0.5) and (1 1)→(0 0.49999999999999994) look equal
   * under atan2; endpoint quadrant + orientation keeps them
   * distinct and antisymmetric. Not a TANGENT stamp.
   */
  public void testLeaveAngleCompareRobust() {
    Coordinate o = new Coordinate(1, 1);
    CurveSegmentDcel.Half upper = chordHalf(o, 0, 0.5);
    CurveSegmentDcel.Half lower = chordHalf(o, 0, 0.49999999999999994);
    CurveSegmentDcel.Half north = chordHalf(o, 0, 1);
    assertTrue("edges with distinct direction points must not compare equal",
        CurveSegmentDcel.compareLeave(upper, lower) != 0);
    assertTrue("leave comparison must be antisymmetric",
        CurveSegmentDcel.compareLeave(upper, lower)
            == -CurveSegmentDcel.compareLeave(lower, upper));
    assertTrue("north is a different leave",
        CurveSegmentDcel.compareLeave(upper, north) != 0);
    assertTrue("distinct direction points are not a TANGENT stamp",
        !CurveSegmentDcel.leavesCoincide(upper, lower));
    assertEquals("same-ray leave is coincident",
        0, CurveSegmentDcel.compareLeave(upper, chordHalf(o, -1, 0)));
  }

  /**
   * HALF_DISC and STADIUM_ODD leave (0 5) on the same east
   * tangent. The robust compare still ties, so the N=3 walk
   * keeps the TANGENT_LEAVE_ANGLE stamp.
   */
  public void testArcLeaveTangentStillCoincident() {
    Coordinate o = new Coordinate(0, 5);
    double s = Math.sqrt(0.5);
    CurveSegmentString disc = CurveSegmentString.arc(o,
        new Coordinate(5 * s, 5 * s), new Coordinate(5, 0));
    CurveSegmentString cap = CurveSegmentString.arc(o,
        new Coordinate(s, 4 + s), new Coordinate(1, 4));
    CurveSegmentDcel.Half a = new CurveSegmentDcel.Half(o, disc.getEnd(),
        disc);
    CurveSegmentDcel.Half b = new CurveSegmentDcel.Half(o, cap.getEnd(),
        cap);
    assertTrue("both pieces stay arcs", a.isArc() && b.isArc());
    assertTrue("same leave tangent at (0 5)",
        CurveSegmentDcel.leavesCoincide(a, b));
  }

  /**
   * HALF_DISC × HALF_CROSSING_UPPER: collinear diameters abort the
   * node set and hide the arc–arc lens nodes. Generic
   * {@code nodes==null} walk is unsafe. Named stamp, not a noder.
   */
  public void testMixedHidesCrossingStampsNull() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(HALF_CROSSING_UPPER);
    assertNull("collinear pair aborts the node set",
        CurveSegmentNoder.nodes(a, b));
    List<CurveSegmentString> edges = CurveSegmentNoder.edges(a, b);
    assertNotNull(edges);
    assertTrue("noder still names the diameter overlap", !edges.isEmpty());
    CurveSegmentDcel dcel = CurveSegmentDcel.of(new Geometry[] { a, b });
    assertNull("do not sew a DCEL over a hidden crossing", dcel);
    assertEquals(CurveSegmentDcel.MIXED_HIDES_CROSSING,
        CurveSegmentDcel.missReason());
  }

  /**
   * onString stays in R². A flat (colinear) triple is a chord
   * whose sagitta residual is 0. A high-sagitta arc accepts an
   * on-circle point by {@code |dx²+dy² − R²| ≤ eps²}, not
   * {@code hypot(d) − R} and not a sagitta quotient.
   * leaveAngle / compareLeave are untouched.
   */
  public void testOnStringExtremeSagittasStayInR2() {
    double eps = 1.0e-9;
    double eps2 = eps * eps;

    CurveSegmentString flat = CurveSegmentString.arc(
        new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(2, 0));
    assertFalse("flat sagitta is a chord", flat.isArc());
    Coordinate flatMid = new Coordinate(1, 0);
    assertTrue("flat sagitta → 0: midpoint is on the chord",
        CurveSegmentDcel.onString(flat, flatMid, eps));
    double flatDx = flatMid.x - 1.0;
    double flatDy = flatMid.y - 0.0;
    assertEquals("flat sagitta residual is 0 in R²",
        0.0, flatDx * flatDx + flatDy * flatDy, 0.0);
    assertTrue("on-chord within eps²",
        CurveSegmentDcel.onString(flat, new Coordinate(1, 0.5 * eps), eps));
    assertFalse("off-chord by more than eps",
        CurveSegmentDcel.onString(flat, new Coordinate(1, 2.0 * eps), eps));

    CurveSegmentString high = CurveSegmentString.arc(
        new Coordinate(1, 0), new Coordinate(-1, 0),
        new Coordinate(0.6, 0.8));
    assertTrue("high-sagitta stays an arc", high.isArc());
    TwoNodeClip.Edge e = high.asEdge();
    double r2 = e.circle[2] * e.circle[2];
    Coordinate apex = high.getMid();
    double dx = apex.x - e.circle[0];
    double dy = apex.y - e.circle[1];
    assertTrue("high-sagitta mid residual is in eps² of R²",
        Math.abs(dx * dx + dy * dy - r2) <= eps2);
    assertTrue("high-sagitta mid is onString",
        CurveSegmentDcel.onString(high, apex, eps));
    assertTrue("high-sagitta end is onString",
        CurveSegmentDcel.onString(high, high.getEnd(), eps));
    assertFalse("centre is not on the high-sagitta arc",
        CurveSegmentDcel.onString(high,
            new Coordinate(e.circle[0], e.circle[1]), eps));
  }

  /**
   * Pinch / kiss / holed Geometry-level stay null. Not a face,
   * not a DCEL. No invented noder.
   */
  public void testPinchKissHoledStayNull() throws Exception {
    assertNull("H-ANNULUS-TANGENT: pinch is not a DCEL",
        CurveSegmentDcel.of(new Geometry[] {
            readCurve(CIRCLE_5), readCurve(CIRCLE_INT_TAN) }));
    assertNull("TOUCH-ext: kiss is not a DCEL",
        CurveSegmentDcel.of(new Geometry[] {
            readCurve(UNIT_DISC), readCurve(UNIT_DISC_TOUCH) }));
    assertNull("H-SHELL-HOLE-X: holed Geometry-level stays null",
        CurveSegmentDcel.of(new Geometry[] {
            readCurve(HALF_HOLED), readCurve(HOLE_X) }));
  }

  private static void assertLinks(CurveSegmentDcel dcel) {
    assertNotNull(dcel);
    List<CurveSegmentDcel.Half> halves = dcel.halves();
    assertTrue("has half-edges", !halves.isEmpty());
    double eps = dcel.eps();
    for (int i = 0; i < halves.size(); i++) {
      CurveSegmentDcel.Half h = halves.get(i);
      assertNotNull("twin", h.twin());
      assertSame("twin.twin", h, h.twin().twin());
      assertNotNull("next", h.next());
      assertNotNull("prev", h.prev());
      assertSame("next.prev", h, h.next().prev());
      assertSame("prev.next", h, h.prev().next());
      assertNotNull("incident face", h.face());
      assertNotNull("member", h.member());
      assertTrue("twin dest is origin",
          h.origin().distance(h.twin().dest()) <= eps);
      assertTrue("twin origin is dest",
          h.dest().distance(h.twin().origin()) <= eps);
      assertCycleCloses(h, halves.size());
    }
  }

  private static void assertCycleCloses(CurveSegmentDcel.Half start, int guard) {
    CurveSegmentDcel.Half cur = start;
    int n = 0;
    boolean closed = false;
    while (n++ < guard && !closed) {
      cur = cur.next();
      if (cur == start) {
        closed = true;
      }
    }
    assertTrue("next-cycle closes", closed);
    cur = start;
    n = 0;
    closed = false;
    while (n++ < guard && !closed) {
      cur = cur.prev();
      if (cur == start) {
        closed = true;
      }
    }
    assertTrue("prev-cycle closes", closed);
  }

  private static void assertTwinReverses(CurveSegmentDcel.Half h) {
    assertNotNull(h);
    CurveSegmentDcel.Half t = h.twin();
    assertNotNull(t);
    assertSame(h, t.twin());
    assertEquals(h.isArc(), t.isArc());
    if (h.isArc()) {
      assertEquals(h.member().getMid().x, t.member().getMid().x, EXACT);
      assertEquals(h.member().getMid().y, t.member().getMid().y, EXACT);
    }
  }

  private static void assertHasBoundedArea(CurveSegmentDcel dcel, double area) {
    boolean found = false;
    List<CurveSegmentDcel.Face> cells = dcel.boundedFaces();
    for (int i = 0; i < cells.size() && !found; i++) {
      if (Math.abs(cells.get(i).signedArea() - area) <= 1.0e-8) {
        found = true;
      }
    }
    assertTrue("missing bounded area " + area, found);
  }

  private static CurveSegmentDcel.Vertex vertexAt(CurveSegmentDcel dcel,
      double x, double y) {
    Coordinate want = new Coordinate(x, y);
    CurveSegmentDcel.Vertex found = null;
    List<CurveSegmentDcel.Vertex> verts = dcel.vertices();
    for (int i = 0; i < verts.size() && found == null; i++) {
      if (verts.get(i).coordinate().distance(want) <= 1.0e-8) {
        found = verts.get(i);
      }
    }
    assertNotNull("missing vertex (" + x + " " + y + ")", found);
    return found;
  }

  private static CurveSegmentDcel.Half chordHalf(Coordinate origin,
      double x, double y) {
    Coordinate dest = new Coordinate(x, y);
    return new CurveSegmentDcel.Half(origin, dest,
        CurveSegmentString.segment(origin, dest));
  }

  private static CurveSegmentDcel.Half findHalfNear(CurveSegmentDcel dcel,
      double x0, double y0, double x1, double y1) {
    Coordinate a = new Coordinate(x0, y0);
    Coordinate b = new Coordinate(x1, y1);
    CurveSegmentDcel.Half found = null;
    List<CurveSegmentDcel.Half> halves = dcel.halves();
    for (int i = 0; i < halves.size() && found == null; i++) {
      CurveSegmentDcel.Half h = halves.get(i);
      if (h.origin().distance(a) <= 1.0e-8
          && h.dest().distance(b) <= 1.0e-6) {
        found = h;
      }
    }
    return found;
  }

  private static CurveSegmentDcel.Half findChordHalf(CurveSegmentDcel dcel,
      double x0, double y0, double x1, double y1) {
    Coordinate p = new Coordinate(x0, y0);
    Coordinate q = new Coordinate(x1, y1);
    CurveSegmentDcel.Half found = null;
    List<CurveSegmentDcel.Half> halves = dcel.halves();
    for (int i = 0; i < halves.size() && found == null; i++) {
      CurveSegmentDcel.Half h = halves.get(i);
      if (h.isArc()) {
        continue;
      }
      boolean ends = h.origin().distance(p) <= EXACT
              && h.dest().distance(q) <= EXACT
          || h.origin().distance(q) <= EXACT
              && h.dest().distance(p) <= EXACT;
      if (ends) {
        found = h;
      }
    }
    return found;
  }
}
