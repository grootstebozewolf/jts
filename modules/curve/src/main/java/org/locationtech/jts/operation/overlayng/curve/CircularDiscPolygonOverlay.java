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

import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CurvePolygon;

/**
 * Closed-form overlay of one circular disc and one plain Polygon.
 * Package-private -- not a new public API, and not a noder.
 * <p>
 * Lives next to {@link OverlayNGCurve} so the ratchet can call it without
 * a public bridge (the same reason {@link CircularDiscOverlay} is here
 * rather than in {@code geom.curve}). Two-disc pairs stay on
 * {@link CircularDiscOverlay}; this class does not overload that path.
 * <p>
 * Two proper line–circle nodes become a {@link CurvePolygon} whose shell
 * is the surviving circular arc plus the polygon walk between the nodes
 * (CAP the clip, CUP the blob, SUB a bite or a cap, XOR both), assembled
 * by {@link TwoNodeClip}. An even run of 4+ nodes that alternate
 * in/out around the circle is {@link NSpanClip} (CAP / CUP one ring,
 * SUB / XOR the paired caps and ears) -- not a general noder.
 * Anything else -- not this shape pair, holes, 0 / 1 / odd nodes, a
 * non-alternating cut -- returns {@code null} so the caller can take
 * the chord baseline without paying this path first. A 0-node
 * covering square minus a disc ({@code R1.6-honesty}) is that miss:
 * not a disc-in-square punch, public overlay stays the chordsaw.
 */
final class CircularDiscPolygonOverlay {

  private CircularDiscPolygonOverlay() { }

  /**
   * Exact overlay of a circular disc and a plain polygon, or {@code null}
   * if this class cannot answer. The cheap shape check runs first; a miss
   * does not intersect and does not node.
   */
  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    double[] disc = CircularDiscOverlay.centreRadius(a);
    Geometry poly = b;
    boolean discFirst = true;
    if (disc == null) {
      disc = CircularDiscOverlay.centreRadius(b);
      poly = a;
      discFirst = false;
    }
    if (disc == null || !TwoNodeClip.isPlainPolygon(poly)) return null;

    Clip clip = clip(disc[0], disc[1], disc[2], (Polygon) poly);
    if (clip == null) {
      return nNodeOverlay(disc[0], disc[1], disc[2], (Polygon) poly,
          discFirst, opCode, a);
    }

