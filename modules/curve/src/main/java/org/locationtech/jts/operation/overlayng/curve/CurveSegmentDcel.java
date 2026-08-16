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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularArcDensifier;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;

/**
 * Package-private curve DCEL. Half-edges, twins, next/prev, incident
 * face. Members stay {@link CurveSegmentString} (an arc stays an
 * arc). Built from the P2.1–P2.5.2 node set plus already-named
 * MIXED / shared-edge ends. Cycle order is the Faces left-most /
 * next-outgoing walk, persisted as links.
 * <p>
 * PLG / COV eat this structure. Not a noder, not OverlayNG-for-circles,
 * not a public API, not another Geometry assembler.
 * {@link CurveSegmentFaces} may assemble rings from the bounded
 * faces; that is a consumer, not this product.
 * <p>
 * Generic walk-on-{@code nodes==null} is unsafe: a MIXED abort can
 * hide a real crossing ({@code HALF_DISC × HALF_CROSSING_UPPER}).
 * That pair stamps {@link #MIXED_HIDES_CROSSING}. A coincident
 * leave-angle is snap-rounding ({@link #TANGENT_LEAVE_ANGLE}).
 * Pinch / kiss / holed Geometry-level stay {@code null}. Densify
 * is never a noder. Not P2.5.5.
 */
final class CurveSegmentDcel {

  /** Named stamp: coincident leave-angle. Snap-rounding, not a walk. */
  static final String TANGENT_LEAVE_ANGLE = "P2.5.4 tangent leave-angle";

  /**
   * Named stamp: {@code nodes==null} because a collinear pair aborted,
   * and another pair still has a discrete crossing. Not a noder.
   */
  static final String MIXED_HIDES_CROSSING =
      "MIXED nodes==null hides a crossing";

  /**
   * Near-tangent window for an arc leave. Chord coincidence is
   * {@link #compareLeave} only: subtracted-vector atan2 can collapse
   * distinct direction points (locationtech #1224). An arc centre
   * from a rebuilt circumcircle can put a theoretically-on-axis
   * tangent in either adjacent quadrant, so the N=3 stamp still
   * needs this window.
   */
  private static final double ANGLE_EPS = 1.0e-8;

  private static String missReason;

  private final List<Half> halves;
  private final List<Face> faces;
  private final List<Vertex> vertices;
  private final double eps;

  private CurveSegmentDcel(List<Half> halves, List<Face> faces,
      List<Vertex> vertices, double eps) {
    this.halves = halves;
    this.faces = faces;
    this.vertices = vertices;
    this.eps = eps;
  }

  /**
   * Why the last {@link #of(Geometry[])} / string-group call
   * returned {@code null}, or {@code null} when a DCEL was
   * produced. Package-private -- not a public API.
   */
  static String missReason() {
    return missReason;
  }

  /**
   * Arrangement DCEL of N hole-free circular / compound shells, or
   * {@code null}. Sewn at discrete nodes, or at a named MIXED
   * interval with no hidden crossing. {@link #missReason()} names
   * a stamp when the walk would need snap-rounding or a noder.
   */
  static CurveSegmentDcel of(Geometry[] geoms) {
    missReason = null;
    if (geoms == null || geoms.length < 2) return null;
    List<List<CurveSegmentString>> groups =
        new ArrayList<List<CurveSegmentString>>(geoms.length);
    boolean miss = false;
    for (int i = 0; i < geoms.length && !miss; i++) {
      if (geoms[i] == null || geoms[i].isEmpty() || hasHole(geoms[i])) {
        miss = true;
      }
      else {
        List<CurveSegmentString> s = CurveSegmentString.of(geoms[i]);
        if (s == null) {
          miss = true;
        }
        else {
          groups.add(s);
        }
      }
    }
    if (miss) return null;
    return of(groups, scaleOf(geoms));
  }

  /**
   * Arrangement DCEL of N string collections. Same sew as
   * {@link #of(Geometry[])}; no Geometry hole check.
   */
  static CurveSegmentDcel of(List<List<CurveSegmentString>> groups,
      double scale) {
    missReason = null;
    if (groups == null || groups.size() < 2) return null;
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    Coordinate[] nodes = CurveSegmentNoder.nodes(groups, scale);
    if (nodes == null) {
      if (hidesCrossing(groups, scale, eps)) {
        missReason = MIXED_HIDES_CROSSING;
        return null;
      }
      if (!hasNamedInterval(groups, scale)) {
        return null;
      }
    }
    return build(groups, nodes, scale, eps);
  }

