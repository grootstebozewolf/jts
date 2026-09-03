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
package org.locationtech.jtstest.testbuilder.ui.tools;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jtstest.testbuilder.geom.GeometryComponentTransformer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * CRV-CC ticket 30: MoveTool whole-geom translate must keep
 * COMPOUNDCURVE members (ISO/IEC 13249-3). Same apply as
 * {@link MoveTool} / {@link CircularStringMoveToolBasicsTest}.
 */
public class CompoundCurveMoveToolBasicsTest extends TestCase {

  private static final String INPUT =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 20 0))";
  private static final double DX = 10.0;
  private static final double DY = 8.0;
  private static final double EPS = 1.0e-12;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() {
    return new TestSuite(CompoundCurveMoveToolBasicsTest.class);
  }
  public CompoundCurveMoveToolBasicsTest(String name) { super(name); }

  private static Geometry read(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static Geometry moveToolTranslate(Geometry g, double dx, double dy) {
    AffineTransformation trans = AffineTransformation.translationInstance(dx, dy);
    return GeometryComponentTransformer.transform(g, trans);
  }

  public void testMoveToolTranslateKeepsCompoundCurveMembers() throws Exception {
    Geometry drawn = read(INPUT);
    assertTrue(drawn instanceof CompoundCurve);
    Geometry moved = moveToolTranslate(drawn, DX, DY);

    assertTrue("MoveTool must keep CompoundCurve, got " + moved.getClass().getName(),
        moved instanceof CompoundCurve);
    assertFalse(moved.getClass().equals(LineString.class));
    CompoundCurve cc = (CompoundCurve) moved;
    assertEquals(2, cc.getNumMembers());
    assertTrue(cc.getMemberN(0) instanceof CircularString);
    assertFalse(cc.getMemberN(1) instanceof CircularString);

    String wkt = new CurveWKTWriter().write(moved);
    assertTrue("after MoveTool WKT must stay COMPOUNDCURVE, got " + wkt,
        wkt.startsWith("COMPOUNDCURVE"));
    assertFalse(wkt.startsWith("LINESTRING"));

    Coordinate[] from = drawn.getCoordinates();
    Coordinate[] to = moved.getCoordinates();
    assertEquals(from.length, to.length);
    for (int i = 0; i < from.length; i++) {
      assertEquals(from[i].x + DX, to[i].x, EPS);
      assertEquals(from[i].y + DY, to[i].y, EPS);
    }
  }

  public void testMoveToolTranslateKeepsArcInterior() throws Exception {
    Geometry drawn = read(INPUT);
    Geometry moved = moveToolTranslate(drawn, DX, DY);
    CompoundCurve cc = (CompoundCurve) moved;
    Coordinate[] c = cc.getMemberN(0).getCoordinates();
    double chordMidX = (c[0].x + c[2].x) / 2.0;
    double chordMidY = (c[0].y + c[2].y) / 2.0;
    double bulge = Math.hypot(c[1].x - chordMidX, c[1].y - chordMidY);
    assertTrue("arc mid must stay off the endpoint chord after MoveTool (bulge="
        + bulge + ")", bulge > 1.0);
  }
}
