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

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.JTSVersion;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jts.util.GeometricShapeFactory;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Pins TestBuilder JTS {@code logoLines} as real curve geometry: J and S
 * are {@link CompoundCurve}s whose bowls are {@link CircularString}
 * members, not 10-point {@link LineString} arcs from
 * {@link GeometricShapeFactory#createArc}.
 */
public class JTSFunctionsLogoLinesCurveTest extends TestCase {

  private static final int SHAPE_FACTORY_ARC_POINTS = 10;
  private static final double ARC_EPS = 1e-8;
  private static final double J_RADIUS = 25.0;
  private static final double S_RADIUS = 17.5;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(JTSFunctionsLogoLinesCurveTest.class); }
  public JTSFunctionsLogoLinesCurveTest(String name) { super(name); }

  public void testLogoLinesContainsCompoundCurveAndCircularString() {
    Geometry logo = JTSFunctions.logoLines(null);
    assertTrue("logoLines must be a MultiCurve so TestBuilder can paint members",
        logo instanceof MultiCurve);
    List<CompoundCurve> compounds = new ArrayList<CompoundCurve>();
    List<CircularString> arcs = new ArrayList<CircularString>();
    collectCurves(logo, compounds, arcs);
    assertTrue("logoLines must contain a CompoundCurve", !compounds.isEmpty());
    assertTrue("logoLines must contain a CircularString", !arcs.isEmpty());
  }

  public void testJSAreCompoundCurvesNotTenPointLineStringArcs() {
    Geometry logo = JTSFunctions.logoLines(null);
    List<CompoundCurve> compounds = new ArrayList<CompoundCurve>();
    List<CircularString> arcs = new ArrayList<CircularString>();
    collectCurves(logo, compounds, arcs);

    assertEquals("J (stem+hook+base) and S (straights+two bowls)", 2, compounds.size());

    CompoundCurve j = compounds.get(0);
    assertEquals("J is vertical + quarter-circle + base", 3, j.getNumMembers());
    assertFalse(j.getMemberN(0) instanceof CircularString);
    assertTrue(j.getMemberN(1) instanceof CircularString);
    assertFalse(j.getMemberN(2) instanceof CircularString);
    assertEquals(3, j.getMemberN(1).getNumPoints());
    assertEquals(J_RADIUS * Math.PI / 2.0, j.getMemberN(1).getLength(), ARC_EPS);

    CompoundCurve s = compounds.get(1);
    assertEquals("S is straights + two semicircle CircularStrings", 4, s.getNumMembers());
    assertFalse(s.getMemberN(0) instanceof CircularString);
    assertTrue(s.getMemberN(1) instanceof CircularString);
    assertTrue(s.getMemberN(2) instanceof CircularString);
    assertFalse(s.getMemberN(3) instanceof CircularString);
    assertEquals(3, s.getMemberN(1).getNumPoints());
    assertEquals(3, s.getMemberN(2).getNumPoints());
    assertEquals(S_RADIUS * Math.PI, s.getMemberN(1).getLength(), ARC_EPS);
    assertEquals(S_RADIUS * Math.PI, s.getMemberN(2).getLength(), ARC_EPS);

    for (int i = 0; i < arcs.size(); i++) {
      assertEquals("CircularString control triple, not a densified polyline",
          3, arcs.get(i).getNumPoints());
    }

    List<LineString> plain = new ArrayList<LineString>();
    collectPlainLineStrings(logo, plain);
    for (int i = 0; i < plain.size(); i++) {
      assertFalse("J/S must not be 10-point GeometricShapeFactory polylines: "
          + plain.get(i),
          plain.get(i).getNumPoints() == SHAPE_FACTORY_ARC_POINTS);
    }

    String wkt = new CurveWKTWriter().write(logo);
    assertTrue("WKT must name MULTICURVE, got " + wkt, wkt.contains("MULTICURVE"));
    assertTrue("WKT must name CIRCULARSTRING, got " + wkt, wkt.contains("CIRCULARSTRING"));
    assertTrue("WKT must name COMPOUNDCURVE, got " + wkt, wkt.contains("COMPOUNDCURVE"));
  }

  public void testShapeFactoryTenPointArcsAreNotInTheLogo() {
    Geometry logo = JTSFunctions.logoLines(null);
    GeometricShapeFactory gsf = new GeometricShapeFactory(new CurveGeometryFactory());
    gsf.setBase(new Coordinate(30 - 2 * 25, 0));
    gsf.setSize(2 * 25);
    gsf.setNumPoints(SHAPE_FACTORY_ARC_POINTS);
    LineString jArc = gsf.createArc(1.5 * Math.PI, 0.5 * Math.PI);
    assertEquals(SHAPE_FACTORY_ARC_POINTS, jArc.getNumPoints());
    assertEquals("LineString", jArc.getGeometryType());

    List<LineString> plain = new ArrayList<LineString>();
    collectPlainLineStrings(logo, plain);
    for (int i = 0; i < plain.size(); i++) {
      assertFalse("logo must not contain the J hook as a 10-point LineString",
          jArc.equalsExact(plain.get(i)));
    }
    List<CircularString> arcs = new ArrayList<CircularString>();
    collectCurves(logo, new ArrayList<CompoundCurve>(), arcs);
    assertTrue("the J hook is a CircularString, not the ShapeFactory polyline",
        !arcs.isEmpty());
  }

  public void testJtsVersionUnchanged() {
    assertEquals(JTSVersion.CURRENT_VERSION.toString(), JTSFunctions.jtsVersion(null));
  }

  private static void collectCurves(Geometry g, List<CompoundCurve> compounds,
      List<CircularString> arcs)
  {
    if (g instanceof CircularString) {
      arcs.add((CircularString) g);
      return;
    }
    if (g instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) g;
      compounds.add(cc);
      LineString[] members = cc.getMembers();
      for (int i = 0; i < members.length; i++) {
        collectCurves(members[i], compounds, arcs);
      }
      return;
    }
    if (g instanceof GeometryCollection) {
      for (int i = 0; i < g.getNumGeometries(); i++) {
        collectCurves(g.getGeometryN(i), compounds, arcs);
      }
    }
  }

  private static void collectPlainLineStrings(Geometry g, List<LineString> plain)
  {
    if (g instanceof CircularString || g instanceof CompoundCurve) {
      if (g instanceof CompoundCurve) {
        LineString[] members = ((CompoundCurve) g).getMembers();
        for (int i = 0; i < members.length; i++) {
          collectPlainLineStrings(members[i], plain);
        }
      }
      return;
    }
    if (g.getClass().equals(LineString.class)) {
      plain.add((LineString) g);
      return;
    }
    if (g instanceof GeometryCollection) {
      for (int i = 0; i < g.getNumGeometries(); i++) {
        collectPlainLineStrings(g.getGeometryN(i), plain);
      }
    }
  }
}
