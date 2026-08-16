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
import java.util.List;

import org.locationtech.jts.algorithm.LineIntersector;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CurvePolygon;

/**
 * Two hole-free CompoundCurve shells: 0 / 1 node is containment or a
 * touch, two proper nodes walk via {@link TwoNodeClip}, an even 4+
 * alternating cut is {@link NSpanClip}. A tangent is a zero-length
 * NSpan, so two crossings plus a touch assemble like even-n.
 * Collinear overlap stays {@code null}. Not a noder.
 */
final class TwoShellClip {

  private TwoShellClip() { }

  static Geometry overlay(CurvePolygon a, CurvePolygon b, int opCode,
      Geometry first) {
    List<TwoNodeClip.Edge> edgesA = TwoNodeClip.flatten(a);
    List<TwoNodeClip.Edge> edgesB = TwoNodeClip.flatten(b);
    if (edgesA == null || edgesB == null) return null;
    double scale = Math.max(
        Math.max(a.getEnvelopeInternal().getWidth(),
            a.getEnvelopeInternal().getHeight()),
        Math.max(b.getEnvelopeInternal().getWidth(),
            b.getEnvelopeInternal().getHeight()));
    List<TwoNodeClip.Node> nodesA = new ArrayList<TwoNodeClip.Node>();
    if (!collectTwoShellHits(edgesA, edgesB, nodesA, scale)) return null;
    if (nodesA.size() <= 1) {
      return fewNodeOverlay(a, b, opCode, first);
    }
    if (nodesA.size() >= 3) {
      return nShellClip(a, b, edgesA, edgesB, nodesA, opCode, first, scale);
    }
    if (!TwoNodeClip.properPair(nodesA, scale)) return null;

    TwoNodeClip.Node pA = nodesA.get(0);
    TwoNodeClip.Node qA = nodesA.get(1);
    TwoNodeClip.Node pB = nodeOn(edgesB, pA.pt, scale);
    TwoNodeClip.Node qB = nodeOn(edgesB, qA.pt, scale);
    if (pB == null || qB == null) return null;

    GeometryFactory f = TwoNodeClip.curveFactory(first);
    List<LineString> aPQ = TwoNodeClip.walkEdges(edgesA, pA, qA, f);
    List<LineString> aQP = TwoNodeClip.walkEdges(edgesA, qA, pA, f);
    List<LineString> bPQ = TwoNodeClip.walkEdges(edgesB, pB, qB, f);
    List<LineString> bQP = TwoNodeClip.walkEdges(edgesB, qB, pB, f);
    if (aPQ == null || aQP == null || bPQ == null || bQP == null) return null;

    int aPQside = sideOfShell(aPQ, b);
    int aQPside = sideOfShell(aQP, b);
    int bPQside = sideOfShell(bPQ, a);
    int bQPside = sideOfShell(bQP, a);
    if (aPQside == TwoNodeClip.MIXED || aQPside == TwoNodeClip.MIXED
        || bPQside == TwoNodeClip.MIXED || bQPside == TwoNodeClip.MIXED
        || aPQside == aQPside || bPQside == bQPside) {
      return null;
    }
    List<LineString> aIn = aPQside == TwoNodeClip.IN ? aPQ : aQP;
    List<LineString> aOut = aPQside == TwoNodeClip.IN ? aQP : aPQ;
    List<LineString> bIn = bPQside == TwoNodeClip.IN ? bPQ : bQP;
    List<LineString> bOut = bPQside == TwoNodeClip.IN ? bQP : bPQ;
    return TwoNodeClip.overlay(opCode, true, aIn, aOut, bIn, bOut,
        pA.pt, qA.pt, f, scale);
  }

  /**
   * 0 or 1 proper node: one shell inside the other, or interiors
   * disjoint (including a single vertex touch). Not a 1-node walk.
   */
  private static Geometry fewNodeOverlay(CurvePolygon a, CurvePolygon b,
      int opCode, Geometry first) {
    Coordinate sa = HalfDiscOverlay.shellSample(a);
    Coordinate sb = HalfDiscOverlay.shellSample(b);
    if (sa == null || sb == null) return null;
    int aInB = TwoNodeClip.locateInShell(sa, b);
    int bInA = TwoNodeClip.locateInShell(sb, a);
    if (aInB == TwoNodeClip.MIXED || bInA == TwoNodeClip.MIXED) {
      return null;
    }
    GeometryFactory f = TwoNodeClip.curveFactory(first);
    if (aInB == TwoNodeClip.IN && bInA == TwoNodeClip.OUT) {
      return HalfDiscOverlay.containedShell(a, b, true, opCode, first, f);
    }
    if (bInA == TwoNodeClip.IN && aInB == TwoNodeClip.OUT) {
      return HalfDiscOverlay.containedShell(b, a, false, opCode, first, f);
    }
    if (aInB == TwoNodeClip.OUT && bInA == TwoNodeClip.OUT) {
      return HalfDiscOverlay.disjointShells(opCode, first, a, b, f);
    }
    return null;
  }

