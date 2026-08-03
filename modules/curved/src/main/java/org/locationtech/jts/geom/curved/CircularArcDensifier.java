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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;

/**
 * Sagitta-based densification of a circular arc into a straight-chord
 * polyline. Used by {@link CircularString#toLinear(double)} (and by
 * any other curve type that needs an arc-correct linearisation).
 *
 * <p>Given an arc of radius {@code R} and a target chord-error
 * (sagitta) {@code ε}, a single sub-chord that spans angular sweep
 * {@code θ} has sagitta {@code R(1 − cos(θ/2))}. The densifier picks
 * the smallest integer number of equal-sweep sub-chords {@code n}
 * such that {@code R(1 − cos((sweep/n)/2)) ≤ ε}.
 *
 * <h3>Tolerance handling</h3>
 * <ul>
 *   <li>{@code tolerance < 0}: {@link IllegalArgumentException}.</li>
 *   <li>{@code tolerance == 0}: implementation default of
 *       {@code radius / 100} (1% of radius — visually clean and a
 *       reasonable spatial-op accuracy).</li>
 *   <li>{@code tolerance ≥ R}: 1 chord sufficient (the chord itself
 *       is closer than {@code R} to the arc everywhere).</li>
 *   <li>{@code tolerance ≥ 2R}: a degenerate "very loose" tolerance —
 *       still emits 1 chord.</li>
 * </ul>
 *
 * <h3>Degenerate triples</h3>
 * Three colinear (or coincident) control points cannot define a
 * circular arc. {@link #densifyArc} returns {@code [start, end]} —
 * a single straight chord — for these cases.
 */
public final class CircularArcDensifier {

  /** Default chord-error fraction of the arc radius (1%). */
  public static final double DEFAULT_TOLERANCE_FRACTION = 0.01;

  private CircularArcDensifier() {
  }

  /**
   * Densify a single arc defined by three control points (start, mid,
   * end) where {@code mid} lies on the arc. Returns the chord polyline
   * starting with {@code start} and ending with {@code end}.
   */
  public static List<Coordinate> densifyArc(Coordinate start, Coordinate mid, Coordinate end,
                                            double tolerance) {
    return densifyArc(start, mid, end, tolerance, Collections.<Coordinate>emptyList());
  }

  /**
   * Densify a single arc, also guaranteeing that every projection of a
   * {@code mustInclude} coordinate that lies within {@code tolerance}
   * of the arc appears in the output at its parametric position.
   *
   * @param mustInclude coordinates the caller wants to surface in the
   *                    polyline; entries further than {@code tolerance}
   *                    from the arc are silently dropped. {@code null}
   *                    is treated as empty.
   */
  public static List<Coordinate> densifyArc(Coordinate start, Coordinate mid, Coordinate end,
                                            double tolerance,
                                            List<Coordinate> mustInclude) {
    if (tolerance < 0.0) {
      throw new IllegalArgumentException("tolerance must be non-negative: " + tolerance);
    }

    Circle c = Circle.fromThreePoints(start, mid, end);
    if (c == null) {
      // Degenerate (colinear or coincident) — single straight chord.
      List<Coordinate> out = new ArrayList<Coordinate>(2);
      out.add(new Coordinate(start));
      out.add(new Coordinate(end));
      return out;
    }

    double effectiveTolerance = tolerance == 0.0
        ? c.r * DEFAULT_TOLERANCE_FRACTION
        : tolerance;

    double a0 = Math.atan2(start.y - c.cy, start.x - c.cx);
    double aMid = Math.atan2(mid.y - c.cy, mid.x - c.cx);
    double a1 = Math.atan2(end.y - c.cy, end.x - c.cx);

    boolean ccw = isMidInCcwSweep(a0, aMid, a1);
    double sweep = signedSweep(a0, a1, ccw);

    int segments = computeSegmentCount(c.r, sweep, effectiveTolerance);
    double delta = sweep / segments;
    if (!ccw) delta = -delta;

    // Collect projected angles for must-include points (in [0, sweep]).
    List<ProjectedPoint> projected = projectMustIncludeOntoArc(
        mustInclude, c, a0, sweep, ccw, effectiveTolerance);

    // Emit the chord polyline, interleaving projected points at their
    // sweep-relative angle.
    List<Coordinate> out = new ArrayList<Coordinate>(segments + 1 + projected.size());
    out.add(new Coordinate(start));

    int projIdx = 0;
    for (int i = 1; i <= segments; i++) {
      double sweepEnd = i * Math.abs(delta); // 0..sweep monotonically
      // Insert any projected points whose sweep-angle is < sweepEnd
      // before the next chord vertex.
      while (projIdx < projected.size()
             && projected.get(projIdx).sweepAngle < sweepEnd) {
        out.add(projected.get(projIdx).coord);
        projIdx++;
      }
      double angle = a0 + i * delta;
      out.add(new Coordinate(c.cx + c.r * Math.cos(angle),
                              c.cy + c.r * Math.sin(angle)));
    }
    // Any projected points exactly at the end are appended too (rare).
    while (projIdx < projected.size()) {
      out.add(projected.get(projIdx).coord);
      projIdx++;
    }

    // The last vertex ended up via the loop using cos/sin; replace with
    // the original `end` coordinate to avoid floating-point drift.
    out.set(out.size() - 1, new Coordinate(end));
    return out;
  }

