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

package org.locationtech.jtstest.testbuilder.geom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.locationtech.jts.algorithm.PointLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.Tin;
import org.locationtech.jts.geom.curve.Triangle;

public class GeometryCombiner 
{
  private GeometryFactory geomFactory;
  
  public GeometryCombiner(GeometryFactory geomFactory) {
    this.geomFactory = geomFactory;
  }

  public Geometry addPolygonRing(Geometry orig, Coordinate[] pts)
  {
    LinearRing ring = geomFactory.createLinearRing(pts);
    
    if (orig == null) {
      return geomFactory.createPolygon(ring, null);
    }
    if (! (orig instanceof Polygonal)) {
      return combine(orig, 
          geomFactory.createPolygon(ring, null));
    }
    // add the ring as either a hole or a shell
    Polygon polyContaining = findPolygonContaining(orig, pts[0]);
    if (polyContaining == null) {
      return combine(orig, geomFactory.createPolygon(ring, null));
    }
    
    // add ring as hole
    Polygon polyWithHole = addHole(polyContaining, ring);
    return replace(orig, polyContaining, polyWithHole);
  }
  
  public Geometry addLineString(Geometry orig, Coordinate[] pts)
  {
    LineString line = geomFactory.createLineString(pts);
    return combine(orig, line);
  }

  public Geometry addCircularString(Geometry orig, Coordinate[] pts)
  {
    CurveGeometryFactory cgf = (geomFactory instanceof CurveGeometryFactory)
        ? (CurveGeometryFactory) geomFactory
        : new CurveGeometryFactory(geomFactory.getPrecisionModel(), geomFactory.getSRID());
    CircularString line = cgf.createCircularString(geomFactory.getCoordinateSequenceFactory().create(pts));
    return combine(orig, line);
  }

  /**
   * Builds a Triangle (Polygon with a single closed 4-point ring,
   * no holes) from the three captured corner coordinates and combines
   * it with {@code orig}. The closing point (== first) is appended
   * automatically.
   */
  public Geometry addTriangle(Geometry orig, Coordinate[] corners)
  {
    if (corners.length < 3) {
      // Defensive: degrade to nothing rather than throw.
      return orig == null ? geomFactory.createGeometryCollection() : orig;
    }
    CurveGeometryFactory cgf = (geomFactory instanceof CurveGeometryFactory)
        ? (CurveGeometryFactory) geomFactory
        : new CurveGeometryFactory(geomFactory.getPrecisionModel(), geomFactory.getSRID());
    Coordinate[] ring = new Coordinate[] {
        corners[0], corners[1], corners[2], new Coordinate(corners[0])
    };
    LinearRing shell = geomFactory.createLinearRing(ring);
    Triangle tri = cgf.createTriangle(shell);
    return combine(orig, tri);
  }

  /**
   * Builds a Tin from {@code coords} interpreted as consecutive groups
   * of three corner coordinates (one triangular patch per triple). If
   * {@code orig} is null, the Tin is returned directly so the
   * subclass survives the "first geometry in the model" path; otherwise
   * the Tin is run through {@link #combine(Geometry, Geometry)} which
   * may degrade it to a {@link org.locationtech.jts.geom.MultiPolygon}
   * (a known phase-1 limitation tied to
   * {@link #extractElements(Geometry, boolean)} flattening collections).
   */
  public Geometry addTin(Geometry orig, Coordinate[] coords)
  {
    int n = coords.length / 3;
    if (n < 1) {
      return orig == null ? geomFactory.createGeometryCollection() : orig;
    }
    CurveGeometryFactory cgf = (geomFactory instanceof CurveGeometryFactory)
        ? (CurveGeometryFactory) geomFactory
        : new CurveGeometryFactory(geomFactory.getPrecisionModel(), geomFactory.getSRID());
    Polygon[] patches = new Polygon[n];
    for (int i = 0; i < n; i++) {
      Coordinate a = coords[3 * i];
      Coordinate b = coords[3 * i + 1];
      Coordinate c = coords[3 * i + 2];
      Coordinate[] ring = new Coordinate[] { a, b, c, new Coordinate(a) };
      LinearRing shell = geomFactory.createLinearRing(ring);
      patches[i] = cgf.createTriangle(shell);
    }
    Tin tin = cgf.createTin(patches);
    if (orig == null || orig.isEmpty()) return tin;
    return combine(orig, tin);
  }

  public Geometry addPoint(Geometry orig, Coordinate pt)
  {
    Point point = geomFactory.createPoint(pt);
    return combine(orig, point);
  }
  
  private static Polygon findPolygonContaining(Geometry geom, Coordinate pt)
  {
    PointLocator locator = new PointLocator();
    for (int i = 0; i < geom.getNumGeometries(); i++) {
      Polygon poly = (Polygon) geom.getGeometryN(i);
      int loc = locator.locate(pt, poly);
      if (loc == Location.INTERIOR)
        return poly;
    }
    return null;
  }
  
  public Polygon addHole(Polygon poly, LinearRing hole)
  {
    int nOrigHoles = poly.getNumInteriorRing();
    LinearRing[] newHoles = new LinearRing[nOrigHoles + 1];
    for (int i = 0; i < nOrigHoles; i++) {
      newHoles[i] = poly.getInteriorRingN(i);
    }
    newHoles[nOrigHoles] = hole;
    return geomFactory.createPolygon(poly.getExteriorRing(), newHoles);
  }
  
  public Geometry combine(Geometry orig, Geometry geom)
  {
    List origList = extractElements(orig, true);
    List geomList = extractElements(geom, true);
    origList.addAll(geomList);
    
    if (origList.size() == 0) {
      // return a clone of the orig geometry
      return (Geometry) orig.clone();
    }
    // return the "simplest possible" geometry
    return geomFactory.buildGeometry(origList);
  }
  
  public static List extractElements(Geometry geom, boolean skipEmpty)
  {
    List elem = new ArrayList();
    if (geom == null)
      return elem;
    
    for (int i = 0; i < geom.getNumGeometries(); i++) {
      Geometry elemGeom = geom.getGeometryN(i);
      if (skipEmpty && elemGeom.isEmpty())
        continue;
      elem.add(elemGeom);
    }
    return elem;
  }
  
  public static Geometry replace(Geometry parent, Geometry original, Geometry replacement)
  {
    List elem = extractElements(parent, false);
    Collections.replaceAll(elem, original, replacement);
    return parent.getFactory().buildGeometry(elem);
  }
}
