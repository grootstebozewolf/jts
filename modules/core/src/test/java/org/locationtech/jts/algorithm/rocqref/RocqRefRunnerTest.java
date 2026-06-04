/*
 * Copyright (c) 2026 Martin Davis / locationtech.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.algorithm.rocqref;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import java.util.List;

/**
 * Tests for RocqRefRunner: loads vectors from proofs artifacts (e.g. the
 * one at the query URL for exact full-binary64 orientation soundness, JTS #1106),
 * validates them, hardens JTS vs the certified ORIENT_EXACT oracle, and runs
 * a hunter for more adversarial cases.
 *
 * To refresh vectors: use RocqRefRunner (oracle_bin) in proofs + gen_*.sh
 * with the artifact, then drop the .txt here (and in curved).
 */
public class RocqRefRunnerTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(RocqRefRunnerTest.class);
  }

  public RocqRefRunnerTest(String name) { super(name); }

  public void testLoadAndValidateOrientationVectors() throws Exception {
    String res = "/org/locationtech/jts/algorithm/rocqref/orientation_proof_vectors.txt";
    List<RocqRefRunner.OrientCase> cases = RocqRefRunner.loadProofCases(res);
    assertTrue("should have loaded proof cases from artifact", cases.size() > 0);
    RocqRefRunner.Result r = RocqRefRunner.run(cases);
    assertTrue("JTS must be sound on all Rocq proof vectors (from " +
        "https://github.com/grootstebozewolf/NetTopologySuite.Proofs/actions/runs/26798343472/artifacts/7349017657 ): " + r,
        r.isSound());
  }

  public void testHunterFindsOrFixesAdversarialOrientationBugs() {
    // Hunter generates including extreme-magnitude cases (the ones the new
    // ORIENT_EXACT artifact covers, where old DD overflows).
    List<RocqRefRunner.OrientCase> bad = RocqRefRunner.hunt(5000);
    if (!bad.isEmpty()) {
      System.out.println("RocqRefRunner hunter found " + bad.size() + " mismatches (pre-fix?):");
      for (int i=0; i<Math.min(3, bad.size()); i++) System.out.println("  " + bad.get(i));
    }
    // After our fix in CGAlgorithmsDD (BD fallback for DD-zero cases), expect 0.
    assertTrue("RocqRefRunner hunter + BD fallback fix: no adversarial orientation bugs remain. Found: " + bad.size(),
        bad.isEmpty());
  }

  public void testExtremeOverflowCaseFromArtifactVectors() throws Exception {
    // Specific from gen_orientation_vectors.sh / artifact:
    // 0 0  0x1p+512 0  0 0x1p+512   POS
    double p0x=0, p0y=0, p1x=Math.pow(2,512), p1y=0, qx=0, qy=Math.pow(2,512);
    int exp = RocqRefRunner.exactSignBD(p0x,p0y,p1x,p1y,qx,qy);
    assertEquals("exact from Rocq must be POS for this overflow vector", 1, exp);
    RocqRefRunner.OrientCase c = new RocqRefRunner.OrientCase(p0x,p0y,p1x,p1y,qx,qy,exp);
    assertEquals("JTS (post fix) must match Rocq ORIENT_EXACT on 2^512 overflow case", exp, c.jtsSign());
  }
}
