/*
 * Copyright (c) 2019 Martin Davis.
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

import static org.locationtech.jts.operation.overlayng.OverlayNG.DIFFERENCE;
import static org.locationtech.jts.operation.overlayng.OverlayNG.INTERSECTION;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.IntersectionMatrix;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jtstest.geomfunction.Metadata;

/**
 * Functions to test using spatial predicates 
 * as a filter in front of overlay operations
 * to optimize performance.
 * 
 * @author Martin Davis
 *
 */
public class OverlayNGOptFunctions {

  /**
   * Densifies a curve so a decision can be made about it.
   * <p>
   * Used for the <em>predicate</em> and for the fall-through overlay, never for
   * the answer: when a filter fires, the original operand is returned untouched,
   * which is exact. The chord ring of an arc circle is its inscribed polygon, so
   * a filter reading control points can conclude that a geometry wholly inside
   * the circle is disjoint from it -- the opposite verdict, not a rounding of it.
   * <p>
   * Non-curve input is returned as the same object, so the prepared-geometry
   * cache and the plain-input timings this class exists to measure are unaffected.
   */
  private static Geometry arc(Geometry g) {
    return CurveFunctions.linearizeForOps(g);
  }
  
  private static Geometry fastCoversIntersection(Geometry a, Geometry b) {
    IntersectionMatrix im = arc(a).relate(arc(b));
    if (! im.isIntersects()) return createEmpty(a);
    if (im.isCovers()) return b.copy();
    if (im.isCoveredBy()) return a.copy();
    // null indicates full overlay required
    return null;
  }

  private static Geometry createEmpty(Geometry a) {
    return a.getFactory().createEmpty(a.getDimension());
  }
  
  private static Geometry fastCoversDifference(Geometry a, Geometry b) {
    IntersectionMatrix im = arc(a).relate(arc(b));
    if (! im.isIntersects()) return a.copy();
    if (im.isCoveredBy()) return createEmpty(a);
    // null indicates full overlay required
    return null;
  }
  
  /**
   * Use spatial predicates as a filter
   * in front of intersection.
   * 
   * @param a a geometry
   * @param b a geometry
   * @return the intersection of the geometries
   */
  public static Geometry intersectionOrigClassic(Geometry a, Geometry b) {
    Geometry intFast = fastCoversIntersection(a, b);
    if (intFast != null) return intFast;
    return a.intersection(b);
  }
  
  /**
   * Use prepared geometry spatial predicates as a filter
   * in front of intersection,
   * with the first operand prepared.
   * 
   * @param a a geometry to prepare
   * @param b a geometry
   * @return the intersection of the geometries
   */
  public static Geometry intersectionOrigPrep(Geometry a, Geometry b) {
    PreparedGeometry pg = cacheFetch(a);
    Geometry bArc = arc(b);
    if (! pg.intersects(bArc)) return null;
    if (pg.covers(bArc)) return b.copy();
    return a.intersection(b);
  }
  
  public static Geometry intersectionOrigPrepNoCache(Geometry a, Geometry b) {
    PreparedGeometry pg = (new PreparedGeometryFactory()).create(arc(a));
    Geometry bArc = arc(b);
    if (! pg.intersects(bArc)) return null;
    if (pg.covers(bArc)) return b.copy();
    return a.intersection(b);
  }
  
  public static Geometry intersectionSR(Geometry a, Geometry b, 
      @Metadata(title="Grid Scale") double scaleFactor) {
    Geometry intFast = fastCoversIntersection(a, b);
    if (intFast != null) return intFast;
    return OverlayNG.overlay(arc(a), arc(b), INTERSECTION, new PrecisionModel(scaleFactor));
  }
  
  public static Geometry intersectionPrepSR(Geometry a, Geometry b,
      @Metadata(title="Grid Scale") double scaleFactor) {
    PreparedGeometry pg = cacheFetch(a);
    Geometry bArc = arc(b);
    if (! pg.intersects(bArc)) return null;
    if (pg.covers(bArc)) return b.copy();
    return OverlayNG.overlay(arc(a), arc(b), INTERSECTION, new PrecisionModel(scaleFactor));
  }
  
  public static Geometry difference(Geometry a, Geometry b) {
    Geometry intFast = fastCoversDifference(a, b);
    if (intFast != null) return intFast;
    return OverlayNGRobust.overlay(arc(a), arc(b), DIFFERENCE);
  }
  
  public static Geometry intersection(Geometry a, Geometry b) {
    Geometry intFast = fastCoversIntersection(a, b);
    if (intFast != null) return intFast;
    return OverlayNGRobust.overlay(arc(a), arc(b), INTERSECTION);
  }
  
  /**
   * Using auto slows things down quite a bit (due to need to scan to find
   * scale factor), so not recommended.
   * 
   * @param a
   * @param b
   * @return
   */
  public static Geometry intersectionPrep(Geometry a, Geometry b) {
    PreparedGeometry pg = cacheFetch(a);
    Geometry bArc = arc(b);
    if (! pg.intersects(bArc)) return null;
    if (pg.covers(bArc)) return b.copy();
    return OverlayNGRobust.overlay(arc(a), arc(b), OverlayNG.INTERSECTION);
  }
  
  public static Geometry intersectionPrepNoCache(Geometry a, Geometry b) {
    PreparedGeometry pg = (new PreparedGeometryFactory()).create(arc(a));
    Geometry bArc = arc(b);
    if (! pg.intersects(bArc)) return null;
    if (pg.covers(bArc)) return b.copy();
    return OverlayNGRobust.overlay(arc(a), arc(b), OverlayNG.INTERSECTION);
  }
  
  private static Geometry cacheKey = null;
  private static PreparedGeometry cache = null;
  

  private static PreparedGeometry cacheFetch(Geometry g) {
    if (g != cacheKey) {
      cacheKey = g;
      cache = (new PreparedGeometryFactory()).create(arc(g));
    }
    return cache;
  }
  
}
