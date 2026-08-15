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
 * Package-private two-node clip kit for the overlay ratchet.
 * One place for segment–circle / circle–circle hits, a ring or
 * CompoundCurve-member walk between two nodes, side-of, and
 * CAP / CUP / SUB / XOR of a {@link CurvePolygon} whose shell is a
 * {@link CompoundCurve} of the surviving pieces.
 * <p>
 * Not a noder. 0 / 1 / 3+ nodes are the caller's miss. R1.5, R1.6,
 * and R1.7 call this; they keep their own shape dispatch.
 */
final class TwoNodeClip {

  /**
   * Two computed nodes closer than this fraction of the scale are a
   * tangent pair in floating point, not a proper chord.
   */
  static final double PROPER_CROSS_FRAC = 1.0e-9;
  static final double TWO_PI = 2.0 * Math.PI;
  static final int IN = 1;
  static final int OUT = -1;
  static final int MIXED = 0;

  private TwoNodeClip() { }

  /**
   * CAP / CUP / SUB / XOR of two classified walks. {@code aFirst}
   * is whether operand A is the {@code aIn}/{@code aOut} side.
   * A miss (empty walk, unorientable ring) is {@code null}.
   */
  static Geometry overlay(int opCode, boolean aFirst,
      List<LineString> aIn, List<LineString> aOut,
      List<LineString> bIn, List<LineString> bOut,
      Coordinate p, Coordinate q, GeometryFactory f, double scale) {
    if (opCode == OverlayNG.INTERSECTION) {
      return ring(aIn, bIn, p, q, f, scale);
    }
    if (opCode == OverlayNG.UNION) {
      return ring(aOut, bOut, p, q, f, scale);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return aFirst
          ? ring(aOut, bIn, p, q, f, scale)
          : ring(bOut, aIn, p, q, f, scale);
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      Polygon ab = ring(aOut, bIn, p, q, f, scale);
      Polygon ba = ring(bOut, aIn, p, q, f, scale);
      if (ab == null || ba == null) return null;
      return new MultiSurface(new Polygon[] { ab, ba }, f);
    }
    return null;
  }

  /**
   * Closed CompoundCurve: {@code along} from P to Q, then {@code back}
   * from Q to P. A LineString member stays a segment; an arc stays an
   * arc. Lists are reversed as a whole when they currently run the
   * other way.
   */
  static Polygon ring(List<LineString> along, List<LineString> back,
      Coordinate p, Coordinate q, GeometryFactory f, double scale) {
    if (along == null || along.isEmpty() || back == null || back.isEmpty()) {
      return null;
    }
    double eps = Math.max(PROPER_CROSS_FRAC * scale, 1.0e-12);
    List<LineString> alongDir = directedList(along, p, q, eps, f);
    List<LineString> backDir = directedList(back, q, p, eps, f);
    if (alongDir == null || backDir == null) return null;
    List<LineString> members = new ArrayList<LineString>();
    addAll(members, alongDir);
    addAll(members, backDir);
    return closeRing(members, f, eps);
  }

  static Polygon closeRing(List<LineString> members, GeometryFactory f,
      double eps) {
    List<LineString> clean = new ArrayList<LineString>();
    for (int i = 0; i < members.size(); i++) {
      LineString m = members.get(i);
      if (m == null || m.isEmpty() || m.getNumPoints() < 2) continue;
      if (m.getLength() <= eps && !(m instanceof CircularString)) continue;
      clean.add(m);
    }
    if (clean.size() < 1) return null;
    try {
      CompoundCurve cc = new CompoundCurve(
          clean.toArray(new LineString[0]), f);
      if (signedArea(cc) < 0.0) {
        cc = (CompoundCurve) cc.reverse();
      }
      return new CurvePolygon(cc, null, f);
    }
    catch (RuntimeException ex) {
      return null;
    }
  }

