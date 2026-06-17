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
   * Intersection points of the circular arc through {@code (s, m, e)} with the
   * line segment {@code (p, q)} (N-AL, JTS #1195). Returns each {@code [x, y]}
   * lying on both the segment ({@code 0 <= t <= 1}) and the arc's swept span
   * (the directed sweep start->mid->end). Returns 0, 1, or 2 points; empty for a
   * tangent miss, a degenerate segment, or a collinear (non-circular) arc.
   */
  static double[][] intersectSegment(double sx, double sy, double mx, double my, double ex, double ey,
                                     double px, double py, double qx, double qy) {
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (d == 0.0) return new double[0][];               // collinear arc: no circle
    double s2 = sx * sx + sy * sy, m2 = mx * mx + my * my, e2 = ex * ex + ey * ey;
    double cx = (s2 * (my - ey) + m2 * (ey - sy) + e2 * (sy - my)) / d;
    double cy = (s2 * (ex - mx) + m2 * (sx - ex) + e2 * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (!Double.isFinite(r) || r == 0.0) return new double[0][];

    // segment X(t) = p + t*(q-p); solve |X - C|^2 = r^2
    double dx = qx - px, dy = qy - py;
    double a = dx * dx + dy * dy;
    if (a == 0.0) return new double[0][];               // degenerate segment
    double fx = px - cx, fy = py - cy;
    double bb = 2 * (fx * dx + fy * dy);
    double cc = fx * fx + fy * fy - r * r;
    double disc = bb * bb - 4 * a * cc;
    if (disc < 0) return new double[0][];               // line misses circle
    double sq = Math.sqrt(disc);
    double[] ts = (disc == 0.0) ? new double[]{ -bb / (2 * a) }
                                : new double[]{ (-bb - sq) / (2 * a), (-bb + sq) / (2 * a) };

    double a0 = Math.atan2(sy - cy, sx - cx);
    double am = Math.atan2(my - cy, mx - cx);
    double ae = Math.atan2(ey - cy, ex - cx);
    boolean ccw = d > 0;
    double theta = directedSweep(a0, am, ccw) + directedSweep(am, ae, ccw);

    final double EPS = 1e-9;
    double[][] out = new double[ts.length][];
    int n = 0;
    for (double t : ts) {
      if (t < -EPS || t > 1 + EPS) continue;            // off the segment
      double x = px + t * dx, y = py + t * dy;
      double sweep = directedSweep(a0, Math.atan2(y - cy, x - cx), ccw);
      // on the arc span iff 0 <= sweep <= theta (allow tiny wrap just before start)
      if (sweep <= theta + EPS || sweep >= 2 * Math.PI - EPS) {
        out[n++] = new double[]{ x, y };
      }
    }
    if (n == out.length) return out;
    double[][] trimmed = new double[n][];
    System.arraycopy(out, 0, trimmed, 0, n);
    return trimmed;
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
