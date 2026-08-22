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

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateArrays;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Exact convex hull of circular arcs plus straight members: a
 * {@link CurvePolygon} whose shell is the exposed arcs and the
 * supporting tangent / chord segments. A disc stays a disc; a stadium
 * stays the two caps plus the two outer tangents.
 * <p>
 * Package-private -- not a new public API. {@link CurveExact} takes
 * this only when every member is a circular arc or a straight
 * segment. A clothoid, or any mix this class cannot certify, is
 * {@code null} so {@link CurveOps} can take the chords alone. The
 * densify path is never flagged exact.
 */
final class CurveConvexHull {

  private static final double TWO_PI = 2.0 * Math.PI;
  private static final double PT_EPS = 1.0e-9;
  private static final double ANG_EPS = 1.0e-9;
  private static final double TURN_EPS = 1.0e-10;
  private static final double CROSS_EPS = 1.0e-8;

  private CurveConvexHull() { }

  /**
   * Exact hull, or {@code null} if {@code g} is not a circular +
   * straight mix this class can answer.
   */
  static Geometry hull(Geometry g) {
    if (g == null || g.isEmpty()) return null;
    List<Site> points = new ArrayList<Site>();
    List<Site> arcs = new ArrayList<Site>();
    if (!collect(g, points, arcs)) return null;
    if (arcs.isEmpty()) return null;
    GeometryFactory f = g.getFactory();
    List<Member> ring = wrap(points, arcs);
    Geometry built = (ring == null || ring.isEmpty()) ? null : build(ring, f, arcs);
    if (built == null) {
      ring = wrapFromPointHull(points, arcs, f);
      if (ring != null && !ring.isEmpty()) {
        built = build(ring, f, arcs);
      }
    }
    return built;
  }

