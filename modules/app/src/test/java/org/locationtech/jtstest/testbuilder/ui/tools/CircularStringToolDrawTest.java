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
package org.locationtech.jtstest.testbuilder.ui.tools;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jtstest.testbuilder.model.GeometryEditModel;
import org.locationtech.jtstest.testbuilder.model.GeometryType;
import org.locationtech.jtstest.testbuilder.model.TestCaseEdit;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Issue #92: CircularString draw must not inherit LineString
 * stream-add-on-drag. A second intersecting arc's third click is the
 * arc end, not a leftover chord after a drag ghost completed a stub.
 */
public class CircularStringToolDrawTest extends TestCase {

  /** First committed arc from the UX report. */
  private static final Coordinate A0 = new Coordinate(6, 73);
  private static final Coordinate A1 = new Coordinate(51, 75);
  private static final Coordinate A2 = new Coordinate(80, 15);

  /** Second arc as clicked: third point is (46 91), not the drag ghost. */
  private static final Coordinate B0 = new Coordinate(38, 27);
  private static final Coordinate B1 = new Coordinate(35, 71);
  private static final Coordinate B_GHOST = new Coordinate(35.4, 72.6);
  private static final Coordinate B2 = new Coordinate(46, 91);

  public CircularStringToolDrawTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(CircularStringToolDrawTest.class);
  }

  public void testCircularStringDoesNotStreamAddOnDrag() {
    assertFalse("CS draw is click-to-control, not LineString stream",
        CircularStringTool.getInstance().isStreamAddOnDrag());
  }

  public void testLineStringStillStreamsOnDrag() {
    assertTrue(LineStringTool.getInstance().isStreamAddOnDrag());
  }

  public void testCompoundCurveAndCurvePolygonDoNotStreamAddOnDrag() {
    assertFalse(CompoundCurveTool.getInstance().isStreamAddOnDrag());
    assertFalse(CurvePolygonTool.getInstance().isStreamAddOnDrag());
  }

  public void testTwoClicksPlusMouseIsACompleteArcPreview() {
    assertEquals("2 clicks + mouse = one triple",
        3, CircularStringTool.completeArcPointCount(3));
    assertFalse("no leftover chord on a complete triple",
        CircularStringTool.previewHasTrailingChord(3));
  }

  public void testDragGhostThenThirdClickWouldBeALeftoverChord() {
    // 2 clicks + drag ghost = 3; real third click = 4 → chord leftover.
    assertTrue(CircularStringTool.previewHasTrailingChord(4));
    assertEquals(3, CircularStringTool.completeArcPointCount(4));
  }

  /**
   * UX pin: first CS, then second intersecting CS through the clicked
   * third point (46 91). Must not commit the drag stub (35.4 72.6).
   */
  public void testIntersectingSecondArcKeepsClickedThirdPoint() {
    GeometryEditModel model = newModel();
    model.addComponent(list(A0, A1, A2));
    model.addComponent(list(B0, B1, B2));

    Geometry g = model.getGeometry();
    String wkt = new CurveWKTWriter().write(g);
    assertTrue("got " + wkt, g instanceof MultiCurve);
    assertEquals(2, g.getNumGeometries());
    Geometry second = g.getGeometryN(1);
    assertTrue(second instanceof CircularString);
    assertFalse(second.getClass().equals(LineString.class));
    assertEquals(3, second.getNumPoints());
    assertEquals(B2.x, second.getCoordinates()[2].x, 1.0e-12);
    assertEquals(B2.y, second.getCoordinates()[2].y, 1.0e-12);
    assertFalse("must not cut off at the drag ghost: " + wkt,
        Math.abs(second.getCoordinates()[2].x - B_GHOST.x) < 0.2
            && Math.abs(second.getCoordinates()[2].y - B_GHOST.y) < 0.2);
    assertTrue(wkt.indexOf("CIRCULARSTRING") >= 0);
  }

  private static GeometryEditModel newModel() {
    GeometryEditModel model = new GeometryEditModel();
    model.setTestCase(new TestCaseEdit(new PrecisionModel()));
    model.setGeometryType(GeometryType.CIRCULARSTRING);
    return model;
  }

  private static List list(Coordinate a, Coordinate b, Coordinate c) {
    List coords = new ArrayList();
    coords.add(new Coordinate(a));
    coords.add(new Coordinate(b));
    coords.add(new Coordinate(c));
    return coords;
  }
}
