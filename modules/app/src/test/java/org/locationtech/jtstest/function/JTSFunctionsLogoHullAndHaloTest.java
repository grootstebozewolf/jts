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
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.Linearizable;
import org.locationtech.jts.io.curve.CurveWKTWriter;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * UX issue #90: logoLines convex hull / alphaShape(40) and clothoidHalo
 * must stay laser curve types, not densified POLYGON / LINESTRING.
 */
public class JTSFunctionsLogoHullAndHaloTest extends TestCase {

  public JTSFunctionsLogoHullAndHaloTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(JTSFunctionsLogoHullAndHaloTest.class);
  }

  public void testLogoLinesConvexHullIsCurveNotDensifiedPolygon() {
    Geometry logo = JTSFunctions.logoLines(null);
    Geometry hull = HullFunctions.convexHull(logo);
    String wkt = new CurveWKTWriter().write(hull);
    assertFalse("convexHull of logoLines must not be a densified POLYGON, got "
        + hull.getNumPoints() + " " + wkt, hull.getClass().equals(Polygon.class));
    assertTrue("must keep curve identity, got " + hull.getClass().getName()
        + " " + wkt, hull instanceof CurvePolygon);
    assertTrue("exposed S/J arcs stay CIRCULARSTRING, got " + wkt,
        wkt.indexOf("CIRCULARSTRING") >= 0);
    assertTrue("shell is a CompoundCurve of arcs and straights, got " + wkt,
        wkt.indexOf("COMPOUNDCURVE") >= 0);
  }

  public void testLogoLinesAlphaShape40IsLaserHull() {
    Geometry logo = JTSFunctions.logoLines(null);
    Geometry shape = HullFunctions.alphaShape(logo, 40.0);
    String wkt = new CurveWKTWriter().write(shape);
    assertFalse("alphaShape(40) must not flatten logoLines to a chord POLYGON, got n="
        + shape.getNumPoints() + " " + wkt,
        shape.getClass().equals(Polygon.class));
    assertTrue("got " + shape.getClass().getName() + " " + wkt,
        shape instanceof CurvePolygon || shape instanceof Linearizable);
  }

  public void testClothoidHaloIsClothoidFrameNotLineString() {
    Geometry halo = JTSFunctions.clothoidHalo(null);
    String wkt = new CurveWKTWriter().write(halo);
    assertFalse("clothoidHalo must not be LINESTRING chords, got " + wkt,
        halo.getClass().equals(LineString.class));
    assertTrue("must contain a ClothoidSegment, got " + halo.getClass().getName()
        + " " + wkt, hasClothoid(halo));
  }

  private static boolean hasClothoid(Geometry g) {
    if (g instanceof ClothoidSegment) {
      return true;
    }
    if (g instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) g;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        if (hasClothoid(cc.getMemberN(i))) {
          return true;
        }
      }
    }
    if (g instanceof CurvePolygon) {
      CurvePolygon cp = (CurvePolygon) g;
      if (hasClothoid(cp.getExteriorCurve())) {
        return true;
      }
      for (int i = 0; i < cp.getNumInteriorRing(); i++) {
        if (hasClothoid(cp.getInteriorCurveN(i))) {
          return true;
        }
      }
    }
    for (int i = 0; i < g.getNumGeometries(); i++) {
      if (g.getGeometryN(i) != g && hasClothoid(g.getGeometryN(i))) {
        return true;
      }
    }
    return false;
  }
}
