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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.GeometryFilter;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;


/**
 * Implementations for various geometry functions.
 * 
 * @author Martin Davis
 * 
 */
public class GeometryFunctions 
{
	public static String lengthDescription = "Computes the length of perimeter of a Geometry";
	public static double length(Geometry g)				{		return g.getLength();	}
  public static double area(Geometry g)         {   return g.getArea(); }
  public static double SRID(Geometry g)         {   return g.getSRID(); }
  
  public static boolean isEmpty(Geometry g)    {   return g.isEmpty();  }
  public static boolean isSimple(Geometry g)    {   return g.isSimple();  }
	public static boolean isValid(Geometry g)			{		return g.isValid();	}
	public static boolean isRectangle(Geometry g)	{		return g.isRectangle();	}
	public static boolean isClosed(Geometry g)	{
		if (g instanceof LineString) return ((LineString) g).isClosed();
		if (g instanceof MultiLineString) return ((MultiLineString) g).isClosed();
		// other geometry types are defined to be closed
		return true;	
		}
	
  public static Geometry copy(Geometry g)       { return g.copy(); }
  public static Geometry envelope(Geometry g) 	{ return g.getEnvelope();  }
  public static Geometry reverse(Geometry g)    { return g.reverse();  }
  public static Geometry normalize(Geometry g) 
  {      
  	Geometry gNorm = g.copy();
  	gNorm.normalize();
    return gNorm;
  }

	public static Geometry getGeometryN(Geometry g, int i)
	{
		return g.getGeometryN(i);
	}

  public static Geometry getPolygonShell(Geometry g)
  {
    if (g instanceof Polygon) {
      LinearRing shell = ((Polygon) g).getExteriorRing();
      return g.getFactory().createPolygon(shell, null);
    }
    if (g instanceof MultiPolygon) {
      Polygon[] poly = new Polygon[g.getNumGeometries()];
      for (int i = 0; i < g.getNumGeometries(); i++) {
        LinearRing shell = ((Polygon) g.getGeometryN(i)).getExteriorRing();
        poly[i] = g.getFactory().createPolygon(shell, null);
      }
      return g.getFactory().createMultiPolygon(poly);
    }
    return null;
  }

  public static Geometry getPolygonHoles(Geometry geom)
  {
    final List holePolys = new ArrayList();
    geom.apply(new GeometryFilter() {

      public void filter(Geometry geom) {
        if (geom instanceof Polygon) {
          Polygon poly = (Polygon) geom;
          for (int i = 0; i < poly.getNumInteriorRing(); i++) {
            Polygon hole = geom.getFactory().createPolygon(poly.getInteriorRingN(i), null);
            holePolys.add(hole);
          }
        }
      }      
    });
    return geom.getFactory().buildGeometry(holePolys);
  }

	public static Geometry getPolygonHoleN(Geometry g, int i)
	{
		if (g instanceof Polygon) {
			LinearRing ring = ((Polygon) g).getInteriorRingN(i);
			return ring;
		}
		return null;
	}

	public static Geometry getCoordinates(Geometry g)
	{
		Coordinate[] pts = g.getCoordinates();
		return g.getFactory().createMultiPointFromCoords(pts);
	}
	
	/**
	 * Adds the rings of B as holes in A.
	 * <p>
	 * Both inputs are linearised first, because {@code getExteriorRing()} on a
	 * CurvePolygon is the flat control-point view: a circle of radius 5 came
	 * through as its inscribed square, so a CURVEPOLYGON shell with a CURVEPOLYGON
	 * hole gave area 32 instead of {@code 25*pi - 9*pi} = 50.265. Non-curve input
	 * is returned unchanged by {@code linearizeForOps}, so plain polygons are
	 * unaffected. The result is necessarily a plain Polygon -- it is assembled
	 * from LinearRings -- so curve identity cannot be preserved here at any
	 * tolerance, only the shape.
	 * <p>
	 * The input checks replace two unguarded casts that leaked a raw
	 * ClassCastException, reported for a CircularString A but equally true of a
	 * plain LineString: this function needs polygonal input and the failure had
	 * nothing to do with curves. The messages match
	 * {@code EditFunctions.addHole}, which already guarded properly, so the two
	 * refuse the same input the same way.
	 */
	public static Geometry addHoles(Geometry g, Geometry holeGeom) {
	  //TODO: support adding to MultiPolygon
	  Geometry linear = CurveFunctions.linearizeForOps(g);
	  if (! (linear instanceof Polygon))
	    throw new IllegalArgumentException("A is not a polygon");
	  Geometry linearHoles = CurveFunctions.linearizeForOps(holeGeom);

	  Polygon poly = (Polygon) linear;
	  LinearRing shell = poly.getExteriorRing();
	  List<LinearRing> holes = new ArrayList<LinearRing>();

    for (int i = 0; i < poly.getNumInteriorRing(); i++) {
      holes.add(poly.getInteriorRingN(i));
    }
    for (int i = 0; i < linearHoles.getNumGeometries(); i++) {
      Geometry member = linearHoles.getGeometryN(i);
      if (! (member instanceof Polygon))
        throw new IllegalArgumentException("B must be polygonal");
      holes.add(((Polygon) member).getExteriorRing());
    }

    return g.getFactory().createPolygon(shell, GeometryFactory.toLinearRingArray(holes));
	}
}
