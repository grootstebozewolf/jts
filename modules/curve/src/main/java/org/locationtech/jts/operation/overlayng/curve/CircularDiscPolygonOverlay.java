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

import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularArcDensifier;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurveOps;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.operation.overlayng.OverlayNG;

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
 * (CAP the clip, CUP the blob, SUB a bite or a cap, XOR both). Anything
 * else -- not this shape pair, holes, 0 / 1 / 3+ nodes -- returns
 * {@code null} so the caller can take the chord baseline without paying
 * this path first.
 */
final class CircularDiscPolygonOverlay {

  /**
   * Two computed nodes closer than this fraction of the radius are a
   * tangent pair in floating point, not a proper chord.
   */
  private static final double PROPER_CROSS_FRAC = 1.0e-9;
  private static final double TWO_PI = 2.0 * Math.PI;
  private static final int IN = 1;
  private static final int OUT = -1;
  private static final int MIXED = 0;

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
    if (disc == null || !isPlainPolygon(poly)) return null;

    Clip clip = clip(disc[0], disc[1], disc[2], (Polygon) poly);
    if (clip == null) return null;

    GeometryFactory f = curveFactory(a);
    if (opCode == OverlayNG.INTERSECTION) {
      return clip.cap(f);
    }
    if (opCode == OverlayNG.UNION) {
      return clip.cup(f);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return discFirst ? clip.discMinusPoly(f) : clip.polyMinusDisc(f);
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      Polygon ab = clip.discMinusPoly(f);
      Polygon ba = clip.polyMinusDisc(f);
      if (ab == null || ba == null) return null;
      return new MultiSurface(new Polygon[] { ab, ba }, f);
    }
    return null;
  }

  private static boolean isPlainPolygon(Geometry g) {
    if (g == null || g.isEmpty()) return false;
    if (g instanceof CurvePolygon) return false;
    if (!(g instanceof Polygon)) return false;
    if (((Polygon) g).getNumInteriorRing() > 0) return false;
    return CurveOps.tolerance(g) <= 0.0;
  }

  private static Clip clip(double cx, double cy, double r, Polygon poly) {
    Coordinate[] ring = poly.getExteriorRing().getCoordinates();
    if (ring.length < 4) return null;
    int n = ring.length - 1;
    List<Node> nodes = new ArrayList<Node>();
    for (int i = 0; i < n; i++) {
      Coordinate[] hits = intersectSegmentCircle(cx, cy, r, ring[i], ring[i + 1]);
      for (int k = 0; k < hits.length; k++) {
        addUnique(nodes, new Node(i, parameter(ring[i], ring[i + 1], hits[k]),
            hits[k]), r);
      }
    }
    if (nodes.size() != 2) return null;
    if (nodes.get(0).pt.distance(nodes.get(1).pt) < PROPER_CROSS_FRAC * r) {
      return null;
    }

    Node p = nodes.get(0);
    Node q = nodes.get(1);
    List<Coordinate> pq = walk(ring, p, q, r);
    List<Coordinate> qp = walk(ring, q, p, r);
    if (pq == null || qp == null) return null;
    int pqSide = sideOfDisc(pq, cx, cy, r);
    int qpSide = sideOfDisc(qp, cx, cy, r);
    if (pqSide == MIXED || qpSide == MIXED || pqSide == qpSide) return null;
    List<Coordinate> pathIn = pqSide == IN ? pq : qp;
    List<Coordinate> pathOut = pqSide == IN ? qp : pq;

    Coordinate midIn = arcMidInside(p.pt, q.pt, cx, cy, r, poly, true);
    Coordinate midOut = arcMidInside(p.pt, q.pt, cx, cy, r, poly, false);
    if (midIn == null || midOut == null) return null;
    if (midIn.distance(p.pt) < PROPER_CROSS_FRAC * r
        || midIn.distance(q.pt) < PROPER_CROSS_FRAC * r
        || midOut.distance(p.pt) < PROPER_CROSS_FRAC * r
        || midOut.distance(q.pt) < PROPER_CROSS_FRAC * r) {
      return null;
    }
    return new Clip(p.pt, q.pt, midIn, midOut, pathIn, pathOut, r);
  }

  /**
   * Mid-arc control of the sweep from {@code p} to {@code q} whose midpoint
   * is inside ({@code wantInside}) or outside the polygon. Null when both
   * sweeps land on the same side -- that is not a two-node clip this
   * class can answer.
   */
  private static Coordinate arcMidInside(Coordinate p, Coordinate q,
      double cx, double cy, double r, Polygon poly, boolean wantInside) {
    double aP = Math.atan2(p.y - cy, p.x - cx);
    double aQ = Math.atan2(q.y - cy, q.x - cx);
    Coordinate ccw = midOnCircle(cx, cy, r, aP, normPos(aQ - aP));
    Coordinate cw = midOnCircle(cx, cy, r, aP, -normPos(aP - aQ));
    boolean ccwIn = SimplePointInAreaLocator.locate(ccw, poly) == Location.INTERIOR;
    boolean cwIn = SimplePointInAreaLocator.locate(cw, poly) == Location.INTERIOR;
    if (ccwIn == cwIn) return null;
    if (wantInside) return ccwIn ? ccw : cw;
    return ccwIn ? cw : ccw;
  }

  private static Coordinate midOnCircle(double cx, double cy, double r,
      double a0, double signedSweep) {
    if (signedSweep == 0.0) signedSweep = TWO_PI;
    double a = a0 + 0.5 * signedSweep;
    return new Coordinate(cx + r * Math.cos(a), cy + r * Math.sin(a));
  }

  private static double normPos(double angle) {
    angle = angle % TWO_PI;
    if (angle < 0.0) angle += TWO_PI;
    return angle;
  }

  private static void addUnique(List<Node> nodes, Node n, double r) {
    double eps = Math.max(PROPER_CROSS_FRAC * r, 1.0e-12);
    for (int i = 0; i < nodes.size(); i++) {
      if (nodes.get(i).pt.distance(n.pt) <= eps) return;
    }
    nodes.add(n);
  }

  private static double parameter(Coordinate a, Coordinate b, Coordinate p) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double len2 = dx * dx + dy * dy;
    if (len2 == 0.0) return 0.0;
    double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2;
    if (t < 0.0) return 0.0;
    if (t > 1.0) return 1.0;
    return t;
  }

  private static List<Coordinate> walk(Coordinate[] ring, Node from, Node to,
      double r) {
    int n = ring.length - 1;
    List<RingPt> pts = new ArrayList<RingPt>();
    for (int i = 0; i < n; i++) {
      pts.add(new RingPt(i, 0.0, ring[i]));
    }
    pts.add(new RingPt(from.edge, from.t, from.pt));
    pts.add(new RingPt(to.edge, to.t, to.pt));
    Collections.sort(pts);

    double eps = Math.max(PROPER_CROSS_FRAC * r, 1.0e-12);
    List<RingPt> uniq = new ArrayList<RingPt>();
    for (int i = 0; i < pts.size(); i++) {
      RingPt cur = pts.get(i);
      if (!uniq.isEmpty() && uniq.get(uniq.size() - 1).pt.distance(cur.pt) <= eps) {
        continue;
      }
      uniq.add(cur);
    }
    if (uniq.size() > 1
        && uniq.get(0).pt.distance(uniq.get(uniq.size() - 1).pt) <= eps) {
      uniq.remove(uniq.size() - 1);
    }

    int iFrom = nearest(uniq, from.pt);
    int iTo = nearest(uniq, to.pt);
    if (iFrom < 0 || iTo < 0 || iFrom == iTo) return null;

    List<Coordinate> path = new ArrayList<Coordinate>();
    int i = iFrom;
    path.add(new Coordinate(uniq.get(i).pt));
    do {
      i = (i + 1) % uniq.size();
      path.add(new Coordinate(uniq.get(i).pt));
    } while (i != iTo);
    return path.size() >= 2 ? path : null;
  }

  private static int nearest(List<RingPt> pts, Coordinate p) {
    int best = -1;
    double bestD = Double.POSITIVE_INFINITY;
    for (int i = 0; i < pts.size(); i++) {
      double d = pts.get(i).pt.distance(p);
      if (d < bestD) {
        bestD = d;
        best = i;
      }
    }
    return best;
  }

  private static int sideOfDisc(List<Coordinate> path, double cx, double cy,
      double r) {
    double eps = Math.max(1.0e-8 * r, 1.0e-12);
    boolean sawIn = false;
    boolean sawOut = false;
    for (int i = 1; i < path.size() - 1; i++) {
      double d = Math.hypot(path.get(i).x - cx, path.get(i).y - cy);
      if (d < r - eps) sawIn = true;
      else if (d > r + eps) sawOut = true;
    }
    if (sawIn && sawOut) return MIXED;
    if (sawIn) return IN;
    if (sawOut) return OUT;
    Coordinate a = path.get(0);
    Coordinate b = path.get(path.size() - 1);
    double mx = 0.5 * (a.x + b.x);
    double my = 0.5 * (a.y + b.y);
    return Math.hypot(mx - cx, my - cy) <= r + eps ? IN : OUT;
  }

  /**
   * Same quadratic as {@link CircularArcDensifier#intersectSegmentCircle}.
   * Copied so this class can live next to OverlayNGCurve without a public
   * intersection API.
   */
  private static Coordinate[] intersectSegmentCircle(double cx, double cy,
      double r, Coordinate s0, Coordinate s1) {
    double dx = s1.x - s0.x;
    double dy = s1.y - s0.y;
    double fx = s0.x - cx;
    double fy = s0.y - cy;
    double A = dx * dx + dy * dy;
    if (A == 0.0) {
      if (Math.abs(Math.hypot(fx, fy) - r) <= 1.0e-12) {
        return new Coordinate[] { new Coordinate(s0) };
      }
      return new Coordinate[0];
    }
    double B = 2.0 * (fx * dx + fy * dy);
    double C = fx * fx + fy * fy - r * r;
    double disc = B * B - 4.0 * A * C;
    if (disc < 0.0) return new Coordinate[0];
    double sqrt = Math.sqrt(disc);
    Coordinate p0 = null;
    Coordinate p1 = null;
    int n = 0;
    for (int sign = -1; sign <= 1; sign += 2) {
      double t = (-B + sign * sqrt) / (2.0 * A);
      if (t < -1.0e-12 || t > 1.0 + 1.0e-12) continue;
      Coordinate p = new Coordinate(s0.x + t * dx, s0.y + t * dy);
      if (n == 0) {
        p0 = p;
        n = 1;
      } else if (p0.distance(p) > 1.0e-12) {
        p1 = p;
        n = 2;
      }
    }
    if (n == 0) return new Coordinate[0];
    if (n == 1) return new Coordinate[] { p0 };
    return new Coordinate[] { p0, p1 };
  }

  private static CircularString arc(Coordinate start, Coordinate mid,
      Coordinate end, GeometryFactory f) {
    Coordinate[] pts = new Coordinate[] {
        new Coordinate(start), new Coordinate(mid), new Coordinate(end)
    };
    return new CircularString(f.getCoordinateSequenceFactory().create(pts), f);
  }

  private static GeometryFactory curveFactory(Geometry g) {
    GeometryFactory f = g.getFactory();
    if (f instanceof CurveGeometryFactory) return f;
    return new CurveGeometryFactory(f.getPrecisionModel(), f.getSRID(),
        f.getCoordinateSequenceFactory());
  }

  private static List<Coordinate> reverse(List<Coordinate> path) {
    List<Coordinate> out = new ArrayList<Coordinate>(path.size());
    for (int i = path.size() - 1; i >= 0; i--) {
      out.add(path.get(i));
    }
    return out;
  }

  private static List<Coordinate> startingAt(List<Coordinate> path,
      Coordinate start, double eps) {
    if (path.get(0).distance(start) <= eps) return path;
    return reverse(path);
  }

  private static Polygon curveRing(Coordinate p, Coordinate mid, Coordinate q,
      List<Coordinate> qToP, GeometryFactory f, double r) {
    double eps = Math.max(PROPER_CROSS_FRAC * r, 1.0e-12);
    List<Coordinate> line = new ArrayList<Coordinate>();
    line.add(new Coordinate(q));
    List<Coordinate> directed = startingAt(qToP, q, eps);
    for (int i = 0; i < directed.size(); i++) {
      Coordinate c = directed.get(i);
      if (line.get(line.size() - 1).distance(c) <= eps) continue;
      line.add(new Coordinate(c));
    }
    if (line.get(line.size() - 1).distance(p) > eps) {
      line.add(new Coordinate(p));
    }
    if (line.size() < 2) {
      line.clear();
      line.add(new Coordinate(q));
      line.add(new Coordinate(p));
    }

    Coordinate start = p;
    Coordinate end = q;
    List<Coordinate> pathPts = line;
    double signed = signedArea(start, mid, end, pathPts);
    if (signed < 0.0) {
      start = q;
      end = p;
      pathPts = reverse(line);
    }

    CircularString cs = arc(start, mid, end, f);
    LineString path = f.createLineString(pathPts.toArray(new Coordinate[0]));
    CompoundCurve shell = new CompoundCurve(new LineString[] { cs, path }, f);
    return new CurvePolygon(shell, null, f);
  }

  private static double signedArea(Coordinate start, Coordinate mid,
      Coordinate end, List<Coordinate> path) {
    double signed = CircularArcDensifier.arcAreaContribution(start, mid, end);
    for (int i = 0; i < path.size() - 1; i++) {
      Coordinate a = path.get(i);
      Coordinate b = path.get(i + 1);
      signed += 0.5 * (a.x * b.y - b.x * a.y);
    }
    return signed;
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

    Polygon cap(GeometryFactory f) {
      return curveRing(p, midIn, q, pathIn, f, r);
    }

    Polygon cup(GeometryFactory f) {
      return curveRing(p, midOut, q, pathOut, f, r);
    }

    Polygon discMinusPoly(GeometryFactory f) {
      return curveRing(p, midOut, q, pathIn, f, r);
    }

    Polygon polyMinusDisc(GeometryFactory f) {
      return curveRing(p, midIn, q, pathOut, f, r);
    }
  }

  private static final class Node {
    final int edge;
    final double t;
    final Coordinate pt;
    Node(int edge, double t, Coordinate pt) {
      this.edge = edge;
      this.t = t;
      this.pt = pt;
    }
  }

  private static final class RingPt implements Comparable<RingPt> {
    final int edge;
    final double t;
    final Coordinate pt;
    RingPt(int edge, double t, Coordinate pt) {
      this.edge = edge;
      this.t = t;
      this.pt = pt;
    }

    public int compareTo(RingPt o) {
      if (edge != o.edge) return edge < o.edge ? -1 : 1;
      return Double.compare(t, o.t);
    }
  }
}