  /** Pick the minimal integer segment count that keeps sagitta ≤ ε. */
  static int computeSegmentCount(double radius, double sweep, double tolerance) {
    if (tolerance >= radius) return 1;
    // Max sub-arc angle θ such that R(1 − cos(θ/2)) ≤ ε.
    double thetaMax = 2.0 * Math.acos(1.0 - tolerance / radius);
    if (!Double.isFinite(thetaMax) || thetaMax <= 0.0) return 1;
    int n = (int) Math.ceil(sweep / thetaMax);
    return n < 1 ? 1 : n;
  }

  private static boolean isMidInCcwSweep(double a0, double aMid, double a1) {
    double sweepCcw = normalizePositive(a1 - a0);
    double midOffset = normalizePositive(aMid - a0);
    return midOffset < sweepCcw;
  }

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

  /** Project every must-include point onto the arc; keep those within
   *  {@code tolerance} radial distance, then sort by sweep-angle so the
   *  caller can interleave them with the chord-vertex emission. */
  private static List<ProjectedPoint> projectMustIncludeOntoArc(
      List<Coordinate> mustInclude, Circle c,
      double a0, double sweep, boolean ccw, double tolerance) {
    if (mustInclude == null || mustInclude.isEmpty()) {
      return Collections.emptyList();
    }
    List<ProjectedPoint> out = new ArrayList<ProjectedPoint>();
    for (Coordinate p : mustInclude) {
      if (p == null) continue;
      double dx = p.x - c.cx;
      double dy = p.y - c.cy;
      double dist = Math.hypot(dx, dy);
      if (Math.abs(dist - c.r) > tolerance) continue;          // off-curve
      double angle = Math.atan2(dy, dx);
      double sweepAngle = ccw
          ? normalizePositive(angle - a0)
          : normalizePositive(a0 - angle);
      if (sweepAngle > sweep) continue;                        // outside sweep
      Coordinate projected = new Coordinate(
          c.cx + c.r * Math.cos(angle),
          c.cy + c.r * Math.sin(angle));
      out.add(new ProjectedPoint(sweepAngle, projected));
    }
    Collections.sort(out, new Comparator<ProjectedPoint>() {
      @Override
      public int compare(ProjectedPoint a, ProjectedPoint b) {
        return Double.compare(a.sweepAngle, b.sweepAngle);
      }
    });
    return out;
  }

  /** Internal: a must-include point projected onto the arc. */
  private static final class ProjectedPoint {
    final double sweepAngle;
    final Coordinate coord;

    ProjectedPoint(double sweepAngle, Coordinate coord) {
      this.sweepAngle = sweepAngle;
      this.coord = coord;
    }
  }

  /**
   * True arc length of the arc through three control points: {@code r * sweep}.
   * <p>
   * Degenerate (colinear or coincident) triples describe no arc, so the result
   * degrades to the straight-line distance {@code start..end} -- the same
   * fallback {@link #densifyArc} makes.
   *
   * @return the arc length, never negative
   */
  public static double arcLength(Coordinate start, Coordinate mid, Coordinate end) {
    Circle c = Circle.fromThreePoints(start, mid, end);
    if (c == null) return start.distance(end);
    double a0 = Math.atan2(start.y - c.cy, start.x - c.cx);
    double aMid = Math.atan2(mid.y - c.cy, mid.x - c.cx);
    double a1 = Math.atan2(end.y - c.cy, end.x - c.cx);
    boolean ccw = isMidInCcwSweep(a0, aMid, a1);
    return c.r * signedSweep(a0, a1, ccw);
  }

  /** Circumcircle of three points, or {@code null} if colinear. */
  private static final class Circle {
    final double cx, cy, r;

    private Circle(double cx, double cy, double r) {
      this.cx = cx;
      this.cy = cy;
      this.r = r;
    }

    static Circle fromThreePoints(Coordinate a, Coordinate b, Coordinate c) {
      // Use the Orientation-index colinearity test for robustness;
      // the determinant variant is fast but fragile for near-colinear
      // input.
      if (Orientation.index(a, b, c) == Orientation.COLLINEAR) return null;
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
