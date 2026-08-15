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
 * R1.7: a CompoundCurve-shelled CurvePolygon (half-disc) clipped by a
 * circular disc or a plain polygon at two nodes. CAP / CUP / SUB / XOR
 * keep the surviving arc, exact, and JTS-class with the chord overlay.
 * A three-point LineString is not an arc. Anything else is {@code null}
 * so OverlayNGCurve can take R2 without paying this path first.
 */
public class CompoundCurveShellOverlayTest extends GeometryTestCase {

  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  /** Axis-aligned cap cut: y = 2 through the upper half-disc. */
  private static final String SQUARE_CAP =
      "POLYGON ((-6 2, 6 2, 6 10, -6 10, -6 2))";
  /**
   * Same outline as {@link #HALF_DISC} but the "semicircle" is a
   * three-point LineString. That member is two chords, not an arc.
   */
  private static final String CHORD_SHELL =
      "CURVEPOLYGON (COMPOUNDCURVE ((-5 0, 0 5, 5 0), (5 0, -5 0)))";

  /** Upper half of the two-disc lens (r=5, d=7). */
  private static final double HALF_LENS =
      25.0 * Math.acos(0.7) - 0.25 * 7.0 * Math.sqrt(51.0);
  /** Circular cap of CIRCLE_5 above y=2: 25 acos(0.4) − 2√21. */
  private static final double CAP =
      25.0 * Math.acos(0.4) - 2.0 * Math.sqrt(21.0);
  private static final double HALF = 12.5 * Math.PI;
  private static final double DISC = 25.0 * Math.PI;
  private static final double SQUARE = 96.0;
  private static final double AREA_TOL = 1.0e-3;
  private static final double EXACT = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(CompoundCurveShellOverlayTest.class);
  }

  public CompoundCurveShellOverlayTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testHalfDiscCapVsCrossingDiscKeepsTheArc() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_CROSSING);
    OverlayNGCurve op = new OverlayNGCurve(half, disc);
    Geometry laser = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("R1.7 half ∩ disc is exact", op.isApproximate());
    assertEquals("upper half-lens", HALF_LENS, laser.getArea(), EXACT);
    assertArcAndLineShell(laser);
    assertParity(half, disc, OverlayNG.INTERSECTION, laser);

    Geometry viaInstance = half.intersection(disc);
    assertEquals("Geometry.intersection routes (half, disc)",
        HALF_LENS, viaInstance.getArea(), EXACT);
    assertArcAndLineShell(viaInstance);
  }

  public void testHalfDiscCapVsSquareIsCompoundCurveNotNGon() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry square = readCurve(SQUARE_CAP);
    OverlayNGCurve op = new OverlayNGCurve(half, square);
    Geometry laser = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("R1.7 half ∩ square is exact", op.isApproximate());
    assertEquals("circular cap above y=2", CAP, laser.getArea(), EXACT);
    assertArcAndLineShell(laser);
    assertParity(half, square, OverlayNG.INTERSECTION, laser);
  }

  public void testReverseOrderHitsTheSameLaser() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_CROSSING);
    Geometry square = readCurve(SQUARE_CAP);

    OverlayNGCurve vsDisc = new OverlayNGCurve(disc, half);
    Geometry capDisc = vsDisc.getResult(OverlayNG.INTERSECTION);
    assertFalse("disc ∩ half is exact", vsDisc.isApproximate());
    assertEquals("reverse half ∩ disc", HALF_LENS, capDisc.getArea(), EXACT);
    assertArcAndLineShell(capDisc);

    OverlayNGCurve vsSquare = new OverlayNGCurve(square, half);
    Geometry capSq = vsSquare.getResult(OverlayNG.INTERSECTION);
    assertFalse("square ∩ half is exact", vsSquare.isApproximate());
    assertEquals("reverse half ∩ square", CAP, capSq.getArea(), EXACT);
    assertArcAndLineShell(capSq);
  }

  public void testHalfDiscCupVsCrossingDisc() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_CROSSING);
    OverlayNGCurve op = new OverlayNGCurve(half, disc);
    Geometry u = op.getResult(OverlayNG.UNION);
    assertFalse("R1.7 half ∪ disc is exact", op.isApproximate());
    assertEquals("half plus disc minus upper half-lens",
        HALF + DISC - HALF_LENS, u.getArea(), EXACT);
    assertArcShell(u);
    assertParity(half, disc, OverlayNG.UNION, u);
  }

  public void testHalfDiscSubBiteVsCrossingDisc() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_CROSSING);
    OverlayNGCurve op = new OverlayNGCurve(half, disc);
    Geometry bite = op.getResult(OverlayNG.DIFFERENCE);
    assertFalse("R1.7 half \\ disc is exact", op.isApproximate());
    assertEquals("half-disc minus upper half-lens", HALF - HALF_LENS,
        bite.getArea(), EXACT);
    assertArcAndLineShell(bite);
    assertParity(half, disc, OverlayNG.DIFFERENCE, bite);

    OverlayNGCurve rev = new OverlayNGCurve(disc, half);
    Geometry otherBite = rev.getResult(OverlayNG.DIFFERENCE);
    assertFalse("disc \\ half is exact", rev.isApproximate());
    assertEquals("crossing disc minus upper half-lens", DISC - HALF_LENS,
        otherBite.getArea(), EXACT);
    assertArcShell(otherBite);
  }

  public void testHalfDiscSubBiteVsSquare() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry square = readCurve(SQUARE_CAP);
    OverlayNGCurve op = new OverlayNGCurve(half, square);
    Geometry bite = op.getResult(OverlayNG.DIFFERENCE);
    assertFalse("R1.7 half \\ square is exact", op.isApproximate());
    assertEquals("half-disc below y=2", HALF - CAP, bite.getArea(), EXACT);
    assertArcAndLineShell(bite);
    assertParity(half, square, OverlayNG.DIFFERENCE, bite);
  }

  public void testHalfDiscCupAndXorVsSquare() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry square = readCurve(SQUARE_CAP);
    OverlayNGCurve cup = new OverlayNGCurve(half, square);
    Geometry u = cup.getResult(OverlayNG.UNION);
    assertFalse("R1.7 half ∪ square is exact", cup.isApproximate());
    assertEquals("square plus the band below y=2", SQUARE + HALF - CAP,
        u.getArea(), EXACT);
    assertArcAndLineShell(u);

    OverlayNGCurve xor = new OverlayNGCurve(half, square);
    Geometry x = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("R1.7 half XOR square is exact", xor.isApproximate());
    assertEquals("both bites", SQUARE + HALF - 2.0 * CAP, x.getArea(), EXACT);
    assertEquals("two members", 2, x.getNumGeometries());
    assertArcAndLineShell(x.getGeometryN(0));
    assertArcAndLineShell(x.getGeometryN(1));
  }

  public void testLineStringOfThreePointsIsNotAnArc() throws Exception {
    Geometry chords = readCurve(CHORD_SHELL);
    Geometry disc = readCurve(CIRCLE_CROSSING);
    assertNull("a LineString member is not an arc",
        CompoundCurveShellOverlay.overlay(chords, disc, OverlayNG.INTERSECTION));
    OverlayNGCurve op = new OverlayNGCurve(chords, disc);
    Geometry r = op.getResult(OverlayNG.INTERSECTION);
    assertTrue("three-point LINESTRING shell falls to R2", op.isApproximate());
    assertFalse("chord overlay is still non-empty", r.isEmpty());
  }

  public void testNotThisCellReturnsNull() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry disc = readCurve(CIRCLE_5);
    Geometry other = readCurve(CIRCLE_CROSSING);
    Geometry square = readCurve(SQUARE_CAP);
    Geometry chords = readCurve(CHORD_SHELL);
    Geometry right = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 -5, 5 0, 0 5), (0 5, 0 -5)))");
    assertNull("two discs stay on R1.5",
        CompoundCurveShellOverlay.overlay(disc, other, OverlayNG.INTERSECTION));
    assertNull("plain vs plain",
        CompoundCurveShellOverlay.overlay(square, square, OverlayNG.UNION));
    assertNull("two CompoundCurve shells that are not complementary",
        CompoundCurveShellOverlay.overlay(half, right, OverlayNG.INTERSECTION));
    assertNull("line-only shell",
        CompoundCurveShellOverlay.overlay(chords, square, OverlayNG.INTERSECTION));
  }

  private static void assertParity(Geometry a, Geometry b, int opCode,
      Geometry laser) {
    Geometry chord = OverlayNGRobust.overlay(
        CurveOps.linearise(a), CurveOps.linearise(b), opCode);
    assertEquals("area vs chord overlay", chord.getArea(), laser.getArea(),
        AREA_TOL);
    double hd = DiscreteHausdorffDistance.distance(
        CurveOps.linearise(laser), chord);
    assertTrue("Hausdorff vs chord overlay " + hd + " > " + AREA_TOL,
        hd <= AREA_TOL);
  }

  private static void assertArcAndLineShell(Geometry g) {
    assertEquals("CurvePolygon", g.getGeometryType());
    LineString shell = ((CurvePolygon) g).getExteriorCurve();
    assertTrue("shell is a CompoundCurve, got " + shell.getGeometryType(),
        shell instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) shell;
    boolean hasArc = false;
    boolean hasLine = false;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      if (m instanceof CircularString) hasArc = true;
      else hasLine = true;
    }
    assertTrue("CompoundCurve shell has a CircularString", hasArc);
    assertTrue("CompoundCurve shell has a LineString (not a densified n-gon)",
        hasLine);
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
