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
import java.util.List;
import java.util.TreeSet;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LineString;

/**
 * Arc-aware interior point for a curve-bounded polygon (C-IP).
 *
 * <p>The standard {@code InteriorPointArea} scans the densified ring, so for a
 * thin crescent (two near-parallel arcs) it can return a point that lies
 * outside the true curved region. This computes the interior point against the
 * actual arc boundary: it intersects a set of horizontal scan lines with the
 * curved boundary, and returns the midpoint of the widest interior interval.
 * By the even-odd rule that midpoint lies strictly between an entering and an
 * exiting boundary crossing, hence provably inside the curved region.
 *
 * <p>Scan heights are chosen as midpoints between consecutive "critical" Y
 * values (every control-point Y, plus each arc's vertical extremes
 * {@code cy +/- R}). That keeps every scan line clear of vertices and arc
 * tangents, so each crossing is transversal and the sorted crossings pair up
 * cleanly into inside/outside intervals.
 */
final class CurvedInteriorPoint {

  private CurvedInteriorPoint() {
  }

  /** A point provably inside the rings {@code [shell, hole0, ...]}, or null. */
  static Coordinate of(LineString[] rings) {
    if (rings == null || rings.length == 0
        || rings[0] == null || rings[0].isEmpty()) {
      return null;
    }
    TreeSet<Double> critical = new TreeSet<Double>();
    for (LineString r : rings) {
      collectCriticalY(r, critical);
    }
    if (critical.size() < 2) {
      return null;
    }
    Double[] ys = critical.toArray(new Double[0]);
    double bestWidth = -1.0, bestX = 0.0, bestY = 0.0;
    for (int i = 0; i + 1 < ys.length; i++) {
      double y = (ys[i] + ys[i + 1]) / 2.0;
      List<Double> xs = new ArrayList<Double>();
      for (LineString r : rings) {
        addRingCrossings(r, y, xs);
      }
      if (xs.size() < 2) continue;
      Collections.sort(xs);
      // Even-odd: [xs[0],xs[1]] inside, [xs[2],xs[3]] inside, ...
      for (int k = 0; k + 1 < xs.size(); k += 2) {
        double w = xs.get(k + 1) - xs.get(k);
        if (w > bestWidth) {
          bestWidth = w;
          bestX = (xs.get(k) + xs.get(k + 1)) / 2.0;
          bestY = y;
        }
      }
    }
    if (bestWidth < 0.0) return null;
    return new Coordinate(bestX, bestY);
  }

  // ---- critical Y values -------------------------------------------------

  private static void collectCriticalY(LineString ring, TreeSet<Double> out) {
    if (ring instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) ring;
      for (int i = 0; i < cc.getNumCurves(); i++) {
        collectMemberCriticalY(cc.getCurveN(i), out);
      }
    } else {
      collectMemberCriticalY(ring, out);
    }
  }

  private static void collectMemberCriticalY(LineString m, TreeSet<Double> out) {
    CoordinateSequence seq = m.getCoordinateSequence();
    for (int i = 0; i < seq.size(); i++) {
      out.add(seq.getY(i));
    }
    if (m instanceof CircularString && seq.size() >= 3) {
      for (int i = 0; i + 2 < seq.size(); i += 2) {
        double[] g = arc(seq.getX(i), seq.getY(i), seq.getX(i + 1), seq.getY(i + 1),
                         seq.getX(i + 2), seq.getY(i + 2));
        if (g != null) {
          out.add(g[1] + g[2]); // cy + R
          out.add(g[1] - g[2]); // cy - R
        }
      }
    }
  }

  // ---- horizontal-line crossings -----------------------------------------

  private static void addRingCrossings(LineString ring, double y, List<Double> xs) {
    if (ring instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) ring;
      for (int i = 0; i < cc.getNumCurves(); i++) {
        addMemberCrossings(cc.getCurveN(i), y, xs);
      }
    } else {
      addMemberCrossings(ring, y, xs);
    }
  }

  private static void addMemberCrossings(LineString m, double y, List<Double> xs) {
    CoordinateSequence seq = m.getCoordinateSequence();
    if (m instanceof CircularString && seq.size() >= 3) {
      for (int i = 0; i + 2 < seq.size(); i += 2) {
        addArcCrossing(seq.getX(i), seq.getY(i), seq.getX(i + 1), seq.getY(i + 1),
                       seq.getX(i + 2), seq.getY(i + 2), y, xs);
      }
    } else {
      addStraightCrossings(seq, y, xs);
    }
  }

  private static void addStraightCrossings(CoordinateSequence seq, double y, List<Double> xs) {
    for (int i = 0; i + 1 < seq.size(); i++) {
      double y0 = seq.getY(i), y1 = seq.getY(i + 1);
      if ((y0 < y && y < y1) || (y1 < y && y < y0)) {
        double x0 = seq.getX(i), x1 = seq.getX(i + 1);
        xs.add(x0 + (y - y0) * (x1 - x0) / (y1 - y0));
      }
    }
  }

  private static void addArcCrossing(double sx, double sy, double mx, double my,
                                     double ex, double ey, double y, List<Double> xs) {
    double[] g = arc(sx, sy, mx, my, ex, ey);
    if (g == null) {
      // degenerate -> straight chord
      if ((sy < y && y < ey) || (ey < y && y < sy)) {
        xs.add(sx + (y - sy) * (ex - sx) / (ey - sy));
      }
      return;
    }
    double cx = g[0], cy = g[1], r = g[2], delta = g[3];
    double dy = y - cy;
    if (Math.abs(dy) > r) return;
    double dxv = Math.sqrt(r * r - dy * dy);
    double a0 = Math.atan2(sy - cy, sx - cx);
    double[] cand = { cx - dxv, cx + dxv };
    for (int k = 0; k < cand.length; k++) {
      double xc = cand[k];
      double phi = Math.atan2(y - cy, xc - cx);
      double off = (delta >= 0) ? normTwoPi(phi - a0) : normTwoPi(a0 - phi);
      if (off <= Math.abs(delta)) {
        xs.add(xc);
      }
    }
  }

  // ---- arc geometry: [cx, cy, r, signedSweep] or null if degenerate -------

  private static double[] arc(double sx, double sy, double mx, double my,
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
    if (!Double.isFinite(r) || r == 0.0) return null;
    double a0 = Math.atan2(-cyL, -cxL);
    double a1 = Math.atan2(myL - cyL, mxL - cxL);
    double a2 = Math.atan2(eyL - cyL, exL - cxL);
    double midCcw = normTwoPi(a1 - a0);
    double endCcw = normTwoPi(a2 - a0);
    double delta = (midCcw <= endCcw) ? endCcw : endCcw - 2 * Math.PI;
    return new double[] { cxL + sx, cyL + sy, r, delta };
  }

  private static double normTwoPi(double a) {
    double twoPi = 2 * Math.PI;
    a = a % twoPi;
    if (a < 0) a += twoPi;
    return a;
  }
}
