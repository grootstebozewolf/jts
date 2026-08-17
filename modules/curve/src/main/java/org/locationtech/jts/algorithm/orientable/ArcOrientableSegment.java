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
 * Lightweight Option B arc predicate on A's {@link ExactCircularArc}.
 * Side + intersect only — no length/area/centroid copy.
 */
public final class ArcOrientableSegment implements OrientableSegment {

  private static final double FILTER_EPS = 1.0e-12;

  private final ExactCircularArc exact;
  private final StraightOrientableSegment straight;

  public ArcOrientableSegment(Coordinate start, Coordinate mid, Coordinate end) {
    this(new ExactCircularArc(start, mid, end));
  }

  public ArcOrientableSegment(ExactCircularArc exact) {
    this.exact = exact;
    this.straight = exact.isArc()
        ? null
        : new StraightOrientableSegment(exact.getStart(), exact.getEnd());
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
    double cx = exact.centerX();
    double cy = exact.centerY();
    double r = exact.radius();
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
      return intersectsStraight(s) || endpointOnSegment(s);
    }
    if (other instanceof ArcOrientableSegment) {
      ArcOrientableSegment a = (ArcOrientableSegment) other;
      if (!a.exact.isArc()) {
        return intersects(a.straight);
      }
      return intersectsArc(a) || endpointOnArc(a);
    }
    return other.intersects(this);
  }

  private boolean intersectsStraight(StraightOrientableSegment s) {
    return ArcIntersects.segment(exact, s.getStart(), s.getEnd());
  }

  private boolean intersectsArc(ArcOrientableSegment a) {
    return ArcIntersects.arcs(exact, a.exact);
  }

  private static int signCross(double tx, double ty, double qx, double qy) {
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
