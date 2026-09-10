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

/**
 * Closed-form MIC of a certified stadium. Package-private -- not a
 * public JTS API. A miss returns {@code null} so the caller stays on
 * the chord path; this class never densifies.
 * <p>
 * A stadium here is a hole-free {@link CurvePolygon} whose exterior is
 * a closed {@link CompoundCurve} of four members, alternating two
 * {@link CircularString} caps and two plain {@link LineString} sides:
 * <ul>
 * <li>both caps lie on the same circle test as a disc
 *     ({@link CurveExact#sameCircle}), with equal radius {@code r}</li>
 * <li>each cap is a semicircle ({@link CurveExact#totalSweep} of
 *     {@code ±π})</li>
 * <li>the sides are parallel single segments, distance {@code 2r}
 *     apart</li>
 * <li>each cap centre sits on the medial line (distance {@code r} to
 *     both sides) and the mid-arc points outward</li>
 * </ul>
 * Member order may start on a cap or a side. CW and CCW both certify
 * ({@code STADIUM_FOUR} is CCW; {@code STADIUM_ODD} is CW).
 * {@code HALF_DISC} has two members and stamps.
 * <p>
 * The MIC radius is the cap radius. The centre is not unique -- any
 * point on the medial segment between the cap centres is a MIC centre.
 * This cell picks the <b>midpoint of the two cap centres</b>.
 */
final class StadiumMic {

  private static final double EPS = 1.0e-9;
  private static final double SWEEP_EPS = 1.0e-9;
  private static final double PI = Math.PI;

  private StadiumMic() { }

  /**
   * {@code Circle(midpoint, r)} of a certified stadium, or {@code null}.
   */
  static CircularArcDensifier.Circle compute(Geometry g) {
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
    split(members, caps, sides);
    if (!isSegment(sides[0]) || !isSegment(sides[1])) return null;

    CircularArcDensifier.Circle c0 = CurveExact.sameCircle(caps[0], null);
    CircularArcDensifier.Circle c1 = CurveExact.sameCircle(caps[1], null);
    if (c0 == null || c1 == null) return null;
    if (Math.abs(c0.r - c1.r) > EPS || c0.r <= 0.0) return null;
    double r = c0.r;
    if (!isSemicircle(caps[0]) || !isSemicircle(caps[1])) return null;
    if (!parallel(sides[0], sides[1])) return null;
    if (Math.abs(lineDistance(sides[0], sides[1]) - 2.0 * r) > EPS) {
      return null;
    }
    if (!onMedial(c0, sides, r) || !onMedial(c1, sides, r)) return null;
    if (!capsFaceOutward(c0, c1, caps[0], caps[1])) return null;

    return new CircularArcDensifier.Circle(
        0.5 * (c0.cx + c1.cx), 0.5 * (c0.cy + c1.cy), r);
  }

  /**
   * ABAB or BABA in a 4-cycle. AABB (two caps adjacent, two sides
   * adjacent) is not a stadium.
   */
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
      ok = ok && end.distance(start) <= EPS;
    }
    return ok;
  }

  private static void split(LineString[] members, CircularString[] caps,
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

  private static boolean isSemicircle(CircularString cs) {
    double sweep = CurveExact.totalSweep(cs);
    return Math.abs(Math.abs(sweep) - PI) <= SWEEP_EPS;
  }

  private static boolean isSegment(LineString ls) {
    return !(ls instanceof CircularString) && ls.getNumPoints() == 2
        && !ls.getCoordinateN(0).equals2D(ls.getCoordinateN(1));
  }

  private static boolean parallel(LineString a, LineString b) {
    double dx0 = a.getCoordinateN(1).x - a.getCoordinateN(0).x;
    double dy0 = a.getCoordinateN(1).y - a.getCoordinateN(0).y;
    double dx1 = b.getCoordinateN(1).x - b.getCoordinateN(0).x;
    double dy1 = b.getCoordinateN(1).y - b.getCoordinateN(0).y;
    double cross = dx0 * dy1 - dy0 * dx1;
    double scale = Math.hypot(dx0, dy0) * Math.hypot(dx1, dy1);
    return Math.abs(cross) <= EPS * Math.max(1.0, scale);
  }

  private static double lineDistance(LineString a, LineString b) {
    Coordinate a0 = a.getCoordinateN(0);
    Coordinate a1 = a.getCoordinateN(1);
    Coordinate b0 = b.getCoordinateN(0);
    double dx = a1.x - a0.x;
    double dy = a1.y - a0.y;
    double len = Math.hypot(dx, dy);
    if (len == 0.0) return Double.POSITIVE_INFINITY;
    return Math.abs((b0.x - a0.x) * dy - (b0.y - a0.y) * dx) / len;
  }

  private static boolean onMedial(CircularArcDensifier.Circle c,
      LineString[] sides, double r) {
    return Math.abs(pointToLine(c.cx, c.cy, sides[0]) - r) <= EPS
        && Math.abs(pointToLine(c.cx, c.cy, sides[1]) - r) <= EPS;
  }

  private static double pointToLine(double x, double y, LineString side) {
    Coordinate a = side.getCoordinateN(0);
    Coordinate b = side.getCoordinateN(1);
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double len = Math.hypot(dx, dy);
    if (len == 0.0) return Double.POSITIVE_INFINITY;
    return Math.abs((x - a.x) * dy - (y - a.y) * dx) / len;
  }

  /**
   * Each cap's first mid-control (a point on the arc) must lie on the
   * opposite side of that cap's centre from the other cap -- the
   * rounded ends, not inward bites.
   */
  private static boolean capsFaceOutward(CircularArcDensifier.Circle c0,
      CircularArcDensifier.Circle c1, CircularString cap0,
      CircularString cap1) {
    Coordinate m0 = cap0.getCoordinateN(1);
    Coordinate m1 = cap1.getCoordinateN(1);
    double d0 = (m0.x - c0.cx) * (c1.cx - c0.cx)
        + (m0.y - c0.cy) * (c1.cy - c0.cy);
    double d1 = (m1.x - c1.cx) * (c0.cx - c1.cx)
        + (m1.y - c1.cy) * (c0.cy - c1.cy);
    return d0 < -EPS && d1 < -EPS;
  }
}
