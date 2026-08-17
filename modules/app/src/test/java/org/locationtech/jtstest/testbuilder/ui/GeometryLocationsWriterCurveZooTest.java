/*
 * Copyright (c) 2026 Jeroen Tech Solutions Ltd / JTS contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jtstest.testbuilder.ui;

import java.util.Collections;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.BezierCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.EllipseCurve;
import org.locationtech.jts.geom.curve.NurbsCurve;
import org.locationtech.jtstest.testbuilder.geom.GeometryLocation;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Phase 4/5 (#1195): inspect labels for WKB 18–21 greenfield types.
 */
public class GeometryLocationsWriterCurveZooTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(GeometryLocationsWriterCurveZooTest.class);
  }

  public GeometryLocationsWriterCurveZooTest(String name) {
    super(name);
  }

  public void testBezierEllipseNurbsLabels() {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    Geometry bez = gf.createBezierCurve(gf.getCoordinateSequenceFactory().create(
        new Coordinate[] {
            new Coordinate(0, 0), new Coordinate(1, 2),
            new Coordinate(3, 2), new Coordinate(4, 0)
        }));
    Geometry ell = gf.createEllipseCurve(0, 0, Double.NaN, 5, 3, 0, 0, Math.PI);
    Geometry nur = gf.createNurbsCurve(
        gf.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(0, 0), new Coordinate(1, 1), new Coordinate(2, 0)
        }),
        2,
        new double[] { 1, 1, 1 },
        new double[] { 0, 0, 0, 1, 1, 1 });

    assertTrue(bez instanceof BezierCurve);
    assertTrue(ell instanceof EllipseCurve);
    assertTrue(nur instanceof NurbsCurve);

    GeometryLocationsWriter w = new GeometryLocationsWriter();
    String sBez = labelOf(w, bez);
    String sEll = labelOf(w, ell);
    String sNur = labelOf(w, nur);
    assertTrue(sBez, sBez.indexOf("Bezier") >= 0);
    assertTrue(sEll, sEll.indexOf("Ellipse") >= 0);
    assertTrue(sNur, sNur.indexOf("NURBS") >= 0);
  }

  private static String labelOf(GeometryLocationsWriter w, Geometry g) {
    GeometryLocation loc = new GeometryLocation(g, g, new int[] { 0 }, 0, true,
        g.getCoordinate());
    return w.writeFacetLocations(Collections.singletonList(loc));
  }
}