  /**
   * n≥3 nodes classified in/out on both shells. Even-n that
   * already alternates is {@link NSpanClip} as before. Odd-n
   * inserts a zero-length span at each same-label joint (a
   * tangent) so the walk alternates; a leftover that still
   * cannot pair is {@code null}.
   */
  private static Geometry nShellClip(CurvePolygon a, CurvePolygon b,
      List<TwoNodeClip.Edge> edgesA, List<TwoNodeClip.Edge> edgesB,
      List<TwoNodeClip.Node> nodesA, int opCode, Geometry first,
      double scale) {
    List<TwoNodeClip.Node> nodesB = new ArrayList<TwoNodeClip.Node>();
    boolean miss = false;
    for (int i = 0; i < nodesA.size() && !miss; i++) {
      TwoNodeClip.Node nb = nodeOn(edgesB, nodesA.get(i).pt, scale);
      if (nb == null) {
        miss = true;
      }
      else {
        nodesB.add(nb);
      }
    }
    if (miss) return null;

    Collections.sort(nodesA, NSpanClip.RING_T);
    Collections.sort(nodesB, NSpanClip.RING_T);
    GeometryFactory f = TwoNodeClip.curveFactory(first);
    List<ShellSpan> spansA = edgeSpans(edgesA, nodesA, b, f);
    List<ShellSpan> spansB = edgeSpans(edgesB, nodesB, a, f);
    if (spansA == null || spansB == null) return null;
    if ((spansA.size() & 1) == 1) {
      spansA = padTangents(spansA, f);
    }
    if ((spansB.size() & 1) == 1) {
      spansB = padTangents(spansB, f);
    }
    if (!alternates(spansA) || !alternates(spansB)) return null;

    return NSpanClip.overlay(opCode, true,
        spanPieces(spansA, true), spanPieces(spansA, false),
        spanPieces(spansB, true), spanPieces(spansB, false),
        f, scale);
  }

  private static List<ShellSpan> edgeSpans(List<TwoNodeClip.Edge> edges,
      List<TwoNodeClip.Node> ord, CurvePolygon other, GeometryFactory f) {
    List<ShellSpan> out = new ArrayList<ShellSpan>();
    boolean miss = false;
    for (int i = 0; i < ord.size() && !miss; i++) {
      TwoNodeClip.Node from = ord.get(i);
      TwoNodeClip.Node to = ord.get((i + 1) % ord.size());
      List<LineString> walk = TwoNodeClip.walkEdges(edges, from, to, f);
      if (walk == null) {
        miss = true;
      }
      else {
        int side = sideOfShell(walk, other);
        if (side == TwoNodeClip.MIXED) {
          miss = true;
        }
        else {
          out.add(new ShellSpan(walk, side == TwoNodeClip.IN));
        }
      }
    }
    return miss ? null : out;
  }

  /**
   * Same-label adjacent spans meet at a tangent. Insert a
   * zero-length opposite-label span there so the cycle
   * alternates. Even-n that already alternates is not padded.
   */
  private static List<ShellSpan> padTangents(List<ShellSpan> spans,
      GeometryFactory f) {
    List<ShellSpan> out = new ArrayList<ShellSpan>();
    for (int i = 0; i < spans.size(); i++) {
      ShellSpan cur = spans.get(i);
      out.add(cur);
      ShellSpan next = spans.get((i + 1) % spans.size());
      if (cur.in == next.in) {
        out.add(new ShellSpan(TwoNodeClip.listOf(pointEdge(endOf(cur.members),
            f)), !cur.in));
      }
    }
    return out;
  }

  private static LineString pointEdge(Coordinate at, GeometryFactory f) {
    return f.createLineString(new Coordinate[] {
        new Coordinate(at), new Coordinate(at)
    });
  }

  private static Coordinate endOf(List<LineString> parts) {
    LineString last = parts.get(parts.size() - 1);
    return last.getCoordinateN(last.getNumPoints() - 1);
  }

  private static boolean alternates(List<ShellSpan> spans) {
    boolean[] in = new boolean[spans.size()];
    for (int i = 0; i < spans.size(); i++) {
      in[i] = spans.get(i).in;
    }
    return NSpanClip.alternates(in);
  }

  private static List<List<LineString>> spanPieces(List<ShellSpan> spans,
      boolean wantIn) {
    List<List<LineString>> out = new ArrayList<List<LineString>>();
    for (int i = 0; i < spans.size(); i++) {
      if (spans.get(i).in == wantIn) {
        out.add(spans.get(i).members);
      }
    }
    return out;
  }

