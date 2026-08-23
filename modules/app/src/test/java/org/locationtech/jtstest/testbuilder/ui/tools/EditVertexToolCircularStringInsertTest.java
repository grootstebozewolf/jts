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

import java.awt.Color;
import java.awt.event.KeyEvent;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jtstest.testbuilder.AppColors;
import org.locationtech.jtstest.testbuilder.AppConstants;
import org.locationtech.jtstest.testbuilder.geom.GeometryLocation;
import org.locationtech.jtstest.testbuilder.geom.GeometryPointLocater;
import org.locationtech.jtstest.testbuilder.geom.GeometryVertexInserter;
import org.locationtech.jtstest.testbuilder.model.GeometryEditModel;
import org.locationtech.jtstest.testbuilder.model.TestCaseEdit;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * UX canvas SIGN for issue #82: two-click red insert on an existing
 * CIRCULARSTRING. First click does not write A (A stays blue odd CS).
 * Red overlay is not B. Second click commits +2. Escape cancels.
 * Never even, never flatten, never coincident consecutive. Not #83
 * chord-mid. ISO/IEC 13249-3 odd &ge; 3 WKT tokens. No DOI.
 */
public class EditVertexToolCircularStringInsertTest extends TestCase {

  private static final String INPUT =
      "GEOMETRYCOLLECTION (CIRCULARSTRING (172 410, 180 398, 190 380, 196 370, "
          + "210 349, 225 329, 237 314, 258 284, 279 264, 299 245, 311 237, "
          + "330 225, 361 215, 371 221, 387 232, 395 238, 406 248, 413 256, "
          + "510 330))";

  private static final Coordinate FIRST = new Coordinate(461.5, 293);
  private static final Coordinate SECOND = new Coordinate(430, 310);

  public EditVertexToolCircularStringInsertTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(EditVertexToolCircularStringInsertTest.class);
  }

  public void testOverlayIsRedNotGeometryB() {
    Color overlay = CircularStringInsertGesture.overlayColor();
    assertEquals(AppConstants.BAND_CLR, overlay);
    assertEquals(255, overlay.getRed());
    assertEquals(0, overlay.getGreen());
    assertEquals(0, overlay.getBlue());
    assertFalse("overlay is indicator red, not a write to B",
        CircularStringInsertGesture.overlayWritesGeometryB());
    assertEquals("A stays blue; overlay must not be A-blue",
        new Color(0, 0, 255), AppColors.GEOM_A);
    assertEquals(Color.RED, AppColors.GEOM_B);
    assertFalse("BAND red is not the B slot",
        CircularStringInsertGesture.overlayWritesGeometryB());
  }

  public void testFirstClickDoesNotWriteA() {
    assertFalse(CircularStringInsertGesture.firstClickWritesA());
  }

  public void testOnlyEscapeCancels() {
    assertTrue(CircularStringInsertGesture.isCancelKey(KeyEvent.VK_ESCAPE));
    assertFalse(CircularStringInsertGesture.isCancelKey(KeyEvent.VK_ENTER));
    assertFalse(CircularStringInsertGesture.isCancelKey(KeyEvent.VK_BACK_SPACE));
    assertFalse(CircularStringInsertGesture.isCancelKey(KeyEvent.VK_DELETE));
  }

  public void testFirstClickLeavesAOddBlueCircularString() throws ParseException {
    Geometry a = read(INPUT);
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(a, FIRST, 5.0);
    assertNotNull(loc);

    CircularStringInsertGesture gesture = new CircularStringInsertGesture();
    assertTrue(gesture.begin(loc));
    assertTrue(gesture.isPending());
    assertTrue(FIRST.equals2D(gesture.getFirst()));

    assertSame("first click does not write A", a, loc.insert());
    Geometry child = a.getGeometryN(0);
    assertTrue(child instanceof CircularString);
    assertEquals(19, child.getNumPoints());
    assertEquals(1, child.getNumPoints() % 2);
    assertFalse(child.getClass().equals(LineString.class));
  }

  public void testSecondClickCommitsPlusTwoAndNotChordMid() throws ParseException {
    Geometry a = read(INPUT);
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(a, FIRST, 5.0);
    CircularStringInsertGesture gesture = new CircularStringInsertGesture();
    assertTrue(gesture.begin(loc));

    Geometry edited = gesture.commit(SECOND);
    assertNotNull(edited);
    assertFalse(gesture.isPending());
    assertTrue(edited instanceof GeometryCollection);
    Geometry child = edited.getGeometryN(0);
    assertTrue(child instanceof CircularString);
    assertEquals(21, child.getNumPoints());
    assertEquals(1, child.getNumPoints() % 2);
    assertTrue(contains(child, FIRST));
    assertTrue(contains(child, SECOND));
    assertFalse(contains(child, GeometryVertexInserter.chordMidpoint(
        new Coordinate(406, 248), FIRST)));
    assertFalse(contains(child, GeometryVertexInserter.chordMidpoint(
        FIRST, new Coordinate(510, 330))));
    assertNotNull(read(write(edited)));
  }

  public void testEscapeCancelsWithoutWritingA() throws ParseException {
    Geometry a = read(INPUT);
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(a, FIRST, 5.0);
    CircularStringInsertGesture gesture = new CircularStringInsertGesture();
    assertTrue(gesture.begin(loc));
    gesture.cancel();
    assertFalse(gesture.isPending());
    assertEquals(19, a.getGeometryN(0).getNumPoints());
    assertTrue(a.getGeometryN(0) instanceof CircularString);
    assertNull(gesture.commit(SECOND));
  }

  public void testCoincidentSecondClickDoesNotWrite() throws ParseException {
    Geometry a = read(INPUT);
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(a, FIRST, 5.0);
    CircularStringInsertGesture gesture = new CircularStringInsertGesture();
    assertTrue(gesture.begin(loc));
    assertNull(gesture.commit(FIRST));
    assertTrue("stay pending so the user can click a distinct partner",
        gesture.isPending());
    assertEquals(19, a.getGeometryN(0).getNumPoints());
  }

  public void testModelAUnchangedUntilCommitAndBNeverWritten() throws ParseException {
    GeometryEditModel model = new GeometryEditModel();
    model.setTestCase(new TestCaseEdit(new CurveGeometryFactory().getPrecisionModel()));
    Geometry input = read(INPUT);
    model.setGeometry(0, input);
    model.setGeometry(1, null);

    GeometryLocation loc = model.locateNonVertexPoint(FIRST, 5.0);
    CircularStringInsertGesture gesture = new CircularStringInsertGesture();
    assertTrue(gesture.begin(loc));
    assertEquals(19, model.getGeometry(0).getGeometryN(0).getNumPoints());
    assertNull(model.getGeometry(1));

    Geometry edited = gesture.commit(SECOND);
    model.setGeometry(0, edited);
    assertEquals(21, model.getGeometry(0).getGeometryN(0).getNumPoints());
    assertNull("red overlay / commit must not write B", model.getGeometry(1));
  }

  public void testBeginIgnoresNonCircularString() throws ParseException {
    Geometry line = read("LINESTRING (0 0, 2 0)");
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(
        line, new Coordinate(1, 0), 0.2);
    assertNotNull(loc);
    CircularStringInsertGesture gesture = new CircularStringInsertGesture();
    assertFalse(gesture.begin(loc));
    assertFalse(gesture.isPending());
  }

  private static boolean contains(Geometry g, Coordinate pt) {
    Coordinate[] coords = g.getCoordinates();
    for (int i = 0; i < coords.length; i++) {
      if (coords[i].equals2D(pt)) {
        return true;
      }
    }
    return false;
  }

  private static Geometry read(String wkt) throws ParseException {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static String write(Geometry g) {
    return new CurveWKTWriter().write(g);
  }
}