  /**
   * Flatten a CompoundCurve shell to typed edges. A LineString member
   * becomes segments; a CircularString becomes 3-control sweep windows.
   * A colinear triple is a segment, not an arc.
   */
  static List<Edge> flatten(CurvePolygon shell) {
    LineString ring = shell.getExteriorCurve();
    if (!(ring instanceof CompoundCurve)) return null;
    CompoundCurve cc = (CompoundCurve) ring;
    List<Edge> edges = new ArrayList<Edge>();
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      Coordinate[] pts = m.getCoordinates();
      if (m instanceof CircularString) {
        if (pts.length < 3) return null;
        for (int k = 0; k + 2 < pts.length; k += 2) {
          double[] c = CircularArcDensifier.circumcircle(
              pts[k], pts[k + 1], pts[k + 2]);
          if (c == null) {
            edges.add(new Edge(pts[k], null, pts[k + 2], false, null));
          }
          else {
            edges.add(new Edge(pts[k], pts[k + 1], pts[k + 2], true, c));
          }
        }
      }
      else {
        if (pts.length < 2) return null;
        for (int k = 0; k < pts.length - 1; k++) {
          edges.add(new Edge(pts[k], null, pts[k + 1], false, null));
        }
      }
    }
    return edges.isEmpty() ? null : edges;
  }

  static List<Node> nodesVsDisc(List<Edge> edges, double cx, double cy,
      double r) {
    List<Node> nodes = new ArrayList<Node>();
    for (int i = 0; i < edges.size(); i++) {
      Edge e = edges.get(i);
      if (e.isArc) {
        Coordinate[] hits = intersectCircles(e.circle[0], e.circle[1],
            e.circle[2], cx, cy, r);
        for (int k = 0; k < hits.length; k++) {
          if (!isOnSweep(hits[k], e.circle, e.a, e.mid, e.b)) continue;
          addUnique(nodes, new Node(i, e.param(hits[k]), hits[k]), r);
        }
      }
      else {
        Coordinate[] hits = intersectSegmentCircle(cx, cy, r, e.a, e.b);
        for (int k = 0; k < hits.length; k++) {
          addUnique(nodes, new Node(i, e.param(hits[k]), hits[k]), r);
        }
      }
    }
    return nodes;
  }

  static List<Node> nodesVsPolygon(List<Edge> edges, Coordinate[] ring) {
    List<Node> nodes = new ArrayList<Node>();
    int n = ring.length - 1;
    LineIntersector li = new RobustLineIntersector();
    for (int i = 0; i < edges.size(); i++) {
      Edge e = edges.get(i);
      for (int j = 0; j < n; j++) {
        if (e.isArc) {
          Coordinate[] hits = intersectSegmentCircle(
              e.circle[0], e.circle[1], e.circle[2], ring[j], ring[j + 1]);
          for (int k = 0; k < hits.length; k++) {
            if (!isOnSweep(hits[k], e.circle, e.a, e.mid, e.b)) continue;
            addUnique(nodes, new Node(i, e.param(hits[k]), hits[k]), 1.0);
          }
        }
        else {
          li.computeIntersection(e.a, e.b, ring[j], ring[j + 1]);
          if (li.getIntersectionNum() == 1) {
            addUnique(nodes, new Node(i, e.param(li.getIntersection(0)),
                li.getIntersection(0)), 1.0);
          }
        }
      }
    }
    return nodes;
  }

  /**
   * Walk a typed-edge ring from {@code from} to {@code to}, keeping
   * each piece as a segment or an arc sweep.
   */
  static List<LineString> walkEdges(List<Edge> edges, Node from, Node to,
      GeometryFactory f) {
    if (from.edge == to.edge && from.t <= to.t + 1.0e-12) {
      LineString piece = subEdge(edges.get(from.edge), from.pt, to.pt, f);
      if (piece == null) return null;
      return listOf(piece);
    }
    List<LineString> out = new ArrayList<LineString>();
    int e = from.edge;
    Coordinate cursor = from.pt;
    int guard = 0;
    while (guard++ < edges.size() + 2) {
      Edge edge = edges.get(e);
      if (e == to.edge && cursor != from.pt) {
        LineString piece = subEdge(edge, cursor, to.pt, f);
        if (piece != null) out.add(piece);
        return out.isEmpty() ? null : out;
      }
      LineString piece = subEdge(edge, cursor, edge.b, f);
      if (piece != null) out.add(piece);
      e = (e + 1) % edges.size();
      cursor = edges.get(e).a;
      if (e == to.edge) {
        LineString last = subEdge(edges.get(e), cursor, to.pt, f);
        if (last != null) out.add(last);
        return out.isEmpty() ? null : out;
      }
    }
    return null;
  }

  /**
   * Walk a plain ring from a known node to another. Same walk R1.6
   * uses: vertices plus the two hits, one way around.
   */
  static List<Coordinate> walkRing(Coordinate[] ring, Node from, Node to,
      double scale) {
    int n = ring.length - 1;
    List<RingPt> pts = new ArrayList<RingPt>();
    for (int i = 0; i < n; i++) {
      pts.add(new RingPt(i, 0.0, ring[i]));
    }
    pts.add(new RingPt(from.edge, from.t, from.pt));
    pts.add(new RingPt(to.edge, to.t, to.pt));
    Collections.sort(pts);

    double eps = Math.max(PROPER_CROSS_FRAC * scale, 1.0e-12);
    List<RingPt> uniq = new ArrayList<RingPt>();
    for (int i = 0; i < pts.size(); i++) {
      RingPt cur = pts.get(i);
      if (!uniq.isEmpty()
          && uniq.get(uniq.size() - 1).pt.distance(cur.pt) <= eps) {
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

  /** Project {@code from}/{@code to} onto the ring, then walk. */
  static List<Coordinate> walkRing(Coordinate[] ring, Coordinate from,
      Coordinate to) {
    return walkRing(ring, hitOnRing(ring, from), hitOnRing(ring, to), 1.0);
  }

  static Node hitOnRing(Coordinate[] ring, Coordinate p) {
    int n = ring.length - 1;
    int best = 0;
    double bestD = Double.POSITIVE_INFINITY;
    double bestT = 0.0;
    for (int i = 0; i < n; i++) {
      double t = parameter(ring[i], ring[i + 1], p);
      Coordinate q = new Coordinate(
          ring[i].x + t * (ring[i + 1].x - ring[i].x),
          ring[i].y + t * (ring[i + 1].y - ring[i].y));
      double d = p.distance(q);
      if (d < bestD) {
        bestD = d;
        best = i;
        bestT = t;
      }
    }
    return new Node(best, bestT, p);
  }

  static List<Coordinate> startingAt(List<Coordinate> path, Coordinate start) {
    if (path.get(0).distance(start) <= 1.0e-12) return path;
    List<Coordinate> rev = new ArrayList<Coordinate>(path.size());
    for (int i = path.size() - 1; i >= 0; i--) {
      rev.add(path.get(i));
    }
    return rev;
  }

  static int sideOfDisc(Coordinate sample, double cx, double cy, double r) {
    if (sample == null) return MIXED;
    double eps = Math.max(1.0e-8 * r, 1.0e-12);
    double d = Math.hypot(sample.x - cx, sample.y - cy);
    if (d < r - eps) return IN;
    if (d > r + eps) return OUT;
    return MIXED;
  }

  static int sideOfDisc(List<LineString> walk, double cx, double cy, double r) {
    return sideOfDisc(sample(walk), cx, cy, r);
  }

  /**
   * R1.6 path classification: every interior vertex, then the chord
   * midpoint if the walk has no interior vertex off the circle.
   */
  static int sideOfDisc(List<Coordinate> path, double cx, double cy, double r) {
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

  static int sideOfPolygon(Coordinate sample, Polygon poly) {
    if (sample == null) return MIXED;
    int loc = SimplePointInAreaLocator.locate(sample, poly);
    if (loc == Location.INTERIOR) return IN;
    if (loc == Location.EXTERIOR) return OUT;
    return MIXED;
  }

  static int sideOfPolygon(List<LineString> walk, Polygon poly) {
    return sideOfPolygon(sample(walk), poly);
  }

  /**
   * Even-odd ray cast against a CompoundCurve shell. Boundary hits
   * are MIXED so a two-node clip that lands on the ring is refused.
   */
  static int locateInShell(Coordinate p, CurvePolygon shell) {
    List<Edge> edges = flatten(shell);
    if (edges == null) return MIXED;
    double envW = Math.max(1.0, shell.getEnvelopeInternal().getWidth());
    Coordinate far = new Coordinate(p.x + envW * 4.0 + 1.0, p.y);
    int crossings = 0;
    for (int i = 0; i < edges.size(); i++) {
      Edge e = edges.get(i);
      if (e.isArc) {
        Coordinate[] hits = intersectSegmentCircle(
            e.circle[0], e.circle[1], e.circle[2], p, far);
        for (int k = 0; k < hits.length; k++) {
          if (hits[k].distance(p) <= 1.0e-12) return MIXED;
          if (!isOnSweep(hits[k], e.circle, e.a, e.mid, e.b)) continue;
          if (hits[k].x > p.x + 1.0e-12) crossings++;
        }
      }
      else {
        if (onSegment(p, e.a, e.b)) return MIXED;
        if (rayCrosses(p, e.a, e.b)) crossings++;
      }
    }
    return (crossings & 1) == 1 ? IN : OUT;
  }

  static Coordinate sample(List<LineString> walk) {
    for (int i = 0; i < walk.size(); i++) {
      LineString m = walk.get(i);
      if (m instanceof CircularString && m.getNumPoints() >= 3) {
        return m.getCoordinateN(1);
      }
      Coordinate[] c = m.getCoordinates();
      if (c.length >= 2) {
        return new Coordinate(0.5 * (c[0].x + c[c.length - 1].x),
            0.5 * (c[0].y + c[c.length - 1].y));
      }
    }
    return null;
  }

  static Coordinate midOnCircle(double cx, double cy, double r,
      double a0, double signedSweep) {
    if (signedSweep == 0.0) signedSweep = TWO_PI;
    double a = a0 + 0.5 * signedSweep;
    return new Coordinate(cx + r * Math.cos(a), cy + r * Math.sin(a));
  }

  /**
   * Mid-arc control of the sweep from {@code p} to {@code q} whose
   * midpoint is inside ({@code wantInside}) or outside, given a
   * side test on each candidate. Null when both sweeps land on the
   * same side.
   */
  static Coordinate sweepMid(Coordinate p, Coordinate q,
      double cx, double cy, double r, boolean wantInside, Side at) {
    double aP = Math.atan2(p.y - cy, p.x - cx);
    double aQ = Math.atan2(q.y - cy, q.x - cx);
    Coordinate ccw = midOnCircle(cx, cy, r, aP, normPos(aQ - aP));
    Coordinate cw = midOnCircle(cx, cy, r, aP, -normPos(aP - aQ));
    boolean ccwIn = at.inside(ccw);
    boolean cwIn = at.inside(cw);
    if (ccwIn == cwIn) return null;
    if (wantInside) return ccwIn ? ccw : cw;
    return ccwIn ? cw : ccw;
  }

  static boolean isPlainPolygon(Geometry g) {
    if (g == null || g.isEmpty()) return false;
    if (g instanceof CurvePolygon) return false;
    if (!(g instanceof Polygon)) return false;
    if (((Polygon) g).getNumInteriorRing() > 0) return false;
    return CurveOps.tolerance(g) <= 0.0;
  }

  static boolean properPair(List<Node> nodes, double scale) {
    if (nodes == null || nodes.size() != 2) return false;
    return nodes.get(0).pt.distance(nodes.get(1).pt)
        >= PROPER_CROSS_FRAC * scale;
  }

  static void addUnique(List<Node> nodes, Node n, double scale) {
    double eps = Math.max(PROPER_CROSS_FRAC * scale, 1.0e-12);
    for (int i = 0; i < nodes.size(); i++) {
      if (nodes.get(i).pt.distance(n.pt) <= eps) return;
    }
    nodes.add(n);
  }

  static double parameter(Coordinate a, Coordinate b, Coordinate p) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double len2 = dx * dx + dy * dy;
    if (len2 == 0.0) return 0.0;
    double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2;
    if (t < 0.0) return 0.0;
    if (t > 1.0) return 1.0;
    return t;
  }

  static double normPos(double angle) {
    angle = angle % TWO_PI;
    if (angle < 0.0) angle += TWO_PI;
    return angle;
  }

  static boolean isOnSweep(Coordinate p, double[] c, Coordinate start,
      Coordinate mid, Coordinate end) {
    double a0 = Math.atan2(start.y - c[1], start.x - c[0]);
    double aMid = Math.atan2(mid.y - c[1], mid.x - c[0]);
    double a1 = Math.atan2(end.y - c[1], end.x - c[0]);
    boolean ccw = normPos(aMid - a0) < normPos(a1 - a0);
    double sweep = ccw ? normPos(a1 - a0) : normPos(a0 - a1);
    if (sweep == 0.0) sweep = TWO_PI;
    double angle = Math.atan2(p.y - c[1], p.x - c[0]);
    double travelled = ccw ? normPos(angle - a0) : normPos(a0 - angle);
    return travelled <= sweep + 1.0e-12;
  }

  static Coordinate[] intersectCircles(double c1x, double c1y, double r1,
      double c2x, double c2y, double r2) {
    double dx = c2x - c1x;
    double dy = c2y - c1y;
    double d = Math.hypot(dx, dy);
    if (d > r1 + r2 || d < Math.abs(r1 - r2) || d == 0.0) {
      return new Coordinate[0];
    }
    double a = (r1 * r1 - r2 * r2 + d * d) / (2.0 * d);
    double h2 = r1 * r1 - a * a;
    if (h2 < 0.0) return new Coordinate[0];
    double ux = dx / d;
    double uy = dy / d;
    double mx = c1x + a * ux;
    double my = c1y + a * uy;
    if (h2 == 0.0) {
      return new Coordinate[] { new Coordinate(mx, my) };
    }
    double h = Math.sqrt(h2);
    return new Coordinate[] {
        new Coordinate(mx + h * -uy, my + h * ux),
        new Coordinate(mx - h * -uy, my - h * ux)
    };
  }

  static Coordinate[] intersectSegmentCircle(double cx, double cy, double r,
      Coordinate s0, Coordinate s1) {
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
      }
      else if (p0.distance(p) > 1.0e-12) {
        p1 = p;
        n = 2;
      }
    }
    if (n == 0) return new Coordinate[0];
    if (n == 1) return new Coordinate[] { p0 };
    return new Coordinate[] { p0, p1 };
  }

  static CircularString arc(Coordinate start, Coordinate mid, Coordinate end,
      GeometryFactory f) {
    Coordinate[] pts = new Coordinate[] {
        new Coordinate(start), new Coordinate(mid), new Coordinate(end)
    };
    return new CircularString(f.getCoordinateSequenceFactory().create(pts), f);
  }

  static GeometryFactory curveFactory(Geometry g) {
    GeometryFactory f = g.getFactory();
    if (f instanceof CurveGeometryFactory) return f;
    return new CurveGeometryFactory(f.getPrecisionModel(), f.getSRID(),
        f.getCoordinateSequenceFactory());
  }

  static List<LineString> listOf(LineString g) {
    List<LineString> out = new ArrayList<LineString>();
    out.add(g);
    return out;
  }

  static LineString asLine(List<Coordinate> path, GeometryFactory f) {
    return f.createLineString(path.toArray(new Coordinate[0]));
  }

  private static List<LineString> directedList(List<LineString> parts,
      Coordinate from, Coordinate to, double eps, GeometryFactory f) {
    Coordinate start = parts.get(0).getCoordinateN(0);
    LineString last = parts.get(parts.size() - 1);
    Coordinate end = last.getCoordinateN(last.getNumPoints() - 1);
    if (start.distance(from) <= eps && end.distance(to) <= eps) {
      return parts;
    }
    if (start.distance(to) <= eps && end.distance(from) <= eps) {
      return reverseMembers(parts);
    }
    if (parts.size() == 1) {
      LineString d = directed(parts.get(0), from, to, eps, f);
      if (d == null) return null;
      return listOf(d);
    }
    return null;
  }

  private static List<LineString> reverseMembers(List<LineString> parts) {
    List<LineString> out = new ArrayList<LineString>(parts.size());
    for (int i = parts.size() - 1; i >= 0; i--) {
      out.add((LineString) parts.get(i).reverse());
    }
    return out;
  }

  private static LineString directed(LineString g, Coordinate from,
      Coordinate to, double eps, GeometryFactory f) {
    Coordinate[] c = g.getCoordinates();
    if (c.length < 2) return null;
    boolean startFrom = c[0].distance(from) <= eps;
    boolean endTo = c[c.length - 1].distance(to) <= eps;
    if (startFrom && endTo) return g;
    boolean startTo = c[0].distance(to) <= eps;
    boolean endFrom = c[c.length - 1].distance(from) <= eps;
    if (startTo && endFrom) {
      return (LineString) g.reverse();
    }
    if (!(g instanceof CircularString)) {
      return f.createLineString(new Coordinate[] {
          new Coordinate(from), new Coordinate(to)
      });
    }
    Coordinate mid = c.length >= 3 ? c[c.length / 2] : null;
    if (mid == null) return null;
    return arc(from, mid, to, f);
  }

  private static double signedArea(CompoundCurve cc) {
    double signed = 0.0;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
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
          signed += 0.5 * (pts[k].x * pts[k + 1].y - pts[k + 1].x * pts[k].y);
        }
      }
    }
    return signed;
  }

  private static LineString subEdge(Edge e, Coordinate from, Coordinate to,
      GeometryFactory f) {
    double eps = 1.0e-12;
    if (from.distance(to) <= eps) return null;
    if (!e.isArc) {
      return f.createLineString(new Coordinate[] {
          new Coordinate(from), new Coordinate(to)
      });
    }
    Coordinate mid = midOnSweep(from, to, e);
    if (mid == null || mid.distance(from) <= eps || mid.distance(to) <= eps) {
      return f.createLineString(new Coordinate[] {
          new Coordinate(from), new Coordinate(to)
      });
    }
    return arc(from, mid, to, f);
  }

  private static Coordinate midOnSweep(Coordinate from, Coordinate to, Edge e) {
    double[] c = e.circle;
    double a0 = Math.atan2(from.y - c[1], from.x - c[0]);
    double a1 = Math.atan2(to.y - c[1], to.x - c[0]);
    double aS = Math.atan2(e.a.y - c[1], e.a.x - c[0]);
    double aM = Math.atan2(e.mid.y - c[1], e.mid.x - c[0]);
    double aE = Math.atan2(e.b.y - c[1], e.b.x - c[0]);
    boolean ccw = normPos(aM - aS) < normPos(aE - aS);
    double sweep = ccw ? normPos(a1 - a0) : -normPos(a0 - a1);
    if (sweep == 0.0) sweep = ccw ? TWO_PI : -TWO_PI;
    double a = a0 + 0.5 * sweep;
    return new Coordinate(c[0] + c[2] * Math.cos(a), c[1] + c[2] * Math.sin(a));
  }

  private static boolean rayCrosses(Coordinate p, Coordinate a, Coordinate b) {
    if (a.y > b.y) {
      Coordinate t = a;
      a = b;
      b = t;
    }
    if (p.y < a.y || p.y >= b.y) return false;
    if (a.y == b.y) return false;
    double x = a.x + (p.y - a.y) * (b.x - a.x) / (b.y - a.y);
    return x > p.x;
  }

  private static boolean onSegment(Coordinate p, Coordinate a, Coordinate b) {
    double vx = b.x - a.x;
    double vy = b.y - a.y;
    double len2 = vx * vx + vy * vy;
    if (len2 == 0.0) return p.distance(a) <= 1.0e-12;
    double t = ((p.x - a.x) * vx + (p.y - a.y) * vy) / len2;
    if (t < -1.0e-12 || t > 1.0 + 1.0e-12) return false;
    Coordinate q = new Coordinate(a.x + t * vx, a.y + t * vy);
    return p.distance(q) <= 1.0e-12;
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

  private static void addAll(List<LineString> dest, List<LineString> src) {
    for (int i = 0; i < src.size(); i++) {
      dest.add(src.get(i));
    }
  }

  /** Side test used by {@link #sweepMid}. */
  interface Side {
    boolean inside(Coordinate p);
  }

  static final class Edge {
    final Coordinate a;
    final Coordinate mid;
    final Coordinate b;
    final boolean isArc;
    final double[] circle;

    Edge(Coordinate a, Coordinate mid, Coordinate b, boolean isArc,
        double[] circle) {
      this.a = a;
      this.mid = mid;
      this.b = b;
      this.isArc = isArc;
      this.circle = circle;
    }

    double param(Coordinate p) {
      if (!isArc) {
        return TwoNodeClip.parameter(a, b, p);
      }
      double a0 = Math.atan2(a.y - circle[1], a.x - circle[0]);
      double aM = Math.atan2(mid.y - circle[1], mid.x - circle[0]);
      double a1 = Math.atan2(b.y - circle[1], b.x - circle[0]);
      boolean ccw = normPos(aM - a0) < normPos(a1 - a0);
      double sweep = ccw ? normPos(a1 - a0) : normPos(a0 - a1);
      if (sweep == 0.0) sweep = TWO_PI;
      double angle = Math.atan2(p.y - circle[1], p.x - circle[0]);
      double travelled = ccw ? normPos(angle - a0) : normPos(a0 - angle);
      return travelled / sweep;
    }
  }

  static final class Node {
    final int edge;
    final double t;
    final Coordinate pt;
    Node(int edge, double t, Coordinate pt) {
      this.edge = edge;
      this.t = t;
      this.pt = pt;
    }
  }

  static final class RingPt implements Comparable<RingPt> {
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
