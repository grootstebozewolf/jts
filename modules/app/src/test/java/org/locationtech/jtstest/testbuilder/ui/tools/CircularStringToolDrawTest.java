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

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Triangle;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jtstest.testbuilder.model.GeometryEditModel;
import org.locationtech.jtstest.testbuilder.model.GeometryType;
import org.locationtech.jtstest.testbuilder.model.TestCaseEdit;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Issue #92: after the first ISO/IEC 13249-3 {@code CIRCULARSTRING}
 * commits in A, the next A-blue new-draw starts clean. Click-only
 * controls (not LineString stream-add). Chord until click 3 is the
 * unique circle. Escape cancels the in-progress second; the first
 * {@code CIRCULARSTRING} stays. Never even {@code CIRCULARSTRING} in
 * A. Never flatten. Combining intersecting members must not clip.
 * No DOI.
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
        CircularStringTool.STREAM_ADD_ON_DRAG);
  }

  public void testLineStringStillStreamsOnDrag() {
    assertTrue(AbstractStreamDrawTool.DEFAULT_STREAM_ADD_ON_DRAG);
  }

  public void testNewDrawAfterCommitStartsClean() {
    assertEquals("next A-blue new-draw starts with zero captured",
        0, CircularStringTool.newDrawCapturedCount());
  }

  public void testChordUntilClick3IsUniqueCircle() {
    assertTrue("1 click + mouse is still a chord",
        CircularStringTool.previewHasTrailingChord(2));
    assertEquals(0, CircularStringTool.completeArcPointCount(2));
    assertEquals("2 clicks + mouse = one triple, the unique circle",
        3, CircularStringTool.completeArcPointCount(3));
    assertFalse("complete (start, mid, mouse) triple is an arc, not a leftover chord",
        CircularStringTool.previewHasTrailingChord(3));
    assertTrue("three non-collinear controls determine one circle",
        Orientation.index(B0, B1, B2) != Orientation.COLLINEAR);
    Coordinate c = Triangle.circumcentre(B0, B1, B2);
    assertEquals(c.distance(B0), c.distance(B1), 1.0e-12);
    assertEquals(c.distance(B0), c.distance(B2), 1.0e-12);
  }

  public void testDragGhostThenThirdClickWouldBeALeftoverChord() {
    // 2 clicks + drag ghost = 3; real third click = 4 → chord leftover.
    assertTrue(CircularStringTool.previewHasTrailingChord(4));
    assertEquals(3, CircularStringTool.completeArcPointCount(4));
  }

  public void testOnlyEscapeIsCancelKey() {
    assertTrue(CircularStringTool.isCancelKey(KeyEvent.VK_ESCAPE));
    assertFalse(CircularStringTool.isCancelKey(KeyEvent.VK_ENTER));
    assertFalse(CircularStringTool.isCancelKey(KeyEvent.VK_BACK_SPACE));
    assertFalse(CircularStringTool.isCancelKey(KeyEvent.VK_DELETE));
  }

  public void testCancelDoesNotStealInputTab() {
    assertFalse("Log auto-switch is not the lock; do not SIGN showInfoTab on cancel",
        CircularStringTool.cancelStealsInputTab());
    assertFalse(CircularStringTool.cancelCallsDisplayInfo());
  }

  /**
   * Escape drops the in-progress second. First ISO/IEC 13249-3
   * {@code CIRCULARSTRING} in A stays. Never flatten.
   */
  public void testEscapeKeepsFirstCircularString() {
    GeometryEditModel model = newModel();
    model.addComponent(list(A0, A1, A2));
    Geometry first = model.getGeometry();
    assertTrue(first instanceof CircularString);
    String before = wkt(first);

    List<Coordinate> inProgress = CircularStringTool.controlsForCommit(
        Arrays.asList(B0, B1));
    assertTrue("two clicks are not yet a CIRCULARSTRING; Escape drops them",
        inProgress.isEmpty());
    assertSame("cancel must not addComponent", first, model.getGeometry());
    assertEquals(before, wkt(model.getGeometry()));
    assertFalse(wkt(model.getGeometry()).startsWith("LINESTRING"));
    assertTrue(CircularStringTool.isValidCircularStringCount(
        model.getGeometry().getNumPoints()));
  }

  /**
   * ISO/IEC 13249-3: odd control count &ge; 3. Even leftover is
   * dropped. A never holds an even {@code CIRCULARSTRING}.
   */
  public void testNeverEvenCircularStringInA() {
    assertFalse(CircularStringTool.isValidCircularStringCount(0));
    assertFalse(CircularStringTool.isValidCircularStringCount(2));
    assertFalse(CircularStringTool.isValidCircularStringCount(4));
    assertTrue(CircularStringTool.isValidCircularStringCount(3));
    assertTrue(CircularStringTool.isValidCircularStringCount(5));

    List<Coordinate> dropped = CircularStringTool.controlsForCommit(
        Arrays.asList(B0, B1, B_GHOST, B2));
    assertEquals(3, dropped.size());
    assertTrue(CircularStringTool.isValidCircularStringCount(dropped.size()));

    GeometryEditModel model = newModel();
    model.addComponent(list(A0, A1, A2));
    List even = new ArrayList();
    even.add(new Coordinate(B0));
    even.add(new Coordinate(B1));
    even.add(new Coordinate(B_GHOST));
    even.add(new Coordinate(B2));
    model.addComponent(even);
    Geometry g = model.getGeometry();
    assertTrue(g instanceof CircularString);
    assertEquals("even leftover must not land in A", 3, g.getNumPoints());
    assertTrue(CircularStringTool.isValidCircularStringCount(g.getNumPoints()));
    assertEquals(A2.x, g.getCoordinates()[2].x, 1.0e-12);
  }

  /**
   * UX pin: first CS, then second intersecting CS through the clicked
   * third point (46 91). Must not commit the drag stub (35.4 72.6).
   * Combining must not clip. Never flatten.
   */
  public void testIntersectingSecondArcKeepsClickedThirdPoint() {
    GeometryEditModel model = newModel();
    model.addComponent(list(A0, A1, A2));
    assertEquals("new-draw after first commit is a fresh component",
        1, model.getGeometry().getNumGeometries());
    model.addComponent(list(B0, B1, B2));

    Geometry g = model.getGeometry();
    String emitted = wkt(g);
    assertTrue("got " + emitted, g instanceof MultiCurve);
    assertEquals(2, g.getNumGeometries());
    Geometry first = g.getGeometryN(0);
    Geometry second = g.getGeometryN(1);
    assertTrue(first instanceof CircularString);
    assertTrue(second instanceof CircularString);
    assertFalse(first.getClass().equals(LineString.class));
    assertFalse(second.getClass().equals(LineString.class));
    assertEquals(3, first.getNumPoints());
    assertEquals(3, second.getNumPoints());
    assertTrue(CircularStringTool.isValidCircularStringCount(first.getNumPoints()));
    assertTrue(CircularStringTool.isValidCircularStringCount(second.getNumPoints()));
    assertEquals(B2.x, second.getCoordinates()[2].x, 1.0e-12);
    assertEquals(B2.y, second.getCoordinates()[2].y, 1.0e-12);
    assertFalse("must not cut off at the drag ghost: " + emitted,
        Math.abs(second.getCoordinates()[2].x - B_GHOST.x) < 0.2
            && Math.abs(second.getCoordinates()[2].y - B_GHOST.y) < 0.2);
    assertTrue(emitted.indexOf("CIRCULARSTRING") >= 0);
    assertFalse(emitted.startsWith("LINESTRING"));
    assertFalse(emitted.startsWith("MULTILINESTRING"));
    assertEquals("intersecting combine must not clip the first arc",
        A2.x, first.getCoordinates()[2].x, 1.0e-12);
    assertEquals(A2.y, first.getCoordinates()[2].y, 1.0e-12);
    double firstLen = first.getLength();
    double secondLen = second.getLength();
    double chord = B0.distance(B1) + B1.distance(B2);
    assertTrue("second member must be the arc, not a leftover chord",
        secondLen > chord);
    assertEquals("combine must not clip member lengths",
        firstLen + secondLen, g.getLength(), 1.0e-12);
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

  private static List list(Coordinate a, Coordinate b, Coordinate c) {
    List coords = new ArrayList();
    coords.add(new Coordinate(a));
    coords.add(new Coordinate(b));
    coords.add(new Coordinate(c));
    return coords;
  }
}
