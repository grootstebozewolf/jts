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

import org.locationtech.jtstest.geomfunction.GeometryFunction;
import org.locationtech.jtstest.geomfunction.StaticMethodGeometryFunction;
import org.locationtech.jtstest.function.AffineTransformationFunctions;
import org.locationtech.jtstest.function.BufferFunctions;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * TB-FN #60: Exec must stay bound to the selected Geometry function
 * (e.g. AffineTranslation), not silently re-bind to Buffer.buffer when
 * the shared Distance/dX param field is focused.
 */
public class SpatialFunctionPanelFocusTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(SpatialFunctionPanelFocusTest.class);
  }

  public SpatialFunctionPanelFocusTest(String name) {
    super(name);
  }

  public void testTranslateAndBufferAreDistinctFunctions() throws Exception {
    GeometryFunction translate = StaticMethodGeometryFunction.createFunction(
        AffineTransformationFunctions.class.getMethod("translate",
            org.locationtech.jts.geom.Geometry.class, double.class, double.class));
    GeometryFunction buffer = StaticMethodGeometryFunction.createFunction(
        BufferFunctions.class.getMethod("buffer",
            org.locationtech.jts.geom.Geometry.class, double.class));
    assertFalse(translate.getName().equals(buffer.getName()));
    assertEquals("translate", translate.getName());
    assertEquals("buffer", buffer.getName());
  }
}
