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
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * Closed-form lineal overlay of two {@link CircularString}s (or a lineal
 * {@link CompoundCurve} against a CircularString). Package-private -- not
 * a new public API, and not a noder.
 * <p>
 * Nodes are the circle–circle hits that lie on <em>both</em> sweeps, not
 * the control-chord crossings. CAP is the node Point / MultiPoint; CUP /
 * SUB / XOR split each arc at those nodes and keep {@link CircularString}
 * pieces. A three-point LineString is not an arc (that pair stays on
 * {@link CircularLineOverlay}). Same-circle overlap, 3+ nodes on one
 * sweep window, or a pair this class cannot answer exactly return
 * {@code null} so the caller takes the chord baseline.
 */
final class CircularArcOverlay {

  private static final double EPS = 1.0e-12;

  private CircularArcOverlay() { }

  /**
   * Exact lineal overlay of two curve lines, or {@code null} if this
   * class cannot answer. Zero nodes is an answer (empty CAP, both
   * pieces for CUP), not a miss.
   */
  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    LineString ca = CircularLineOverlay.linealCurve(a);
    LineString cb = CircularLineOverlay.linealCurve(b);
    if (ca == null || cb == null) return null;

    List<TwoNodeClip.Edge> edgesA = CircularLineOverlay.flattenLineal(ca);
    List<TwoNodeClip.Edge> edgesB = CircularLineOverlay.flattenLineal(cb);
    if (edgesA == null || edgesB == null) return null;

    double scale = CircularLineOverlay.scaleOf(ca, cb);
    List<List<TwoNodeClip.Node>> byA = emptyHits(edgesA.size());
    List<List<TwoNodeClip.Node>> byB = emptyHits(edgesB.size());
    if (!collectHits(edgesA, edgesB, byA, byB, scale)) {
      return null;
    }

