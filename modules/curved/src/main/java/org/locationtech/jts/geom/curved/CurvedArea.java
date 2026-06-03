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
 * Analytical area and area-centroid for curve-bounded rings (M-AREA-CP /
 * C-AREA), using the exact circular-segment correction rather than the flat
 * chord polygon.
 *
 * <p>Both are evaluated by Green's theorem over the ring boundary, walked
 * segment by segment. With the boundary integral {@code oint},
 * <pre>
 *   A      = (1/2) oint (x dy - y dx)
 *   ∬ x dA = (1/2) oint x^2 dy
 *   ∬ y dA = -(1/2) oint y^2 dx
 * </pre>
 * the centroid is {@code (∬x dA / A, ∬y dA / A)}. A straight segment
 * contributes elementary polynomial terms; a circular arc contributes the
 * closed-form trigonometric integrals over its sweep. A disk expressed as
 * arcs yields area {@code pi*R^2} and centroid at its centre; a half-disk
 * yields centroid {@code 4R/(3*pi)} from the centre.
 *
 * <p>Arc geometry uses the robustness measures proven out for arc length
 * (M-LEN-CS): scale-invariant {@link Orientation} degeneracy
 * (ArcOrient.arc_side_chord_mid_nonzero), a start-translated circumcentre, and
 * the orientation-robust CCW-span signed sweep. Rings are evaluated in a frame
 * translated to the shell's first vertex to limit cancellation far from the
 * origin.
 */
final class CurvedArea {

  private CurvedArea() {
  }

  /** Area of a curve-bounded polygon: {@code |shell| - sum|hole|}. */
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
    return ringMoments(ring, o.x, o.y)[0] / 2.0;
  }

  /**
   * Area centroid of the rings {@code [shell, hole0, hole1, ...]}, or
   * {@code null} when the area is zero / non-finite. Holes are detected by
   * opposite winding to the shell and subtracted.
   */
  static double[] centroid(LineString[] rings) {
    if (rings == null || rings.length == 0
        || rings[0] == null || rings[0].isEmpty()) {
      return null;
    }
    Coordinate anchor = rings[0].getCoordinateN(0);
    double ox = anchor.x, oy = anchor.y;
    double[] m = ringMoments(rings[0], ox, oy);
    double twoArea = m[0], mx = m[1], my = m[2];
    double shellSign = Math.signum(m[0]);
    for (int i = 1; i < rings.length; i++) {
      double[] h = ringMoments(rings[i], ox, oy);
      // A hole with the same winding as the shell must subtract.
      double f = (Math.signum(h[0]) == shellSign) ? -1.0 : 1.0;
      twoArea += f * h[0];
      mx += f * h[1];
      my += f * h[2];
    }
    double area = twoArea / 2.0;
    if (area == 0.0 || !Double.isFinite(area)) return null;
    // Sign of the (signed) area cancels in the moment ratios.
    return new double[] { mx / area + ox, my / area + oy };
  }

  /** {2*signedArea, ∬x dA, ∬y dA} for one ring, in the (ox,oy)-translated frame. */
  private static double[] ringMoments(LineString ring, double ox, double oy) {
    double[] a = new double[3];
    if (ring instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) ring;
      for (int i = 0; i < cc.getNumCurves(); i++) {
        memberMoments(cc.getCurveN(i), ox, oy, a);
      }
    } else {
      memberMoments(ring, ox, oy, a);
    }
    return a;
  }

  private static void memberMoments(LineString m, double ox, double oy, double[] a) {
    if (m instanceof CircularString) {
      arcsMoments(m.getCoordinateSequence(), ox, oy, a);
    } else {
      straightMoments(m.getCoordinateSequence(), ox, oy, a);
    }
  }

  private static void straightMoments(CoordinateSequence seq, double ox, double oy, double[] a) {
    for (int i = 0; i + 1 < seq.size(); i++) {
      straightSeg(seq.getX(i) - ox, seq.getY(i) - oy,
                  seq.getX(i + 1) - ox, seq.getY(i + 1) - oy, a);
    }
  }

  private static void arcsMoments(CoordinateSequence seq, double ox, double oy, double[] a) {
    int n = seq.size();
    if (n < 3) { straightMoments(seq, ox, oy, a); return; }
    for (int i = 0; i + 2 < n; i += 2) {
      arcMoments(seq.getX(i) - ox,     seq.getY(i) - oy,
                 seq.getX(i + 1) - ox, seq.getY(i + 1) - oy,
                 seq.getX(i + 2) - ox, seq.getY(i + 2) - oy, a);
    }
  }

  /** Straight edge contribution to {2*area, ∬x dA, ∬y dA}. */
  private static void straightSeg(double x0, double y0, double x1, double y1, double[] a) {
    a[0] += x0 * y1 - x1 * y0;
    a[1] += (y1 - y0) * (x0 * x0 + x0 * x1 + x1 * x1) / 6.0;
    a[2] += -(x1 - x0) * (y0 * y0 + y0 * y1 + y1 * y1) / 6.0;
  }

  /** Circular-arc contribution to {2*area, ∬x dA, ∬y dA} (coords pre-translated). */
  private static void arcMoments(double sx, double sy, double mx, double my,
                                 double ex, double ey, double[] a) {
    if (Orientation.index(new Coordinate(sx, sy), new Coordinate(mx, my),
                          new Coordinate(ex, ey)) == Orientation.COLLINEAR) {
      straightSeg(sx, sy, ex, ey, a);
      return;
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
      straightSeg(sx, sy, ex, ey, a);
      return;
    }
    double a0 = Math.atan2(-cyL, -cxL);
    double a1 = Math.atan2(myL - cyL, mxL - cxL);
    double a2 = Math.atan2(eyL - cyL, exL - cxL);
    double midCcw = normTwoPi(a1 - a0);
    double endCcw = normTwoPi(a2 - a0);
    double delta = (midCcw <= endCcw) ? endCcw : endCcw - 2 * Math.PI;
    double cx = cxL + sx, cy = cyL + sy;        // centre, ring-local frame
    double al = Math.atan2(sy - cy, sx - cx);   // start angle from centre
    double be = al + delta;

    // area: (1/2) oint (x dy - y dx) over the arc.
    a[0] += r * r * delta + cx * (ey - sy) - cy * (ex - sx);

    double sinAl = Math.sin(al), sinBe = Math.sin(be);
    double cosAl = Math.cos(al), cosBe = Math.cos(be);
    double sin2 = Math.sin(2 * be) - Math.sin(2 * al);

    // (1/2) oint x^2 dy over the arc.
    double s1 = sinBe - sinAl;
    double c2 = delta / 2.0 + sin2 / 4.0;
    double c3 = (sinBe - sinAl) - (sinBe * sinBe * sinBe - sinAl * sinAl * sinAl) / 3.0;
    double ixdy = r * (cx * cx * s1 + 2 * cx * r * c2 + r * r * c3);
    a[1] += ixdy / 2.0;

    // -(1/2) oint y^2 dx over the arc.
    double sa = cosAl - cosBe;
    double s2b = delta / 2.0 - sin2 / 4.0;
    double s3b = (cosAl - cosBe) + (cosBe * cosBe * cosBe - cosAl * cosAl * cosAl) / 3.0;
    double iy2dx = -r * (cy * cy * sa + 2 * cy * r * s2b + r * r * s3b);
    a[2] += -iy2dx / 2.0;
  }

  /** Reduce an angle to {@code [0, 2*PI)}. */
  private static double normTwoPi(double a) {
    double twoPi = 2 * Math.PI;
    a = a % twoPi;
    if (a < 0) a += twoPi;
    return a;
  }
}
