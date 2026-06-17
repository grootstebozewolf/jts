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
   * Centroid {@code [x, y]} of the circular arc through the three control points
   * (the centre of mass of the 1-D arc). It lies on the angle bisector at
   * distance {@code r * sin(theta/2) / (theta/2)} from the centre, where
   * {@code theta} is the (possibly reflex) central angle. Collinear/degenerate
   * triples return the chord midpoint.
   */
  static double[] arcCentroid(double sx, double sy, double mx, double my, double ex, double ey) {
    double[] mid = { 0.5 * (sx + ex), 0.5 * (sy + ey) };
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (d == 0.0) return mid;
    double s2 = sx * sx + sy * sy;
    double m2 = mx * mx + my * my;
    double e2 = ex * ex + ey * ey;
    double cx = (s2 * (my - ey) + m2 * (ey - sy) + e2 * (sy - my)) / d;
    double cy = (s2 * (ex - mx) + m2 * (sx - ex) + e2 * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (!Double.isFinite(r) || r == 0.0) return mid;
    double a0 = Math.atan2(sy - cy, sx - cx);
    double am = Math.atan2(my - cy, mx - cx);
    double ae = Math.atan2(ey - cy, ex - cx);
    boolean ccw = d > 0;
    double theta = directedSweep(a0, am, ccw) + directedSweep(am, ae, ccw);
    double half = 0.5 * theta;
    if (half == 0.0) return mid;
    double dist = r * Math.sin(half) / half;       // arc centroid distance from centre
    double midAngle = a0 + (ccw ? half : -half);   // bisector direction (towards the arc)
    double x = cx + dist * Math.cos(midAngle);
    double y = cy + dist * Math.sin(midAngle);
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
