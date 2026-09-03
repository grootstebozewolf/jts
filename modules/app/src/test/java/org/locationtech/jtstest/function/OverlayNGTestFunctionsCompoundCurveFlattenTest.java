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
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * CRV-CC ticket 30: TestBuilder OverlayNG inspect must not eat a
 * COMPOUNDCURVE as control-polygon edges. SQL/IEC 13249-3 WKB 9.
 * CircularString CurvePolygon inspect stays raw (sibling leftover).
 */
public class OverlayNGTestFunctionsCompoundCurveFlattenTest extends TestCase {

  private static final String COMPOUND =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 20 0))";
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String CIRCLE =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() {
    return new TestSuite(OverlayNGTestFunctionsCompoundCurveFlattenTest.class);
  }
  public OverlayNGTestFunctionsCompoundCurveFlattenTest(String name) {
    super(name);
  }

  private static Geometry read(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static void assertRefused(String site, Runnable r) {
    try {
      r.run();
      fail(site + " must refuse COMPOUNDCURVE control-chord inspect");
    }
    catch (IllegalArgumentException e) {
      assertTrue(site + " message: " + e.getMessage(),
          e.getMessage().indexOf("ISO/IEC 13249-3") >= 0);
    }
  }

  public void testEdgesNodedRefusesLinealCompoundCurve() throws Exception {
    final Geometry cc = read(COMPOUND);
    final Geometry line = read("LINESTRING (0 1, 20 1)");
    assertTrue(cc instanceof CompoundCurve);
    assertRefused("OverlayNGTest.edgesNoded", new Runnable() {
      public void run() {
        OverlayNGTestFunctions.edgesNoded(cc, line, 10.0);
      }
    });
  }

  public void testEdgesNodedRefusesCompoundCurveShell() throws Exception {
    final Geometry half = read(HALF_DISC);
    final Geometry square = read("POLYGON ((-6 2, 6 2, 6 10, -6 10, -6 2))");
    assertRefused("OverlayNGTest.edgesNoded CC shell", new Runnable() {
      public void run() {
        OverlayNGTestFunctions.edgesNoded(half, square, 10.0);
      }
    });
  }

  /**
   * Sibling leftover: CircularString CurvePolygon inspect stays raw.
   * Do not steal that cell into this CompoundCurve refuse.
   */
  public void testCircularStringCurvePolygonInspectStaysRaw() throws Exception {
    Geometry a = read(CIRCLE);
    Geometry b = read(
        "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))");
    Geometry edges = OverlayNGTestFunctions.edgesNoded(a, b, 10.0);
    assertEquals("CircularString CurvePolygon inspect stays control points",
        20, edges.getNumPoints());
  }

  public void testNamedOverlayNGFunctionsPathStillRuns() throws Exception {
    Geometry cc = read(COMPOUND);
    Geometry line = read("LINESTRING (0 1, 20 1)");
    Geometry r = OverlayNGFunctions.intersection(cc, line);
    assertNotNull(r);
    assertFalse("named toLinear path must not return the CompoundCurve itself",
        r instanceof CompoundCurve);
  }
}
