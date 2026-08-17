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
package org.locationtech.jts.operation.overlayng.curve;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.algorithm.LineIntersector;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularArcDensifier;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.geom.curve.MultiSurface;

/**
 * One curve piece: a straight chord or a circular arc. Package-private
 * -- not a core {@code SegmentString}, not N-SS, not a public API.
 * Lives next to {@link TwoNodeClip} so circle–circle / sweep / line–circle
 * stay the existing atoms. A noder that densifies is a lie; this string
 * carries the control triple or the chord the kits already use.
 * <p>
 * A shared run is an {@linkplain #overlap edge} (a same-circle sweep
 * interval, or a collinear segment). {@link #intersect} stays a
 * discrete node set: collinear overlap is still {@code null} there
 * (MIXED is not a pair of endpoints). A pinch / kiss that is not an
 * interval is a zero-length degenerate edge, or {@code null}.
 */
final class CurveSegmentString {

  private final Coordinate a;
  private final Coordinate mid;
  private final Coordinate b;
  private final boolean isArc;
  private final double[] circle;

  private CurveSegmentString(Coordinate a, Coordinate mid, Coordinate b,
      boolean isArc, double[] circle) {
    this.a = a;
    this.mid = mid;
    this.b = b;
    this.isArc = isArc;
    this.circle = circle;
  }

  static CurveSegmentString segment(Coordinate a, Coordinate b) {
    return new CurveSegmentString(a, null, b, false, null);
  }

  /**
   * Circular arc through three controls, or a chord when the triple
   * is colinear ({@link CircularArcDensifier#circumcircle} is null).
   */
  static CurveSegmentString arc(Coordinate a, Coordinate mid, Coordinate b) {
    double[] c = CircularArcDensifier.circumcircle(a, mid, b);
    if (c == null) {
      return segment(a, b);
    }
    return new CurveSegmentString(a, mid, b, true, c);
  }

  static CurveSegmentString of(TwoNodeClip.Edge e) {
    return new CurveSegmentString(e.a, e.mid, e.b, e.isArc, e.circle);
  }

  boolean isArc() {
    return isArc;
  }

  Coordinate getStart() {
    return a;
  }

  Coordinate getEnd() {
    return b;
  }

  Coordinate getMid() {
    return mid;
  }

  TwoNodeClip.Edge asEdge() {
    return new TwoNodeClip.Edge(a, mid, b, isArc, circle);
  }

  /**
   * Zero-length pinch / kiss. A point is not an interval; the noder
   * may still name it as a degenerate edge. A full-circle arc
   * ({@code a == b} with a mid on the opposite side) is not this.
   * Not a face.
   */
  boolean isDegenerate() {
    if (a.distance(b) > 1.0e-12) return false;
    if (!isArc) return true;
    return mid == null || mid.distance(a) <= 1.0e-12;
  }

  /**
   * Chord length, or r × sweep. A degenerate edge is 0.
   */
  double length() {
    if (!isArc) return a.distance(b);
    double a0 = Math.atan2(a.y - circle[1], a.x - circle[0]);
    double aM = Math.atan2(mid.y - circle[1], mid.x - circle[0]);
    double a1 = Math.atan2(b.y - circle[1], b.x - circle[0]);
    boolean ccw = TwoNodeClip.normPos(aM - a0) < TwoNodeClip.normPos(a1 - a0);
    double sweep = ccw ? TwoNodeClip.normPos(a1 - a0)
        : TwoNodeClip.normPos(a0 - a1);
    if (sweep == 0.0) sweep = TwoNodeClip.TWO_PI;
    return circle[2] * sweep;
  }

  /**
   * Exterior pieces of a hole-free circular / compound / plain ring,
   * or the pieces of a lineal CircularString / CompoundCurve /
   * LineString (R-LL). A holed CurvePolygon stays {@code null};
   * pass the hole ring itself as a LineString. Two holes are P2.4.
   */
  static List<CurveSegmentString> of(Geometry g) {
    Geometry geom = unwrap(g);
    if (geom == null) return null;
    if (geom instanceof CurvePolygon) {
      CurvePolygon cp = (CurvePolygon) geom;
      if (cp.isEmpty() || cp.getNumInteriorRing() > 0) return null;
      LineString ring = cp.getExteriorCurve();
      if (ring instanceof CompoundCurve) {
        List<TwoNodeClip.Edge> edges = TwoNodeClip.flatten(cp);
        return edges == null ? null : ofEdges(edges);
      }
      if (ring instanceof CircularString) {
        return ofCircular((CircularString) ring);
      }
      return null;
    }
    if (TwoNodeClip.isPlainPolygon(geom)) {
      return ofPlain(((Polygon) geom).getExteriorRing().getCoordinates());
    }
    if (geom instanceof LineString) {
      List<TwoNodeClip.Edge> edges = CircularLineOverlay.flattenLineal(
          (LineString) geom);
      return edges == null ? null : ofEdges(edges);
    }
    return null;
  }

