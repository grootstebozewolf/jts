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
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jtstest.testbuilder.AppColors;
import org.locationtech.jtstest.testbuilder.AppConstants;
import org.locationtech.jtstest.testbuilder.model.GeometryEditModel;
import org.locationtech.jtstest.testbuilder.model.GeometryType;
import org.locationtech.jtstest.testbuilder.model.TestCaseEdit;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Style A colinear {@code CIRCULARSTRING} draw (PO 23 Aug 2026).
 * Ctrl+left-click, two clicks Start then End. Commit is ISO/IEC
 * 13249-3 {@code CIRCULARSTRING (Start, Mid(Start,End), End)}. Mid is
 * the Euclidean midpoint. Odd control count &ge; 3. Type stays
 * {@code CIRCULARSTRING}. Never flatten. Never even. Escape cancels
 * with nothing written to A. Coincident Start=End is refused.
 *
 * <p>Headless: does not construct {@code AppCursors} / a GUI.
 * Style B {@code (Start, Start, End)} stays HOLD. Not #82 insert.
 * Not #83 chord-mid insert. Unique-circle three-click path is
 * unchanged. No DOI.
 */
public class CircularStringToolColinearDrawTest extends TestCase {

  private static final Coordinate START = new Coordinate(0, 0);
  private static final Coordinate END = new Coordinate(10, 4);
  private static final Coordinate CURSOR = new Coordinate(8, 3.2);

  public CircularStringToolColinearDrawTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(CircularStringToolColinearDrawTest.class);
  }

  public void testGestureIsCtrlLeftClickOnly() {
    assertTrue(CircularStringTool.isStyleAClick(true, true, false));
    assertTrue(CircularStringColinearDrawGesture.isStyleAClick(true, true));
    assertFalse("plain left-click stays unique-circle",
        CircularStringTool.isStyleAClick(false, true, false));
    assertFalse("Ctrl+right-click stays #97 delete",
        CircularStringTool.isStyleAClick(true, false, true));
    assertFalse(CircularStringTool.isStyleAClick(false, false, true));
    assertFalse(CircularStringTool.isStyleAClick(true, false, false));
  }

  public void testUniqueCirclePathUnchanged() {
    assertFalse(CircularStringTool.STREAM_ADD_ON_DRAG);
    assertEquals(0, CircularStringTool.newDrawCapturedCount());
    assertTrue(CircularStringTool.previewHasTrailingChord(2));
    assertEquals(3, CircularStringTool.completeArcPointCount(3));
    assertFalse(CircularStringTool.previewHasTrailingChord(3));
    assertFalse("Style A preview must not be the unique-circle triple",
        CircularStringColinearDrawGesture.previewIsUniqueCircle());
  }

  public void testStyleBStaysHold() {
    assertTrue(CircularStringTool.styleBIsHold());
    assertTrue(CircularStringColinearDrawGesture.styleBIsHold());
    List<Coordinate> styleA = CircularStringColinearDrawGesture
        .controlsForCommit(START, END);
    assertEquals(3, styleA.size());
    assertFalse("Style A mid is not Start (Style B HOLD)",
        styleA.get(1).equals2D(START));
  }

  public void testPreviewIsABlueChordNotInsertRed() {
    assertTrue(CircularStringTool.styleAPreviewIsChord());
    assertTrue(CircularStringColinearDrawGesture.previewIsChord());
    Color preview = CircularStringTool.styleAPreviewColor();
    assertEquals(AppColors.GEOM_A, preview);
    assertEquals(new Color(0, 0, 255), preview);
    assertFalse("Style A preview is A-blue, not #82 insert red",
        preview.equals(AppConstants.BAND_CLR));
    assertFalse(CircularStringColinearDrawGesture.previewWritesGeometryB());
    assertFalse(CircularStringColinearDrawGesture.firstClickWritesA());
  }

  public void testFirstClickDoesNotWriteA() {
    GeometryEditModel model = newModel();
    CircularStringColinearDrawGesture gesture =
        new CircularStringColinearDrawGesture();
    assertTrue(gesture.begin(START));
    assertTrue(gesture.isPending());
    assertTrue(START.equals2D(gesture.getStart()));
    assertNull(model.getGeometry());
    assertFalse(CircularStringColinearDrawGesture.firstClickWritesA());
  }

  public void testPendingPreviewTracksCursorAsChord() {
    CircularStringColinearDrawGesture gesture =
        new CircularStringColinearDrawGesture();
    gesture.begin(START);
    gesture.setPreview(CURSOR);
    assertTrue(START.equals2D(gesture.getStart()));
    assertTrue(CURSOR.equals2D(gesture.getPreviewEnd()));
    assertTrue(CircularStringColinearDrawGesture.previewIsChord());
    assertFalse(CircularStringColinearDrawGesture.previewIsUniqueCircle());
  }

  /**
   * Second click commits ISO/IEC 13249-3
   * {@code CIRCULARSTRING (Start, Mid(Start,End), End)}. Mid is the
   * Euclidean midpoint. Odd &ge; 3. Never flatten.
   */
  public void testSecondClickCommitsStartMidEnd() {
    CircularStringColinearDrawGesture gesture =
        new CircularStringColinearDrawGesture();
    gesture.begin(START);
    List<Coordinate> controls = gesture.commit(END);
    assertNotNull(controls);
    assertFalse(gesture.isPending());
    assertEquals(3, controls.size());
    assertTrue(CircularStringTool.isValidCircularStringCount(controls.size()));
    assertTrue(START.equals2D(controls.get(0)));
    Coordinate mid = LineSegment.midPoint(START, END);
    assertTrue("Mid must be the Euclidean midpoint of Start and End",
        mid.equals2D(controls.get(1)));
    assertEquals((START.x + END.x) / 2.0, controls.get(1).x, 1.0e-12);
    assertEquals((START.y + END.y) / 2.0, controls.get(1).y, 1.0e-12);
    assertTrue(END.equals2D(controls.get(2)));
    assertEquals(Orientation.COLLINEAR,
        Orientation.index(controls.get(0), controls.get(1), controls.get(2)));
    assertFalse(controls.get(1).equals2D(START));
  }

  public void testCommitWritesCircularStringNotLineString() {
    GeometryEditModel model = newModel();
    List<Coordinate> controls = CircularStringColinearDrawGesture
        .controlsForCommit(START, END);
    model.addComponent(list(controls));

    Geometry g = model.getGeometry();
    assertTrue(g instanceof CircularString);
    assertFalse(g.getClass().equals(LineString.class));
    assertEquals(3, g.getNumPoints());
    assertTrue(CircularStringTool.isValidCircularStringCount(g.getNumPoints()));
    String emitted = wkt(g);
    assertTrue("got " + emitted, emitted.startsWith("CIRCULARSTRING"));
    assertFalse(emitted.startsWith("LINESTRING"));
    assertFalse(emitted.startsWith("MULTILINESTRING"));
    assertTrue(START.equals2D(g.getCoordinates()[0]));
    assertTrue(LineSegment.midPoint(START, END).equals2D(g.getCoordinates()[1]));
    assertTrue(END.equals2D(g.getCoordinates()[2]));
  }

  public void testNeverEvenCircularString() {
    List<Coordinate> controls = CircularStringColinearDrawGesture
        .controlsForCommit(START, END);
    assertEquals(3, controls.size());
    assertEquals(1, controls.size() % 2);
    assertTrue(CircularStringTool.isValidCircularStringCount(controls.size()));
    assertFalse(CircularStringTool.isValidCircularStringCount(2));
    assertFalse(CircularStringTool.isValidCircularStringCount(4));
  }

  public void testCoincidentStartEndRefusesCommit() {
    GeometryEditModel model = newModel();
    CircularStringColinearDrawGesture gesture =
        new CircularStringColinearDrawGesture();
    gesture.begin(START);
    assertTrue(CircularStringColinearDrawGesture.isCoincident(START, START));
    assertNull(gesture.commit(START));
    assertTrue("stay pending so the user can click a distinct End",
        gesture.isPending());
    assertNull(model.getGeometry());
    assertTrue(CircularStringColinearDrawGesture
        .controlsForCommit(START, START).isEmpty());
  }

  public void testEscapeCancelsWithoutWritingA() {
    GeometryEditModel model = newModel();
    CircularStringColinearDrawGesture gesture =
        new CircularStringColinearDrawGesture();
    gesture.begin(START);
    gesture.setPreview(CURSOR);
    assertTrue(CircularStringTool.isCancelKey(KeyEvent.VK_ESCAPE));
    assertTrue(CircularStringColinearDrawGesture.isCancelKey(KeyEvent.VK_ESCAPE));
    assertFalse(CircularStringColinearDrawGesture.isCancelKey(KeyEvent.VK_ENTER));
    gesture.cancel();
    assertFalse(gesture.isPending());
    assertNull(model.getGeometry());
    assertNull(gesture.commit(END));
  }

  public void testStyleADoesNotUseInsertPairOrChordMidInsert() {
    List<Coordinate> controls = CircularStringColinearDrawGesture
        .controlsForCommit(START, END);
    assertEquals("Style A is a new-draw triple, not #82 +2 insert",
        3, controls.size());
    assertFalse("Style A Mid is the draw midpoint, not #83 insert",
        CircularStringColinearDrawGesture.previewWritesGeometryB());
  }

  private static String wkt(Geometry g) {
    return new CurveWKTWriter().write(g);
  }

  private static GeometryEditModel newModel() {
    GeometryEditModel model = new GeometryEditModel();
    model.setTestCase(new TestCaseEdit(new PrecisionModel()));
    model.setGeometryType(GeometryType.CIRCULARSTRING);
    return model;
  }

  private static List list(List<Coordinate> coords) {
    List copy = new ArrayList();
    for (Coordinate c : coords) {
      copy.add(new Coordinate(c));
    }
    return copy;
  }
}
