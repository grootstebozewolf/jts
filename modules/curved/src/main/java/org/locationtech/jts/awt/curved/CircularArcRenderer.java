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
package org.locationtech.jts.awt.curved;

import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;

import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.geom.Coordinate;

/**
 * Renders circular arcs as sequences of cubic Bezier segments using a
 * 120&deg;-cap split with the canonical {@code (4/3)·tan(theta/4)}
 * handle-length formula. Used by {@link CurvedShapeWriter} for finished
 * geometry rendering and by drawing-tool previews so both produce
 * identical visual output.
 *
 * <p>The math is performed in model coordinates and individual Bezier
 * control points are transformed to view coordinates only when emitted,
 * via the supplied {@link PointTransformation}. This keeps the result
 * correct under viewport Y-flip and under uniform scale.
 */
public final class CircularArcRenderer {

  /** Maximum sweep covered by a single cubic Bezier sub-segment. */
  public static final double MAX_ARC_RADIANS = 2.0 * Math.PI / 3.0; // 120°

  private CircularArcRenderer() {
  }

  /**
   * Appends one circular arc — defined by three points (start, mid, end)
   * with mid on the arc — to {@code path} as a sequence of cubic Beziers,
   * each spanning at most {@link #MAX_ARC_RADIANS}. The path is assumed
   * to already be at {@code start} (via a previous {@code moveTo} or
   * {@code lineTo}); only {@code curveTo} (or a fallback {@code lineTo}
   * for degenerate triples) calls are emitted.
   *
   * @param path  destination path; must already be at the start point
   * @param start arc start (model coords)
   * @param mid   point on the arc between start and end (model coords)
   * @param end   arc end (model coords)
   * @param pt    transformation applied to each emitted control point
   */
  public static void appendArc(GeneralPath path,
                               Coordinate start, Coordinate mid, Coordinate end,
                               PointTransformation pt) {
    Circle c = Circle.fromThreePoints(start, mid, end);
    if (c == null) {
      Point2D v = transform(pt, end);
      path.lineTo((float) v.getX(), (float) v.getY());
      return;
    }

    double a0 = Math.atan2(start.y - c.cy, start.x - c.cx);
    double aMid = Math.atan2(mid.y - c.cy, mid.x - c.cx);
    double a1 = Math.atan2(end.y - c.cy, end.x - c.cx);

    boolean ccw = isMidInCcwSweep(a0, aMid, a1);
    double sweep = signedSweep(a0, a1, ccw);

    int segments = (int) Math.ceil(sweep / MAX_ARC_RADIANS);
    if (segments < 1) segments = 1;
    double delta = sweep / segments;
    if (!ccw) delta = -delta;

    double angle = a0;
    for (int i = 0; i < segments; i++) {
      double next = angle + delta;
      appendBezierForArc(path, c, angle, next, pt);
      angle = next;
    }
  }

  private static void appendBezierForArc(GeneralPath path, Circle circle,
                                         double startAngle, double endAngle,
                                         PointTransformation pt) {
    double theta = endAngle - startAngle;
    double kappa = (4.0 / 3.0) * Math.tan(theta / 4.0);

    double sinA = Math.sin(startAngle);
    double cosA = Math.cos(startAngle);
    double sinB = Math.sin(endAngle);
    double cosB = Math.cos(endAngle);

    double r = circle.r;
    double cx = circle.cx, cy = circle.cy;

    double p0x = cx + r * cosA;
    double p0y = cy + r * sinA;
    double p3x = cx + r * cosB;
    double p3y = cy + r * sinB;

    double p1x = p0x - kappa * r * sinA;
    double p1y = p0y + kappa * r * cosA;
    double p2x = p3x + kappa * r * sinB;
    double p2y = p3y - kappa * r * cosB;

    Point2D c1 = transform(pt, new Coordinate(p1x, p1y));
    Point2D c2 = transform(pt, new Coordinate(p2x, p2y));
    Point2D c3 = transform(pt, new Coordinate(p3x, p3y));
    path.curveTo((float) c1.getX(), (float) c1.getY(),
                 (float) c2.getX(), (float) c2.getY(),
                 (float) c3.getX(), (float) c3.getY());
  }

  private static Point2D transform(PointTransformation pt, Coordinate model) {
    Point2D out = new Point2D.Double();
    pt.transform(model, out);
    return out;
  }

  /** True iff {@code aMid} lies strictly inside the CCW sweep from
   *  {@code a0} to {@code a1}. */
  private static boolean isMidInCcwSweep(double a0, double aMid, double a1) {
    double sweepCcw = normalizePositive(a1 - a0);
    double midOffset = normalizePositive(aMid - a0);
    return midOffset < sweepCcw;
  }

  /** Magnitude of the sweep from {@code a0} to {@code a1} in the
   *  specified direction, in (0, 2π]. */
  private static double signedSweep(double a0, double a1, boolean ccw) {
    double sweep = ccw
        ? normalizePositive(a1 - a0)
        : normalizePositive(a0 - a1);
    if (sweep == 0.0) sweep = 2.0 * Math.PI;
    return sweep;
  }

  private static double normalizePositive(double angle) {
    double twoPi = 2.0 * Math.PI;
    angle = angle % twoPi;
    if (angle < 0.0) angle += twoPi;
    return angle;
  }

  /** A circle in model space, defined by center and radius. */
  private static final class Circle {
    final double cx, cy, r;

    private Circle(double cx, double cy, double r) {
      this.cx = cx;
      this.cy = cy;
      this.r = r;
    }

    /** Returns the circumcircle of three points, or {@code null} if the
     *  points are colinear or coincident. */
    static Circle fromThreePoints(Coordinate a, Coordinate b, Coordinate c) {
      double ax = a.x, ay = a.y;
      double bx = b.x, by = b.y;
      double cx = c.x, cy = c.y;
      double d = 2.0 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by));
      if (d == 0.0) return null;
      double ax2ay2 = ax * ax + ay * ay;
      double bx2by2 = bx * bx + by * by;
      double cx2cy2 = cx * cx + cy * cy;
      double ux = (ax2ay2 * (by - cy) + bx2by2 * (cy - ay) + cx2cy2 * (ay - by)) / d;
      double uy = (ax2ay2 * (cx - bx) + bx2by2 * (ax - cx) + cx2cy2 * (bx - ax)) / d;
      double r = Math.hypot(ax - ux, ay - uy);
      if (!Double.isFinite(r) || r == 0.0) return null;
      return new Circle(ux, uy, r);
    }
  }
}
