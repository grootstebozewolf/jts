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

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * A connected sequence of circular arcs, where each consecutive triple of
 * control points (start, mid, end) defines one arc and the end point of one
 * arc is the start point of the next.
 * <p>
 * This is a phase-1 stand-in: the control points are stored as a single
 * {@link CoordinateSequence} (inherited via {@link LineString}) and spatial
 * operations fall through to the parent's polyline behaviour. Native
 * arc-aware algorithms are out of scope for this module today.
 */
public class CircularString extends LineString implements Linearizable {
  private static final long serialVersionUID = 1L;

  public CircularString(CoordinateSequence points, GeometryFactory factory) {
    super(points, factory);
  }

  @Override
  public String getGeometryType() {
    return "CircularString";
  }

  /**
   * B-CC (lineal) guard for CircularString: explicit override of the
   * inherited line boundary contract, for symmetry with the CompoundCurve
   * guard and to assert the intent for curved lineals.
   *
   * <p>CircularString is a 1D lineal; its boundary is therefore the same
   * as LineString: open -> MultiPoint of its two control endpoints
   * (start of first arc, end of last arc); closed -> empty (modulo bnRule).
   * We make this explicit so the contract is visible on the curved subtype.
   */
  @Override
  public Geometry getBoundary() {
    return super.getBoundary();
  }

  /**
   * D-PT: analytical point-to-arc distance (clamps query angle to the arc sweep
   * around the circle centre; falls back to segment for degenerate).
   * For CompoundCurve containing arcs, the containing CC will delegate per-member.
   * Other geometries fall back to control-point polyline distance.
   */
  @Override
  public double distance(Geometry g) {
    if (g instanceof org.locationtech.jts.geom.Point) {
      org.locationtech.jts.geom.Point pt = (org.locationtech.jts.geom.Point) g;
      org.locationtech.jts.geom.Coordinate p = pt.getCoordinate();
      if (p == null) return 0.0;
      return distanceToPoint(p);
    }
    if (g instanceof CircularString) {
      CircularString o = (CircularString) g;
      org.locationtech.jts.geom.CoordinateSequence my = getCoordinateSequence();
      org.locationtech.jts.geom.CoordinateSequence oth = o.getCoordinateSequence();
      double min = Double.POSITIVE_INFINITY;
      for (int i = 0; i + 2 < my.size(); i += 2) {
        for (int j = 0; j + 2 < oth.size(); j += 2) {
          double d = distanceArcToArc(
              my.getX(i), my.getY(i), my.getX(i + 1), my.getY(i + 1), my.getX(i + 2), my.getY(i + 2),
              oth.getX(j), oth.getY(j), oth.getX(j + 1), oth.getY(j + 1), oth.getX(j + 2), oth.getY(j + 2)
          );
          if (d < min) min = d;
        }
      }
      return min;
    }
    if (g instanceof CompoundCurve) {
      CompoundCurve o = (CompoundCurve) g;
      double min = Double.POSITIVE_INFINITY;
      for (int i = 0; i < o.getNumCurves(); i++) {
        double d = this.distance(o.getCurveN(i));
        if (d < min) min = d;
      }
      return min;
    }
    return super.distance(g);
  }

  private double distanceToPoint(org.locationtech.jts.geom.Coordinate p) {
    org.locationtech.jts.geom.CoordinateSequence seq = getCoordinateSequence();
    int n = seq.size();
    if (n < 2) return 0.0;
    double min = Double.POSITIVE_INFINITY;
    for (int i = 0; i + 2 < n; i += 2) {
      double d = distancePointToArc(p,
          seq.getX(i), seq.getY(i),
          seq.getX(i+1), seq.getY(i+1),
          seq.getX(i+2), seq.getY(i+2));
      if (d < min) min = d;
    }
    // also direct to controls as safety
    for (int i = 0; i < n; i++) {
      double d = dist2d(p.x, p.y, seq.getX(i), seq.getY(i));
      if (d < min) min = d;
    }
    return min;
  }

  @Override
  protected CircularString copyInternal() {
    return new CircularString(getCoordinateSequence().copy(), getFactory());
  }

  @Override
  public Geometry toLinear(double tolerance) {
    return getFactory().createLineString(getCoordinateSequence().copy());
  }

