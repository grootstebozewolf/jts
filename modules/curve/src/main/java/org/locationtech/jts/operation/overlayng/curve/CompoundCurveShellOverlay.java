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
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;

/**
 * Dispatch for a hole-free {@link CurvePolygon} whose shell is a
 * mixed {@link CompoundCurve} (LineString + CircularString: a
 * half-disc or stadium). Package-private -- not a new public API,
 * and not a noder.
 * <p>
 * Flatten / classify the pair, then one kit:
 * {@link SameOuterHoleOverlay}, {@link DifferentOuterHoleOverlay},
 * {@link HalfDiscOverlay} (complementary / sectors / collinear),
 * {@link TwoShellClip} (0 / 1 / 2 / even-n / odd-n with a tangent
 * as a degenerate NSpan), {@link BiteVsHole} (straddling hole,
 * or a hole whose ring overlaps the other shell: new edge ⊂
 * other.shell is a bite, not a punch),
 * {@link TwoHoleOverlay} (two holes that cross on the same outer),
 * or a two-node walk vs a disc or plain polygon via
 * {@link TwoNodeClip}. A 0-node mixed shell vs a CircularString
 * disc is a dispatch gap, not missing math: D4
 * {@code nestedAnnulus} and {@link TwoShellClip} already call
 * {@link HalfDiscOverlay#containedShell}. This cell
 * is only that type pair ({@code CC-NEST-ANNULUS}). A 1-node
 * tangent stays {@code null}. A clothoid member is
 * {@link ClothoidOverlay} (0-node identity / disjoint / nest) or
 * a named Fresnel miss -- never a chord flatten. A miss is
 * {@code null}.
 */
final class CompoundCurveShellOverlay {

  private CompoundCurveShellOverlay() { }

