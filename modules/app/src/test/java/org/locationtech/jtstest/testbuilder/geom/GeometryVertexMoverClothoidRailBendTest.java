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
package org.locationtech.jtstest.testbuilder.geom;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jtstest.function.CurveExampleFunctions;
import org.locationtech.jtstest.testbuilder.model.GeometryEditModel;
import org.locationtech.jtstest.testbuilder.model.TestCaseEdit;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * UX issue #88: Move-vertex on {@code clothoidRailBend} must not flatten
 * to {@code LINESTRING}. Same path as
 * {@code EditVertexTool.mouseReleased} → {@link GeometryEditModel#moveVertex}.
 */
public class GeometryVertexMoverClothoidRailBendTest extends TestCase {

  private static final Coordinate FROM = new Coordinate(116095.653, 412104.165);
  private static final Coordinate TO = new Coordinate(116065, 412096);

  public GeometryVertexMoverClothoidRailBendTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(GeometryVertexMoverClothoidRailBendTest.class);
  }

  public void testMoveJoinKeepsCompoundCurveMembers() {
    Geometry g = CurveExampleFunctions.clothoidRailBend(null);
    assertTrue(g instanceof CompoundCurve);
    assertEquals(5, ((CompoundCurve) g).getNumMembers());

    Geometry moved = GeometryVertexMover.move(g, FROM, TO);
    String wkt = write(moved);
    assertTrue("EditVertex must not flatten clothoidRailBend to LINESTRING, got "
        + moved.getClass().getName() + " " + wkt,
        moved instanceof CompoundCurve);
    assertFalse(moved.getClass().equals(LineString.class));
    CompoundCurve cc = (CompoundCurve) moved;
    assertEquals(5, cc.getNumMembers());
    assertEquals("LineString", cc.getMemberN(0).getGeometryType());
    assertTrue(cc.getMemberN(1) instanceof ClothoidSegment);
    assertTrue(cc.getMemberN(2) instanceof CircularString);
    assertTrue(cc.getMemberN(3) instanceof ClothoidSegment);
    assertEquals("LineString", cc.getMemberN(4).getGeometryType());
    assertFalse(wkt.startsWith("LINESTRING"));
    assertTrue("got " + wkt, wkt.startsWith("COMPOUNDCURVE"));
    assertTrue("got " + wkt, wkt.indexOf("CLOTHOID") >= 0);
    assertTrue("got " + wkt, wkt.indexOf("CIRCULARSTRING") >= 0);
  }

  public void testGeometryEditModelMoveVertexKeepsRailBend() {
    GeometryEditModel model = new GeometryEditModel();
    model.setTestCase(new TestCaseEdit(new CurveGeometryFactory().getPrecisionModel()));
    model.setGeometry(CurveExampleFunctions.clothoidRailBend(null));
    model.moveVertex(FROM, TO);
    Geometry moved = model.getGeometry();
    assertTrue(moved instanceof CompoundCurve);
    assertTrue(((CompoundCurve) moved).getMemberN(1) instanceof ClothoidSegment);
    assertTrue(((CompoundCurve) moved).getMemberN(2) instanceof CircularString);
    assertTrue(((CompoundCurve) moved).getMemberN(3) instanceof ClothoidSegment);
  }

  private static String write(Geometry g) {
    return new CurveWKTWriter().write(g);
  }
}
