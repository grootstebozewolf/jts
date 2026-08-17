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

package org.locationtech.jts.algorithm.distance;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

/**
 * An algorithm for computing a distance metric
 * which is an approximation to the Hausdorff Distance
 * based on a discretization of the input {@link Geometry}s.
 * The algorithm computes the Hausdorff distance restricted to discrete points
 * for one of the geometries.
 * The algorithm works on point and linear geometries only; 
 * areal geometries are treated as their linear boundary.
 * The points can be either the vertices of the geometries (the default), 
 * or the geometries with line segments densified by a given fraction.
 * The class can also determine two points of the geometries 
 * which are separated by the computed distance.
* <p>
 * This algorithm is an approximation to the standard Hausdorff distance.
 * Specifically, 
 * <blockquote>
 *    <i>for all geometries A, B:    DHD(A, B) &lt;= HD(A, B)</i>
 * </blockquote>
 * The approximation can be made as close as needed by densifying the input geometries.  
 * In the limit, this value will approach the true Hausdorff distance:
 * <blockquote>
 *    <i>DHD(A, B, densifyFactor) &rarr; HD(A, B) as densifyFactor &rarr; 0.0</i>
 * </blockquote>
 * The default approximation is exact or close enough for a large subset of useful cases.
 * Examples of these are:
 * <ul>
 * <li>computing distance between Linestrings that are roughly parallel to each other,
 * and roughly equal in length.  This occurs in matching linear networks.
 * <li>Testing similarity of geometries.
 * </ul>
 * An example where the default approximation is not close is:
 * <pre>
 *   A = LINESTRING (0 0, 100 0, 10 100, 10 100)
 *   B = LINESTRING (0 100, 0 10, 80 10)
 *   
 *   DHD(A, B) = 22.360679774997898
 *   HD(A, B) ~= 47.8
 * </pre>
 * The class can compute the oriented Hausdorff distance from A to B.
 * This computes the distance to the farthest point on A from B.
 * <blockquote>
 *   <i>OHD(A, B) = max<sub>a &isin; A</sub>( Distance(a, B) )</i>
 *   <br>
 *   with
 *   <br>
 *   <i>HD(A, B) = max( OHD(A, B), OHD(B, A) )</i>
 * </blockquote>
 * A use case is to test whether a geometry A lies completely within a given 
 * distance of another one B.
 * This is more efficient than testing whether A is covered by a buffer of B.
 * <p>
 * Two certified curve pairs use a closed form instead of vertices or
 * chord densify: two circular discs (full-circle {@code CurvePolygon}
 * or a single-member {@code MultiSurface} of one), and a single-arc
 * {@code CircularString} toward a single-segment {@code LineString}.
 * Detection uses {@link Geometry#getGeometryType()} so this class does
 * not import jts-curve. Any other pair keeps the vertex / chord path;
 * {@code densifyFrac} still interpolates chords on that fallback.
 * 
 * @see DiscreteFrechetDistance
 * @see DirectedHausdorffDistance
 * 
 */
public class DiscreteHausdorffDistance
{
  /**
   * Computes the Hausdorff distance between two geometries.
   * 
   * @param g0 the first input
   * @param g1 the second input
   * @return the Hausdorff distance between g0 and g1
   */
  public static double distance(Geometry g0, Geometry g1)
  {
    DiscreteHausdorffDistance dist = new DiscreteHausdorffDistance(g0, g1);
    return dist.distance();
  }

  /**
   * Computes the Hausdorff distance between two geometries,
   * with each segment densified by the given fraction.
   * 
   * @param g0 the first input
   * @param g1 the second input
   * @param densifyFrac the densification fraction (in [0, 1])
   * @return the Hausdorff distance between g0 and g1
   */
  public static double distance(Geometry g0, Geometry g1, double densifyFrac)
  {
    DiscreteHausdorffDistance dist = new DiscreteHausdorffDistance(g0, g1);
    dist.setDensifyFraction(densifyFrac);
    return dist.distance();
  }