  @Override
  public double getLength() {
    // M-LEN-CS green: analytical sum, not chord sum of controls.
    // Walks the control seq taking every consecutive triple (stride 2) as one arc.
    CoordinateSequence cs = getCoordinateSequence();
    int n = cs.size();
    if (n < 3) return 0.0;
    double len = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      len += exactCircularArcLength(
          cs.getX(i), cs.getY(i),
          cs.getX(i + 1), cs.getY(i + 1),
          cs.getX(i + 2), cs.getY(i + 2)
      );
    }
    return len;
  }

  /**
   * Exact arc length for one circular arc given its 3 control points.
   * (Inlined here for main-code use by getLength(); the test CurveRefRunner
   * keeps its own copy for adversarial/hunter isolation.)
   */
  private static double exactCircularArcLength(double sx, double sy,
                                               double mx, double my,
                                               double ex, double ey) {
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (Math.abs(d) < 1e-12) {
      return Math.hypot(ex - sx, ey - sy);
    }
    double cx = ((sx * sx + sy * sy) * (my - ey)
               + (mx * mx + my * my) * (ey - sy)
               + (ex * ex + ey * ey) * (sy - my)) / d;
    double cy = ((sx * sx + sy * sy) * (ex - mx)
               + (mx * mx + my * my) * (sx - ex)
               + (ex * ex + ey * ey) * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (r < 1e-12) {
      return Math.hypot(ex - sx, ey - sy);
    }
    double a0 = Math.atan2(sy - cy, sx - cx);
    double a1 = Math.atan2(my - cy, mx - cx);
    double a2 = Math.atan2(ey - cy, ex - cx);
    double sweep = a2 - a0;
    sweep = ((sweep + Math.PI) % (2 * Math.PI)) - Math.PI;
    double aMidRel = a1 - a0;
    aMidRel = ((aMidRel + Math.PI) % (2 * Math.PI)) - Math.PI;
    if (Math.signum(sweep) * Math.signum(aMidRel) < 0 && Math.abs(sweep) < Math.PI) {
      sweep = (sweep > 0 ? sweep - 2 * Math.PI : sweep + 2 * Math.PI);
    }
    double theta = Math.abs(sweep);
    return r * theta;
  }

  // --- D-PT helpers (analytical point-to-arc) ---

  private static double[] computeCenterRadiusAndSweep(double sx, double sy,
                                                      double mx, double my,
                                                      double ex, double ey) {
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (Math.abs(d) < 1e-12) {
      return null; // collinear / degenerate
    }
    double cx = ((sx * sx + sy * sy) * (my - ey)
               + (mx * mx + my * my) * (ey - sy)
               + (ex * ex + ey * ey) * (sy - my)) / d;
    double cy = ((sx * sx + sy * sy) * (ex - mx)
               + (mx * mx + my * my) * (sx - ex)
               + (ex * ex + ey * ey) * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (r < 1e-12) return null;
    double a0 = Math.atan2(sy - cy, sx - cx);
    double a1 = Math.atan2(my - cy, mx - cx);
    double a2 = Math.atan2(ey - cy, ex - cx);
    double sweep = a2 - a0;
    sweep = ((sweep + Math.PI) % (2 * Math.PI)) - Math.PI;
    double aMidRel = a1 - a0;
    aMidRel = ((aMidRel + Math.PI) % (2 * Math.PI)) - Math.PI;
    if (Math.signum(sweep) * Math.signum(aMidRel) < 0 && Math.abs(sweep) < Math.PI) {
      sweep = (sweep > 0 ? sweep - 2 * Math.PI : sweep + 2 * Math.PI);
    }
    return new double[]{cx, cy, r, sweep};
  }

  private static double dist2d(double x1, double y1, double x2, double y2) {
    double dx = x1 - x2;
    double dy = y1 - y2;
    return Math.hypot(dx, dy);
  }

  private static double distanceToSegment(double px, double py,
                                          double x1, double y1,
                                          double x2, double y2) {
    double dx = x2 - x1;
    double dy = y2 - y1;
    double len2 = dx * dx + dy * dy;
    if (len2 == 0) return dist2d(px, py, x1, y1);
    double t = ((px - x1) * dx + (py - y1) * dy) / len2;
    t = Math.max(0, Math.min(1, t));
    double qx = x1 + t * dx;
    double qy = y1 + t * dy;
    return dist2d(px, py, qx, qy);
  }

  private static double distancePointToArc(org.locationtech.jts.geom.Coordinate p,
                                           double sx, double sy,
                                           double mx, double my,
                                           double ex, double ey) {
    double[] csr = computeCenterRadiusAndSweep(sx, sy, mx, my, ex, ey);
    if (csr == null) {
      return distanceToSegment(p.x, p.y, sx, sy, ex, ey);
    }
    double cx = csr[0], cy = csr[1], r = csr[2], sweep = csr[3];
    double a0 = Math.atan2(sy - cy, sx - cx);
    double ap = Math.atan2(p.y - cy, p.x - cx);
    double closest;
    if (sweep >= 0) {
      // ccw arc
      double d = (ap - a0 + 2 * Math.PI) % (2 * Math.PI);
      if (d > sweep) d = sweep;
      closest = a0 + d;
    } else {
      // cw arc
      double d = (a0 - ap + 2 * Math.PI) % (2 * Math.PI);
      double absS = -sweep;
      if (d > absS) d = absS;
      closest = a0 - d;
    }
    double qx = cx + r * Math.cos(closest);
    double qy = cy + r * Math.sin(closest);
    double dArc = dist2d(p.x, p.y, qx, qy);
    // safety mins to ends
    double ds = dist2d(p.x, p.y, sx, sy);
    double de = dist2d(p.x, p.y, ex, ey);
    return Math.min(dArc, Math.min(ds, de));
  }

  /** D-AA: arc-to-arc analytical (two-circle dist + sweep clip to arcs).
   *  Uses radial closest points (clamped) + endpoint-to-arc projs (reuse point-to-arc).
   *  Returns 0 for overlapping projections.
   */
  private static double distanceArcToArc(double s1x, double s1y, double m1x, double m1y, double e1x, double e1y,
                                         double s2x, double s2y, double m2x, double m2y, double e2x, double e2y) {
    double[] c1 = computeCenterRadiusAndSweep(s1x, s1y, m1x, m1y, e1x, e1y);
    double[] c2 = computeCenterRadiusAndSweep(s2x, s2y, m2x, m2y, e2x, e2y);
    if (c1 == null || c2 == null) {
      // degen to segments, min of cross endpoint-segment
      double d = Double.POSITIVE_INFINITY;
      d = Math.min(d, distanceToSegment(s1x, s1y, s2x, s2y, e2x, e2y));
      d = Math.min(d, distanceToSegment(e1x, e1y, s2x, s2y, e2x, e2y));
      d = Math.min(d, distanceToSegment(s2x, s2y, s1x, s1y, e1x, e1y));
      d = Math.min(d, distanceToSegment(e2x, e2y, s1x, s1y, e1x, e1y));
      return d;
    }
    double cx1 = c1[0], cy1 = c1[1], r1 = c1[2], sw1 = c1[3];
    double cx2 = c2[0], cy2 = c2[1], r2 = c2[2], sw2 = c2[3];
    double dx = cx2 - cx1, dy = cy2 - cy1;
    double d = Math.hypot(dx, dy);
    double minD = Double.POSITIVE_INFINITY;

    // 4 endpoint-to-opposite-arc (reuse point-to-arc)
    minD = Math.min(minD, distancePointToArc(new org.locationtech.jts.geom.Coordinate(s1x, s1y), s2x, s2y, m2x, m2y, e2x, e2y));
    minD = Math.min(minD, distancePointToArc(new org.locationtech.jts.geom.Coordinate(e1x, e1y), s2x, s2y, m2x, m2y, e2x, e2y));
    minD = Math.min(minD, distancePointToArc(new org.locationtech.jts.geom.Coordinate(s2x, s2y), s1x, s1y, m1x, m1y, e1x, e1y));
    minD = Math.min(minD, distancePointToArc(new org.locationtech.jts.geom.Coordinate(e2x, e2y), s1x, s1y, m1x, m1y, e1x, e1y));

    // radial closest (outer)
    if (d > 1e-9) {
      double ang = Math.atan2(dy, dx);
      // for arc1 in dir to c2
      double ap1 = Math.atan2(cy2 - cy1, cx2 - cx1);
      double a01 = Math.atan2(s1y - cy1, s1x - cx1);
      double delta1 = ap1 - a01;
      double cl1;
      if (sw1 >= 0) {
        delta1 = (delta1 + 2 * Math.PI) % (2 * Math.PI);
        if (delta1 > sw1) delta1 = sw1;
        cl1 = a01 + delta1;
      } else {
        delta1 = (a01 - ap1 + 2 * Math.PI) % (2 * Math.PI);
        double as = -sw1;
        if (delta1 > as) delta1 = as;
        cl1 = a01 - delta1;
      }
      double q1x = cx1 + r1 * Math.cos(cl1);
      double q1y = cy1 + r1 * Math.sin(cl1);
      // for arc2 in dir to c1 (ang + pi)
      double ap2 = ap1 + Math.PI;
      double a02 = Math.atan2(s2y - cy2, s2x - cx2);
      double delta2 = ap2 - a02;
      double cl2;
      if (sw2 >= 0) {
        delta2 = (delta2 + 2 * Math.PI) % (2 * Math.PI);
        if (delta2 > sw2) delta2 = sw2;
        cl2 = a02 + delta2;
      } else {
        delta2 = (a02 - ap2 + 2 * Math.PI) % (2 * Math.PI);
        double as = -sw2;
        if (delta2 > as) delta2 = as;
        cl2 = a02 - delta2;
      }
      double q2x = cx2 + r2 * Math.cos(cl2);
      double q2y = cy2 + r2 * Math.sin(cl2);
      minD = Math.min(minD, dist2d(q1x, q1y, q2x, q2y));
    }

    return minD;
  }
}
