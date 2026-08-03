/*
 * Copyright (c) 2016 Vivid Solutions.
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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateArrays;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jtstest.geomfunction.Metadata;

public class EditFunctions {
  
  /**
   * Adds a closed line or polygon B as a hole in polygon A.
   * <p>
   * Both inputs are linearised first: {@code getExteriorRing()} on a CurvePolygon
   * is the flat control-point view, so a CURVEPOLYGON circle of radius 5 with a
   * radius-3 CURVEPOLYGON hole gave area 32 rather than {@code 25*pi - 9*pi} =
   * 50.265. Non-curve input is returned unchanged, so plain polygons behave
   * exactly as before, and the result is a plain Polygon either way since it is
   * built from LinearRings.
   * <p>
   * The input checks are unchanged and were already correct: a CircularString is
   * a LineString, and "A is not a polygon" is the right answer for it -- linearising
   * a CircularString yields a LineString, which still fails the check, as it
   * should. That refusal was reported as a bug but is not one; the genuine defect
   * was the CURVEPOLYGON case above, which passed the check and returned the
   * wrong shape.
   */
  @Metadata(description="Add a hole (closed line or polygon) to a polygon")
  public static Geometry addHole(
      Geometry polyGeom,
      Geometry hole) {
    GeometryFactory factory = polyGeom.getFactory();

    Geometry linearPoly = CurveFunctions.linearizeForOps(polyGeom);
    Geometry linearHole = CurveFunctions.linearizeForOps(hole);

    // input checks
    boolean isPolygonal = linearPoly instanceof Polygon;
    if (! isPolygonal)
      throw new IllegalArgumentException("A is not a polygon");
    if (! (linearHole instanceof Polygon || linearHole instanceof LineString))
      throw new IllegalArgumentException("B must be a polygon or line");
    Coordinate[] holePts = extractLine(linearHole);
    if (! CoordinateArrays.isRing(holePts)) {
      throw new IllegalArgumentException("B is not a valid ring");
    }

    Polygon polygon = (Polygon) linearPoly;
    LinearRing shell = (LinearRing) polygon.getExteriorRing().copy();
    
    LinearRing[] holes = new LinearRing[ polygon.getNumInteriorRing() + 1 ];
    for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
      holes[i] = (LinearRing) polygon.getInteriorRingN(i).copy();
    }
    holes[holes.length - 1] = factory.createLinearRing(holePts);
    return factory.createPolygon(shell, holes);
  }

  private static Coordinate[] extractLine(Geometry hole) {
    if (hole instanceof Polygon) {
      return ((Polygon) hole).getExteriorRing().getCoordinates();
    }
    return hole.getCoordinates();
  }
}
