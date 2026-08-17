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
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * BUF-1 / BUF-NEG: open-arc buffer corridor (#1195).
 */
public class CurveBufferArcTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(CurveBufferArcTest.class);
  }

  public CurveBufferArcTest(String name) {
    super(name);
  }

  public void testSingleArcBufferIsCurvePolygon() throws Exception {
    Geometry arc = new CurveWKTReader().read(
        "CIRCULARSTRING (5 0, 0 5, -5 0)");
    Geometry buf = arc.buffer(1.0);
    assertTrue(buf instanceof CurvePolygon);
    CurvePolygon cp = (CurvePolygon) buf;
    assertTrue(cp.getExteriorCurve() instanceof CompoundCurve);
    CompoundCurve shell = (CompoundCurve) cp.getExteriorCurve();
    assertEquals(4, shell.getNumMembers());
    assertTrue(shell.getMemberN(0) instanceof CircularString);
    assertTrue(shell.getMemberN(1) instanceof CircularString);
    assertTrue(shell.getMemberN(2) instanceof CircularString);
    assertTrue(shell.getMemberN(3) instanceof CircularString);
    assertTrue(buf.getArea() > 0.0);
  }

  public void testBufNegOpenArcEmpty() throws Exception {
    Geometry arc = new CurveWKTReader().read(
        "CIRCULARSTRING (-5 0, 0 5, 5 0)");
    Geometry buf = arc.buffer(-10.0);
    assertTrue(buf.isEmpty());
  }

  public void testDiscBufferStillLaser() throws Exception {
    Geometry disc = new CurveWKTReader().read(
        "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))");
    Geometry buf = disc.buffer(1.0);
    assertTrue(buf instanceof CurvePolygon);
    assertEquals(36.0 * Math.PI, buf.getArea(), 1.0e-6);
  }

  public void testStadiumBufferPreservesCompoundCurve() throws Exception {
    Geometry stadium = new CurveWKTReader().read(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-1 -1, 0 -2, 1 -1), (1 -1, 1 6), CIRCULARSTRING (1 6, 0 7, -1 6), (-1 6, -1 -1)))");
    Geometry buf = stadium.buffer(1.0);
    assertTrue(buf instanceof CurvePolygon);
    assertTrue(((CurvePolygon) buf).getExteriorCurve() instanceof CompoundCurve);
    CompoundCurve shell = (CompoundCurve) ((CurvePolygon) buf).getExteriorCurve();
    assertEquals(4, shell.getNumMembers());
    assertTrue(shell.getMemberN(0) instanceof CircularString);
    // MIC radius was 1; after +1 buffer MIC radius is 2
    CircularArcDensifier.Circle mic = CurveExact.stadiumMic(buf);
    assertNotNull(mic);
    assertEquals(2.0, mic.r, 1.0e-9);
  }

  public void testOpenMixedLineArcCorridor() throws Exception {
    Geometry g = new CurveWKTReader().read(
        "COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0))");
    Geometry buf = g.buffer(2.0);
    assertTrue(buf instanceof CurvePolygon);
    CurvePolygon cp = (CurvePolygon) buf;
    assertTrue(cp.getExteriorCurve() instanceof CompoundCurve);
    CompoundCurve shell = (CompoundCurve) cp.getExteriorCurve();
    assertTrue(shell.getNumMembers() >= 4);
    boolean sawArc = false;
    for (int i = 0; i < shell.getNumMembers(); i++) {
      if (shell.getMemberN(i) instanceof CircularString) {
        sawArc = true;
      }
    }
    assertTrue("corridor must keep circular parallels/caps", sawArc);
    assertTrue(buf.getArea() > 0.0);
    assertFalse(buf instanceof org.locationtech.jts.geom.Polygon
        && !(buf instanceof CurvePolygon));
  }
}
