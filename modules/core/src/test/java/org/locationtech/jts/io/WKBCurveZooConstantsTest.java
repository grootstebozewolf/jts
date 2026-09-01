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
 * Signed I/O is 8–12 only (ISO/IEC 13249-3). Preview 18–21 codes
 * remain on the tree and are HOLD — not SIGNED I/O, not the curve SoT.
 */
public class WKBCurveZooConstantsTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(WKBCurveZooConstantsTest.class);
  }

  public WKBCurveZooConstantsTest(String name) {
    super(name);
  }

  public void testPreviewHoldCodesUnchanged() {
    assertEquals(8, WKBConstants.wkbCircularString);
    assertEquals(12, WKBConstants.wkbMultiSurface);
    assertEquals(18, WKBConstants.wkbClothoid);
    assertEquals(19, WKBConstants.wkbBezier);
    assertEquals(20, WKBConstants.wkbEllipse);
    assertEquals(21, WKBConstants.wkbNurbs);
  }
}
