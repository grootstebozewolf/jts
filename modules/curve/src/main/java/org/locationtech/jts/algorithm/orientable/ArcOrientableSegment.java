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
import org.locationtech.jts.algorithm.exactcurve.AngleBetween;
import org.locationtech.jts.algorithm.exactcurve.ExactCircularArc;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.curve.ArcGeometry;

/**
 * Lightweight Proofs Option B arc carrier on top of A's
 * {@link ExactCircularArc}. Owns only side + intersect; length / sweep /
 * area / centroid stay on the A front-end.
 * <p>
 * Hot path snapshots public A getters once (no second circumcircle).
 * Side: filter → {@link CGAlgorithmsDD#signOfDet2x2}. Sweep tests use
 * {@link AngleBetween#travelled}.
 */
public final class ArcOrientableSegment implements OrientableSegment {

  private static final double FILTER_EPS = 1.0e-12;

  private final ExactCircularArc exact;
  private final StraightOrientableSegment straight;
  /** Snapshot of A public geometry for allocation-light side tests. */
  private final double cx;
  private final double cy;
  private final double r;
  private final boolean ccw;
  private final double sweep;
  private final double startUx;
  private final double startUy;

  public ArcOrientableSegment(Coordinate start, Coordinate mid, Coordinate end) {
    this(new ExactCircularArc(start, mid, end));
  }

  /**
   * Wrap an existing A-team arc (preferred when the caller already has one).
   */
  public ArcOrientableSegment(ExactCircularArc exact) {
    this.exact = exact;
    if (!exact.isArc()) {
      this.straight = new StraightOrientableSegment(exact.getStart(), exact.getEnd());
      this.cx = Double.NaN;
      this.cy = Double.NaN;
      this.r = 0.0;
      this.ccw = true;
      this.sweep = 0.0;
      this.startUx = 0.0;
      this.startUy = 0.0;
      return;
    }
    this.straight = null;
    Coordinate c = exact.center();
    this.cx = c.x;
    this.cy = c.y;
    this.r = exact.radius();
    this.ccw = exact.isCcw();
    this.sweep = exact.sweep();
    Coordinate s = exact.getStart();
    this.startUx = s.x - cx;
    this.startUy = s.y - cy;
  }

  public ExactCircularArc exactArc() {
    return exact;
  }

  public Coordinate getStart() {
    return exact.getStart();
  }

  public Coordinate getMid() {
    return exact.getMid();
  }

  public Coordinate getEnd() {
    return exact.getEnd();
  }

  public boolean isCircular() {
    return exact.isArc();
  }

  public int orientationIndex(Coordinate q) {
    if (!exact.isArc()) {
      return straight.orientationIndex(q);
    }
    double dx = q.x - cx;
    double dy = q.y - cy;
    double dist = Math.hypot(dx, dy);
    double ox;
    double oy;
    if (dist == 0.0) {
      ox = exact.getStart().x;
      oy = exact.getStart().y;
    }
    else {
      ox = cx + r * dx / dist;
      oy = cy + r * dy / dist;
      if (!onSweep(ox - cx, oy - cy)) {
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
    }
    double tx = -(oy - cy);
    double ty = ox - cx;
    if (!ccw) {
      tx = -tx;
      ty = -ty;
    }
    double qx = q.x - ox;
    double qy = q.y - oy;
    double cross = tx * qy - ty * qx;
    if (cross > FILTER_EPS) {
      return Orientation.COUNTERCLOCKWISE;
    }
    if (cross < -FILTER_EPS) {
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

  public boolean intersects(OrientableSegment other) {
    if (!exact.isArc()) {
      return straight.intersects(other);
    }
    if (other instanceof StraightOrientableSegment) {
      StraightOrientableSegment s = (StraightOrientableSegment) other;
      if (ArcGeometry.intersectsSegment(
          exact.getStart(), exact.getMid(), exact.getEnd(),
          s.getStart(), s.getEnd())) {
        return true;
      }
      return endpointOnSegment(s);
    }
    if (other instanceof ArcOrientableSegment) {
      ArcOrientableSegment a = (ArcOrientableSegment) other;
      if (!a.exact.isArc()) {
        return intersects(a.straight);
      }
      return ArcGeometry.intersectsArc(
          exact.getStart(), exact.getMid(), exact.getEnd(),
          a.exact.getStart(), a.exact.getMid(), a.exact.getEnd())
          || endpointOnArc(a);
    }
    return other.intersects(this);
  }

  private boolean onSweep(double ux, double uy) {
    double travelled = AngleBetween.travelled(ccw, startUx, startUy, ux, uy);
    return travelled <= sweep + Math.ulp(sweep);
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

  private boolean endpointOnArc(ArcOrientableSegment a) {
    ExactCircularArc o = a.exact;
    return o.inArc(exact.getStart(), 0.0)
        || o.inArc(exact.getEnd(), 0.0)
        || exact.inArc(o.getStart(), 0.0)
        || exact.inArc(o.getEnd(), 0.0);
  }
}
