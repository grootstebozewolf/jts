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
package org.locationtech.jts.algorithm.exactcurve;

import org.locationtech.jts.geom.Coordinate;

/**
 * Sweep owner for {@link ExactCircularArc}: signed short via
 * {@code atan2(cross, dot)}, then mid-point long/short disambiguation.
 * <p>
 * One place for the transcendental. Callers must not re-roll
 * {@code % 2π} locally — including {@code CircularArcDensifier}.
 * Relative {@code atan2(u×v, u·v)} is used instead of subtracting two
 * absolute {@code atan2}s, so the {@code ±π} branch cut does not
 * collapse a tiny crossing into a false full turn.
 */
public final class AngleBetween {

  public static final double TWO_PI = 2.0 * Math.PI;

  private AngleBetween() { }

  /**
   * Signed short angle from {@code u} to {@code v} in {@code (-π, π]}.
   * {@code atan2(u×v, u·v)}.
   */
  public static double signedShort(double ux, double uy, double vx, double vy) {
    return Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
  }

  /**
   * Signed short from {@code from} to {@code to}, both measured from
   * {@code (cx, cy)}.
   */
  public static double signedShort(double cx, double cy, Coordinate from,
      Coordinate to) {
    return signedShort(from.x - cx, from.y - cy, to.x - cx, to.y - cy);
  }

  /**
   * Directed sweep through {@code mid}: orientation and magnitude
   * together, so they cannot desynchronize.
   */
  public static DirectedSweep through(double cx, double cy,
      Coordinate start, Coordinate mid, Coordinate end) {
    return fromShorts(
        signedShort(cx, cy, start, end),
        signedShort(cx, cy, start, mid));
  }

  /**
   * Same decision as {@link #through} when the caller already has
   * absolute angles. Reconstructs unit vectors so the branch cut of
   * {@code atan2} is not re-introduced by subtracting those angles.
   */
  public static DirectedSweep throughAngles(double a0, double aMid, double a1) {
    return fromShorts(
        signedShort(Math.cos(a0), Math.sin(a0), Math.cos(a1), Math.sin(a1)),
        signedShort(Math.cos(a0), Math.sin(a0), Math.cos(aMid), Math.sin(aMid)));
  }

  /**
   * Positive directed sweep in {@code (0, 2π]} through {@code mid}.
   * Allocation-free — used by the static {@code r·θ} path.
   */
  public static double directedSweep(double cx, double cy,
      Coordinate start, Coordinate mid, Coordinate end) {
    return sweepFromShorts(
        signedShort(cx, cy, start, end),
        signedShort(cx, cy, start, mid));
  }

  public static boolean isCcw(double cx, double cy,
      Coordinate start, Coordinate mid, Coordinate end) {
    return ccwFromShorts(
        signedShort(cx, cy, start, end),
        signedShort(cx, cy, start, mid));
  }

  public static boolean isCcw(double a0, double aMid, double a1) {
    return throughAngles(a0, aMid, a1).isCcw();
  }

  public static double directedSweepFromAngles(double a0, double aMid,
      double a1) {
    return throughAngles(a0, aMid, a1).radians();
  }

  /**
   * How far {@code p} has travelled from {@code start} along the
   * directed sweep, in {@code [0, 2π)}.
   */
  public static double travelled(boolean ccw, double ux, double uy,
      double px, double py) {
    double s = signedShort(ux, uy, px, py);
    return ccw ? normalizePositive(s) : normalizePositive(-s);
  }

  public static double travelledFromAngles(boolean ccw, double a0, double ap) {
    return ccw ? normalizePositive(ap - a0) : normalizePositive(a0 - ap);
  }

  public static double normalizePositive(double angle) {
    angle = angle % TWO_PI;
    if (angle < 0.0) {
      angle += TWO_PI;
    }
    return angle;
  }

  /**
   * Orientation plus magnitude of one 3-control window. Prefer this
   * over a bare {@code boolean} + {@code double} pair.
   */
  public static final class DirectedSweep {
    private final boolean ccw;
    private final double radians;

    DirectedSweep(boolean ccw, double radians) {
      this.ccw = ccw;
      this.radians = radians;
    }

    public boolean isCcw() {
      return ccw;
    }

    /** Central angle in {@code (0, 2π]}. */
    public double radians() {
      return radians;
    }

    /** {@code +θ} CCW, {@code −θ} CW. */
    public double signed() {
      return ccw ? radians : -radians;
    }
  }

  private static DirectedSweep fromShorts(double shortSE, double shortSM) {
    boolean ccw = ccwFromShorts(shortSE, shortSM);
    return new DirectedSweep(ccw, sweepFromShorts(shortSE, shortSM));
  }

  private static boolean ccwFromShorts(double shortSE, double shortSM) {
    return normalizePositive(shortSM) < normalizePositive(shortSE);
  }

  private static double sweepFromShorts(double shortSE, double shortSM) {
    double s = ccwFromShorts(shortSE, shortSM)
        ? normalizePositive(shortSE)
        : normalizePositive(-shortSE);
    return s == 0.0 ? TWO_PI : s;
  }
}
