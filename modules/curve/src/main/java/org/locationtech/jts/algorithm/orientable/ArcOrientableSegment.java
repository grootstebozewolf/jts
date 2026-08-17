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
package org.locationtech.jts.algorithm.orientable;

import org.locationtech.jts.algorithm.CGAlgorithmsDD;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.algorithm.exactcurve.ExactCircularArc;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.curve.ArcIntersects;

/**
 * Package-private adapter that <b>composes</b> {@link ExactCircularArc}.
 * Does not re-derive circumcircle or sweep. Construct via
 * {@link OrientableSegments#arc(ExactCircularArc)}.
 */
final class ArcOrientableSegment implements OrientableSegment {

  private final ExactCircularArc exact;
  private final StraightOrientableSegment straight;

  ArcOrientableSegment(ExactCircularArc exact) {
    this.exact = exact;
    this.straight = exact.isArc()
        ? null
        : new StraightOrientableSegment(exact.getStart(), exact.getEnd());
  }

  /** Package access for tests in this package. */
  ExactCircularArc exactArc() {
    return exact;
  }

  public Coordinate getStart() {
    return exact.getStart();
  }

  public Coordinate getEnd() {
    return exact.getEnd();
  }

  public double length() {
    return exact.length();
  }

  /**
   * Side of {@code q} relative to the directed arc.
   * <p>
   * Radial projection onto the circle, clamped to an endpoint if the
   * projected point is off-sweep; then the directed tangent frame at
   * that foot. If {@code q} coincides with the circle centre
   * ({@code dist == 0}), the tangent frame is undefined — returns
   * {@link Orientation#COLLINEAR} (documented sentinel, not “on the arc”).
   */
  public int orientationIndex(Coordinate q) {
    if (!exact.isArc()) {
      return straight.orientationIndex(q);
    }
    double cx = exact.centerX();
    double cy = exact.centerY();
    double r = exact.radius();
    double dx = q.x - cx;
    double dy = q.y - cy;
    double dist = Math.hypot(dx, dy);
    if (dist == 0.0) {
      // Centre: no unique radial projection / tangent.
      return Orientation.COLLINEAR;
    }
    double ox = cx + r * dx / dist;
    double oy = cy + r * dy / dist;
    if (!exact.isOnSweep(ox, oy)) {
      Coordinate s = exact.getStart();
      Coordinate e = exact.getEnd();
      if (q.distance(s) <= q.distance(e)) {
        ox = s.x;
        oy = s.y;
      }
      else {
        ox = e.x;
        oy = e.y;
      }
    }
    double tx = -(oy - cy);
    double ty = ox - cx;
    if (!exact.isCcw()) {
      tx = -tx;
      ty = -ty;
    }
    return signCross(tx, ty, q.x - ox, q.y - oy);
  }

  public boolean intersects(OrientableSegment other) {
    if (!exact.isArc()) {
      return straight.intersects(other);
    }
    if (other instanceof StraightOrientableSegment) {
      StraightOrientableSegment s = (StraightOrientableSegment) other;
      return ArcIntersects.segment(exact, s.getStart(), s.getEnd())
          || endpointOnSegment(s);
    }
    if (other instanceof ArcOrientableSegment) {
      ArcOrientableSegment a = (ArcOrientableSegment) other;
      if (!a.exact.isArc()) {
        return intersects(a.straight);
      }
      return ArcIntersects.arcs(exact, a.exact) || endpointOnArc(a);
    }
    return other.intersects(this);
  }

  /**
   * Filter then DD. Absolute floor is scaled by the tangent magnitude so
   * large coordinates do not force every near-zero cross through float
   * noise before DD (Orientation-family style).
   */
  private static int signCross(double tx, double ty, double qx, double qy) {
    double cross = tx * qy - ty * qx;
    double scale = Math.hypot(tx, ty) * Math.hypot(qx, qy);
    double eps = 1.0e-12 * (scale > 1.0 ? scale : 1.0);
    if (cross > eps) {
      return Orientation.COUNTERCLOCKWISE;
    }
    if (cross < -eps) {
      return Orientation.CLOCKWISE;
    }
    int s = CGAlgorithmsDD.signOfDet2x2(tx, ty, qx, qy);
    if (s > 0) {
      return Orientation.COUNTERCLOCKWISE;
    }
    if (s < 0) {
      return Orientation.CLOCKWISE;
    }
    return Orientation.COLLINEAR;
  }

  private boolean endpointOnSegment(StraightOrientableSegment s) {
    RobustLineIntersector li = new RobustLineIntersector();
    Coordinate a = exact.getStart();
    Coordinate b = exact.getEnd();
    li.computeIntersection(a, a, s.getStart(), s.getEnd());
    if (li.hasIntersection()) {
      return true;
    }
    li.computeIntersection(b, b, s.getStart(), s.getEnd());
    return li.hasIntersection();
  }

  /**
   * Endpoint extras for densify-bridge misses. Radial tolerance matches
   * one ulp of {@code r} so constructed endpoints are not rejected by a
   * strict {@code 0.0} {@link ExactCircularArc#inArc} band.
   */
  private boolean endpointOnArc(ArcOrientableSegment a) {
    ExactCircularArc o = a.exact;
    double tol = Math.ulp(Math.max(exact.radius(), o.radius()));
    if (tol == 0.0) {
      tol = Math.ulp(1.0);
    }
    return o.inArc(exact.getStart(), tol)
        || o.inArc(exact.getEnd(), tol)
        || exact.inArc(o.getStart(), tol)
        || exact.inArc(o.getEnd(), tol);
  }
}
