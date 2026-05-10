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
package org.locationtech.jtstest.function;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Phase-5 spike — red tests for {@link BufferFunctions#bufferCurveWithParams}
 * applied to OGC SFA / ISO 19125-2 curve geometries. Today the function
 * delegates to {@code BufferCurveSetBuilder} which only sees the
 * {@link org.locationtech.jts.geom.LineString} parent of a
 * {@link org.locationtech.jts.geom.curved.CircularString}, treating arc
 * control points as a 3-vertex polyline and producing visibly jagged
 * offsets at the arc midpoint instead of a smooth buffer curve.
 *
 * <p>Each test compares the function's vertex count against a smoothness
 * threshold that a polyline-treated 3-point arc cannot meet but a
 * properly densified arc easily exceeds.
 */
public class BufferCurveWithParamsCurvedTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(BufferCurveWithParamsCurvedTest.class); }
  public BufferCurveWithParamsCurvedTest(String name) { super(name); }

  /**
   * A half-circle CIRCULARSTRING has only 3 control points. Polyline
   * treatment of those 3 points yields ~12-vertex buffer offsets;
   * arc-correct treatment (densified to chord tolerance) yields 50+.
   */
  public void testHalfCircleBufferIsSmooth() throws Exception {
    Geometry arc = read("CIRCULARSTRING (45 45, 0 90, -45 45)");
    Geometry buffered = BufferFunctions.bufferCurveWithParams(arc, 12.0, 8, 1, 1, 5.0);
    int n = buffered.getNumPoints();
    assertTrue(
        "bufferCurveWithParams of a CIRCULARSTRING half-circle should be smooth (>= 50 verts), got " + n,
        n >= 50);
  }

  /**
   * The same arc densified to chord-tolerance ≤ 0.5 is the gold
   * standard. The arc-aware buffer should produce a result with at
   * least the densified-input vertex count, indicating the arc was
   * recognised and densified before buffering.
   */
  public void testArcBufferMatchesDensifiedReference() throws Exception {
    Geometry arc = read("CIRCULARSTRING (45 45, 0 90, -45 45)");
    Geometry densified = read("CIRCULARSTRING (45 45, 0 90, -45 45)");
    Geometry referenceLinear =
        ((org.locationtech.jts.geom.curved.Linearizable) densified).toLinear(0.5);
    Geometry referenceBuffer =
        BufferFunctions.bufferCurveWithParams(referenceLinear, 12.0, 8, 1, 1, 5.0);

    Geometry actualBuffer =
        BufferFunctions.bufferCurveWithParams(arc, 12.0, 8, 1, 1, 5.0);

    int reference = referenceBuffer.getNumPoints();
    int actual = actualBuffer.getNumPoints();
    assertTrue(
        "arc-aware buffer (" + actual + " verts) should be at least 80% of densified-reference ("
            + reference + ")",
        actual >= 0.8 * reference);
  }

  /**
   * The JTS-logo GEOMETRYCOLLECTION mixes straight LineStrings with
   * three CircularString-shaped halves of the S. The buffer should be
   * smooth on the curved parts; total vertex count >= 200 indicates
   * the arcs were densified.
   */
  public void testJtsLogoBufferIsSmooth() throws Exception {
    Geometry logo = read(
        "GEOMETRYCOLLECTION ("
        + "  LINESTRING (-38 265, 265 265),"
        + "  LINESTRING (52 265, 52 130),"
        + "  CIRCULARSTRING (52 130, 7 85, -38 130),"
        + "  LINESTRING (130 265, 130 85),"
        + "  CIRCULARSTRING (240 265, 195 220, 240 175),"
        + "  CIRCULARSTRING (240 175, 285 130, 240 85),"
        + "  LINESTRING (215 85, 240 85))");
    Geometry buffered =
        BufferFunctions.bufferCurveWithParams(logo, 12.0, 8, 1, 1, 5.0);
    int n = buffered.getNumPoints();
    assertTrue(
        "bufferCurveWithParams of the JTS logo should be smooth on the arcs (>= 200 verts), got " + n,
        n >= 200);
  }

  // ---- helpers --------------------------------------------------------------

  private static Geometry read(String wkt) throws Exception {
    return new CurvedWKTReader(new CurvedGeometryFactory()).read(wkt);
  }
}
