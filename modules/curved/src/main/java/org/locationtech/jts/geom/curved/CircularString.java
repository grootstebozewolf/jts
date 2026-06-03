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
}
