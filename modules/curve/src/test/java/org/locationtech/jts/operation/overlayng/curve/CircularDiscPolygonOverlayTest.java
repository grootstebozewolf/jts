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

import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurveOps;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * R1.6: one circular disc clipped by a plain polygon at two line–circle
 * nodes. CAP / CUP / SUB / XOR keep the surviving arc, exact, and
 * JTS-class with the chord overlay. Anything else is {@code null} so
 * OverlayNGCurve can take R2 without paying this path first.
 * A covering square minus a concentric disc has 0 line–circle
 * nodes ({@code R1.6-honesty}): keep the miss, do not punch.
 */
public class CircularDiscPolygonOverlayTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_3 =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";
  /** Covering square: 0 line–circle nodes on CIRCLE_3. */
  private static final String PLAIN_SQUARE =
      "POLYGON ((-6 -6, 6 -6, 6 6, -6 6, -6 -6))";
  private static final String CIRCLE_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  /** Axis-aligned half-plane cut: the right half of CIRCLE_5. */
  private static final String SQUARE_RIGHT =
      "POLYGON ((0 -6, 10 -6, 10 6, 0 6, 0 -6))";
  /**
   * Vertex (3,0) inside the disc; the two slanted edges each cut one
   * proper chord node -- not a single cutting line.
   */
  private static final String TRIANGLE =
      "POLYGON ((3 0, 12 8, 12 -8, 3 0))";

  private static final double HALF = 12.5 * Math.PI;
  private static final double SQUARE = 120.0;
  private static final double AREA_TOL = 1.0e-3;
  private static final double EXACT = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(CircularDiscPolygonOverlayTest.class);
  }

  public CircularDiscPolygonOverlayTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testHalfDiscCapIsExactSemicircle() throws Exception {
    assertSquareOp(OverlayNG.INTERSECTION, HALF, 1);
  }

  public void testHalfDiscCupIsSquarePlusLeftCap() throws Exception {
    assertSquareOp(OverlayNG.UNION, SQUARE + HALF, 1);
  }

  public void testHalfDiscSubIsLeftCap() throws Exception {
    assertSquareOp(OverlayNG.DIFFERENCE, HALF, 1);
  }

  public void testHalfDiscXorIsSquare() throws Exception {
    assertSquareOp(OverlayNG.SYMDIFFERENCE, SQUARE, 2);
  }

  public void testReverseOrderHitsTheSameLaser() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry square = readCurve(SQUARE_RIGHT);

    OverlayNGCurve cap = new OverlayNGCurve(square, disc);
    Geometry r = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("plain ∩ disc is exact", cap.isApproximate());
    assertEquals("right half-disc", HALF, r.getArea(), EXACT);
    assertArcShell(r.getGeometryN(0));

    OverlayNGCurve sub = new OverlayNGCurve(square, disc);
    Geometry bite = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("plain \\ disc is exact", sub.isApproximate());
    assertEquals("square minus half-disc", SQUARE - HALF, bite.getArea(), EXACT);
    assertArcShell(bite);

    Geometry viaInstance = square.difference(disc);
    assertEquals("Geometry.difference routes (plain, disc)",
        SQUARE - HALF, viaInstance.getArea(), EXACT);
    assertTrue("reverse SUB keeps the arc",
        viaInstance instanceof CurvePolygon);
  }

  public void testTriangleTwoChordNodes() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry tri = readCurve(TRIANGLE);
    int[] ops = {
        OverlayNG.INTERSECTION, OverlayNG.UNION,
        OverlayNG.DIFFERENCE, OverlayNG.SYMDIFFERENCE
    };
    for (int i = 0; i < ops.length; i++) {
      OverlayNGCurve op = new OverlayNGCurve(disc, tri);
      Geometry laser = op.getResult(ops[i]);
      assertFalse("triangle op " + ops[i] + " is exact", op.isApproximate());
      assertFalse("triangle op " + ops[i] + " is non-empty", laser.isEmpty());
      assertArcShell(laser.getGeometryN(0));
      assertParity(disc, tri, ops[i], laser);
    }
    OverlayNGCurve rev = new OverlayNGCurve(tri, disc);
    Geometry revCap = rev.getResult(OverlayNG.INTERSECTION);
    assertFalse("tri ∩ disc is exact", rev.isApproximate());
    assertEquals("tri ∩ disc matches disc ∩ tri",
        OverlayNGCurve.intersection(disc, tri).getArea(),
        revCap.getArea(), EXACT);
  }

  /**
   * Named R1.6-honesty stamp. Covering PLAIN_SQUARE minus
   * CIRCLE_3 has 0 line–circle nodes, so this cell misses.
   * Public overlay stays the chordsaw. Do not expand R1.6 past
   * two-node / even-n. Not a disc-in-square nest punch. Not D4.
   */
  public void testR16HonestyCoveringSquareMinusDiscIsNamedMiss()
      throws Exception {
    Geometry square = readCurve(PLAIN_SQUARE);
    Geometry inner = readCurve(CIRCLE_3);
    assertNull("R1.6-honesty: overlay(PLAIN_SQUARE, CIRCLE_3, SUB) is null",
        CircularDiscPolygonOverlay.overlay(square, inner, OverlayNG.DIFFERENCE));
    OverlayNGCurve sub = new OverlayNGCurve(square, inner);
    Geometry saw = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("the chordsaw still answers", saw.isEmpty());
    assertTrue("R1.6-honesty: public isApproximate=true",
        sub.isApproximate());
  }

  public void testNotDiscAndPlainPolygonReturnsNull() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry other = readCurve(CIRCLE_CROSSING);
    Geometry square = readCurve(SQUARE_RIGHT);
    Geometry tri = readCurve(TRIANGLE);
    assertNull("two discs stay on R1.5",
        CircularDiscPolygonOverlay.overlay(disc, other, OverlayNG.INTERSECTION));
    assertNull("plain vs plain",
        CircularDiscPolygonOverlay.overlay(square, tri, OverlayNG.INTERSECTION));
    assertNull("and the other way",
        CircularDiscPolygonOverlay.overlay(tri, square, OverlayNG.UNION));
  }

  /**
   * The laser result keeps a CircularString, is not a densified ring,
   * and matches the chord overlay's topology and area.
   */
  private void assertSquareOp(int opCode, double exactArea, int parts)
      throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(SQUARE_RIGHT);
    OverlayNGCurve op = new OverlayNGCurve(a, b);
    Geometry laser = op.getResult(opCode);
    assertFalse("R1.6 is exact", op.isApproximate());
    assertEquals("exact closed-form area", exactArea, laser.getArea(), EXACT);
    assertEquals("member count", parts, laser.getNumGeometries());
    assertArcShell(laser.getGeometryN(0));
    if (parts == 2) assertArcShell(laser.getGeometryN(1));
    assertParity(a, b, opCode, laser);
  }

  private static void assertParity(Geometry a, Geometry b, int opCode,
      Geometry laser) {
    Geometry chord = OverlayNGRobust.overlay(
        CurveOps.linearise(a), CurveOps.linearise(b), opCode);
    assertEquals("area vs chord overlay", chord.getArea(), laser.getArea(),
        AREA_TOL);
    // equalsTopo is the wrong ask: the laser is the true arc, the chord
    // overlay is an inscribed ring. Hausdorff stays inside the densify
    // budget, which is the JTS-class claim.
    double hd = DiscreteHausdorffDistance.distance(
        CurveOps.linearise(laser), chord);
    assertTrue("Hausdorff vs chord overlay " + hd + " > " + AREA_TOL,
        hd <= AREA_TOL);
  }

  private static void assertArcShell(Geometry g) {
    assertEquals("CurvePolygon", g.getGeometryType());
    LineString shell = ((CurvePolygon) g).getExteriorCurve();
    assertTrue("shell keeps a circular arc, got " + shell.getGeometryType(),
        shell instanceof CompoundCurve || shell instanceof CircularString);
    if (shell instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) shell;
      boolean hasArc = false;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        if (cc.getMemberN(i) instanceof CircularString) hasArc = true;
      }
      assertTrue("CompoundCurve shell has a CircularString", hasArc);
    }
  }
}
