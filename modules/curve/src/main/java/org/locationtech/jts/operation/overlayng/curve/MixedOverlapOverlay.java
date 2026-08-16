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

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * H-SHELL-N-MIXED: two hole-free CompoundCurve shells whose only
 * named interaction is a collinear overlap (an interval, not a
 * discrete node set). {@link CurveSegmentNoder#edges} already
 * names that run; {@link CurveSegmentNoder#nodes} stays
 * {@code null}. This kit walks each shell from the run's ends
 * and treats the overlap walk as a shared edge, not a node pair.
 * <p>
 * A rest walk that sits entirely inside the other shell is the
 * inner; the other rest is the outer. CAP / CUP are those
 * shells. SUB / XOR splice the two rests into a bite (the
 * overlap is on the outer, so this is not a punch). A rest that
 * weaves in and out (collinear overlap plus a hidden crossing)
 * stays {@code null} -- that pair is not this cell. Nested or
 * crossing half-discs stay on {@link HalfDiscOverlay}. Pinch /
 * TOUCH-ext / R-LL are not this kit. Package-private -- not a
 * noder, not a public API, not snap-rounding.
 */
final class MixedOverlapOverlay {

  private MixedOverlapOverlay() { }

  static Geometry overlay(CurvePolygon a, CurvePolygon b, int opCode,
      Geometry first) {
    if (a == null || b == null) return null;
    if (CurveSegmentNoder.nodes(a, b) != null) return null;
    List<CurveSegmentString> edges = CurveSegmentNoder.edges(a, b);
    CurveSegmentString run = singleChord(edges);
    if (run == null) return null;

    List<TwoNodeClip.Edge> edgesA = TwoNodeClip.flatten(a);
    List<TwoNodeClip.Edge> edgesB = TwoNodeClip.flatten(b);
    if (edgesA == null || edgesB == null) return null;
    double scale = Math.max(
        Math.max(a.getEnvelopeInternal().getWidth(),
            a.getEnvelopeInternal().getHeight()),
        Math.max(b.getEnvelopeInternal().getWidth(),
            b.getEnvelopeInternal().getHeight()));
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);

    Coordinate u = run.getStart();
    Coordinate v = run.getEnd();
    GeometryFactory f = TwoNodeClip.curveFactory(first);
    List<LineString> aRest = restWalk(edgesA, u, v, run, scale, eps, f);
    List<LineString> bRest = restWalk(edgesB, u, v, run, scale, eps, f);
    if (aRest == null || bRest == null) return null;

    int aSide = sideOfWalk(aRest, b);
    int bSide = sideOfWalk(bRest, a);
    if (aSide == TwoNodeClip.MIXED || bSide == TwoNodeClip.MIXED
        || aSide == bSide) {
      return null;
    }
    boolean aOuter = aSide == TwoNodeClip.OUT;
    List<LineString> outerRest = aOuter ? aRest : bRest;
    List<LineString> innerRest = aOuter ? bRest : aRest;
    CurvePolygon outer = aOuter ? a : b;
    CurvePolygon inner = aOuter ? b : a;

    if (opCode == OverlayNG.INTERSECTION) {
      return inner.copy();
    }
    if (opCode == OverlayNG.UNION) {
      return outer.copy();
    }
    Geometry bite = TwoNodeClip.ring(outerRest, innerRest, u, v, f, scale);
    if (bite == null) return null;
    if (opCode == OverlayNG.DIFFERENCE) {
      return aOuter ? bite : f.createEmpty(2);
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      return bite;
    }
    return null;
  }

  /**
   * The one non-degenerate collinear run, or {@code null}. An arc
   * overlap is a same-circle sweep, not this cell. A pinch is not
   * an interval.
   */
  private static CurveSegmentString singleChord(List<CurveSegmentString> edges) {
    if (edges == null || edges.isEmpty()) return null;
    CurveSegmentString run = null;
    boolean many = false;
    for (int i = 0; i < edges.size(); i++) {
      CurveSegmentString e = edges.get(i);
      if (!e.isDegenerate()) {
        if (run != null) {
          many = true;
        }
        else {
          run = e;
        }
      }
    }
    if (many || run == null || run.isArc()) return null;
    return run;
  }

  /**
   * The shell walk between the overlap ends that is <em>not</em>
   * the named run. The other walk is the shared edge itself.
   */
  private static List<LineString> restWalk(List<TwoNodeClip.Edge> edges,
      Coordinate u, Coordinate v, CurveSegmentString run, double scale,
      double eps, GeometryFactory f) {
    TwoNodeClip.Node nU = nodeOn(edges, u, scale);
    TwoNodeClip.Node nV = nodeOn(edges, v, scale);
    if (nU == null || nV == null) return null;
    List<LineString> uv = TwoNodeClip.walkEdges(edges, nU, nV, f);
    List<LineString> vu = TwoNodeClip.walkEdges(edges, nV, nU, f);
    if (uv == null || vu == null) return null;
    boolean uvRun = isOverlapWalk(uv, run, eps);
    boolean vuRun = isOverlapWalk(vu, run, eps);
    if (uvRun == vuRun) return null;
    return uvRun ? vu : uv;
  }

  private static boolean isOverlapWalk(List<LineString> walk,
      CurveSegmentString run, double eps) {
    if (walk == null || walk.size() != 1) return false;
    LineString ls = walk.get(0);
    if (ls instanceof CircularString) return false;
    Coordinate[] c = ls.getCoordinates();
    if (c.length < 2) return false;
    if (Math.abs(ls.getLength() - run.length()) > eps) return false;
    return sameEnds(c[0], c[c.length - 1], run.getStart(), run.getEnd(),
        eps);
  }

  /**
   * Every off-boundary sample of the walk must agree. A weave
   * (in and out) is MIXED -- not a containment bite.
   */
  private static int sideOfWalk(List<LineString> walk, CurvePolygon other) {
    if (walk == null || walk.isEmpty()) return TwoNodeClip.MIXED;
    int side = TwoNodeClip.MIXED;
    boolean saw = false;
    boolean mixed = false;
    for (int i = 0; i < walk.size() && !mixed; i++) {
      LineString m = walk.get(i);
      if (m instanceof CircularString && m.getNumPoints() >= 3) {
        Coordinate[] pts = m.getCoordinates();
        for (int k = 1; k + 1 < pts.length && !mixed; k += 2) {
          mixed = !acceptSample(pts[k], other, side, saw);
          if (!mixed) {
            int loc = TwoNodeClip.locateInShell(pts[k], other);
            if (loc != TwoNodeClip.MIXED) {
              side = loc;
              saw = true;
            }
          }
        }
      }
      else {
        Coordinate[] c = m.getCoordinates();
        if (c.length >= 2) {
          Coordinate mid = new Coordinate(0.5 * (c[0].x + c[c.length - 1].x),
              0.5 * (c[0].y + c[c.length - 1].y));
          mixed = !acceptSample(mid, other, side, saw);
          if (!mixed) {
            int loc = TwoNodeClip.locateInShell(mid, other);
            if (loc != TwoNodeClip.MIXED) {
              side = loc;
              saw = true;
            }
          }
        }
      }
    }
    if (mixed || !saw) return TwoNodeClip.MIXED;
    return side;
  }

  /**
   * {@code false} when this off-boundary sample disagrees with the
   * side already seen.
   */
  private static boolean acceptSample(Coordinate p, CurvePolygon other,
      int side, boolean saw) {
    int loc = TwoNodeClip.locateInShell(p, other);
    if (loc == TwoNodeClip.MIXED) return true;
    return !saw || loc == side;
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

  private static boolean sameEnds(Coordinate a0, Coordinate a1,
      Coordinate b0, Coordinate b1, double eps) {
    return a0.distance(b0) <= eps && a1.distance(b1) <= eps
        || a0.distance(b1) <= eps && a1.distance(b0) <= eps;
  }
}
