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

import org.locationtech.jts.algorithm.hull.ConcaveHull;
import org.locationtech.jts.algorithm.hull.ConcaveHullOfPolygons;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jtstest.geomfunction.Metadata;

public class HullFunctions {
  /**
   * Arc-aware without help: {@code convexHull()} is an instance method, so the
   * curve types override it (see {@code CurveOps}).
   */
  public static Geometry convexHull(Geometry g) {      return g.convexHull();  }

  /**
   * The ConcaveHull entry points below are <em>static</em> and take a
   * {@link Geometry}, so there is no virtual call for a curve type to override,
   * and jts-core cannot see the curve types to linearise on its own. Left alone
   * they build from {@code getCoordinates()} -- for a curve, only its control
   * points -- and silently return a plausible hull of the wrong shape.
   * <p>
   * So the caller linearises. Non-curves pass through untouched, which is why
   * this can be applied unconditionally.
   * <p>
   * The tolerance is {@code linearizeForHull}'s, deliberately coarser than the
   * one {@code convexHull} and {@code distance} use: those converge as the
   * sampling tightens, whereas a point-set concave hull erodes onto the curve
   * and degenerates into a one-chord-wide ribbon.
   * <p>
   * <b>Known limitation.</b> Coarsening rescales that ribbon, it does not remove
   * it. The waist settles at roughly one chord regardless of the parameter --
   * 0.1988 on a 10-unit compound curve, 0.3970 on a 4-unit curve polygon, for
   * every max-edge-length from 0.3 to 5.0 -- so a curve input still renders as a
   * pinched bowtie, just at a visible width rather than a hairline. Only two
   * settings escape it: a length ratio of 1.0, which effectively disables erosion
   * and converges on the convex hull, and an alpha radius large enough to bridge
   * the ribbon.
   * <p>
   * This is inherent rather than a defect in the linearisation. These operations
   * are defined over a point set (or, for {@code ConcaveHullOfPolygons}, over
   * polygons -- which is why those throw on a 1-D curve instead), and sampling a
   * curve into a point cloud has no well-posed concave hull: the answer is a
   * function of the sampling. Accepted as-is; callers wanting control should
   * linearise deliberately with {@code Curve -> toLinear} and hull the result,
   * or supply genuinely 2-D input.
   */
  private static Geometry arcAware(Geometry geom) {
    return CurveFunctions.linearizeForHull(geom);
  }

  public static Geometry concaveHullPoints(Geometry geom,
      @Metadata(title="Max Edge Length")
      double maxLen) {
    return ConcaveHull.concaveHullByLength(arcAware(geom), maxLen);
  }

  public static Geometry concaveHullPointsWithHoles(Geometry geom,
      @Metadata(title="Max Edge Length")
      double maxLen) {
    return ConcaveHull.concaveHullByLength(arcAware(geom), maxLen, true);
  }

  @Metadata(curveAwareness="passthrough")
  public static Geometry concaveHullPointsByLenRatio(Geometry geom,
      @Metadata(title="Length Ratio")
      double maxLenRatio) {
    return ConcaveHull.concaveHullByLengthRatio(arcAware(geom), maxLenRatio);
  }

  public static Geometry concaveHullPointsWithHolesByLenRatio(Geometry geom,
      @Metadata(title="Length Ratio")
      double maxLenRatio) {
    return ConcaveHull.concaveHullByLengthRatio(arcAware(geom), maxLenRatio, true);
  }

  public static Geometry alphaShape(Geometry geom,
      @Metadata(title="Alpha (Radius)")
      double alpha) {
    return ConcaveHull.alphaShape(arcAware(geom), alpha, false);
  }

  public static Geometry alphaShapeWithHoles(Geometry geom,
      @Metadata(title="Alpha (Radius)")
      double alpha) {
    return ConcaveHull.alphaShape(arcAware(geom), alpha, true);
  }

  public static double concaveHullLenGuess(Geometry geom) {
    return ConcaveHull.uniformGridEdgeLength(arcAware(geom));
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
  
  public static Geometry concaveHullPolygons(Geometry geom, 
      @Metadata(title="Max Edge Length")
      double maxEdgeLen) {
    return ConcaveHullOfPolygons.concaveHullByLength(arcAware(geom), maxEdgeLen);
  }
  
  public static Geometry concaveHullPolygonsWithHoles(Geometry geom, 
      @Metadata(title="Max Edge Length")
      double maxEdgeLen) {
    return ConcaveHullOfPolygons.concaveHullByLength(arcAware(geom), maxEdgeLen, false, true);
  }
  
  public static Geometry concaveHullPolygonsTight(Geometry geom, 
      @Metadata(title="Max Edge Length")
      double maxEdgeLen) {
    return ConcaveHullOfPolygons.concaveHullByLength(arcAware(geom), maxEdgeLen, true, false);
  }
  
  public static Geometry concaveHullPolygonsByLenRatio(Geometry geom, 
      @Metadata(title="Edge Length Ratio")
      double maxEdgeLenRatio) {
    return ConcaveHullOfPolygons.concaveHullByLengthRatio(arcAware(geom), maxEdgeLenRatio);
  }
  
  public static Geometry concaveHullPolygonsTightByLenRatio(Geometry geom, 
      @Metadata(title="Edge Length Ratio")
      double maxEdgeLenRatio) {
    return ConcaveHullOfPolygons.concaveHullByLengthRatio(arcAware(geom), maxEdgeLenRatio, true, false);
  }
  
  public static Geometry concaveFill(Geometry geom, 
      @Metadata(title="Max Edge Length")
      double maxEdgeLen) {
    return ConcaveHullOfPolygons.concaveFillByLength(arcAware(geom), maxEdgeLen);
  }
  
  public static Geometry concaveFillByLenRatio(Geometry geom, 
      @Metadata(title="Edge Length Ratio")
      double maxEdgeLenRatio) {
    return ConcaveHullOfPolygons.concaveFillByLengthRatio(arcAware(geom), maxEdgeLenRatio);
  }
  
}
