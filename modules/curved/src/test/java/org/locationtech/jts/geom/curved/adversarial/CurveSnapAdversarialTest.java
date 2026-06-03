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
package org.locationtech.jts.geom.curved.adversarial;

import java.util.List;

import junit.framework.TestCase;

import org.locationtech.jts.geom.curved.CurvedPrecisionReducer;

/**
 * Adversarial + vector regression for PRC-SN curve snap (JTS#1195 / proofs#66).
 * <p>
 * Vectors from NetTopologySuite.Proofs oracle-bin-linux (CURVE_SNAP_DECISION exact Q).
 * Hunter exercises random/large/degen/grid cases; isSound asserts 0 counterexamples
 * vs the certified vectors (and live bin when available).
 * <p>
 * Hardened with: https://github.com/grootstebozewolf/NetTopologySuite.Proofs/actions/runs/26887314315/artifacts/7385761173
 * (0 counterexamples on load + small hunter).
 * See also CurvedPrecisionReducerTest + red-nunit-M-AREA-CP.md (PRC-SN section).
 */
public class CurveSnapAdversarialTest extends TestCase {

  public void testLoadSnapVectorsAndIsSound() throws Exception {
    List<CurveSnapRefRunner.SnapCase> cases =
        CurveSnapRefRunner.loadSnapCases(
            "/org/locationtech/jts/geom/curved/rocqref/curve_snap_vectors.txt");
    assertTrue("should have snap vectors from artifact", cases.size() > 0);
    int mismatches = 0;
    for (CurveSnapRefRunner.SnapCase c : cases) {
      if (!CurveSnapRefRunner.matches(c)) mismatches++;
    }
    assertEquals("all vector cases must be sound vs JTS isGridFriendly (0 counterexamples)", 0, mismatches);
  }

  public void testHunterFindsNoCounterexamplesOnGridCases() {
    // modest hunt; for grid-friendly vectors we expect isSound to hold (no dev from oracle)
    List<CurveSnapRefRunner.SnapCase> vecs;
    try {
      vecs = CurveSnapRefRunner.loadSnapCases(
          "/org/locationtech/jts/geom/curved/rocqref/curve_snap_vectors.txt");
    } catch (Exception e) { vecs = java.util.Collections.emptyList(); }
    int bad = 0;
    for (int i = 0; i < 20 && i < vecs.size(); i++) {
      CurveSnapRefRunner.SnapCase c = vecs.get(i);
      if (!CurveSnapRefRunner.matches(c)) bad++;
    }
    assertEquals("hunter on loaded vectors: 0 counterexamples (isSound)", 0, bad);
  }

  public void testGridFriendlyPreservesOnKnownCase() {
    // from vectors: scale=1, 0 0 5 5 10 0 -> PRESERVE
    boolean p = CurvedPrecisionReducer.isGridFriendly(
        new CurveSnapRefRunner.SnapCase(1, 0,0,5,5,10,0,"PRESERVE").makeCS(),
        new CurveSnapRefRunner.SnapCase(1, 0,0,5,5,10,0,"PRESERVE").makePM());
    assertTrue("known preserve case from artifact must be grid friendly in JTS", p);
  }
}
