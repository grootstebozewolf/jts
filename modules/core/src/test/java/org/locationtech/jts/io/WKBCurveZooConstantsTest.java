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
package org.locationtech.jts.io;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Fork MMF (#1195): WKB 18–21 codes are SIGNED greenfield constants.
 */
public class WKBCurveZooConstantsTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(WKBCurveZooConstantsTest.class);
  }

  public WKBCurveZooConstantsTest(String name) {
    super(name);
  }

  public void testSignedZooCodes() {
    assertEquals(8, WKBConstants.wkbCircularString);
    assertEquals(12, WKBConstants.wkbMultiSurface);
    assertEquals(18, WKBConstants.wkbClothoid);
    assertEquals(19, WKBConstants.wkbBezier);
    assertEquals(20, WKBConstants.wkbEllipse);
    assertEquals(21, WKBConstants.wkbNurbs);
  }
}
