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
package org.locationtech.jts.algorithm.exactarc;

import org.locationtech.jts.geom.Coordinate;

/**
 * Proofs #64 {@code AngleBetween}: signed short sweep via
 * {@code atan2(cross, dot)}, then mid-point long/short disambiguation
 * ({@code arc_sweep}, Proofs #261).
 * <p>
 * One place for the transcendental. {@link ExactCircularArc} and
 * callers must not re-roll {@code % 2π} locally.
 */
public final class AngleBetween {

  static final double TWO_PI = 2.0 * Math.PI;

  private AngleBetween() { }

  /**
   * Signed short angle from {@code u} to {@code v} in {@code (-π, π]}.
   * {@code atan2(u×v, u·v)}.
   */
  public static double signedShort(double ux, double uy, double vx, double vy) {
    return Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
  }

  /**
   * Positive directed sweep in {@code (0, 2π]} through {@code mid}.
   * A zero short-angle with a distinct mid is a full turn.
   */
  public static double directedSweep(double cx, double cy,
      Coordinate start, Coordinate mid, Coordinate end) {
    double a0 = Math.atan2(start.y - cy, start.x - cx);
    double aMid = Math.atan2(mid.y - cy, mid.x - cx);
    double a1 = Math.atan2(end.y - cy, end.x - cx);
    return directedSweepFromAngles(a0, aMid, a1);
  }

  public static boolean isCcw(double a0, double aMid, double a1) {
    return normalizePositive(aMid - a0) < normalizePositive(a1 - a0);
  }

  public static double directedSweepFromAngles(double a0, double aMid,
      double a1) {
    boolean ccw = isCcw(a0, aMid, a1);
    double s = ccw
        ? normalizePositive(a1 - a0)
        : normalizePositive(a0 - a1);
    return s == 0.0 ? TWO_PI : s;
  }

  public static double normalizePositive(double angle) {
    angle = angle % TWO_PI;
    if (angle < 0.0) {
      angle += TWO_PI;
    }
    return angle;
  }
}
