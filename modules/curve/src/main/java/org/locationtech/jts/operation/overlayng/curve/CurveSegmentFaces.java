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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularArcDensifier;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * Faces of an N-shell arrangement. Package-private -- not a public
 * API, not an N-ary overlay, not a noder. OverlayNG stays binary;
 * CAP / CUP / SUB / XOR of a pair among the N stay on the existing
 * kits. This rung splits the P2.5.2 node set (and P2.2 shared-edge
 * ends) into pieces and walks left-most / next-outgoing.
 * <p>
 * N=2 recovers the pair-kit faces: a discrete crossing walks the
 * same rings {@link TwoShellClip} / {@link NSpanClip} /
 * {@link CircularDiscOverlay} already assemble (CAP + XOR). A
 * 0-node containment or a same-circle special case falls back to
 * those kits. MIXED (collinear overlap) recovers the pair-kit
 * faces once {@link MixedOverlapOverlay} certifies the shared
 * edge (CAP + XOR = inner + bite). Pinch / holed Geometry-level
 * stays {@code null} -- hole rings are walked as strings, as in
 * P2.3 / P2.4. A coincident leave-angle at a node (near-tangent) is
 * snap-rounding (P2.5.4): {@code faces} returns {@code null} and
 * {@link #missReason()} names {@link #TANGENT_LEAVE_ANGLE}.
 * Ordering those leaves needs HotPixel / ScaledNoder / core
 * {@code SegmentString} -- stamp and stop. Densify is never a
 * noder. Not P2.5.5.
 */
final class CurveSegmentFaces {

  /** Named stamp: coincident leave-angle. Snap-rounding, not a walk. */
  static final String TANGENT_LEAVE_ANGLE = "P2.5.4 tangent leave-angle";

  private static final double ANGLE_EPS = 1.0e-8;

  private static String missReason;

  private CurveSegmentFaces() { }

  /**
   * Why the last {@link #faces(Geometry[])} / string-group call
   * returned {@code null}, or {@code null} when faces were
   * produced. Package-private -- not a public API.
   */
  static String missReason() {
    return missReason;
  }

  /**
   * Bounded faces of N hole-free circular / compound shells, or
   * {@code null}. N=2 is the pair-kit rings. N≥3 is the walk, or
   * {@code null} when the walk would need snap-rounding
   * ({@link #missReason()} names the stamp).
   */
  static Geometry faces(Geometry[] geoms) {
    missReason = null;
    if (geoms == null || geoms.length < 2) return null;
    List<List<CurveSegmentString>> groups =
        new ArrayList<List<CurveSegmentString>>(geoms.length);
    Geometry factorySrc = null;
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
          if (factorySrc == null) {
            factorySrc = geoms[i];
          }
        }
      }
    }
    if (miss) return null;
    GeometryFactory f = factorySrc == null
        ? new CurveGeometryFactory()
        : TwoNodeClip.curveFactory(factorySrc);
    return faces(groups, scaleOf(geoms), f, geoms);
  }

  /**
   * Bounded faces of N string collections. Same walk as
   * {@link #faces(Geometry[])}; no pair-kit fallback (no Geometry
   * overlay to recover).
   */
  static Geometry faces(List<List<CurveSegmentString>> groups, double scale) {
    return faces(groups, scale, new CurveGeometryFactory(), null);
  }

  private static Geometry faces(List<List<CurveSegmentString>> groups,
      double scale, GeometryFactory f, Geometry[] geoms) {
    missReason = null;
    if (groups == null || groups.size() < 2) return null;
    Coordinate[] nodes = CurveSegmentNoder.nodes(groups, scale);
    if (nodes == null) {
      return geoms != null && geoms.length == 2
          ? pairKitFaces(geoms[0], geoms[1])
          : null;
    }
    Geometry walked = walk(groups, nodes, scale, f);
    if (walked != null) {
      missReason = null;
      return walked;
    }
    if (geoms != null && geoms.length == 2) {
      Geometry kit = pairKitFaces(geoms[0], geoms[1]);
      if (kit != null) {
        missReason = null;
      }
      return kit;
    }
    return null;
  }

  /**
   * Pair-kit CAP + XOR components. Empty CAP is not a miss. A kit
   * that cannot certify (pinch, lineal) is {@code null}. MIXED
   * collinear overlap is {@link MixedOverlapOverlay} (shared
   * edge, not a discrete node pair). Does not call
   * {@link OverlayNGCurve} -- that would densify.
   */
  static Geometry pairKitFaces(Geometry a, Geometry b) {
    Geometry cap = exactOverlay(a, b, OverlayNG.INTERSECTION);
    Geometry xor = exactOverlay(a, b, OverlayNG.SYMDIFFERENCE);
    if (cap == null || xor == null) return null;
    List<Polygon> faces = new ArrayList<Polygon>();
    if (!addPoly(faces, cap) || !addPoly(faces, xor)) return null;
    return toGeometry(faces, TwoNodeClip.curveFactory(a));
  }

  private static Geometry walk(List<List<CurveSegmentString>> groups,
      Coordinate[] nodes, double scale, GeometryFactory f) {
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    List<Coordinate> pool = new ArrayList<Coordinate>();
    addCanon(pool, nodes, eps);
    addEdgeEnds(pool, groups, scale, eps);
    addStringEnds(pool, groups, eps);

    List<CurveSegmentString> pieces = splitAll(groups, pool, scale, eps);
    if (pieces == null || pieces.isEmpty()) return null;
    pieces = mergeCoincident(pieces, scale, eps);
    List<Half> halves = buildHalves(pieces, pool, eps);
    if (halves == null) return null;

    List<Vertex> verts = indexByStart(halves, pool, eps);
    if (verts == null) return null;
    if (hasCoincidentLeave(verts)) {
      missReason = TANGENT_LEAVE_ANGLE;
      return null;
    }

    List<Polygon> faces = new ArrayList<Polygon>();
    boolean miss = false;
    for (int i = 0; i < halves.size() && !miss; i++) {
      Half start = halves.get(i);
      if (!start.used) {
        List<LineString> members = walkRing(start, eps, f);
        if (members == null) {
          miss = true;
        }
        else if (Math.abs(signedArea(members)) > eps * eps) {
          Polygon face = TwoNodeClip.closeRing(members, f, eps);
          if (face == null) {
            miss = true;
          }
          else {
            faces.add(face);
          }
        }
      }
    }
    if (miss || faces.isEmpty()) return null;
    dropUnion(faces, eps);
    if (faces.isEmpty()) return null;
    return toGeometry(faces, f);
  }

  /**
   * The walk also closes the union (the complementary outer ring).
   * That ring's area is the sum of the bounded faces; drop it.
   */
  private static void dropUnion(List<Polygon> faces, double eps) {
    if (faces.size() < 2) return;
    int maxAt = 0;
    double maxA = faces.get(0).getArea();
    double sum = maxA;
    for (int i = 1; i < faces.size(); i++) {
      double a = faces.get(i).getArea();
      sum += a;
      if (a > maxA) {
        maxA = a;
        maxAt = i;
      }
    }
    if (Math.abs(maxA - (sum - maxA)) <= Math.max(eps, 1.0e-8)) {
      faces.remove(maxAt);
    }
  }

  private static List<CurveSegmentString> splitAll(
      List<List<CurveSegmentString>> groups, List<Coordinate> pool,
      double scale, double eps) {
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
        hf.rev = hr;
        hr.rev = hf;
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
      Vertex v = vertexAt(verts, h.from, eps);
      if (v == null) {
        miss = true;
      }
      else {
        v.out.add(h);
      }
    }
    if (miss) return null;
    for (int i = 0; i < verts.size(); i++) {
      List<Half> out = verts.get(i).out;
      Collections.sort(out, Half.BY_ANGLE);
      for (int j = 0; j < out.size(); j++) {
        out.get(j).originOut = out;
      }
    }
    return verts;
  }

  /**
   * Two different pieces leaving at the same angle are a tangent.
   * Ordering them is snap-rounding (P2.5.4). Stamp and stop.
   */
  private static boolean hasCoincidentLeave(List<Vertex> verts) {
    boolean hit = false;
    for (int i = 0; i < verts.size() && !hit; i++) {
      List<Half> out = verts.get(i).out;
      for (int j = 0; j < out.size() && !hit; j++) {
        Half a = out.get(j);
        Half b = out.get((j + 1) % out.size());
        if (a != b && angleDiff(a.leaveAngle, b.leaveAngle) < ANGLE_EPS) {
          hit = true;
        }
      }
    }
    return hit;
  }

  private static List<LineString> walkRing(Half start, double eps,
      GeometryFactory f) {
    List<LineString> members = new ArrayList<LineString>();
    Half cur = start;
    int guard = 0;
    boolean closed = false;
    boolean miss = false;
    while (guard++ < 256 && !closed && !miss) {
      if (cur.used) {
        miss = true;
      }
      else {
        cur.used = true;
        LineString ls = toLine(cur, f);
        if (ls == null) {
          miss = true;
        }
        else {
          members.add(ls);
          Half next = nextAfter(cur.rev);
          if (next == null) {
            miss = true;
          }
          else if (next == start) {
            closed = true;
          }
          else {
            cur = next;
          }
        }
      }
    }
    if (miss || !closed || members.isEmpty()) return null;
    Coordinate a = members.get(0).getCoordinateN(0);
    LineString last = members.get(members.size() - 1);
    Coordinate b = last.getCoordinateN(last.getNumPoints() - 1);
    if (a.distance(b) > eps) return null;
    return members;
  }

  /**
   * Left-most: the next half after {@code back} in CCW leave-angle
   * order at {@code back.from}.
   */
  private static Half nextAfter(Half back) {
    // Re-find the vertex outgoing by scanning the reverse's neighbours
    // is awkward; store the sorted list on the half at index time.
    List<Half> out = back.originOut;
    if (out == null || out.isEmpty()) return null;
    int at = -1;
    for (int i = 0; i < out.size(); i++) {
      if (out.get(i) == back) {
        at = i;
      }
    }
    if (at < 0) return null;
    return out.get((at + 1) % out.size());
  }

  private static LineString toLine(Half h, GeometryFactory f) {
    CurveSegmentString s = h.piece;
    if (!s.isArc()) {
      return f.createLineString(new Coordinate[] {
          new Coordinate(h.from), new Coordinate(h.to)
      });
    }
    return TwoNodeClip.arc(h.from, s.getMid(), h.to, f);
  }

  private static double signedArea(List<LineString> members) {
    double signed = 0.0;
    for (int i = 0; i < members.size(); i++) {
      LineString m = members.get(i);
      if (m instanceof CircularString) {
        Coordinate[] pts = m.getCoordinates();
        for (int k = 0; k + 2 < pts.length; k += 2) {
          signed += CircularArcDensifier.arcAreaContribution(
              pts[k], pts[k + 1], pts[k + 2]);
        }
      }
      else {
        Coordinate[] pts = m.getCoordinates();
        for (int k = 0; k < pts.length - 1; k++) {
          signed += 0.5 * (pts[k].x * pts[k + 1].y
              - pts[k + 1].x * pts[k].y);
        }
      }
    }
    return signed;
  }

  private static Geometry exactOverlay(Geometry a, Geometry b, int opCode) {
    Geometry g = CircularDiscOverlay.overlay(a, b, opCode);
    if (g != null) return g;
    g = CircularDiscPolygonOverlay.overlay(a, b, opCode);
    if (g != null) return g;
    return CompoundCurveShellOverlay.overlay(a, b, opCode);
  }

  private static boolean addPoly(List<Polygon> dest, Geometry g) {
    if (g == null) return false;
    if (g.isEmpty()) return true;
    if (g instanceof Polygon) {
      dest.add((Polygon) g);
      return true;
    }
    boolean ok = true;
    for (int i = 0; i < g.getNumGeometries() && ok; i++) {
      if (!addPoly(dest, g.getGeometryN(i))) {
        ok = false;
      }
    }
    return ok;
  }

  private static Geometry toGeometry(List<Polygon> faces, GeometryFactory f) {
    if (faces == null || faces.isEmpty()) return null;
    if (faces.size() == 1) return faces.get(0);
    return new MultiSurface(faces.toArray(new Polygon[0]), f);
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

  private static Vertex vertexAt(List<Vertex> verts, Coordinate p, double eps) {
    Vertex found = null;
    for (int i = 0; i < verts.size() && found == null; i++) {
      if (verts.get(i).pt.distance(p) <= eps) {
        found = verts.get(i);
      }
    }
    return found;
  }

  private static double leaveAngle(CurveSegmentString s) {
    Coordinate from = s.getStart();
    if (!s.isArc()) {
      return Math.atan2(s.getEnd().y - from.y, s.getEnd().x - from.x);
    }
    TwoNodeClip.Edge e = s.asEdge();
    double rx = from.x - e.circle[0];
    double ry = from.y - e.circle[1];
    double a0 = Math.atan2(e.a.y - e.circle[1], e.a.x - e.circle[0]);
    double aM = Math.atan2(e.mid.y - e.circle[1], e.mid.x - e.circle[0]);
    double a1 = Math.atan2(e.b.y - e.circle[1], e.b.x - e.circle[0]);
    boolean ccw = TwoNodeClip.normPos(aM - a0) < TwoNodeClip.normPos(a1 - a0);
    return ccw ? Math.atan2(rx, -ry) : Math.atan2(-rx, ry);
  }

  private static double angleDiff(double a, double b) {
    double d = Math.abs(a - b);
    if (d > Math.PI) {
      d = TwoNodeClip.TWO_PI - d;
    }
    return d;
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

  private static final class Half {
    static final Comparator<Half> BY_ANGLE = new Comparator<Half>() {
      public int compare(Half a, Half b) {
        return Double.compare(a.leaveAngle, b.leaveAngle);
      }
    };
    final Coordinate from;
    final Coordinate to;
    final CurveSegmentString piece;
    final double leaveAngle;
    Half rev;
    List<Half> originOut;
    boolean used;
    Half(Coordinate from, Coordinate to, CurveSegmentString piece) {
      this.from = from;
      this.to = to;
      this.piece = piece;
      this.leaveAngle = leaveAngle(piece);
    }
  }

  private static final class Vertex {
    final Coordinate pt;
    final List<Half> out = new ArrayList<Half>();
    Vertex(Coordinate pt) {
      this.pt = pt;
    }
  }
}
