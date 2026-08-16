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

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.algorithm.MinimumBoundingCircle;
import org.locationtech.jts.algorithm.MinimumBoundingTriangle;
import org.locationtech.jts.algorithm.MinimumDiameter;
import org.locationtech.jts.algorithm.MinimumAreaRectangle;
import org.locationtech.jts.algorithm.construct.LargestEmptyCircle;
import org.locationtech.jts.algorithm.construct.MaximumInscribedCircle;
import org.locationtech.jts.algorithm.hull.ConcaveHull;
import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.OctagonalEnvelope;
import org.locationtech.jtstest.geomfunction.Metadata;

public class ConstructionFunctions {
  /**
   * MaximumInscribedCircle still measures from coordinates, so curve
   * input is fitted against its control polygon unless a certified
   * closed form applies. LargestEmptyCircle uses the public class
   * directly: the typed obstacle-distance oracle (and the certified
   * disc cell) already see arcs and discs.
   * <p>
   * <b>PERF-GATE.</b> A circular disc's MIC is the disc itself (centre +
   * radius) and is taken in closed form. A certified stadium's MIC is
   * the cap radius with centre at the midpoint of the two cap centres.
   * LargestEmptyCircle of a circular disc used as boundary-as-obstacle
   * is the same closed form (centre, r).
   */
  private static Geometry arc(Geometry g) {
    return CurveFunctions.linearizeForOps(g);
  }

  public static Geometry octagonalEnvelope(Geometry g) { return OctagonalEnvelope.octagonalEnvelope(g); }
  
  public static Geometry minimumDiameter(Geometry g) {      return (new MinimumDiameter(g)).getDiameter();  }
  public static double minimumDiameterLength(Geometry g) {      return (new MinimumDiameter(g)).getDiameter().getLength();  }
  public static Geometry minimumDiameterRectangle(Geometry g) { return MinimumDiameter.getMinimumRectangle(g);  }

  public static Geometry minimumAreaRectangle(Geometry g) { return MinimumAreaRectangle.getMinimumRectangle(g);  }
    
  public static Geometry minimumBoundingCircle(Geometry g) { return (new MinimumBoundingCircle(g)).getCircle();  }
  public static double minimumBoundingCircleDiameterLen(Geometry g) {      return 2 * (new MinimumBoundingCircle(g)).getRadius();  }

  public static Geometry minimumBoundingTriangle(Geometry g) { return (new MinimumBoundingTriangle(g)).getTriangle();  }

  public static Geometry maximumDiameter(Geometry g) {      return (new MinimumBoundingCircle(g)).getMaximumDiameter();  }
  public static double maximumDiameterLength(Geometry g) {  
    return (new MinimumBoundingCircle(g)).getMaximumDiameter().getLength();
  }
  
  public static Geometry boundary(Geometry g) {      return g.getBoundary();  }
  public static Geometry convexHull(Geometry g) {      return g.convexHull();  }
  public static Geometry centroid(Geometry g) {      return g.getCentroid();  }
  public static Geometry interiorPoint(Geometry g) {      return g.getInteriorPoint();  }

  public static Geometry densify(Geometry g, double distance) { return Densifier.densify(g, distance); }
  
  //--------------------------------------------
  
  @Metadata(description="Constructs the Maximum Inscribed Circle of a polygonal geometry")
  public static Geometry maxInscribedCircle(Geometry g,
      @Metadata(title="Distance tolerance")
      double tolerance) { 
    LineString exact = exactMicRadiusLine(g);
    if (exact != null) return circleByRadiusLine(exact, 60);
    MaximumInscribedCircle mic = new MaximumInscribedCircle(arc(g), tolerance); 
    Coordinate center = mic.getCenter().getCoordinate();
    Coordinate radiusPt = mic.getRadiusPoint().getCoordinate();
    LineString radiusLine = g.getFactory().createLineString(new Coordinate[] { center, radiusPt });
    return circleByRadiusLine(radiusLine, 60);
  }
  
  @Metadata(description="Constructs the center point of the Maximum Inscribed Circle of a polygonal geometry")
  public static Geometry maxInscribedCircleCenter(Geometry g,
      @Metadata(title="Distance tolerance")
      double tolerance) { 
    Coordinate c = CurveExactFns.micCenter(g);
    if (c != null) return g.getFactory().createPoint(c);
    return MaximumInscribedCircle.getCenter(arc(g), tolerance); 
  }
  
  @Metadata(description="Constructs a radius line of the Maximum Inscribed Circle of a polygonal geometry")
  public static Geometry maxInscribedCircleRadius(Geometry g,
      @Metadata(title="Distance tolerance")
      double tolerance) { 
    LineString exact = exactMicRadiusLine(g);
    if (exact != null) return exact;
    MaximumInscribedCircle mic = new MaximumInscribedCircle(arc(g), tolerance); 
    return mic.getRadiusLine(); 
  }

