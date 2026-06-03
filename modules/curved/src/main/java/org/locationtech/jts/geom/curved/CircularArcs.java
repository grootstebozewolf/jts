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

import org.locationtech.jts.geom.Coordinate;

/**
 * Shared analytical primitives for circular arcs defined by three control
 * points (start, mid, end), where {@code mid} lies on the arc between
 * {@code start} and {@code end}.
 *
 * <p>This consolidates the circumcircle and signed-sweep math that
 * arc-aware algorithms need. {@link CircularArcDensifier} currently keeps
 * its own equivalent private copies; new arc-analytic code (area, and the
 * forthcoming boundary / validity / centroid work) should depend on this
 * class so there is a single source of truth, and the densifier can be
 * migrated to delegate here in a later, separately-tested change.
 */
final class CircularArcs {

  private CircularArcs() {
  }

  /**
   * Circumcentre of the three control points as {@code [cx, cy]}, or
   * {@code null} when the points are colinear or coincident (no finite
   * circle through them).
   */
  static double[] circumcentre(Coordinate a, Coordinate b, Coordinate c) {
    double ax = a.x, ay = a.y;
    double bx = b.x, by = b.y;
    double cx = c.x, cy = c.y;
    double d = 2.0 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by));
    if (d == 0.0) return null;
    double a2 = ax * ax + ay * ay;
    double b2 = bx * bx + by * by;
    double c2 = cx * cx + cy * cy;
    double ux = (a2 * (by - cy) + b2 * (cy - ay) + c2 * (ay - by)) / d;
    double uy = (a2 * (cx - bx) + b2 * (ax - cx) + c2 * (bx - ax)) / d;
    if (!Double.isFinite(ux) || !Double.isFinite(uy)) return null;
    return new double[] { ux, uy };
  }

  /**
   * Signed sweep angle (radians) of the arc travelling from {@code start}
   * to {@code end} through {@code mid}, measured about the centre
   * {@code (cx, cy)}. Positive is counter-clockwise; the magnitude lies in
   * {@code (0, 2*PI]}.
   */
  static double signedSweep(double cx, double cy,
                            Coordinate start, Coordinate mid, Coordinate end) {
    double a0 = Math.atan2(start.y - cy, start.x - cx);
    double aMid = Math.atan2(mid.y - cy, mid.x - cx);
    double a1 = Math.atan2(end.y - cy, end.x - cx);
    boolean ccw = isMidInCcwSweep(a0, aMid, a1);
    double magnitude = ccw ? normalizePositive(a1 - a0) : normalizePositive(a0 - a1);
    if (magnitude == 0.0) magnitude = 2.0 * Math.PI;
    return ccw ? magnitude : -magnitude;
  }

  /** True when {@code mid} falls inside the counter-clockwise arc start to end. */
  private static boolean isMidInCcwSweep(double a0, double aMid, double a1) {
    double sweepCcw = normalizePositive(a1 - a0);
    double midOffset = normalizePositive(aMid - a0);
    return midOffset < sweepCcw;
  }

  /** Reduce an angle to the {@code [0, 2*PI)} range. */
  private static double normalizePositive(double angle) {
    double twoPi = 2.0 * Math.PI;
    angle = angle % twoPi;
    if (angle < 0.0) angle += twoPi;
    return angle;
  }
}