  /**
   * Computes a line containing points indicating 
   * the Hausdorff distance between two geometries.
   * 
   * @param g0 the first input
   * @param g1 the second input
   * @return a 2-point line indicating the distance
   */
  public static LineString distanceLine(Geometry g0, Geometry g1)
  {
    DiscreteHausdorffDistance dist = new DiscreteHausdorffDistance(g0, g1);
    dist.distance();
    return g0.getFactory().createLineString(dist.getCoordinates());  
  }

  /**
   * Computes a line containing points indicating 
   * the Hausdorff distance between two geometries,
   * with each segment densified by the given fraction.
   * 
   * @param g0 the first input
   * @param g1 the second input
   * @param densifyFrac the densification fraction (in [0, 1])
   * @return a 2-point line indicating the distance
   */
  public static LineString distanceLine(Geometry g0, Geometry g1, double densifyFrac)
  {
    DiscreteHausdorffDistance dist = new DiscreteHausdorffDistance(g0, g1);
    dist.setDensifyFraction(densifyFrac);
    dist.distance();
    return g0.getFactory().createLineString(dist.getCoordinates());  
  }

  /**
   * Computes the oriented Hausdorff distance from one geometry to another.
   * 
   * @param g0 the first input
   * @param g1 the second input
   * @return the oriented Hausdorff distance from g0 to g1
   */
  public static double orientedDistance(Geometry g0, Geometry g1)
  {
    DiscreteHausdorffDistance dist = new DiscreteHausdorffDistance(g0, g1);
    return dist.orientedDistance();
  }

  /**
   * Computes the oriented Hausdorff distance from one geometry to another,
   * with each segment densified by the given fraction.
   * 
   * @param g0 the first input
   * @param g1 the second input
   * @param densifyFrac the densification fraction (in [0, 1])
   * @return the oriented Hausdorff distance from g0 to g1
   */
  public static double orientedDistance(Geometry g0, Geometry g1, double densifyFrac)
  {
    DiscreteHausdorffDistance dist = new DiscreteHausdorffDistance(g0, g1);
    dist.setDensifyFraction(densifyFrac);
    return dist.orientedDistance();
  }

  /**
   * Computes a line containing points indicating 
   * the computed oriented Hausdorff distance from one geometry to another.
   * 
   * @param g0 the first input
   * @param g1 the second input
   * @return a 2-point line indicating the distance
   */
  public static LineString orientedDistanceLine(Geometry g0, Geometry g1)
  {
    DiscreteHausdorffDistance dist = new DiscreteHausdorffDistance(g0, g1);
    dist.orientedDistance();
    return g0.getFactory().createLineString(dist.getCoordinates());  
  }

  /**
   * Computes a line containing points indicating 
   * the computed oriented Hausdorff distance from one geometry to another,
   * with each segment densified by the given fraction.
   *
   * @param g0 the first input
   * @param g1 the second input
   * @param densifyFrac the densification fraction (in [0, 1])
   * @return a 2-point line indicating the distance
   */
  public static LineString orientedDistanceLine(Geometry g0, Geometry g1, double densifyFrac)
  {
    DiscreteHausdorffDistance dist = new DiscreteHausdorffDistance(g0, g1);
    dist.setDensifyFraction(densifyFrac);
    dist.orientedDistance();
    return g0.getFactory().createLineString(dist.getCoordinates());  
  }

  /**
   * Directed Hausdorff distance from circle 1 to circle 2 (the boundaries).
   */
  public static double directedHausdorffCircleToCircle(
      double c1x, double c1y, double r1, double c2x, double c2y, double r2) {
    PointPairDistance dest = new PointPairDistance();
    circleToCircle(c1x, c1y, r1, c2x, c2y, r2, dest);
    return dest.getDistance();
  }

  /**
   * Directed Hausdorff distance from the circular arc through
   * {@code start, mid, end} to the segment {@code seg0, seg1}.
   * The answer is the farthest point on the arc from the segment
   * (the apex on the D-HF witness), not the far-end chord.
   */
  public static double directedHausdorffArcToSegment(
      Coordinate start, Coordinate mid, Coordinate end,
      Coordinate seg0, Coordinate seg1) {
    PointPairDistance dest = new PointPairDistance();
    arcToSegment(start, mid, end, seg0, seg1, dest);
    return dest.getDistance();
  }

