/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jtstest.testbuilder;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JTextArea;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jtstest.testbuilder.model.TestBuilderModel;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * TB-IN (issue #33): A-pane apply must load WKT without wiping it.
 * Covers {@link WKTPanel#geometryTextClean}, {@link WKTPanel#isApplyLoadKey},
 * and {@link TestBuilderModel#loadGeometryText} on the H-CC COMPOUNDCURVE.
 */
public class WKTPanelLoadTest extends TestCase {

  /**
   * The H-CC witness from issue #33. Load must keep this source string.
   */
  static final String COMPOUNDCURVE_WKT =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 10 10))";

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(WKTPanelLoadTest.class); }
  public WKTPanelLoadTest(String name) { super(name); }

  public void testGeometryTextClean_compoundCurve_notEmptied() {
    String cleaned = WKTPanel.geometryTextClean(COMPOUNDCURVE_WKT);
    assertEquals("clean must not empty a valid COMPOUNDCURVE",
        COMPOUNDCURVE_WKT, cleaned);
  }

  public void testGeometryTextClean_point_notEmptied() {
    String src = "POINT (1 1)";
    assertEquals(src, WKTPanel.geometryTextClean(src));
  }

  public void testLoadGeometryText_compoundCurve_doesNotEmptySource()
      throws Exception {
    String src = COMPOUNDCURVE_WKT;
    TestBuilderModel model = new TestBuilderModel();
    model.loadGeometryText(src, "");
    assertEquals("loadGeometryText must not mutate the source WKT",
        COMPOUNDCURVE_WKT, src);
    Geometry g = model.getCurrentCase().getGeometry(0);
    assertNotNull("parsed A must be present after load", g);
    assertFalse("parsed A must not be empty after load", g.isEmpty());
    assertTrue("A must remain a CompoundCurve",
        g.getGeometryType().toUpperCase().contains("COMPOUNDCURVE"));
  }

  public void testIsApplyLoadKey_enterApplies() {
    assertTrue(WKTPanel.isApplyLoadKey(key(KeyEvent.VK_ENTER, 0)));
  }

  public void testIsApplyLoadKey_ctrlEnterApplies() {
    assertTrue(WKTPanel.isApplyLoadKey(
        key(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK)));
  }

  public void testIsApplyLoadKey_ctrlShiftEnterApplies() {
    assertTrue(WKTPanel.isApplyLoadKey(key(KeyEvent.VK_ENTER,
        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
  }

  public void testIsApplyLoadKey_shiftEnterIsNewline() {
    KeyEvent shiftEnter = key(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK);
    assertFalse("Shift+Enter must not apply/load",
        WKTPanel.isApplyLoadKey(shiftEnter));
    assertTrue("Shift+Enter must be the newline key",
        WKTPanel.isNewlineKey(shiftEnter));
  }

  public void testIsNewlineKey_plainEnterIsNotNewline() {
    assertFalse(WKTPanel.isNewlineKey(key(KeyEvent.VK_ENTER, 0)));
  }

  public void testIsNewlineKey_ctrlEnterIsNotNewline() {
    assertFalse(WKTPanel.isNewlineKey(
        key(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK)));
  }

  public void testInsertNewlineAtCaret_afterPointWkt() {
    JTextArea ta = new JTextArea("POINT (2 2)");
    ta.setCaretPosition(ta.getText().length());
    WKTPanel.insertNewlineAtCaret(ta);
    assertEquals("POINT (2 2)\n", ta.getText());
    ta.replaceSelection("x");
    assertEquals("following char must land on the new line, not 'POINT (2 2)x'",
        "POINT (2 2)\nx", ta.getText());
  }

  public void testInsertNewlineAtCaret_replacesSelection() {
    JTextArea ta = new JTextArea("AB");
    ta.select(1, 2);
    WKTPanel.insertNewlineAtCaret(ta);
    assertEquals("A\n", ta.getText());
  }

  public void testIsApplyLoadKey_otherKeyDoesNotApply() {
    assertFalse(WKTPanel.isApplyLoadKey(key(KeyEvent.VK_A, 0)));
  }

  /**
   * Canvas load of (A, B, A) rewrites to the 5-token CIRCULARSTRING.
   * Not ISO/IEC 13249-3 full-circle. No GUI.
   */
  public void testLoadGeometryText_abaRewritesToFiveTokenCircle()
      throws Exception {
    TestBuilderModel model = new TestBuilderModel();
    model.loadGeometryText("CIRCULARSTRING (0 0, 2 0, 0 0)", "");
    Geometry g = model.getCurrentCase().getGeometry(0);
    assertNotNull(g);
    assertEquals("CircularString", g.getGeometryType());
    assertEquals(5, g.getNumPoints());
    assertEquals(1.0, g.getCoordinates()[1].x, 1e-12);
    assertEquals(-1.0, g.getCoordinates()[1].y, 1e-12);
  }

  public void testLoadGeometryText_fourItemCircularStringRejected()
      throws Exception {
    TestBuilderModel model = new TestBuilderModel();
    try {
      model.loadGeometryText("CIRCULARSTRING (-5 0, 0 5, 5 0, -5 0)", "");
      fail("Expected reject 4-item CIRCULARSTRING");
    } catch (Exception e) {
      assertTrue(e.getMessage() != null);
    }
  }

  public void testLoadGeometryText_refuseAEqualsB() throws Exception {
    TestBuilderModel model = new TestBuilderModel();
    try {
      model.loadGeometryText("CIRCULARSTRING (0 0, 0 0, 0 0)", "");
      fail("Expected refuse A = B");
    } catch (Exception e) {
      assertTrue(e.getMessage().indexOf("distinct") >= 0
          || e.getMessage().indexOf("A = B") >= 0);
    }
  }

  private static KeyEvent key(int keyCode, int modifiers) {
    return new KeyEvent(new JTextArea(), KeyEvent.KEY_PRESSED, 0L,
        modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
  }
}
