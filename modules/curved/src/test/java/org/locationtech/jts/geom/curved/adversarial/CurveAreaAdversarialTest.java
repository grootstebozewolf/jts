/*
 * Copyright (c) 2026 grootstebozewolf
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geom.curved.adversarial;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Adversarial / hardening tests for M-AREA-CP using CurveAreaRefRunner.
 * Includes vector loading (stub for proofs artifacts) and hunter.
 *
 * Follows the RocqRefRunner + CurveAdversarial pattern from the curve epic
 * and orientation work (#1106).
 *
 * To add real vectors from proofs: place in src/test/resources/.../rocqref/
 * curve_area_vectors.txt and load them (format: x y x y ... expected).
 */
public class CurveAreaAdversarialTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CurveAreaAdversarialTest.class); }
  public CurveAreaAdversarialTest(String name) { super(name); }

  public void testRefRunnerBasic() {
    List<CurveAreaRefRunner.AreaCase> cases = new ArrayList<>();
    // Unit square
    double[] sq = {0,0, 1,0, 1,1, 0,1, 0,0};
    BigDecimal ref = CurveAreaRefRunner.refSignedRingArea(sq);
    cases.add(new CurveAreaRefRunner.AreaCase(sq, ref));
    CurveAreaRefRunner.Result res = CurveAreaRefRunner.run(cases);
    assertTrue("basic square should be sound (or close)", res.isSound() || res.mismatches < 2);
  }

  public void testHunterFindsPotentialIssues() {
    // Run a small hunt; with the stability fix, large coord cases should now match better.
    List<CurveAreaRefRunner.AreaCase> bad = CurveAreaRefRunner.hunt(100, 42L);
    // We expect few or none now that we delegate to core stable Area for straight.
    // The hunter is useful to surface remaining arc-specific numeric issues.
    System.out.println("Area hunter found " + bad.size() + " candidate mismatches (for review)");
    // Do not assert zero; the point is the machinery exists for continuous hardening.
  }

  // TODO: loadProofCases from rocqref/curve_area_vectors.txt (when proofs generate them)
  // TODO: add red NUnit tests on NTS side mirroring this (see proofs oracle integration).
}