  private Geometry g0;
  private Geometry g1;
  private PointPairDistance ptDist = new PointPairDistance();
  
  /**
   * Value of 0.0 indicates that no densification should take place
   */
  private double densifyFrac = 0.0;

  public DiscreteHausdorffDistance(Geometry g0, Geometry g1)
  {
    this.g0 = g0;
    this.g1 = g1;
  }

  /**
   * Sets the fraction by which to densify each segment.
   * Each segment will be (virtually) split into a number of equal-length
   * subsegments, whose fraction of the total length is closest
   * to the given fraction.
   * 
   * @param densifyFrac a fraction in range (0, 1]
   */
  public void setDensifyFraction(double densifyFrac)
  {
    if (densifyFrac > 1.0 
        || densifyFrac <= 0.0)
      throw new IllegalArgumentException("Fraction is not in range (0.0 - 1.0]");
        
    this.densifyFrac = densifyFrac;
  }
  
  /** 
   * Computes the Hausdorff distance between A and B.
   * 
   * @return the Hausdorff distance
   */
  public double distance() 
  { 
    compute(g0, g1);
    return ptDist.getDistance(); 
  }

  /** 
   * Computes the oriented Hausdorff distance from A to B.
   * 
   * @return the oriented Hausdorff distance
   */
  public double orientedDistance() 
  { 
    computeOrientedDistance(g0, g1, ptDist);
    return ptDist.getDistance(); 
  }

  public Coordinate[] getCoordinates() { return ptDist.getCoordinates(); }

  private void compute(Geometry g0, Geometry g1)
  {
    computeOrientedDistance(g0, g1, ptDist);
    computeOrientedDistance(g1, g0, ptDist);
  }

  private void computeOrientedDistance(Geometry discreteGeom, Geometry geom, PointPairDistance ptDist)
  {
    if (computeExactOriented(discreteGeom, geom, ptDist)) {
      return;
    }
    // D-HF (#1195): general curve inputs densify so the vertex path
    // samples the arc, not only control chords. Certified pairs above
    // still own the closed form (and skip densifyFrac on that path).
    Geometry sampled = densifyCurvePackage(discreteGeom);
    MaxPointDistanceFilter distFilter = new MaxPointDistanceFilter(geom);
    sampled.apply(distFilter);
    ptDist.setMaximum(distFilter.getMaxPointDistance());
    
    if (densifyFrac > 0) {
      MaxDensifiedByFractionDistanceFilter fracFilter = new MaxDensifiedByFractionDistanceFilter(geom, densifyFrac);
      sampled.apply(fracFilter);
      ptDist.setMaximum(fracFilter.getMaxPointDistance());
      
    }
  }

  /**
   * Densify jts-curve package geometries for the general Hausdorff path.
   * Detection by package name so core does not import jts-curve.
   */
  private static Geometry densifyCurvePackage(Geometry g) {
    if (g == null || g.getClass().getName().indexOf(".geom.curve.") < 0) {
      return g;
    }
    org.locationtech.jts.geom.Envelope env = g.getEnvelopeInternal();
    double extent = Math.max(env.getWidth(), env.getHeight());
    double tol = (extent > 0.0 ? extent : 1.0) * 1.0e-4;
    return org.locationtech.jts.densify.Densifier.densify(g, tol);
  }

  /**
   * Closed form for the two certified pairs. Returns {@code true} when
   * {@code ptDist} was updated and the vertex / chord path must be skipped.
   */
  private static boolean computeExactOriented(Geometry from, Geometry to,
      PointPairDistance ptDist) {
    double[] da = circularDisc(from);
    double[] db = circularDisc(to);
    if (da != null && db != null) {
      circleToCircle(da[0], da[1], da[2], db[0], db[1], db[2], ptDist);
      return true;
    }
    if (isSingleArc(from) && isSingleSegment(to)) {
      Coordinate[] a = from.getCoordinates();
      Coordinate[] b = to.getCoordinates();
      arcToSegment(a[0], a[1], a[2], b[0], b[1], ptDist);
      return true;
    }
    return false;
  }

