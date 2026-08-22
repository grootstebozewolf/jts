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
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;


/**
 * Locates the elements of a Geometry
 * which lie in a target area.
 * 
 * @author Martin Davis
 * @see FacetLocater
 */
public class GeometryElementLocater {

  public static Geometry extractElements(Geometry parentGeom, Geometry aoi)
  {
    GeometryElementLocater locater = new GeometryElementLocater(parentGeom);
    List locs = locater.getElements(aoi);
    List geoms = extractLocationGeometry(locs);
    if (geoms.size() <= 0)
      return null;
    if (geoms.size() == 1) 
      return (Geometry) geoms.get(0);
    // if parent was a GC, ensure returning a GC
    if (parentGeom.getGeometryType().equals("GeometryCollection"))
      return parentGeom.getFactory().createGeometryCollection(GeometryFactory.toGeometryArray(geoms));
    // otherwise return MultiGeom
    return parentGeom.getFactory().buildGeometry(geoms);
  }
  
  private static List extractLocationGeometry(List locs)
  {
    List geoms = new ArrayList();
    for (Iterator i = locs.iterator(); i.hasNext();) {
      GeometryLocation loc = (GeometryLocation) i.next();
      geoms.add(loc.getElement());
    }
    return geoms;
  }
  
  public static List<GeometryLocation> getElements(Geometry parentGeom, Coordinate queryPt, double tolerance) {
    GeometryElementLocater locater = new GeometryElementLocater(parentGeom);
    return locater.getElements(queryPt, tolerance);
  }

  
  private Geometry parentGeom;
  private List<GeometryLocation> elements = new ArrayList();
  private Geometry aoi;

  public GeometryElementLocater(Geometry parentGeom) {
    this.parentGeom = parentGeom;
  }
  
  /**
   * 
   * @param queryPt
   * @param tolerance
   * @return a List of the element Geometrys
   */
  public List<GeometryLocation> getElements(Coordinate queryPt, double tolerance)
  {
    //Coordinate queryPt = queryPt;
    //this.tolerance = tolerance;
    aoi = createAOI(queryPt, tolerance);
    return getElements(aoi);
  }

  public List<GeometryLocation> getElements(Geometry aoi)
  {
    //Coordinate queryPt = queryPt;
    //this.tolerance = tolerance;
    this.aoi = aoi;
    findElements(new Stack(), parentGeom, elements);
    return elements;
  }

  private Geometry createAOI(Coordinate queryPt, double tolerance)
  {
    Envelope env = new Envelope(queryPt);
    env.expandBy(2 * tolerance);
    return parentGeom.getFactory().toGeometry(env);
  }
  
  private void findElements(Stack path, Geometry geom, List elements)
  {
    if (geom instanceof GeometryCollection) {
      for (int i = 0; i < geom.getNumGeometries(); i++ ) {
        Geometry subGeom = geom.getGeometryN(i);
  			path.push(i);
        findElements(path, subGeom, elements);
        path.pop();
      }
      return;
    }
    if (geom instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) geom;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        path.push(i);
        findElements(path, cc.getMemberN(i), elements);
        path.pop();
      }
      return;
    }
    if (geom instanceof CurvePolygon) {
      CurvePolygon cp = (CurvePolygon) geom;
      LineString shell = cp.getExteriorCurve();
      if (shell != null) {
        path.push(0);
        findElements(path, shell, elements);
        path.pop();
      }
      for (int i = 0; i < cp.getNumInteriorRing(); i++) {
        LineString hole = cp.getInteriorCurveN(i);
        if (hole != null) {
          path.push(i + 1);
          findElements(path, hole, elements);
          path.pop();
        }
      }
      return;
    }
    // TODO: make this robust - do not use Geometry.intersects()
    // atomic element - check for match
    if (aoi.intersects(geom))
      elements.add(new GeometryLocation(parentGeom, geom, 
      		FacetLocater.toIntArray(path)));
  }

}
