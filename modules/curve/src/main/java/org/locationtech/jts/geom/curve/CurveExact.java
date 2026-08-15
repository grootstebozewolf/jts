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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

/**
 * Closed-form answers for the shapes a cheap check can recognise: a circular
 * disc, a single circular arc, a point against an arc. Package-private -- not
 * a new public API. {@link CurveOps} takes these only when they can answer;
 * anything else goes straight to the chord baseline. Trying and falling
 * through would pay both tools.
 */
final class CurveExact {

  private static final double TWO_PI = 2.0 * Math.PI;
  private static final double SWEEP_EPS = 1.0e-9;

  private CurveExact() { }

  /**
   * Exact convex hull, or {@code null} if this geometry is not a disc or a
   * single arc. A disc's hull is the disc; a single arc's hull is the slice
   * bounded by the arc and its chord -- a {@link CurvePolygon}, so the result
   * contains the arc instead of an inscribed polyline.
   */
  static Geometry convexHull(Geometry g) {
    if (g == null || g.isEmpty()) return null;
    CircularArcDensifier.Circle disc = circularDisc(g);
    if (disc != null) {
      return g instanceof CurvePolygon ? g.copy() : makeDisc(disc, g.getFactory());
    }
    if (g instanceof CircularString) {
      CircularString cs = (CircularString) g;
      if (isSingleArc(cs)) {
        return arcChordHull(cs);
      }
    }
    return null;
  }

  /**
   * Exact buffer of a circular disc, or {@code null} if {@code g} is not one.
   * Open arcs stay on {@code BufferOp}: an offset corridor is not a disc.
   */
  static Geometry buffer(Geometry g, double distance) {
    CircularArcDensifier.Circle disc = circularDisc(g);
    if (disc == null) return null;
    double r = disc.r + distance;
    if (r <= 0.0) return g.getFactory().createPolygon();
    return makeDisc(new CircularArcDensifier.Circle(disc.cx, disc.cy, r),
        g.getFactory());
  }

  /**
   * Exact distance, or {@code null} if the pair is not a shape this class
   * can answer (disc-disc, disc-point, point-arc, arc-arc, arc-segment).
   */
  static Double distance(Geometry a, Geometry b) {
    if (a == null || b == null || a.isEmpty() || b.isEmpty()) return null;
    CircularArcDensifier.Circle da = circularDisc(a);
    CircularArcDensifier.Circle db = circularDisc(b);
    if (da != null && db != null) {
      return Double.valueOf(Math.max(0.0,
          Math.hypot(da.cx - db.cx, da.cy - db.cy) - da.r - db.r));
    }
    if (da != null && b instanceof Point) {
      return Double.valueOf(pointToFilledDisc(((Point) b).getCoordinate(), da));
    }
    if (db != null && a instanceof Point) {
      return Double.valueOf(pointToFilledDisc(((Point) a).getCoordinate(), db));
    }
    Point pt = null;
    Geometry curve = null;
    if (a instanceof Point && isLinealCurve(b)) {
      pt = (Point) a;
      curve = b;
    } else if (b instanceof Point && isLinealCurve(a)) {
      pt = (Point) b;
      curve = a;
    }
    if (pt != null) {
      return Double.valueOf(pointToLinealCurve(pt.getCoordinate(), curve));
    }
    if (isLinealCurve(a) && isLinealCurve(b)) {
      return Double.valueOf(linealToLineal(a, b));
    }
    if (isLinealCurve(a) && isPlainLineal(b)) {
      return Double.valueOf(linealToPlain(a, (LineString) b));
    }
    if (isLinealCurve(b) && isPlainLineal(a)) {
      return Double.valueOf(linealToPlain(b, (LineString) a));
    }
    return null;
  }

  static CircularArcDensifier.Circle circularDisc(Geometry g) {
    if (!(g instanceof CurvePolygon)) return null;
    CurvePolygon cp = (CurvePolygon) g;
    if (cp.isEmpty() || cp.getNumInteriorRing() > 0) return null;
    return fullCircle(cp.getExteriorCurve());
  }

