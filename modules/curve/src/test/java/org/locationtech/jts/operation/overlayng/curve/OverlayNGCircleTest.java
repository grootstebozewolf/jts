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
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.noding.CircularNodedSegmentString;
import org.locationtech.jts.noding.NodedSegmentString;
import org.locationtech.jts.operation.overlayng.OverlayNG;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * P2.5.5 OverlayNG-for-circles first slice under Draft v6 MMF Option B
 * ({@link org.locationtech.jts.noding.SegmentKind} ARC / CERTIFIED /
 * LINEARIZED). H-SHELL-N-MIXED is noded on core
 * {@link CircularNodedSegmentString} and overlaid without densify.
 * The P2.1–P2.5.4 kits stay refused. The P2.5.4 tangent stamp is unchanged.
 * Full public N-SS hierarchy remains deferred — this is the deliberate start.
 */
public class OverlayNGCircleTest extends GeometryTestCase {

  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  /** H-SHELL-N-MIXED: collinear overlap on the diameter. */
  private static final String ON_DIAMETER =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 1, 0 2, 1 1), (1 1, 1 0), (1 0, -1 0), (-1 0, -1 1)))";
  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String STADIUM_NEST =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, -2 0, -1 1), (-1 1, 1 1), CIRCULARSTRING (1 1, 2 0, 1 -1), (1 -1, -1 -1)))";
  private static final String HALF_HANGING =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 8, 0 3, 5 8), (5 8, -5 8)))";
  private static final String STADIUM_ODD =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 4, 0 5, 1 4), (1 4, 1 -1), CIRCULARSTRING (1 -1, 0 -2, -1 -1), (-1 -1, -1 4)))";

  private static final double HALF = 12.5 * Math.PI;
  /** Rectangle 2×1 plus upper unit semicircle. */
  private static final double STADIUM = 2.0 + 0.5 * Math.PI;
  private static final double EXACT = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(OverlayNGCircleTest.class);
  }

  public OverlayNGCircleTest(String name) {
    super(name);
  }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testConversionCarriesTheArcNotTheChord() {
    CurveSegmentString arc = CurveSegmentString.arc(
        new Coordinate(-5, 0), new Coordinate(0, 5),
        new Coordinate(5, 0));
    NodedSegmentString ss = OverlayNGCircleNoder.toCore(arc, "A");
    assertTrue(ss instanceof CircularNodedSegmentString);
    CircularNodedSegmentString circ = (CircularNodedSegmentString) ss;
    assertEquals(org.locationtech.jts.noding.SegmentKind.ARC,
        circ.getSegmentKind(0));
    assertTrue(circ.isExact(0));
    assertFalse(circ.mayCollapseToChord(0));
    assertEquals(2, circ.size());
    assertTrue(circ.getArcMidpoint(0).equals2D(new Coordinate(0, 5)));

    CurveSegmentString chord = CurveSegmentString.segment(
        new Coordinate(5, 0), new Coordinate(-5, 0));
    NodedSegmentString line = OverlayNGCircleNoder.toCore(chord, "L");
    assertEquals(org.locationtech.jts.noding.SegmentKind.CERTIFIED,
        line.getSegmentKind(0));
    assertTrue(line.isExact(0));
    assertFalse(line.mayCollapseToChord(0));
  }

  public void testNoderNamesMixedDiameterOverlap() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry onDiameter = readCurve(ON_DIAMETER);
    java.util.List<CurveSegmentString> sa = CurveSegmentString.of(half);
    java.util.List<CurveSegmentString> sb = CurveSegmentString.of(onDiameter);
    java.util.List<NodedSegmentString> edges =
        new java.util.ArrayList<NodedSegmentString>();
    for (int i = 0; i < sa.size(); i++) {
      edges.add(OverlayNGCircleNoder.toCore(sa.get(i), Integer.valueOf(0)));
    }
    for (int i = 0; i < sb.size(); i++) {
      edges.add(OverlayNGCircleNoder.toCore(sb.get(i), Integer.valueOf(1)));
    }
    OverlayNGCircleNoder noder = new OverlayNGCircleNoder(10.0);
    noder.computeNodes(edges);
    assertTrue("H-SHELL-N-MIXED: OverlayNG noder names the overlap",
        noder.hasMixedOverlap());
    assertFalse("H-SHELL-N-MIXED: no proper crossing",
        noder.hasProperCrossing());
    assertFalse("noded substrings stay non-empty",
        noder.getNodedSubstrings().isEmpty());
  }

  public void testMixedDiameterIsExactNamedArea() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry onDiameter = readCurve(ON_DIAMETER);
    OverlayNGCurve cap = new OverlayNGCurve(half, onDiameter);
    Geometry rCap = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("H-SHELL-N-MIXED CAP is laser", cap.isApproximate());
    assertTrue(rCap instanceof CurvePolygon);
    assertEquals(STADIUM, rCap.getArea(), EXACT);
    assertTrue(OverlayNGCurve.isSameGeometry(rCap, onDiameter));

    OverlayNGCurve cup = new OverlayNGCurve(half, onDiameter);
    Geometry rCup = cup.getResult(OverlayNG.UNION);
    assertFalse("H-SHELL-N-MIXED CUP is laser", cup.isApproximate());
    assertEquals(HALF, rCup.getArea(), EXACT);
    assertTrue(OverlayNGCurve.isSameGeometry(rCup, half));

    OverlayNGCurve sub = new OverlayNGCurve(half, onDiameter);
    Geometry rSub = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("H-SHELL-N-MIXED SUB is laser", sub.isApproximate());
    assertTrue(rSub instanceof CurvePolygon);
    assertEquals(1, ((CurvePolygon) rSub).getNumInteriorRing());
    assertEquals(HALF - STADIUM, rSub.getArea(), EXACT);
    LineString hole = ((CurvePolygon) rSub).getInteriorCurveN(0);
    assertTrue(hole instanceof CompoundCurve);

    OverlayNGCurve xor = new OverlayNGCurve(half, onDiameter);
    Geometry rXor = xor.getResult(OverlayNG.SYMDIFFERENCE);
    assertFalse("H-SHELL-N-MIXED XOR is laser", xor.isApproximate());
    assertEquals(HALF - STADIUM, rXor.getArea(), EXACT);
  }

  public void testMixedDiameterReverseSubIsEmpty() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry onDiameter = readCurve(ON_DIAMETER);
    OverlayNGCurve sub = new OverlayNGCurve(onDiameter, half);
    Geometry r = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse(sub.isApproximate());
    assertTrue(r.isEmpty());
  }

  public void testKitStillRefusesMixed() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry onDiameter = readCurve(ON_DIAMETER);
    assertNull("R1.7 kit stays refused",
        CompoundCurveShellOverlay.overlay(half, onDiameter,
            OverlayNG.INTERSECTION));
    assertNull("pair noder stays MIXED (interval, not points)",
        CurveSegmentNoder.nodes(half, onDiameter));
  }

  public void testCcNestAnnulusStaysR2() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry nest = readCurve(STADIUM_NEST);
    assertNull(OverlayNGCircle.overlay(disc, nest, OverlayNG.DIFFERENCE));
    OverlayNGCurve op = new OverlayNGCurve(disc, nest);
    op.getResult(OverlayNG.DIFFERENCE);
    assertTrue("CC-NEST-ANNULUS stays the chordsaw", op.isApproximate());
  }

  public void testP254TangentStampUnchanged() throws Exception {
    Geometry[] geoms = new Geometry[] {
        readCurve(HALF_DISC), readCurve(HALF_HANGING),
        readCurve(STADIUM_ODD)
    };
    assertNull(CurveSegmentFaces.faces(geoms));
    assertEquals(CurveSegmentFaces.SHARED_SNAPPED_RAY,
        CurveSegmentFaces.missReason());
  }

  /**
   * Deliberate Option-B expand: proper two-shell crossing (no MIXED)
   * nodes on CircularNodedSegmentString then TwoShellClip assemble.
   */
  public void testProperCrossingTwoShellIsExactViaNoder() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry hanging = readCurve(HALF_HANGING);
    Geometry viaCircle = OverlayNGCircle.overlay(half, hanging,
        OverlayNG.INTERSECTION);
    assertNotNull("Option-B expand owns half×hanging CAP", viaCircle);
    OverlayNGCurve op = new OverlayNGCurve(half, hanging);
    Geometry laser = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("public OverlayNGCurve stays laser", op.isApproximate());
    assertEquals(viaCircle.getArea(), laser.getArea(), EXACT);
    assertEquals(8.17505543966422, laser.getArea(), 1.0e-9);
  }
}
