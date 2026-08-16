/*
 * Copyright (c) 2026 grootstebozewolf
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.algorithm.construct;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Typed distance from a query point to a set of LEC obstacles.
 * <p>
 * Collections are flattened. Each component is measured with the
 * metric that matches its kind: Euclidean for points, facet /
 * interior-zero for linear rings and polygons, point-to-arc for
 * {@code CircularString} windows, and the closed disc formulas for
 * a circular disc (filled) or a full-circle ring (circumference).
 * A {@code CompoundCurve} is the min over its members.
 * <p>
 * Package-private on purpose: not a user-facing type. Detection uses
 * {@link Geometry#getGeometryType()}, {@link Geometry#getBoundary()},
 * {@link DiscreteHausdorffDistance#circularDisc(Geometry)} and
 * {@link DiscreteHausdorffDistance#circularRing(Geometry)} so this
 * class does not import jts-curve.
 *
 * @author JTS
 */
class ObstacleDistance {

  private final List<Component> components = new ArrayList<Component>();

  ObstacleDistance(Geometry obstacles) {
    flatten(obstacles);
  }

  /**
   * Distance from {@code pt} to the nearest obstacle. Interior of a
   * filled polygonal / disc obstacle is 0.
   *
   * @param pt the query point
   * @return the distance, or {@link Double#MAX_VALUE} if there are
   *     no components
   */
  public double distance(Point pt) {
    if (components.isEmpty()) {
      return Double.MAX_VALUE;
    }
    Coordinate q = pt.getCoordinate();
    double min = Double.POSITIVE_INFINITY;
    for (int i = 0; i < components.size(); i++) {
      double d = components.get(i).distance(pt, q);
      if (d < min) {
        min = d;
      }
    }
    return min;
  }

  /**
   * Nearest locations between the obstacles and {@code pt}.
   * The first coordinate lies on an obstacle; the second is
   * {@code pt}.
   *
   * @param pt the query point
   * @return a pair of coordinates
   */
  public Coordinate[] nearestPoints(Point pt) {
    Coordinate q = pt.getCoordinate();
    if (components.isEmpty()) {
      return new Coordinate[] { q.copy(), q.copy() };
    }
    double min = Double.POSITIVE_INFINITY;
    Coordinate onObs = q;
    for (int i = 0; i < components.size(); i++) {
      Coordinate cand = components.get(i).nearestOnObstacle(pt, q);
      double d = q.distance(cand);
      if (d < min) {
        min = d;
        onObs = cand;
      }
    }
    return new Coordinate[] { onObs, q.copy() };
  }

  private void flatten(Geometry g) {
    if (g == null || g.isEmpty()) {
      return;
    }
    double[] disc = DiscreteHausdorffDistance.circularDisc(g);
    if (disc != null) {
      components.add(new DiscComponent(disc[0], disc[1], disc[2], true));
      return;
    }
    String type = g.getGeometryType();
    if ("CompoundCurve".equals(type)) {
      flattenCompound(g);
      return;
    }
    if (isCollection(g, type)) {
      for (int i = 0; i < g.getNumGeometries(); i++) {
        flatten(g.getGeometryN(i));
      }
      return;
    }
    if ("CircularString".equals(type)) {
      addCircularString(g);
      return;
    }
    if ("CurvePolygon".equals(type)) {
      flatten(g.getBoundary());
      return;
    }
    if (g instanceof Point) {
      components.add(new PointComponent(g.getCoordinate()));
      return;
    }
    if (g instanceof Polygon || g instanceof LineString) {
      components.add(new FacetComponent(g));
      return;
    }
    components.add(new FacetComponent(g));
  }

  private static boolean isCollection(Geometry g, String type) {
    return g.getNumGeometries() > 1
        || "GeometryCollection".equals(type)
        || "MultiPoint".equals(type)
        || "MultiLineString".equals(type)
        || "MultiPolygon".equals(type)
        || "MultiCurve".equals(type)
        || "MultiSurface".equals(type);
  }

  /**
   * {@code CompoundCurve} exposes members as {@code getNumMembers} /
   * {@code getMemberN} rather than {@code getNumGeometries}. Invoked
   * reflectively so jts-core does not import jts-curve. Core-only
   * tests never construct a CompoundCurve, so the fallback is unused
   * there.
   */
  private void flattenCompound(Geometry g) {
    try {
      int n = ((Integer) g.getClass().getMethod("getNumMembers")
          .invoke(g)).intValue();
      Method memberN = g.getClass().getMethod("getMemberN", int.class);
      if (n == 0) {
        return;
      }
      for (int i = 0; i < n; i++) {
        flatten((Geometry) memberN.invoke(g, Integer.valueOf(i)));
      }
    }
    catch (ReflectiveOperationException ex) {
      components.add(new FacetComponent(g));
    }
  }

  private void addCircularString(Geometry g) {
    double[] ring = DiscreteHausdorffDistance.circularRing(g);
    if (ring != null) {
      components.add(new DiscComponent(ring[0], ring[1], ring[2], false));
      return;
    }
    Coordinate[] pts = g.getCoordinates();
    for (int i = 0; i + 2 < pts.length; i += 2) {
      components.add(new ArcComponent(pts[i], pts[i + 1], pts[i + 2]));
    }
  }

  private interface Component {
    double distance(Point pt, Coordinate q);
    Coordinate nearestOnObstacle(Point pt, Coordinate q);
  }

  private static final class PointComponent implements Component {
    private final Coordinate c;

    PointComponent(Coordinate c) {
      this.c = c;
    }

