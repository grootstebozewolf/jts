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
package org.locationtech.jtstest.testbuilder.appium;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurveLinearizationStrategy;
import org.locationtech.jts.geom.curve.CurveOps;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jtstest.testbuilder.ui.AutomationIds;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Draft v6 MMF Option B contracts for the Appium / TestBuilder surface:
 * no silent linearization; default {@code LINEARIZED} warns;
 * {@code PRESERVE} keeps curve identity; strategy AutomationIds exist;
 * overlay name stays OverlayNGCurve (never Curved).
 */
public class TbAppiumOptionBContractTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(TbAppiumOptionBContractTest.class);
  }

  public TbAppiumOptionBContractTest(String name) {
    super(name);
  }

  @Override
  protected void tearDown() {
    CurveLinearizationStrategy.clearThreadOverride();
    CurveLinearizationStrategy.setDefault(CurveLinearizationStrategy.LINEARIZED);
  }

  public void testStrategyAutomationIdsPresent() {
    assertEquals("jts.tb.menu.edit.curveStrategy.linearized",
        AutomationIds.MENU_CURVE_STRATEGY_LINEARIZED);
    assertEquals("jts.tb.menu.edit.curveStrategy.preserve",
        AutomationIds.MENU_CURVE_STRATEGY_PRESERVE);
    assertEquals("jts.tb.status.curveStrategy",
        AutomationIds.STATUS_CURVE_STRATEGY);
  }

  public void testDefaultStrategyIsLinearized() {
    assertEquals(CurveLinearizationStrategy.LINEARIZED,
        CurveLinearizationStrategy.getDefault());
  }

  public void testLinearizedWarnsOnDensify() throws Exception {
    Geometry cs = new CurveWKTReader().read("CIRCULARSTRING (0 0, 5 5, 10 0)");
    Logger log = Logger.getLogger(CurveLinearizationStrategy.class.getName());
    CapturingHandler cap = new CapturingHandler();
    log.addHandler(cap);
    Level prev = log.getLevel();
    log.setLevel(Level.WARNING);
    try {
      CurveLinearizationStrategy.setThreadOverride(
          CurveLinearizationStrategy.LINEARIZED);
      Geometry out = CurveOps.linearise(cs);
      assertFalse(out instanceof CircularString);
      assertTrue("Option B: LINEARIZED must warn", cap.sawWarning);
      assertTrue(cap.message.indexOf("linearizing") >= 0);
    } finally {
      log.removeHandler(cap);
      log.setLevel(prev);
    }
  }

  public void testPreserveKeepsCircularString() throws Exception {
    Geometry cs = new CurveWKTReader().read("CIRCULARSTRING (0 0, 5 5, 10 0)");
    CurveLinearizationStrategy.setThreadOverride(
        CurveLinearizationStrategy.PRESERVE);
    Geometry out = CurveOps.linearise(cs);
    assertSame(cs, out);
    assertTrue(out instanceof CircularString);
  }

  public void testPr7SequencesPinOptionBStrategyIds() throws Exception {
    // Representative densify path must document strategy IDs in the suite.
    String toLinear = TbAppiumPaths.readFile(
        TbAppiumPaths.sequence("Curve", "toLinear.disc.pr7.json"));
    assertTrue(toLinear.contains(AutomationIds.MENU_CURVE_STRATEGY_LINEARIZED)
        || toLinear.contains("optionB"));
    assertTrue(toLinear.contains("LINEARIZED") || toLinear.contains("optionB"));
  }

  private static final class CapturingHandler extends Handler {
    boolean sawWarning;
    String message = "";

    public void publish(LogRecord record) {
      if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
        sawWarning = true;
        message = String.valueOf(record.getMessage());
      }
    }

    public void flush() {
    }

    public void close() {
    }
  }
}
