/*
 * Copyright (c) 2026 grootstebozewolf
 * Portions adapted from a 2020 contribution by Jeroen Bloemscheer
 * to a JTS fork (the `CIRCULARSTRING` branch).
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

import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.awt.PointShapeFactory;
import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.MultiCurve;

/**
 * A {@link ShapeWriter} that renders the OGC SFA / ISO 19125-2 curve
 * geometry types as cubic Bezier approximations of true circular arcs.
 *
 * <h3>Algorithm (CircularString)</h3>
 *
 * For each consecutive triple of control points (start, mid, end) — where
 * {@code mid} lies <em>on</em> the arc, not at a Bezier handle — the
 * implementation:
 *
 * <ol>
 *   <li>fits a circle through the three points (circumcircle);</li>
 *   <li>splits the arc into one or more sub-arcs of at most 120&deg;;</li>
 *   <li>approximates each sub-arc with a single cubic Bezier whose handle
 *       length is {@code (4/3)·tan(theta/4)·radius}, with handle directions
 *       along the arc tangent at the endpoints.</li>
 * </ol>
 *
 * The 120&deg; cap (3 segments per full circle, vs. the classic 90&deg;
 * 4-per-circle split) keeps the segment count low while staying
 * well within the visual tolerance of a screen pixel at typical zoom
 * levels.
 *
 * <p>Three colinear points fall through to a straight {@code lineTo}.
 *
 * <p>The math is performed in <em>model</em> coordinates and individual
 * Bezier control points are transformed to view coordinates only when
 * emitted, so the result is correct regardless of viewport Y-flip or
 * non-uniform scale (under uniform scale, the circle is preserved; under
 * anisotropic scale it degenerates as expected to an elliptical arc, with
 * the Bezier still tracking the transformed shape closely).
 */
public class CurvedShapeWriter extends ShapeWriter {

  /** One full circle is split into segments of at most this angle. */
  private static final double MAX_ARC_RADIANS = 2.0 * Math.PI / 3.0; // 120°

  public CurvedShapeWriter() {
    super();
  }

  public CurvedShapeWriter(PointTransformation pointTransformer) {
    super(pointTransformer);
  }

  public CurvedShapeWriter(PointTransformation pointTransformer, PointShapeFactory pointFactory) {
    super(pointTransformer, pointFactory);
  }

  @Override
  protected Shape toShapeOther(Geometry geometry) {
    if (geometry instanceof CircularString) return toShape((CircularString) geometry);
    if (geometry instanceof MultiCurve) return toShape((MultiCurve) geometry);
    return null;
  }

  private Shape toShape(MultiCurve mc) {
    GeneralPath path = new GeneralPath();
    for (int i = 0; i < mc.getNumGeometries(); i++) {
      Geometry member = mc.getGeometryN(i);
      if (member instanceof CircularString) {
        path.append(toShape((CircularString) member), false);
      } else if (member instanceof LineString) {
        path.append(toShape(member), false);
      }
    }
    return path;
  }

  private Shape toShape(CircularString cs) {
    GeneralPath path = new GeneralPath();
    if (cs.isEmpty()) return path;

    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    if (n < 3) {
      // Degenerate: render whatever points we have as a polyline.
      moveToView(path, seq.getCoordinate(0));
      for (int i = 1; i < n; i++) lineToView(path, seq.getCoordinate(i));
      return path;
    }

    moveToView(path, seq.getCoordinate(0));
    for (int i = 0; i + 2 < n; i += 2) {
      Coordinate start = seq.getCoordinate(i);
      Coordinate mid   = seq.getCoordinate(i + 1);
      Coordinate end   = seq.getCoordinate(i + 2);
      appendArc(path, start, mid, end);
    }
    return path;
  }

