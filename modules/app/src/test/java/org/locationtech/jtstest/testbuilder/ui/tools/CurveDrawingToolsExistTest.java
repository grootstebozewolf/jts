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

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * TB-T (#1195): CompoundCurveTool and CurvePolygonTool exist as siblings
 * of CircularStringTool. Gesture locks live in CurvePolygonToolTest (#56).
 */
public class CurveDrawingToolsExistTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(CurveDrawingToolsExistTest.class);
  }

  public CurveDrawingToolsExistTest(String name) {
    super(name);
  }

  public void testToolsExist() {
    assertNotNull(CircularStringTool.getInstance());
    assertNotNull(CompoundCurveTool.getInstance());
    assertNotNull(CurvePolygonTool.getInstance());
    assertSame(CompoundCurveTool.getInstance(), CompoundCurveTool.getInstance());
    assertSame(CurvePolygonTool.getInstance(), CurvePolygonTool.getInstance());
  }
}
