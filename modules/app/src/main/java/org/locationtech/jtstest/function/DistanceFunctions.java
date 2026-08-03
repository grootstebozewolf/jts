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

import org.locationtech.jts.algorithm.distance.DiscreteFrechetDistance;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.algorithm.distance.DirectedHausdorffDistance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;
import org.locationtech.jtstest.geomfunction.Metadata;

public class DistanceFunctions {

  /**
   * Linearises curve operands so the discrete distance algorithms sample points
   * on the arc instead of its control polyline.
   * <p>
   * Everything below distance/isWithinDistance calls a core static
   * (DiscreteHausdorffDistance, DirectedHausdorffDistance,
   * DiscreteFrechetDistance, DistanceOp, IndexedFacetDistance) that reads
   * coordinates, so a curve was measured by its chords: the directed Hausdorff
   * of an arc with apex 3.968 over its chord baseline came out 3.0, and the
   * Frechet came out 3.606 -- BELOW the directed Hausdorff it mathematically
   * dominates. The densify fraction on orientedDistanceLine cannot help,
   * because it subdivides the chords, which lie inside the arc.
   * <p>
   * distance and isWithinDistance are deliberately not routed through this:
   * they delegate to instance methods the curve types already override
   * (CRV-OPS). Non-curve input is returned as the same object.
   */
  private static Geometry arc(Geometry g) {
    return CurveFunctions.linearizeForOps(g);
  }

  public static double distance(Geometry a, Geometry b) {
    return a.distance(b);
  }

  public static boolean isWithinDistance(Geometry a, Geometry b, double dist) {
    return a.isWithinDistance(b, dist);
  }

  public static Geometry nearestPoints(Geometry a, Geometry b) {
    Coordinate[] pts = DistanceOp.nearestPoints(arc(a), arc(b));
    return a.getFactory().createLineString(pts);
  }

  public static double frechetDistance(Geometry a, Geometry b)  
  {   
    // Quadratic DP: coarser sampling, or a curve pair runs for 20 seconds.
    // Error bound and rationale on CurveFunctions.linearizeForQuadratic.
    return DiscreteFrechetDistance.distance(
        CurveFunctions.linearizeForQuadratic(a), CurveFunctions.linearizeForQuadratic(b));
  }

  public static Geometry frechetDistanceLine(Geometry a, Geometry b)  
  {   
    DiscreteFrechetDistance dist = new DiscreteFrechetDistance(
        CurveFunctions.linearizeForQuadratic(a), CurveFunctions.linearizeForQuadratic(b));
    return a.getFactory().createLineString(dist.getCoordinates());
  }

  @Metadata(description="Oriented discrete Hausdorff distance from A to B")
	public static double orientedDiscreteHausdorffDistance(Geometry a, Geometry b)	
	{		
    return DiscreteHausdorffDistance.orientedDistance(arc(a), arc(b));
	}
	
  @Metadata(description="Oriented discrete Hausdorff distance line from A to B, densified")
  public static Geometry orientedDiscreteHausdorffLineDensify(Geometry a, Geometry b, 
      @Metadata(title="Densify fraction (0..1]")
      double frac)  
  {   
    // Core rejects out-of-range fractions with a message that names neither the
    // parameter nor its meaning; a visual-QA session passed 10.0 here, reading
    // the knob as a distance like the tolerances nearby. Say what it is.
    if (frac <= 0.0 || frac > 1.0) {
      throw new IllegalArgumentException(
          "Densify fraction must be in (0, 1] -- it subdivides each segment to "
          + "that FRACTION of its length (e.g. 0.05), it is not a distance; got "
          + frac);
    }
    return DiscreteHausdorffDistance.orientedDistanceLine(arc(a), arc(b), frac);
  }

  @Metadata(description="Clipped directed Hausdorff distance from A to B")
  public static Geometry clippedDirectedHausdorffLine(Geometry a, Geometry b)  
  {   
    Geometry la = arc(a);
    Geometry lb = arc(b);
    // projectOnLine casts its input to LineString; a GeometryCollection reached
    // it in visual QA and died as a raw ClassCastException. Refuse with the
    // contract instead.
    if (!(la instanceof LineString) || !(lb instanceof LineString)) {
      throw new IllegalArgumentException(
          "clippedDirectedHausdorffLine needs single LineStrings (linear "
          + "referencing projects onto one line); got "
          + la.getGeometryType() + " and " + lb.getGeometryType());
    }
    Geometry clippedLine = LinearReferencingFunctions.project(la, lb);
    Coordinate[] pts = DirectedHausdorffDistance.distancePoints(clippedLine, lb);
    return a.getFactory().createLineString(pts);
  }
  
  @Metadata(description="Directed Hausdorff distance from A to B, up to tolerance")
  public static double directedHausdorffDistance(Geometry a, Geometry b, 
      @Metadata(title="Distance tolerance")
      double distTol)  
  {   
    return DirectedHausdorffDistance.distance(arc(a), arc(b), distTol);
  }
  
  @Metadata(description="Directed Hausdorff distance line from A to B, up to tolerance")
  public static Geometry directedHausdorffLineTol(Geometry a, Geometry b, 
      @Metadata(title="Distance tolerance")
      double distTol)  
  {   
    Coordinate[] pts = DirectedHausdorffDistance.distancePoints(arc(a), arc(b), distTol);
    return a.getFactory().createLineString(pts);
  }
  
  @Metadata(description="Directed Hausdorff distance line from A to B")
  public static Geometry directedHausdorffLine(Geometry a, Geometry b)  
  {   
    Coordinate[] pts = DirectedHausdorffDistance.distancePoints(arc(a), arc(b));
    return a.getFactory().createLineString(pts);
  }
  
  @Metadata(description="Hausdorff distance between A and B, up to tolerance")
  public static Geometry hausdorffLine(Geometry a, Geometry b)  
  {   
    Coordinate[] pts = DirectedHausdorffDistance.hausdorffDistancePoints(arc(a), arc(b));
    return a.getFactory().createLineString(pts);
  }
  
  //--------------------------------------------
  
  public static double distanceIndexed(Geometry a, Geometry b) {
    return IndexedFacetDistance.distance(arc(a), arc(b));
  }
  
  public static boolean isWithinDistanceIndexed(Geometry a, Geometry b, double distance) {
    return IndexedFacetDistance.isWithinDistance(arc(a), arc(b), distance);
  }
  
  public static Geometry nearestPointsIndexed(Geometry a, Geometry b) {
    Coordinate[] pts =  IndexedFacetDistance.nearestPoints(arc(a), arc(b));
    return a.getFactory().createLineString(pts);
  }
  
  public static Geometry nearestPointsIndexedEachB(Geometry a, Geometry b) {
    IndexedFacetDistance ifd = new IndexedFacetDistance(arc(a));
    
    int n = b.getNumGeometries();
    LineString[] lines = new LineString[n];
    for (int i = 0; i < n; i++) {
      Coordinate[] pts =  ifd.nearestPoints(arc(b.getGeometryN(i)));
      lines[i] = a.getFactory().createLineString(pts);
    }
    
    return a.getFactory().createMultiLineString(lines);
  }

}
