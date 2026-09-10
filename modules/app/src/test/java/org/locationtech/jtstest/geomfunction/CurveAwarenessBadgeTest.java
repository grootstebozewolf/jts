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
package org.locationtech.jtstest.geomfunction;

import java.lang.reflect.Method;

import org.locationtech.jtstest.function.GeometryFunctions;
import org.locationtech.jtstest.function.OffsetCurveFunctions;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * TB-FN (#1195): Metadata.curveAwareness wires into GeometryFunction.
 */
public class CurveAwarenessBadgeTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(CurveAwarenessBadgeTest.class);
  }

  public CurveAwarenessBadgeTest(String name) {
    super(name);
  }

  public void testOffsetCurveIsNative() throws Exception {
    Method m = OffsetCurveFunctions.class.getMethod("offsetCurve",
        org.locationtech.jts.geom.Geometry.class, double.class);
    GeometryFunction f = StaticMethodGeometryFunction.createFunction(m);
    assertEquals("native", f.getCurveAwareness());
  }

  public void testLengthIsNative() throws Exception {
    Method m = GeometryFunctions.class.getMethod("length",
        org.locationtech.jts.geom.Geometry.class);
    GeometryFunction f = StaticMethodGeometryFunction.createFunction(m);
    assertEquals("native", f.getCurveAwareness());
  }

  public void testUnannotatedDefaultsToFlattens() throws Exception {
    Method m = GeometryFunctions.class.getMethod("SRID",
        org.locationtech.jts.geom.Geometry.class);
    GeometryFunction f = StaticMethodGeometryFunction.createFunction(m);
    assertEquals("flattens", f.getCurveAwareness());
  }
}