  private static boolean collect(Geometry g, List<Site> points, List<Site> arcs) {
    if (g == null || g.isEmpty()) return true;
    if (g instanceof ClothoidSegment) return false;
    if (g instanceof CircularString) {
      return collectCircular((CircularString) g, points, arcs);
    }
    if (g instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) g;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        if (!collect(cc.getMemberN(i), points, arcs)) return false;
      }
      return true;
    }
    if (g instanceof CurvePolygon) {
      return collect(((CurvePolygon) g).getExteriorCurve(), points, arcs);
    }
    if (g instanceof Point) {
      addPoint(points, ((Point) g).getCoordinate());
      return true;
    }
    if (g instanceof LineString) {
      addLineVertices(points, (LineString) g);
      return true;
    }
    if (g instanceof Polygon) {
      return collect(((Polygon) g).getExteriorRing(), points, arcs);
    }
    int n = g.getNumGeometries();
    if (n >= 1 && g.getGeometryN(0) != g) {
      for (int i = 0; i < n; i++) {
        if (!collect(g.getGeometryN(i), points, arcs)) return false;
      }
      return true;
    }
    return false;
  }

  private static boolean collectCircular(CircularString cs, List<Site> points,
      List<Site> arcs) {
    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    if (n < 3) {
      addLineVertices(points, cs);
      return true;
    }
    for (int i = 0; i + 2 < n; i += 2) {
      Coordinate a = seq.getCoordinate(i);
      Coordinate b = seq.getCoordinate(i + 1);
      Coordinate c = seq.getCoordinate(i + 2);
      CircularArcDensifier.Circle circ =
          CircularArcDensifier.Circle.fromThreePoints(a, b, c);
      if (circ == null) {
        addPoint(points, a);
        addPoint(points, c);
      }
      else {
        arcs.add(Site.arc(circ, a, b, c));
        addPoint(points, a);
        addPoint(points, c);
      }
    }
    return true;
  }

  private static void addLineVertices(List<Site> points, LineString ls) {
    Coordinate[] c = ls.getCoordinates();
    for (int i = 0; i < c.length; i++) {
      addPoint(points, c[i]);
    }
  }

  private static void addPoint(List<Site> points, Coordinate p) {
    if (p == null) return;
    for (int i = 0; i < points.size(); i++) {
      if (points.get(i).c.distance(p) <= PT_EPS) return;
    }
    points.add(Site.point(p));
  }

  private static List<Member> wrap(List<Site> points, List<Site> arcs) {
    Start start = findStart(points, arcs);
    if (start == null) return null;
    List<Member> ring = new ArrayList<Member>();
    Coordinate curr = start.p;
    double currAng = start.ang;
    boolean riding = start.riding;
    Site rideArc = start.arc;
    Coordinate inDir = new Coordinate(1.0, 0.0);
    int guard = (points.size() + arcs.size()) * 4 + 8;
    for (int step = 0; step < guard; step++) {
      Cand best = bestCand(curr, currAng, riding, rideArc, inDir, points, arcs);
      if (best == null) return null;
      if (best.rideArc != null && best.sweep > ANG_EPS) {
        ring.add(Member.arc(best.rideArc, currAng, best.leaveAng, curr, best.leave));
        curr = best.leave;
        currAng = best.leaveAng;
      }
      if (best.dest.distance(start.p) <= PT_EPS && !ring.isEmpty()) {
        if (best.leave.distance(best.dest) > PT_EPS) {
          ring.add(Member.seg(best.leave, best.dest));
        }
        return ring;
      }
      if (best.leave.distance(best.dest) > PT_EPS) {
        ring.add(Member.seg(best.leave, best.dest));
      }
      inDir = new Coordinate(best.dest.x - best.leave.x, best.dest.y - best.leave.y);
      if (best.destSite != null && best.destSite.isArc) {
        curr = best.dest;
        currAng = best.destAng;
        riding = best.destSite.canRideCcw(currAng);
        rideArc = riding ? best.destSite : null;
      }
      else {
        curr = best.dest;
        riding = false;
        rideArc = null;
        Site on = arcThrough(curr, arcs);
        if (on != null && on.canRideCcw(on.angleOf(curr))) {
          riding = true;
          rideArc = on;
          currAng = on.angleOf(curr);
        }
      }
      if (curr.distance(start.p) <= PT_EPS && !ring.isEmpty()) {
        return ring;
      }
    }
    return null;
  }

  /**
   * Fallback when gift-wrap cannot close (S-bowls, interior stems).
   * Convex hull of vertices plus on-arc extrema, then rebuild any hull
   * edge whose bulge is an exposed source arc as a CircularString.
   */
  private static List<Member> wrapFromPointHull(List<Site> points, List<Site> arcs,
      GeometryFactory f) {
    List<Coordinate> cand = new ArrayList<Coordinate>();
    for (int i = 0; i < points.size(); i++) {
      addIfNew(cand, points.get(i).c);
    }
    for (int i = 0; i < arcs.size(); i++) {
      Site a = arcs.get(i);
      addIfNew(cand, a.at(a.a0));
      addIfNew(cand, a.at(a.a1));
      double span = a.ccw ? normPos(a.a1 - a.a0) : normPos(a.a0 - a.a1);
      double mid = a.ccw ? a.a0 + 0.5 * span : a.a0 - 0.5 * span;
      addIfNew(cand, a.at(mid));
      addIfNew(cand, a.onArc(0.0) ? a.at(0.0) : null);
      addIfNew(cand, a.onArc(Math.PI / 2.0) ? a.at(Math.PI / 2.0) : null);
      addIfNew(cand, a.onArc(Math.PI) ? a.at(Math.PI) : null);
      addIfNew(cand, a.onArc(-Math.PI / 2.0) ? a.at(-Math.PI / 2.0) : null);
    }
    if (cand.size() < 3) return null;
    Geometry hull = new ConvexHull(cand.toArray(new Coordinate[0]), f)
        .getConvexHull();
    if (!(hull instanceof Polygon)) return null;
    Coordinate[] ring = ((Polygon) hull).getExteriorRing().getCoordinates();
    if (ring.length < 4) return null;
    if (!Orientation.isCCW(ring)) {
      CoordinateArrays.reverse(ring);
    }
    int arcHits = 0;
    List<Member> members = new ArrayList<Member>();
    for (int i = 0; i < ring.length - 1; i++) {
      Coordinate a = ring[i];
      Coordinate b = ring[i + 1];
      if (a.distance(b) <= PT_EPS) continue;
      Site arc = sharedExposedArc(a, b, arcs);
      if (arc != null) {
        arcHits++;
        members.add(Member.arc(arc, arc.angleOf(a), arc.angleOf(b), a, b));
      }
      else {
        members.add(Member.seg(a, b));
      }
    }
    if (arcHits == 0) return null;
    return members.isEmpty() ? null : members;
  }

  private static void addIfNew(List<Coordinate> cand, Coordinate p) {
    if (p == null) return;
    for (int i = 0; i < cand.size(); i++) {
      if (cand.get(i).distance(p) <= PT_EPS) return;
    }
    cand.add(new Coordinate(p));
  }

  /**
   * Hull edge A→B is an exposed arc when both ends lie on the same
   * source arc and the CCW minor bulge is outside (to the right of
   * the CCW hull chord; the centre sits to the left).
   */
  private static Site sharedExposedArc(Coordinate a, Coordinate b, List<Site> arcs) {
    Site found = null;
    for (int i = 0; i < arcs.size(); i++) {
      Site s = arcs.get(i);
      if (!nearArc(s, a) || !nearArc(s, b)) continue;
      double angA = s.angleOf(a);
      double angB = s.angleOf(b);
      if (!s.ccwPathOnArc(angA, angB)) continue;
      double sweep = normPos(angB - angA);
      if (sweep <= ANG_EPS || sweep >= Math.PI - ANG_EPS) continue;
      Coordinate mid = s.at(angA + 0.5 * sweep);
      if (cross(a, b, mid) >= -CROSS_EPS) continue;
      found = s;
    }
    return found;
  }

  private static boolean nearArc(Site s, Coordinate p) {
    if (Math.abs(p.distance(s.c) - s.r) > 1.0e-6) return false;
    return s.onArc(s.angleOf(p));
  }

  private static Start findStart(List<Site> points, List<Site> arcs) {
    Coordinate best = null;
    Site bestArc = null;
    double bestAng = 0.0;
    for (int i = 0; i < points.size(); i++) {
      Coordinate p = points.get(i).c;
      if (lower(p, best)) {
        best = p;
        bestArc = null;
      }
    }
    for (int i = 0; i < arcs.size(); i++) {
      Site a = arcs.get(i);
      Coordinate p = a.lowest();
      if (lower(p, best)) {
        best = p;
        bestArc = a;
        bestAng = a.angleOf(p);
      }
    }
    if (best == null) return null;
    Start s = new Start();
    s.p = best;
    if (bestArc != null && bestArc.canRideCcw(bestAng)) {
      s.riding = true;
      s.arc = bestArc;
      s.ang = bestAng;
    }
    else {
      Site on = arcThrough(best, arcs);
      if (on != null && on.canRideCcw(on.angleOf(best))) {
        s.riding = true;
        s.arc = on;
        s.ang = on.angleOf(best);
      }
    }
    return s;
  }

  private static boolean lower(Coordinate p, Coordinate best) {
    if (best == null) return true;
    if (p.y < best.y - PT_EPS) return true;
    if (p.y > best.y + PT_EPS) return false;
    return p.x < best.x - PT_EPS;
  }

  private static Site arcThrough(Coordinate p, List<Site> arcs) {
    Site found = null;
    for (int i = 0; i < arcs.size(); i++) {
      Site a = arcs.get(i);
      if (a.containsPoint(p) && a.canRideCcw(a.angleOf(p))) {
        found = a;
      }
    }
    return found;
  }

  private static Cand bestCand(Coordinate curr, double currAng, boolean riding,
      Site rideArc, Coordinate inDir, List<Site> points, List<Site> arcs) {
    Cand best = null;
    if (riding && rideArc != null) {
      for (int i = 0; i < points.size(); i++) {
        best = considerOnArcPoint(best, curr, currAng, rideArc, inDir,
            points.get(i), points, arcs);
      }
      for (int i = 0; i < arcs.size(); i++) {
        Site other = arcs.get(i);
        if (other != rideArc) {
          best = considerTangent(best, curr, currAng, rideArc, inDir, other,
              points, arcs);
        }
      }
    }
    for (int i = 0; i < points.size(); i++) {
      Site q = points.get(i);
      if (q.c.distance(curr) > PT_EPS) {
        best = considerSeg(best, curr, inDir, q.c, q, points, arcs);
      }
    }
    for (int i = 0; i < arcs.size(); i++) {
      Site a = arcs.get(i);
      if (riding && a == rideArc) {
        // already considered as the ride
      }
      else if (a.containsPoint(curr) && a.canRideCcw(a.angleOf(curr))) {
        double ang = a.angleOf(curr);
        for (int j = 0; j < points.size(); j++) {
          best = considerOnArcPoint(best, curr, ang, a, inDir, points.get(j),
              points, arcs);
        }
        for (int j = 0; j < arcs.size(); j++) {
          if (arcs.get(j) != a) {
            best = considerTangent(best, curr, ang, a, inDir, arcs.get(j),
                points, arcs);
          }
        }
      }
      else if (Math.abs(curr.distance(a.c) - a.r) > 1.0e-7) {
        Site pt = Site.point(curr);
        best = considerTangent(best, curr, Double.NaN, pt, inDir, a, points, arcs);
      }
    }
    return best;
  }

  private static Cand considerSeg(Cand best, Coordinate curr, Coordinate inDir,
      Coordinate dest, Site destSite, List<Site> points, List<Site> arcs) {
    if (!allLeftOf(curr, dest, points, arcs)) return best;
    return keep(best, candOf(inDir, curr, dest, 0.0, curr, dest, Double.NaN,
        Double.NaN, destSite, null));
  }

  private static Cand considerOnArcPoint(Cand best, Coordinate curr, double currAng,
      Site arc, Coordinate inDir, Site pt, List<Site> points, List<Site> arcs) {
    if (!arc.containsPoint(pt.c)) return best;
    double leaveAng = arc.angleOf(pt.c);
    if (!arc.ccwPathOnArc(currAng, leaveAng)) return best;
    double sweep = normPos(leaveAng - currAng);
    Coordinate leave = sweep <= ANG_EPS ? curr : arc.at(leaveAng);
    if (leave.distance(pt.c) > PT_EPS && !allLeftOf(leave, pt.c, points, arcs)) {
      return best;
    }
    if (sweep <= ANG_EPS && leave.distance(pt.c) <= PT_EPS) return best;
    return keep(best, candOf(inDir, curr, destDir(curr, currAng, arc, sweep, pt.c),
        sweep, leave, pt.c, leaveAng, Double.NaN, pt, sweep > ANG_EPS ? arc : null));
  }

  private static Cand considerTangent(Cand best, Coordinate curr, double currAng,
      Site from, Coordinate inDir, Site to, List<Site> points, List<Site> arcs) {
    Tangent t = leftExternal(from, to);
    if (t == null) return best;
    double sweep = 0.0;
    Coordinate leave = t.t1;
    if (from.isArc && !Double.isNaN(currAng)) {
      if (!from.ccwPathOnArc(currAng, t.ang1)) return best;
      sweep = normPos(t.ang1 - currAng);
      if (sweep <= ANG_EPS) leave = curr;
    }
    else if (from.isArc) {
      if (!from.onArc(t.ang1)) return best;
    }
    if (to.isArc && !to.onArc(t.ang2)) return best;
    if (leave.distance(t.t2) > PT_EPS && !allLeftOf(leave, t.t2, points, arcs)) {
      return best;
    }
    Coordinate dest = t.t2;
    return keep(best, candOf(inDir, curr,
        destDir(curr, currAng, from.isArc ? from : null, sweep, dest),
        sweep, leave, dest, from.isArc ? t.ang1 : Double.NaN,
        to.isArc ? t.ang2 : Double.NaN, to, sweep > ANG_EPS ? from : null));
  }

  private static Coordinate destDir(Coordinate curr, double currAng, Site arc,
      double sweep, Coordinate dest) {
    if (arc != null && sweep > ANG_EPS) {
      return arc.ccwTangent(currAng);
    }
    return dest;
  }

  private static Cand candOf(Coordinate inDir, Coordinate curr, Coordinate destOrDir,
      double sweep, Coordinate leave, Coordinate dest, double leaveAng,
      double destAng, Site destSite, Site rideArc) {
    Cand c = new Cand();
    c.turn = turn(inDir, curr, destOrDir);
    c.dist = sweep > ANG_EPS ? 0.0 : curr.distance(dest);
    c.sweep = sweep;
    c.leave = leave;
    c.dest = dest;
    c.leaveAng = leaveAng;
    c.destAng = destAng;
    c.destSite = destSite;
    c.rideArc = rideArc;
    return c;
  }

  private static Cand keep(Cand best, Cand next) {
    if (next == null) return best;
    if (best == null) return next;
    if (next.turn < best.turn - TURN_EPS) return next;
    if (next.turn > best.turn + TURN_EPS) return best;
    if (next.dist > best.dist + PT_EPS) return next;
    if (next.dist < best.dist - PT_EPS) return best;
    if (next.sweep < best.sweep) return next;
    return best;
  }

  private static double turn(Coordinate inDir, Coordinate from, Coordinate toOrDir) {
    double ox;
    double oy;
    if (toOrDir == from) {
      ox = 1.0;
      oy = 0.0;
    }
    else if (Math.abs(toOrDir.x - from.x) + Math.abs(toOrDir.y - from.y) > 0.0
        && toOrDir.distance(from) > PT_EPS) {
      ox = toOrDir.x - from.x;
      oy = toOrDir.y - from.y;
    }
    else {
      ox = toOrDir.x;
      oy = toOrDir.y;
    }
    double cross = inDir.x * oy - inDir.y * ox;
    double dot = inDir.x * ox + inDir.y * oy;
    double a = Math.atan2(cross, dot);
    if (a < 0.0) a += TWO_PI;
    return a;
  }

  /**
   * External common tangent of two discs with both centres to the left
   * of {@code T1→T2} and a positive travel length. Points are radius-0
   * discs. No solution when one disc is strictly inside the other, or
   * the sites share a centre.
   */
  private static Tangent leftExternal(Site a, Site b) {
    double r1 = a.r;
    double r2 = b.r;
    double dx = b.c.x - a.c.x;
    double dy = b.c.y - a.c.y;
    double d = Math.hypot(dx, dy);
    if (d <= PT_EPS) return null;
    double delta = r2 - r1;
    if (Math.abs(delta) > d + PT_EPS) return null;
    double inv = 1.0 / d;
    double A = dx * inv;
    double B = -dy * inv;
    double C = -delta * inv;
    double R = Math.hypot(A, B);
    if (R == 0.0) return null;
    double cNorm = C / R;
    if (cNorm > 1.0) {
      if (cNorm > 1.0 + 1.0e-12) return null;
      cNorm = 1.0;
    }
    else if (cNorm < -1.0) {
      if (cNorm < -1.0 - 1.0e-12) return null;
      cNorm = -1.0;
    }
    double phi = Math.atan2(B, A);
    double alpha = Math.asin(cNorm);
    double[] thetas = new double[] { alpha - phi, Math.PI - alpha - phi };
    Tangent found = null;
    for (int i = 0; i < thetas.length; i++) {
      double ux = Math.cos(thetas[i]);
      double uy = Math.sin(thetas[i]);
      double lambda = dx * ux + dy * uy;
      if (lambda > PT_EPS) {
        double lx = -uy;
        double ly = ux;
        Tangent t = new Tangent();
        t.t1 = new Coordinate(a.c.x - r1 * lx, a.c.y - r1 * ly);
        t.t2 = new Coordinate(b.c.x - r2 * lx, b.c.y - r2 * ly);
        t.ang1 = a.isArc ? Math.atan2(t.t1.y - a.c.y, t.t1.x - a.c.x) : Double.NaN;
        t.ang2 = b.isArc ? Math.atan2(t.t2.y - b.c.y, t.t2.x - b.c.x) : Double.NaN;
        found = t;
      }
    }
    return found;
  }

  private static boolean allLeftOf(Coordinate a, Coordinate b, List<Site> points,
      List<Site> arcs) {
    if (a.distance(b) <= PT_EPS) return true;
    for (int i = 0; i < points.size(); i++) {
      if (cross(a, b, points.get(i).c) < -CROSS_EPS) return false;
    }
    for (int i = 0; i < arcs.size(); i++) {
      if (!arcLeftOf(a, b, arcs.get(i))) return false;
    }
    return true;
  }

  private static boolean arcLeftOf(Coordinate a, Coordinate b, Site s) {
    if (cross(a, b, s.at(s.a0)) < -CROSS_EPS) return false;
    if (cross(a, b, s.at(s.a1)) < -CROSS_EPS) return false;
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double len = Math.hypot(dx, dy);
    if (len <= PT_EPS) return true;
    double rx = dy / len;
    double ry = -dx / len;
    double ang = Math.atan2(ry, rx);
    if (s.onArc(ang) && cross(a, b, s.at(ang)) < -CROSS_EPS) return false;
    return true;
  }

  private static double cross(Coordinate a, Coordinate b, Coordinate p) {
    return (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x);
  }

  private static Geometry build(List<Member> ring, GeometryFactory f, List<Site> arcs) {
    if (isFullCircle(ring, arcs)) {
      Site a = firstArc(ring);
      return CurveExact.makeDisc(
          new CircularArcDensifier.Circle(a.c.x, a.c.y, a.r), f);
    }
    List<LineString> members = new ArrayList<LineString>();
    boolean anyArc = false;
    for (int i = 0; i < ring.size(); i++) {
      Member m = ring.get(i);
      if (m.arc != null) {
        double sweep = normPos(m.a1 - m.a0);
        if (sweep <= ANG_EPS) {
          // skip a collapsed arc
        }
        else {
          members.add(m.arc.toCircularString(m.a0, m.a1, m.p0, m.p1, f));
          anyArc = true;
        }
      }
      else if (m.p0.distance(m.p1) > PT_EPS) {
        members.add(f.createLineString(new Coordinate[] {
            new Coordinate(m.p0), new Coordinate(m.p1)
        }));
      }
    }
    if (members.isEmpty()) return null;
    closeRing(members, f);
    snapClosed(members, f);
    if (!anyArc) return null;
    LineString shell;
    if (members.size() == 1) {
      shell = members.get(0);
    }
    else {
      shell = new CompoundCurve(members.toArray(new LineString[0]), f);
    }
    return new CurvePolygon(shell, null, f);
  }

  private static void closeRing(List<LineString> members, GeometryFactory f) {
    Coordinate first = members.get(0).getCoordinateN(0);
    LineString last = members.get(members.size() - 1);
    Coordinate lastPt = last.getCoordinateN(last.getNumPoints() - 1);
    if (first.distance(lastPt) > PT_EPS) {
      members.add(f.createLineString(new Coordinate[] {
          new Coordinate(lastPt), new Coordinate(first)
      }));
    }
  }

  /**
   * Force the concatenated control points to be a closed ring. Arc
   * endpoints come from {@code cos}/{@code sin} and will not
   * {@code equals} the input vertex they represent, which
   * {@link CurvePolygon} rejects when it derives the legacy
   * {@code LinearRing}.
   */
  private static void snapClosed(List<LineString> members, GeometryFactory f) {
    Coordinate first = new Coordinate(members.get(0).getCoordinateN(0));
    for (int i = 1; i < members.size(); i++) {
      LineString prev = members.get(i - 1);
      Coordinate join = prev.getCoordinateN(prev.getNumPoints() - 1);
      members.set(i, withStart(members.get(i), join, f));
    }
    LineString last = members.get(members.size() - 1);
    members.set(members.size() - 1, withEnd(last, first, f));
    members.set(0, withStart(members.get(0), first, f));
  }

  private static LineString withStart(LineString ls, Coordinate start,
      GeometryFactory f) {
    if (ls instanceof CircularString) {
      Coordinate[] c = ls.getCoordinates();
      c[0] = new Coordinate(start);
      return new CircularString(f.getCoordinateSequenceFactory().create(c), f);
    }
    Coordinate[] c = ls.getCoordinates();
    c[0] = new Coordinate(start);
    return f.createLineString(c);
  }

  private static LineString withEnd(LineString ls, Coordinate end,
      GeometryFactory f) {
    if (ls instanceof CircularString) {
      Coordinate[] c = ls.getCoordinates();
      c[c.length - 1] = new Coordinate(end);
      return new CircularString(f.getCoordinateSequenceFactory().create(c), f);
    }
    Coordinate[] c = ls.getCoordinates();
    c[c.length - 1] = new Coordinate(end);
    return f.createLineString(c);
  }

  private static boolean isFullCircle(List<Member> ring, List<Site> arcs) {
    if (arcs.isEmpty()) return false;
    Site a0 = arcs.get(0);
    double sweep = 0.0;
    for (int i = 0; i < ring.size(); i++) {
      Member m = ring.get(i);
      if (m.arc == null) {
        if (m.p0.distance(m.p1) > PT_EPS) return false;
      }
      else if (Math.hypot(m.arc.c.x - a0.c.x, m.arc.c.y - a0.c.y) > PT_EPS
          || Math.abs(m.arc.r - a0.r) > PT_EPS) {
        return false;
      }
      else {
        sweep += normPos(m.a1 - m.a0);
      }
    }
    return Math.abs(sweep - TWO_PI) <= 1.0e-6
        || Math.abs(sweep - 2.0 * TWO_PI) <= 1.0e-6;
  }

  private static Site firstArc(List<Member> ring) {
    Site s = null;
    for (int i = 0; i < ring.size(); i++) {
      if (ring.get(i).arc != null) s = ring.get(i).arc;
    }
    return s;
  }

  private static double normPos(double angle) {
    angle = angle % TWO_PI;
    if (angle < 0.0) angle += TWO_PI;
    return angle;
  }

  private static final class Start {
    Coordinate p;
    boolean riding;
    Site arc;
    double ang;
  }

  private static final class Cand {
    double turn;
    double dist;
    double sweep;
    Coordinate leave;
    Coordinate dest;
    double leaveAng;
    double destAng;
    Site destSite;
    Site rideArc;
  }

  private static final class Tangent {
    Coordinate t1;
    Coordinate t2;
    double ang1;
    double ang2;
  }

  private static final class Member {
    final Site arc;
    final double a0;
    final double a1;
    final Coordinate p0;
    final Coordinate p1;

    private Member(Site arc, double a0, double a1, Coordinate p0, Coordinate p1) {
      this.arc = arc;
      this.a0 = a0;
      this.a1 = a1;
      this.p0 = p0;
      this.p1 = p1;
    }

    static Member arc(Site s, double from, double to, Coordinate p0, Coordinate p1) {
      return new Member(s, from, to, new Coordinate(p0), new Coordinate(p1));
    }

    static Member seg(Coordinate a, Coordinate b) {
      return new Member(null, 0.0, 0.0, new Coordinate(a), new Coordinate(b));
    }
  }

  private static final class Site {
    final Coordinate c;
    final double r;
    final double a0;
    final double a1;
    final boolean ccw;
    final boolean isArc;

    private Site(Coordinate c, double r, double a0, double a1, boolean ccw,
        boolean isArc) {
      this.c = c;
      this.r = r;
      this.a0 = a0;
      this.a1 = a1;
      this.ccw = ccw;
      this.isArc = isArc;
    }

    static Site point(Coordinate p) {
      return new Site(new Coordinate(p), 0.0, 0.0, 0.0, true, false);
    }

    static Site arc(CircularArcDensifier.Circle circ, Coordinate start,
        Coordinate mid, Coordinate end) {
      double s0 = Math.atan2(start.y - circ.cy, start.x - circ.cx);
      double sm = Math.atan2(mid.y - circ.cy, mid.x - circ.cx);
      double s1 = Math.atan2(end.y - circ.cy, end.x - circ.cx);
      boolean ccw = normPos(sm - s0) < normPos(s1 - s0);
      return new Site(new Coordinate(circ.cx, circ.cy), circ.r, s0, s1, ccw, true);
    }

    Coordinate at(double ang) {
      return new Coordinate(c.x + r * Math.cos(ang), c.y + r * Math.sin(ang));
    }

    double angleOf(Coordinate p) {
      return Math.atan2(p.y - c.y, p.x - c.x);
    }

    boolean onArc(double ang) {
      if (!isArc) return true;
      double span = ccw ? normPos(a1 - a0) : normPos(a0 - a1);
      double t = ccw ? normPos(ang - a0) : normPos(a0 - ang);
      return t <= span + ANG_EPS;
    }

    boolean containsPoint(Coordinate p) {
      if (!isArc) return c.distance(p) <= PT_EPS;
      if (Math.abs(p.distance(c) - r) > 1.0e-7) return false;
      return onArc(angleOf(p));
    }

    boolean canRideCcw(double ang) {
      if (!isArc || !onArc(ang)) return false;
      return onArc(ang + 10.0 * ANG_EPS) || ccwPathOnArc(ang, a1)
          || ccwPathOnArc(ang, a0);
    }

    boolean ccwPathOnArc(double from, double to) {
      if (!onArc(from) || !onArc(to)) return false;
      double sweep = normPos(to - from);
      if (sweep <= ANG_EPS) return true;
      return onArc(from + 0.5 * sweep);
    }

    Coordinate lowest() {
      double south = -Math.PI / 2.0;
      if (onArc(south)) return at(south);
      Coordinate p0 = at(a0);
      Coordinate p1 = at(a1);
      if (p0.y < p1.y - PT_EPS) return p0;
      if (p1.y < p0.y - PT_EPS) return p1;
      return p0.x <= p1.x ? p0 : p1;
    }

    Coordinate ccwTangent(double ang) {
      // Radius (cos, sin); CCW tangent is (-sin, cos).
      return new Coordinate(c.x - r * Math.sin(ang) + r * Math.cos(ang),
          c.y + r * Math.cos(ang) + r * Math.sin(ang));
    }

    CircularString toCircularString(double from, double to, Coordinate start,
        Coordinate end, GeometryFactory f) {
      double sweep = normPos(to - from);
      double mid = from + 0.5 * sweep;
      Coordinate[] pts = new Coordinate[] {
          new Coordinate(start), at(mid), new Coordinate(end)
      };
      return new CircularString(f.getCoordinateSequenceFactory().create(pts), f);
    }
  }
}
