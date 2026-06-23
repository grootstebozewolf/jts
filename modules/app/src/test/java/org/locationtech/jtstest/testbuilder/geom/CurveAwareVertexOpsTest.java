/*
 * Copyright (c) 2026 grootstebozewolf
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *
 * SPDX-License-Identifier: (EPL-2.0 OR EDL-1.0) AND CC0-1.0
 * Assisted-by: Claude (Opus-4.7)
 */
package org.locationtech.jtstest.testbuilder.geom;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.ClothoidSegment;
import org.locationtech.jts.geom.curved.CompoundCurve;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;

import junit.framework.TestCase;

/**
 * Verifies that interactive Move / Insert / Delete on curve geometries
 * preserves member structure and subtypes — the regression the generic
 * {@link org.locationtech.jts.geom.util.GeometryEditor} would otherwise
 * introduce by flattening every LineString subclass into a plain
 * LineString.
 */
public class CurveAwareVertexOpsTest extends TestCase {

  private final CurvedGeometryFactory gf = new CurvedGeometryFactory();

  public CurveAwareVertexOpsTest(String name) {
    super(name);
  }

  // -- move ---------------------------------------------------------

  public void testMoveOnCompoundCurvePreservesMemberSubtypes() {
    CompoundCurve cc = buildLineThenArc();
    Coordinate from = new Coordinate(10, 0);
    Coordinate to   = new Coordinate(12, 1);

    Geometry result = CurveAwareVertexOps.move(cc, from, to);

    assertTrue("must return a CompoundCurve, got " + result.getClass().getSimpleName(),
        result instanceof CompoundCurve);
    CompoundCurve out = (CompoundCurve) result;
    assertEquals(2, out.getNumMembers());
    assertFalse("first member must stay a plain LineString",
        out.getMemberN(0) instanceof CircularString);
    assertFalse("first member must stay a plain LineString",
        out.getMemberN(0) instanceof ClothoidSegment);
    assertTrue("second member must stay a CircularString",
        out.getMemberN(1) instanceof CircularString);
  }

  public void testMoveJunctionUpdatesBothNeighbours() {
    CompoundCurve cc = buildLineThenArc();
    // Junction shared by member 0 (end) and member 1 (start) is (10, 0).
    Coordinate from = new Coordinate(10, 0);
    Coordinate to   = new Coordinate(11, 2);

    CompoundCurve out = (CompoundCurve) CurveAwareVertexOps.move(cc, from, to);

    Coordinate[] m0 = out.getMemberN(0).getCoordinates();
    Coordinate[] m1 = out.getMemberN(1).getCoordinates();
    assertTrue("member 0's end must move",  m0[m0.length - 1].equals2D(to));
    assertTrue("member 1's start must move", m1[0].equals2D(to));
  }

