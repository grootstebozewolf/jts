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

  /**
   * Discrete Fréchet distance after arc linearise (quadratic sampler).
   * <p>
   * Fréchet couplings are monotone and must join starts to starts and ends to
   * ends, so the value is always at least {@code d(startA,startB)} and
   * {@code d(endA,endB)}. Free ends that diverge (e.g. A ends at
   * {@code (520 460)} vs B at {@code (522 365)} → leash ≥ 95) routinely
   * <em>are</em> the realizing pair — that is the metric, not a sampling bug.
   * Mid-course gap alone is not what this returns.
   */
  @Metadata(description="Discrete Fréchet after arc linearise (monotone coupling; free ends lower-bound the leash — often the realizing pair)")
  public static double frechetDistance(Geometry a, Geometry b)  
  {   
    // Quadratic DP: coarser sampling, or a curve pair runs for 20 seconds.
    // Error bound and rationale on CurveFunctions.linearizeForQuadratic.
    return DiscreteFrechetDistance.distance(
        CurveFunctions.linearizeForQuadratic(a), CurveFunctions.linearizeForQuadratic(b));
  }

  /**
   * Realizing leash segment for {@link #frechetDistance} (pair of points on A and B
   * that attain the discrete Fréchet value). Same free-end lower bound as the
   * distance: see that method.
   */
  @Metadata(description="Discrete Fréchet realizing leash segment (free ends often dominate — not path-only mid-course gap)")
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
  
  /**
   * Full directed Hausdorff distance h(A,B) with an explicit accuracy tolerance.
   * <p>
   * {@code distTol} is the <em>approximation accuracy</em> in coordinate units
   * (how close the realizing pair is to the true max-min). It is <b>not</b> a
   * free-end clip, a densify fraction, or a path-matching window. Free endpoints
   * still dominate when they stick past the target — use
   * {@link #clippedDirectedHausdorffLine} for mid-course path comparison.
   * <p>
   * Arc linearisation is matched to {@code distTol} (coarser tolerance → fewer
   * chords → faster); previously every call densified at 1e-6 of extent and a
   * visual-QA run with {@code distTol = 10} paid for a dense polyline then a
   * long segment queue (~40 ms) only to report the free-end pair again.
   */
  @Metadata(description="Directed Hausdorff h(A,B): distTol = accuracy in map units (NOT free-end clip / densify frac). Free ends dominate → use clippedDirectedHausdorffLine for paths")
  public static double directedHausdorffDistance(Geometry a, Geometry b, 
      @Metadata(title="Accuracy (map units, not densify frac)")
      double distTol)  
  {   
    Geometry la = CurveFunctions.linearizeForDistanceTol(a, distTol);
    Geometry lb = CurveFunctions.linearizeForDistanceTol(b, distTol);
    return DirectedHausdorffDistance.distance(la, lb, distTol);
  }
  
  /**
   * Realizing segment for {@link #directedHausdorffDistance}. Same contract:
   * full-extent DHD, free ends can dominate, {@code distTol} is accuracy only.
   * Path-to-path → {@link #clippedDirectedHausdorffLine}.
   */
  @Metadata(description="Directed Hausdorff segment h(A,B): distTol = accuracy in map units (NOT free-end clip). Free ends dominate → clippedDirectedHausdorffLine for paths")
  public static Geometry directedHausdorffLineTol(Geometry a, Geometry b, 
      @Metadata(title="Accuracy (map units, not densify frac)")
      double distTol)  
  {   
    Geometry la = CurveFunctions.linearizeForDistanceTol(a, distTol);
    Geometry lb = CurveFunctions.linearizeForDistanceTol(b, distTol);
    Coordinate[] pts = DirectedHausdorffDistance.distancePoints(la, lb, distTol);
    return a.getFactory().createLineString(pts);
  }
  
  /**
   * Full directed Hausdorff realizing segment from A to B (after arc linearise).
   * <p>
   * This is the true max-min over the <em>entire</em> geometries. Free endpoints
   * of A that stick past B routinely dominate — e.g. multi-arc A ending at
   * {@code (1000 410)} against B ending at {@code (1000 300)} realises
   * {@code LINESTRING (1000 410, 1000 300)} of length 110, which is continuous
   * and correct, but is not a path-to-path mismatch. For comparing two routes
   * of similar extent use {@link #clippedDirectedHausdorffLine}, which projects
   * A onto B first and reports the mid-course gap (~69 on that same pair).
   */
  @Metadata(description="Directed Hausdorff realizing segment h(A,B) after arc linearise (FULL extent — free ends dominate; path matching → clippedDirectedHausdorffLine)")
  public static Geometry directedHausdorffLine(Geometry a, Geometry b)  
  {   
    Coordinate[] pts = DirectedHausdorffDistance.distancePoints(arc(a), arc(b));
    return a.getFactory().createLineString(pts);
  }
  
  @Metadata(description="Symmetric Hausdorff realizing segment after arc linearise (full extent — free ends can dominate)")
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
