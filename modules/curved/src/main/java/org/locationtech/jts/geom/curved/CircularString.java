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

import org.locationtech.jts.algorithm.Distance;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

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
   * C-LIN: arc-length-weighted centroid of the circular string. The centroid
   * of each circular arc is taken analytically (it lies on the arc's bisector
   * at distance {@code R*sin(alpha)/alpha} from the centre, {@code alpha} the
   * half-sweep) and the per-arc centroids are averaged weighted by arc length,
   * rather than computing the centroid of the flat chord polyline of the
   * control points. For a half-arc this puts the centroid at {@code 2R/pi}
   * from the centre. Degenerate (collinear) triples contribute their chord
   * midpoint weighted by chord length.
   */
  @Override
  public Point getCentroid() {
    CoordinateSequence cs = getCoordinateSequence();
    int n = cs.size();
    if (n < 3) {
      return super.getCentroid();
    }
    double wSum = 0.0, cxAcc = 0.0, cyAcc = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      double[] c = arcCentroid(
          cs.getX(i), cs.getY(i),
          cs.getX(i + 1), cs.getY(i + 1),
          cs.getX(i + 2), cs.getY(i + 2));
      cxAcc += c[2] * c[0];
      cyAcc += c[2] * c[1];
      wSum += c[2];
    }
    if (!(wSum > 0.0)) {
      return super.getCentroid();
    }
    return getFactory().createPoint(new Coordinate(cxAcc / wSum, cyAcc / wSum));
  }

  /**
   * D-PT: distance to a puntal geometry is computed analytically against the
   * circular arcs (point-to-arc, clamped to each arc's sweep) rather than
   * against the flat chord polyline of the control points. For non-puntal
   * inputs (line/area, i.e. the D-AA / D-OP tags) the inherited densified
   * behaviour is retained.
   */
  @Override
  public double distance(Geometry g) {
    CoordinateSequence cs = getCoordinateSequence();
    int n = cs.size();
    if (g == null || g.isEmpty() || g.getDimension() != 0 || n < 3) {
      return super.distance(g);
    }
    double best = Double.POSITIVE_INFINITY;
    Coordinate[] pts = g.getCoordinates();
    for (int k = 0; k < pts.length; k++) {
      for (int i = 0; i + 2 < n; i += 2) {
        double dpt = pointToArc(pts[k].x, pts[k].y,
            cs.getX(i), cs.getY(i),
            cs.getX(i + 1), cs.getY(i + 1),
            cs.getX(i + 2), cs.getY(i + 2));
        if (dpt < best) best = dpt;
      }
    }
    return best;
  }

  /**
   * Distance from a point to one circular arc. If the point's projection onto
   * the circle falls within the arc's sweep the distance is {@code |d - R|}
   * (d the point-centre distance); otherwise it is the distance to the nearer
   * arc endpoint. Degenerate (collinear) triples use point-to-chord distance.
   */
  private static double pointToArc(double px, double py,
                                   double sx, double sy,
                                   double mx, double my,
                                   double ex, double ey) {
    double[] g = arcGeometry(sx, sy, mx, my, ex, ey);
    if (g == null) {
      return Distance.pointToSegment(new Coordinate(px, py),
          new Coordinate(sx, sy), new Coordinate(ex, ey));
    }
    double cx = g[0], cy = g[1], r = g[2], delta = g[3];
    double dC = Math.hypot(px - cx, py - cy);
    if (dC == 0.0) {
      return r; // point at the centre: every arc point is R away
    }
    double phi = Math.atan2(py - cy, px - cx);
    double a0 = Math.atan2(sy - cy, sx - cx);
    boolean within = (delta >= 0)
        ? (normTwoPi(phi - a0) <= delta)
        : (normTwoPi(a0 - phi) <= -delta);
    if (within) {
      return Math.abs(dC - r);
    }
    return Math.min(Math.hypot(px - sx, py - sy), Math.hypot(px - ex, py - ey));
  }

  /**
   * Exact arc length for one circular arc given its 3 control points.
   * (Inlined here for main-code use by getLength(); the test CurveRefRunner
   * keeps its own copy for adversarial/hunter isolation.)
   */
  private static double exactCircularArcLength(double sx, double sy,
                                               double mx, double my,
                                               double ex, double ey) {
    double[] g = arcGeometry(sx, sy, mx, my, ex, ey);
    if (g == null) {
      return Math.hypot(ex - sx, ey - sy); // degenerate -> chord
    }
    return g[2] * Math.abs(g[3]); // r * |sweep|
  }

  /**
   * Centroid and arc length of one circular arc as {@code [cx, cy, length]}.
   * Degenerate (collinear) triples return the chord midpoint and chord length.
   */
  private static double[] arcCentroid(double sx, double sy,
                                      double mx, double my,
                                      double ex, double ey) {
    double[] g = arcGeometry(sx, sy, mx, my, ex, ey);
    if (g == null) {
      return new double[] { (sx + ex) / 2.0, (sy + ey) / 2.0,
          Math.hypot(ex - sx, ey - sy) };
    }
    double cx = g[0], cy = g[1], r = g[2], delta = g[3];
    double length = r * Math.abs(delta);
    double half = Math.abs(delta) / 2.0;
    // Distance of the arc centroid from the centre along the bisector.
    double dC = r * Math.sin(half) / half;
    // Bisector direction = start direction (from centre) advanced by half the
    // signed sweep.
    double phiMid = Math.atan2(sy - cy, sx - cx) + delta / 2.0;
    return new double[] { cx + dC * Math.cos(phiMid),
        cy + dC * Math.sin(phiMid), length };
  }

  /**
   * Geometry of one circular arc through three control points as
   * {@code [centreX, centreY, radius, signedSweep]} (sweep positive CCW), or
   * {@code null} when the triple is degenerate (collinear / coincident).
   *
   * <p>Degeneracy is decided with the scale-invariant {@link Orientation}
   * predicate -- the criterion proven sound in NetTopologySuite.Proofs
   * ArcOrient.v (arc_side_chord_mid_nonzero) -- not an absolute determinant
   * threshold (which misclassifies valid arcs at small coordinate magnitudes,
   * since det scales as O(coord^2)). The circumcentre is computed in a frame
   * translated to the start point to avoid catastrophic cancellation for arcs
   * small relative to their distance from the origin, and the swept angle uses
   * the orientation-robust CCW-span selection (correct for minor, major and
   * reflex arcs alike).
   */
  private static double[] arcGeometry(double sx, double sy,
                                      double mx, double my,
                                      double ex, double ey) {
    if (Orientation.index(new Coordinate(sx, sy), new Coordinate(mx, my),
                          new Coordinate(ex, ey)) == Orientation.COLLINEAR) {
      return null;
    }
    double mxL = mx - sx, myL = my - sy;
    double exL = ex - sx, eyL = ey - sy;
    double d = 2 * (mxL * eyL - exL * myL);
    double mSq = mxL * mxL + myL * myL;
    double eSq = exL * exL + eyL * eyL;
    double cxL = (mSq * eyL - eSq * myL) / d;
    double cyL = (eSq * mxL - mSq * exL) / d;
    double r = Math.hypot(cxL, cyL);
    if (!Double.isFinite(r) || r == 0.0) {
      return null;
    }
    double a0 = Math.atan2(-cyL, -cxL);
    double a1 = Math.atan2(myL - cyL, mxL - cxL);
    double a2 = Math.atan2(eyL - cyL, exL - cxL);
    double midCcw = normTwoPi(a1 - a0);
    double endCcw = normTwoPi(a2 - a0);
    double delta = (midCcw <= endCcw) ? endCcw : endCcw - 2 * Math.PI;
    return new double[] { cxL + sx, cyL + sy, r, delta };
  }

  /** Reduce an angle to {@code [0, 2*PI)}. */
  private static double normTwoPi(double a) {
    double twoPi = 2 * Math.PI;
    a = a % twoPi;
    if (a < 0) a += twoPi;
    return a;
  }
}
