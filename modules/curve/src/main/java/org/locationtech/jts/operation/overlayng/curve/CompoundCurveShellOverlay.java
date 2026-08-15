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
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * Closed-form overlay of a hole-free {@link CurvePolygon} whose shell is
 * a mixed {@link CompoundCurve} (LineString + CircularString: a half-disc
 * or stadium) against a circular disc or a plain Polygon.
 * Package-private -- not a new public API, and not a noder.
 * <p>
 * Dispatch only: flatten members to typed edges, pick a disc or a
 * plain-polygon partner, then one two-node walk via {@link TwoNodeClip}.
 * A LineString member stays a segment; a three-point LineString is not
 * an arc. A CompoundCurve that is itself a disc is left to
 * {@link CircularDiscOverlay} / {@link CircularDiscPolygonOverlay}.
 * Complementary half-discs of the same circle (shared diameter,
 * opposite caps) are answered in closed form: CAP empty, CUP / XOR
 * the disc, SUB the first half. Same-circle half-discs whose
 * diameters are perpendicular assemble as sectors (quarter / three-
 * quarter). Any other two hole-free CompoundCurve shells with
 * exactly two proper nodes walk the surviving pieces. Holes,
 * 0 / 1 / 3+ nodes, collinear overlap, or a pair that would need a
 * general circular noder return {@code null} so the caller takes the
 * chord baseline without paying this path first.
 */
final class CompoundCurveShellOverlay {

  private CompoundCurveShellOverlay() { }

  /**
   * Exact overlay, or {@code null} if this class cannot answer. The
   * cheap shape check runs first; a miss does not densify and does
   * not node.
   */
  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    CurvePolygon shellA = compoundCurveShell(a);
    CurvePolygon shellB = compoundCurveShell(b);
    if (shellA != null && shellB != null) {
      Geometry halves = complementaryHalfDiscs(shellA, shellB, opCode, a);
      if (halves != null) {
        return halves;
      }
      Geometry sectors = overlappingSameCircleHalfDiscs(shellA, shellB,
          opCode, a);
      if (sectors != null) {
        return sectors;
      }
      return twoShellClip(shellA, shellB, opCode, a);
    }

    CurvePolygon shell = shellA;
    Geometry other = b;
    boolean shellFirst = true;
    if (shell == null) {
      shell = shellB;
      other = a;
      shellFirst = false;
    }
    if (shell == null) return null;
    // A disc-shaped CompoundCurve is R1.5 / R1.6, not this cell.
    if (CircularDiscOverlay.centreRadius(shell) != null) return null;