  List<Half> halves() {
    return halves;
  }

  List<Face> faces() {
    return faces;
  }

  List<Face> boundedFaces() {
    List<Face> out = new ArrayList<Face>();
    for (int i = 0; i < faces.size(); i++) {
      if (faces.get(i).bounded) {
        out.add(faces.get(i));
      }
    }
    return out;
  }

  List<Vertex> vertices() {
    return vertices;
  }

  double eps() {
    return eps;
  }

  private static CurveSegmentDcel build(List<List<CurveSegmentString>> groups,
      Coordinate[] nodes, double scale, double eps) {
    List<Coordinate> pool = new ArrayList<Coordinate>();
    addCanon(pool, nodes, eps);
    addEdgeEnds(pool, groups, scale, eps);
    addStringEnds(pool, groups, eps);

    List<CurveSegmentString> pieces = splitAll(groups, pool, eps);
    if (pieces == null || pieces.isEmpty()) return null;
    pieces = mergeCoincident(pieces, scale, eps);
    List<Half> halves = buildHalves(pieces, pool, eps);
    if (halves == null || halves.isEmpty()) return null;

    List<Vertex> verts = indexByStart(halves, pool, eps);
    if (verts == null) return null;
    if (hasCoincidentLeave(verts)) {
      missReason = TANGENT_LEAVE_ANGLE;
      return null;
    }
    if (!linkCycles(verts, halves)) return null;

    List<Face> faces = assignFaces(halves, eps);
    if (faces == null || faces.isEmpty()) return null;
    return new CurveSegmentDcel(halves, faces, verts, eps);
  }

  /**
   * A MIXED abort ({@link CurveSegmentString#intersect} {@code null})
   * hid a discrete hit that is not an end of a named shared edge.
   * That pair needs a noder, not a generic {@code nodes==null} walk.
   */
  private static boolean hidesCrossing(List<List<CurveSegmentString>> groups,
      double scale, double eps) {
    List<Coordinate> namedEnds = new ArrayList<Coordinate>();
    collectNamedEnds(groups, scale, namedEnds, eps);
    boolean hidden = false;
    for (int i = 0; i < groups.size() && !hidden; i++) {
      for (int j = i + 1; j < groups.size() && !hidden; j++) {
        List<CurveSegmentString> a = groups.get(i);
        List<CurveSegmentString> b = groups.get(j);
        if (a == null || b == null) {
          continue;
        }
        for (int p = 0; p < a.size() && !hidden; p++) {
          for (int q = 0; q < b.size() && !hidden; q++) {
            Coordinate[] xs = CurveSegmentString.intersect(a.get(p),
                b.get(q), scale);
            if (xs != null) {
              hidden = hasUnnamedHit(xs, namedEnds, eps);
            }
          }
        }
      }
    }
    return hidden;
  }

  private static void collectNamedEnds(List<List<CurveSegmentString>> groups,
      double scale, List<Coordinate> namedEnds, double eps) {
    for (int i = 0; i < groups.size(); i++) {
      for (int j = i + 1; j < groups.size(); j++) {
        List<CurveSegmentString> edges = CurveSegmentNoder.edges(
            groups.get(i), groups.get(j), scale);
        if (edges != null) {
          for (int k = 0; k < edges.size(); k++) {
            CurveSegmentString e = edges.get(k);
            canon(e.getStart(), namedEnds, eps);
            canon(e.getEnd(), namedEnds, eps);
          }
        }
      }
    }
  }

  private static boolean hasUnnamedHit(Coordinate[] xs,
      List<Coordinate> namedEnds, double eps) {
    boolean hit = false;
    for (int k = 0; k < xs.length && !hit; k++) {
      if (!nearPool(xs[k], namedEnds, eps)) {
        hit = true;
      }
    }
    return hit;
  }

