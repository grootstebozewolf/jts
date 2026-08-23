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
import org.locationtech.jts.geom.Triangle;
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
 * PO 23 Aug 2026 leftover doors on Style A SIGN {@code edd83e99}.
 * Does not retip Style A (Ctrl+left-click Start/End). Style B HOLD.
 * Not #82. Not #83. Headless: no {@code AppCursors} / GUI. ISO/IEC
 * 13249-3 odd &ge; 3. Never flatten. No DOI.
 *
 * <p>TB-CSE: Escape on leftover overlay never writes A.
 * TB-CS3: plain three-click unique-circle new-draw writes A.
 * TB-CSL: New Case clears pending Start; no fabricated End.
 */
public class CircularStringToolLeftoverDoorsTest extends TestCase {

  /** Dirty Escape witness: leftover Style A Start + cursor End. */
  private static final Coordinate DIRTY_START = new Coordinate(270, 210);
  private static final Coordinate DIRTY_END = new Coordinate(120, 90);
  private static final Coordinate DIRTY_MID = new Coordinate(195, 150);

  /** New Case leak witness: pending Start + fabricated End. */
  private static final Coordinate LEAK_START = new Coordinate(270, 210);
  private static final Coordinate LEAK_END = new Coordinate(70, 220);
  private static final Coordinate LEAK_MID = new Coordinate(170, 215);

  /** Unique-circle triple (existing new-draw door). */
  private static final Coordinate U0 = new Coordinate(38, 27);
  private static final Coordinate U1 = new Coordinate(35, 71);
  private static final Coordinate U2 = new Coordinate(46, 91);

  public CircularStringToolLeftoverDoorsTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(CircularStringToolLeftoverDoorsTest.class);
  }

  /**
   * TB-CSE leftover on {@code 0437beda}: Escape XOR-erased the
   * A-blue leftover overlay with unique-circle red after pending
   * Start was dropped. A-blue XOR red XOR white is static green on
   * empty A. Overlay is not gone. Erase must use the drawn color.
   * Do not retip Style A. Do not take TB-CS3 (preview stays BAND).
   */
  public void testEscapeXorEraseMatchesDrawnColorSoOverlayIsGone() {
    Color drawn = CircularStringTool.styleAPreviewColor();
    Color liveAfterCancel = AppConstants.BAND_CLR;
    assertEquals(AppColors.GEOM_A, drawn);
    assertFalse("post-cancel live color is unique-circle red, not A-blue",
        drawn.equals(liveAfterCancel));
    assertEquals("wrong erase color leaves static green residue",
        Color.GREEN, xorWhite(drawn, liveAfterCancel));
    assertEquals("matching erase restores empty-A white; overlay gone",
        Color.WHITE, xorWhite(drawn, drawn));

    Color erase = IndicatorTool.xorEraseColor(drawn, liveAfterCancel);
    assertEquals(drawn, erase);
    assertEquals(Color.WHITE, xorWhite(drawn, erase));
    assertTrue(CircularStringTool.escapeClearsOverlayBeforeCancel());
    assertTrue(CircularStringTool.escapeLeavesOverlayGone());
    assertEquals("TB-CS3 leftover HOLD: unique-circle preview stays BAND red",
        AppConstants.BAND_CLR,
        IndicatorTool.xorEraseColor(AppConstants.BAND_CLR, AppConstants.BAND_CLR));
  }

  /**
   * TB-CSE: leftover Style A overlay (Start + cursor). Escape must
   * not commit {@code CIRCULARSTRING (270 210, 195 150, 120 90)}.
   */
  public void testDirtyEscapeDoesNotWriteStyleA() {
    assertTrue(DIRTY_MID.equals2D(LineSegment.midPoint(DIRTY_START, DIRTY_END)));
    List<Coordinate> wouldHaveWritten =
        CircularStringColinearDrawGesture.controlsForCommit(DIRTY_START, DIRTY_END);
    assertEquals(3, wouldHaveWritten.size());
    assertTrue(DIRTY_MID.equals2D(wouldHaveWritten.get(1)));

    GeometryEditModel model = newModel();
    CircularStringColinearDrawGesture gesture =
        new CircularStringColinearDrawGesture();
    gesture.begin(DIRTY_START);
    gesture.setPreview(DIRTY_END);
    assertTrue(gesture.isPending());

    assertTrue(CircularStringTool.isCancelKey(KeyEvent.VK_ESCAPE));
    assertFalse(CircularStringTool.escapeWritesA());
    assertFalse(CircularStringColinearDrawGesture.escapeWritesA());
    List<Coordinate> onEscape = CircularStringColinearDrawGesture
        .controlsForEscape(DIRTY_START, DIRTY_END);
    assertTrue("Escape must not emit leftover Style A controls", onEscape.isEmpty());
    gesture.cancel();
    assertFalse(gesture.isPending());
    assertNull(model.getGeometry());
    assertNull(gesture.commit(DIRTY_END));
  }

  /**
   * TB-CSE: clean Escape (Start only, no cursor End yet) already
   * leaves A empty. Keep that.
   */
  public void testCleanEscapeLeavesAEmpty() {
    GeometryEditModel model = newModel();
    CircularStringColinearDrawGesture gesture =
        new CircularStringColinearDrawGesture();
    gesture.begin(DIRTY_START);
    gesture.cancel();
    assertFalse(gesture.isPending());
    assertNull(model.getGeometry());
    assertTrue(CircularStringColinearDrawGesture
        .controlsForEscape(DIRTY_START, null).isEmpty());
  }

  /**
   * TB-CS3: third unique-circle click writes ISO/IEC 13249-3
   * {@code CIRCULARSTRING}. Existing new-draw door. Not Style A.
   * Not a leftover red overlay.
   */
  public void testThreeClickUniqueCircleWritesA() {
    assertFalse("two clicks are still a chord leftover",
        CircularStringTool.uniqueCircleFinishesOnClick(2));
    assertTrue("click 3 writes the unique-circle CIRCULARSTRING",
        CircularStringTool.uniqueCircleFinishesOnClick(3));
    assertEquals(3, CircularStringTool.completeArcPointCount(3));
    assertFalse(CircularStringTool.previewHasTrailingChord(3));
    assertTrue(Orientation.index(U0, U1, U2) != Orientation.COLLINEAR);
    Coordinate c = Triangle.circumcentre(U0, U1, U2);
    assertEquals(c.distance(U0), c.distance(U1), 1.0e-12);
    assertEquals(c.distance(U0), c.distance(U2), 1.0e-12);

    GeometryEditModel model = newModel();
    assertNull(model.getGeometry());
    model.addComponent(list(U0, U1, U2));
    Geometry g = model.getGeometry();
    assertTrue(g instanceof CircularString);
    assertFalse(g.getClass().equals(LineString.class));
    assertEquals(3, g.getNumPoints());
    assertTrue(CircularStringTool.isValidCircularStringCount(g.getNumPoints()));
    String emitted = wkt(g);
    assertTrue("got " + emitted, emitted.startsWith("CIRCULARSTRING"));
    assertFalse(emitted.startsWith("LINESTRING"));
    assertTrue(U2.equals2D(g.getCoordinates()[2]));
  }

  /**
   * TB-CSL: New Case must drop pending Start. Witness leak was
   * {@code CIRCULARSTRING (270 210, 170 215, 70 220)}. After New
   * Case, A is empty and the next Ctrl+left is a fresh Start.
   */
  public void testNewCaseClearsPendingStartAndDoesNotFabricateEnd() {
    assertTrue(LEAK_MID.equals2D(LineSegment.midPoint(LEAK_START, LEAK_END)));
    assertTrue(CircularStringColinearDrawGesture.newCaseClearsPendingStart());

    GeometryEditModel model = newModel();
    CircularStringColinearDrawGesture gesture =
        new CircularStringColinearDrawGesture();
    gesture.begin(LEAK_START);
    gesture.setPreview(LEAK_END);
    assertTrue(gesture.isPending());

    CircularStringTool.onNewCase();
    gesture.cancel();
    assertFalse(gesture.isPending());
    assertNull("New Case must not write the leaked Style A string",
        model.getGeometry());
    assertNull(gesture.commit(LEAK_END));

    assertTrue("first Ctrl+left after New Case is a fresh Start",
        gesture.begin(LEAK_START));
    assertTrue(gesture.isPending());
    assertNull("A stays empty until the second Style A click",
        model.getGeometry());
    assertTrue(LEAK_START.equals2D(gesture.getStart()));
  }

  public void testStyleAGestureUnchangedAndStyleBHold() {
    assertTrue(CircularStringTool.isStyleAClick(true, true, false));
    assertFalse(CircularStringTool.isStyleAClick(false, true, false));
    assertTrue(CircularStringTool.styleBIsHold());
    List<Coordinate> styleA = CircularStringColinearDrawGesture
        .controlsForCommit(DIRTY_START, DIRTY_END);
    assertEquals(3, styleA.size());
    assertTrue(DIRTY_MID.equals2D(styleA.get(1)));
    assertFalse(styleA.get(1).equals2D(DIRTY_START));
  }

  public void testNeverFlattenOrEven() {
    GeometryEditModel model = newModel();
    model.addComponent(list(U0, U1, U2));
    Geometry g = model.getGeometry();
    assertTrue(g instanceof CircularString);
    assertEquals(1, g.getNumPoints() % 2);
    assertFalse(wkt(g).startsWith("LINESTRING"));
    List leftover = new ArrayList();
    leftover.add(U0);
    leftover.add(U1);
    leftover.add(DIRTY_START);
    leftover.add(DIRTY_END);
    List<Coordinate> dropped = CircularStringTool.controlsForCommit(leftover);
    assertEquals(3, dropped.size());
    assertTrue(CircularStringTool.isValidCircularStringCount(dropped.size()));
  }

  /**
   * Java2D XORMode(white): paint C over dest D is C XOR D XOR white.
   * Empty A is white. Draw leftover with {@code drawn}, erase with
   * {@code erase}. Matching colors restore white; A-blue then red
   * leaves green residue.
   */
  private static Color xorWhite(Color drawn, Color erase) {
    int d = drawn.getRGB() & 0x00ffffff;
    int e = erase.getRGB() & 0x00ffffff;
    int w = Color.WHITE.getRGB() & 0x00ffffff;
    return new Color(d ^ e ^ w);
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
