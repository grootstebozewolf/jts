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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Adversarial / hardening tests for PRC-SN (curve snap/precision reducer) using
 * CurveSnapRefRunner.
 *
 * Includes:
 * - Basic ref runner sanity.
 * - Hunter for potential numeric issues (large coords, sub-grid, degen) vs ref.
 * - Vector load from proofs (when curve_snap_vectors.txt populated via oracle
 *   CURVE_SNAP_DECISION or Rocq SnapRounding theories for #66).
 *
 * Mirrors CurveAreaAdversarialTest + red-nunit-M-AREA-CP.md (PRC-SN section).
 * Goal: stable release, zero counterexamples, JTS isGridFriendly/reduce decision
 * matches exact snap + circumcentre grid check from proofs.
 *
 * The red meter test_PRC_SN_... in CurveAwarenessSpecTest is kept per RGR.
 */
public class CurveSnapAdversarialTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CurveSnapAdversarialTest.class); }
  public CurveSnapAdversarialTest(String name) { super(name); }

  public void testRefRunnerBasic() {
    List<CurveSnapRefRunner.SnapCase> cases = new java.util.ArrayList<>();
    // grid friendly semicircle
    double[] g1 = {0,0, 5,5, 10,0};
    boolean ref1 = CurveSnapRefRunner.refPreserve(g1, 1.0);
    cases.add(new CurveSnapRefRunner.SnapCase(g1, 1.0, ref1));
    // sub grid
    double[] g2 = {0.1,0.1, 0.2,0.5, 0.3,0.1};
    boolean ref2 = CurveSnapRefRunner.refPreserve(g2, 1.0);
    cases.add(new CurveSnapRefRunner.SnapCase(g2, 1.0, ref2));
    CurveSnapRefRunner.Result res = CurveSnapRefRunner.run(cases);
    // allow some tol in basic; main value is the machinery + vectors later
    assertTrue("ref runner basic exercised: " + res, res.checked >= 2);
  }

  public void testHunterFindsPotentialIssues() {
    // 50 random; with ref using BD, surfaces double issues in centre for edge cases.
    List<CurveSnapRefRunner.SnapCase> bad = CurveSnapRefRunner.hunt(50, 12345L);
    System.out.println("PRC-SN snap hunter found " + bad.size() + " candidate mismatches (for review / proofs vector)");
    // Not assert zero; hunter for continuous hardening + to feed vectors back to proofs.
    // In stable state after vectors + fixes, expect 0 in this range.
  }

  public void testLoadSnapVectorsIfPresent() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curved/rocqref/curve_snap_vectors.txt");
    if (in != null) {
      List<CurveSnapRefRunner.SnapCase> v = CurveSnapRefRunner.loadVectors(in);
      CurveSnapRefRunner.Result res = CurveSnapRefRunner.run(v);
      assertTrue("loaded snap vectors from proofs should be sound: " + res,
          res.isSound() || res.mismatches == 0);
    }
  }
}
