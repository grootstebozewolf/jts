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

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LineString;

/**
 * Analytical area for curve-bounded rings (M-AREA-CP), using the exact
 * circular-segment correction rather than the area of the flattened chord
 * polygon.
 *
 * <p>The enclosed area is evaluated with Green's theorem,
 * {@code A = (1/2) * oint (x dy - y dx)}. The line integral is path-additive,
 * so a ring is walked segment by segment. A straight segment {@code (p, q)}
 * contributes the shoelace cross term {@code p.x*q.y - q.x*p.y}. A circular
 * arc with centre {@code (cx, cy)}, radius {@code R} and signed sweep
 * {@code delta} (positive counter-clockwise) contributes
 * {@code R^2*delta + cx*(ye - ys) - cy*(xe - xs)}, which already folds in the
 * circular-segment area between the arc and its chord with the correct sign.
 * A disk of radius {@code R} therefore evaluates to exactly {@code pi*R^2}.
 *
 * <p>The arc geometry is computed with the same robustness measures proven
 * out for arc length (M-LEN-CS): the start/mid/end degeneracy is decided by
 * the scale-invariant {@link Orientation} predicate (NetTopologySuite.Proofs
 * ArcOrient.arc_side_chord_mid_nonzero), the circumcentre is computed in a
 * frame translated to the arc start, and the swept angle uses the
 * orientation-robust CCW-span selection. The whole ring is additionally
 * evaluated in a frame translated to its first vertex to limit cancellation
 * for rings far from the origin. The circular-segment value matches the
 * proof oracle's ARC_AREA mode.
 */
final class CurvedArea {

  private CurvedArea() {
  }

  /**
   * Area of a curve-bounded polygon: {@code |shell| - sum|hole|}, mirroring
   * {@link org.locationtech.jts.geom.Polygon#getArea()}.
   *
   * @param rings structural rings in boundary order: [shell, hole0, hole1, ...]
   */
  static double ofRings(LineString[] rings) {
    if (rings == null || rings.length == 0) return 0.0;
    double area = Math.abs(signedRingArea(rings[0]));
    for (int i = 1; i < rings.length; i++) {
      area -= Math.abs(signedRingArea(rings[i]));
    }
    return area;
  }

  /** Signed area of one closed curved ring (positive CCW, negative CW). */
  static double signedRingArea(LineString ring) {
    if (ring == null || ring.isEmpty() || ring.getNumPoints() == 0) return 0.0;
    Coordinate o = ring.getCoordinateN(0);
    return accumulate(ring, o.x, o.y) / 2.0;
  }

  private static double accumulate(LineString ring, double ox, double oy) {
    if (ring instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) ring;
      double sum = 0.0;
      for (int i = 0; i < cc.getNumCurves(); i++) {
        sum += accumulateMember(cc.getCurveN(i), ox, oy);
      }
      return sum;
    }
    return accumulateMember(ring, ox, oy);
  }

  private static double accumulateMember(LineString m, double ox, double oy) {
    if (m instanceof CircularString) {
      return accumulateArcs(m.getCoordinateSequence(), ox, oy);
    }
    return accumulateStraight(m.getCoordinateSequence(), ox, oy);
  }

  /** Straight polyline: shoelace cross term per consecutive vertex pair (origin-translated). */
  private static double accumulateStraight(CoordinateSequence seq, double ox, double oy) {
    double sum = 0.0;
    for (int i = 0; i + 1 < seq.size(); i++) {
      double xs = seq.getX(i) - ox,     ys = seq.getY(i) - oy;
      double xe = seq.getX(i + 1) - ox, ye = seq.getY(i + 1) - oy;
      sum += xs * ye - xe * ys;
    }
    return sum;
  }

  /** CircularString: consecutive (start, mid, end) triples, advancing two at a time. */
  private static double accumulateArcs(CoordinateSequence seq, double ox, double oy) {
    int n = seq.size();
    if (n < 3) return accumulateStraight(seq, ox, oy);
    double sum = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      sum += arcContribution(
          seq.getX(i) - ox,     seq.getY(i) - oy,
          seq.getX(i + 1) - ox, seq.getY(i + 1) - oy,
          seq.getX(i + 2) - ox, seq.getY(i + 2) - oy);
    }
    return sum;
  }

  /**
   * Contribution of one arc to {@code oint (x dy - y dx)}, with all
   * coordinates already in the ring-local (origin-translated) frame.
   */
  private static double arcContribution(double sx, double sy,
                                        double mx, double my,
                                        double ex, double ey) {
    if (Orientation.index(new Coordinate(sx, sy), new Coordinate(mx, my),
                          new Coordinate(ex, ey)) == Orientation.COLLINEAR) {
      return sx * ey - ex * sy; // degenerate: straight chord term
    }
    // Circumcentre relative to the arc start (translate again for stability).
    double mxL = mx - sx, myL = my - sy;
    double exL = ex - sx, eyL = ey - sy;
    double d = 2 * (mxL * eyL - exL * myL);
    double mSq = mxL * mxL + myL * myL;
    double eSq = exL * exL + eyL * eyL;
    double cxL = (mSq * eyL - eSq * myL) / d;
    double cyL = (eSq * mxL - mSq * exL) / d;
    double r2 = cxL * cxL + cyL * cyL;
    if (!Double.isFinite(r2) || r2 == 0.0) {
      return sx * ey - ex * sy;
    }
    // Signed sweep (positive CCW) via the orientation-robust CCW-span selection.
    double a0 = Math.atan2(-cyL, -cxL);
    double a1 = Math.atan2(myL - cyL, mxL - cxL);
    double a2 = Math.atan2(eyL - cyL, exL - cxL);
    double midCcw = normTwoPi(a1 - a0);
    double endCcw = normTwoPi(a2 - a0);
    double delta = (midCcw <= endCcw) ? endCcw : endCcw - 2 * Math.PI;
    // Centre back in the ring-local frame (arc start is at (sx,sy) there).
    double cx = cxL + sx;
    double cy = cyL + sy;
    return r2 * delta + cx * (ey - sy) - cy * (ex - sx);
  }

  /** Reduce an angle to {@code [0, 2*PI)}. */
  private static double normTwoPi(double a) {
    double twoPi = 2 * Math.PI;
    a = a % twoPi;
    if (a < 0) a += twoPi;
    return a;
  }
}
