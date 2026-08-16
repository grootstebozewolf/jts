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
package org.locationtech.jtstest.testbuilder;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GridLayout;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Pins the bottom status bar (Case / PM strip), not the Log tab.
 * Escape for issue #56 must show exactly {@code CurvePolygon cancelled.}
 * there via {@link TestCasePanel#setStatus}. Log auto-switch
 * ({@code showInfoTab} / {@code displayInfo(..., true)}) is not the
 * lock and must not steal the Input tab.
 */
public class TestCasePanelStatusTest extends TestCase {

  public TestCasePanelStatusTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(TestCasePanelStatusTest.class);
  }

  public void testSetStatusShowsCurvePolygonCancelledOnStatusBar() {
    TestCasePanel panel = new TestCasePanel();
    assertEquals("", panel.getStatus());
    panel.setStatus("CurvePolygon cancelled.");
    assertEquals("CurvePolygon cancelled.", panel.getStatus());
  }

  public void testSetStatusDoesNotRequireLogTab() {
    TestCasePanel panel = new TestCasePanel();
    panel.setStatus("CurvePolygon cancelled.");
    assertEquals("status bar is the Case/PM strip, independent of Log",
        "CurvePolygon cancelled.", panel.getStatus());
  }

  public void testCancelledStatusIsNotClipped() {
    TestCasePanel panel = new TestCasePanel();
    assertFalse("1x4 GridLayout Case cell clips CurvePolygon cancelled.",
        panel.statusBarPanel.getLayout() instanceof GridLayout);
    panel.setStatus("CurvePolygon cancelled.");
    assertEquals("CurvePolygon cancelled.", panel.getStatus());
    FontMetrics fm = panel.lblStatus.getFontMetrics(panel.lblStatus.getFont());
    int need = fm.stringWidth("CurvePolygon cancelled.")
        + panel.lblStatus.getInsets().left + panel.lblStatus.getInsets().right;
    Dimension min = panel.lblStatus.getMinimumSize();
    assertTrue("visible label must reserve the full lock string, not a tooltip",
        min.width >= need);
    panel.layoutStatusBar(800);
    assertTrue("allocated width must show CurvePolygon cancelled. including the period",
        panel.isStatusFullyVisible());
  }

  public void testSetStatusEmptyClearsStrip() {
    TestCasePanel panel = new TestCasePanel();
    panel.setStatus("CurvePolygon cancelled.");
    panel.setStatus("");
    assertEquals("", panel.getStatus());
  }
}
