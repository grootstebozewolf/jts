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

import org.locationtech.jts.algorithm.LineIntersector;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.curve.CircularArcDensifier;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveOps;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * Closed-form lineal overlay of a {@link CircularString} (or a lineal
 * {@link CompoundCurve} of LineString + CircularString) against a plain
 * {@link LineString}. Package-private -- not a new public API, and not
 * a noder.
 * <p>
 * Line–circle nodes are exact. A line that hits the arc and misses the
 * control polyline is a node; a line that hits a control chord and
 * misses the arc is not. Arc pieces stay {@link CircularString}; line
 * pieces stay {@link LineString}. A three-point LineString is not an
 * arc. Two CircularStrings, a polygon, or 3+ nodes on one arc window
 * return {@code null} so the caller takes the chord baseline.
 */
final class CircularLineOverlay {

  private static final double EPS = 1.0e-12;

  private CircularLineOverlay() { }

  /**
   * Exact lineal overlay, or {@code null} if this class cannot answer.
   * Zero nodes is an answer (empty CAP, both pieces for CUP), not a miss.
   */
  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    LineString curve = linealCurve(a);
    LineString line = plainLine(b);
    boolean curveFirst = true;
    if (curve == null || line == null) {
      curve = linealCurve(b);
      line = plainLine(a);
      curveFirst = false;
    }
    if (curve == null || line == null) return null;
    // Two lineal curves is arc–arc; out of this slice.
    if (linealCurve(a) != null && linealCurve(b) != null) return null;

    List<TwoNodeClip.Edge> edges = flattenLineal(curve);
    if (edges == null) return null;
    Coordinate[] ring = line.getCoordinates();
    if (ring.length < 2) return null;

    List<List<TwoNodeClip.Node>> byEdge = new ArrayList<List<TwoNodeClip.Node>>();
    List<LineHit> lineHits = new ArrayList<LineHit>();
    double scale = scaleOf(curve, line);
    if (!collectHits(edges, ring, byEdge, lineHits, scale)) {
      return null;
    }