    GeometryFactory f = TwoNodeClip.curveFactory(a);
    List<LineString> discIn = TwoNodeClip.listOf(
        TwoNodeClip.arc(clip.p, clip.midIn, clip.q, f));
    List<LineString> discOut = TwoNodeClip.listOf(
        TwoNodeClip.arc(clip.p, clip.midOut, clip.q, f));
    List<LineString> polyIn = TwoNodeClip.listOf(
        TwoNodeClip.asLine(clip.pathIn, f));
    List<LineString> polyOut = TwoNodeClip.listOf(
        TwoNodeClip.asLine(clip.pathOut, f));
    return TwoNodeClip.overlay(opCode, discFirst, discIn, discOut, polyIn,
        polyOut, clip.p, clip.q, f, clip.r);
  }

  private static Clip clip(double cx, double cy, double r, Polygon poly) {
    Coordinate[] ring = poly.getExteriorRing().getCoordinates();
    if (ring.length < 4) return null;
    int n = ring.length - 1;
    List<TwoNodeClip.Node> nodes = new ArrayList<TwoNodeClip.Node>();
    for (int i = 0; i < n; i++) {
      Coordinate[] hits = TwoNodeClip.intersectSegmentCircle(
          cx, cy, r, ring[i], ring[i + 1]);
      for (int k = 0; k < hits.length; k++) {
        TwoNodeClip.addUnique(nodes, new TwoNodeClip.Node(i,
            TwoNodeClip.parameter(ring[i], ring[i + 1], hits[k]), hits[k]), r);
      }
    }
    if (!TwoNodeClip.properPair(nodes, r)) return null;

    TwoNodeClip.Node p = nodes.get(0);
    TwoNodeClip.Node q = nodes.get(1);
    List<Coordinate> pq = TwoNodeClip.walkRing(ring, p, q, r);
    List<Coordinate> qp = TwoNodeClip.walkRing(ring, q, p, r);
    if (pq == null || qp == null) return null;
    int pqSide = TwoNodeClip.sideOfDiscPath(pq, cx, cy, r);
    int qpSide = TwoNodeClip.sideOfDiscPath(qp, cx, cy, r);
    if (pqSide == TwoNodeClip.MIXED || qpSide == TwoNodeClip.MIXED
        || pqSide == qpSide) {
      return null;
    }
    List<Coordinate> pathIn = pqSide == TwoNodeClip.IN ? pq : qp;
    List<Coordinate> pathOut = pqSide == TwoNodeClip.IN ? qp : pq;

    Coordinate midIn = TwoNodeClip.sweepMid(p.pt, q.pt, cx, cy, r, true,
        insidePoly(poly));
    Coordinate midOut = TwoNodeClip.sweepMid(p.pt, q.pt, cx, cy, r, false,
        insidePoly(poly));
    if (midIn == null || midOut == null) return null;
    if (midIn.distance(p.pt) < TwoNodeClip.PROPER_CROSS_FRAC * r
        || midIn.distance(q.pt) < TwoNodeClip.PROPER_CROSS_FRAC * r
        || midOut.distance(p.pt) < TwoNodeClip.PROPER_CROSS_FRAC * r
        || midOut.distance(q.pt) < TwoNodeClip.PROPER_CROSS_FRAC * r) {
      return null;
    }
    return new Clip(p.pt, q.pt, midIn, midOut, pathIn, pathOut, r);
  }

  /**
   * Even n≥4 line–circle nodes that alternate in/out. Assemble is
   * {@link NSpanClip}.
   */
  private static Geometry nNodeOverlay(double cx, double cy, double r,
      Polygon poly, boolean discFirst, int opCode, Geometry factorySrc) {
    Coordinate[] ring = poly.getExteriorRing().getCoordinates();
    if (ring.length < 4) return null;
    int nSeg = ring.length - 1;
    List<TwoNodeClip.Node> nodes = new ArrayList<TwoNodeClip.Node>();
    for (int i = 0; i < nSeg; i++) {
      Coordinate[] hits = TwoNodeClip.intersectSegmentCircle(
          cx, cy, r, ring[i], ring[i + 1]);
      for (int k = 0; k < hits.length; k++) {
        TwoNodeClip.addUnique(nodes, new TwoNodeClip.Node(i,
            TwoNodeClip.parameter(ring[i], ring[i + 1], hits[k]), hits[k]), r);
      }
    }
    int n = nodes.size();
    if (n < 4 || (n & 1) != 0) return null;

    List<Span> arcs = circleSpans(nodes, cx, cy, r, poly);
    List<Span> walks = ringSpans(nodes, ring, cx, cy, r);
    if (arcs == null || walks == null) return null;
    if (!alternates(arcs)) return null;

    GeometryFactory f = TwoNodeClip.curveFactory(factorySrc);
    return NSpanClip.overlayLines(opCode, discFirst,
        pieces(arcs, true, f), pieces(arcs, false, f),
        pieces(walks, true, f), pieces(walks, false, f),
        f, r);
  }

  private static List<Span> circleSpans(List<TwoNodeClip.Node> nodes,
      double cx, double cy, double r, Polygon poly) {
    List<TwoNodeClip.Node> ord = new ArrayList<TwoNodeClip.Node>(nodes);
    Collections.sort(ord, angleOrder(cx, cy));
    TwoNodeClip.Side at = insidePoly(poly);
    List<Span> out = new ArrayList<Span>();
    for (int i = 0; i < ord.size(); i++) {
      TwoNodeClip.Node a = ord.get(i);
      TwoNodeClip.Node b = ord.get((i + 1) % ord.size());
      double aP = Math.atan2(a.pt.y - cy, a.pt.x - cx);
      double aQ = Math.atan2(b.pt.y - cy, b.pt.x - cx);
      Coordinate mid = TwoNodeClip.midOnCircle(cx, cy, r, aP,
          TwoNodeClip.normPos(aQ - aP));
      if (mid == null) return null;
      int side = at.inside(mid) ? TwoNodeClip.IN : TwoNodeClip.OUT;
      out.add(new Span(a.pt, b.pt, mid, true, side));
    }
    return out;
  }

  private static List<Span> ringSpans(List<TwoNodeClip.Node> nodes,
      Coordinate[] ring, double cx, double cy, double r) {
    List<TwoNodeClip.Node> ord = new ArrayList<TwoNodeClip.Node>(nodes);
    Collections.sort(ord, NSpanClip.RING_T);
    List<Span> out = new ArrayList<Span>();
    for (int i = 0; i < ord.size(); i++) {
      TwoNodeClip.Node a = ord.get(i);
      TwoNodeClip.Node b = ord.get((i + 1) % ord.size());
      List<Coordinate> path = TwoNodeClip.walkRing(ring, a, b, r);
      if (path == null) return null;
      int side = TwoNodeClip.sideOfDiscPath(path, cx, cy, r);
      if (side == TwoNodeClip.MIXED) return null;
      out.add(new Span(a.pt, b.pt, null, false, side, path));
    }
    return out;
  }

  private static boolean alternates(List<Span> spans) {
    boolean[] in = new boolean[spans.size()];
    for (int i = 0; i < spans.size(); i++) {
      in[i] = spans.get(i).in;
    }
    return NSpanClip.alternates(in);
  }

  private static List<LineString> pieces(List<Span> spans, boolean wantIn,
      GeometryFactory f) {
    List<LineString> out = new ArrayList<LineString>();
    for (int i = 0; i < spans.size(); i++) {
      Span s = spans.get(i);
      if (s.in == wantIn) {
        if (s.arc) {
          out.add(TwoNodeClip.arc(s.a, s.mid, s.b, f));
        }
        else {
          out.add(s.asLine(f));
        }
      }
    }
    return out;
  }

  private static Comparator<TwoNodeClip.Node> angleOrder(final double cx,
      final double cy) {
    return new Comparator<TwoNodeClip.Node>() {
      public int compare(TwoNodeClip.Node a, TwoNodeClip.Node b) {
        return Double.compare(
            Math.atan2(a.pt.y - cy, a.pt.x - cx),
            Math.atan2(b.pt.y - cy, b.pt.x - cx));
      }
    };
  }

  private static final class Span {
    final Coordinate a;
    final Coordinate b;
    final Coordinate mid;
    final boolean arc;
    final boolean in;
    final List<Coordinate> path;

    Span(Coordinate a, Coordinate b, Coordinate mid, boolean arc, int side) {
      this(a, b, mid, arc, side, null);
    }

    Span(Coordinate a, Coordinate b, Coordinate mid, boolean arc, int side,
        List<Coordinate> path) {
      this.a = a;
      this.b = b;
      this.mid = mid;
      this.arc = arc;
      this.in = side == TwoNodeClip.IN;
      this.path = path;
    }

    LineString asLine(GeometryFactory f) {
      if (path != null && path.size() >= 2) {
        return TwoNodeClip.asLine(path, f);
      }
      return f.createLineString(new Coordinate[] {
          new Coordinate(a), new Coordinate(b)
      });
    }
  }

  private static TwoNodeClip.Side insidePoly(final Polygon poly) {
    return new TwoNodeClip.Side() {
      public boolean inside(Coordinate c) {
        return SimplePointInAreaLocator.locate(c, poly) == Location.INTERIOR;
      }
    };
  }

  private static final class Clip {
    final Coordinate p, q, midIn, midOut;
    final List<Coordinate> pathIn, pathOut;
    final double r;

    Clip(Coordinate p, Coordinate q, Coordinate midIn, Coordinate midOut,
        List<Coordinate> pathIn, List<Coordinate> pathOut, double r) {
      this.p = p;
      this.q = q;
      this.midIn = midIn;
      this.midOut = midOut;
      this.pathIn = pathIn;
      this.pathOut = pathOut;
      this.r = r;
    }
  }
}
