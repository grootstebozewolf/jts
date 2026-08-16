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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * P2.1 nodes plus P2.2 overlap-as-edge. {@link CurveSegmentString}
 * is the unit; {@link CurveSegmentNoder} emits the discrete node
 * set or a shared run as an edge (interval). P2.3 walks a hole
 * ring as strings into one face decision (bite vs hole). P2.4
 * walks two crossing hole rings into the hole faces. A hole
 * ring that overlaps the other shell (no crossing nodes) is
 * the same P2.3 bite. P2.5.2 is N strings: the unique union of
 * each unordered pair. Not N-SS, not a face walker.
 */
public class CurveSegmentStringTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  /** R1.6-2: axis-aligned cut, two line–circle nodes on CIRCLE_5. */
  private static final String SQUARE_RIGHT =
      "POLYGON ((0 -6, 10 -6, 10 6, 0 6, 0 -6))";
  /** H-FOUR: four line–circle nodes on CIRCLE_5 (y = ±1). */
  private static final String BAND_FOUR =
      "POLYGON ((-8 -1, 8 -1, 8 1, -8 1, -8 -1))";
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  /** H-SHELL-2: two proper nodes (±3, 4). */
  private static final String HALF_HANGING =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 8, 0 3, 5 8), (5 8, -5 8)))";
  /** H-SHELL-N: four nodes on HALF_DISC. */
  private static final String STADIUM_FOUR =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, 0 -2, 1 -1), (1 -1, 1 6), CIRCULARSTRING (1 6, 0 7, -1 6), (-1 6, -1 -1)))";
  /** H-SHELL-N-ODD: 2 crossings + 1 tangent. */
  private static final String STADIUM_ODD =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 4, 0 5, 1 4), (1 4, 1 -1), CIRCULARSTRING (1 -1, 0 -2, -1 -1), (-1 -1, -1 4)))";
  /** H-SHELL-N-MIXED: collinear overlap on the diameter. */
  private static final String ON_DIAMETER =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 1, 0 2, 1 1), (1 1, 1 0), (1 0, -1 0), (-1 0, -1 1)))";
  /** r=3 at (2,0); internally tangent to CIRCLE_5 at (5,0). */
  private static final String CIRCLE_INT_TAN =
      "CURVEPOLYGON (CIRCULARSTRING (-1 0, 2 3, 5 0, 2 -3, -1 0))";
  /**
   * TOUCH-ext / Qed OverlayTouchRow: unit discs, centres (0,0) and
   * (2,0), d = r1+r2, kiss (1,0). Oracle EXT_TANGENT.
   */
  private static final String UNIT_DISC =
      "CURVEPOLYGON (CIRCULARSTRING (-1 0, 0 1, 1 0, 0 -1, -1 0))";
  private static final String UNIT_DISC_TOUCH =
      "CURVEPOLYGON (CIRCULARSTRING (1 0, 2 1, 3 0, 2 -1, 1 0))";
  /** H-SAME-CIRCLE: overlapping quarters of CIRCLE_5. */
  private static final String ARC_SAME_Q1 =
      "CIRCULARSTRING (-5 0, 0 5, 5 0)";
  private static final String ARC_SAME_Q2 =
      "CIRCULARSTRING (0 5, 5 0, 0 -5)";
  /** R-LL: LineString member overlaps a plain line; the arc is a rider. */
  private static final String RLL_COMPOUND =
      "COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 12 2, 14 0))";
  /**
   * Extends past the LineString member so R1 cannot retain. Public
   * CAP is a point count; the overlap is a segment.
   */
  private static final String RLL_OVERLAP_LINE =
      "LINESTRING (2 0, 12 0)";
  private static final String HALF_RIGHT =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 -5, 5 0, 0 5), (0 5, 0 -5)))";
  private static final String HALF_HOLED =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0 1, 1 1, 1 2, 0 2, 0 1))";
  private static final String HOLE_STRADDLE =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (-1 1, 1 1, 1 2, -1 2, -1 1))";
  private static final String HOLE_X =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)), (0.5 0.5, 1.5 0.5, 1.5 1.5, 0.5 1.5, 0.5 0.5))";

  private static final double EXACT = 1.0e-12;
  private static final double SQRT_12_75 = Math.sqrt(12.75);
  private static final double SQRT_24 = Math.sqrt(24.0);

  public static void main(String[] args) {
    TestRunner.run(CurveSegmentStringTest.class);
  }

  public CurveSegmentStringTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  /**
   * The unit is one chord or one arc, not a densified polyline.
   */
  public void testStringIsChordOrArcNotDensify() {
    CurveSegmentString chord = CurveSegmentString.segment(
        new Coordinate(5, 0), new Coordinate(-5, 0));
    assertFalse(chord.isArc());
    assertEquals(5.0, chord.getStart().x, 0.0);
    assertEquals(-5.0, chord.getEnd().x, 0.0);

    CurveSegmentString arc = CurveSegmentString.arc(
        new Coordinate(-5, 0), new Coordinate(0, 5), new Coordinate(5, 0));
    assertTrue(arc.isArc());
    assertEquals(0.0, arc.getMid().x, 0.0);
    assertEquals(5.0, arc.getMid().y, 0.0);

    CurveSegmentString flat = CurveSegmentString.arc(
        new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(2, 0));
    assertFalse("colinear triple is a chord", flat.isArc());
  }

  /**
   * R1.5: two discs. Nodes are the radical-axis pair the kit already
   * computes — not a sampled set.
   */
  public void testR15TwoDiscIsTwoNodes() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_CROSSING);
    Coordinate[] kit = TwoNodeClip.intersectCircles(0, 0, 5, 7, 0, 5);
    assertEquals(2, kit.length);
    Coordinate[] nodes = CurveSegmentNoder.nodes(a, b);
    assertSamePoints("R1.5 two-disc", kit, nodes);
    assertHas(nodes, 3.5, SQRT_12_75);
    assertHas(nodes, 3.5, -SQRT_12_75);
    assertSamePoints("R1.5 reverse", nodes, CurveSegmentNoder.nodes(b, a));
    List<CurveSegmentString> edges = CurveSegmentNoder.edges(a, b);
    assertNotNull(edges);
    assertEquals("R1.5 is nodes, not a shared run", 0, edges.size());
  }

  /**
   * Same R1.5 pair as strings: two semicircle arcs vs two semicircle
   * arcs. The noder's unit of work is {@link CurveSegmentString}.
   */
  public void testR15AsStringsIsTheSamePair() {
    List<CurveSegmentString> a = Arrays.asList(
        CurveSegmentString.arc(c(-5, 0), c(0, 5), c(5, 0)),
        CurveSegmentString.arc(c(5, 0), c(0, -5), c(-5, 0)));
    List<CurveSegmentString> b = Arrays.asList(
        CurveSegmentString.arc(c(2, 0), c(7, 5), c(12, 0)),
        CurveSegmentString.arc(c(12, 0), c(7, -5), c(2, 0)));
    Coordinate[] kit = TwoNodeClip.intersectCircles(0, 0, 5, 7, 0, 5);
    Coordinate[] nodes = CurveSegmentNoder.nodes(a, b, 12.0);
    assertSamePoints("R1.5 strings", kit, nodes);
  }

  /**
   * R1.6-2: disc vs plain half-plane. Two line–circle nodes.
   */
  public void testR16TwoNodeDiscVsPlain() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry square = readCurve(SQUARE_RIGHT);
    Coordinate[] nodes = CurveSegmentNoder.nodes(disc, square);
    assertEquals("R1.6-2", 2, nodes.length);
    assertHas(nodes, 0.0, 5.0);
    assertHas(nodes, 0.0, -5.0);
    Coordinate[] viaSeg = TwoNodeClip.intersectSegmentCircle(
        0, 0, 5, c(0, -6), c(0, 6));
    assertSamePoints("R1.6-2 kit segment–circle", viaSeg, nodes);
    assertSamePoints("R1.6-2 reverse", nodes,
        CurveSegmentNoder.nodes(square, disc));
  }

  /**
   * H-SHELL-2: hanging half vs upper half. (±3, 4).
   */
  public void testHShell2TwoNode() throws Exception {
    Geometry upper = readCurve(HALF_DISC);
    Geometry hanging = readCurve(HALF_HANGING);
    Coordinate[] kit = TwoNodeClip.intersectCircles(0, 0, 5, 0, 8, 5);
    Coordinate[] nodes = CurveSegmentNoder.nodes(upper, hanging);
    assertSamePoints("H-SHELL-2", kit, nodes);
    assertHas(nodes, 3.0, 4.0);
    assertHas(nodes, -3.0, 4.0);
  }

  /**
   * H-FOUR: even-n line–circle nodes of CIRCLE_5 vs the band.
   */
  public void testHFourEvenN() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry band = readCurve(BAND_FOUR);
    Coordinate[] nodes = CurveSegmentNoder.nodes(disc, band);
    assertEquals("H-FOUR even-n", 4, nodes.length);
    assertHas(nodes, SQRT_24, 1.0);
    assertHas(nodes, -SQRT_24, 1.0);
    assertHas(nodes, SQRT_24, -1.0);
    assertHas(nodes, -SQRT_24, -1.0);
  }

  /**
   * H-SHELL-N: even-n on two CompoundCurve shells.
   */
  public void testHShellNEvenN() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry stadium = readCurve(STADIUM_FOUR);
    Coordinate[] nodes = CurveSegmentNoder.nodes(half, stadium);
    assertEquals("H-SHELL-N even-n", 4, nodes.length);
    assertHas(nodes, 1.0, SQRT_24);
    assertHas(nodes, -1.0, SQRT_24);
    assertHas(nodes, 1.0, 0.0);
    assertHas(nodes, -1.0, 0.0);
  }

  /**
   * D2-odd: two diameter crossings plus the tangent at (0, 5).
   * The tangent is a node.
   */
  public void testHShellNOddTangentIsANode() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry stadium = readCurve(STADIUM_ODD);
    Coordinate[] nodes = CurveSegmentNoder.nodes(half, stadium);
    assertEquals("H-SHELL-N-ODD", 3, nodes.length);
    assertHas(nodes, 1.0, 0.0);
    assertHas(nodes, -1.0, 0.0);
    assertHas(nodes, 0.0, 5.0);
  }

  /**
   * MIXED: collinear overlap is an edge, not a discrete node set.
   * Overlay still refuses -- this rung does not walk faces.
   */
  public void testMixedOverlapIsTheDiameterEdge() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry onDiameter = readCurve(ON_DIAMETER);
    assertNull("H-SHELL-N-MIXED: overlay stays refused (no face walk)",
        CompoundCurveShellOverlay.overlay(half, onDiameter,
            org.locationtech.jts.operation.overlayng.OverlayNG.INTERSECTION));
    assertNull("H-SHELL-N-MIXED: nodes stay null (interval, not points)",
        CurveSegmentNoder.nodes(half, onDiameter));

    List<CurveSegmentString> edges = CurveSegmentNoder.edges(half,
        onDiameter);
    assertNotNull("H-SHELL-N-MIXED: noder names the overlap", edges);
    CurveSegmentString run = findChord(edges, -1.0, 0.0, 1.0, 0.0);
    assertNotNull("H-SHELL-N-MIXED: overlapping diameter (-1 0)–(1 0)",
        run);
    assertFalse(run.isArc());
    assertFalse("overlap is an interval, not a pinch", run.isDegenerate());
    assertEquals(2.0, run.length(), EXACT);
  }

  /**
   * H-SAME-CIRCLE: CircleSweepOverlay already sews the shared quarter.
   * Lock that interval on the string, then lift it onto two shells.
   */
  public void testHSameCircleOverlapIsASweepEdge() throws Exception {
    CurveSegmentString q1 = CurveSegmentString.arc(
        c(-5, 0), c(0, 5), c(5, 0));
    CurveSegmentString q2 = CurveSegmentString.arc(
        c(0, 5), c(5, 0), c(0, -5));
    CurveSegmentString run = CurveSegmentString.overlap(q1, q2, 10.0);
    assertNotNull("H-SAME-CIRCLE: shared quarter is an edge", run);
    assertTrue(run.isArc());
    assertFalse(run.isDegenerate());
    assertEquals("shared quarter length", 2.5 * Math.PI, run.length(),
        EXACT);
    assertTrue(sameEnds(run, 0.0, 5.0, 5.0, 0.0));

    CurveSegmentString lower = CurveSegmentString.arc(
        c(5, 0), c(0, -5), c(-5, 0));
    assertNull("same-circle endpoint kiss is not an interval",
        CurveSegmentString.overlap(q1, lower, 10.0));

    Geometry a = readCurve(ARC_SAME_Q1);
    Geometry b = readCurve(ARC_SAME_Q2);
    assertNull("H-SAME-CIRCLE: not a discrete node set",
        CurveSegmentNoder.nodes(a, b));
    List<CurveSegmentString> edges = CurveSegmentNoder.edges(a, b);
    assertEquals("one shared quarter", 1, edges.size());
    assertEquals(2.5 * Math.PI, edges.get(0).length(), EXACT);
    assertTrue(sameEnds(edges.get(0), 0.0, 5.0, 5.0, 0.0));
  }

  /**
   * Two CompoundCurve shells on CIRCLE_5 share the first-quadrant arc.
   */
  public void testHSameCircleLiftsToTwoShells() throws Exception {
    Geometry upper = readCurve(HALF_DISC);
    Geometry right = readCurve(HALF_RIGHT);
    List<CurveSegmentString> edges = CurveSegmentNoder.edges(upper,
        right);
    assertNotNull(edges);
    CurveSegmentString quarter = findArc(edges, 0.0, 5.0, 5.0, 0.0);
    assertNotNull("H-SAME-CIRCLE two-shell: shared quarter", quarter);
    assertEquals(2.5 * Math.PI, quarter.length(), EXACT);
  }

  /**
   * H-ANNULUS-TANGENT: internal tangent is a zero-width pinch, not
   * an annulus. Named as a degenerate edge. No face.
   */
  public void testAnnulusTangentIsADegenerateEdge() throws Exception {
    Geometry disc = readCurve(CIRCLE_5);
    Geometry tan = readCurve(CIRCLE_INT_TAN);
    assertNull("H-ANNULUS-TANGENT: overlay does not punch an annulus",
        CircularDiscOverlay.overlay(disc, tan,
            org.locationtech.jts.operation.overlayng.OverlayNG.DIFFERENCE));
    assertNull("H-ANNULUS-TANGENT: pinch is not a discrete set",
        CurveSegmentNoder.nodes(disc, tan));

    List<CurveSegmentString> edges = CurveSegmentNoder.edges(disc, tan);
    assertNotNull(edges);
    assertEquals("one pinch", 1, edges.size());
    assertTrue("zero-width edge at (5 0)", edges.get(0).isDegenerate());
    assertEquals(5.0, edges.get(0).getStart().x, EXACT);
    assertEquals(0.0, edges.get(0).getStart().y, EXACT);
    assertEquals(0.0, edges.get(0).length(), EXACT);
  }

  /**
   * TOUCH-ext: external tangent, d = r1+r2, one kiss, disjoint
   * interiors. Named as a degenerate edge at (1 0). A point is not
   * an interval and not a CurvePolygon. Overlay stays null.
   */
  public void testTouchExtIsADegenerateEdge() throws Exception {
    Geometry a = readCurve(UNIT_DISC);
    Geometry b = readCurve(UNIT_DISC_TOUCH);
    assertEquals("TOUCH-ext: oracle EXT_TANGENT",
        "FF2F01212", a.relate(b).toString());
    assertNull("TOUCH-ext: overlay does not invent a CurvePolygon",
        CircularDiscOverlay.overlay(a, b,
            org.locationtech.jts.operation.overlayng.OverlayNG.INTERSECTION));
    assertNull("TOUCH-ext: nestedAnnulus is not this pair",
        CircularDiscOverlay.overlay(a, b,
            org.locationtech.jts.operation.overlayng.OverlayNG.DIFFERENCE));
    assertNull("TOUCH-ext: kiss is not a 2-node set",
        CurveSegmentNoder.nodes(a, b));

    List<CurveSegmentString> edges = CurveSegmentNoder.edges(a, b);
    assertNotNull(edges);
    assertEquals("one kiss", 1, edges.size());
    assertTrue("zero-width edge at (1 0)", edges.get(0).isDegenerate());
    assertFalse(edges.get(0).isArc());
    assertEquals(1.0, edges.get(0).getStart().x, EXACT);
    assertEquals(0.0, edges.get(0).getStart().y, EXACT);
    assertEquals(0.0, edges.get(0).length(), EXACT);
    List<CurveSegmentString> rev = CurveSegmentNoder.edges(b, a);
    assertEquals("TOUCH-ext reverse", 1, rev.size());
    assertTrue(rev.get(0).isDegenerate());
    assertEquals(1.0, rev.get(0).getStart().x, EXACT);
    assertEquals(0.0, rev.get(0).getStart().y, EXACT);
  }

  /**
   * R-LL collinear overlap: public CAP is a point count and the
   * helper still misses. The noder names the overlapping segment.
   * Do not chordsaw-as-exact.
   */
  public void testRllCollinearOverlapIsAnEdge() throws Exception {
    Geometry curve = readCurve(RLL_COMPOUND);
    Geometry line = readCurve(RLL_OVERLAP_LINE);
    assertNull("R-LL: helper still misses collinear overlap",
        CircularLineOverlay.overlay(curve, line,
            org.locationtech.jts.operation.overlayng.OverlayNG.INTERSECTION));
    OverlayNGCurve pub = new OverlayNGCurve(curve, line);
    Geometry publicCap = pub.getResult(
        org.locationtech.jts.operation.overlayng.OverlayNG.INTERSECTION);
    assertTrue("R-LL collinear: public stays approximate (not exact)",
        pub.isApproximate());
    assertFalse("R-LL collinear: public count is not the overlap segment",
        publicCap.getLength() == 8.0 && !pub.isApproximate());

    assertNull("R-LL collinear: not a discrete node set",
        CurveSegmentNoder.nodes(curve, line));
    List<CurveSegmentString> edges = CurveSegmentNoder.edges(curve,
        line);
    assertNotNull(edges);
    CurveSegmentString run = findChord(edges, 2.0, 0.0, 10.0, 0.0);
    assertNotNull("R-LL: overlapping segment (2 0)–(10 0)", run);
    assertFalse(run.isArc());
    assertEquals(8.0, run.length(), EXACT);
  }

  public void testHoleStraddleNodesAreTheClipPair() throws Exception {
    Geometry right = readCurve(HALF_RIGHT);
    Geometry straddle = readCurve(HOLE_STRADDLE);
    assertNull("H-SHELL-HOLE-CROSS: Geometry pair still has a hole",
        CurveSegmentNoder.nodes(straddle, right));
    assertNull("H-SHELL-HOLE-CROSS: Geometry edges stay on the outers",
        CurveSegmentNoder.edges(straddle, right));

    CurvePolygon cp = (CurvePolygon) straddle;
    List<CurveSegmentString> hole = CurveSegmentString.of(
        cp.getInteriorCurveN(0));
    List<CurveSegmentString> shell = CurveSegmentString.of(right);
    Coordinate[] nodes = CurveSegmentNoder.nodes(hole, shell, 10.0);
    assertEquals("H-SHELL-HOLE-CROSS: even-n on the hole ring", 2,
        nodes.length);
    assertHas(nodes, 0.0, 1.0);
    assertHas(nodes, 0.0, 2.0);
    assertEquals("new edge ⊂ other.shell ⇒ bite", BiteVsHole.BITE,
        BiteVsHole.decide(straddle, right));
    CurveSegmentString clip = BiteVsHole.clipEdge(straddle, right);
    assertNotNull(clip);
    assertEquals(1.0, clip.length(), EXACT);
    assertTrue(sameEnds(clip, 0.0, 1.0, 0.0, 2.0));

    Geometry holed = readCurve(HALF_HOLED);
    Geometry holeX = readCurve(HOLE_X);
    assertNull("H-SHELL-HOLE-X: Geometry pair still has holes",
        CurveSegmentNoder.nodes(holed, holeX));
    assertNull("H-SHELL-HOLE-X: Geometry edges stay on the outers",
        CurveSegmentNoder.edges(holed, holeX));
    assertEquals("H-SHELL-HOLE-X: bite walk stays a miss",
        BiteVsHole.MISS, BiteVsHole.decide(holed, holeX));

    CurvePolygon ha = (CurvePolygon) holed;
    CurvePolygon hb = (CurvePolygon) holeX;
    List<CurveSegmentString> holeOnDiameter = CurveSegmentString.of(
        ha.getInteriorCurveN(0));
    List<CurveSegmentString> rightShell = CurveSegmentString.of(right);
    assertNull("H-SHELL-HOLE-OUTER: collinear hole-edge is not a node set",
        CurveSegmentNoder.nodes(holeOnDiameter, rightShell, 10.0));
    List<CurveSegmentString> diameterEdges = CurveSegmentNoder.edges(
        holeOnDiameter, rightShell, 10.0);
    assertNotNull(diameterEdges);
    CurveSegmentString diameterRun = findChord(diameterEdges, 0.0, 1.0,
        0.0, 2.0);
    assertNotNull("H-SHELL-HOLE-OUTER: noder names (0 1)–(0 2)",
        diameterRun);
    assertFalse(diameterRun.isArc());
    assertFalse(diameterRun.isDegenerate());
    assertEquals(1.0, diameterRun.length(), EXACT);
    assertEquals("hole-edge ⊂ other.shell ⇒ bite", BiteVsHole.BITE,
        BiteVsHole.decide(holed, right));
    CurveSegmentString diameterClip = BiteVsHole.clipEdge(holed, right);
    assertNotNull(diameterClip);
    assertEquals(1.0, diameterClip.length(), EXACT);
    assertTrue(sameEnds(diameterClip, 0.0, 1.0, 0.0, 2.0));

    List<CurveSegmentString> holeA = CurveSegmentString.of(
        ha.getInteriorCurveN(0));
    List<CurveSegmentString> holeB = CurveSegmentString.of(
        hb.getInteriorCurveN(0));
    Coordinate[] holeNodes = CurveSegmentNoder.nodes(holeA, holeB, 10.0);
    assertEquals("H-SHELL-HOLE-X: two hole–hole nodes", 2, holeNodes.length);
    assertHas(holeNodes, 0.5, 1.0);
    assertHas(holeNodes, 1.0, 1.5);
    List<CurveSegmentString> holeEdges = CurveSegmentNoder.edges(
        holeA, holeB, 10.0);
    assertNotNull(holeEdges);
    assertEquals("H-SHELL-HOLE-X: cross is nodes, not a shared run",
        0, holeEdges.size());
    assertEquals("two hole rings that cross", TwoHoleOverlay.CROSS,
        TwoHoleOverlay.decide(holed, holeX));
    Coordinate[] holeClip = TwoHoleOverlay.clipNodes(holed, holeX);
    assertNotNull(holeClip);
    assertEquals(2, holeClip.length);
    assertHas(holeClip, 0.5, 1.0);
    assertHas(holeClip, 1.0, 1.5);
  }

  public void testNoderDoesNotAssembleFaces() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_CROSSING);
    Coordinate[] nodes = CurveSegmentNoder.nodes(a, b);
    assertNotNull(nodes);
    assertEquals("nodes only — no polygon", 2, nodes.length);
    for (int i = 0; i < nodes.length; i++) {
      assertTrue(nodes[i] instanceof Coordinate);
    }
  }

  /**
   * N=2 is the pair noder. Kit-identical pins stay bit-identical.
   */
  public void testN2IsBitIdenticalToPairNoder() throws Exception {
    assertN2Same(CIRCLE_5, CIRCLE_CROSSING);
    assertN2Same(CIRCLE_5, SQUARE_RIGHT);
    assertN2Same(HALF_DISC, HALF_HANGING);
    assertN2Same(CIRCLE_5, BAND_FOUR);
    assertN2Same(HALF_DISC, STADIUM_FOUR);
    assertN2Same(HALF_DISC, STADIUM_ODD);
    assertN2Same(HALF_DISC, ON_DIAMETER);
    assertN2Same(CIRCLE_5, CIRCLE_INT_TAN);
    assertN2Same(UNIT_DISC, UNIT_DISC_TOUCH);
    assertN2Same(HALF_HOLED, HOLE_X);

    List<CurveSegmentString> sa = Arrays.asList(
        CurveSegmentString.arc(c(-5, 0), c(0, 5), c(5, 0)),
        CurveSegmentString.arc(c(5, 0), c(0, -5), c(-5, 0)));
    List<CurveSegmentString> sb = Arrays.asList(
        CurveSegmentString.arc(c(2, 0), c(7, 5), c(12, 0)),
        CurveSegmentString.arc(c(12, 0), c(7, -5), c(2, 0)));
    Coordinate[] pair = CurveSegmentNoder.nodes(sa, sb, 12.0);
    List<List<CurveSegmentString>> groups =
        Arrays.asList(sa, sb);
    assertSamePoints("N=2 string groups", pair,
        CurveSegmentNoder.nodes(groups, 12.0));
  }

  /**
   * Three pairwise-crossing CompoundCurve shells. Pair nodes are
   * already named (H-SHELL-2, H-SHELL-N, hanging vs stadium). The
   * N-set is that union, no extras.
   */
  public void testN3IsUnionOfPairSets() throws Exception {
    Geometry a = readCurve(HALF_DISC);
    Geometry b = readCurve(HALF_HANGING);
    Geometry c = readCurve(STADIUM_FOUR);
    Coordinate[] ab = CurveSegmentNoder.nodes(a, b);
    Coordinate[] ac = CurveSegmentNoder.nodes(a, c);
    Coordinate[] bc = CurveSegmentNoder.nodes(b, c);
    assertNotNull("H-SHELL-2 pair", ab);
    assertNotNull("H-SHELL-N pair", ac);
    assertNotNull("hanging × stadium pair", bc);
    Coordinate[] expect = unionNodes(ab, ac, bc);
    Coordinate[] n3 = CurveSegmentNoder.nodes(new Geometry[] { a, b, c });
    assertSamePoints("N=3 union", expect, n3);
    assertHas(n3, 3.0, 4.0);
    assertHas(n3, -3.0, 4.0);
    assertHas(n3, 1.0, SQRT_24);
    assertHas(n3, -1.0, SQRT_24);
    assertHas(n3, 1.0, 0.0);
    assertHas(n3, -1.0, 0.0);
    assertSamePoints("N=3 permutation", n3,
        CurveSegmentNoder.nodes(new Geometry[] { c, a, b }));

    List<List<CurveSegmentString>> groups = Arrays.asList(
        CurveSegmentString.of(a), CurveSegmentString.of(b),
        CurveSegmentString.of(c));
    assertSamePoints("N=3 string groups", expect,
        CurveSegmentNoder.nodes(groups, 16.0));

    CurveSegmentString[] shells = new CurveSegmentString[] {
        CurveSegmentString.arc(c(-5, 0), c(0, 5), c(5, 0)),
        CurveSegmentString.arc(c(-5, 8), c(0, 3), c(5, 8)) };
    Coordinate[] hshell2 = TwoNodeClip.intersectCircles(0, 0, 5, 0, 8, 5);
    assertSamePoints("N=2 strings is H-SHELL-2", hshell2,
        CurveSegmentNoder.nodes(shells, 16.0));
  }

  /**
   * A MIXED pair inside an N-set stays an edge. All-pairs must
   * not invent a crossing there. The N-set is the other pairs.
   */
  public void testN3MixedPairAddsNoFakeCrossing() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry mixed = readCurve(ON_DIAMETER);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    assertNull("H-SHELL-N-MIXED pair stays null",
        CurveSegmentNoder.nodes(half, mixed));
    Coordinate[] named = CurveSegmentNoder.nodes(half, cross);
    assertNotNull("half × crossing is a pair set", named);
    Coordinate[] mc = CurveSegmentNoder.nodes(mixed, cross);
    Coordinate[] n3 = CurveSegmentNoder.nodes(
        new Geometry[] { half, mixed, cross });
    if (mc == null) {
      assertSamePoints("MIXED pair contributes nothing", named, n3);
    }
    else {
      assertSamePoints("MIXED pair contributes nothing",
          unionNodes(named, mc), n3);
    }

    Geometry disc = readCurve(CIRCLE_5);
    Geometry tan = readCurve(CIRCLE_INT_TAN);
    Geometry other = readCurve(CIRCLE_CROSSING);
    assertNull("H-ANNULUS-TANGENT pair stays null",
        CurveSegmentNoder.nodes(disc, tan));
    Coordinate[] r15 = CurveSegmentNoder.nodes(disc, other);
    Coordinate[] tanCross = CurveSegmentNoder.nodes(tan, other);
    Coordinate[] pinchN = CurveSegmentNoder.nodes(
        new Geometry[] { disc, tan, other });
    assertSamePoints("pinch pair adds no node",
        unionNodes(r15, tanCross), pinchN);
    assertMissing(pinchN, 5.0, 0.0);
  }

  /**
   * Geometry-level holed pair stays null. Hole rings are walked
   * as strings (P2.3 / P2.4), not invented here.
   */
  public void testNHoledGeometryPairStaysNull() throws Exception {
    Geometry holed = readCurve(HALF_HOLED);
    Geometry holeX = readCurve(HOLE_X);
    assertNull("H-SHELL-HOLE-X Geometry pair",
        CurveSegmentNoder.nodes(holed, holeX));
    assertNull("N=2 holed is the same miss",
        CurveSegmentNoder.nodes(new Geometry[] { holed, holeX }));

    Geometry disc = readCurve(CIRCLE_5);
    Geometry cross = readCurve(CIRCLE_CROSSING);
    Coordinate[] r15 = CurveSegmentNoder.nodes(disc, cross);
    Coordinate[] n3 = CurveSegmentNoder.nodes(
        new Geometry[] { holed, disc, cross });
    assertSamePoints("holed pairs add no Geometry-level node", r15, n3);
  }

  private static Coordinate c(double x, double y) {
    return new Coordinate(x, y);
  }

  private static boolean sameEnds(CurveSegmentString e, double x0, double y0,
      double x1, double y1) {
    Coordinate p = new Coordinate(x0, y0);
    Coordinate q = new Coordinate(x1, y1);
    return (e.getStart().distance(p) <= EXACT
            && e.getEnd().distance(q) <= EXACT)
        || (e.getStart().distance(q) <= EXACT
            && e.getEnd().distance(p) <= EXACT);
  }

  private static CurveSegmentString findChord(List<CurveSegmentString> edges,
      double x0, double y0, double x1, double y1) {
    CurveSegmentString found = null;
    for (int i = 0; i < edges.size() && found == null; i++) {
      CurveSegmentString e = edges.get(i);
      if (!e.isArc() && sameEnds(e, x0, y0, x1, y1)) {
        found = e;
      }
    }
    return found;
  }

  private static CurveSegmentString findArc(List<CurveSegmentString> edges,
      double x0, double y0, double x1, double y1) {
    CurveSegmentString found = null;
    for (int i = 0; i < edges.size() && found == null; i++) {
      CurveSegmentString e = edges.get(i);
      if (e.isArc() && sameEnds(e, x0, y0, x1, y1)) {
        found = e;
      }
    }
    return found;
  }

  private void assertN2Same(String wa, String wb) throws Exception {
    Geometry a = readCurve(wa);
    Geometry b = readCurve(wb);
    Coordinate[] pair = CurveSegmentNoder.nodes(a, b);
    Coordinate[] n2 = CurveSegmentNoder.nodes(new Geometry[] { a, b });
    if (pair == null) {
      assertNull("N=2 miss " + wa, n2);
    }
    else {
      assertSamePoints("N=2 " + wa, pair, n2);
    }
  }

  private static Coordinate[] unionNodes(Coordinate[] a, Coordinate[] b) {
    return unionNodes(a, b, null);
  }

  private static Coordinate[] unionNodes(Coordinate[] a, Coordinate[] b,
      Coordinate[] c) {
    List<Coordinate> hits = new ArrayList<Coordinate>();
    addAll(hits, a);
    addAll(hits, b);
    addAll(hits, c);
    return hits.toArray(new Coordinate[0]);
  }

  private static void addAll(List<Coordinate> hits, Coordinate[] xs) {
    if (xs == null) return;
    for (int k = 0; k < xs.length; k++) {
      boolean seen = false;
      for (int i = 0; i < hits.size() && !seen; i++) {
        if (hits.get(i).x == xs[k].x && hits.get(i).y == xs[k].y) {
          seen = true;
        }
      }
      if (!seen) {
        hits.add(xs[k]);
      }
    }
  }

  private static void assertMissing(Coordinate[] nodes, double x, double y) {
    assertNotNull("missing node set", nodes);
    Coordinate want = new Coordinate(x, y);
    boolean found = false;
    for (int i = 0; i < nodes.length && !found; i++) {
      if (nodes[i].distance(want) <= EXACT) {
        found = true;
      }
    }
    assertFalse("unexpected node (" + x + " " + y + ")", found);
  }

  private static void assertHas(Coordinate[] nodes, double x, double y) {
    assertNotNull("missing node set", nodes);
    Coordinate want = new Coordinate(x, y);
    boolean found = false;
    for (int i = 0; i < nodes.length && !found; i++) {
      if (nodes[i].distance(want) <= EXACT) {
        found = true;
      }
    }
    assertTrue("missing node (" + x + " " + y + ")", found);
  }

  /**
   * Same points, same values — kit closed form, not a new sample.
   */
  private static void assertSamePoints(String label, Coordinate[] expect,
      Coordinate[] got) {
    assertNotNull(label + " noder miss", got);
    assertEquals(label + " count", expect.length, got.length);
    for (int i = 0; i < expect.length; i++) {
      assertHas(got, expect[i].x, expect[i].y);
      boolean bit = false;
      for (int k = 0; k < got.length && !bit; k++) {
        if (got[k].x == expect[i].x && got[k].y == expect[i].y) {
          bit = true;
        }
      }
      assertTrue(label + " not bit-identical to kit at " + expect[i], bit);
    }
  }
}
