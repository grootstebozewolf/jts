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
package org.locationtech.jts.geom.curve;

import java.util.concurrent.atomic.AtomicReference;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Phase 5 (#1195): WarnSink receives linearization warnings for UI log.
 */
public class CurveLinearizationWarnSinkTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(CurveLinearizationWarnSinkTest.class);
  }

  public CurveLinearizationWarnSinkTest(String name) {
    super(name);
  }

  public void testWarnSinkReceivesMessage() throws Exception {
    final AtomicReference<String> got = new AtomicReference<String>();
    CurveLinearizationStrategy.setWarnSink(
        new CurveLinearizationStrategy.WarnSink() {
          public void warn(String message) {
            got.set(message);
          }
        });
    try {
      Geometry cs = new CurveWKTReader(new CurveGeometryFactory())
          .read("CIRCULARSTRING (0 0, 5 5, 10 0)");
      CurveOps.linearise(cs);
      assertNotNull(got.get());
      assertTrue(got.get().indexOf("CircularString") >= 0);
      assertTrue(got.get().indexOf("LINEARIZED") >= 0);
    }
    finally {
      CurveLinearizationStrategy.setWarnSink(null);
    }
  }
}