  static CircularArcDensifier.Circle fullCircle(LineString ring) {
    if (ring instanceof CircularString) {
      return fullCircle((CircularString) ring);
    }
    if (ring instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) ring;
      CircularArcDensifier.Circle found = null;
      double sweep = 0.0;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        LineString m = cc.getMemberN(i);
        if (!(m instanceof CircularString)) return null;
        CircularString cs = (CircularString) m;
        CircularArcDensifier.Circle c = sameCircle(cs, found);
        if (c == null) return null;
        found = c;
        sweep += totalSweep(cs);
      }
      if (found == null || !ring.isClosed()) return null;
      if (Math.abs(Math.abs(sweep) - TWO_PI) > SWEEP_EPS) return null;
      return found;
    }
    return null;
  }

  private static CircularArcDensifier.Circle fullCircle(CircularString cs) {
    if (cs.isEmpty() || !cs.isClosed() || cs.getNumPoints() < 5) return null;
    CircularArcDensifier.Circle c = sameCircle(cs, null);
    if (c == null) return null;
    if (Math.abs(Math.abs(totalSweep(cs)) - TWO_PI) > SWEEP_EPS) return null;
    return c;
  }

  private static CircularArcDensifier.Circle sameCircle(CircularString cs,
      CircularArcDensifier.Circle expected) {
    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    if (n < 3) return null;
    CircularArcDensifier.Circle found = expected;
    for (int i = 0; i + 2 < n; i += 2) {
      CircularArcDensifier.Circle c = CircularArcDensifier.Circle.fromThreePoints(
          seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2));
      if (c == null) return null;
      if (found == null) {
        found = c;
      } else if (Math.hypot(found.cx - c.cx, found.cy - c.cy) > 1.0e-9
          || Math.abs(found.r - c.r) > 1.0e-9) {
        return null;
      }
    }
    return found;
  }

  private static double totalSweep(CircularString cs) {
    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    double total = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      Coordinate start = seq.getCoordinate(i);
      Coordinate mid = seq.getCoordinate(i + 1);
      Coordinate end = seq.getCoordinate(i + 2);
      CircularArcDensifier.Circle c = CircularArcDensifier.Circle.fromThreePoints(
          start, mid, end);
      if (c == null) continue;
      double a0 = Math.atan2(start.y - c.cy, start.x - c.cx);
      double aMid = Math.atan2(mid.y - c.cy, mid.x - c.cx);
      double a1 = Math.atan2(end.y - c.cy, end.x - c.cx);
      boolean ccw = midInCcw(a0, aMid, a1);
      double sweep = ccw ? normPos(a1 - a0) : -normPos(a0 - a1);
      if (sweep == 0.0) sweep = ccw ? TWO_PI : -TWO_PI;
      total += sweep;
    }
    return total;
  }

  private static boolean isSingleArc(CircularString cs) {
    return !cs.isEmpty() && cs.getNumPoints() == 3
        && CircularArcDensifier.Circle.fromThreePoints(
            cs.getCoordinateN(0), cs.getCoordinateN(1), cs.getCoordinateN(2)) != null;
  }

  private static Geometry arcChordHull(CircularString cs) {
    GeometryFactory f = cs.getFactory();
    Coordinate start = cs.getCoordinateN(0);
    Coordinate end = cs.getCoordinateN(2);
    if (start.equals2D(end)) {
      return cs.copy();
    }
    LineString chord = f.createLineString(new Coordinate[] {
        new Coordinate(end), new Coordinate(start)
    });
    CompoundCurve shell = new CompoundCurve(
        new LineString[] { (CircularString) cs.copy(), chord }, f);
    return new CurvePolygon(shell, null, f);
  }

  static Geometry makeDisc(CircularArcDensifier.Circle c, GeometryFactory f) {
    Coordinate[] pts = new Coordinate[] {
        new Coordinate(c.cx + c.r, c.cy),
        new Coordinate(c.cx, c.cy + c.r),
        new Coordinate(c.cx - c.r, c.cy),
        new Coordinate(c.cx, c.cy - c.r),
        new Coordinate(c.cx + c.r, c.cy)
    };
    CircularString ring = new CircularString(
        f.getCoordinateSequenceFactory().create(pts), f);
    return new CurvePolygon(ring, null, f);
  }

  private static double pointToFilledDisc(Coordinate p,
      CircularArcDensifier.Circle c) {
    double d = Math.hypot(p.x - c.cx, p.y - c.cy) - c.r;
    return d < 0.0 ? 0.0 : d;
  }

  private static boolean isLinealCurve(Geometry g) {
    return g instanceof CircularString || g instanceof CompoundCurve;
  }

  private static boolean isPlainLineal(Geometry g) {
    return g instanceof LineString && !isLinealCurve(g);
  }

  private static double pointToLinealCurve(Coordinate p, Geometry curve) {
    if (curve instanceof CircularString) {
      return pointToCircularString(p, (CircularString) curve);
    }
    CompoundCurve cc = (CompoundCurve) curve;
    double min = Double.POSITIVE_INFINITY;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      if (m instanceof CircularString) {
        min = Math.min(min, pointToCircularString(p, (CircularString) m));
      } else {
        min = Math.min(min, pointToLineString(p, m));
      }
    }
    return min;
  }

  private static double pointToCircularString(Coordinate p, CircularString cs) {
    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    if (n < 3) return pointToLineString(p, cs);
    double min = Double.POSITIVE_INFINITY;
    for (int i = 0; i + 2 < n; i += 2) {
      min = Math.min(min, CircularArcDensifier.distancePointToArc(
          p, seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2)));
    }
    return min;
  }

  private static double pointToLineString(Coordinate p, LineString ls) {
    Coordinate[] c = ls.getCoordinates();
    double min = Double.POSITIVE_INFINITY;
    for (int i = 1; i < c.length; i++) {
      min = Math.min(min, CircularArcDensifier.distancePointToSegment(p, c[i - 1], c[i]));
    }
    return min;
  }

  private static double linealToLineal(Geometry a, Geometry b) {
    double min = Double.POSITIVE_INFINITY;
    int na = arcCount(a);
    int nb = arcCount(b);
    for (int i = 0; i < na; i++) {
      Coordinate[] aa = arcAt(a, i);
      for (int j = 0; j < nb; j++) {
        Coordinate[] bb = arcAt(b, j);
        min = Math.min(min, CircularArcDensifier.distanceArcToArc(
            aa[0], aa[1], aa[2], bb[0], bb[1], bb[2]));
      }
    }
    return min;
  }

  private static double linealToPlain(Geometry curve, LineString plain) {
    Coordinate[] p = plain.getCoordinates();
    double min = Double.POSITIVE_INFINITY;
    int n = arcCount(curve);
    for (int i = 0; i < n; i++) {
      Coordinate[] a = arcAt(curve, i);
      for (int j = 1; j < p.length; j++) {
        min = Math.min(min, distanceArcToSegment(a[0], a[1], a[2], p[j - 1], p[j]));
      }
    }
    return min;
  }

  private static double distanceArcToSegment(Coordinate a0, Coordinate a1,
      Coordinate a2, Coordinate s0, Coordinate s1) {
    double min = CircularArcDensifier.distancePointToSegment(a0, s0, s1);
    min = Math.min(min, CircularArcDensifier.distancePointToSegment(a2, s0, s1));
    min = Math.min(min, CircularArcDensifier.distancePointToArc(s0, a0, a1, a2));
    min = Math.min(min, CircularArcDensifier.distancePointToArc(s1, a0, a1, a2));
    return min;
  }

  private static int arcCount(Geometry g) {
    if (g instanceof CircularString) {
      int n = ((CircularString) g).getNumPoints();
      return n < 3 ? 0 : (n - 1) / 2;
    }
    CompoundCurve cc = (CompoundCurve) g;
    int total = 0;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      if (m instanceof CircularString) {
        int n = m.getNumPoints();
        if (n >= 3) total += (n - 1) / 2;
      } else if (m.getNumPoints() >= 2) {
        total += m.getNumPoints() - 1;
      }
    }
    return total;
  }

  /**
   * Triple (start, mid, end) for the i-th piece. Straight members are
   * returned as a colinear triple so {@link CircularArcDensifier} degrades
   * them to the chord.
   */
  private static Coordinate[] arcAt(Geometry g, int index) {
    if (g instanceof CircularString) {
      CoordinateSequence seq = ((CircularString) g).getCoordinateSequence();
      int i = index * 2;
      return new Coordinate[] {
          seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2)
      };
    }
    CompoundCurve cc = (CompoundCurve) g;
    int seen = 0;
    for (int m = 0; m < cc.getNumMembers(); m++) {
      LineString mem = cc.getMemberN(m);
      if (mem instanceof CircularString) {
        int n = mem.getNumPoints();
        int arcs = n < 3 ? 0 : (n - 1) / 2;
        if (index < seen + arcs) {
          int i = (index - seen) * 2;
          return new Coordinate[] {
              mem.getCoordinateN(i), mem.getCoordinateN(i + 1), mem.getCoordinateN(i + 2)
          };
        }
        seen += arcs;
      } else {
        int segs = Math.max(0, mem.getNumPoints() - 1);
        if (index < seen + segs) {
          int i = index - seen;
          Coordinate a = mem.getCoordinateN(i);
          Coordinate b = mem.getCoordinateN(i + 1);
          return new Coordinate[] { a, mid(a, b), b };
        }
        seen += segs;
      }
    }
    throw new IndexOutOfBoundsException("arc " + index);
  }

  private static Coordinate mid(Coordinate a, Coordinate b) {
    return new Coordinate((a.x + b.x) / 2.0, (a.y + b.y) / 2.0);
  }

  private static boolean midInCcw(double a0, double aMid, double a1) {
    return normPos(aMid - a0) < normPos(a1 - a0);
  }

  private static double normPos(double angle) {
    double twoPi = TWO_PI;
    angle = angle % twoPi;
    if (angle < 0.0) angle += twoPi;
    return angle;
  }
}
