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

import javax.swing.JComponent;

/**
 * Appium / accessibility automation IDs for TestBuilder.
 * <p>
 * Sets both {@link JComponent#setName(String)} and
 * {@link javax.accessibility.AccessibleContext#setAccessibleName(String)}
 * so Appium desktop drivers can locate controls by accessibility id.
 * <p>
 * Catalog: {@code doc/APPIUM_IDS.md}. Convention:
 * {@code jts.tb.<surface>.<control>}.
 */
public final class AutomationIds {

  private AutomationIds() {
  }

  public static final String TOOLBAR_CASE_PREV = "jts.tb.toolbar.case.prev";
  public static final String TOOLBAR_CASE_NEXT = "jts.tb.toolbar.case.next";
  public static final String TOOLBAR_CASE_NEW = "jts.tb.toolbar.case.new";
  public static final String TOOLBAR_CASE_COPY = "jts.tb.toolbar.case.copy";
  public static final String TOOLBAR_CASE_DELETE = "jts.tb.toolbar.case.delete";

  public static final String TOOLBAR_ZOOM_ONE_TO_ONE = "jts.tb.toolbar.zoom.oneToOne";
  public static final String TOOLBAR_ZOOM_INPUT = "jts.tb.toolbar.zoom.input";
  public static final String TOOLBAR_ZOOM_INPUT_A = "jts.tb.toolbar.zoom.inputA";
  public static final String TOOLBAR_ZOOM_INPUT_B = "jts.tb.toolbar.zoom.inputB";
  public static final String TOOLBAR_ZOOM_RESULT = "jts.tb.toolbar.zoom.result";
  public static final String TOOLBAR_ZOOM_FULL = "jts.tb.toolbar.zoom.fullExtent";

  public static final String TOOLBAR_DRAW_RECTANGLE = "jts.tb.toolbar.draw.rectangle";
  public static final String TOOLBAR_DRAW_POLYGON = "jts.tb.toolbar.draw.polygon";
  public static final String TOOLBAR_DRAW_LINESTRING = "jts.tb.toolbar.draw.lineString";
  public static final String TOOLBAR_DRAW_POINT = "jts.tb.toolbar.draw.point";
  /** Present on PR #7 / curve builds only. */
  public static final String TOOLBAR_DRAW_CIRCULARSTRING = "jts.tb.toolbar.draw.circularString";
  /** Present on PR #7 / curve builds only. */
  public static final String TOOLBAR_DRAW_COMPOUNDCURVE = "jts.tb.toolbar.draw.compoundCurve";
  /** Present on PR #7 / curve builds only. */
  public static final String TOOLBAR_DRAW_CURVEPOLYGON = "jts.tb.toolbar.draw.curvePolygon";
  /** Present on PR #7 / curve builds only. */
  public static final String TOOLBAR_DRAW_TRIANGLE = "jts.tb.toolbar.draw.triangle";
  /** Present on PR #7 / curve builds only. */
  public static final String TOOLBAR_DRAW_TIN = "jts.tb.toolbar.draw.tin";

  public static final String TOOLBAR_ZOOM_MODE = "jts.tb.toolbar.mode.zoom";
  public static final String TOOLBAR_PAN_MODE = "jts.tb.toolbar.mode.pan";
  public static final String TOOLBAR_INFO_MODE = "jts.tb.toolbar.mode.info";
  public static final String TOOLBAR_EDIT_VERTEX = "jts.tb.toolbar.mode.editVertex";

  public static final String TOOLBAR_MODE_MOVE = "jts.tb.toolbar.mode.move";
  public static final String TOOLBAR_EXTRACT_ELEMENTS = "jts.tb.toolbar.extractElements";
  public static final String TOOLBAR_SELECT_ELEMENTS = "jts.tb.toolbar.selectElements";
  public static final String TOOLBAR_DELETE_VERTEX = "jts.tb.toolbar.deleteVertex";


  public static final String WKT_A = "jts.tb.wkt.a";
  public static final String WKT_B = "jts.tb.wkt.b";
  public static final String WKT_LOAD = "jts.tb.wkt.load";
  public static final String WKT_INSPECT = "jts.tb.wkt.inspect";
  public static final String WKT_EXCHANGE = "jts.tb.wkt.exchange";
  public static final String WKT_A_COPY = "jts.tb.wkt.a.copy";
  public static final String WKT_A_PASTE = "jts.tb.wkt.a.paste";
  public static final String WKT_A_CLEAR = "jts.tb.wkt.a.clear";
  public static final String WKT_B_COPY = "jts.tb.wkt.b.copy";
  public static final String WKT_B_PASTE = "jts.tb.wkt.b.paste";
  public static final String WKT_B_CLEAR = "jts.tb.wkt.b.clear";

  /** PR #7+ curve strategy menu / status. */
  public static final String MENU_CURVE_STRATEGY_LINEARIZED =
      "jts.tb.menu.edit.curveStrategy.linearized";
  public static final String MENU_CURVE_STRATEGY_PRESERVE =
      "jts.tb.menu.edit.curveStrategy.preserve";
  public static final String STATUS_CURVE_STRATEGY = "jts.tb.status.curveStrategy";


  public static final String FN_TREE = "jts.tb.fn.tree";
  public static final String FN_EXEC = "jts.tb.fn.exec";
  public static final String FN_EXEC_NEW = "jts.tb.fn.execToNew";
  public static final String FN_PARAM_0 = "jts.tb.fn.param.0";
  public static final String FN_PARAM_1 = "jts.tb.fn.param.1";
  public static final String FN_PARAM_2 = "jts.tb.fn.param.2";
  public static final String FN_PARAM_3 = "jts.tb.fn.param.3";
  public static final String FN_PARAM_4 = "jts.tb.fn.param.4";

  public static void set(JComponent c, String id) {
    if (c == null || id == null || id.length() == 0) {
      return;
    }
    c.setName(id);
    c.getAccessibleContext().setAccessibleName(id);
  }
}
