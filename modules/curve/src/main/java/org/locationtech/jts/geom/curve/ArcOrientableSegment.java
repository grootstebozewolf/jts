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
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.algorithm.orientable.OrientableSegment;
import org.locationtech.jts.algorithm.orientable.StraightOrientableSegment;
import org.locationtech.jts.geom.Coordinate;

/**
 * Circular 3-control arc window — Proofs Option B arc carrier.
 * Orientation uses the tangent frame at the nearest point on the arc
 * (not the chord). Intersection uses
 * {@link CircularArcDensifier#intersectSegmentCircle} + sweep filter.
 * Colinear / degenerate triples degrade to {@link StraightOrientableSegment}.
 */
public final class ArcOrientableSegment implements OrientableSegment {

  private final Coordinate start;
  private final Coordinate mid;
  private final Coordinate end;
  private final CircularArcDensifier.Circle circle;
  private final boolean ccw;
  private final StraightOrientableSegment chordFallback;

  public ArcOrientableSegment(Coordinate start, Coordinate mid, Coordinate end) {
    this.start = start;
    this.mid = mid;
    this.end = end;
    this.circle = CircularArcDensifier.Circle.fromThreePoints(start, mid, end);
    if (this.circle == null) {
      this.ccw = true;
      this.chordFallback = new StraightOrientableSegment(start, end);
    }
    else {
      double a0 = Math.atan2(start.y - circle.cy, start.x - circle.cx);
      double aM = Math.atan2(mid.y - circle.cy, mid.x - circle.cx);
      double a1 = Math.atan2(end.y - circle.cy, end.x - circle.cx);
      this.ccw = normPos(aM - a0) < normPos(a1 - a0);
      this.chordFallback = null;
    }
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

  public boolean isArc() {
    return circle != null;
  }

  public int orientationIndex(Coordinate q) {
    if (chordFallback != null) {
      return chordFallback.orientationIndex(q);
    }
    Coordinate on = CircularArcDensifier.nearestPointOnArc(q, start, mid, end);
    // Tangent to the circle at on, oriented along the directed sweep.
    double tx = -(on.y - circle.cy);
    double ty = on.x - circle.cx;
    if (!ccw) {
      tx = -tx;
      ty = -ty;
    }
    double cx = q.x - on.x;
    double cy = q.y - on.y;
    double cross = tx * cy - ty * cx;
    if (cross > 0.0) {
      return Orientation.COUNTERCLOCKWISE;
    }
    if (cross < 0.0) {
      return Orientation.CLOCKWISE;
    }
    return Orientation.COLLINEAR;
  }

  public boolean intersects(OrientableSegment other) {
    if (chordFallback != null) {
      return chordFallback.intersects(other);
    }
    if (other instanceof StraightOrientableSegment) {
      return intersectsStraight((StraightOrientableSegment) other);
    }
    if (other instanceof ArcOrientableSegment) {
      return intersectsArc((ArcOrientableSegment) other);
    }
    return other.intersects(this);
  }

  private boolean intersectsStraight(StraightOrientableSegment s) {
    Coordinate[] hits = CircularArcDensifier.intersectSegmentCircle(
        circle, s.getStart(), s.getEnd());
    for (int i = 0; i < hits.length; i++) {
      if (onSweep(hits[i]) && onSegment(hits[i], s.getStart(), s.getEnd())) {
        return true;
      }
    }
    // Endpoint-on-arc / arc-end-on-segment
    if (onSegment(start, s.getStart(), s.getEnd())
        || onSegment(end, s.getStart(), s.getEnd())) {
      return true;
    }
    if (orientationIndex(s.getStart()) == Orientation.COLLINEAR
        && onSweep(s.getStart())) {
      return true;
    }
    if (orientationIndex(s.getEnd()) == Orientation.COLLINEAR
        && onSweep(s.getEnd())) {
      return true;
    }
    return false;
  }

  private boolean intersectsArc(ArcOrientableSegment o) {
    if (o.chordFallback != null) {
      return intersectsStraight(o.chordFallback);
    }
    // Same circle: sweep overlap.
    if (Math.hypot(circle.cx - o.circle.cx, circle.cy - o.circle.cy) <= 1.0e-9
        && Math.abs(circle.r - o.circle.r) <= 1.0e-9) {
      return sweepsOverlap(o);
    }
    // Two circles: up to 2 nodes, must lie on both sweeps.
    Coordinate[] nodes = circleCircleNodes(circle, o.circle);
    for (int i = 0; i < nodes.length; i++) {
      if (onSweep(nodes[i]) && o.onSweep(nodes[i])) {
        return true;
      }
    }
    return false;
  }

  private boolean sweepsOverlap(ArcOrientableSegment o) {
    // Sample mid of this sweep; also check ends.
    if (o.onSweep(start) || o.onSweep(end) || o.onSweep(mid)) {
      return true;
    }
    if (onSweep(o.start) || onSweep(o.end) || onSweep(o.mid)) {
      return true;
    }
    return false;
  }

  private static Coordinate[] circleCircleNodes(
      CircularArcDensifier.Circle a, CircularArcDensifier.Circle b) {
    double dx = b.cx - a.cx;
    double dy = b.cy - a.cy;
    double d = Math.hypot(dx, dy);
    if (d > a.r + b.r + 1.0e-12 || d < Math.abs(a.r - b.r) - 1.0e-12
        || d == 0.0) {
      return new Coordinate[0];
    }
    double x = (d * d + a.r * a.r - b.r * b.r) / (2.0 * d);
    double h2 = a.r * a.r - x * x;
    if (h2 < 0.0 && h2 > -1.0e-12) {
      h2 = 0.0;
    }
    if (h2 < 0.0) {
      return new Coordinate[0];
    }
    double h = Math.sqrt(h2);
    double mx = a.cx + x * dx / d;
    double my = a.cy + x * dy / d;
    if (h == 0.0) {
      return new Coordinate[] { new Coordinate(mx, my) };
    }
    double rx = -dy * h / d;
    double ry = dx * h / d;
    return new Coordinate[] {
        new Coordinate(mx + rx, my + ry),
        new Coordinate(mx - rx, my - ry)
    };
  }

  boolean onSweep(Coordinate p) {
    if (circle == null) {
      return false;
    }
    double a0 = Math.atan2(start.y - circle.cy, start.x - circle.cx);
    double a1 = Math.atan2(end.y - circle.cy, end.x - circle.cx);
    double ap = Math.atan2(p.y - circle.cy, p.x - circle.cx);
    double sweep = ccw ? normPos(a1 - a0) : normPos(a0 - a1);
    if (sweep == 0.0) {
      sweep = 2.0 * Math.PI;
    }
    double travelled = ccw ? normPos(ap - a0) : normPos(a0 - ap);
    return travelled <= sweep + 1.0e-12;
  }

  public Coordinate[] densifyControls(int nChord) {
    if (chordFallback != null || nChord < 2) {
      return new Coordinate[] { start.copy(), end.copy() };
    }
    Coordinate[] pts = new Coordinate[nChord + 1];
    double a0 = Math.atan2(start.y - circle.cy, start.x - circle.cx);
    double a1 = Math.atan2(end.y - circle.cy, end.x - circle.cx);
    double sweep = ccw ? normPos(a1 - a0) : -normPos(a0 - a1);
    if (sweep == 0.0) {
      sweep = ccw ? 2.0 * Math.PI : -2.0 * Math.PI;
    }
    for (int i = 0; i <= nChord; i++) {
      double t = (double) i / (double) nChord;
      double ang = a0 + t * sweep;
      pts[i] = new Coordinate(
          circle.cx + circle.r * Math.cos(ang),
          circle.cy + circle.r * Math.sin(ang));
    }
    return pts;
  }

  /**
   * Densify-reference orientation: nearest chord of an n-gon, then
   * {@link Orientation#index}.
   */
  public int densifyOrientationIndex(Coordinate q, int nChord) {
    Coordinate[] pts = densifyControls(nChord);
    double best = Double.POSITIVE_INFINITY;
    int bestOri = Orientation.COLLINEAR;
    for (int i = 1; i < pts.length; i++) {
      Coordinate a = pts[i - 1];
      Coordinate b = pts[i];
      Coordinate foot = closestOnSeg(q, a, b);
      double d = q.distance(foot);
      if (d < best) {
        best = d;
        bestOri = Orientation.index(a, b, q);
      }
    }
    return bestOri;
  }

  public boolean densifyIntersectsStraight(StraightOrientableSegment s,
      int nChord) {
    Coordinate[] pts = densifyControls(nChord);
    RobustLineIntersector li = new RobustLineIntersector();
    for (int i = 1; i < pts.length; i++) {
      li.computeIntersection(pts[i - 1], pts[i], s.getStart(), s.getEnd());
      if (li.hasIntersection()) {
        return true;
      }
    }
    return false;
  }

  private static Coordinate closestOnSeg(Coordinate p, Coordinate a,
      Coordinate b) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double len2 = dx * dx + dy * dy;
    if (len2 == 0.0) {
      return a.copy();
    }
    double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2;
    if (t < 0.0) {
      t = 0.0;
    }
    else if (t > 1.0) {
      t = 1.0;
    }
    return new Coordinate(a.x + t * dx, a.y + t * dy);
  }

  private static boolean onSegment(Coordinate p, Coordinate a, Coordinate b) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double len2 = dx * dx + dy * dy;
    if (len2 == 0.0) {
      return p.equals2D(a);
    }
    double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2;
    if (t < -1.0e-12 || t > 1.0 + 1.0e-12) {
      return false;
    }
    Coordinate proj = new Coordinate(a.x + t * dx, a.y + t * dy);
    return p.distance(proj) <= 1.0e-9;
  }

  private static double normPos(double angle) {
    double twoPi = 2.0 * Math.PI;
    angle = angle % twoPi;
    if (angle < 0.0) {
      angle += twoPi;
    }
    return angle;
  }
}
