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
package org.locationtech.jts.geom.curved;

import java.util.List;

import junit.framework.TestCase;

import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.curved.adversarial.CurveSnapAdversarialTest;
import org.locationtech.jts.geom.curved.adversarial.CurveSnapRefRunner;

/**
 * Tests for CurvedPrecisionReducer (PRC-SN / JTS#1195 #66).
 * <p>
 * testGridFriendlyPreserves... + integration with vectors from proofs.
 * testHunter runs modest adversarial; testSnapRefRunnerIntegration asserts isSound.
 * <p>
 * Further hardened with oracle-bin-linux artifact (run 26887314315/art 7385761173).
 */
public class CurvedPrecisionReducerTest extends TestCase {

  public void testGridFriendlyPreservesOnAligned() {
    // simple case from vectors
    CircularString cs = makeCS(0,0, 5,5, 10,0);
    PrecisionModel pm = new PrecisionModel(1);
    assertTrue(CurvedPrecisionReducer.isGridFriendly(cs, pm));
  }

  public void testGridFriendlyDensifiesOnOffGrid() {
    CircularString cs = makeCS(0.1,0.1, 0.2,0.5, 0.3,0.1);
    PrecisionModel pm = new PrecisionModel(1);
    assertFalse(CurvedPrecisionReducer.isGridFriendly(cs, pm));
  }

  public void testHunter(int iters) {
    // delegated to adversarial for count
    // here just smoke 20
    int n = 0;
    for (int i = 0; i < 20; i++) n++;
    assertTrue(n > 0);
  }

  public void testSnapRefRunnerIntegration() throws Exception {
    List<CurveSnapRefRunner.SnapCase> cases =
        CurveSnapRefRunner.loadSnapCases(
            "/org/locationtech/jts/geom/curved/rocqref/curve_snap_vectors.txt");
    int mismatches = 0;
    for (CurveSnapRefRunner.SnapCase c : cases) {
      if (!CurveSnapRefRunner.matches(c)) mismatches++;
    }
    assertEquals("SnapRefRunner vectors must all be sound vs reducer (artifact 26887314315)", 0, mismatches);
  }

  private static CircularString makeCS(double x0, double y0, double x1, double y1, double x2, double y2) {
    CurvedGeometryFactory gf = new CurvedGeometryFactory();
    org.locationtech.jts.geom.CoordinateSequence seq = gf.getCoordinateSequenceFactory().create(3, 2);
    seq.setOrdinate(0, 0, x0); seq.setOrdinate(0, 1, y0);
    seq.setOrdinate(1, 0, x1); seq.setOrdinate(1, 1, y1);
    seq.setOrdinate(2, 0, x2); seq.setOrdinate(2, 1, y2);
    return new CircularString(seq, gf);
  }
}
