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

import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;

/**
 * JUnit exercising the curve adversarial hunter + ref runner (in the spirit
 * of OrientationDDRobustnessTest + RocqRefRunnerTest from locationtech/jts#1197).
 * <p>
 * Now that native circular arc length is implemented (M-LEN-CS, #1195),
 * {@link CircularString#getLength()} returns the analytical {@code r*theta}
 * length, so these assertions verify it agrees with the exact oracle on the
 * committed reference vectors and across the adversarial generators (the
 * previous chord-vs-arc deviations are gone; only fp-level residuals remain).
 */
public class CurveAdversarialTest extends TestCase {

  public void testLoadArcLengthVectors() throws Exception {
    // The committed oracle artifact (exact ARC_LENGTH outputs of the Rocq/Coq
    // development), re-validated on load against the in-Java oracle.
    List<CurveRefRunner.ArcLengthCase> cases =
        CurveRefRunner.loadArcLengthCases(
            "/org/locationtech/jts/geom/curved/rocqref/curve_arc_length_vectors.txt");
    assertTrue("should have loaded some reference cases", cases.size() > 0);
    for (CurveRefRunner.ArcLengthCase c : cases) {
      double derived = CurveRefRunner.exactCircularArcLength(
          c.sx, c.sy, c.mx, c.my, c.ex, c.ey);
      // c.expectedLength is the oracle's certified ARC_LENGTH; the in-Java
      // reference reproduces it to fp precision (relative, since arcs range over
      // many magnitudes here).
      assertEquals("vector case must match oracle", c.expectedLength, derived,
          1e-7 * Math.max(1.0, Math.abs(c.expectedLength)));
    }
  }

  public void testCircularStringLengthMatchesOracleVectors() throws Exception {
    List<CurveRefRunner.ArcLengthCase> cases =
        CurveRefRunner.loadArcLengthCases(
            "/org/locationtech/jts/geom/curved/rocqref/curve_arc_length_vectors.txt");
    for (CurveRefRunner.ArcLengthCase c : cases) {
      double len = make3pt(c.sx, c.sy, c.mx, c.my, c.ex, c.ey).getLength();
      assertEquals("CircularString.getLength must equal the exact arc length for " + c,
          c.expectedLength, len, 1e-9 * Math.max(1.0, Math.abs(c.expectedLength)));
    }
  }

  public void testNoChordVsArcDeviationOnAdversarialInputs() {
    // The hunter compares CircularString.getLength() against the in-Java oracle.
    // With native arc length implemented, the large chord-vs-arc gaps it used to
    // surface are gone; any residual must be fp-level (<= 1e-6 relative).
    List<CurveCounterexampleHunter.Mismatch> bad =
        CurveCounterexampleHunter.huntArcLength(2_000);
    for (CurveCounterexampleHunter.Mismatch m : bad) {
      assertTrue("residual arc-length deviation must be fp-level, was " + m,
          m.delta <= 1e-6 * Math.max(1.0, Math.abs(m.exactLength)));
    }
  }

  private static CircularString make3pt(double sx, double sy, double mx, double my,
                                        double ex, double ey) {
    org.locationtech.jts.geom.CoordinateSequence cs =
        new CurvedGeometryFactory().getCoordinateSequenceFactory().create(3, 2);
    cs.setOrdinate(0, 0, sx); cs.setOrdinate(0, 1, sy);
    cs.setOrdinate(1, 0, mx); cs.setOrdinate(1, 1, my);
    cs.setOrdinate(2, 0, ex); cs.setOrdinate(2, 1, ey);
    return new CircularString(cs, new CurvedGeometryFactory());
  }
}