  private static boolean hasNamedInterval(List<List<CurveSegmentString>> groups,
      double scale) {
    boolean found = false;
    for (int i = 0; i < groups.size() && !found; i++) {
      for (int j = i + 1; j < groups.size() && !found; j++) {
        List<CurveSegmentString> edges = CurveSegmentNoder.edges(
            groups.get(i), groups.get(j), scale);
        if (edges != null) {
          for (int k = 0; k < edges.size() && !found; k++) {
            if (!edges.get(k).isDegenerate()) {
              found = true;
            }
          }
        }
      }
    }
    return found;
  }

  private static boolean linkCycles(List<Vertex> verts, List<Half> halves) {
    boolean miss = false;
    for (int i = 0; i < verts.size() && !miss; i++) {
      List<Half> out = verts.get(i).out;
      if (out.isEmpty()) {
        continue;
      }
      for (int j = 0; j < out.size(); j++) {
        Half back = out.get(j);
        // Leave-sorted outgoing is CCW (endpoint quadrant, then
        // orientation — not atan2 of subtracted deltas). The
        // outgoing after `back` in that list is a right turn; the
        // one before is a left turn (interior on the left, CCW face).
        int left = (j - 1 + out.size()) % out.size();
        Half nxt = out.get(left);
        back.twin.next = nxt;
      }
    }
    for (int i = 0; i < halves.size() && !miss; i++) {
      Half h = halves.get(i);
      if (h.next == null) {
        miss = true;
      }
      else {
        h.next.prev = h;
      }
    }
    for (int i = 0; i < halves.size() && !miss; i++) {
      Half h = halves.get(i);
      if (h.twin == null || h.twin.twin != h) {
        miss = true;
      }
      else if (h.next == null || h.prev == null) {
        miss = true;
      }
      else if (h.next.prev != h || h.prev.next != h) {
        miss = true;
      }
    }
    return !miss;
  }

  private static List<Face> assignFaces(List<Half> halves, double eps) {
    List<Face> faces = new ArrayList<Face>();
    boolean miss = false;
    for (int i = 0; i < halves.size() && !miss; i++) {
      Half start = halves.get(i);
      if (start.face == null) {
        Face face = walkFace(start, eps);
        if (face == null) {
          miss = true;
        }
        else {
          faces.add(face);
        }
      }
    }
    return miss ? null : faces;
  }

  private static Face walkFace(Half start, double eps) {
    Face face = new Face();
    Half cur = start;
    int guard = 0;
    boolean closed = false;
    boolean miss = false;
    while (guard++ < 256 && !closed && !miss) {
      if (cur.face != null) {
        miss = true;
      }
      else {
        cur.face = face;
        face.halves.add(cur);
        if (cur.next == start) {
          closed = true;
        }
        else if (cur.next == null) {
          miss = true;
        }
        else {
          cur = cur.next;
        }
      }
    }
    if (miss || !closed || face.halves.isEmpty()) return null;
    face.signedArea = signedArea(face.halves);
    face.bounded = face.signedArea > eps * eps;
    return face;
  }

  private static List<CurveSegmentString> splitAll(
      List<List<CurveSegmentString>> groups, List<Coordinate> pool,
      double eps) {
    List<CurveSegmentString> out = new ArrayList<CurveSegmentString>();
    boolean miss = false;
    for (int g = 0; g < groups.size() && !miss; g++) {
      List<CurveSegmentString> strings = groups.get(g);
      if (strings == null) {
        miss = true;
      }
      else {
        for (int i = 0; i < strings.size() && !miss; i++) {
          List<CurveSegmentString> parts = split(strings.get(i), pool, eps);
          if (parts == null) {
            miss = true;
          }
          else {
            out.addAll(parts);
          }
        }
      }
    }
    return miss ? null : out;
  }

  private static List<CurveSegmentString> split(CurveSegmentString s,
      List<Coordinate> pool, double eps) {
    if (s == null) return null;
    List<Cut> cuts = new ArrayList<Cut>();
    TwoNodeClip.Edge e = s.asEdge();
    for (int i = 0; i < pool.size(); i++) {
      Coordinate p = pool.get(i);
      if (onString(s, p, eps)) {
        cuts.add(new Cut(e.param(p), p));
      }
    }
    if (cuts.size() < 2) {
      List<CurveSegmentString> one = new ArrayList<CurveSegmentString>(1);
      if (!s.isDegenerate()) {
        one.add(s);
      }
      return one;
    }
    Collections.sort(cuts, Cut.BY_T);
    List<Cut> uniq = new ArrayList<Cut>();
    for (int i = 0; i < cuts.size(); i++) {
      Cut c = cuts.get(i);
      if (uniq.isEmpty()
          || uniq.get(uniq.size() - 1).pt.distance(c.pt) > eps) {
        uniq.add(c);
      }
    }
    List<CurveSegmentString> out = new ArrayList<CurveSegmentString>();
    for (int i = 0; i + 1 < uniq.size(); i++) {
      Coordinate a = uniq.get(i).pt;
      Coordinate b = uniq.get(i + 1).pt;
      if (a.distance(b) > eps) {
        out.add(sub(s, a, b));
      }
    }
    return out;
  }

