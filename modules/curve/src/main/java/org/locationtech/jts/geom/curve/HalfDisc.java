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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Location;

/**
 * Certified half-disc: hole-free {@link CurvePolygon} whose shell is a
 * two-member {@link CompoundCurve} — one semicircular {@link CircularString}
 * and one plain diameter segment. Package-private. A miss returns
 * {@code null} so the caller linearises; this class never densifies.
 * <p>
 * R.2 cell. Not a stadium ({@link StadiumMic}); not a full disc.
 */
final class HalfDisc {

  private static final double EPS = 1.0e-9;
  private static final double PI = Math.PI;

  final CircularArcDensifier.Circle circle;
  final Coordinate a;
  final Coordinate b;
  final Coordinate mid;
  /** {@code sign(cross(b-a, mid-a))} — open half-plane of the interior. */
  final double side;

  private HalfDisc(CircularArcDensifier.Circle circle, Coordinate a,
      Coordinate b, Coordinate mid, double side) {
    this.circle = circle;
    this.a = a;
    this.b = b;
    this.mid = mid;
    this.side = side;
  }

  /**
   * Recognise a half-disc, or {@code null}.
   */
  static HalfDisc of(Geometry g) {
    if (!(g instanceof CurvePolygon)) return null;
    CurvePolygon cp = (CurvePolygon) g;
    if (cp.isEmpty() || cp.getNumInteriorRing() > 0) return null;
    LineString ring = cp.getExteriorCurve();
    if (!(ring instanceof CompoundCurve) || !ring.isClosed()) return null;
    CompoundCurve cc = (CompoundCurve) ring;
    if (cc.getNumMembers() != 2) return null;

    LineString m0 = cc.getMemberN(0);
    LineString m1 = cc.getMemberN(1);
    CircularString cap;
    LineString diameter;
    if (m0 instanceof CircularString && isPlainSegment(m1)) {
      cap = (CircularString) m0;
      diameter = m1;
    } else if (m1 instanceof CircularString && isPlainSegment(m0)) {
      cap = (CircularString) m1;
      diameter = m0;
    } else {
      return null;
    }
    if (cap.getNumPoints() != 3) return null;
    if (Math.abs(Math.abs(CurveExact.totalSweep(cap)) - PI) > 1.0e-9) {
      return null;
    }
    CircularArcDensifier.Circle c = CurveExact.sameCircle(cap, null);
    if (c == null || c.r <= 0.0) return null;

    Coordinate a = diameter.getCoordinateN(0);
    Coordinate b = diameter.getCoordinateN(1);
    Coordinate cs0 = cap.getCoordinateN(0);
    Coordinate cs2 = cap.getCoordinateN(2);
    // Diameter must join the arc ends (either orientation).
    boolean endsMatch =
        (a.equals2D(cs0) && b.equals2D(cs2))
            || (a.equals2D(cs2) && b.equals2D(cs0));
    if (!endsMatch) return null;
    // Diameter is a diameter of the circle (midpoint = centre).
    if (Math.hypot(0.5 * (a.x + b.x) - c.cx, 0.5 * (a.y + b.y) - c.cy) > EPS) {
      return null;
    }
    if (Math.abs(a.distance(b) - 2.0 * c.r) > EPS) return null;

    Coordinate mid = cap.getCoordinateN(1);
    double side = cross(a, b, mid);
    if (Math.abs(side) <= EPS) return null;
    return new HalfDisc(c, a, b, mid, side);
  }

  /**
   * {@link Location#INTERIOR}, {@link Location#BOUNDARY}, or
   * {@link Location#EXTERIOR}. Boundary is the semicircle ∪ diameter.
   */
  int locate(Coordinate p) {
    double r2 = circle.r * circle.r;
    double dx = p.x - circle.cx;
    double dy = p.y - circle.cy;
    double d2 = dx * dx + dy * dy;

    if (onDiameter(p)) return Location.BOUNDARY;
    if (d2 > r2 + 1.0e-12) return Location.EXTERIOR;
    if (Math.abs(d2 - r2) <= 1.0e-12) {
      // On the circle: boundary only on the cap sweep.
      if (onCapSweep(p)) return Location.BOUNDARY;
      return Location.EXTERIOR;
    }
    // Strictly inside the circle: interior iff open half-plane of mid.
    double s = cross(a, b, p);
    if (s * side > 0.0) return Location.INTERIOR;
    return Location.EXTERIOR;
  }

  private boolean onDiameter(Coordinate p) {
    double len = a.distance(b);
    if (len == 0.0) return p.equals2D(a);
    double t = ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y))
        / (len * len);
    if (t < -1.0e-12 || t > 1.0 + 1.0e-12) return false;
    Coordinate proj = new Coordinate(
        a.x + t * (b.x - a.x), a.y + t * (b.y - a.y));
    return p.distance(proj) <= 1.0e-9;
  }

  private boolean onCapSweep(Coordinate p) {
    Coordinate s = a;
    Coordinate e = b;
    double a0 = Math.atan2(s.y - circle.cy, s.x - circle.cx);
    double aMid = Math.atan2(mid.y - circle.cy, mid.x - circle.cx);
    double a1 = Math.atan2(e.y - circle.cy, e.x - circle.cx);
    // If mid is not on the a→b CCW sweep, flip ends so mid lies on the sweep.
    if (normPos(aMid - a0) >= normPos(a1 - a0)) {
      s = b;
      e = a;
      a0 = Math.atan2(s.y - circle.cy, s.x - circle.cx);
      a1 = Math.atan2(e.y - circle.cy, e.x - circle.cx);
    }
    boolean ccw = normPos(aMid - a0) < normPos(a1 - a0);
    double sweep = ccw ? normPos(a1 - a0) : normPos(a0 - a1);
    if (sweep == 0.0) sweep = 2.0 * Math.PI;
    double angle = Math.atan2(p.y - circle.cy, p.x - circle.cx);
    double travelled = ccw ? normPos(angle - a0) : normPos(a0 - angle);
    return travelled <= sweep + 1.0e-12;
  }

  private static boolean isPlainSegment(LineString ls) {
    return !(ls instanceof CircularString) && !(ls instanceof CompoundCurve)
        && ls.getNumPoints() == 2;
  }

  private static double cross(Coordinate a, Coordinate b, Coordinate p) {
    return (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x);
  }

  private static double normPos(double angle) {
    double twoPi = 2.0 * Math.PI;
    angle = angle % twoPi;
    if (angle < 0.0) angle += twoPi;
    return angle;
  }
}