  static boolean isSingleArc(Geometry g) {
    if (g == null || !"CircularString".equals(g.getGeometryType())) return false;
    if (g.isEmpty() || g.getNumPoints() != 3) return false;
    Coordinate[] c = g.getCoordinates();
    return circumcircle(c[0], c[1], c[2]) != null;
  }

  static boolean isSingleSegment(Geometry g) {
    return g instanceof LineString
        && "LineString".equals(g.getGeometryType())
        && g.getNumPoints() == 2;
  }

  /**
   * {@code {cx, cy, r}} of a full-circle {@code CircularString}, or {@code null}.
   *
   * @param g a geometry
   * @return {@code {cx, cy, r}} or {@code null}
   */
  public static double[] circularRing(Geometry g) {
    if (g == null || !"CircularString".equals(g.getGeometryType())) return null;
    return fullCircle(g);
  }

  /**
   * {@code {cx, cy, r}} of a hole-free circular disc, or {@code null}.
   * Unwraps a single-member {@code MultiSurface}. Uses
   * {@link Geometry#getGeometryType()} and {@link Geometry#getBoundary()}
   * so the shell type is visible without importing jts-curve.
   *
   * @param g a geometry
   * @return {@code {cx, cy, r}} or {@code null}
   */
  public static double[] circularDisc(Geometry g) {
    if (g == null) return null;
    if ("MultiSurface".equals(g.getGeometryType())) {
      if (g.getNumGeometries() != 1) return null;
      return circularDisc(g.getGeometryN(0));
    }
    if (!"CurvePolygon".equals(g.getGeometryType())) return null;
    Polygon p = (Polygon) g;
    if (p.isEmpty() || p.getNumInteriorRing() > 0) return null;
    Geometry shell = p.getBoundary();
    if (!"CircularString".equals(shell.getGeometryType())) return null;
    return fullCircle(shell);
  }

  private static double[] fullCircle(Geometry ring) {
    if (ring.isEmpty() || !((LineString) ring).isClosed()
        || ring.getNumPoints() < 5) {
      return null;
    }
    Coordinate[] seq = ring.getCoordinates();
    double[] found = null;
    double sweep = 0.0;
    for (int i = 0; i + 2 < seq.length; i += 2) {
      double[] c = circumcircle(seq[i], seq[i + 1], seq[i + 2]);
      if (c == null) return null;
      if (found == null) {
        found = c;
      } else if (Math.hypot(found[0] - c[0], found[1] - c[1]) > 1.0e-9
          || Math.abs(found[2] - c[2]) > 1.0e-9) {
        return null;
      }
      sweep += signedSweep(seq[i], seq[i + 1], seq[i + 2], c);
    }
    if (found == null || Math.abs(Math.abs(sweep) - TWO_PI) > SWEEP_EPS) {
      return null;
    }
    return found;
  }

  static double signedSweep(Coordinate start, Coordinate mid,
      Coordinate end, double[] c) {
    double a0 = Math.atan2(start.y - c[1], start.x - c[0]);
    double aMid = Math.atan2(mid.y - c[1], mid.x - c[0]);
    double a1 = Math.atan2(end.y - c[1], end.x - c[0]);
    boolean ccw = normPos(aMid - a0) < normPos(a1 - a0);
    double sweep = ccw ? normPos(a1 - a0) : -normPos(a0 - a1);
    if (sweep == 0.0) sweep = ccw ? TWO_PI : -TWO_PI;
    return sweep;
  }

  private static final double TWO_PI = 2.0 * Math.PI;
  private static final double SWEEP_EPS = 1.0e-9;

  private static double normPos(double angle) {
    angle = angle % TWO_PI;
    if (angle < 0.0) angle += TWO_PI;
    return angle;
  }