  private static CurveSegmentString sub(CurveSegmentString s, Coordinate from,
      Coordinate to) {
    if (!s.isArc()) {
      return CurveSegmentString.segment(from, to);
    }
    Coordinate mid = TwoNodeClip.midOnSweep(from, to, s.asEdge());
    return CurveSegmentString.arc(from, mid, to);
  }

  private static boolean onString(CurveSegmentString s, Coordinate p,
      double eps) {
    TwoNodeClip.Edge e = s.asEdge();
    if (s.isArc()) {
      double d = Math.hypot(p.x - e.circle[0], p.y - e.circle[1]);
      if (Math.abs(d - e.circle[2]) > eps) return false;
      return TwoNodeClip.isOnSweep(p, e.circle, e.a, e.mid, e.b);
    }
    double t = TwoNodeClip.parameter(e.a, e.b, p);
    Coordinate q = new Coordinate(e.a.x + t * (e.b.x - e.a.x),
        e.a.y + t * (e.b.y - e.a.y));
    return p.distance(q) <= eps;
  }

  private static List<CurveSegmentString> mergeCoincident(
      List<CurveSegmentString> pieces, double scale, double eps) {
    List<CurveSegmentString> out = new ArrayList<CurveSegmentString>();
    for (int i = 0; i < pieces.size(); i++) {
      CurveSegmentString p = pieces.get(i);
      if (!p.isDegenerate() && !containsPiece(out, p, scale, eps)) {
        out.add(p);
      }
    }
    return out;
  }

  private static boolean containsPiece(List<CurveSegmentString> out,
      CurveSegmentString p, double scale, double eps) {
    boolean seen = false;
    for (int i = 0; i < out.size() && !seen; i++) {
      if (samePiece(out.get(i), p, scale, eps)) {
        seen = true;
      }
    }
    return seen;
  }

  private static boolean samePiece(CurveSegmentString p, CurveSegmentString q,
      double scale, double eps) {
    boolean ends = (p.getStart().distance(q.getStart()) <= eps
            && p.getEnd().distance(q.getEnd()) <= eps)
        || (p.getStart().distance(q.getEnd()) <= eps
            && p.getEnd().distance(q.getStart()) <= eps);
    if (!ends) return false;
    if (p.isArc() != q.isArc()) return false;
    if (!p.isArc()) return true;
    return CurveSegmentString.sameCircle(p, q, scale);
  }

  private static List<Half> buildHalves(List<CurveSegmentString> pieces,
      List<Coordinate> pool, double eps) {
    List<Half> halves = new ArrayList<Half>();
    boolean miss = false;
    for (int i = 0; i < pieces.size() && !miss; i++) {
      CurveSegmentString p = pieces.get(i);
      Coordinate a = canon(p.getStart(), pool, eps);
      Coordinate b = canon(p.getEnd(), pool, eps);
      if (a.distance(b) <= eps) {
        continue;
      }
      CurveSegmentString fwd = sub(p, a, b);
      CurveSegmentString rev = reverse(fwd);
      if (fwd.isDegenerate() || rev.isDegenerate()) {
        miss = true;
      }
      else {
        Half hf = new Half(a, b, fwd);
        Half hr = new Half(b, a, rev);
        hf.twin = hr;
        hr.twin = hf;
        halves.add(hf);
        halves.add(hr);
      }
    }
    return miss ? null : halves;
  }

  private static CurveSegmentString reverse(CurveSegmentString s) {
    if (!s.isArc()) {
      return CurveSegmentString.segment(s.getEnd(), s.getStart());
    }
    return CurveSegmentString.arc(s.getEnd(), s.getMid(), s.getStart());
  }

