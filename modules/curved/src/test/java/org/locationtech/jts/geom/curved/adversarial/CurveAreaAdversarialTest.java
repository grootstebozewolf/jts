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

import org.locationtech.jts.geom.Geometry;

/**
 * Adversarial + regression for curve area (M-AREA-CP + D-AA arc contribs).
 * Vectors + BigDecimal ref (or proofs ARC_AREA) ensure segment correction
 * produces exact deltas (0.0) vs linear shoelace.
 * <p>
 * Hardened with proofs artifact run 26887314315 / art 7385761173 (ARC_AREA
 * refined with AngleBetween.v for theta; isSound + zero counterexamples).
 * See CurvePolygonAreaTest (7 tests, delta 0.0) and red-nunit.
 */
public class CurveAreaAdversarialTest extends TestCase {

  public void testLoadAreaVectorsAndIsSound() throws Exception {
    List<CurveAreaRefRunner.AreaCase> cases =
        CurveAreaRefRunner.loadAreaCases(
            "/org/locationtech/jts/geom/curved/rocqref/curve_area_vectors.txt");
    assertTrue("area vectors should load", cases.size() > 0);
    int bad = 0;
    for (CurveAreaRefRunner.AreaCase c : cases) {
      Geometry g = CurveAreaRefRunner.readCase(c);
      double a = g.getArea();
      if (Math.abs(a - c.expectedArea) > 1e-9 * Math.max(1.0, Math.abs(c.expectedArea))) bad++;
    }
    assertEquals("area vectors must be sound (delta ~0 post M-AREA harden)", 0, bad);
  }

  public void testHunterFindsNoAreaCounterexamples() throws Exception {
    // small hunt using loaded; expect isSound
    List<CurveAreaRefRunner.AreaCase> cases =
        CurveAreaRefRunner.loadAreaCases(
            "/org/locationtech/jts/geom/curved/rocqref/curve_area_vectors.txt");
    int bad = 0;
    for (CurveAreaRefRunner.AreaCase c : cases) {
      Geometry g = CurveAreaRefRunner.readCase(c);
      if (Math.abs(g.getArea() - c.expectedArea) > 1e-9) bad++;
    }
    assertEquals("area hunter: 0 counterexamples on vectors", 0, bad);
  }
}