  static void circleToCircle(double c1x, double c1y, double r1,
      double c2x, double c2y, double r2, PointPairDistance dest) {
    double d = Math.hypot(c1x - c2x, c1y - c2y);
    if (d == 0.0) {
      dest.setMaximum(new Coordinate(c1x + r1, c1y),
          new Coordinate(c2x + r2, c2y));
      return;
    }
    double ux = (c1x - c2x) / d;
    double uy = (c1y - c2y) / d;
    Coordinate far = new Coordinate(c1x + r1 * ux, c1y + r1 * uy);
    Coordinate farN = new Coordinate(c2x + r2 * ux, c2y + r2 * uy);
    Coordinate near = new Coordinate(c1x - r1 * ux, c1y - r1 * uy);
    double ndx = near.x - c2x;
    double ndy = near.y - c2y;
    double nlen = Math.hypot(ndx, ndy);
    Coordinate nearN = nlen == 0.0
        ? new Coordinate(c2x + r2, c2y)
        : new Coordinate(c2x + r2 * ndx / nlen, c2y + r2 * ndy / nlen);
    double farD = Math.abs(d + r1 - r2);
    double nearD = Math.abs(Math.abs(d - r1) - r2);
    if (farD >= nearD) dest.setMaximum(far, farN);
    else dest.setMaximum(near, nearN);
  }

  private static void arcToSegment(Coordinate start, Coordinate mid,
      Coordinate end, Coordinate seg0, Coordinate seg1,
      PointPairDistance dest) {
    consider(start, nearestOnSegment(start, seg0, seg1), dest);
    consider(end, nearestOnSegment(end, seg0, seg1), dest);
    double[] c = circumcircle(start, mid, end);
    if (c == null) return;
    double sx = seg1.x - seg0.x;
    double sy = seg1.y - seg0.y;
    double slen = Math.hypot(sx, sy);
    if (slen > 0.0) {
      double nx = -sy / slen;
      double ny = sx / slen;
      for (int sign = -1; sign <= 1; sign += 2) {
        Coordinate q = new Coordinate(c[0] + sign * c[2] * nx,
            c[1] + sign * c[2] * ny);
        if (isOnSweep(q, c, start, mid, end)
            && projectionOnSegment(q, seg0, seg1)) {
          consider(q, nearestOnSegment(q, seg0, seg1), dest);
        }
      }
    }
    considerArcToEndpoint(c, start, mid, end, seg0, seg1, dest);
    considerArcToEndpoint(c, start, mid, end, seg1, seg0, dest);
  }

  private static void considerArcToEndpoint(double[] c, Coordinate start,
      Coordinate mid, Coordinate end, Coordinate endpoint, Coordinate other,
      PointPairDistance dest) {
    Coordinate[] cand = new Coordinate[] { start, end };
    double dx = endpoint.x - c[0];
    double dy = endpoint.y - c[1];
    double dist = Math.hypot(dx, dy);
    if (dist > 0.0) {
      cand = new Coordinate[] {
          start, end,
          new Coordinate(c[0] + c[2] * dx / dist, c[1] + c[2] * dy / dist),
          new Coordinate(c[0] - c[2] * dx / dist, c[1] - c[2] * dy / dist)
      };
    }
    for (int i = 0; i < cand.length; i++) {
      Coordinate p = cand[i];
      if (p != start && p != end && !isOnSweep(p, c, start, mid, end)) continue;
      Coordinate nearest = nearestOnSegment(p, endpoint, other);
      if (nearest.distance(endpoint) > 1.0e-12) continue;
      consider(p, nearest, dest);
    }
  }

  private static void consider(Coordinate onFrom, Coordinate onTo,
      PointPairDistance dest) {
    dest.setMaximum(onFrom, onTo);
  }

  private static boolean isOnSweep(Coordinate p, double[] c, Coordinate start,
      Coordinate mid, Coordinate end) {
    double a0 = Math.atan2(start.y - c[1], start.x - c[0]);
    double aMid = Math.atan2(mid.y - c[1], mid.x - c[0]);
    double a1 = Math.atan2(end.y - c[1], end.x - c[0]);
    boolean ccw = normPos(aMid - a0) < normPos(a1 - a0);
    double sweep = ccw ? normPos(a1 - a0) : normPos(a0 - a1);
    if (sweep == 0.0) sweep = TWO_PI;
    double angle = Math.atan2(p.y - c[1], p.x - c[0]);
    double travelled = ccw ? normPos(angle - a0) : normPos(a0 - angle);
    return travelled <= sweep + 1.0e-12;
  }

