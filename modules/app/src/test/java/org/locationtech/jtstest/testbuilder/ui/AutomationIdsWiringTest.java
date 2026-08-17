/*
 * Copyright (c) 2026 Jeroen Tech Solutions Ltd / JTS contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jtstest.testbuilder.ui;

import java.awt.Component;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JToolBar;

import org.locationtech.jtstest.testbuilder.JTSTestBuilderToolBar;
import org.locationtech.jtstest.testbuilder.WKTPanel;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Asserts Appium automation IDs are present on constructed toolbar / WKT panel.
 * Headless-safe (no display required for construction).
 */
public class AutomationIdsWiringTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(AutomationIdsWiringTest.class);
  }

  public AutomationIdsWiringTest(String name) {
    super(name);
  }

  public void testToolbarHasSharedIds() {
    JTSTestBuilderToolBar bar = new JTSTestBuilderToolBar(null);
    Set<String> names = collectNames(bar.getToolBar());
    assertTrue(names.contains(AutomationIds.TOOLBAR_CASE_PREV));
    assertTrue(names.contains(AutomationIds.TOOLBAR_CASE_NEXT));
    assertTrue(names.contains(AutomationIds.TOOLBAR_CASE_NEW));
    assertTrue(names.contains(AutomationIds.TOOLBAR_ZOOM_ONE_TO_ONE));
    assertTrue(names.contains(AutomationIds.TOOLBAR_DRAW_POLYGON));
    assertTrue(names.contains(AutomationIds.TOOLBAR_DRAW_LINESTRING));
    assertTrue(names.contains(AutomationIds.TOOLBAR_DRAW_POINT));
    assertTrue(names.contains(AutomationIds.TOOLBAR_ZOOM_MODE));
    assertTrue(names.contains(AutomationIds.WKT_A) == false); // not on toolbar
  }

  public void testToolbarHasCurveIdsWhenPresent() {
    JTSTestBuilderToolBar bar = new JTSTestBuilderToolBar(null);
    Set<String> names = collectNames(bar.getToolBar());
    // Curve tools exist on PR #7 builds; skip soft-fail if absent (upstream).
    if (names.contains(AutomationIds.TOOLBAR_DRAW_CIRCULARSTRING)) {
      assertTrue(names.contains(AutomationIds.TOOLBAR_DRAW_COMPOUNDCURVE));
      assertTrue(names.contains(AutomationIds.TOOLBAR_DRAW_CURVEPOLYGON));
    }
  }

  public void testWktPanelHasIds() {
    if (java.awt.GraphicsEnvironment.isHeadless()) {
      // FileDrop requires a display; run under xvfb-run in CI smoke.
      return;
    }
    WKTPanel panel = new WKTPanel(null);
    Set<String> names = collectNames(panel);
    assertTrue(names.contains(AutomationIds.WKT_A));
    assertTrue(names.contains(AutomationIds.WKT_B));
    assertTrue(names.contains(AutomationIds.WKT_LOAD));
    assertTrue(names.contains(AutomationIds.WKT_INSPECT));
    assertTrue(names.contains(AutomationIds.WKT_EXCHANGE));
  }

  private static Set<String> collectNames(Component root) {
    Set<String> out = new HashSet<String>();
    collect(root, out);
    return out;
  }

  private static void collect(Component c, Set<String> out) {
    if (c instanceof JComponent) {
      String n = ((JComponent) c).getName();
      if (n != null && n.startsWith("jts.tb.")) {
        out.add(n);
      }
    }
    if (c instanceof java.awt.Container) {
      Component[] kids = ((java.awt.Container) c).getComponents();
      for (int i = 0; i < kids.length; i++) {
        collect(kids[i], out);
      }
    }
  }
}
