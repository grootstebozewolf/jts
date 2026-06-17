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

import java.math.BigInteger;

/**
 * Analytical helpers for single circular arcs defined by three control points
 * (start, mid, end), per the SQL/MM CIRCULARSTRING model.
 */
final class CircularArcs {

  private CircularArcs() {}

  /**
   * Outcome of snapping an arc's control points to a fixed grid (PRC-SN): the
   * snapped points are degenerate ({@link #DEGEN}), the arc survives because its
   * circumcentre also lands on the grid ({@link #PRESERVE}), or it must be
   * linearised because the snapped centre is off-grid ({@link #DENSIFY}).
   */
  enum SnapDecision { PRESERVE, DENSIFY, DEGEN }

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

  /** Positive angular turn from {@code from} to {@code to} in the given direction, in [0, 2*pi). */
  private static double directedSweep(double from, double to, boolean ccw) {
    double t = ccw ? (to - from) : (from - to);
    double twoPi = 2 * Math.PI;
    t %= twoPi;
    if (t < 0) t += twoPi;
    return t;
  }

  /**
   * Snaps an ordinate to the fixed grid of the given scale: {@code rint(v*scale)/scale}
   * (the same rounding as {@link org.locationtech.jts.geom.PrecisionModel} FIXED).
   */
  static double snapToScale(double v, double scale) {
    return Math.rint(v * scale) / scale;
  }

  /**
   * Classifies what happens to the circular arc through {@code (s, m, e)} when its
   * three control points are snapped to the fixed grid of the given integer
   * {@code scale} (PRC-SN, JTS #1195):
   * <ul>
   *   <li>{@link SnapDecision#DEGEN} — the snapped points are collinear/coincident
   *       (no circle), so there is no arc to preserve;</li>
   *   <li>{@link SnapDecision#PRESERVE} — the snapped arc's circumcentre also lands
   *       exactly on the grid, so its centre / radius / sweep are grid-representable
   *       and the arc survives snapping unchanged in identity;</li>
   *   <li>{@link SnapDecision#DENSIFY} — the snapped centre is off-grid, so the arc
   *       can no longer be represented exactly and must be linearised.</li>
   * </ul>
   * The on-grid test is exact: the snapped control points are integers in grid
   * units, so the circumcentre lies on the grid iff the determinant {@code d}
   * divides both centre numerators exactly. {@link BigInteger} avoids overflow and
   * matches the oracle's make-precise rational arithmetic with no tolerance.
   */
  static SnapDecision snapDecision(double sx, double sy, double mx, double my,
                                   double ex, double ey, long scale) {
    BigInteger gxs = grid(sx, scale), gys = grid(sy, scale);
    BigInteger gxm = grid(mx, scale), gym = grid(my, scale);
    BigInteger gxe = grid(ex, scale), gye = grid(ey, scale);
    // d = 2 * signed area of the snapped triple; zero iff collinear (degenerate).
    BigInteger d = gxs.multiply(gym.subtract(gye))
        .add(gxm.multiply(gye.subtract(gys)))
        .add(gxe.multiply(gys.subtract(gym)))
        .shiftLeft(1);
    if (d.signum() == 0) return SnapDecision.DEGEN;
    BigInteger s2 = gxs.multiply(gxs).add(gys.multiply(gys));
    BigInteger m2 = gxm.multiply(gxm).add(gym.multiply(gym));
    BigInteger e2 = gxe.multiply(gxe).add(gye.multiply(gye));
    BigInteger numCx = s2.multiply(gym.subtract(gye))
        .add(m2.multiply(gye.subtract(gys)))
        .add(e2.multiply(gys.subtract(gym)));
    BigInteger numCy = s2.multiply(gxe.subtract(gxm))
        .add(m2.multiply(gxs.subtract(gxe)))
        .add(e2.multiply(gxm.subtract(gxs)));
    boolean centreOnGrid = numCx.remainder(d).signum() == 0
        && numCy.remainder(d).signum() == 0;
    return centreOnGrid ? SnapDecision.PRESERVE : SnapDecision.DENSIFY;
  }

  /** Snapped ordinate in exact integer grid units: {@code round(v*scale)}. */
  private static BigInteger grid(double v, long scale) {
    return BigInteger.valueOf(Math.round(v * (double) scale));
  }
}
