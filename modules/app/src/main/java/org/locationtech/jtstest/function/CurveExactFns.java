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

import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.curve.CircularArcDensifier;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;

/**
 * Closed forms for the TestBuilder distance / MIC statics. Package-private;
 * not a public JTS API. A laser is returned only when a cheap shape check
 * can answer -- otherwise the caller stays on the chord path without
 * paying a failed attempt. MIC answers a circular disc (ML.0) or a
 * certified stadium (ML.1: cap radius, midpoint of the cap centres).
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
    if (!hasOrientedHausdorffLaser(a, b)) return null;
    return Double.valueOf(DiscreteHausdorffDistance.orientedDistance(a, b));
  }

  /** Certified pairs the public {@link DiscreteHausdorffDistance} answers exactly. */
  static boolean hasOrientedHausdorffLaser(Geometry a, Geometry b) {
    if (circularDisc(a) != null && circularDisc(b) != null) return true;
    if (isSingleArc(a) && isSingleSegment(b)) return true;
    return false;
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

  /** Radius of the MIC of a circular disc or certified stadium, or null. */
  static Double micRadius(Geometry g) {
    double[] d = mic(g);
    return d == null ? null : Double.valueOf(d[2]);
  }

  /** Centre of the MIC of a circular disc or certified stadium, or null. */
  static Coordinate micCenter(Geometry g) {
    double[] d = mic(g);
    return d == null ? null : new Coordinate(d[0], d[1]);
  }

  /**
   * A boundary point of the MIC (for the radius line), or null.
   * Disc and stadium both take the +x point on the circle so the
   * radius line has length {@code r}.
   */
  static Coordinate micRadiusPoint(Geometry g) {
    double[] d = mic(g);
    return d == null ? null : new Coordinate(d[0] + d[2], d[1]);
  }

  /**
   * Disc first (ML.0 bit-identical), then a certified stadium.
   * A miss is {@code null} -- never a densified grid flagged exact.
   */
  private static double[] mic(Geometry g) {
    double[] d = circularDisc(g);
    if (d != null) return d;
    return stadiumMic(g);
  }

  /**
   * Same certify as {@code org.locationtech.jts.geom.curve.StadiumMic}:
   * hole-free four-member CompoundCurve, two equal-r semicircular caps
   * via {@link CircularArcDensifier#circumcircle}, two parallel sides
   * distance {@code 2r} apart, caps facing outward. Centre is the
   * midpoint of the cap centres.
   */
  static double[] stadiumMic(Geometry g) {
    if (!(g instanceof CurvePolygon)) return null;
    CurvePolygon cp = (CurvePolygon) g;
    if (cp.isEmpty() || cp.getNumInteriorRing() > 0) return null;
    LineString ring = cp.getExteriorCurve();
    if (!(ring instanceof CompoundCurve) || !ring.isClosed()) return null;
    CompoundCurve cc = (CompoundCurve) ring;
    if (cc.getNumMembers() != 4) return null;

    LineString[] members = new LineString[4];
    for (int i = 0; i < 4; i++) {
      members[i] = cc.getMemberN(i);
    }
    if (!alternatingCapsAndSides(members)) return null;
    if (!junctionsMeet(members)) return null;

    CircularString[] caps = new CircularString[2];
    LineString[] sides = new LineString[2];
    splitMembers(members, caps, sides);
    if (!isPlainSegment(sides[0]) || !isPlainSegment(sides[1])) return null;

    double[] c0 = sameCircle(caps[0]);
    double[] c1 = sameCircle(caps[1]);
    if (c0 == null || c1 == null) return null;
    if (Math.abs(c0[2] - c1[2]) > 1.0e-9 || c0[2] <= 0.0) return null;
    double r = c0[2];
    if (!isSemicircle(caps[0]) || !isSemicircle(caps[1])) return null;
    if (!parallelSides(sides[0], sides[1])) return null;
    if (Math.abs(sideDistance(sides[0], sides[1]) - 2.0 * r) > 1.0e-9) {
      return null;
    }
    if (!onMedial(c0, sides, r) || !onMedial(c1, sides, r)) return null;
    if (!capsFaceOutward(c0, c1, caps[0], caps[1])) return null;

    return new double[] {
        0.5 * (c0[0] + c1[0]), 0.5 * (c0[1] + c1[1]), r
    };
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

  private static boolean alternatingCapsAndSides(LineString[] m) {
    boolean a0 = m[0] instanceof CircularString;
    boolean a1 = m[1] instanceof CircularString;
    boolean a2 = m[2] instanceof CircularString;
    boolean a3 = m[3] instanceof CircularString;
    return a0 != a1 && a0 == a2 && a1 == a3;
  }

  private static boolean junctionsMeet(LineString[] m) {
    boolean ok = true;
    for (int i = 0; i < 4; i++) {
      Coordinate end = m[i].getCoordinateN(m[i].getNumPoints() - 1);
      Coordinate start = m[(i + 1) % 4].getCoordinateN(0);
      ok = ok && end.distance(start) <= 1.0e-9;
    }
    return ok;
  }

  private static void splitMembers(LineString[] members, CircularString[] caps,
      LineString[] sides) {
    int ic = 0;
    int is = 0;
    for (int i = 0; i < 4; i++) {
      if (members[i] instanceof CircularString) {
        caps[ic] = (CircularString) members[i];
        ic++;
      }
      else {
        sides[is] = members[i];
        is++;
      }
    }
  }

  /**
   * Same circle test as {@link #fullCircle}: every 3-point window
   * shares one {@link CircularArcDensifier#circumcircle}.
   */
  private static double[] sameCircle(CircularString cs) {
    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    if (n < 3) return null;
    double[] found = null;
    for (int i = 0; i + 2 < n; i += 2) {
      double[] c = CircularArcDensifier.circumcircle(
          seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2));
      if (c == null) return null;
      if (found == null) {
        found = c;
      } else if (Math.hypot(found[0] - c[0], found[1] - c[1]) > 1.0e-9
          || Math.abs(found[2] - c[2]) > 1.0e-9) {
        return null;
      }
    }
    return found;
  }

  private static boolean isSemicircle(CircularString cs) {
    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    double sweep = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      Coordinate start = seq.getCoordinate(i);
      Coordinate mid = seq.getCoordinate(i + 1);
      Coordinate end = seq.getCoordinate(i + 2);
      double[] c = CircularArcDensifier.circumcircle(start, mid, end);
      if (c != null) {
        sweep += signedSweep(start, mid, end, c);
      }
    }
    return Math.abs(Math.abs(sweep) - Math.PI) <= SWEEP_EPS;
  }

  private static boolean isPlainSegment(LineString ls) {
    return !(ls instanceof CircularString) && ls.getNumPoints() == 2
        && !ls.getCoordinateN(0).equals2D(ls.getCoordinateN(1));
  }

  private static boolean parallelSides(LineString a, LineString b) {
    double dx0 = a.getCoordinateN(1).x - a.getCoordinateN(0).x;
    double dy0 = a.getCoordinateN(1).y - a.getCoordinateN(0).y;
    double dx1 = b.getCoordinateN(1).x - b.getCoordinateN(0).x;
    double dy1 = b.getCoordinateN(1).y - b.getCoordinateN(0).y;
    double cross = dx0 * dy1 - dy0 * dx1;
    double scale = Math.hypot(dx0, dy0) * Math.hypot(dx1, dy1);
    return Math.abs(cross) <= 1.0e-9 * Math.max(1.0, scale);
  }

  private static double sideDistance(LineString a, LineString b) {
    Coordinate a0 = a.getCoordinateN(0);
    Coordinate a1 = a.getCoordinateN(1);
    Coordinate b0 = b.getCoordinateN(0);
    double dx = a1.x - a0.x;
    double dy = a1.y - a0.y;
    double len = Math.hypot(dx, dy);
    if (len == 0.0) return Double.POSITIVE_INFINITY;
    return Math.abs((b0.x - a0.x) * dy - (b0.y - a0.y) * dx) / len;
  }

  private static boolean onMedial(double[] c, LineString[] sides, double r) {
    return Math.abs(pointToSide(c[0], c[1], sides[0]) - r) <= 1.0e-9
        && Math.abs(pointToSide(c[0], c[1], sides[1]) - r) <= 1.0e-9;
  }

  private static double pointToSide(double x, double y, LineString side) {
    Coordinate a = side.getCoordinateN(0);
    Coordinate b = side.getCoordinateN(1);
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double len = Math.hypot(dx, dy);
    if (len == 0.0) return Double.POSITIVE_INFINITY;
    return Math.abs((x - a.x) * dy - (y - a.y) * dx) / len;
  }

  private static boolean capsFaceOutward(double[] c0, double[] c1,
      CircularString cap0, CircularString cap1) {
    Coordinate m0 = cap0.getCoordinateN(1);
    Coordinate m1 = cap1.getCoordinateN(1);
    double d0 = (m0.x - c0[0]) * (c1[0] - c0[0])
        + (m0.y - c0[1]) * (c1[1] - c0[1]);
    double d1 = (m1.x - c1[0]) * (c0[0] - c1[0])
        + (m1.y - c1[1]) * (c0[1] - c1[1]);
    return d0 < -1.0e-9 && d1 < -1.0e-9;
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
