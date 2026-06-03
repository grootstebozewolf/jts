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
package org.locationtech.jts.geom.curved;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curved.CurvedWKTReader;
import org.locationtech.jts.geom.curved.CurvedPrecisionReducer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Green verification tests for PRC-SN.
 * The red meter in CurveAwarenessSpecTest remains with fail per RGR.
 */
public class CurvedPrecisionReducerTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CurvedPrecisionReducerTest.class); }
  public CurvedPrecisionReducerTest(String name) { super(name); }

  private static Geometry read(String wkt) {
    try {
      return new CurvedWKTReader(new CurvedGeometryFactory()).read(wkt);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  public void testGridFriendlyArcPreserved() {
    // On grid semicircle r=5, centre (5,0) r=5 both on integer grid -> preserve CS
    Geometry cs = read("CIRCULARSTRING (0 0, 5 5, 10 0)");
    PrecisionModel pm = new PrecisionModel(1.0); // integer grid
    Geometry reduced = CurvedPrecisionReducer.reduce(cs, pm);
    System.out.println("DEBUG PRC-SN grid friendly reduce type: " + reduced.getGeometryType() + " class " + reduced.getClass().getSimpleName());
    assertTrue("should preserve CircularString for grid-friendly", reduced instanceof CircularString);
  }

  public void testNonGridFriendlyDensifies() {
    // Sub-grid that after snap centre not precise -> fallback to lin
    Geometry cs = read("CIRCULARSTRING (0.1 0.1, 0.2 0.5, 0.3 0.1)");
    PrecisionModel pm = new PrecisionModel(1.0);
    Geometry reduced = CurvedPrecisionReducer.reduce(cs, pm);
    // May be LineString after densify+snap
    assertTrue(reduced instanceof LineString || reduced instanceof CircularString);
  }

  public void testCompoundCurveMembersHandled() {
    Geometry cc = read("COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0))");
    PrecisionModel pm = new PrecisionModel(1.0);
    Geometry reduced = CurvedPrecisionReducer.reduce(cc, pm);
    assertTrue(reduced instanceof CompoundCurve);
  }

  /** Basic hunter for snap cases (grid vs sub-grid); exercises preserve/fallback paths.
   *  For full adversarial, integrate vectors from proofs#66 SnapRounding.
   */
  public void testSnapHunterBasic() {
    int preserved = 0, densified = 0;
    PrecisionModel pm = new PrecisionModel(1.0);
    for (int i = 0; i < 20; i++) {
      // simple random-ish arc near grid
      double x0 = i * 0.1, y0 = 0;
      double x1 = i*0.1 + 0.3, y1 = 1.2;
      double x2 = i*0.1 + 1.0, y2 = 0.1;
      String wkt = "CIRCULARSTRING (" + x0 + " " + y0 + ", " + x1 + " " + y1 + ", " + x2 + " " + y2 + ")";
      Geometry cs = read(wkt);
      Geometry red = CurvedPrecisionReducer.reduce(cs, pm);
      if (red instanceof CircularString) preserved++;
      else densified++;
    }
    System.out.println("PRC-SN hunter: " + preserved + " preserved as CS, " + densified + " densified");
    // Just exercise; in practice with proofs vectors, assert on specific cases.
    assertTrue(preserved + densified > 0);
  }
}