    public double distance(Point pt, Coordinate q) {
      return q.distance(c);
    }

    public Coordinate nearestOnObstacle(Point pt, Coordinate q) {
      return c.copy();
    }
  }

  private static final class FacetComponent implements Component {
    private final IndexedDistanceToPoint index;

    FacetComponent(Geometry g) {
      this.index = new IndexedDistanceToPoint(g);
    }

    public double distance(Point pt, Coordinate q) {
      return index.distance(pt);
    }

    public Coordinate nearestOnObstacle(Point pt, Coordinate q) {
      return index.nearestPoints(pt)[0];
    }
  }

  /**
   * Circular disc. Filled ({@code CurvePolygon} / single-member
   * {@code MultiSurface}): {@code max(0, |p-c|-r)}. Linear ring
   * (standalone full-circle {@code CircularString}):
   * {@code ||p-c|-r|}.
   */
  private static final class DiscComponent implements Component {
    private final double cx;
    private final double cy;
    private final double r;
    private final boolean filled;

    DiscComponent(double cx, double cy, double r, boolean filled) {
      this.cx = cx;
      this.cy = cy;
      this.r = r;
      this.filled = filled;
    }

    public double distance(Point pt, Coordinate q) {
      double d = Math.hypot(q.x - cx, q.y - cy);
      if (filled) {
        return Math.max(0.0, d - r);
      }
      return Math.abs(d - r);
    }

    public Coordinate nearestOnObstacle(Point pt, Coordinate q) {
      double dx = q.x - cx;
      double dy = q.y - cy;
      double d = Math.hypot(dx, dy);
      if (filled && d <= r) {
        return q.copy();
      }
      if (d == 0.0) {
        return new Coordinate(cx + r, cy);
      }
      return new Coordinate(cx + r * dx / d, cy + r * dy / d);
    }
  }

  /**
   * One 3-control circular-arc window. Colinear triples degrade to
   * the chord. Not the control-point polyline.
   */
  private static final class ArcComponent implements Component {
    private final Coordinate start;
    private final Coordinate mid;
    private final Coordinate end;
    private final double[] circle;

    ArcComponent(Coordinate start, Coordinate mid, Coordinate end) {
      this.start = start;
      this.mid = mid;
      this.end = end;
      this.circle = DiscreteHausdorffDistance.circumcircle(start, mid, end);
    }

    public double distance(Point pt, Coordinate q) {
      return q.distance(nearestPointOnArc(q, start, mid, end, circle));
    }

    public Coordinate nearestOnObstacle(Point pt, Coordinate q) {
      return nearestPointOnArc(q, start, mid, end, circle);
    }
  }

  /**
   * Closest point on the arc through {@code start, mid, end} to
   * {@code p}. Same algorithm as
   * {@code CircularArcDensifier.nearestPointOnArc}, using
   * {@link DiscreteHausdorffDistance#circumcircle} so the formula
   * lives in core.
   */
  static Coordinate nearestPointOnArc(Coordinate p, Coordinate start,
      Coordinate mid, Coordinate end) {
    return nearestPointOnArc(p, start, mid, end,
        DiscreteHausdorffDistance.circumcircle(start, mid, end));
  }

  private static Coordinate nearestPointOnArc(Coordinate p, Coordinate start,
      Coordinate mid, Coordinate end, double[] c) {
    if (c == null) {
      return nearestOnSegment(p, start, end);
    }
    double dx = p.x - c[0];
    double dy = p.y - c[1];
    double dist = Math.hypot(dx, dy);
    Coordinate onCircle;
    if (dist == 0.0) {
      onCircle = new Coordinate(start);
    }
    else {
      onCircle = new Coordinate(c[0] + c[2] * dx / dist,
          c[1] + c[2] * dy / dist);
    }
    if (isOnSweep(onCircle, c, start, mid, end)) {
      return onCircle;
    }
    return p.distance(start) <= p.distance(end)
        ? new Coordinate(start) : new Coordinate(end);
  }

  private static boolean isOnSweep(Coordinate p, double[] c,
      Coordinate start, Coordinate mid, Coordinate end) {
    double a0 = Math.atan2(start.y - c[1], start.x - c[0]);
    double aMid = Math.atan2(mid.y - c[1], mid.x - c[0]);
    double a1 = Math.atan2(end.y - c[1], end.x - c[0]);
    boolean ccw = normPos(aMid - a0) < normPos(a1 - a0);
    double sweep = ccw ? normPos(a1 - a0) : normPos(a0 - a1);
    if (sweep == 0.0) {
      sweep = 2.0 * Math.PI;
    }
    double angle = Math.atan2(p.y - c[1], p.x - c[0]);
    double travelled = ccw ? normPos(angle - a0) : normPos(a0 - angle);
    return travelled <= sweep + 1.0e-12;
  }

  private static double normPos(double angle) {
    double twoPi = 2.0 * Math.PI;
    angle = angle % twoPi;
    if (angle < 0.0) {
      angle += twoPi;
    }
    return angle;
  }

  private static Coordinate nearestOnSegment(Coordinate p, Coordinate a,
      Coordinate b) {
    double vx = b.x - a.x;
    double vy = b.y - a.y;
    double len2 = vx * vx + vy * vy;
    if (len2 == 0.0) {
      return new Coordinate(a);
    }
    double t = ((p.x - a.x) * vx + (p.y - a.y) * vy) / len2;
    if (t <= 0.0) {
      return new Coordinate(a);
    }
    if (t >= 1.0) {
      return new Coordinate(b);
    }
    return new Coordinate(a.x + t * vx, a.y + t * vy);
  }
}
