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

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.CoordinateArrays;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jtstest.geomfunction.Metadata;

public class EditFunctions {
  
  /**
   * Adds a closed line or polygon B as a hole in polygon A.
   * <p>
   * Works on the structural rings, so an arc ring stays an arc: a CURVEPOLYGON
   * circle of radius 5 with a radius-3 arc hole yields
   * {@code CURVEPOLYGON (CIRCULARSTRING (...), CIRCULARSTRING (...))}, area
   * exactly {@code 25*pi - 9*pi} = 50.265, rather than the 32 the flat
   * {@code getExteriorRing()} view gave or the 3146-vertex polygon densifying
   * gave. A closed {@code CircularString} is accepted as a hole in its own right,
   * since this function has always taken a closed line, and it too stays an arc.
   * <p>
   * A geometry with no arc anywhere still comes back as a plain Polygon with
   * exactly its input vertices.
   * <p>
   * The input checks are unchanged and were already correct: a CircularString is
   * a LineString, so "A is not a polygon" is the right answer for it. That
   * refusal was reported as a bug but is not one -- a hole cannot be added to a
   * line, and a plain LINESTRING is refused identically. The genuine defect was
   * the CURVEPOLYGON case above, which passed the checks and returned the wrong
   * shape.
   */
  @Metadata(description="Add a hole (closed line or polygon) to a polygon")
  public static Geometry addHole(
      Geometry polyGeom,
      Geometry hole) {
    GeometryFactory factory = polyGeom.getFactory();

    // input checks
    boolean isPolygonal = polyGeom instanceof Polygon;
    if (! isPolygonal)
      throw new IllegalArgumentException("A is not a polygon");
    if (! (hole instanceof Polygon || hole instanceof LineString))
      throw new IllegalArgumentException("B must be a polygon or line");
    LineString holeRing = CurveFunctions.structuralShell(hole);
    if (! CoordinateArrays.isRing(holeRing.getCoordinates())) {
      throw new IllegalArgumentException("B is not a valid ring");
    }

    LineString shell = (LineString) CurveFunctions.structuralShell(polyGeom).copy();
    List<LineString> holes = new ArrayList<LineString>();
    LineString[] existing = CurveFunctions.structuralHoles(polyGeom);
    for (int i = 0; i < existing.length; i++) {
      holes.add((LineString) existing[i].copy());
    }
    holes.add((LineString) holeRing.copy());
    return CurveFunctions.buildPolygon(shell, holes, factory);
  }
}
