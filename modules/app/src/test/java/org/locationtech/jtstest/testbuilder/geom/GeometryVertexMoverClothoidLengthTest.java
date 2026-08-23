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
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jtstest.function.CurveExampleFunctions;
import org.locationtech.jtstest.testbuilder.model.GeometryEditModel;
import org.locationtech.jtstest.testbuilder.model.TestCaseEdit;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * UX issue #100: EditVertex drag of a clothoid end must scale L by
 * newChord/oldChord and keep the start, not translate the whole
 * spiral (which kept L and jumped off the preceding member).
 * Same path as {@code EditVertexTool.mouseReleased} →
 * {@link GeometryVertexMover#move} →
 * {@code GeometryEditor.rebuildClothoid}.
 */
public class GeometryVertexMoverClothoidLengthTest extends TestCase {

  private static final double EPS = 1.0e-6;

  public GeometryVertexMoverClothoidLengthTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(GeometryVertexMoverClothoidLengthTest.class);
  }

  public void testDragClothoidEndScalesLAndKeepsStart() {
    Geometry g = CurveExampleFunctions.clothoidSingleTransition(null);
    CompoundCurve cc = (CompoundCurve) g;
    ClothoidSegment cl = (ClothoidSegment) cc.getMemberN(1);
    Coordinate start = cl.getStartCoordinate();
    Coordinate end = cl.getEndCoordinate();
    double oldL = cl.getLength();
    double oldChord = start.distance(end);
    assertEquals(80.0, oldL, EPS);
    assertTrue(oldChord > 0);

    Coordinate newEnd = new Coordinate(end.x + 20, end.y);
    Geometry moved = GeometryVertexMover.move(g, end, newEnd);
    assertTrue(moved instanceof CompoundCurve);
    ClothoidSegment movedCl = (ClothoidSegment)
        ((CompoundCurve) moved).getMemberN(1);

    assertTrue("start must stay put (not translate with the end), got "
        + movedCl.getStartCoordinate(),
        movedCl.getStartCoordinate().equals2D(start));
    double expectedL = oldL * start.distance(newEnd) / oldChord;
    assertEquals("L must scale with the edited control chord",
        expectedL, movedCl.getLength(), EPS);
    assertFalse("L must change when the chord changes",
        Math.abs(movedCl.getLength() - oldL) < EPS);
    assertEquals(cl.getStartKappa(), movedCl.getStartKappa(), EPS);
    assertEquals(cl.getEndKappa(), movedCl.getEndKappa(), EPS);
  }

  public void testRailBendExitClothoidDragScalesL() {
    Geometry g = CurveExampleFunctions.clothoidRailBend(null);
    CompoundCurve cc = (CompoundCurve) g;
    ClothoidSegment cl = (ClothoidSegment) cc.getMemberN(3);
    Coordinate start = cl.getStartCoordinate();
    Coordinate end = cl.getEndCoordinate();
    double oldL = cl.getLength();
    double oldChord = start.distance(end);
    assertEquals(42.0, oldL, EPS);

    Coordinate newEnd = new Coordinate(116065, 412096);
    Geometry moved = GeometryVertexMover.move(g, end, newEnd);
    ClothoidSegment movedCl = (ClothoidSegment)
        ((CompoundCurve) moved).getMemberN(3);

    assertTrue(movedCl.getStartCoordinate().equals2D(start));
    double expectedL = oldL * start.distance(newEnd) / oldChord;
    assertEquals(expectedL, movedCl.getLength(), EPS);
  }

  public void testGeometryEditModelMoveVertexScalesL() {
    Geometry g = CurveExampleFunctions.clothoidSingleTransition(null);
    ClothoidSegment cl = (ClothoidSegment) ((CompoundCurve) g).getMemberN(1);
    Coordinate start = cl.getStartCoordinate();
    Coordinate end = cl.getEndCoordinate();
    double oldL = cl.getLength();
    double oldChord = start.distance(end);

    GeometryEditModel model = new GeometryEditModel();
    model.setTestCase(new TestCaseEdit(new CurveGeometryFactory().getPrecisionModel()));
    model.setGeometry(g);
    Coordinate newEnd = new Coordinate(end.x + 20, end.y);
    model.moveVertex(end, newEnd);

    ClothoidSegment movedCl = (ClothoidSegment)
        ((CompoundCurve) model.getGeometry()).getMemberN(1);
    assertTrue(movedCl.getStartCoordinate().equals2D(start));
    assertEquals(oldL * start.distance(newEnd) / oldChord,
        movedCl.getLength(), EPS);
  }
}