  /**
   * Exact overlay, or {@code null} if this class cannot answer. The
   * cheap shape check runs first; a miss does not densify and does
   * not node.
   */
  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    if (ClothoidOverlay.hasClothoid(a) || ClothoidOverlay.hasClothoid(b)) {
      return ClothoidOverlay.overlay(a, b, opCode);
    }
    Geometry holeCell = SameOuterHoleOverlay.overlay(a, b, opCode);
    if (holeCell != null) {
      return holeCell;
    }
    Geometry differentHole = DifferentOuterHoleOverlay.overlay(a, b, opCode);
    if (differentHole != null) {
      return differentHole;
    }
    Geometry bite = BiteVsHole.overlay(a, b, opCode);
    if (bite != null) {
      return bite;
    }
    Geometry twoHole = TwoHoleOverlay.overlay(a, b, opCode);
    if (twoHole != null) {
      return twoHole;
    }
    CurvePolygon shellA = compoundCurveShell(a);
    CurvePolygon shellB = compoundCurveShell(b);
    if (shellA != null && shellB != null) {
      Geometry halves = HalfDiscOverlay.overlay(shellA, shellB, opCode, a);
      if (halves != null) {
        return halves;
      }
      return TwoShellClip.overlay(shellA, shellB, opCode, a);
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
      Geometry nest = mixedNestPunch(shell, other, disc, shellFirst, opCode, a);
      if (nest != null) {
        return nest;
      }
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
      if (m instanceof ClothoidSegment) {
        return null;
      }
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
   * Dispatch gap: mixed CompoundCurve vs CircularString disc.
   * Not missing math -- {@link HalfDiscOverlay#containedShell} is
   * the product. Not D4 (the stadium is not a disc). Not
   * {@link TwoShellClip}'s sample walk: that path's
   * {@code shellSample} collapses when both envelopes are
   * centered at the origin. Certificate is disc-aware: 0 nodes,
   * {@code centreRadius} already in hand, and the stadium
   * strictly inside (envelope vs {@code r − eps}, or control
   * points plus cap extrema). A 1-node tangent is not this cell.
   */
  private static Geometry mixedNestPunch(CurvePolygon shell, Geometry other,
      double[] disc, boolean shellFirst, int opCode, Geometry factorySrc) {
    List<TwoNodeClip.Edge> edges = TwoNodeClip.flatten(shell);
    if (edges == null) return null;
    List<TwoNodeClip.Node> nodes = TwoNodeClip.nodesVsDisc(edges, disc[0],
        disc[1], disc[2]);
    if (nodes == null || !nodes.isEmpty()) return null;
    if (!shellInsideDisc(shell, edges, disc[0], disc[1], disc[2])) {
      return null;
    }
    CurvePolygon discPoly = holeFreeCurvePolygon(other);
    if (discPoly == null) return null;
    return HalfDiscOverlay.containedShell(shell, discPoly, shellFirst, opCode,
        factorySrc, TwoNodeClip.curveFactory(factorySrc));
  }

  /**
   * Disc-aware inside test. Not a sample of both shells: those
   * land on (0,0) for this fixture. Envelope vs {@code r − eps}
   * is the cheap radii analog; control points plus each cap's
   * outer pole cover a stadium whose AABB corners sit outside
   * the circle.
   */
  private static boolean shellInsideDisc(CurvePolygon shell,
      List<TwoNodeClip.Edge> edges, double cx, double cy, double r) {
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * r, 1.0e-12);
    double lim = r - eps;
    if (envelopeInsideDisc(shell, cx, cy, lim)) {
      return true;
    }
    return controlsAndCapExtremaInside(edges, cx, cy, lim);
  }

  private static boolean envelopeInsideDisc(CurvePolygon shell, double cx,
      double cy, double lim) {
    Envelope env = shell.getEnvelopeInternal();
    double dx = Math.max(Math.abs(env.getMinX() - cx),
        Math.abs(env.getMaxX() - cx));
    double dy = Math.max(Math.abs(env.getMinY() - cy),
        Math.abs(env.getMaxY() - cy));
    return Math.hypot(dx, dy) <= lim;
  }

  private static boolean controlsAndCapExtremaInside(
      List<TwoNodeClip.Edge> edges, double cx, double cy, double lim) {
    boolean inside = true;
    for (int i = 0; i < edges.size() && inside; i++) {
      TwoNodeClip.Edge e = edges.get(i);
      if (!pointInsideDisc(e.a, cx, cy, lim)
          || !pointInsideDisc(e.b, cx, cy, lim)) {
        inside = false;
      }
      else if (e.isArc) {
        if (e.mid != null && !pointInsideDisc(e.mid, cx, cy, lim)) {
          inside = false;
        }
        else if (!capExtremumInside(e, cx, cy, lim)) {
          inside = false;
        }
      }
    }
    return inside;
  }

  /**
   * Outer pole of the cap: the supporting-circle point in the
   * direction from the disc centre through the arc centre. On
   * the sweep it is the cap extremum; off the sweep it is not
   * a boundary point.
   */
  private static boolean capExtremumInside(TwoNodeClip.Edge e, double cx,
      double cy, double lim) {
    double vx = e.circle[0] - cx;
    double vy = e.circle[1] - cy;
    double n = Math.hypot(vx, vy);
    if (n == 0.0) {
      return e.circle[2] <= lim;
    }
    Coordinate pole = new Coordinate(
        e.circle[0] + e.circle[2] * vx / n,
        e.circle[1] + e.circle[2] * vy / n);
    if (!TwoNodeClip.isOnSweep(pole, e.circle, e.a, e.mid, e.b)) {
      return true;
    }
    return pointInsideDisc(pole, cx, cy, lim);
  }

  private static boolean pointInsideDisc(Coordinate p, double cx, double cy,
      double lim) {
    return Math.hypot(p.x - cx, p.y - cy) <= lim;
  }

  private static CurvePolygon holeFreeCurvePolygon(Geometry g) {
    if (g instanceof MultiSurface) {
      if (g.getNumGeometries() != 1) return null;
      g = g.getGeometryN(0);
    }
    if (!(g instanceof CurvePolygon)) return null;
    CurvePolygon cp = (CurvePolygon) g;
    if (cp.isEmpty() || cp.getNumInteriorRing() > 0) return null;
    return cp;
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
