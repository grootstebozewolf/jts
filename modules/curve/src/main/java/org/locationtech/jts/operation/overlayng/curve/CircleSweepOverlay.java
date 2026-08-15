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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * Angular-interval overlay of two lineal operands that live on the
 * <em>same</em> circle. Package-private -- not a two-node clip, not a
 * noder, not a public API.
 * <p>
 * Each sweep window becomes a closed arc of the circle; CAP / CUP /
 * SUB / XOR are the interval operations; pieces stay
 * {@link CircularString}. Degenerate (point) intersection of otherwise
 * disjoint sweeps is the shared endpoint(s).
 */
final class CircleSweepOverlay {

  private static final double EPS = 1.0e-12;
  private static final double TWO_PI = TwoNodeClip.TWO_PI;

  private CircleSweepOverlay() { }

  static boolean allSameCircle(List<TwoNodeClip.Edge> a,
      List<TwoNodeClip.Edge> b, double scale) {
    double[] c = circleOf(a, scale);
    if (c == null) return false;
    return sameCircle(c, circleOf(b, scale), scale);
  }

  static Geometry overlay(List<TwoNodeClip.Edge> a, List<TwoNodeClip.Edge> b,
      int opCode, Geometry factorySrc, double scale) {
    double[] c = circleOf(a, scale);
    if (!sameCircle(c, circleOf(b, scale), scale)) return null;
    Intervals ia = Intervals.cover(a, c);
    Intervals ib = Intervals.cover(b, c);
    if (ia == null || ib == null) return null;
    GeometryFactory f = TwoNodeClip.curveFactory(factorySrc);
    if (opCode == OverlayNG.INTERSECTION) {
      Geometry arcs = ia.combine(ib, 2).toArcs(c, f);
      if (arcs != null && !arcs.isEmpty()) return arcs;
      return CircularLineOverlay.points(sharedEnds(a, b, c, scale), f);
    }
    Intervals out;
    if (opCode == OverlayNG.UNION) {
      out = ia.combine(ib, 1);
    }
    else if (opCode == OverlayNG.DIFFERENCE) {
      out = ia.minus(ib);
    }
    else if (opCode == OverlayNG.SYMDIFFERENCE) {
      out = ia.minus(ib).combine(ib.minus(ia), 1);
    }
    else {
      return null;
    }
    Geometry g = out.toArcs(c, f);
    return g != null ? g : f.createEmpty(1);
  }

  private static double[] circleOf(List<TwoNodeClip.Edge> edges, double scale) {
    if (edges == null || edges.isEmpty()) return null;
    double[] c = null;
    for (int i = 0; i < edges.size(); i++) {
      TwoNodeClip.Edge e = edges.get(i);
      if (!e.isArc) return null;
      if (c == null) {
        c = new double[] { e.circle[0], e.circle[1], e.circle[2] };
      }
      else if (!sameCircle(c, e.circle, scale)) {
        return null;
      }
    }
    return c;
  }