    double[] disc = CircularDiscOverlay.centreRadius(other);
    if (disc != null) {
      return clip(shell, new DiscOther(disc), shellFirst, opCode, a);
    }
    if (TwoNodeClip.isPlainPolygon(other)) {
      return clip(shell, new PolygonOther((Polygon) other, shell), shellFirst,
          opCode, a);
    }
    return null;
  }

  /**
   * Hole-free CurvePolygon whose shell is a CompoundCurve with at
   * least one CircularString and at least one plain LineString.
   */
  private static CurvePolygon compoundCurveShell(Geometry g) {
    if (g instanceof MultiSurface) {
      if (g.getNumGeometries() != 1) return null;
      g = g.getGeometryN(0);
    }
    if (!(g instanceof CurvePolygon)) return null;
    CurvePolygon cp = (CurvePolygon) g;
    if (cp.isEmpty() || cp.getNumInteriorRing() > 0) return null;
    LineString ring = cp.getExteriorCurve();
    if (!(ring instanceof CompoundCurve) || !ring.isClosed()) return null;
    CompoundCurve cc = (CompoundCurve) ring;
    boolean hasArc = false;
    boolean hasLine = false;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      if (m instanceof CircularString) {
        hasArc = true;
      }
      else if (m instanceof LineString) {
        hasLine = true;
      }
      else {
        return null;
      }
    }
    if (!hasArc || !hasLine) return null;
    return cp;
  }

  /**
   * Two half-discs of the same circle that share a diameter and take
   * opposite caps. Not a two-shell noder: any other pair is
   * {@code null} so the caller can try sectors or a two-node walk.
   */
  private static Geometry complementaryHalfDiscs(CurvePolygon a,
      CurvePolygon b, int opCode, Geometry first) {
    HalfDisc ha = halfDisc(a);
    HalfDisc hb = halfDisc(b);
    if (ha == null || hb == null) return null;
    double scale = Math.max(ha.r, 1.0);
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    if (ha.centre.distance(hb.centre) > eps || Math.abs(ha.r - hb.r) > eps) {
      return null;
    }
    if (!sameEnds(ha.d0, ha.d1, hb.d0, hb.d1, eps)) return null;
    if (ha.mid.distance(hb.mid) <= ha.r) return null;
    GeometryFactory f = TwoNodeClip.curveFactory(first);
    if (opCode == OverlayNG.INTERSECTION) {
      return f.createEmpty(2);
    }
    if (opCode == OverlayNG.UNION || opCode == OverlayNG.SYMDIFFERENCE) {
      return CircularDiscOverlay.discPolygon(ha.centre.x, ha.centre.y, ha.r, f);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return first;
    }
    return null;
  }

  /**
   * Two half-discs of the same circle whose diameters are
   * perpendicular. Cheaper than a two-node walk: the diameters meet
   * at the centre, so a generic clip would see three nodes.
   */
  private static Geometry overlappingSameCircleHalfDiscs(CurvePolygon a,
      CurvePolygon b, int opCode, Geometry first) {
    HalfDisc ha = halfDisc(a);
    HalfDisc hb = halfDisc(b);
    if (ha == null || hb == null) return null;
    double scale = Math.max(ha.r, 1.0);
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    if (ha.centre.distance(hb.centre) > eps || Math.abs(ha.r - hb.r) > eps) {
      return null;
    }
    if (!isDiameter(ha, eps) || !isDiameter(hb, eps)) return null;
    double adx = ha.d1.x - ha.d0.x;
    double ady = ha.d1.y - ha.d0.y;
    double bdx = hb.d1.x - hb.d0.x;
    double bdy = hb.d1.y - hb.d0.y;
    if (Math.abs(adx * bdx + ady * bdy) > eps * 4.0 * ha.r) return null;

    Coordinate capA = strictlyInsideEnd(ha, hb, eps);
    Coordinate fromA = strictlyOutsideEnd(ha, hb, eps);
    Coordinate capB = strictlyInsideEnd(hb, ha, eps);
    Coordinate fromB = strictlyOutsideEnd(hb, ha, eps);
    if (capA == null || fromA == null || capB == null || fromB == null) {
      return null;
    }
    Coordinate midCap = onCircle(ha.centre, ha.r, capA, capB);
    Coordinate midAB = onCircle(ha.centre, ha.r, fromA, capB);
    Coordinate midBA = onCircle(ha.centre, ha.r, fromB, capA);
    if (midCap == null || midAB == null || midBA == null) return null;

    GeometryFactory f = TwoNodeClip.curveFactory(first);
    if (opCode == OverlayNG.INTERSECTION) {
      return sector(capA, midCap, capB, ha.centre, f, scale);
    }
    if (opCode == OverlayNG.UNION) {
      return sector(fromA, midCap, fromB, ha.centre, f, scale);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return sector(fromA, midAB, capB, ha.centre, f, scale);
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      Polygon ab = sector(fromA, midAB, capB, ha.centre, f, scale);
      Polygon ba = sector(fromB, midBA, capA, ha.centre, f, scale);
      if (ab == null || ba == null) return null;
      return new MultiSurface(new Polygon[] { ab, ba }, f);
    }
    return null;
  }

  /**
   * Exactly two proper nodes between two hole-free CompoundCurve
   * shells. Segment–segment, segment–circle, and circle–circle hits
   * only; collinear overlap or 0 / 1 / 3+ nodes are a miss.
   */
  private static Geometry twoShellClip(CurvePolygon a, CurvePolygon b,
      int opCode, Geometry first) {
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

  private static boolean isDiameter(HalfDisc h, double eps) {
    if (Math.abs(h.d0.distance(h.d1) - 2.0 * h.r) > eps) return false;
    double t = TwoNodeClip.parameter(h.d0, h.d1, h.centre);
    Coordinate q = new Coordinate(
        h.d0.x + t * (h.d1.x - h.d0.x),
        h.d0.y + t * (h.d1.y - h.d0.y));
    return q.distance(h.centre) <= eps
        && h.d0.distance(h.centre) > eps
        && h.d1.distance(h.centre) > eps;
  }

  private static double capDot(Coordinate p, HalfDisc h) {
    return (p.x - h.centre.x) * (h.mid.x - h.centre.x)
        + (p.y - h.centre.y) * (h.mid.y - h.centre.y);
  }

  private static Coordinate strictlyInsideEnd(HalfDisc self, HalfDisc other,
      double eps) {
    double thresh = eps * other.r * other.r;
    boolean in0 = capDot(self.d0, other) > thresh;
    boolean in1 = capDot(self.d1, other) > thresh;
    if (in0 == in1) return null;
    return in0 ? self.d0 : self.d1;
  }

  private static Coordinate strictlyOutsideEnd(HalfDisc self, HalfDisc other,
      double eps) {
    Coordinate inside = strictlyInsideEnd(self, other, eps);
    if (inside == null) return null;
    return inside.distance(self.d0) <= eps ? self.d1 : self.d0;
  }

  private static Coordinate onCircle(Coordinate c, double r, Coordinate p,
      Coordinate q) {
    double ux = (p.x - c.x) + (q.x - c.x);
    double uy = (p.y - c.y) + (q.y - c.y);
    double n = Math.hypot(ux, uy);
    if (n == 0.0) return null;
    return new Coordinate(c.x + r * ux / n, c.y + r * uy / n);
  }

  private static Polygon sector(Coordinate from, Coordinate mid, Coordinate to,
      Coordinate c, GeometryFactory f, double scale) {
    List<LineString> members = new ArrayList<LineString>();
    members.add(f.createLineString(new Coordinate[] {
        new Coordinate(c), new Coordinate(from)
    }));
    members.add(TwoNodeClip.arc(from, mid, to, f));
    members.add(f.createLineString(new Coordinate[] {
        new Coordinate(to), new Coordinate(c)
    }));
    return TwoNodeClip.closeRing(members, f,
        Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12));
  }

  private static HalfDisc halfDisc(CurvePolygon cp) {
    List<TwoNodeClip.Edge> edges = TwoNodeClip.flatten(cp);
    if (edges == null || edges.size() != 2) return null;
    TwoNodeClip.Edge arc = null;
    TwoNodeClip.Edge line = null;
    for (int i = 0; i < edges.size(); i++) {
      TwoNodeClip.Edge e = edges.get(i);
      if (e.isArc) {
        if (arc != null) return null;
        arc = e;
      }
      else {
        if (line != null) return null;
        line = e;
      }
    }
    if (arc == null || line == null) return null;
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * arc.circle[2],
        1.0e-12);
    if (!sameEnds(line.a, line.b, arc.a, arc.b, eps)) return null;
    return new HalfDisc(new Coordinate(arc.circle[0], arc.circle[1]),
        arc.circle[2], arc.a, arc.b, arc.mid);
  }

  private static boolean sameEnds(Coordinate a0, Coordinate a1,
      Coordinate b0, Coordinate b1, double eps) {
    return a0.distance(b0) <= eps && a1.distance(b1) <= eps
        || a0.distance(b1) <= eps && a1.distance(b0) <= eps;
  }

  private static final class HalfDisc {
    final Coordinate centre;
    final double r;
    final Coordinate d0;
    final Coordinate d1;
    final Coordinate mid;
    HalfDisc(Coordinate centre, double r, Coordinate d0, Coordinate d1,
        Coordinate mid) {
      this.centre = centre;
      this.r = r;
      this.d0 = d0;
      this.d1 = d1;
      this.mid = mid;
    }
  }

  /**
   * One two-node walk. The partner supplies nodes, scale, side-of,
   * and the other-side pieces; the shell walk is always the typed
   * CompoundCurve members.
   */
  private static Geometry clip(CurvePolygon shell, Other other,
      boolean shellFirst, int opCode, Geometry factorySrc) {
    List<TwoNodeClip.Edge> edges = TwoNodeClip.flatten(shell);
    if (edges == null) return null;
    List<TwoNodeClip.Node> nodes = other.nodes(edges);
    if (!TwoNodeClip.properPair(nodes, other.scale())) return null;

    TwoNodeClip.Node p = nodes.get(0);
    TwoNodeClip.Node q = nodes.get(1);
    GeometryFactory f = TwoNodeClip.curveFactory(factorySrc);
    List<LineString> pq = TwoNodeClip.walkEdges(edges, p, q, f);
    List<LineString> qp = TwoNodeClip.walkEdges(edges, q, p, f);
    if (pq == null || qp == null) return null;
    int pqSide = other.sideOf(pq);
    int qpSide = other.sideOf(qp);
    if (pqSide == TwoNodeClip.MIXED || qpSide == TwoNodeClip.MIXED
        || pqSide == qpSide) {
      return null;
    }
    List<LineString> shellIn = pqSide == TwoNodeClip.IN ? pq : qp;
    List<LineString> shellOut = pqSide == TwoNodeClip.IN ? qp : pq;

    List<LineString> otherIn = other.walk(p.pt, q.pt, shell, true, f);
    List<LineString> otherOut = other.walk(p.pt, q.pt, shell, false, f);
    if (otherIn == null || otherOut == null) return null;

    return TwoNodeClip.overlay(opCode, shellFirst, shellIn, shellOut,
        otherIn, otherOut, p.pt, q.pt, f, other.scale());
  }

  private interface Other {
    List<TwoNodeClip.Node> nodes(List<TwoNodeClip.Edge> edges);
    double scale();
    int sideOf(List<LineString> walk);
    List<LineString> walk(Coordinate p, Coordinate q, CurvePolygon shell,
        boolean wantInside, GeometryFactory f);
  }

  private static final class DiscOther implements Other {
    private final double cx, cy, r;

    DiscOther(double[] disc) {
      this.cx = disc[0];
      this.cy = disc[1];
      this.r = disc[2];
    }

    public List<TwoNodeClip.Node> nodes(List<TwoNodeClip.Edge> edges) {
      return TwoNodeClip.nodesVsDisc(edges, cx, cy, r);
    }

    public double scale() {
      return r;
    }

    public int sideOf(List<LineString> walk) {
      return TwoNodeClip.sideOfDisc(walk, cx, cy, r);
    }

    public List<LineString> walk(Coordinate p, Coordinate q,
        CurvePolygon shell, boolean wantInside, GeometryFactory f) {
      Coordinate mid = TwoNodeClip.sweepMid(p, q, cx, cy, r, wantInside,
          new TwoNodeClip.Side() {
            public boolean inside(Coordinate c) {
              return TwoNodeClip.locateInShell(c, shell) == TwoNodeClip.IN;
            }
          });
      if (mid == null) return null;
      return TwoNodeClip.listOf(TwoNodeClip.arc(p, mid, q, f));
    }
  }

  private static final class PolygonOther implements Other {
    private final Polygon poly;
    private final Coordinate[] ring;
    private final double scale;

    PolygonOther(Polygon poly, CurvePolygon shell) {
      this.poly = poly;
      this.ring = poly.getExteriorRing().getCoordinates();
      this.scale = Math.max(shell.getEnvelopeInternal().getWidth(),
          shell.getEnvelopeInternal().getHeight());
    }

    public List<TwoNodeClip.Node> nodes(List<TwoNodeClip.Edge> edges) {
      if (ring.length < 4) return null;
      return TwoNodeClip.nodesVsPolygon(edges, ring);
    }

    public double scale() {
      return scale;
    }

    public int sideOf(List<LineString> walk) {
      return TwoNodeClip.sideOfPolygon(walk, poly);
    }

    public List<LineString> walk(Coordinate p, Coordinate q,
        CurvePolygon shell, boolean wantInside, GeometryFactory f) {
      List<Coordinate> pq = TwoNodeClip.walkRing(ring, p, q);
      List<Coordinate> qp = TwoNodeClip.walkRing(ring, q, p);
      if (pq == null || qp == null) return null;
      int pqSide = sideOfShell(pq, shell);
      int qpSide = sideOfShell(qp, shell);
      if (pqSide == TwoNodeClip.MIXED || qpSide == TwoNodeClip.MIXED
          || pqSide == qpSide) {
        return null;
      }
      List<Coordinate> chosen =
          (pqSide == TwoNodeClip.IN) == wantInside ? pq : qp;
      return TwoNodeClip.listOf(
          TwoNodeClip.asLine(TwoNodeClip.startingAt(chosen, p), f));
    }

    private static int sideOfShell(List<Coordinate> path, CurvePolygon shell) {
      if (path.size() < 2) return TwoNodeClip.MIXED;
      Coordinate sample;
      if (path.size() >= 3) {
        sample = path.get(path.size() / 2);
      }
      else {
        sample = new Coordinate(0.5 * (path.get(0).x + path.get(1).x),
            0.5 * (path.get(0).y + path.get(1).y));
      }
      return TwoNodeClip.locateInShell(sample, shell);
    }
  }
}
