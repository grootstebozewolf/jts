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
package org.locationtech.jts.geom.curve;

import java.util.Arrays;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Tests for the structural member list on {@link CompoundCurve}.
 */
public class CompoundCurveStructureTest extends GeometryTestCase {

  public static void main(String[] args) {
    TestRunner.run(CompoundCurveStructureTest.class);
  }

  public CompoundCurveStructureTest(String name) { super(name); }

  private CurveGeometryFactory cgf() {
    return new CurveGeometryFactory();
  }

  public void testMixedMembersExposed() {
    CurveGeometryFactory f = cgf();
    LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    CircularString arc = f.createCircularString(f.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(10, 0), new Coordinate(15, 5), new Coordinate(20, 0)
    }));
    CompoundCurve cc = new CompoundCurve(new LineString[] { line, arc }, f);
    assertEquals(2, cc.getNumMembers());
    assertTrue(cc.getMemberN(0) instanceof LineString);
    assertTrue(cc.getMemberN(1) instanceof CircularString);
  }

  public void testFlattenedCoordsDedupeJunction() {
    CurveGeometryFactory f = cgf();
    LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    CircularString arc = f.createCircularString(f.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(10, 0), new Coordinate(15, 5), new Coordinate(20, 0)
    }));
    CompoundCurve cc = new CompoundCurve(new LineString[] { line, arc }, f);
    Coordinate[] flat = cc.getCoordinates();
    // line has 2 points, arc has 3, junction (10,0) is shared once
    assertEquals(4, flat.length);
    assertEquals(0.0,  flat[0].x, 0.0);
    assertEquals(10.0, flat[1].x, 0.0);
    assertEquals(15.0, flat[2].x, 0.0);
    assertEquals(20.0, flat[3].x, 0.0);
  }

  public void testCopyPreservesMemberTypes() {
    CurveGeometryFactory f = cgf();
    LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    CircularString arc = f.createCircularString(f.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(10, 0), new Coordinate(15, 5), new Coordinate(20, 0)
    }));
    CompoundCurve cc = new CompoundCurve(new LineString[] { line, arc }, f);
    CompoundCurve copy = (CompoundCurve) cc.copy();
    assertEquals(2, copy.getNumMembers());
    assertTrue(copy.getMemberN(0) instanceof LineString);
    assertFalse(copy.getMemberN(0) instanceof CircularString);
    assertTrue(copy.getMemberN(1) instanceof CircularString);
  }

  public void testToLinearDensifiesArcMembersOnly() {
    CurveGeometryFactory f = cgf();
    LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    CircularString arc = f.createCircularString(f.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(10, 0), new Coordinate(15, 5), new Coordinate(20, 0)
    }));
    CompoundCurve cc = new CompoundCurve(new LineString[] { line, arc }, f);
    Coordinate[] flat = cc.getCoordinates();
    Coordinate[] linearized = cc.toLinear(0.5).getCoordinates();
    assertTrue("toLinear should add chord vertices to the arc but not the straight",
        linearized.length > flat.length);
    // first point and last point of the chain are preserved exactly
    assertEquals(0.0,  linearized[0].x, 0.0);
    assertEquals(20.0, linearized[linearized.length - 1].x, 0.0);
  }

  public void testEmptyCompoundCurve() {
    CurveGeometryFactory f = cgf();
    CompoundCurve cc = new CompoundCurve(new LineString[0], f);
    assertEquals(0, cc.getNumMembers());
    assertTrue(cc.isEmpty());
    CompoundCurve copy = (CompoundCurve) cc.copy();
    assertEquals(0, copy.getNumMembers());
    assertTrue(copy.isEmpty());
    assertTrue(cc.toLinear(0.5).isEmpty());
  }

  public void testToLinearPinsMemberControlPointsAsAnchors() {
    CurveGeometryFactory f = cgf();
    // Multi-arc CircularString: 5 points = 2 arcs sharing (200, 260)
    CircularString arc = f.createCircularString(f.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(90, 260),
        new Coordinate(200, 260),
        new Coordinate(200, 100),
        new Coordinate(200, 0),
        new Coordinate(100, 0)
    }));
    LineString tail = f.createLineString(new Coordinate[] {
        new Coordinate(100, 0), new Coordinate(0, 0)
    });
    CompoundCurve cc = new CompoundCurve(new LineString[] { arc, tail }, f);

    Coordinate[] dense = cc.toLinear(1.0).getCoordinates();

    // Every input control point of every member must appear in the
    // densified output exactly (2D equality).
    Coordinate[] required = new Coordinate[] {
        new Coordinate(90, 260),
        new Coordinate(200, 260),
        new Coordinate(200, 100),
        new Coordinate(200, 0),
        new Coordinate(100, 0),
        new Coordinate(0, 0)
    };
    for (Coordinate r : required) {
      boolean found = false;
      for (Coordinate d : dense) {
        if (d.equals2D(r)) { found = true; break; }
      }
      assertTrue("densified CompoundCurve should anchor input control point " + r
          + " but didn't; output = " + Arrays.toString(dense), found);
    }
    // And the LineString member's contribution must not be orphaned —
    // the chain ends at the LineString's far endpoint.
    Coordinate end = dense[dense.length - 1];
    assertEquals(0.0, end.x, 0.0);
    assertEquals(0.0, end.y, 0.0);
  }

  public void testLegacyFlatConstructorWrapsAsSingleMember() {
    CurveGeometryFactory f = cgf();
    Coordinate[] pts = new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0), new Coordinate(20, 5)
    };
    CompoundCurve cc = new CompoundCurve(f.getCoordinateSequenceFactory().create(pts), f);
    assertEquals(1, cc.getNumMembers());
    assertEquals(3, cc.getCoordinates().length);
  }
}