  /**
   * Shared run of two strings: a same-circle sweep interval, a
   * collinear segment, or a different-circle tangent pinch (zero
   * length). {@code null} is no interval and no pinch this rung
   * will name. Does not assemble a face.
   */
  static CurveSegmentString overlap(CurveSegmentString p,
      CurveSegmentString q, double scale) {
    List<CurveSegmentString> s = shared(p, q, scale);
    if (s.isEmpty()) return null;
    return s.get(0);
  }

  /**
   * All shared runs of a pair. Empty is no interval / no pinch.
   */
  static List<CurveSegmentString> shared(CurveSegmentString p,
      CurveSegmentString q, double scale) {
    List<CurveSegmentString> out = new ArrayList<CurveSegmentString>();
    if (p == null || q == null) return out;
    if (p.isArc && q.isArc) {
      if (sameCircle(p, q, scale)) {
        List<TwoNodeClip.Edge> sweeps = CircleSweepOverlay.sharedSweep(
            p.asEdge(), q.asEdge(), scale);
        if (sweeps != null) {
          for (int i = 0; i < sweeps.size(); i++) {
            out.add(of(sweeps.get(i)));
          }
        }
        return out;
      }
      CurveSegmentString pinch = tangentPinch(p, q);
      if (pinch != null) {
        out.add(pinch);
      }
      return out;
    }
    if (!p.isArc && !q.isArc) {
      CurveSegmentString seg = overlapSegments(p, q);
      if (seg != null) {
        out.add(seg);
      }
    }
    return out;
  }

  /**
   * Discrete hits of this string and {@code other}. {@code null} is
   * collinear overlap (MIXED) -- an interval, not a node set.
   * Same-circle arcs add no node here. Empty is a miss-free pair
   * with no point.
   */
  static Coordinate[] intersect(CurveSegmentString p, CurveSegmentString q,
      double scale) {
    if (p == null || q == null) return null;
    if (p.isArc && q.isArc) {
      return intersectArcs(p, q, scale);
    }
    if (p.isArc) {
      return intersectSegCircle(q.a, q.b, p);
    }
    if (q.isArc) {
      return intersectSegCircle(p.a, p.b, q);
    }
    return intersectSegments(p, q);
  }

  private static Coordinate[] intersectArcs(CurveSegmentString p,
      CurveSegmentString q, double scale) {
    if (sameCircle(p, q, scale)) {
      return new Coordinate[0];
    }
    Coordinate[] xs = TwoNodeClip.intersectCircles(
        p.circle[0], p.circle[1], p.circle[2],
        q.circle[0], q.circle[1], q.circle[2]);
    List<Coordinate> hits = new ArrayList<Coordinate>();
    for (int k = 0; k < xs.length; k++) {
      if (TwoNodeClip.isOnSweep(xs[k], p.circle, p.a, p.mid, p.b)
          && TwoNodeClip.isOnSweep(xs[k], q.circle, q.a, q.mid, q.b)) {
        hits.add(xs[k]);
      }
    }
    return toArray(hits);
  }

  private static Coordinate[] intersectSegCircle(Coordinate s0, Coordinate s1,
      CurveSegmentString sweep) {
    Coordinate[] xs = TwoNodeClip.intersectSegmentCircle(
        sweep.circle[0], sweep.circle[1], sweep.circle[2], s0, s1);
    List<Coordinate> hits = new ArrayList<Coordinate>();
    for (int k = 0; k < xs.length; k++) {
      if (TwoNodeClip.isOnSweep(xs[k], sweep.circle, sweep.a, sweep.mid,
          sweep.b)) {
        hits.add(xs[k]);
      }
    }
    return toArray(hits);
  }

