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
   * Signed area contribution of the circular segment between the arc (through
   * the mid point) and its chord, for the boundary area integral
   * {@code (1/2) * integral(x dy - y dx)}: positive when the arc bulges to the
   * left of the directed chord {@code start -> end}, negative when it bulges to
   * the right. The magnitude is {@code (r^2/2)(theta - sin theta)} with
   * {@code theta} the (possibly reflex) central angle, so it is correct for
   * major arcs too. Collinear/degenerate triples contribute zero.
   */
  static double signedSegmentArea(double sx, double sy, double mx, double my, double ex, double ey) {
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (d == 0.0) return 0.0;
    double s2 = sx * sx + sy * sy;
    double m2 = mx * mx + my * my;
    double e2 = ex * ex + ey * ey;
    double cx = (s2 * (my - ey) + m2 * (ey - sy) + e2 * (sy - my)) / d;
    double cy = (s2 * (ex - mx) + m2 * (sx - ex) + e2 * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (!Double.isFinite(r) || r == 0.0) return 0.0;
    double a0 = Math.atan2(sy - cy, sx - cx);
    double am = Math.atan2(my - cy, mx - cx);
    double ae = Math.atan2(ey - cy, ex - cx);
    boolean ccw = d > 0;
    double theta = directedSweep(a0, am, ccw) + directedSweep(am, ae, ccw);
    double magnitude = 0.5 * r * r * (theta - Math.sin(theta));
    // Sign = orientation of (start, mid, end): the loop start->arc->end->chord is
    // CCW (positive area) exactly when (start, mid, end) turns left. This is the
    // segment's signed contribution to (1/2) * integral(x dy - y dx).
    double orient = (mx - sx) * (ey - sy) - (my - sy) * (ex - sx);
    double signed = Math.signum(orient) * magnitude;
    return Double.isFinite(signed) ? signed : 0.0;
  }

  /**
   * Centroid {@code [x, y]} of the circular segment between the arc (through the
   * mid point) and its chord: the region whose signed area is
   * {@link #signedSegmentArea}. It lies on the bisector at distance
   * {@code 4 r sin^3(theta/2) / (3 (theta - sin theta))} from the centre, towards
   * the arc. Collinear/degenerate (zero-area) triples return the chord midpoint.
   */
  static double[] segmentCentroid(double sx, double sy, double mx, double my, double ex, double ey) {
    double[] mid = { 0.5 * (sx + ex), 0.5 * (sy + ey) };
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (d == 0.0) return mid;
    double s2 = sx * sx + sy * sy, m2 = mx * mx + my * my, e2 = ex * ex + ey * ey;
    double cx = (s2 * (my - ey) + m2 * (ey - sy) + e2 * (sy - my)) / d;
    double cy = (s2 * (ex - mx) + m2 * (sx - ex) + e2 * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (!Double.isFinite(r) || r == 0.0) return mid;
    double a0 = Math.atan2(sy - cy, sx - cx);
    double am = Math.atan2(my - cy, mx - cx);
    double ae = Math.atan2(ey - cy, ex - cx);
    boolean ccw = d > 0;
    double theta = directedSweep(a0, am, ccw) + directedSweep(am, ae, ccw);
    double denom = theta - Math.sin(theta);
    if (Math.abs(denom) < 1e-15) return mid;          // theta ~ 0: zero-area segment
    double half = 0.5 * theta;
    double sh = Math.sin(half);
    double segDist = 4.0 * r * sh * sh * sh / (3.0 * denom);
    double midAngle = a0 + (ccw ? half : -half);
    double x = cx + segDist * Math.cos(midAngle);
    double y = cy + segDist * Math.sin(midAngle);
    return (Double.isFinite(x) && Double.isFinite(y)) ? new double[] { x, y } : mid;
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