  public void testMoveOnClothoidReanchorsStartOnly() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, 0.0, 0.01, 50.0, gf);
    Coordinate start = cs.getStartCoordinate();
    Coordinate end   = cs.getEndCoordinate();

    Geometry moved = CurveAwareVertexOps.move(cs, start, new Coordinate(5, 5));
    assertTrue("re-anchor at start must keep subtype",
        moved instanceof ClothoidSegment);
    ClothoidSegment movedCs = (ClothoidSegment) moved;
    assertEquals(5.0, movedCs.getStartCoordinate().x, 1e-12);
    assertEquals(5.0, movedCs.getStartCoordinate().y, 1e-12);
    assertEquals(cs.getStartKappa(), movedCs.getStartKappa(), 1e-15);
    assertEquals(cs.getEndKappa(),   movedCs.getEndKappa(),   1e-15);
    assertEquals(cs.getLength(),     movedCs.getLength(),     1e-12);

    // Moving the end (analytic, ambiguous) must refuse: same instance.
    Geometry refused = CurveAwareVertexOps.move(cs, end, new Coordinate(99, 99));
    assertSame("end-move must refuse and return input unchanged", cs, refused);
  }

  public void testMoveReturnsNullForPlainLineString() {
    Geometry plain = gf.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    // Not a curve type → null, so the caller falls back to GeometryEditor.
    assertNull(CurveAwareVertexOps.move(plain, new Coordinate(0, 0), new Coordinate(1, 1)));
  }

  // -- insert -------------------------------------------------------

  public void testInsertOnLineMemberKeepsCompoundCurve() {
    CompoundCurve cc = buildLineThenArc();
    // Segment 0 of the flat sequence is the line member's only segment
    // (between (0,0) and (10,0)).
    Geometry result = CurveAwareVertexOps.insert(cc, cc, 0, new Coordinate(5, 0));
    assertTrue(result instanceof CompoundCurve);
    CompoundCurve out = (CompoundCurve) result;
    assertEquals(2, out.getNumMembers());
    assertEquals("line member should now have 3 points",
        3, out.getMemberN(0).getNumPoints());
    assertTrue("arc member must stay a CircularString",
        out.getMemberN(1) instanceof CircularString);
  }

  public void testInsertOnArcSegmentRefuses() {
    CompoundCurve cc = buildLineThenArc();
    // Segment 1 is the first arc segment of the CircularString member.
    Geometry result = CurveAwareVertexOps.insert(cc, cc, 1, new Coordinate(13, 4));
    assertSame("insert on arc member must refuse", cc, result);
  }

  // -- delete -------------------------------------------------------

  public void testDeleteRefusesJunctionVertex() {
    CompoundCurve cc = buildLineThenArcWithLineTail();
    // Flat indices: 0=(0,0) 1=(5,0) 2=(10,0)[junc] 3=(15,5)[arc-mid] 4=(20,0)[junc] 5=(30,0)
    Geometry result = CurveAwareVertexOps.delete(cc, cc, 2);
    assertSame("junction delete must refuse", cc, result);
    Geometry result2 = CurveAwareVertexOps.delete(cc, cc, 4);
    assertSame("junction delete must refuse", cc, result2);
  }

  public void testDeleteRefusesArcControlPoint() {
    CompoundCurve cc = buildLineThenArcWithLineTail();
    // Flat index 3 is the arc's mid control point.
    Geometry result = CurveAwareVertexOps.delete(cc, cc, 3);
    assertSame("arc control-point delete must refuse", cc, result);
  }

  public void testDeleteInternalLineVertex() {
    CompoundCurve cc = buildLineThenArcWithLineTail();
    // Flat index 1 = internal vertex of the first line member (between
    // its endpoints).
    Geometry result = CurveAwareVertexOps.delete(cc, cc, 1);
    assertTrue(result instanceof CompoundCurve);
    CompoundCurve out = (CompoundCurve) result;
    assertEquals("first line member loses one point",
        2, out.getMemberN(0).getNumPoints());
    assertTrue("arc member preserved",
        out.getMemberN(1) instanceof CircularString);
    assertEquals("tail line member preserved",
        2, out.getMemberN(2).getNumPoints());
  }

  // -- helpers ------------------------------------------------------

  /** [LINESTRING(0 0, 10 0), CIRCULARSTRING(10 0, 15 5, 20 0)] */
  private CompoundCurve buildLineThenArc() {
    LineString line = gf.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    CircularString arc = gf.createCircularString(
        gf.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(10, 0), new Coordinate(15, 5), new Coordinate(20, 0)
        }));
    return new CompoundCurve(new LineString[] { line, arc }, gf);
  }

  /** [LINESTRING(0 0, 5 0, 10 0), CIRCULARSTRING(10 0, 15 5, 20 0),
   *   LINESTRING(20 0, 30 0)] — 3 members, with one internal vertex
   *   in the leading line so delete-of-internal is meaningful. */
  private CompoundCurve buildLineThenArcWithLineTail() {
    LineString line = gf.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(5, 0), new Coordinate(10, 0)
    });
    CircularString arc = gf.createCircularString(
        gf.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(10, 0), new Coordinate(15, 5), new Coordinate(20, 0)
        }));
    LineString tail = gf.createLineString(new Coordinate[] {
        new Coordinate(20, 0), new Coordinate(30, 0)
    });
    return new CompoundCurve(new LineString[] { line, arc, tail }, gf);
  }
}
