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

import javax.swing.JTextField;
import javax.swing.text.BadLocationException;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jtstest.function.AffineTransformationFunctions;
import org.locationtech.jtstest.function.BufferFunctions;
import org.locationtech.jtstest.function.JTSFunctions;
import org.locationtech.jtstest.geomfunction.GeometryFunction;
import org.locationtech.jtstest.geomfunction.StaticMethodGeometryFunction;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * TB-FN leftover (#60): translate dX/dY must accept 10 and 8 without
 * dX jumping to 810. Tree stays Affine translate. A stays the curved
 * logo (ISO/IEC 13249-3 MULTICURVE / COMPOUNDCURVE / CIRCULARSTRING).
 * <p>
 * #72 wipe SIGN still stands: focusing dX must not re-bind Exec to
 * Buffer.buffer or empty A to POLYGON EMPTY. This class locks the
 * leftover mash, not MoveTool and not the hero Affine canvas shot.
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

  /**
   * Caret at 0 on the Distance default {@code 10} plus a stray 8 is
   * the RC4 leftover (810). {@link SpatialFunctionPanel#keepParamKeystrokes}
   * replaces, so typed 10 stays 10.
   */
  public void testKeepParamKeystrokesReplacesRatherThanPrependsTo810()
      throws BadLocationException {
    JTextField dx = new JTextField("10");
    dx.setCaretPosition(0);
    dx.getDocument().insertString(0, "8", null);
    assertEquals("premise: caret-0 insert of 8 mashes default 10 to 810",
        "810", dx.getText());

    SpatialFunctionPanel.keepParamKeystrokes(dx, "10");
    assertEquals("10", dx.getText());
    assertFalse("typed 10 must not remain mashed to 810",
        "810".equals(dx.getText()));
  }

  public void testTranslateDx10Dy8DoesNotMashDxTo810() {
    SpatialFunctionPanel panel = new SpatialFunctionPanel();
    assertTrue(panel.selectFunction("AffineTransformation", "translate"));
    assertEquals("translate", panel.getFunction().getName());
    assertEquals("translate", panel.getMetaFunction().getName());

    JTextField dx = panel.paramTextField(0);
    JTextField dy = panel.paramTextField(1);
    assertEquals("shared Distance default is 10 before leftover accept",
        "10", dx.getText());

    panel.acceptParamKeystrokes(0, "10");
    panel.acceptParamKeystrokes(1, "8");

    assertEquals("10", dx.getText());
    assertEquals("8", dy.getText());
    assertFalse("dX must not jump to 810", "810".equals(dx.getText()));

    Object[] params = panel.getFunctionParams();
    assertEquals(2, params.length);
    assertEquals(10.0, ((Number) params[0]).doubleValue(), 0.0);
    assertEquals(8.0, ((Number) params[1]).doubleValue(), 0.0);

    assertEquals("translate", panel.getMetaFunction().getName());
    assertFalse("buffer".equals(panel.getMetaFunction().getName()));
    assertFalse(panel.isAutoExecute());
  }

  public void testFocusingDxKeepsTranslateNotBuffer() {
    SpatialFunctionPanel panel = new SpatialFunctionPanel();
    assertTrue(panel.selectFunction("AffineTransformation", "translate"));
    panel.paramTextField(0).selectAll();
    panel.acceptParamKeystrokes(0, "10");
    assertEquals("translate", panel.getFunction().getName());
    assertEquals("translate", panel.getMetaFunction().getName());
    assertFalse("buffer".equals(panel.getFunction().getName()));
    assertFalse(panel.isAutoExecute());
  }

  /**
   * A stays the ISO/IEC 13249-3 curved logo after leftover 10 / 8.
   * Not Buffer.buffer, not POLYGON EMPTY.
   */
  public void testLogoStaysCurvedOnTranslateTenEight() {
    SpatialFunctionPanel panel = new SpatialFunctionPanel();
    assertTrue(panel.selectFunction("AffineTransformation", "translate"));
    panel.acceptParamKeystrokes(0, "10");
    panel.acceptParamKeystrokes(1, "8");

    Geometry logo = JTSFunctions.logoLines(null);
    assertTrue(logo.getGeometryType().toUpperCase().contains("MULTICURVE"));

    Geometry moved = (Geometry) panel.getMetaFunction()
        .invoke(logo, panel.getFunctionParams());
    assertFalse(moved.isEmpty());
    assertFalse("POLYGON EMPTY".equalsIgnoreCase(moved.toText()));

    String wkt = new CurveWKTWriter().write(moved);
    String u = wkt.toUpperCase();
    assertTrue("A stays ISO/IEC 13249-3 MULTICURVE", u.contains("MULTICURVE"));
    assertTrue(u.contains("COMPOUNDCURVE"));
    assertTrue(u.contains("CIRCULARSTRING"));
    assertFalse(u.contains("POLYGON EMPTY"));

    assertEquals("translate", panel.getMetaFunction().getName());
    assertFalse("buffer".equals(panel.getMetaFunction().getName()));
  }
}