    GeometryFactory f = TwoNodeClip.curveFactory(a);
    if (opCode == OverlayNG.INTERSECTION) {
      return points(allPoints(byEdge), f);
    }
    List<LineString> curvePieces = splitCurve(edges, byEdge, f);
    List<LineString> linePieces = splitLine(ring, lineHits, f);
    if (curvePieces == null || linePieces == null) return null;
    if (opCode == OverlayNG.UNION || opCode == OverlayNG.SYMDIFFERENCE) {
      return linealResult(concat(curvePieces, linePieces), f);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return linealResult(curveFirst ? curvePieces : linePieces, f);
    }
    return null;
  }

  /**
   * A CircularString, or a CompoundCurve with at least one CircularString
   * and the rest plain LineStrings. Single-member MultiCurve unwraps.
   */
  private static LineString linealCurve(Geometry g) {
    g = unwrapLineal(g);
    if (g instanceof CircularString) {
      return g.isEmpty() ? null : (CircularString) g;
    }
    if (!(g instanceof CompoundCurve)) return null;
    CompoundCurve cc = (CompoundCurve) g;
    if (cc.isEmpty()) return null;
    boolean hasArc = false;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      if (m instanceof CircularString) {
        hasArc = true;
      }
      else if (m instanceof CompoundCurve) {
        return null;
      }
    }
    return hasArc ? cc : null;
  }

  private static LineString plainLine(Geometry g) {
    g = unwrapLineal(g);
    if (g == null || g.isEmpty()) return null;
    if (g instanceof CircularString || g instanceof CompoundCurve) return null;
    if (!(g instanceof LineString)) return null;
    if (CurveOps.tolerance(g) > 0.0) return null;
    return (LineString) g;
  }

  private static Geometry unwrapLineal(Geometry g) {
    if (g instanceof MultiCurve || g instanceof MultiLineString) {
      if (g.getNumGeometries() != 1) return null;
      return g.getGeometryN(0);
    }
    return g;
  }

  private static List<TwoNodeClip.Edge> flattenLineal(LineString g) {
    List<TwoNodeClip.Edge> edges = new ArrayList<TwoNodeClip.Edge>();
    if (g instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) g;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        if (!addMember(edges, cc.getMemberN(i))) return null;
      }
    }
    else if (!addMember(edges, g)) {
      return null;
    }
    return edges.isEmpty() ? null : edges;
  }

  private static boolean addMember(List<TwoNodeClip.Edge> edges, LineString m) {
    Coordinate[] pts = m.getCoordinates();
    if (m instanceof CircularString) {
      if (pts.length < 3) return false;
      for (int k = 0; k + 2 < pts.length; k += 2) {
        double[] c = CircularArcDensifier.circumcircle(
            pts[k], pts[k + 1], pts[k + 2]);
        if (c == null) {
          edges.add(new TwoNodeClip.Edge(pts[k], null, pts[k + 2], false, null));
        }
        else {
          edges.add(new TwoNodeClip.Edge(pts[k], pts[k + 1], pts[k + 2], true, c));
        }
      }
      return true;
    }
    if (pts.length < 2) return false;
    for (int k = 0; k < pts.length - 1; k++) {
      edges.add(new TwoNodeClip.Edge(pts[k], null, pts[k + 1], false, null));
    }
    return true;
  }

  /**
   * @return {@code false} on a miss this class will not answer (overlap,
   *         3+ nodes on one arc window)
   */
  private static boolean collectHits(List<TwoNodeClip.Edge> edges,
      Coordinate[] ring, List<List<TwoNodeClip.Node>> byEdge,
      List<LineHit> lineHits, double scale) {
    LineIntersector li = new RobustLineIntersector();
    int n = ring.length - 1;
    for (int i = 0; i < edges.size(); i++) {
      TwoNodeClip.Edge e = edges.get(i);
      List<TwoNodeClip.Node> hits = new ArrayList<TwoNodeClip.Node>();
      for (int j = 0; j < n; j++) {
        if (e.isArc) {
          Coordinate[] xs = TwoNodeClip.intersectSegmentCircle(
              e.circle[0], e.circle[1], e.circle[2], ring[j], ring[j + 1]);
          for (int k = 0; k < xs.length; k++) {
            if (!TwoNodeClip.isOnSweep(xs[k], e.circle, e.a, e.mid, e.b)) {
              continue;
            }
            TwoNodeClip.addUnique(hits,
                new TwoNodeClip.Node(i, e.param(xs[k]), xs[k]), scale);
            addLineHit(lineHits, j, ring[j], ring[j + 1], xs[k], scale);
          }
        }
        else {
          li.computeIntersection(e.a, e.b, ring[j], ring[j + 1]);
          if (li.getIntersectionNum() == LineIntersector.COLLINEAR_INTERSECTION) {
            return false;
          }
          if (li.getIntersectionNum() == 1) {
            Coordinate p = li.getIntersection(0);
            TwoNodeClip.addUnique(hits,
                new TwoNodeClip.Node(i, e.param(p), p), scale);
            addLineHit(lineHits, j, ring[j], ring[j + 1], p, scale);
          }
        }
      }
      if (e.isArc && hits.size() > 2) return false;
      byEdge.add(hits);
    }
    return true;
  }

  private static void addLineHit(List<LineHit> hits, int seg,
      Coordinate a, Coordinate b, Coordinate p, double scale) {
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, EPS);
    for (int i = 0; i < hits.size(); i++) {
      if (hits.get(i).pt.distance(p) <= eps) return;
    }
    hits.add(new LineHit(seg, TwoNodeClip.parameter(a, b, p), p));
  }

  private static List<Coordinate> allPoints(List<List<TwoNodeClip.Node>> byEdge) {
    List<Coordinate> out = new ArrayList<Coordinate>();
    for (int i = 0; i < byEdge.size(); i++) {
      List<TwoNodeClip.Node> hits = byEdge.get(i);
      for (int k = 0; k < hits.size(); k++) {
        out.add(hits.get(k).pt);
      }
    }
    return out;
  }

  private static Geometry points(List<Coordinate> pts, GeometryFactory f) {
    if (pts.isEmpty()) return f.createEmpty(0);
    if (pts.size() == 1) return f.createPoint(new Coordinate(pts.get(0)));
    Coordinate[] c = new Coordinate[pts.size()];
    for (int i = 0; i < pts.size(); i++) {
      c[i] = new Coordinate(pts.get(i));
    }
    return f.createMultiPointFromCoords(c);
  }

  private static List<LineString> splitCurve(List<TwoNodeClip.Edge> edges,
      List<List<TwoNodeClip.Node>> byEdge, GeometryFactory f) {
    List<LineString> out = new ArrayList<LineString>();
    for (int i = 0; i < edges.size(); i++) {
      TwoNodeClip.Edge e = edges.get(i);
      List<TwoNodeClip.Node> hits = new ArrayList<TwoNodeClip.Node>(byEdge.get(i));
      Collections.sort(hits, NODE_T);
      Coordinate cursor = e.a;
      for (int k = 0; k < hits.size(); k++) {
        LineString piece = subEdge(e, cursor, hits.get(k).pt, f);
        if (piece != null) out.add(piece);
        cursor = hits.get(k).pt;
      }
      LineString last = subEdge(e, cursor, e.b, f);
      if (last != null) out.add(last);
    }
    return out;
  }

  private static LineString subEdge(TwoNodeClip.Edge e, Coordinate from,
      Coordinate to, GeometryFactory f) {
    if (from.distance(to) <= EPS) return null;
    if (!e.isArc) {
      return f.createLineString(new Coordinate[] {
          new Coordinate(from), new Coordinate(to)
      });
    }
    Coordinate mid = TwoNodeClip.midOnSweep(from, to, e);
    if (mid == null || mid.distance(from) <= EPS || mid.distance(to) <= EPS) {
      return f.createLineString(new Coordinate[] {
          new Coordinate(from), new Coordinate(to)
      });
    }
    return TwoNodeClip.arc(from, mid, to, f);
  }

  private static List<LineString> splitLine(Coordinate[] ring,
      List<LineHit> hits, GeometryFactory f) {
    int n = ring.length - 1;
    List<LineHit> pts = new ArrayList<LineHit>();
    for (int i = 0; i < n; i++) {
      pts.add(new LineHit(i, 0.0, ring[i]));
    }
    pts.add(new LineHit(n - 1, 1.0, ring[n]));
    pts.addAll(hits);
    Collections.sort(pts);
    List<LineHit> uniq = new ArrayList<LineHit>();
    for (int i = 0; i < pts.size(); i++) {
      LineHit cur = pts.get(i);
      if (!uniq.isEmpty() && uniq.get(uniq.size() - 1).pt.distance(cur.pt) <= EPS) {
        continue;
      }
      uniq.add(cur);
    }
    List<LineString> out = new ArrayList<LineString>();
    for (int i = 0; i < uniq.size() - 1; i++) {
      Coordinate a = uniq.get(i).pt;
      Coordinate b = uniq.get(i + 1).pt;
      if (a.distance(b) <= EPS) continue;
      out.add(f.createLineString(new Coordinate[] {
          new Coordinate(a), new Coordinate(b)
      }));
    }
    return out;
  }

  private static Geometry linealResult(List<LineString> pieces,
      GeometryFactory f) {
    List<LineString> clean = new ArrayList<LineString>();
    for (int i = 0; i < pieces.size(); i++) {
      LineString p = pieces.get(i);
      if (p != null && !p.isEmpty() && p.getNumPoints() >= 2) {
        clean.add(p);
      }
    }
    if (clean.isEmpty()) return f.createEmpty(1);
    if (clean.size() == 1) return clean.get(0);
    return f.createMultiLineString(clean.toArray(new LineString[0]));
  }

  private static List<LineString> concat(List<LineString> a, List<LineString> b) {
    List<LineString> out = new ArrayList<LineString>(a.size() + b.size());
    out.addAll(a);
    out.addAll(b);
    return out;
  }

  private static double scaleOf(Geometry a, Geometry b) {
    double w = Math.max(a.getEnvelopeInternal().getWidth(),
        a.getEnvelopeInternal().getHeight());
    double v = Math.max(b.getEnvelopeInternal().getWidth(),
        b.getEnvelopeInternal().getHeight());
    return Math.max(Math.max(w, v), 1.0);
  }

  private static final Comparator<TwoNodeClip.Node> NODE_T =
      new Comparator<TwoNodeClip.Node>() {
        public int compare(TwoNodeClip.Node a, TwoNodeClip.Node b) {
          return Double.compare(a.t, b.t);
        }
      };

  private static final class LineHit implements Comparable<LineHit> {
    final int seg;
    final double t;
    final Coordinate pt;
    LineHit(int seg, double t, Coordinate pt) {
      this.seg = seg;
      this.t = t;
      this.pt = pt;
    }

    public int compareTo(LineHit o) {
      if (seg != o.seg) return seg < o.seg ? -1 : 1;
      return Double.compare(t, o.t);
    }
  }
}