  /**
   * Appends one circular arc — defined by three points (start, mid, end)
   * with mid on the arc — to {@code path} as a sequence of cubic Beziers,
   * each covering at most {@link #MAX_ARC_RADIANS}.
   */
  private void appendArc(GeneralPath path, Coordinate start, Coordinate mid, Coordinate end) {
    Circle c = Circle.fromThreePoints(start, mid, end);
    if (c == null) {
      // Degenerate triple (colinear or coincident) — straight segment.
      lineToView(path, end);
      return;
    }

    double a0 = Math.atan2(start.y - c.cy, start.x - c.cx);
    double aMid = Math.atan2(mid.y - c.cy, mid.x - c.cx);
    double a1 = Math.atan2(end.y - c.cy, end.x - c.cx);

    // Determine sweep direction: the arc must pass through `mid`, so pick
    // whichever of CCW / CW puts `aMid` between `a0` and `a1`.
    boolean ccw = isMidInCcwSweep(a0, aMid, a1);
    double sweep = signedSweep(a0, a1, ccw); // (0, 2π]

    int segments = (int) Math.ceil(sweep / MAX_ARC_RADIANS);
    if (segments < 1) segments = 1;
    double delta = sweep / segments;
    if (!ccw) delta = -delta;

    double angle = a0;
    for (int i = 0; i < segments; i++) {
      double next = angle + delta;
      appendBezierForArc(path, c, angle, next);
      angle = next;
    }
  }

  /**
   * Cubic-Bezier approximation of a circular arc on {@code circle} from
   * {@code startAngle} to {@code endAngle} (signed sweep). Uses the
   * canonical {@code kappa = (4/3)·tan(theta/4)} handle-length factor.
   */
  private void appendBezierForArc(GeneralPath path, Circle circle, double startAngle, double endAngle) {
    double theta = endAngle - startAngle;
    double kappa = (4.0 / 3.0) * Math.tan(theta / 4.0);

    double sinA = Math.sin(startAngle);
    double cosA = Math.cos(startAngle);
    double sinB = Math.sin(endAngle);
    double cosB = Math.cos(endAngle);

    double r = circle.r;
    double cx = circle.cx, cy = circle.cy;

    // Endpoints on the circle.
    double p0x = cx + r * cosA;
    double p0y = cy + r * sinA;
    double p3x = cx + r * cosB;
    double p3y = cy + r * sinB;

    // Tangent direction at p0 is (-sinA, cosA) for CCW sweep; sign of
    // theta handles the CW case.
    double p1x = p0x - kappa * r * sinA;
    double p1y = p0y + kappa * r * cosA;
    double p2x = p3x + kappa * r * sinB;
    double p2y = p3y - kappa * r * cosB;

    Point2D c1 = transformPoint(new Coordinate(p1x, p1y));
    Point2D c2 = transformPoint(new Coordinate(p2x, p2y));
    Point2D c3 = transformPoint(new Coordinate(p3x, p3y));
    path.curveTo((float) c1.getX(), (float) c1.getY(),
                 (float) c2.getX(), (float) c2.getY(),
                 (float) c3.getX(), (float) c3.getY());
  }

  private void moveToView(GeneralPath path, Coordinate model) {
    Point2D v = transformPoint(model);
    path.moveTo((float) v.getX(), (float) v.getY());
  }

  private void lineToView(GeneralPath path, Coordinate model) {
    Point2D v = transformPoint(model);
    path.lineTo((float) v.getX(), (float) v.getY());
  }

  /** Returns true iff angle {@code aMid} lies strictly inside the CCW
   *  sweep from {@code a0} to {@code a1}. */
  private static boolean isMidInCcwSweep(double a0, double aMid, double a1) {
    double sweepCcw = Angle.normalizePositive(a1 - a0);
    double midOffset = Angle.normalizePositive(aMid - a0);
    return midOffset < sweepCcw;
  }

  /** Magnitude of the sweep from {@code a0} to {@code a1} in the
   *  specified direction, in (0, 2π]. */
  private static double signedSweep(double a0, double a1, boolean ccw) {
    double sweep = ccw
        ? Angle.normalizePositive(a1 - a0)
        : Angle.normalizePositive(a0 - a1);
    // Treat a "zero" sweep as a full circle (start == end means full loop).
    if (sweep == 0.0) sweep = 2.0 * Math.PI;
    return sweep;
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
