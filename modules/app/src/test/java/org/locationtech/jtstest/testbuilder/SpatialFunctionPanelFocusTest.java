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

import javax.swing.JComponent;
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
 * Headless: no SpatialFunctionPanel construct (icon resources).
 */
public class SpatialFunctionPanelFocusTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(SpatialFunctionPanelFocusTest.class);
  }

  public SpatialFunctionPanelFocusTest(String name) {
    super(name);
  }

  public void testTranslateAndBufferAreDistinctFunctions() throws Exception {
    GeometryFunction translate = translateFn();
    GeometryFunction buffer = bufferFn();
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

  public void testAcceptTenAndEightKeepsDxAndDyIndependent() {
    JTextField dx = new JTextField("10");
    JTextField dy = new JTextField();
    JComponent[] fields = new JComponent[] { dx, dy };

    SpatialFunctionPanel.keepParamKeystrokes(dx, "10");
    SpatialFunctionPanel.keepParamKeystrokes(dy, "8");

    String[] saved = SpatialFunctionPanel.snapshotTextParams(fields);
    dx.setText("810");
    SpatialFunctionPanel.restoreTextParams(fields, saved);

    assertEquals("10", dx.getText());
    assertEquals("8", dy.getText());
    assertFalse("dX must not jump to 810", "810".equals(dx.getText()));
  }

  public void testBoundExecStaysTranslateWhenTreeShowsBuffer() throws Exception {
    GeometryFunction translate = translateFn();
    GeometryFunction buffer = bufferFn();
    GeometryFunction exec = SpatialFunctionPanel.boundExec(translate, buffer);
    assertEquals("translate", exec.getName());
    assertFalse("buffer".equals(exec.getName()));
    assertEquals("buffer", SpatialFunctionPanel.boundExec(null, buffer).getName());
  }

  /**
   * A stays the ISO/IEC 13249-3 curved logo after leftover 10 / 8.
   * Not Buffer.buffer, not POLYGON EMPTY.
   */
  public void testLogoKeepsIso13249CurveTypesOnTranslateTenEight() throws Exception {
    JTextField dx = new JTextField("10");
    JTextField dy = new JTextField();
    SpatialFunctionPanel.keepParamKeystrokes(dx, "10");
    SpatialFunctionPanel.keepParamKeystrokes(dy, "8");
    assertEquals("10", dx.getText());
    assertEquals("8", dy.getText());
    assertFalse("810".equals(dx.getText()));

    GeometryFunction exec = SpatialFunctionPanel.boundExec(translateFn(), bufferFn());
    assertEquals("translate", exec.getName());

    Geometry logo = JTSFunctions.logoLines(null);
    assertTrue(logo.getGeometryType().toUpperCase().contains("MULTICURVE"));

    double dX = Double.parseDouble(dx.getText());
    double dY = Double.parseDouble(dy.getText());
    Geometry moved = (Geometry) exec.invoke(logo, new Object[] { dX, dY });
    assertFalse(moved.isEmpty());
    assertFalse("POLYGON EMPTY".equalsIgnoreCase(moved.toText()));

    String wkt = new CurveWKTWriter().write(moved);
    String u = wkt.toUpperCase();
    assertTrue("A stays ISO/IEC 13249-3 MULTICURVE", u.contains("MULTICURVE"));
    assertTrue(u.contains("COMPOUNDCURVE"));
    assertTrue(u.contains("CIRCULARSTRING"));
    assertFalse(u.contains("POLYGON EMPTY"));
  }

  private static GeometryFunction translateFn() throws Exception {
    return StaticMethodGeometryFunction.createFunction(
        AffineTransformationFunctions.class.getMethod("translate",
            Geometry.class, double.class, double.class));
  }

  private static GeometryFunction bufferFn() throws Exception {
    return StaticMethodGeometryFunction.createFunction(
        BufferFunctions.class.getMethod("buffer",
            Geometry.class, double.class));
  }
}
