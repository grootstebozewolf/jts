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
package org.locationtech.jts.geom.curve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.locationtech.jts.algorithm.LineIntersector;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

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
    //
    // Every append goes through addUnique. A must-include anchor that is one of
    // the arc's own control points -- which is what CompoundCurve.toLinear
    // passes -- projects onto a vertex the chord walk already emitted, and the
    // resulting repeated point makes a Delaunay triangulation of the output
    // degenerate (see DensifierRepeatedPointTest).
    double eps = coincidenceTolerance(c.r);
    List<Coordinate> out = new ArrayList<Coordinate>(segments + 1 + projected.size());
    out.add(new Coordinate(start));

    int projIdx = 0;
    for (int i = 1; i <= segments; i++) {
      double sweepEnd = i * Math.abs(delta); // 0..sweep monotonically
      // Insert any projected points whose sweep-angle is < sweepEnd
      // before the next chord vertex.
      while (projIdx < projected.size()
             && projected.get(projIdx).sweepAngle < sweepEnd) {
        addAnchor(out, projected.get(projIdx).coord, eps);
        projIdx++;
      }
      double angle = a0 + i * delta;
      addUnique(out, new Coordinate(c.cx + c.r * Math.cos(angle),
                              c.cy + c.r * Math.sin(angle)), eps);
    }
    // Any projected points exactly at the end are appended too (rare).
    while (projIdx < projected.size()) {
      addAnchor(out, projected.get(projIdx).coord, eps);
      projIdx++;
    }

    // Finish on the original `end` coordinate rather than a cos/sin value, so
    // the endpoint is exact. Drop whatever already sits within eps of it --
    // the final chord vertex, and possibly a projected `end` anchor -- instead
    // of overwriting only the last slot, which left the other one behind.
    while (out.size() > 1 && out.get(out.size() - 1).distance(end) <= eps) {
      out.remove(out.size() - 1);
    }
    addUnique(out, new Coordinate(end), eps);
    return out;
  }

  /**
   * Distance below which two vertices of an arc are the same point.
   * <p>
   * Scaled by radius so it means the same thing at any size, and many orders of
   * magnitude below the shortest legitimate chord: at a tolerance fine enough to
   * give 800 segments on a radius-5 arc the chords are still ~0.02 long, against
   * an eps of 5e-9.
   */
  private static double coincidenceTolerance(double radius) {
    return Math.max(1.0e-12, radius * 1.0e-9);
  }

  /** Appends unless it would repeat the vertex already at the tail. */
  /**
   * Appends an anchor -- a point the caller supplied exactly -- preferring it
   * over a coincident computed vertex.
   * <p>
   * {@code addUnique} suppresses near-duplicates by keeping the FIRST of a
   * coincident pair, which is right for computed points but backwards for
   * anchors: when the chord walk's vertex lands on a control point (twelve
   * segments over a semicircle put a vertex exactly at the mid control), the
   * computed cos/sin value won the tie and the exact control point was dropped.
   * Here the coincident predecessor is replaced instead -- unless it is the
   * start point at index 0, which is itself an exact control point.
   */
  private static void addAnchor(List<Coordinate> out, Coordinate c, double eps) {
    if (!out.isEmpty()) {
      int last = out.size() - 1;
      if (out.get(last).distance(c) <= eps) {
        if (last > 0) {
          out.set(last, new Coordinate(c));
        }
        return;
      }
    }
    out.add(new Coordinate(c));
  }

  private static void addUnique(List<Coordinate> out, Coordinate c, double eps) {
    if (!out.isEmpty() && out.get(out.size() - 1).distance(c) <= eps) return;
    out.add(c);
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
      // Carry the ORIGINAL coordinate, not its projection onto the circle. The
      // angle exists only to order the anchor among the chord vertices; the
      // mustInclude contract is that the caller's exact point appears in the
      // output. Re-projecting turned (0, 1) into (6.1e-17, 1) -- the same
      // cos/sin noise DENS-DUP had to dedup away -- and the radial filter above
      // already guarantees the point lies within tolerance of the arc.
      out.add(new ProjectedPoint(sweepAngle, new Coordinate(p)));
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

  /**
   * The arc's contribution to the contour integral
   * {@code 1/2 * integral(x dy - y dx)}, whose value over a closed boundary is
   * the signed enclosed area (Green's theorem).
   * <p>
   * For an arc on centre {@code (cx, cy)} radius {@code r} sweeping
   * {@code a0 -> a1} this evaluates in closed form to
   * <pre>
   *   1/2 * [ r^2 (a1 - a0)
   *           + cx * r * (sin a1 - sin a0)
   *           - cy * r * (cos a1 - cos a0) ]
   * </pre>
   * The sweep is signed by traversal direction, so the result needs no
   * orientation heuristic and composes by simple summation with the shoelace
   * terms of any straight pieces in the same ring.
   * <p>
   * Degenerate (colinear or coincident) triples describe no arc and contribute
   * the straight chord {@code start..end}.
   */
  public static double arcAreaContribution(Coordinate start, Coordinate mid, Coordinate end) {
    Circle c = Circle.fromThreePoints(start, mid, end);
    if (c == null) {
      return 0.5 * (start.x * end.y - end.x * start.y);
    }
    double a0 = Math.atan2(start.y - c.cy, start.x - c.cx);
    double aMid = Math.atan2(mid.y - c.cy, mid.x - c.cx);
    double a1 = Math.atan2(end.y - c.cy, end.x - c.cx);
    boolean ccw = isMidInCcwSweep(a0, aMid, a1);
    double sweep = signedSweep(a0, a1, ccw);
    double delta = ccw ? sweep : -sweep;
    return 0.5 * (c.r * c.r * delta
        + c.cx * c.r * (Math.sin(a0 + delta) - Math.sin(a0))
        - c.cy * c.r * (Math.cos(a0 + delta) - Math.cos(a0)));
  }

  /**
   * Expands {@code env} to cover the arc exactly.
   * <p>
   * An arc's extremes are its endpoints plus whichever of the four axis
   * extremes -- rightmost, top, leftmost, bottom, at angles 0, pi/2, pi and
   * 3pi/2 -- the sweep actually passes through. Only those need adding, so this
   * is exact without densifying.
   * <p>
   * Degenerate (colinear or coincident) triples describe no arc, so only the
   * control points are added.
   */
  public static void expandEnvelope(Coordinate start, Coordinate mid, Coordinate end,
                                    Envelope env) {
    env.expandToInclude(start);
    env.expandToInclude(end);
    Circle c = Circle.fromThreePoints(start, mid, end);
    if (c == null) {
      env.expandToInclude(mid);
      return;
    }
    addAxisExtrema(c, start, mid, end, env, null);
  }

  /**
   * Circumcircle of three points as {@code {cx, cy, r}}, or {@code null} if
   * the triple is colinear or coincident.
   */
  public static double[] circumcircle(Coordinate a, Coordinate b, Coordinate c) {
    Circle circle = Circle.fromThreePoints(a, b, c);
    if (circle == null) return null;
    return new double[] { circle.cx, circle.cy, circle.r };
  }

  /**
   * Appends the arc's endpoints and the axis extrema the sweep actually
   * passes through. Degenerate triples contribute the three control points.
   */
  public static void addArcExtrema(Coordinate start, Coordinate mid, Coordinate end,
      List<Coordinate> dest) {
    dest.add(new Coordinate(start));
    Circle c = Circle.fromThreePoints(start, mid, end);
    if (c == null) {
      dest.add(new Coordinate(mid));
      dest.add(new Coordinate(end));
      return;
    }
    addAxisExtrema(c, start, mid, end, null, dest);
    dest.add(new Coordinate(end));
  }

  /**
   * Distance from {@code p} to the circular arc through
   * {@code start, mid, end}. Degenerate triples degrade to the chord.
   */
  public static double distancePointToArc(Coordinate p, Coordinate start,
      Coordinate mid, Coordinate end) {
    return p.distance(nearestPointOnArc(p, start, mid, end));
  }

  /**
   * Closest point on the arc (or its chord, if the triple is colinear) to
   * {@code p}.
   */
  public static Coordinate nearestPointOnArc(Coordinate p, Coordinate start,
      Coordinate mid, Coordinate end) {
    Circle c = Circle.fromThreePoints(start, mid, end);
    if (c == null) {
      return nearestPointOnSegment(p, start, end);
    }
    double dx = p.x - c.cx;
    double dy = p.y - c.cy;
    double dist = Math.hypot(dx, dy);
    Coordinate onCircle;
    if (dist == 0.0) {
      onCircle = new Coordinate(start);
    } else {
      onCircle = new Coordinate(c.cx + c.r * dx / dist, c.cy + c.r * dy / dist);
    }
    if (isOnSweep(onCircle, c, start, mid, end)) {
      return onCircle;
    }
    return p.distance(start) <= p.distance(end)
        ? new Coordinate(start) : new Coordinate(end);
  }

  /**
   * Directed Hausdorff distance from the arc to the segment: the maximum
   * distance from a point on the arc to the segment.
   * Delegates to {@link DiscreteHausdorffDistance} so the public class
   * owns the formula.
   */
  public static double directedHausdorffArcToSegment(
      Coordinate start, Coordinate mid, Coordinate end,
      Coordinate seg0, Coordinate seg1) {
    return DiscreteHausdorffDistance.directedHausdorffArcToSegment(
        start, mid, end, seg0, seg1);
  }

  /**
   * Directed Hausdorff distance from circle 1 to circle 2 (the boundaries).
   * Delegates to {@link DiscreteHausdorffDistance} so the public class
   * owns the formula.
   */
  public static double directedHausdorffCircleToCircle(
      double c1x, double c1y, double r1, double c2x, double c2y, double r2) {
    return DiscreteHausdorffDistance.directedHausdorffCircleToCircle(
        c1x, c1y, r1, c2x, c2y, r2);
  }

  /**
   * Minimum distance from the circular arc through {@code a0, a1, a2} to
   * the segment {@code s0, s1}. A colinear triple degrades to the chord.
   * Candidates are the four endpoints, the two circle points whose radius
   * is parallel to the segment normal (same extrema
   * {@link #directedHausdorffArcToSegment} uses), and any proper
   * segment-arc crossing (distance 0).
   */
  static double distanceArcToSegment(Coordinate a0, Coordinate a1, Coordinate a2,
      Coordinate s0, Coordinate s1) {
    Circle c = Circle.fromThreePoints(a0, a1, a2);
    if (c == null) {
      return distanceSegmentToSegment(a0, a2, s0, s1);
    }
    if (segmentIntersectsArc(c, a0, a1, a2, s0, s1)) {
      return 0.0;
    }
    double min = distancePointToSegment(a0, s0, s1);
    min = Math.min(min, distancePointToSegment(a2, s0, s1));
    min = Math.min(min, distancePointToArc(s0, a0, a1, a2));
    min = Math.min(min, distancePointToArc(s1, a0, a1, a2));
    double sx = s1.x - s0.x;
    double sy = s1.y - s0.y;
    double slen = Math.hypot(sx, sy);
    if (slen > 0.0) {
      double nx = -sy / slen;
      double ny = sx / slen;
      for (int sign = -1; sign <= 1; sign += 2) {
        Coordinate q = new Coordinate(c.cx + sign * c.r * nx, c.cy + sign * c.r * ny);
        if (isOnSweep(q, c, a0, a1, a2) && projectionOnSegment(q, s0, s1)) {
          min = Math.min(min, distancePointToSegment(q, s0, s1));
        }
      }
    }
    return min;
  }

  static double distanceSegmentToSegment(Coordinate a0, Coordinate a1,
      Coordinate b0, Coordinate b1) {
    LineIntersector li = new RobustLineIntersector();
    li.computeIntersection(a0, a1, b0, b1);
    if (li.hasIntersection()) return 0.0;
    double min = distancePointToSegment(a0, b0, b1);
    min = Math.min(min, distancePointToSegment(a1, b0, b1));
    min = Math.min(min, distancePointToSegment(b0, a0, a1));
    return Math.min(min, distancePointToSegment(b1, a0, a1));
  }

  private static boolean segmentIntersectsArc(Circle c, Coordinate a0,
      Coordinate a1, Coordinate a2, Coordinate s0, Coordinate s1) {
    Coordinate[] hits = intersectSegmentCircle(c, s0, s1);
    for (int i = 0; i < hits.length; i++) {
      if (isOnSweep(hits[i], c, a0, a1, a2)) return true;
    }
    return false;
  }

  /**
   * Line–circle intersections that also lie on the segment
   * ({@code t ∈ [0, 1]}). Package-private -- not a new public API.
   * <p>
   * Twin of the N-AL {@code ARC_SEGMENT_XY} oracle: the same quadratic
   * {@link #segmentIntersectsArc} used to throw away. Callers that need
   * the full circle (a disc) keep every hit; callers that need an arc
   * also ask {@link #isOnSweep}.
   */
  static Coordinate[] intersectSegmentCircle(Circle c, Coordinate s0,
      Coordinate s1) {
    if (c == null) return new Coordinate[0];
    return intersectSegmentCircle(c.cx, c.cy, c.r, s0, s1);
  }

  static Coordinate[] intersectSegmentCircle(double cx, double cy, double r,
      Coordinate s0, Coordinate s1) {
    double dx = s1.x - s0.x;
    double dy = s1.y - s0.y;
    double fx = s0.x - cx;
    double fy = s0.y - cy;
    double A = dx * dx + dy * dy;
    if (A == 0.0) {
      if (Math.abs(Math.hypot(fx, fy) - r) <= 1.0e-12) {
        return new Coordinate[] { new Coordinate(s0) };
      }
      return new Coordinate[0];
    }
    double B = 2.0 * (fx * dx + fy * dy);
    double C = fx * fx + fy * fy - r * r;
    double disc = B * B - 4.0 * A * C;
    if (disc < 0.0) return new Coordinate[0];
    double sqrt = Math.sqrt(disc);
    Coordinate p0 = null;
    Coordinate p1 = null;
    int n = 0;
    for (int sign = -1; sign <= 1; sign += 2) {
      double t = (-B + sign * sqrt) / (2.0 * A);
      if (t < -1.0e-12 || t > 1.0 + 1.0e-12) continue;
      Coordinate p = new Coordinate(s0.x + t * dx, s0.y + t * dy);
      if (n == 0) {
        p0 = p;
        n = 1;
      } else if (p0.distance(p) > 1.0e-12) {
        p1 = p;
        n = 2;
      }
    }
    if (n == 0) return new Coordinate[0];
    if (n == 1) return new Coordinate[] { p0 };
    return new Coordinate[] { p0, p1 };
  }

  static double distanceArcToArc(Coordinate a0, Coordinate a1, Coordinate a2,
      Coordinate b0, Coordinate b1, Coordinate b2) {
    Circle ca = Circle.fromThreePoints(a0, a1, a2);
    Circle cb = Circle.fromThreePoints(b0, b1, b2);
    if (ca == null && cb == null) {
      return distanceSegmentToSegment(a0, a2, b0, b2);
    }
    if (ca == null) {
      return distanceArcToSegment(b0, b1, b2, a0, a2);
    }
    if (cb == null) {
      return distanceArcToSegment(a0, a1, a2, b0, b2);
    }
    double min = distancePointToArc(a0, b0, b1, b2);
    min = Math.min(min, distancePointToArc(a2, b0, b1, b2));
    min = Math.min(min, distancePointToArc(b0, a0, a1, a2));
    min = Math.min(min, distancePointToArc(b2, a0, a1, a2));
    double dx = cb.cx - ca.cx;
    double dy = cb.cy - ca.cy;
    double d = Math.hypot(dx, dy);
    if (d == 0.0) {
      return Math.min(min, Math.abs(ca.r - cb.r));
    }
    double ux = dx / d;
    double uy = dy / d;
    for (int sa = -1; sa <= 1; sa += 2) {
      Coordinate pa = new Coordinate(ca.cx + sa * ca.r * ux, ca.cy + sa * ca.r * uy);
      if (!isOnSweep(pa, ca, a0, a1, a2)) continue;
      for (int sb = -1; sb <= 1; sb += 2) {
        Coordinate pb = new Coordinate(cb.cx + sb * cb.r * ux, cb.cy + sb * cb.r * uy);
        if (!isOnSweep(pb, cb, b0, b1, b2)) continue;
        min = Math.min(min, pa.distance(pb));
      }
    }
    if (circlesIntersectOnBothSweeps(ca, cb, a0, a1, a2, b0, b1, b2)) {
      return 0.0;
    }
    return min;
  }

  /**
   * Intersection points of two supporting circles. Empty when the circles
   * are disjoint, nested without touching, or coincident ({@code d == 0}).
   * A tangent pair returns one point; a proper crossing returns two.
   * <p>
   * Package-private -- not a new public API. The radical-axis formula lived
   * in {@link #circlesIntersectOnBothSweeps} and threw the points away.
   */
  static Coordinate[] intersectCircles(Circle ca, Circle cb) {
    if (ca == null || cb == null) return new Coordinate[0];
    double dx = cb.cx - ca.cx;
    double dy = cb.cy - ca.cy;
    double d = Math.hypot(dx, dy);
    if (d > ca.r + cb.r || d < Math.abs(ca.r - cb.r) || d == 0.0) {
      return new Coordinate[0];
    }
    double a = (ca.r * ca.r - cb.r * cb.r + d * d) / (2.0 * d);
    double h2 = ca.r * ca.r - a * a;
    if (h2 < 0.0) return new Coordinate[0];
    double ux = dx / d;
    double uy = dy / d;
    double mx = ca.cx + a * ux;
    double my = ca.cy + a * uy;
    if (h2 == 0.0) {
      return new Coordinate[] { new Coordinate(mx, my) };
    }
    double h = Math.sqrt(h2);
    return new Coordinate[] {
        new Coordinate(mx + h * -uy, my + h * ux),
        new Coordinate(mx - h * -uy, my - h * ux)
    };
  }

  /**
   * Circle-circle intersections that also lie on both circular-arc sweeps.
   * Empty when the supporting circles miss, coincide, or the radical-axis
   * points fall outside either sweep.
   */
  static Coordinate[] intersectArcs(Coordinate a0, Coordinate a1, Coordinate a2,
      Coordinate b0, Coordinate b1, Coordinate b2) {
    Circle ca = Circle.fromThreePoints(a0, a1, a2);
    Circle cb = Circle.fromThreePoints(b0, b1, b2);
    if (ca == null || cb == null) return new Coordinate[0];
    Coordinate[] raw = intersectCircles(ca, cb);
    int keep = 0;
    Coordinate[] on = new Coordinate[raw.length];
    for (int i = 0; i < raw.length; i++) {
      if (isOnSweep(raw[i], ca, a0, a1, a2) && isOnSweep(raw[i], cb, b0, b1, b2)) {
        on[keep++] = raw[i];
      }
    }
    if (keep == raw.length) return raw;
    Coordinate[] clipped = new Coordinate[keep];
    System.arraycopy(on, 0, clipped, 0, keep);
    return clipped;
  }

  private static boolean circlesIntersectOnBothSweeps(Circle ca, Circle cb,
      Coordinate a0, Coordinate a1, Coordinate a2,
      Coordinate b0, Coordinate b1, Coordinate b2) {
    Coordinate[] pts = intersectCircles(ca, cb);
    for (int i = 0; i < pts.length; i++) {
      if (isOnSweep(pts[i], ca, a0, a1, a2) && isOnSweep(pts[i], cb, b0, b1, b2)) {
        return true;
      }
    }
    return false;
  }

  private static void addAxisExtrema(Circle c, Coordinate start, Coordinate mid,
      Coordinate end, Envelope env, List<Coordinate> dest) {
    double a0 = Math.atan2(start.y - c.cy, start.x - c.cx);
    double aMid = Math.atan2(mid.y - c.cy, mid.x - c.cx);
    double a1 = Math.atan2(end.y - c.cy, end.x - c.cx);
    boolean ccw = isMidInCcwSweep(a0, aMid, a1);
    double sweep = signedSweep(a0, a1, ccw);
    for (int q = 0; q < 4; q++) {
      double axis = q * Math.PI / 2.0;
      double travelled = ccw
          ? normalizePositive(axis - a0)
          : normalizePositive(a0 - axis);
      if (travelled <= sweep) {
        double x = c.cx + c.r * Math.cos(axis);
        double y = c.cy + c.r * Math.sin(axis);
        if (env != null) env.expandToInclude(x, y);
        if (dest != null) dest.add(new Coordinate(x, y));
      }
    }
  }

  static boolean isOnSweep(Coordinate p, Circle c, Coordinate start,
      Coordinate mid, Coordinate end) {
    double a0 = Math.atan2(start.y - c.cy, start.x - c.cx);
    double aMid = Math.atan2(mid.y - c.cy, mid.x - c.cx);
    double a1 = Math.atan2(end.y - c.cy, end.x - c.cx);
    boolean ccw = isMidInCcwSweep(a0, aMid, a1);
    double sweep = signedSweep(a0, a1, ccw);
    double angle = Math.atan2(p.y - c.cy, p.x - c.cx);
    double travelled = ccw
        ? normalizePositive(angle - a0)
        : normalizePositive(a0 - angle);
    return travelled <= sweep + 1.0e-12;
  }

  static Coordinate nearestPointOnSegment(Coordinate p, Coordinate a, Coordinate b) {
    double vx = b.x - a.x;
    double vy = b.y - a.y;
    double len2 = vx * vx + vy * vy;
    if (len2 == 0.0) return new Coordinate(a);
    double t = ((p.x - a.x) * vx + (p.y - a.y) * vy) / len2;
    if (t <= 0.0) return new Coordinate(a);
    if (t >= 1.0) return new Coordinate(b);
    return new Coordinate(a.x + t * vx, a.y + t * vy);
  }

  static double distancePointToSegment(Coordinate p, Coordinate a, Coordinate b) {
    return p.distance(nearestPointOnSegment(p, a, b));
  }

  private static boolean projectionOnSegment(Coordinate p, Coordinate a, Coordinate b) {
    double vx = b.x - a.x;
    double vy = b.y - a.y;
    double len2 = vx * vx + vy * vy;
    if (len2 == 0.0) return false;
    double t = ((p.x - a.x) * vx + (p.y - a.y) * vy) / len2;
    return t >= 0.0 && t <= 1.0;
  }

  /** Circumcircle of three points, or {@code null} if colinear. */
  static final class Circle {
    final double cx, cy, r;

    Circle(double cx, double cy, double r) {
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
