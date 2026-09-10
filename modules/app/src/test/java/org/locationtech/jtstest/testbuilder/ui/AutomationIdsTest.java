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

import javax.swing.JButton;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Locks Appium automation ID wiring helper.
 */
public class AutomationIdsTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(AutomationIdsTest.class);
  }

  public AutomationIdsTest(String name) {
    super(name);
  }

  public void testSetWritesNameAndAccessibleName() {
    JButton b = new JButton();
    AutomationIds.set(b, AutomationIds.TOOLBAR_CASE_NEW);
    assertEquals(AutomationIds.TOOLBAR_CASE_NEW, b.getName());
    assertEquals(AutomationIds.TOOLBAR_CASE_NEW,
        b.getAccessibleContext().getAccessibleName());
  }

  public void testSharedIdsAreStable() {
    assertEquals("jts.tb.wkt.a", AutomationIds.WKT_A);
    assertEquals("jts.tb.toolbar.draw.polygon",
        AutomationIds.TOOLBAR_DRAW_POLYGON);
  }
}