  private static List<Vertex> indexByStart(List<Half> halves,
      List<Coordinate> pool, double eps) {
    List<Vertex> verts = new ArrayList<Vertex>();
    for (int i = 0; i < pool.size(); i++) {
      verts.add(new Vertex(pool.get(i)));
    }
    boolean miss = false;
    for (int i = 0; i < halves.size() && !miss; i++) {
      Half h = halves.get(i);
      Vertex v = vertexAt(verts, h.origin, eps);
      if (v == null) {
        miss = true;
      }
      else {
        v.out.add(h);
        h.originVertex = v;
      }
    }
    if (miss) return null;
    List<Vertex> used = new ArrayList<Vertex>();
    for (int i = 0; i < verts.size(); i++) {
      Vertex v = verts.get(i);
      if (!v.out.isEmpty()) {
        Collections.sort(v.out, Half.BY_ANGLE);
        used.add(v);
      }
    }
    return used;
  }

  /**
   * Two different pieces leaving in the same direction are a
   * tangent. Distinct direction points stay distinct (locationtech
   * #1224); the quadrant comes from the endpoints, not FP deltas
   * (#1226). Ordering a true tie is snap-rounding (P2.5.4). Stamp
   * and stop.
   */
  private static boolean hasCoincidentLeave(List<Vertex> verts) {
    boolean hit = false;
    for (int i = 0; i < verts.size() && !hit; i++) {
      List<Half> out = verts.get(i).out;
      for (int j = 0; j < out.size() && !hit; j++) {
        Half a = out.get(j);
        Half b = out.get((j + 1) % out.size());
        if (a != b && leavesCoincide(a, b)) {
          hit = true;
        }
      }
    }
    return hit;
  }

  private static double signedArea(List<Half> members) {
    double signed = 0.0;
    for (int i = 0; i < members.size(); i++) {
      CurveSegmentString s = members.get(i).member;
      if (s.isArc()) {
        signed += CircularArcDensifier.arcAreaContribution(
            s.getStart(), s.getMid(), s.getEnd());
      }
      else {
        Coordinate a = s.getStart();
        Coordinate b = s.getEnd();
        signed += 0.5 * (a.x * b.y - b.x * a.y);
      }
    }
    return signed;
  }

  private static void addEdgeEnds(List<Coordinate> pool,
      List<List<CurveSegmentString>> groups, double scale, double eps) {
    for (int i = 0; i < groups.size(); i++) {
      for (int j = i + 1; j < groups.size(); j++) {
        List<CurveSegmentString> edges = CurveSegmentNoder.edges(
            groups.get(i), groups.get(j), scale);
        if (edges != null) {
          for (int k = 0; k < edges.size(); k++) {
            CurveSegmentString e = edges.get(k);
            canon(e.getStart(), pool, eps);
            canon(e.getEnd(), pool, eps);
          }
        }
      }
    }
  }

  private static void addStringEnds(List<Coordinate> pool,
      List<List<CurveSegmentString>> groups, double eps) {
    for (int g = 0; g < groups.size(); g++) {
      List<CurveSegmentString> strings = groups.get(g);
      if (strings != null) {
        for (int i = 0; i < strings.size(); i++) {
          CurveSegmentString s = strings.get(i);
          canon(s.getStart(), pool, eps);
          canon(s.getEnd(), pool, eps);
        }
      }
    }
  }

  private static void addCanon(List<Coordinate> pool, Coordinate[] xs,
      double eps) {
    if (xs == null) return;
    for (int i = 0; i < xs.length; i++) {
      canon(xs[i], pool, eps);
    }
  }

  private static Coordinate canon(Coordinate p, List<Coordinate> pool,
      double eps) {
    Coordinate found = null;
    for (int i = 0; i < pool.size() && found == null; i++) {
      if (pool.get(i).distance(p) <= eps) {
        found = pool.get(i);
      }
    }
    if (found != null) return found;
    Coordinate n = new Coordinate(p);
    pool.add(n);
    return n;
  }

  private static boolean nearPool(Coordinate p, List<Coordinate> pool,
      double eps) {
    boolean found = false;
    for (int i = 0; i < pool.size() && !found; i++) {
      if (pool.get(i).distance(p) <= eps) {
        found = true;
      }
    }
    return found;
  }

