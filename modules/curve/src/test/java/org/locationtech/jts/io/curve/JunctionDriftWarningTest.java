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
/*
 * AI Disclosure (Eclipse Foundation GenAI Guidelines):
 * AI-generated portions are dedicated to CC0-1.0; human-reviewed.
 *
 * SPDX-License-Identifier: (EPL-2.0 OR EDL-1.0) AND CC0-1.0
 * Assisted-by: xAI Grok (grok-4.3)
 * Assisted-by: Claude (Opus-4.7)
 */
package org.locationtech.jts.io.curve;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * §3.3 — junction drift warning. After a CLOTHOID, the next member's
 * typed start coordinate is authoritative, but if it disagrees with the
 * clothoid's analytical end by more than the threshold the reader emits
 * a warning. The constructed geometry uses the typed coordinate either
 * way; warnings are purely informational and accessed via
 * {@link CurveWKTReader#getWarnings()}.
 */
public class JunctionDriftWarningTest extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(JunctionDriftWarningTest.class); }
  public JunctionDriftWarningTest(String name) { super(name); }

  private CurveWKTReader newReader() {
    return new CurveWKTReader(new CurveGeometryFactory());
  }

  /** A correctly-aligned WKT (typed coord exactly matches the clothoid's
   *  analytical end) emits zero warnings. The §3.4 fix means real-world
   *  data lands here cleanly. */
  public void testNoDriftNoWarnings() throws Exception {
    CurveWKTReader r = newReader();
    // Clothoid (κ:0 → 0.005, L=48) starting at (0,0) heading 0 ends at
    // approximately (47.930926064689615, 1.9180260474731798). Use the
    // exact value so drift = 0.
    Geometry g = r.read(
        "COMPOUNDCURVE ((-100 0, 0 0), CLOTHOID (0, 0.005, 48), "
        + "(47.930926064689615 1.9180260474731798, 100 0))");
    assertNotNull(g);
    assertTrue(g instanceof CompoundCurve);
    assertTrue("clean alignment must not warn, got: " + r.getWarnings(),
        r.getWarnings().isEmpty());
  }

  /** A WKT with the next-member typed start displaced by 1 m from the
   *  clothoid's analytical end emits exactly one warning, but the
   *  parsed geometry still uses the typed coordinate. */
  public void testDriftEmitsWarning() throws Exception {
    CurveWKTReader r = newReader();
    // Deliberately wrong typed start -- 1 m off the analytical end in x.
    Geometry g = r.read(
        "COMPOUNDCURVE ((-100 0, 0 0), CLOTHOID (0, 0.005, 48), "
        + "(48.930926064689615 1.9180260474731798, 100 0))");
    assertNotNull(g);
    assertEquals("expected exactly one drift warning, got: " + r.getWarnings(),
        1, r.getWarnings().size());
    String warning = r.getWarnings().get(0);
    assertTrue("warning text must mention drift magnitude: " + warning,
        warning.contains("drift"));
    assertTrue("warning must reference §3.3: " + warning,
        warning.contains("3.3"));
    // Typed coord wins -- the third member starts where it was typed,
    // not at the analytical end.
    CompoundCurve cc = (CompoundCurve) g;
    assertEquals(48.930926064689615, cc.getMemberN(2).getCoordinates()[0].x, 0.0);
  }

  /** Drift below the threshold (1e-9 relative to chord) does not warn. */
  public void testTinyDriftBelowThresholdSilent() throws Exception {
    CurveWKTReader r = newReader();
    // ~1e-15 m drift in y (least-significant digit perturbed), chord
    // ~100 m, threshold 1e-9 · 100 = 1e-7 m. Drift is ~8 orders of
    // magnitude below threshold.
    Geometry g = r.read(
        "COMPOUNDCURVE ((-100 0, 0 0), CLOTHOID (0, 0.005, 48), "
        + "(47.930926064689615 1.9180260474731799, 100 0))");
    assertNotNull(g);
    assertTrue("sub-threshold drift must not warn, got: " + r.getWarnings(),
        r.getWarnings().isEmpty());
  }

  /** Multiple drifted clothoid junctions in one COMPOUNDCURVE produce
   *  one warning per drifted junction (the warnings list accumulates). */
  public void testMultipleDriftsAccumulate() throws Exception {
    CurveWKTReader r = newReader();
    Geometry g = r.read(
        "COMPOUNDCURVE ((-100 0, 0 0), CLOTHOID (0, 0.005, 48), "
        // first drift: ~0.5 m off analytical end (47.93, 1.92)
        + "(48.43 2.0, 60 5), "
        + "CLOTHOID (0, -0.005, 30), "
        // second drift: ~0.3 m off this clothoid's analytical end (89.98, 4.25)
        + "(89.7 4.0, 100 0))");
    assertNotNull(g);
    assertEquals("two drifted junctions should produce two warnings",
        2, r.getWarnings().size());
  }

  /** {@code clearWarnings()} resets the accumulator without affecting the
   *  reader's other state. */
  public void testClearWarningsResets() throws Exception {
    CurveWKTReader r = newReader();
    r.read("COMPOUNDCURVE ((-100 0, 0 0), CLOTHOID (0, 0.005, 48), "
        + "(48.930926064689615 1.9180260474731798, 100 0))");
    assertEquals(1, r.getWarnings().size());
    r.clearWarnings();
    assertTrue(r.getWarnings().isEmpty());
  }
}
