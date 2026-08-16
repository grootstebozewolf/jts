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

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Pins the bottom status bar (Case / PM strip), not the Log tab.
 * Escape for issue #56 must show exactly {@code CurvePolygon cancelled.}
 * there.
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
}