  @Metadata(description="Computes the radius of the Maximum Inscribed Circle of a polygonal geometry")
  public static double maxInscribedCircleRadiusLen(Geometry g,
      @Metadata(title="Distance tolerance")
      double tolerance) { 
    Double r = CurveExactFns.micRadius(g);
    if (r != null) return r.doubleValue();
    MaximumInscribedCircle mic = new MaximumInscribedCircle(arc(g), tolerance); 
    return mic.getRadiusLine().getLength(); 
  }

  private static LineString exactMicRadiusLine(Geometry g) {
    Coordinate c = CurveExactFns.micCenter(g);
    Coordinate p = CurveExactFns.micRadiusPoint(g);
    if (c == null || p == null) return null;
    return g.getFactory().createLineString(new Coordinate[] { c, p });
  }

  //--------------------------------------------
  
  @Metadata(description="Constructs the Largest Empty Circle in a set of obstacles")
  public static Geometry largestEmptyCircle(Geometry obstacles, Geometry boundary,
      @Metadata(title="Accuracy distance tolerance")
      double tolerance) { 
    LineString radiusLine = LargestEmptyCircle.getRadiusLine(
        obstacles, boundary, tolerance);
    return circleByRadiusLine(radiusLine, 60);
  }
  
  @Metadata(description="Computes a radius line of the Largest Empty Circle in a set of obstacles")
  public static Geometry largestEmptyCircleCenter(Geometry obstacles, Geometry boundary,
      @Metadata(title="Accuracy distance tolerance")
      double tolerance) { 
    return LargestEmptyCircle.getCenter(obstacles, boundary, tolerance);
  }
  
  @Metadata(description="Computes a radius line of the Largest Empty Circle in a set of obstacles")
  public static Geometry largestEmptyCircleRadius(Geometry obstacles, Geometry boundary, 
      @Metadata(title="Accuracy distance tolerance")
      double tolerance) { 
    return LargestEmptyCircle.getRadiusLine(obstacles, boundary, tolerance); 
  }
  
  //--------------------------------------------

  @Metadata(description="Constructs an n-point circle from a 2-point line giving the radius")
  public static Geometry circleByRadiusLine(Geometry radiusLine,
      @Metadata(title="Number of vertices")
      int nPts) {
    // nPts = 1 used to die inside LinearRing's constructor ("found 2 - must
    // be 0 or >= 3"), naming neither the parameter nor its bound.
    if (nPts < 3) {
      throw new IllegalArgumentException(
          "Number of vertices must be at least 3 to form a ring; got " + nPts);
    }
    Coordinate[] radiusPts = radiusLine.getCoordinates();
    Coordinate center = radiusPts[0];
    Coordinate radiusPt = radiusPts[1];
    double dist = radiusPt.distance(center);
    
    double angInc = 2 * Math.PI / (nPts - 1);
    Coordinate[] circlePts = new Coordinate[nPts + 1];
    circlePts[0] = radiusPt.copy();
    circlePts[nPts] = radiusPt.copy();
    double angStart = Angle.angle(center, radiusPt);
    for (int i = 1; i < nPts; i++) {
      double x = center.getX() + dist * Math.cos(angStart + i * angInc);
      double y = center.getY() + dist * Math.sin(angStart + i * angInc);
      circlePts[i] =  new Coordinate(x,y);
    }
    return radiusLine.getFactory().createPolygon(circlePts);
  }
 
  public static Geometry concaveHullByLen(Geometry geom, 
      @Metadata(title="Length")
      double maxLen) {
    return ConcaveHull.concaveHullByLength(geom, maxLen);
  }
  
  public static Geometry concaveHullWithHolesByLen(Geometry geom, 
      @Metadata(title="Length")
      double maxLen) {
    return ConcaveHull.concaveHullByLength(geom, maxLen, true);
  }
  
  public static Geometry concaveHullByLenRatio(Geometry geom, 
      @Metadata(title="Length Ratio")
      double maxLen) {
    return ConcaveHull.concaveHullByLengthRatio(geom, maxLen);
  }
  
  public static Geometry concaveHullWithHolesByLenRatio(Geometry geom, 
      @Metadata(title="Length Ratio")
      double maxLen) {
    return ConcaveHull.concaveHullByLengthRatio(geom, maxLen, true);
  }
  
  public static double concaveHullLenGuess(Geometry geom) {
    return ConcaveHull.uniformGridEdgeLength(geom);
  }
  
  /**
   * A concaveness measure defined in terms of the perimeter length
   * relative to the convex hull perimeter.
   * <pre>
   * C = ( P(geom) - P(CH) ) / P(CH)
   * </pre>
   * Concaveness values are >= 0.  
   * A convex polygon has C = 0. 
   * A higher concaveness indicates a more concave polygon.
   * <p>
   * Originally defined by Park & Oh, 2012.
   * 
   * @param geom a polygonal geometry
   * @return the concaveness measure of the geometry
   */
  public static double concaveness(Geometry geom) {
    double convexLen = geom.convexHull().getLength();
    return (geom.getLength() - convexLen) / convexLen;
  }
  
}
