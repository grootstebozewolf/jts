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
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (d == 0.0) return chord;

    double s2 = sx * sx + sy * sy;
    double m2 = mx * mx + my * my;
    double e2 = ex * ex + ey * ey;
    double cx = (s2 * (my - ey) + m2 * (ey - sy) + e2 * (sy - my)) / d;
    double cy = (s2 * (ex - mx) + m2 * (sx - ex) + e2 * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (!Double.isFinite(r) || r == 0.0) return chord;

    double a0 = Math.atan2(sy - cy, sx - cx);
    double am = Math.atan2(my - cy, mx - cx);
    double ae = Math.atan2(ey - cy, ex - cx);
    boolean ccw = d > 0;
    double theta = directedSweep(a0, am, ccw) + directedSweep(am, ae, ccw);
    double len = r * theta;
    return Double.isFinite(len) ? len : chord;
  }

  /**
   * The arc through {@code (s, m, e)} offset radially by signed distance {@code d}
   * (OFF, JTS #1195): the concentric arc with the same centre and angular sweep
   * and radius {@code r + d}, returned as its three control points (each original
   * control point pushed radially to the new radius). Returns {@code null} when
   * the offset collapses ({@code r + d <= 0}) or the triple is collinear (no
   * circle). A {@code CircularString} offset to one side uses {@code +d} and the
   * other side {@code -d} — the R±d parallel arcs.
   *
   * <p>Pinned against the {@code ARC_OFFSET_XY} oracle in
   * NetTopologySuite.Proofs.
   */
  static double[] offsetArc(double sx, double sy, double mx, double my, double ex, double ey, double d) {
    double det = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (det == 0.0) return null;
    double s2 = sx*sx+sy*sy, m2 = mx*mx+my*my, e2 = ex*ex+ey*ey;
    double cx = (s2*(my-ey) + m2*(ey-sy) + e2*(sy-my)) / det;
    double cy = (s2*(ex-mx) + m2*(sx-ex) + e2*(mx-sx)) / det;
    double r = Math.hypot(sx - cx, sy - cy);
    if (!Double.isFinite(r) || r == 0.0) return null;
    double rn = r + d;
    if (rn <= 0.0) return null;
    double k = rn / r;
    return new double[]{
        cx + (sx-cx)*k, cy + (sy-cy)*k,
        cx + (mx-cx)*k, cy + (my-cy)*k,
        cx + (ex-cx)*k, cy + (ey-cy)*k };
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
