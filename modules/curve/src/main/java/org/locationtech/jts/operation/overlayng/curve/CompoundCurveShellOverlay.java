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
 * the disc, SUB the first half. Any other two CompoundCurve shells
 * -- holes, 0 / 1 / 3+ nodes, a line-only shell -- return
 * {@code null} so the caller takes the chord baseline without paying
 * this path first.
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
      return complementaryHalfDiscs(shellA, shellB, opCode, a);
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
   * {@code null}.
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