  private static boolean sameCircle(double[] a, double[] b, double scale) {
    if (a == null || b == null) return false;
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, EPS);
    Coordinate ca = new Coordinate(a[0], a[1]);
    Coordinate cb = new Coordinate(b[0], b[1]);
    return ca.distance(cb) <= eps && Math.abs(a[2] - b[2]) <= eps;
  }

  private static List<Coordinate> sharedEnds(List<TwoNodeClip.Edge> a,
      List<TwoNodeClip.Edge> b, double[] c, double scale) {
    List<Coordinate> out = new ArrayList<Coordinate>();
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, EPS);
    for (int i = 0; i < a.size(); i++) {
      addIfOn(out, a.get(i).a, b, c, eps);
      addIfOn(out, a.get(i).b, b, c, eps);
    }
    return out;
  }

  private static void addIfOn(List<Coordinate> out, Coordinate p,
      List<TwoNodeClip.Edge> edges, double[] c, double eps) {
    for (int i = 0; i < edges.size(); i++) {
      TwoNodeClip.Edge e = edges.get(i);
      if (!TwoNodeClip.isOnSweep(p, c, e.a, e.mid, e.b)) continue;
      for (int k = 0; k < out.size(); k++) {
        if (out.get(k).distance(p) <= eps) return;
      }
      out.add(new Coordinate(p));
      return;
    }
  }

  /**
   * Disjoint CCW intervals on [0, 2π). A full circle is [0, 2π).
   */
  static final class Intervals {
    private final List<double[]> segs;

    private Intervals(List<double[]> segs) {
      this.segs = segs;
    }

    static Intervals cover(List<TwoNodeClip.Edge> edges, double[] c) {
      Intervals u = new Intervals(new ArrayList<double[]>());
      for (int i = 0; i < edges.size(); i++) {
        Intervals one = ofEdge(edges.get(i), c);
        if (one == null) return null;
        u = u.combine(one, 1);
      }
      return u;
    }

    static Intervals ofEdge(TwoNodeClip.Edge e, double[] c) {
      double a0 = Math.atan2(e.a.y - c[1], e.a.x - c[0]);
      double aM = Math.atan2(e.mid.y - c[1], e.mid.x - c[0]);
      double a1 = Math.atan2(e.b.y - c[1], e.b.x - c[0]);
      boolean ccw = TwoNodeClip.normPos(aM - a0) < TwoNodeClip.normPos(a1 - a0);
      double from = ccw ? a0 : a1;
      double to = ccw ? a1 : a0;
      if (e.a.distance(e.b) <= EPS) return full();
      double sweep = TwoNodeClip.normPos(to - from);
      if (sweep <= EPS) return new Intervals(new ArrayList<double[]>());
      return fromTo(TwoNodeClip.normPos(from), TwoNodeClip.normPos(to));
    }

    static Intervals full() {
      List<double[]> s = new ArrayList<double[]>();
      s.add(new double[] { 0.0, TWO_PI });
      return new Intervals(s);
    }

    static Intervals fromTo(double from, double to) {
      List<double[]> s = new ArrayList<double[]>();
      if (from < to - EPS) {
        s.add(new double[] { from, to });
      }
      else if (from > to + EPS) {
        s.add(new double[] { from, TWO_PI });
        s.add(new double[] { 0.0, to });
      }
      return new Intervals(s);
    }

    /**
     * Coverage sweep. {@code need == 1} is union, {@code need == 2}
     * is intersection. Openings sort before closings at a shared
     * angle so touching intervals merge.
     */
    Intervals combine(Intervals o, int need) {
      List<Ev> ev = new ArrayList<Ev>();
      addEvents(ev);
      o.addEvents(ev);
      Collections.sort(ev, EV);
      List<double[]> out = new ArrayList<double[]>();
      int cover = 0;
      double start = 0.0;
      boolean open = false;
      for (int i = 0; i < ev.size(); i++) {
        Ev e = ev.get(i);
        int next = cover + e.d;
        if (!open && next >= need) {
          start = e.a;
          open = true;
        }
        else if (open && next < need) {
          if (e.a - start > EPS) {
            out.add(new double[] { start, e.a });
          }
          open = false;
        }
        cover = next;
      }
      return new Intervals(mergeAdjacent(out));
    }

    Intervals minus(Intervals o) {
      List<Ev> ev = new ArrayList<Ev>();
      addEvents(ev, 0);
      o.addEvents(ev, 1);
      Collections.sort(ev, EV);
      int a = 0;
      int b = 0;
      double start = 0.0;
      boolean open = false;
      List<double[]> out = new ArrayList<double[]>();
      for (int i = 0; i < ev.size(); i++) {
        Ev e = ev.get(i);
        if (e.which == 0) a += e.d;
        else b += e.d;
        boolean next = a > 0 && b == 0;
        if (!open && next) {
          start = e.a;
          open = true;
        }
        else if (open && !next) {
          if (e.a - start > EPS) {
            out.add(new double[] { start, e.a });
          }
          open = false;
        }
      }
      return new Intervals(mergeAdjacent(out));
    }

    Geometry toArcs(double[] c, GeometryFactory f) {
      List<double[]> merged = mergeWrap(segs);
      List<LineString> pieces = new ArrayList<LineString>();
      for (int i = 0; i < merged.size(); i++) {
        LineString arc = arcOf(merged.get(i)[0], merged.get(i)[1], c, f);
        if (arc != null) pieces.add(arc);
      }
      return CircularLineOverlay.linealResult(pieces, f);
    }

    private void addEvents(List<Ev> ev) {
      addEvents(ev, 0);
    }

    private void addEvents(List<Ev> ev, int which) {
      for (int i = 0; i < segs.size(); i++) {
        ev.add(new Ev(segs.get(i)[0], 1, which));
        ev.add(new Ev(segs.get(i)[1], -1, which));
      }
    }

    private static List<double[]> mergeAdjacent(List<double[]> segs) {
      if (segs.size() < 2) return segs;
      List<double[]> out = new ArrayList<double[]>();
      double lo = segs.get(0)[0];
      double hi = segs.get(0)[1];
      for (int i = 1; i < segs.size(); i++) {
        if (segs.get(i)[0] <= hi + EPS) {
          hi = Math.max(hi, segs.get(i)[1]);
        }
        else {
          out.add(new double[] { lo, hi });
          lo = segs.get(i)[0];
          hi = segs.get(i)[1];
        }
      }
      out.add(new double[] { lo, hi });
      return out;
    }

    /**
     * Join a trailing [a, 2π) to a leading [0, b] into one sweep so
     * CUP of two overlapping quarters is one three-quarter arc.
     */
    private static List<double[]> mergeWrap(List<double[]> segs) {
      if (segs.size() < 2) return segs;
      double[] first = segs.get(0);
      double[] last = segs.get(segs.size() - 1);
      if (first[0] > EPS || last[1] < TWO_PI - EPS) return segs;
      List<double[]> out = new ArrayList<double[]>();
      for (int i = 1; i < segs.size() - 1; i++) {
        out.add(segs.get(i));
      }
      out.add(new double[] { last[0], first[1] + TWO_PI });
      return out;
    }

    private static LineString arcOf(double a0, double a1, double[] c,
        GeometryFactory f) {
      double sweep = a1 - a0;
      if (sweep <= EPS) return null;
      if (sweep >= TWO_PI - 1.0e-9) return fullCircle(c, f);
      double mid = a0 + 0.5 * sweep;
      return TwoNodeClip.arc(pt(a0, c), pt(mid, c), pt(a1, c), f);
    }

    private static CircularString fullCircle(double[] c, GeometryFactory f) {
      Coordinate[] pts = new Coordinate[] {
          pt(0.0, c), pt(0.5 * Math.PI, c), pt(Math.PI, c),
          pt(1.5 * Math.PI, c), pt(0.0, c)
      };
      return new CircularString(
          f.getCoordinateSequenceFactory().create(pts), f);
    }

    private static Coordinate pt(double a, double[] c) {
      return new Coordinate(c[0] + c[2] * Math.cos(a),
          c[1] + c[2] * Math.sin(a));
    }
  }

  private static final class Ev {
    final double a;
    final int d;
    final int which;
    Ev(double a, int d, int which) {
      this.a = a;
      this.d = d;
      this.which = which;
    }
  }

  private static final Comparator<Ev> EV = new Comparator<Ev>() {
    public int compare(Ev x, Ev y) {
      if (x.a < y.a - EPS) return -1;
      if (x.a > y.a + EPS) return 1;
      if (x.d != y.d) return y.d - x.d;
      return x.which - y.which;
    }
  };
}
