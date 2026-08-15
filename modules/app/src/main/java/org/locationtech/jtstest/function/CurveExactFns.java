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
package org.locationtech.jtstest.function;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.curve.CircularArcDensifier;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurvePolygon;

/**
 * Closed forms for the TestBuilder distance / MIC statics. Package-private;
 * not a public JTS API. A laser is returned only when a cheap shape check
 * can answer -- otherwise the caller stays on the chord path without
 * paying a failed attempt.
 */
final class CurveExactFns {

  private static final double TWO_PI = 2.0 * Math.PI;
  private static final double SWEEP_EPS = 1.0e-9;

  private CurveExactFns() { }

  /** {@code {cx, cy, r}} of a hole-free circular {@link CurvePolygon}, or null. */
  static double[] circularDisc(Geometry g) {
    if (!(g instanceof CurvePolygon)) return null;
    CurvePolygon cp = (CurvePolygon) g;
    if (cp.isEmpty() || cp.getNumInteriorRing() > 0) return null;
    return fullCircle(cp.getExteriorCurve());
  }

  static Double orientedHausdorff(Geometry a, Geometry b) {
    double[] da = circularDisc(a);
    double[] db = circularDisc(b);
    if (da != null && db != null) {
      return Double.valueOf(CircularArcDensifier.directedHausdorffCircleToCircle(
          da[0], da[1], da[2], db[0], db[1], db[2]));
    }
    if (isSingleArc(a) && isSingleSegment(b)) {
      CircularString cs = (CircularString) a;
      LineString ls = (LineString) b;
      return Double.valueOf(CircularArcDensifier.directedHausdorffArcToSegment(
          cs.getCoordinateN(0), cs.getCoordinateN(1), cs.getCoordinateN(2),
          ls.getCoordinateN(0), ls.getCoordinateN(1)));
    }
    if (isSingleArc(b) && isSingleSegment(a)) {
      // oriented is from A to B; a segment-to-arc form is not this laser.
      return null;
    }
    return null;
  }

  static Coordinate[] nearestPoints(Geometry a, Geometry b) {
    if (a instanceof Point && isSingleArc(b)) {
      Coordinate[] pts = pointToArc((Point) a, (CircularString) b);
      return new Coordinate[] { pts[1], pts[0] };
    }
    if (b instanceof Point && isSingleArc(a)) {
      return pointToArc((Point) b, (CircularString) a);
    }
    double[] da = circularDisc(a);
    double[] db = circularDisc(b);
    if (da != null && db != null) {
      return discToDisc(da, db);
    }
    return null;
  }

  /** Radius of the MIC of a circular disc, or null. */
  static Double micRadius(Geometry g) {
    double[] d = circularDisc(g);
    return d == null ? null : Double.valueOf(d[2]);
  }

  /** Centre of the MIC of a circular disc, or null. */
  static Coordinate micCenter(Geometry g) {
    double[] d = circularDisc(g);
    return d == null ? null : new Coordinate(d[0], d[1]);
  }

  /** A boundary point of a circular disc (for the radius line), or null. */
  static Coordinate micRadiusPoint(Geometry g) {
    double[] d = circularDisc(g);
    return d == null ? null : new Coordinate(d[0] + d[2], d[1]);
  }

  private static Coordinate[] pointToArc(Point p, CircularString cs) {
    Coordinate q = CircularArcDensifier.nearestPointOnArc(
        p.getCoordinate(),
        cs.getCoordinateN(0), cs.getCoordinateN(1), cs.getCoordinateN(2));
    return new Coordinate[] { q, p.getCoordinate() };
  }

  /**
   * Areal (filled-disc) nearest points. Overlap or nest is distance 0:
   * a lens node when the boundaries cross, the smaller centre when one
   * disc contains the other. Facet / {@code IndexedFacetDistance}
   * callers must not use this -- they keep boundary semantics.
   */
  private static Coordinate[] discToDisc(double[] a, double[] b) {
    double dx = b[0] - a[0];
    double dy = b[1] - a[1];
    double d = Math.hypot(dx, dy);
    double ra = a[2];
    double rb = b[2];
    if (d <= ra + rb) {
      if (d == 0.0 || d + Math.min(ra, rb) <= Math.max(ra, rb)) {
        double[] inner = ra <= rb ? a : b;
        Coordinate c = new Coordinate(inner[0], inner[1]);
        return new Coordinate[] { c, new Coordinate(c) };
      }
      double along = (ra * ra - rb * rb + d * d) / (2.0 * d);
      double h2 = ra * ra - along * along;
      double ux = dx / d;
      double uy = dy / d;
      double mx = a[0] + along * ux;
      double my = a[1] + along * uy;
      Coordinate lens = h2 <= 0.0
          ? new Coordinate(mx, my)
          : new Coordinate(mx + Math.sqrt(h2) * -uy, my + Math.sqrt(h2) * ux);
      return new Coordinate[] { lens, new Coordinate(lens) };
    }
    double ux = dx / d;
    double uy = dy / d;
    return new Coordinate[] {
        new Coordinate(a[0] + ra * ux, a[1] + ra * uy),
        new Coordinate(b[0] - rb * ux, b[1] - rb * uy)
    };
  }

  private static boolean isSingleArc(Geometry g) {
    if (!(g instanceof CircularString)) return false;
    CircularString cs = (CircularString) g;
    return !cs.isEmpty() && cs.getNumPoints() == 3
        && CircularArcDensifier.circumcircle(
            cs.getCoordinateN(0), cs.getCoordinateN(1), cs.getCoordinateN(2)) != null;
  }

  private static boolean isSingleSegment(Geometry g) {
    return g instanceof LineString && !(g instanceof CircularString)
        && g.getNumPoints() == 2;
  }

  private static double[] fullCircle(LineString ring) {
    if (!(ring instanceof CircularString)) return null;
    CircularString cs = (CircularString) ring;
    if (cs.isEmpty() || !cs.isClosed() || cs.getNumPoints() < 5) return null;
    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    double[] found = null;
    double sweep = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      Coordinate start = seq.getCoordinate(i);
      Coordinate mid = seq.getCoordinate(i + 1);
      Coordinate end = seq.getCoordinate(i + 2);
      double[] c = CircularArcDensifier.circumcircle(start, mid, end);
      if (c == null) return null;
      if (found == null) {
        found = c;
      } else if (Math.hypot(found[0] - c[0], found[1] - c[1]) > 1.0e-9
          || Math.abs(found[2] - c[2]) > 1.0e-9) {
        return null;
      }
      sweep += signedSweep(start, mid, end, c);
    }
    if (found == null || Math.abs(Math.abs(sweep) - TWO_PI) > SWEEP_EPS) return null;
    return found;
  }

  private static double signedSweep(Coordinate start, Coordinate mid, Coordinate end,
      double[] c) {
    double a0 = Math.atan2(start.y - c[1], start.x - c[0]);
    double aMid = Math.atan2(mid.y - c[1], mid.x - c[0]);
    double a1 = Math.atan2(end.y - c[1], end.x - c[0]);
    boolean ccw = normPos(aMid - a0) < normPos(a1 - a0);
    double sweep = ccw ? normPos(a1 - a0) : -normPos(a0 - a1);
    if (sweep == 0.0) sweep = ccw ? TWO_PI : -TWO_PI;
    return sweep;
  }

  private static double normPos(double angle) {
    angle = angle % TWO_PI;
    if (angle < 0.0) angle += TWO_PI;
    return angle;
  }
}
