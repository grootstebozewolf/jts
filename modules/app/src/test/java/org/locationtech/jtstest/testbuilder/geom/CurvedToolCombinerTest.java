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
package org.locationtech.jtstest.testbuilder.geom;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.CompoundCurve;
import org.locationtech.jts.geom.curved.CurvePolygon;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jtstest.testbuilder.model.GeometryType;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * TB-T (#1195): unit tests for the model-layer methods that back the
 * {@link CompoundCurveTool} and {@link CurvePolygonTool} drawing tools
 * (the {@link GeometryCombiner} factory methods and {@link GeometryType}
 * constants). No GUI is started.
 */
public class CurvedToolCombinerTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(CurvedToolCombinerTest.class); }
  public CurvedToolCombinerTest(String name) { super(name); }

  private final CurvedGeometryFactory GF = new CurvedGeometryFactory();
  private final GeometryCombiner combiner = new GeometryCombiner(GF);

  // ---- CompoundCurve tool ---------------------------------------------------

  public void testAddCompoundCurveReturnsCompoundCurve() {
    Coordinate[] pts = { new Coordinate(0,0), new Coordinate(1,1), new Coordinate(2,0) };
    Geometry result = combiner.addCompoundCurve(null, pts);
    assertTrue("result should be CompoundCurve", result instanceof CompoundCurve);
  }

  public void testAddCompoundCurvePreservesControlPoints() {
    Coordinate[] pts = {
        new Coordinate(0,0), new Coordinate(1,1), new Coordinate(2,0),
        new Coordinate(3,1), new Coordinate(4,0)
    };
    Geometry result = combiner.addCompoundCurve(null, pts);
    assertEquals(5, result.getNumPoints());
  }

  public void testAddCompoundCurveCombinesWithExisting() {
    Coordinate[] pts = { new Coordinate(0,0), new Coordinate(1,1), new Coordinate(2,0) };
    Geometry first = combiner.addCompoundCurve(null, pts);
    Geometry second = combiner.addCompoundCurve(first, pts);
    assertFalse("combined result should not be empty", second.isEmpty());
  }

  // ---- CurvePolygon tool ---------------------------------------------------

  public void testAddCurvePolygonReturnsCurvePolygon() {
    // disk(0,0,5): 4 control points (ring closed by addCurvePolygon)
    Coordinate[] pts = {
        new Coordinate(5,0), new Coordinate(0,5), new Coordinate(-5,0),
        new Coordinate(0,-5)
    };
    Geometry result = combiner.addCurvePolygon(null, pts);
    assertTrue("result should be CurvePolygon", result instanceof CurvePolygon);
  }

  public void testAddCurvePolygonClosesRingAutomatically() {
    // Supply an open ring (first != last); addCurvePolygon must close it
    Coordinate[] pts = {
        new Coordinate(5,0), new Coordinate(0,5), new Coordinate(-5,0),
        new Coordinate(0,-5)
    };
    Geometry result = combiner.addCurvePolygon(null, pts);
    CurvePolygon cp = (CurvePolygon) result;
    LineString shell = cp.getExteriorCurve();
    assertNotNull("exterior curve should not be null", shell);
    Coordinate first = shell.getCoordinateN(0);
    Coordinate last  = shell.getCoordinateN(shell.getNumPoints() - 1);
    assertEquals("ring should be closed: first.x", first.x, last.x, 1e-10);
    assertEquals("ring should be closed: first.y", first.y, last.y, 1e-10);
  }

  public void testAddCurvePolygonShellIsCircularString() {
    Coordinate[] pts = {
        new Coordinate(5,0), new Coordinate(0,5), new Coordinate(-5,0),
        new Coordinate(0,-5)
    };
    Geometry result = combiner.addCurvePolygon(null, pts);
    CurvePolygon cp = (CurvePolygon) result;
    assertTrue("shell should be CircularString", cp.getExteriorCurve() instanceof CircularString);
  }

  public void testAddCurvePolygonAlreadyClosedRingNotDoubled() {
    // Supply a ring already closed
    Coordinate[] pts = {
        new Coordinate(5,0), new Coordinate(0,5), new Coordinate(-5,0),
        new Coordinate(0,-5), new Coordinate(5,0)
    };
    Geometry result = combiner.addCurvePolygon(null, pts);
    CurvePolygon cp = (CurvePolygon) result;
    LineString shell = cp.getExteriorCurve();
    assertEquals("closed ring should not duplicate closing point", 5, shell.getNumPoints());
  }

  // ---- GeometryType constants -----------------------------------------------

  public void testGeometryTypeConstantsAreDistinct() {
    int[] types = {
        GeometryType.GEOMETRYCOLLECTION, GeometryType.MULTIPOLYGON, GeometryType.MULTILINESTRING,
        GeometryType.MULTIPOINT, GeometryType.POLYGON, GeometryType.LINESTRING,
        GeometryType.POINT, GeometryType.CIRCULARSTRING, GeometryType.TRIANGLE,
        GeometryType.TIN, GeometryType.COMPOUNDCURVE, GeometryType.CURVEPOLYGON
    };
    java.util.Set<Integer> seen = new java.util.HashSet<Integer>();
    for (int t : types) {
      assertTrue("duplicate GeometryType value: " + t, seen.add(t));
    }
  }
}
