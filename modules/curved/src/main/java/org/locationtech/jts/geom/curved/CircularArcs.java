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
package org.locationtech.jts.geom.curved;

/**
 * Analytical helpers for single circular arcs defined by three control points
 * (start, mid, end), per the SQL/MM CIRCULARSTRING model.
 */
final class CircularArcs {

  private CircularArcs() {}

  /**
   * Length of the circular arc through the three control points, i.e.
   * {@code r * theta}. The mid point disambiguates which of the two arcs through
   * the endpoints is meant, so the result is correct for arcs up to a full turn.
   * Collinear (or otherwise degenerate) triples fall back to the chord length
   * {@code |end - start|}, matching the limiting behaviour as the radius grows.
   */
  static double arcLength(double sx, double sy, double mx, double my, double ex, double ey) {
    double chord = Math.hypot(ex - sx, ey - sy);
    // 2 * signed area of (s, m, e); zero iff the three points are collinear.
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (d == 0.0) return chord;

    double s2 = sx * sx + sy * sy;
    double m2 = mx * mx + my * my;
    double e2 = ex * ex + ey * ey;
    double cx = (s2 * (my - ey) + m2 * (ey - sy) + e2 * (sy - my)) / d;
    double cy = (s2 * (ex - mx) + m2 * (sx - ex) + e2 * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (!Double.isFinite(r) || r == 0.0) return chord;

    // Central angle accumulated in the arc's rotational direction (CCW iff the
    // signed area d > 0), going start -> mid -> end. Each step is the positive
    // turn in that direction, so a sub-arc that sweeps more than pi is measured
    // the long way round (an unsigned angle-between-radii would wrongly take the
    // short way). The total is the true sweep, valid up to a full turn.
    double a0 = Math.atan2(sy - cy, sx - cx);
    double am = Math.atan2(my - cy, mx - cx);
    double ae = Math.atan2(ey - cy, ex - cx);
    boolean ccw = d > 0;
    double theta = directedSweep(a0, am, ccw) + directedSweep(am, ae, ccw);
    double len = r * theta;
    return Double.isFinite(len) ? len : chord;
  }

  /**
   * Shortest distance from the point {@code (px, py)} to the circular arc through
   * {@code (s, m, e)} (D-PT, JTS #1195). The closest point on the full circle is
   * the foot of the radial through {@code P}; if that foot lies on the arc's
   * swept span the distance is {@code | |P - C| - r |}, otherwise the arc is
   * approached at its nearer endpoint. Collinear (degenerate) triples fall back
   * to the point-to-segment distance of the chord.
   */
  static double distancePointToArc(double sx, double sy, double mx, double my, double ex, double ey,
                                   double px, double py) {
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (d == 0.0) return distancePointToSegment(px, py, sx, sy, ex, ey);
    double s2 = sx * sx + sy * sy, m2 = mx * mx + my * my, e2 = ex * ex + ey * ey;
    double cx = (s2 * (my - ey) + m2 * (ey - sy) + e2 * (sy - my)) / d;
    double cy = (s2 * (ex - mx) + m2 * (sx - ex) + e2 * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (!Double.isFinite(r) || r == 0.0) return distancePointToSegment(px, py, sx, sy, ex, ey);

    double dist = Math.hypot(px - cx, py - cy);
    if (dist == 0.0) return r;                          // P at the centre: every arc point is r away
    double fx = cx + (px - cx) / dist * r;              // foot on the full circle nearest P
    double fy = cy + (py - cy) / dist * r;
    double a0 = Math.atan2(sy - cy, sx - cx);
    double am = Math.atan2(my - cy, mx - cx);
    double ae = Math.atan2(ey - cy, ex - cx);
    boolean ccw = d > 0;
    double theta = directedSweep(a0, am, ccw) + directedSweep(am, ae, ccw);
    double sweepF = directedSweep(a0, Math.atan2(fy - cy, fx - cx), ccw);
    final double EPS = 1e-12;
    if (sweepF <= theta + EPS || sweepF >= 2 * Math.PI - EPS) {
      return Math.abs(dist - r);                        // foot on the arc span
    }
    return Math.min(Math.hypot(px - sx, py - sy), Math.hypot(px - ex, py - ey));
  }

  /** Distance from {@code (px,py)} to the segment {@code (ax,ay)-(bx,by)}. */
  private static double distancePointToSegment(double px, double py, double ax, double ay, double bx, double by) {
    double dx = bx - ax, dy = by - ay;
    double l2 = dx * dx + dy * dy;
    if (l2 == 0.0) return Math.hypot(px - ax, py - ay);
    double t = ((px - ax) * dx + (py - ay) * dy) / l2;
    if (t < 0) t = 0; else if (t > 1) t = 1;
    return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
  }

  /** Positive angular turn from {@code from} to {@code to} in the given direction, in [0, 2*pi). */
  private static double directedSweep(double from, double to, boolean ccw) {
    double t = ccw ? (to - from) : (from - to);
    double twoPi = 2 * Math.PI;
    t %= twoPi;
    if (t < 0) t += twoPi;
    return t;
  }
}