  private static Coordinate[] intersectSegments(CurveSegmentString p,
      CurveSegmentString q) {
    LineIntersector li = new RobustLineIntersector();
    li.computeIntersection(p.a, p.b, q.a, q.b);
    if (li.getIntersectionNum() == LineIntersector.COLLINEAR_INTERSECTION) {
      return null;
    }
    if (li.getIntersectionNum() == 1) {
      return new Coordinate[] { li.getIntersection(0) };
    }
    return new Coordinate[0];
  }

  /**
   * Collinear overlap as a segment. A kiss (zero length) is not an
   * interval -- {@code null}, not a pair of endpoints.
   */
  private static CurveSegmentString overlapSegments(CurveSegmentString p,
      CurveSegmentString q) {
    LineIntersector li = new RobustLineIntersector();
    li.computeIntersection(p.a, p.b, q.a, q.b);
    if (li.getIntersectionNum() != LineIntersector.COLLINEAR_INTERSECTION) {
      return null;
    }
    Coordinate u = li.getIntersection(0);
    Coordinate v = li.getIntersection(1);
    if (u.distance(v) <= 1.0e-12) return null;
    return segment(u, v);
  }

  /**
   * Different-circle tangent: {@code intersectCircles} returns one
   * point, and it lies on both sweeps. Named as a zero-length edge.
   * Covers TOUCH-ext ({@code d = r1+r2}) and H-ANNULUS-TANGENT
   * ({@code d+r = R}). Two crossings with only one on-sweep hit
   * stay a node, not this. Not a face.
   */
  private static CurveSegmentString tangentPinch(CurveSegmentString p,
      CurveSegmentString q) {
    Coordinate[] xs = TwoNodeClip.intersectCircles(
        p.circle[0], p.circle[1], p.circle[2],
        q.circle[0], q.circle[1], q.circle[2]);
    if (xs.length != 1) return null;
    if (!TwoNodeClip.isOnSweep(xs[0], p.circle, p.a, p.mid, p.b)) {
      return null;
    }
    if (!TwoNodeClip.isOnSweep(xs[0], q.circle, q.a, q.mid, q.b)) {
      return null;
    }
    return segment(xs[0], xs[0]);
  }

  static boolean sameCircle(CurveSegmentString p, CurveSegmentString q,
      double scale) {
    if (!p.isArc || !q.isArc) return false;
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    Coordinate cp = new Coordinate(p.circle[0], p.circle[1]);
    Coordinate cq = new Coordinate(q.circle[0], q.circle[1]);
    return cp.distance(cq) <= eps
        && Math.abs(p.circle[2] - q.circle[2]) <= eps;
  }

  private static List<CurveSegmentString> ofEdges(List<TwoNodeClip.Edge> edges) {
    List<CurveSegmentString> out = new ArrayList<CurveSegmentString>();
    for (int i = 0; i < edges.size(); i++) {
      out.add(of(edges.get(i)));
    }
    return out;
  }

  private static List<CurveSegmentString> ofCircular(CircularString cs) {
    Coordinate[] pts = cs.getCoordinates();
    if (pts.length < 3) return null;
    List<CurveSegmentString> out = new ArrayList<CurveSegmentString>();
    for (int k = 0; k + 2 < pts.length; k += 2) {
      out.add(arc(pts[k], pts[k + 1], pts[k + 2]));
    }
    return out.isEmpty() ? null : out;
  }

  private static List<CurveSegmentString> ofPlain(Coordinate[] ring) {
    if (ring == null || ring.length < 4) return null;
    List<CurveSegmentString> out = new ArrayList<CurveSegmentString>();
    int n = ring.length - 1;
    for (int i = 0; i < n; i++) {
      out.add(segment(ring[i], ring[i + 1]));
    }
    return out;
  }

  private static Geometry unwrap(Geometry g) {
    if (g == null || g.isEmpty()) return null;
    if (g instanceof MultiSurface || g instanceof MultiCurve
        || g instanceof MultiLineString) {
      if (g.getNumGeometries() != 1) return null;
      return unwrap(g.getGeometryN(0));
    }
    return g;
  }

  private static Coordinate[] toArray(List<Coordinate> hits) {
    return hits.toArray(new Coordinate[0]);
  }
}