    GeometryFactory f = TwoNodeClip.curveFactory(a);
    if (opCode == OverlayNG.INTERSECTION) {
      return CircularLineOverlay.points(allPoints(byA, scale), f);
    }
    List<LineString> piecesA = CircularLineOverlay.splitCurve(edgesA, byA, f);
    List<LineString> piecesB = CircularLineOverlay.splitCurve(edgesB, byB, f);
    if (piecesA == null || piecesB == null) return null;
    if (opCode == OverlayNG.UNION || opCode == OverlayNG.SYMDIFFERENCE) {
      return CircularLineOverlay.linealResult(concat(piecesA, piecesB), f);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return CircularLineOverlay.linealResult(piecesA, f);
    }
    return null;
  }

  /**
   * @return {@code false} on a miss this class will not answer
   *         (same-circle pair, collinear overlap, 3+ nodes on one
   *         arc window)
   */
  private static boolean collectHits(List<TwoNodeClip.Edge> edgesA,
      List<TwoNodeClip.Edge> edgesB, List<List<TwoNodeClip.Node>> byA,
      List<List<TwoNodeClip.Node>> byB, double scale) {
    LineIntersector li = new RobustLineIntersector();
    for (int i = 0; i < edgesA.size(); i++) {
      TwoNodeClip.Edge ea = edgesA.get(i);
      for (int j = 0; j < edgesB.size(); j++) {
        TwoNodeClip.Edge eb = edgesB.get(j);
        if (!pairHits(ea, eb, i, j, byA, byB, li, scale)) {
          return false;
        }
      }
      if (ea.isArc && byA.get(i).size() > 2) return false;
    }
    for (int j = 0; j < edgesB.size(); j++) {
      if (edgesB.get(j).isArc && byB.get(j).size() > 2) return false;
    }
    return true;
  }

  private static boolean pairHits(TwoNodeClip.Edge ea, TwoNodeClip.Edge eb,
      int i, int j, List<List<TwoNodeClip.Node>> byA,
      List<List<TwoNodeClip.Node>> byB, LineIntersector li, double scale) {
    if (ea.isArc && eb.isArc) {
      if (sameCircle(ea, eb, scale)) return false;
      Coordinate[] xs = TwoNodeClip.intersectCircles(
          ea.circle[0], ea.circle[1], ea.circle[2],
          eb.circle[0], eb.circle[1], eb.circle[2]);
      for (int k = 0; k < xs.length; k++) {
        if (!TwoNodeClip.isOnSweep(xs[k], ea.circle, ea.a, ea.mid, ea.b)) {
          continue;
        }
        if (!TwoNodeClip.isOnSweep(xs[k], eb.circle, eb.a, eb.mid, eb.b)) {
          continue;
        }
        addHit(byA.get(i), i, ea, xs[k], scale);
        addHit(byB.get(j), j, eb, xs[k], scale);
      }
      return true;
    }
    if (ea.isArc) {
      return lineVsArc(eb, ea, j, i, byB, byA, scale);
    }
    if (eb.isArc) {
      return lineVsArc(ea, eb, i, j, byA, byB, scale);
    }
    li.computeIntersection(ea.a, ea.b, eb.a, eb.b);
    if (li.getIntersectionNum() == LineIntersector.COLLINEAR_INTERSECTION) {
      return false;
    }
    if (li.getIntersectionNum() == 1) {
      Coordinate p = li.getIntersection(0);
      addHit(byA.get(i), i, ea, p, scale);
      addHit(byB.get(j), j, eb, p, scale);
    }
    return true;
  }

  private static boolean lineVsArc(TwoNodeClip.Edge line, TwoNodeClip.Edge arc,
      int lineIdx, int arcIdx, List<List<TwoNodeClip.Node>> byLine,
      List<List<TwoNodeClip.Node>> byArc, double scale) {
    Coordinate[] xs = TwoNodeClip.intersectSegmentCircle(
        arc.circle[0], arc.circle[1], arc.circle[2], line.a, line.b);
    for (int k = 0; k < xs.length; k++) {
      if (!TwoNodeClip.isOnSweep(xs[k], arc.circle, arc.a, arc.mid, arc.b)) {
        continue;
      }
      addHit(byArc.get(arcIdx), arcIdx, arc, xs[k], scale);
      addHit(byLine.get(lineIdx), lineIdx, line, xs[k], scale);
    }
    return true;
  }

  private static boolean sameCircle(TwoNodeClip.Edge a, TwoNodeClip.Edge b,
      double scale) {
    if (!a.isArc || !b.isArc) return false;
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, EPS);
    Coordinate ca = new Coordinate(a.circle[0], a.circle[1]);
    Coordinate cb = new Coordinate(b.circle[0], b.circle[1]);
    return ca.distance(cb) <= eps && Math.abs(a.circle[2] - b.circle[2]) <= eps;
  }

  private static void addHit(List<TwoNodeClip.Node> hits, int edge,
      TwoNodeClip.Edge e, Coordinate p, double scale) {
    TwoNodeClip.addUnique(hits, new TwoNodeClip.Node(edge, e.param(p), p),
        scale);
  }

  private static List<List<TwoNodeClip.Node>> emptyHits(int n) {
    List<List<TwoNodeClip.Node>> out = new ArrayList<List<TwoNodeClip.Node>>(n);
    for (int i = 0; i < n; i++) {
      out.add(new ArrayList<TwoNodeClip.Node>());
    }
    return out;
  }

  private static List<Coordinate> allPoints(List<List<TwoNodeClip.Node>> byEdge,
      double scale) {
    List<TwoNodeClip.Node> uniq = new ArrayList<TwoNodeClip.Node>();
    for (int i = 0; i < byEdge.size(); i++) {
      List<TwoNodeClip.Node> hits = byEdge.get(i);
      for (int k = 0; k < hits.size(); k++) {
        TwoNodeClip.addUnique(uniq, hits.get(k), scale);
      }
    }
    List<Coordinate> out = new ArrayList<Coordinate>(uniq.size());
    for (int i = 0; i < uniq.size(); i++) {
      out.add(uniq.get(i).pt);
    }
    return out;
  }

  private static List<LineString> concat(List<LineString> a, List<LineString> b) {
    List<LineString> out = new ArrayList<LineString>(a.size() + b.size());
    out.addAll(a);
    out.addAll(b);
    return out;
  }
}