  private static Coordinate nearestOnSegment(Coordinate p, Coordinate a,
      Coordinate b) {
    double vx = b.x - a.x;
    double vy = b.y - a.y;
    double len2 = vx * vx + vy * vy;
    if (len2 == 0.0) return new Coordinate(a);
    double t = ((p.x - a.x) * vx + (p.y - a.y) * vy) / len2;
    if (t <= 0.0) return new Coordinate(a);
    if (t >= 1.0) return new Coordinate(b);
    return new Coordinate(a.x + t * vx, a.y + t * vy);
  }

  private static boolean projectionOnSegment(Coordinate p, Coordinate a,
      Coordinate b) {
    double vx = b.x - a.x;
    double vy = b.y - a.y;
    double len2 = vx * vx + vy * vy;
    if (len2 == 0.0) return false;
    double t = ((p.x - a.x) * vx + (p.y - a.y) * vy) / len2;
    return t >= 0.0 && t <= 1.0;
  }

  /**
   * Circumcircle of three points as {@code {cx, cy, r}}, or {@code null}
   * if the triple is colinear or coincident.
   *
   * @param a first point
   * @param b second point
   * @param c third point
   * @return {@code {cx, cy, r}} or {@code null}
   */
  public static double[] circumcircle(Coordinate a, Coordinate b, Coordinate c) {
    if (Orientation.index(a, b, c) == Orientation.COLLINEAR) return null;
    double ax = a.x, ay = a.y;
    double bx = b.x, by = b.y;
    double cx = c.x, cy = c.y;
    double d = 2.0 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by));
    if (d == 0.0) return null;
    double ax2ay2 = ax * ax + ay * ay;
    double bx2by2 = bx * bx + by * by;
    double cx2cy2 = cx * cx + cy * cy;
    double ux = (ax2ay2 * (by - cy) + bx2by2 * (cy - ay) + cx2cy2 * (ay - by)) / d;
    double uy = (ax2ay2 * (cx - bx) + bx2by2 * (ax - cx) + cx2cy2 * (bx - ax)) / d;
    double r = Math.hypot(ax - ux, ay - uy);
    if (!Double.isFinite(r) || r == 0.0) return null;
    return new double[] { ux, uy, r };
  }

  private static class MaxPointDistanceFilter
      implements CoordinateFilter
  {
    private PointPairDistance maxPtDist = new PointPairDistance();
    private PointPairDistance minPtDist = new PointPairDistance();
    private Geometry geom;

    public MaxPointDistanceFilter(Geometry geom)
    {
      this.geom = geom;
    }

    public void filter(Coordinate pt)
    {
      minPtDist.initialize();
      DistanceToPoint.computeDistance(geom, pt, minPtDist);
      maxPtDist.setMaximum(minPtDist);
    }

    public PointPairDistance getMaxPointDistance() { return maxPtDist; }
  }
  
  private static class MaxDensifiedByFractionDistanceFilter 
  implements CoordinateSequenceFilter 
  {
    private PointPairDistance maxPtDist = new PointPairDistance();
    private PointPairDistance minPtDist = new PointPairDistance();
    private Geometry geom;
    private int numSubSegs = 0;
  
    public MaxDensifiedByFractionDistanceFilter(Geometry geom, double fraction) {
      this.geom = geom;
      numSubSegs = (int) Math.rint(1.0/fraction);
    }
  
    public void filter(CoordinateSequence seq, int index) 
    {
      /**
       * This logic also handles skipping Point geometries
       */
      if (index == 0)
        return;
      
      Coordinate p0 = seq.getCoordinate(index - 1);
      Coordinate p1 = seq.getCoordinate(index);
      
      double delx = (p1.x - p0.x)/numSubSegs;
      double dely = (p1.y - p0.y)/numSubSegs;
  
      for (int i = 0; i < numSubSegs; i++) {
        double x = p0.x + i*delx;
        double y = p0.y + i*dely;
        Coordinate pt = new Coordinate(x, y);
        minPtDist.initialize();
        DistanceToPoint.computeDistance(geom, pt, minPtDist);
        maxPtDist.setMaximum(minPtDist);  
      }
      
      
    }
  
    public boolean isGeometryChanged() { return false; }
    
    public boolean isDone() { return false; }
    
    public PointPairDistance getMaxPointDistance() {
      return maxPtDist;
    }
  }

}
