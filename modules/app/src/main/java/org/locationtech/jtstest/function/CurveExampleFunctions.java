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
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;
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
   * Real-world example from the
   * <a href="https://maps.prorail.nl/arcgis/rest/services/Spoorgeometrie/FeatureServer/11">ProRail
   * Spoorgeometrie</a> open dataset (CC BY 4.0): track {@code 823_12V_4.3},
   * {@code SIGMATRAJECT_GUID = 510603bc-fef6-4d29-9a01-2bad891057ca},
   * elements at {@code VOLGNUMMER 33–37}.
   *
   * <p>The canonical Dutch railway alignment pattern that motivates the
   * proposal — *Rechtstand → Overgangsboog → Boog → Overgangsboog →
   * Rechtstand*: a 12.18 m straight, a 48 m entry clothoid taking
   * curvature from 0 to 1/200 (R = 200 m inside the bend, CCW), a
   * 311.4 m circular arc at R = 200 m, a 42 m exit clothoid back to
   * straight, and a 290.8 m final straight. Total bend ≈ 102°.
   *
   * <p>Coordinates are in EPSG:28992 (Dutch Rijksdriehoek), so values
   * are in the (115000, 412000) range. Use TestBuilder's <em>Zoom to
   * A</em> after loading.
   */
  @Metadata(description="Real-world ProRail track 823_12V_4.3: line → CLOTHOID → R=200 arc → CLOTHOID → line (RD coords; Zoom to A)")
  public static Geometry clothoidRailBend(Geometry g) {
    String wkt =
        "COMPOUNDCURVE ("
        + "(116414.353 411964.758, 116410.740 411976.388), "
        + "CLOTHOID (0, 0.005, 48), "
        + "CIRCULARSTRING (116394.687 412021.591, 116284.527 412126.266, 116132.940 412123.450), "
        + "CLOTHOID (0.005, 0, 42), "
        + "(116095.653 412104.165, 115842.170 411961.603)"
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

  /**
   * G1-continuous chain {@code straight + R=50 arc + straight} with no
   * spirals at the two junctions. The straight at (0,0)–(100,0) is
   * tangent to the 90° CCW arc centred at (100,50), and the second
   * straight (150,50)–(150,200) is tangent at the arc's end. Used as a
   * target for the Clothoid panel's "Insert spiral before next arc"
   * action — the operation upgrades each junction from G1 to G2 by
   * inserting a properly-fitted CLOTHOID transition.
   */
  @Metadata(description="Sample COMPOUNDCURVE: line + R=50 arc + line, G1-only (target for Insert spiral action)")
  public static Geometry arcChainNoSpirals(Geometry g) {
    return readCurved(g,
        "COMPOUNDCURVE ("
        + "(0 0, 100 0), "
        + "CIRCULARSTRING (100 0, 135.355 14.645, 150 50), "
        + "(150 50, 150 200))");
  }

  // ---------------- helpers --------------------------------------

  private static Geometry readCurved(Geometry g, String wkt) {
    GeometryFactory base = (g != null) ? g.getFactory() : null;
    CurveGeometryFactory cf = (base instanceof CurveGeometryFactory)
        ? (CurveGeometryFactory) base
        : new CurveGeometryFactory();
    try {
      return new CurveWKTReader(cf).read(wkt);
    } catch (Exception ex) {
      throw new RuntimeException("CurveWKTReader failed on example: " + wkt, ex);
    }
  }
}