  private static boolean collectTwoShellHits(List<TwoNodeClip.Edge> edgesA,
      List<TwoNodeClip.Edge> edgesB, List<TwoNodeClip.Node> nodesA,
      double scale) {
    LineIntersector li = new RobustLineIntersector();
    boolean miss = false;
    for (int i = 0; i < edgesA.size() && !miss; i++) {
      TwoNodeClip.Edge ea = edgesA.get(i);
      for (int j = 0; j < edgesB.size() && !miss; j++) {
        TwoNodeClip.Edge eb = edgesB.get(j);
        if (!addEdgePairHits(ea, eb, i, nodesA, li, scale)) {
          miss = true;
        }
      }
    }
    return !miss;
  }

  private static boolean addEdgePairHits(TwoNodeClip.Edge ea,
      TwoNodeClip.Edge eb, int iA, List<TwoNodeClip.Node> nodesA,
      LineIntersector li, double scale) {
    if (ea.isArc && eb.isArc) {
      if (sameCircle(ea, eb, scale)) {
        return true;
      }
      Coordinate[] xs = TwoNodeClip.intersectCircles(
          ea.circle[0], ea.circle[1], ea.circle[2],
          eb.circle[0], eb.circle[1], eb.circle[2]);
      for (int k = 0; k < xs.length; k++) {
        if (TwoNodeClip.isOnSweep(xs[k], ea.circle, ea.a, ea.mid, ea.b)
            && TwoNodeClip.isOnSweep(xs[k], eb.circle, eb.a, eb.mid, eb.b)) {
          TwoNodeClip.addUnique(nodesA,
              new TwoNodeClip.Node(iA, ea.param(xs[k]), xs[k]), scale);
        }
      }
      return true;
    }
    if (ea.isArc) {
      addSegCircleHits(eb.a, eb.b, ea, ea, iA, nodesA, scale);
      return true;
    }
    if (eb.isArc) {
      addSegCircleHits(ea.a, ea.b, eb, ea, iA, nodesA, scale);
      return true;
    }
    li.computeIntersection(ea.a, ea.b, eb.a, eb.b);
    if (li.getIntersectionNum() == LineIntersector.COLLINEAR_INTERSECTION) {
      return false;
    }
    if (li.getIntersectionNum() == 1) {
      Coordinate p = li.getIntersection(0);
      TwoNodeClip.addUnique(nodesA,
          new TwoNodeClip.Node(iA, ea.param(p), p), scale);
    }
    return true;
  }

  private static void addSegCircleHits(Coordinate s0, Coordinate s1,
      TwoNodeClip.Edge sweep, TwoNodeClip.Edge onA, int iA,
      List<TwoNodeClip.Node> nodesA, double scale) {
    Coordinate[] xs = TwoNodeClip.intersectSegmentCircle(
        sweep.circle[0], sweep.circle[1], sweep.circle[2], s0, s1);
    for (int k = 0; k < xs.length; k++) {
      if (TwoNodeClip.isOnSweep(xs[k], sweep.circle, sweep.a, sweep.mid,
          sweep.b)) {
        TwoNodeClip.addUnique(nodesA,
            new TwoNodeClip.Node(iA, onA.param(xs[k]), xs[k]), scale);
      }
    }
  }

  private static TwoNodeClip.Node nodeOn(List<TwoNodeClip.Edge> edges,
      Coordinate p, double scale) {
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    TwoNodeClip.Node found = null;
    for (int i = 0; i < edges.size(); i++) {
      TwoNodeClip.Edge e = edges.get(i);
      if (edgeHolds(e, p, eps) && found == null) {
        found = new TwoNodeClip.Node(i, e.param(p), p);
      }
    }
    return found;
  }

  private static boolean edgeHolds(TwoNodeClip.Edge e, Coordinate p,
      double eps) {
    if (e.isArc) {
      double d = Math.hypot(p.x - e.circle[0], p.y - e.circle[1]);
      if (Math.abs(d - e.circle[2]) > eps) {
        return false;
      }
      return TwoNodeClip.isOnSweep(p, e.circle, e.a, e.mid, e.b);
    }
    double t = TwoNodeClip.parameter(e.a, e.b, p);
    Coordinate q = new Coordinate(e.a.x + t * (e.b.x - e.a.x),
        e.a.y + t * (e.b.y - e.a.y));
    return p.distance(q) <= eps;
  }

  private static int sideOfShell(List<LineString> walk, CurvePolygon other) {
    Coordinate sample = TwoNodeClip.sample(walk);
    if (sample == null) return TwoNodeClip.MIXED;
    return TwoNodeClip.locateInShell(sample, other);
  }

  private static boolean sameCircle(TwoNodeClip.Edge a, TwoNodeClip.Edge b,
      double scale) {
    if (!a.isArc || !b.isArc) return false;
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    Coordinate ca = new Coordinate(a.circle[0], a.circle[1]);
    Coordinate cb = new Coordinate(b.circle[0], b.circle[1]);
    return ca.distance(cb) <= eps && Math.abs(a.circle[2] - b.circle[2]) <= eps;
  }

  private static final class ShellSpan {
    final List<LineString> members;
    final boolean in;
    ShellSpan(List<LineString> members, boolean in) {
      this.members = members;
      this.in = in;
    }
  }
}
