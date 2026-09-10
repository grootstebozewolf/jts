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
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.overlayng.OverlayNG;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * R-LL: CircularString (or lineal CompoundCurve) vs a plain LineString.
 * CAP is the exact line–circle node(s). CUP / SUB keep CircularString
 * pieces. A three-point LineString is not an arc.
 */
public class CircularLineOverlayTest extends GeometryTestCase {

  private static final String ARC =
      "CIRCULARSTRING (0 0, 2 3, 10 0)";
  private static final String LINE_Y2 =
      "LINESTRING (-1 2, 11 2)";
  /** Hits the control chord at (1, 0); the arc at x=1 is near y=2.05. */
  private static final String CHORD_ONLY =
      "LINESTRING (1 -0.5, 1 0.5)";
  private static final String COMPOUND =
      "COMPOUNDCURVE ((0 0, 2 0), CIRCULARSTRING (2 0, 6 4, 10 0))";
  private static final String CHORD_ARC =
      "LINESTRING (0 0, 2 3, 10 0)";

  /** Circle through (0,0), (2,3), (10,0): centre (5, −7/6). */
  private static final double X_LEFT = 5.0 - 7.0 / Math.sqrt(3.0);
  private static final double X_RIGHT = 5.0 + 7.0 / Math.sqrt(3.0);
  private static final double EXACT = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(CircularLineOverlayTest.class);
  }

  public CircularLineOverlayTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testArcCapVsHorizontalIsExactCircleLineNodes() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry line = readCurve(LINE_Y2);
    OverlayNGCurve op = new OverlayNGCurve(arc, line);
    Geometry laser = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("R-LL arc ∩ line is exact", op.isApproximate());
    assertEquals("two nodes", 2, laser.getNumPoints());
    assertY2Nodes(laser);

    Geometry viaInstance = arc.intersection(line);
    assertEquals("Geometry.intersection routes (arc, line)",
        2, viaInstance.getNumPoints());
    assertY2Nodes(viaInstance);
  }

  public void testChordOnlyLineIsEmpty() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry line = readCurve(CHORD_ONLY);
    OverlayNGCurve op = new OverlayNGCurve(arc, line);
    Geometry laser = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("chord-only miss is exact", op.isApproximate());
    assertTrue("a control-chord hit is not an arc node", laser.isEmpty());
    Geometry helper = CircularLineOverlay.overlay(arc, line, OverlayNG.INTERSECTION);
    assertNotNull("zero nodes is an answer, not a miss", helper);
    assertTrue("helper CAP is empty", helper.isEmpty());
  }

  public void testReverseOrderHitsTheSameLaser() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry line = readCurve(LINE_Y2);
    OverlayNGCurve op = new OverlayNGCurve(line, arc);
    Geometry laser = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("line ∩ arc is exact", op.isApproximate());
    assertEquals(2, laser.getNumPoints());
    assertY2Nodes(laser);

    Geometry viaInstance = line.intersection(arc);
    assertEquals(2, viaInstance.getNumPoints());
    assertY2Nodes(viaInstance);
  }

  public void testUnionAndDifferenceKeepCircularStringPieces() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry line = readCurve(LINE_Y2);
    double arcLen = arc.getLength();
    double lineLen = line.getLength();

    OverlayNGCurve cup = new OverlayNGCurve(arc, line);
    Geometry u = cup.getResult(OverlayNG.UNION);
    assertFalse("R-LL ∪ is exact", cup.isApproximate());
    assertTrue("union keeps an arc", hasCircularString(u));
    assertEquals("union length is both (nodes have no measure)",
        arcLen + lineLen, u.getLength(), EXACT);

    OverlayNGCurve sub = new OverlayNGCurve(arc, line);
    Geometry bite = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("R-LL arc \\ line is exact", sub.isApproximate());
    assertTrue("difference keeps an arc", hasCircularString(bite));
    assertEquals("noding a point does not shorten the arc",
        arcLen, bite.getLength(), EXACT);

    OverlayNGCurve rev = new OverlayNGCurve(line, arc);
    Geometry lineBite = rev.getResult(OverlayNG.DIFFERENCE);
    assertFalse("line \\ arc is exact", rev.isApproximate());
    assertFalse("line pieces stay LineString", hasCircularString(lineBite));
    assertEquals(lineLen, lineBite.getLength(), EXACT);
  }

  public void testCompoundCurveVsLineString() throws Exception {
    Geometry cc = readCurve(COMPOUND);
    Geometry line = readCurve(LINE_Y2);
    OverlayNGCurve cap = new OverlayNGCurve(cc, line);
    Geometry nodes = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("R-LL compound ∩ line is exact", cap.isApproximate());
    assertFalse(nodes.isEmpty());

    OverlayNGCurve cup = new OverlayNGCurve(cc, line);
    Geometry u = cup.getResult(OverlayNG.UNION);
    assertFalse("R-LL compound ∪ line is exact", cup.isApproximate());
    assertTrue("union keeps the arc member", hasCircularString(u));
    assertTrue("the LineString member stays a segment", hasPlainLine(u));
  }

  public void testLineStringOfThreePointsIsNotAnArc() throws Exception {
    Geometry chords = readCurve(CHORD_ARC);
    Geometry line = readCurve(LINE_Y2);
    assertNull("a LineString of three points is not an arc",
        CircularLineOverlay.overlay(chords, line, OverlayNG.INTERSECTION));
    OverlayNGCurve op = new OverlayNGCurve(chords, line);
    Geometry r = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("plain vs plain is exact R2", op.isApproximate());
    assertEquals("two segment hits, not the circle–line pair",
        2, r.getNumPoints());
    assertFalse("must not invent the left circle node",
        hasPointNear(r, X_LEFT, 2.0));
    assertFalse("must not invent the right circle node",
        hasPointNear(r, X_RIGHT, 2.0));
    assertTrue("left chord hit", hasPointNear(r, 4.0 / 3.0, 2.0));
    assertTrue("right chord hit", hasPointNear(r, 14.0 / 3.0, 2.0));
  }

  public void testNotThisCellReturnsNull() throws Exception {
    Geometry arc = readCurve(ARC);
    Geometry other = readCurve("CIRCULARSTRING (0 1, 5 4, 10 1)");
    Geometry disc = readCurve(
        "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))");
    Geometry square = readCurve("POLYGON ((0 -1, 10 -1, 10 4, 0 4, 0 -1))");
    assertNull("two CircularStrings are arc–arc",
        CircularLineOverlay.overlay(arc, other, OverlayNG.INTERSECTION));
    assertNull("a disc is R1.5 / R1.6",
        CircularLineOverlay.overlay(arc, disc, OverlayNG.UNION));
    assertNull("a polygon is not a LineString",
        CircularLineOverlay.overlay(arc, square, OverlayNG.INTERSECTION));
  }

  private static void assertY2Nodes(Geometry g) {
    assertTrue("left node", hasPointNear(g, X_LEFT, 2.0));
    assertTrue("right node", hasPointNear(g, X_RIGHT, 2.0));
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
    for (int i = 0; i < g.getNumGeometries(); i++) {
      Geometry m = g.getGeometryN(i);
      if (m instanceof CircularString) return true;
      if (m instanceof CompoundCurve) {
        CompoundCurve cc = (CompoundCurve) m;
        for (int k = 0; k < cc.getNumMembers(); k++) {
          if (cc.getMemberN(k) instanceof CircularString) return true;
        }
      }
    }
    return false;
  }

  private static boolean hasPlainLine(Geometry g) {
    for (int i = 0; i < g.getNumGeometries(); i++) {
      Geometry m = g.getGeometryN(i);
      if (m instanceof LineString && !(m instanceof CircularString)
          && !(m instanceof CompoundCurve)) {
        return true;
      }
    }
    return false;
  }
}