  private static Vertex vertexAt(List<Vertex> verts, Coordinate p, double eps) {
    Vertex found = null;
    for (int i = 0; i < verts.size() && found == null; i++) {
      if (verts.get(i).pt.distance(p) <= eps) {
        found = verts.get(i);
      }
    }
    return found;
  }

  /**
   * CCW order of leave directions around a shared origin. Same
   * walk as locationtech {@code HalfEdge.compareAngularDirection}
   * after #1224 / #1226, kept here: this DCEL does not use core
   * {@code HalfEdge} or {@code Quadrant}. Direction-point equality
   * first (not subtracted-vector {@code ==}), then quadrant from
   * the endpoints, then {@link Orientation#index}.
   */
  static int compareLeave(Half a, Half b) {
    if (a.leaveDir.equals2D(b.leaveDir)) {
      return 0;
    }
    int qa = leaveQuadrant(a);
    int qb = leaveQuadrant(b);
    if (qa > qb) {
      return 1;
    }
    if (qa < qb) {
      return -1;
    }
    return Orientation.index(b.origin, b.leaveDir, a.leaveDir);
  }

  /**
   * True same leave, or an arc near-tangent. Distinct chord
   * direction points that {@link #compareLeave} separates are
   * not a stamp, even when atan2 of the deltas collapsed.
   */
  static boolean leavesCoincide(Half a, Half b) {
    if (compareLeave(a, b) == 0) {
      return true;
    }
    if (!a.member.isArc() && !b.member.isArc()) {
      return false;
    }
    return angleDiff(leaveAngle(a), leaveAngle(b)) < ANGLE_EPS;
  }

  private static double leaveAngle(Half h) {
    return Math.atan2(h.leaveDir.y - h.origin.y, h.leaveDir.x - h.origin.x);
  }

  private static double angleDiff(double a, double b) {
    double d = Math.abs(a - b);
    if (d > Math.PI) {
      d = TwoNodeClip.TWO_PI - d;
    }
    return d;
  }

  /**
   * Quadrant of the leave ray from the endpoints, not from
   * subtracted {@code dx}/{@code dy}. A chord uses origin→dest.
   * An arc uses origin vs centre so the tangent quadrant does
   * not go through {@code (origin - centre)}.
   */
  private static int leaveQuadrant(Half h) {
    if (!h.member.isArc()) {
      return quadrant(h.origin, h.dest);
    }
    TwoNodeClip.Edge e = h.member.asEdge();
    Coordinate c = new Coordinate(e.circle[0], e.circle[1]);
    if (sweepCcw(e)) {
      return quadrantBits(c.y >= h.origin.y, h.origin.x >= c.x);
    }
    return quadrantBits(h.origin.y >= c.y, c.x >= h.origin.x);
  }

  private static int quadrant(Coordinate o, Coordinate d) {
    return quadrantBits(d.x >= o.x, d.y >= o.y);
  }

  private static int quadrantBits(boolean xNonNeg, boolean yNonNeg) {
    if (xNonNeg) {
      return yNonNeg ? 0 : 3;
    }
    return yNonNeg ? 1 : 2;
  }

  /**
   * Point that names the leave direction. A chord uses dest. An
   * arc uses the tangent at the start (radius rotated 90°), not
   * the chord to dest.
   */
  private static Coordinate leaveDir(CurveSegmentString s,
      Coordinate origin, Coordinate dest) {
    if (!s.isArc()) {
      return dest;
    }
    TwoNodeClip.Edge e = s.asEdge();
    double rx = origin.x - e.circle[0];
    double ry = origin.y - e.circle[1];
    if (sweepCcw(e)) {
      return new Coordinate(origin.x - ry, origin.y + rx);
    }
    return new Coordinate(origin.x + ry, origin.y - rx);
  }

  private static boolean sweepCcw(TwoNodeClip.Edge e) {
    double a0 = Math.atan2(e.a.y - e.circle[1], e.a.x - e.circle[0]);
    double aM = Math.atan2(e.mid.y - e.circle[1], e.mid.x - e.circle[0]);
    double a1 = Math.atan2(e.b.y - e.circle[1], e.b.x - e.circle[0]);
    return TwoNodeClip.normPos(aM - a0) < TwoNodeClip.normPos(a1 - a0);
  }

