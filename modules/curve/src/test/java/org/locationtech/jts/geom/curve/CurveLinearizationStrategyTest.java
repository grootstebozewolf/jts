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
package org.locationtech.jts.geom.curve;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * MMF: no silent linearization — default LINEARIZED warns; PRESERVE keeps type.
 */
public class CurveLinearizationStrategyTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(CurveLinearizationStrategyTest.class);
  }

  public CurveLinearizationStrategyTest(String name) {
    super(name);
  }

  @Override
  protected void tearDown() {
    CurveLinearizationStrategy.clearThreadOverride();
    CurveLinearizationStrategy.setDefault(CurveLinearizationStrategy.LINEARIZED);
  }

  public void testDefaultIsLinearized() {
    assertEquals(CurveLinearizationStrategy.LINEARIZED,
        CurveLinearizationStrategy.getDefault());
    assertEquals(CurveLinearizationStrategy.LINEARIZED,
        CurveLinearizationStrategy.current());
  }

  public void testPreserveReturnsSameInstance() throws Exception {
    Geometry cs = new CurveWKTReader().read("CIRCULARSTRING (0 0, 5 5, 10 0)");
    CurveLinearizationStrategy.setThreadOverride(
        CurveLinearizationStrategy.PRESERVE);
    Geometry out = CurveOps.linearise(cs);
    assertSame(cs, out);
    assertTrue(out instanceof CircularString);
  }

  public void testLinearizedWarnsAndDensifies() throws Exception {
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
      assertTrue("expected linearization warning", cap.sawWarning);
      assertTrue(cap.message.indexOf("linearizing") >= 0);
    }
    finally {
      log.removeHandler(cap);
      log.setLevel(prev);
    }
  }

  private static final class CapturingHandler extends Handler {
    boolean sawWarning;
    String message;

    @Override
    public void publish(LogRecord record) {
      if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
        sawWarning = true;
        message = record.getMessage();
      }
    }

    @Override
    public void flush() { }

    @Override
    public void close() { }
  }
}
