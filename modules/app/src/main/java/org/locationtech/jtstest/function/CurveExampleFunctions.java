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
package org.locationtech.jtstest.function;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jts.io.curved.CurvedWKTReader;
import org.locationtech.jtstest.geomfunction.Metadata;

/**
 * Playground entries for the JTS curve extensions discussed in the
 * <a href="https://github.com/antlr/grammars-v4/discussions/4847">CLOTHOID
 * proposal (grammars-v4 #4847)</a>. Each function returns a baked-in
 * example WKT so the user can click once in TestBuilder and immediately
 * see a curve geometry rendered, parsed, and ready for buffer / overlay /
 * mergeCurves experimentation.
 *
 * <p>The {@code Geometry g} parameter is unused — TestBuilder requires
 * functions to take at least one geometry argument so they slot into the
 * function tree, but these are pure generators and don't need an input.
 */
public class CurveExampleFunctions {

  /**
   * Highway entry-and-exit spiral pattern from §4.1 of the proposal:
   * straight → entry clothoid → circular arc → exit clothoid → straight.
   * Two transition curves bracket a 200-unit-radius arc.
   */
  @Metadata(description="Sample COMPOUNDCURVE: highway entry/exit spiral (line → CLOTHOID → arc → CLOTHOID → line)")
  public static Geometry clothoidHighwayBend(Geometry g) {
    String wkt =
        "COMPOUNDCURVE ("
        + "(0 0, 100 0), "
        + "CLOTHOID (0, 0.005, 80), "
        + "CIRCULARSTRING (179.68 5.32, 196.7 14.4, 195.0 32.5), "
        + "CLOTHOID (0.005, 0, 80), "
        + "(231 75, 300 75)"
        + ")";
    return readCurved(g, wkt);
  }

  /**
   * Single CLOTHOID transition: 100-unit straight followed by an
   * 80-unit clothoid curving from κ=0 to κ=0.005 (R=200 inside the
   * bend at the spiral's end). Useful for inspecting one transition
   * in isolation without the arc-and-return complication.
   */
  @Metadata(description="Sample COMPOUNDCURVE: line + single CLOTHOID transition (κ goes 0 → 0.005 over L=80)")
  public static Geometry clothoidSingleTransition(Geometry g) {
    return readCurved(g,
        "COMPOUNDCURVE ((0 0, 100 0), CLOTHOID (0, 0.005, 80))");
  }

  /**
   * Tight spiral with κ swinging from -0.02 to 0.02 over 60 units —
   * dramatic visual S-bend. Useful for stress-testing the
   * densifier and renderer.
   */
  @Metadata(description="Sample COMPOUNDCURVE: line + dramatic CLOTHOID S-bend (κ swings −0.02 → 0.02 over L=60)")
  public static Geometry clothoidSBend(Geometry g) {
    return readCurved(g,
        "COMPOUNDCURVE ((0 0, 50 0), CLOTHOID (-0.02, 0.02, 60))");
  }

  // ---------------- helpers --------------------------------------

  private static Geometry readCurved(Geometry g, String wkt) {
    GeometryFactory base = (g != null) ? g.getFactory() : null;
    CurvedGeometryFactory cf = (base instanceof CurvedGeometryFactory)
        ? (CurvedGeometryFactory) base
        : new CurvedGeometryFactory();
    try {
      return new CurvedWKTReader(cf).read(wkt);
    } catch (Exception ex) {
      throw new RuntimeException("CurvedWKTReader failed on example: " + wkt, ex);
    }
  }
}
