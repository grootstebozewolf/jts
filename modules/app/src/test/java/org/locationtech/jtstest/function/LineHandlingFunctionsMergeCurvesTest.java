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
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.CompoundCurve;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Tests for {@link LineHandlingFunctions#mergeCurves}: arc-aware
 * endpoint join over a collection of LineString / CircularString /
 * CompoundCurve members.
 */
public class LineHandlingFunctionsMergeCurvesTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(LineHandlingFunctionsMergeCurvesTest.class); }
  public LineHandlingFunctionsMergeCurvesTest(String name) { super(name); }

  // ---- single-type chains ---------------------------------------

  public void testTwoLinesShareEndpointJoinIntoOneLineString() throws Exception {
    Geometry g = read("GEOMETRYCOLLECTION (LINESTRING (0 0, 10 0), LINESTRING (10 0, 20 0))");
    Geometry merged = LineHandlingFunctions.mergeCurves(g);
    assertEquals("LineString", merged.getGeometryType());
    assertEquals(3, merged.getNumPoints());
  }

  public void testTwoArcsShareEndpointJoinIntoOneCircularString() throws Exception {
    Geometry g = read(
        "GEOMETRYCOLLECTION ("
        + "  CIRCULARSTRING (0 0, 5 5, 10 0),"
        + "  CIRCULARSTRING (10 0, 15 -5, 20 0)"
        + ")");
    Geometry merged = LineHandlingFunctions.mergeCurves(g);
    assertEquals("CircularString", merged.getGeometryType());
    assertEquals(5, merged.getNumPoints());
  }

  // ---- mixed chain → CompoundCurve --------------------------------

  public void testLineThenArcJoinIntoCompoundCurve() throws Exception {
    Geometry g = read(
        "GEOMETRYCOLLECTION ("
        + "  LINESTRING (0 0, 10 0),"
        + "  CIRCULARSTRING (10 0, 15 5, 20 0)"
        + ")");
    Geometry merged = LineHandlingFunctions.mergeCurves(g);
    assertTrue("expected CompoundCurve, got " + merged.getGeometryType(),
        merged instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) merged;
    assertEquals(2, cc.getNumMembers());
    assertFalse(cc.getMemberN(0) instanceof CircularString);
    assertTrue(cc.getMemberN(1) instanceof CircularString);
  }

  // ---- reversal needed --------------------------------------------

  public void testTailToTailMatchReversesSecondMember() throws Exception {
    // Both segments end at (10 0); second must be reversed before joining.
    Geometry g = read(
        "GEOMETRYCOLLECTION ("
        + "  LINESTRING (0 0, 10 0),"
        + "  LINESTRING (20 0, 10 0)"
        + ")");
    Geometry merged = LineHandlingFunctions.mergeCurves(g);
    assertEquals("LineString", merged.getGeometryType());
    assertEquals(3, merged.getNumPoints());
    LineString ls = (LineString) merged;
    assertEquals(0.0,  ls.getCoordinateN(0).x, 0.0);
    assertEquals(20.0, ls.getCoordinateN(2).x, 0.0);
  }

  // ---- disconnected members stay separate -------------------------

  public void testDisconnectedMembersPassThroughAsCollection() throws Exception {
    Geometry g = read(
        "GEOMETRYCOLLECTION ("
        + "  LINESTRING (0 0, 10 0),"
        + "  LINESTRING (50 0, 60 0)"
        + ")");
    Geometry merged = LineHandlingFunctions.mergeCurves(g);
    assertTrue(merged instanceof GeometryCollection);
    assertEquals(2, merged.getNumGeometries());
  }

  // ---- non-linear members pass through ----------------------------

  public void testNonLinearMembersPreserved() throws Exception {
    Geometry g = read(
        "GEOMETRYCOLLECTION ("
        + "  LINESTRING (0 0, 10 0),"
        + "  LINESTRING (10 0, 20 0),"
        + "  POINT (50 50)"
        + ")");
    Geometry merged = LineHandlingFunctions.mergeCurves(g);
    assertTrue(merged instanceof GeometryCollection);
    assertEquals(2, merged.getNumGeometries());
    boolean sawLine = false, sawPoint = false;
    for (int i = 0; i < merged.getNumGeometries(); i++) {
      String t = merged.getGeometryN(i).getGeometryType();
      if (t.equals("LineString")) sawLine = true;
      if (t.equals("Point")) sawPoint = true;
    }
    assertTrue(sawLine);
    assertTrue(sawPoint);
  }

  // ---- idempotence over CompoundCurve input -----------------------

  public void testCompoundCurveInputExpandsAndRejoins() throws Exception {
    // pre-built CompoundCurve in a collection alongside a separate matching member
    Geometry g = read(
        "GEOMETRYCOLLECTION ("
        + "  COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0)),"
        + "  LINESTRING (20 0, 30 0)"
        + ")");
    Geometry merged = LineHandlingFunctions.mergeCurves(g);
    assertTrue(merged instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) merged;
    // expanded then re-joined: 3 members
    assertEquals(3, cc.getNumMembers());
    assertFalse(cc.getMemberN(0) instanceof CircularString);
    assertTrue(cc.getMemberN(1)  instanceof CircularString);
    assertFalse(cc.getMemberN(2) instanceof CircularString);
  }

  // ---- the JTS-logo case ------------------------------------------

  public void testJtsLogoEndpointJoin() throws Exception {
    Geometry g = read(
        "GEOMETRYCOLLECTION ("
        + "  LINESTRING (-38 265, 265 265),"                // top bar
        + "  LINESTRING (52 265, 52 130),"                  // T1 vertical
        + "  CIRCULARSTRING (52 130, 7 85, -38 130),"       // J hook
        + "  LINESTRING (130 265, 130 85),"                 // T2 vertical
        + "  CIRCULARSTRING (240 265, 195 220, 240 175),"   // S top
        + "  CIRCULARSTRING (240 175, 285 130, 240 85),"    // S bottom
        + "  LINESTRING (215 85, 240 85)"                   // S serif
        + ")");
    Geometry merged = LineHandlingFunctions.mergeCurves(g);
    assertTrue(merged instanceof GeometryCollection);
    // 4 chains expected:
    //   - top bar (alone)
    //   - T1 + J hook (CompoundCurve)
    //   - T2 vertical (alone)
    //   - S top + S bottom + serif (3 arcs together would be a single CircularString,
    //     but mixed with LineString serif → CompoundCurve)
    assertEquals(4, merged.getNumGeometries());
    int compoundCurves = 0;
    for (int i = 0; i < merged.getNumGeometries(); i++) {
      if (merged.getGeometryN(i) instanceof CompoundCurve) compoundCurves++;
    }
    assertEquals("expected exactly two mixed-chain CompoundCurves (J chain, S chain)",
        2, compoundCurves);
  }

  // ---- helpers -----------------------------------------------------

  private static Geometry read(String wkt) throws Exception {
    return new CurvedWKTReader(new CurvedGeometryFactory()).read(wkt);
  }
}