  private static boolean hasHole(Geometry g) {
    Geometry geom = unwrap(g);
    if (geom instanceof CurvePolygon) {
      return ((CurvePolygon) geom).getNumInteriorRing() > 0;
    }
    if (geom instanceof Polygon) {
      return ((Polygon) geom).getNumInteriorRing() > 0;
    }
    return false;
  }

  private static Geometry unwrap(Geometry g) {
    if (g == null || g.isEmpty()) return null;
    if (g instanceof MultiSurface) {
      if (g.getNumGeometries() != 1) return null;
      return unwrap(g.getGeometryN(0));
    }
    return g;
  }

  private static double scaleOf(Geometry[] geoms) {
    double s = 1.0;
    if (geoms == null) return s;
    for (int i = 0; i < geoms.length; i++) {
      if (geoms[i] == null || geoms[i].isEmpty()) {
        continue;
      }
      double w = Math.max(geoms[i].getEnvelopeInternal().getWidth(),
          geoms[i].getEnvelopeInternal().getHeight());
      if (w > s) {
        s = w;
      }
    }
    return s;
  }

  private static final class Cut {
    static final Comparator<Cut> BY_T = new Comparator<Cut>() {
      public int compare(Cut a, Cut b) {
        return Double.compare(a.t, b.t);
      }
    };
    final double t;
    final Coordinate pt;
    Cut(double t, Coordinate pt) {
      this.t = t;
      this.pt = pt;
    }
  }

  /**
   * Directed half-edge. Twin is the reverse. {@link #next} /
   * {@link #prev} walk the left face. Member is a
   * {@link CurveSegmentString}.
   */
  static final class Half {
    static final Comparator<Half> BY_ANGLE = new Comparator<Half>() {
      public int compare(Half a, Half b) {
        return compareLeave(a, b);
      }
    };
    final Coordinate origin;
    final Coordinate dest;
    final CurveSegmentString member;
    final Coordinate leaveDir;
    Half twin;
    Half next;
    Half prev;
    Face face;
    Vertex originVertex;

    Half(Coordinate origin, Coordinate dest, CurveSegmentString member) {
      this.origin = origin;
      this.dest = dest;
      this.member = member;
      this.leaveDir = leaveDir(member, origin, dest);
    }

    Coordinate origin() {
      return origin;
    }

    Coordinate dest() {
      return dest;
    }

    Half twin() {
      return twin;
    }

    Half next() {
      return next;
    }

    Half prev() {
      return prev;
    }

    Face face() {
      return face;
    }

    CurveSegmentString member() {
      return member;
    }

    boolean isArc() {
      return member.isArc();
    }

    LineString toLine(GeometryFactory f) {
      if (!member.isArc()) {
        return f.createLineString(new Coordinate[] {
            new Coordinate(origin), new Coordinate(dest)
        });
      }
      return TwoNodeClip.arc(origin, member.getMid(), dest, f);
    }
  }

  /**
   * Left face of a next-cycle. Bounded when the walk has positive
   * signed area (CCW). The complementary outer (union) ring is
   * bounded too; {@link CurveSegmentFaces} drops it when assembling
   * Geometry. Unbounded is the clockwise exterior.
   */
  static final class Face {
    final List<Half> halves = new ArrayList<Half>();
    double signedArea;
    boolean bounded;

    Half edge() {
      return halves.isEmpty() ? null : halves.get(0);
    }

    boolean isBounded() {
      return bounded;
    }

    int edgeCount() {
      return halves.size();
    }

    double signedArea() {
      return signedArea;
    }

    List<LineString> toLines(GeometryFactory f) {
      List<LineString> members = new ArrayList<LineString>(halves.size());
      boolean miss = false;
      for (int i = 0; i < halves.size() && !miss; i++) {
        LineString ls = halves.get(i).toLine(f);
        if (ls == null) {
          miss = true;
        }
        else {
          members.add(ls);
        }
      }
      return miss ? null : members;
    }
  }

  static final class Vertex {
    final Coordinate pt;
    final List<Half> out = new ArrayList<Half>();

    Vertex(Coordinate pt) {
      this.pt = pt;
    }

    Coordinate coordinate() {
      return pt;
    }

    List<Half> outgoing() {
      return out;
    }

    int degree() {
      return out.size();
    }
  }
}
