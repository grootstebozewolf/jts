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
import org.locationtech.jts.algorithm.exactarc.AngleBetween;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.curve.ArcGeometry;

/**
 * Circular 3-control arc window — Proofs Option B (round 2).
 * <p>
 * Cached circumcircle + {@link AngleBetween} sweep (shared with A).
 * Side: filter-then-{@link CGAlgorithmsDD#signOfDet2x2} on the directed
 * tangent at the nearest arc point — same robustness family as
 * {@link Orientation}, no densify. Intersection via {@link ArcGeometry}.
 */
public final class ArcOrientableSegment implements OrientableSegment {

  /** Adaptive filter bound (same spirit as Orientation's filter). */
  private static final double FILTER_EPS = 1.0e-12;

  private final Coordinate start;
  private final Coordinate mid;
  private final Coordinate end;
  private final double cx;
  private final double cy;
  private final double r;
  private final double a0;
  private final boolean ccw;
  private final double sweep;
  private final boolean circular;
  private final StraightOrientableSegment straight;

  public ArcOrientableSegment(Coordinate start, Coordinate mid, Coordinate end) {
    this.start = start;
    this.mid = mid;
    this.end = end;
    double[] circ = ArcGeometry.circumcircle(start, mid, end);
    if (circ == null) {
      this.cx = Double.NaN;
      this.cy = Double.NaN;
      this.r = 0.0;
      this.a0 = 0.0;
      this.ccw = true;
      this.sweep = 0.0;
      this.circular = false;
      this.straight = new StraightOrientableSegment(start, end);
      return;
    }
    this.cx = circ[0];
    this.cy = circ[1];
    this.r = circ[2];
    this.a0 = Math.atan2(start.y - cy, start.x - cx);
    double aMid = Math.atan2(mid.y - cy, mid.x - cx);
    double a1 = Math.atan2(end.y - cy, end.x - cx);
    this.ccw = AngleBetween.isCcw(a0, aMid, a1);
    this.sweep = AngleBetween.directedSweepFromAngles(a0, aMid, a1);
    this.circular = true;
    this.straight = null;
  }

  public Coordinate getStart() {
    return start;
  }

  public Coordinate getMid() {
    return mid;
  }

  public Coordinate getEnd() {
    return end;
  }

  public boolean isCircular() {
    return circular;
  }

  public int orientationIndex(Coordinate q) {
    if (!circular) {
      return straight.orientationIndex(q);
    }
    double dx = q.x - cx;
    double dy = q.y - cy;
    double dist = Math.hypot(dx, dy);
    double ox;
    double oy;
    if (dist == 0.0) {
      ox = start.x;
      oy = start.y;
    }
    else {
      ox = cx + r * dx / dist;
      oy = cy + r * dy / dist;
      if (!onSweep(ox, oy)) {
        // Nearest is an endpoint of the window.
        if (q.distance(start) <= q.distance(end)) {
          ox = start.x;
          oy = start.y;
        }
        else {
          ox = end.x;
          oy = end.y;
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
    // Fast filter; DD only when the cross is near zero.
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
    if (!circular) {
      return straight.intersects(other);
    }
    if (other instanceof StraightOrientableSegment) {
      StraightOrientableSegment s = (StraightOrientableSegment) other;
      if (ArcGeometry.intersectsSegment(start, mid, end, s.getStart(), s.getEnd())) {
        return true;
      }
      return endpointOnSegment(s);
    }
    if (other instanceof ArcOrientableSegment) {
      ArcOrientableSegment a = (ArcOrientableSegment) other;
      if (!a.circular) {
        return intersects(a.straight);
      }
      return ArcGeometry.intersectsArc(start, mid, end, a.start, a.mid, a.end)
          || endpointOnArc(a);
    }
    return other.intersects(this);
  }

  private boolean onSweep(double x, double y) {
    double ap = Math.atan2(y - cy, x - cx);
    double travelled = ccw
        ? AngleBetween.normalizePositive(ap - a0)
        : AngleBetween.normalizePositive(a0 - ap);
    return travelled <= sweep + 1.0e-12;
  }

  private boolean endpointOnSegment(StraightOrientableSegment s) {
    RobustLineIntersector li = new RobustLineIntersector();
    li.computeIntersection(start, start, s.getStart(), s.getEnd());
    if (li.hasIntersection()) {
      return true;
    }
    li.computeIntersection(end, end, s.getStart(), s.getEnd());
    return li.hasIntersection();
  }

  private boolean endpointOnArc(ArcOrientableSegment a) {
    return ArcGeometry.distancePointToArc(start, a.start, a.mid, a.end) == 0.0
        || ArcGeometry.distancePointToArc(end, a.start, a.mid, a.end) == 0.0
        || ArcGeometry.distancePointToArc(a.start, start, mid, end) == 0.0
        || ArcGeometry.distancePointToArc(a.end, start, mid, end) == 0.0;
  }
}
