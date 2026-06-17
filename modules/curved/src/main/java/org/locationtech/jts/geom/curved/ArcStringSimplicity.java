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
package org.locationtech.jts.geom.curved;

import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;

/**
 * Arc-aware simplicity for a {@link CircularString} (V-CS, JTS #1195).
 * <p>
 * The control points are read as consecutive arc pieces
 * {@code (p[2i], p[2i+1], p[2i+2])}. A collinear triple degrades to its chord
 * segment. The curve is <i>simple</i> when no two pieces meet anywhere except at
 * a shared adjacency endpoint (and, for a closed curve, the single shared
 * closing endpoint of the first and last pieces) — i.e. no crossing, no
 * tangential self-touch, and no overlapping run.
 * <p>
 * Pairwise crossings reuse the oracle-pinned primitives in {@link CircularArcs}
 * ({@code intersectArc} for arc/arc, {@code intersectSegment} for arc/segment)
 * and {@link RobustLineIntersector} for segment/segment (which also reports a
 * collinear overlap as its two endpoints). Two arcs sharing the same circle
 * produce no isolated intersection points, so their angular spans are tested for
 * positive-measure overlap explicitly.
 */
final class ArcStringSimplicity {

  private ArcStringSimplicity() {}

  private static final double EPS = 1e-7;

  static boolean isSimple(CoordinateSequence seq) {
    int n = seq.size();
    int nArcs = (n - 1) / 2;
    boolean closed = same(seq.getX(0), seq.getY(0), seq.getX(n - 1), seq.getY(n - 1));

    for (int i = 0; i < nArcs; i++) {
      for (int k = i + 1; k < nArcs; k++) {
        Coordinate[] allowed = sharedEndpoints(seq, n, i, k, nArcs, closed);
        for (double[] p : pieceIntersections(seq, i, k)) {
          if (!isAllowed(p, allowed)) return false;
        }
        if (piecesOverlap(seq, i, k)) return false;
      }
    }
    return true;
  }

  /** Intersection points common to arc/segment pieces i and k. */
  private static double[][] pieceIntersections(CoordinateSequence seq, int i, int k) {
    double[] a = piece(seq, i), b = piece(seq, k);
    boolean ai = isArc(a), bi = isArc(b);
    if (ai && bi) {
      return CircularArcs.intersectArc(a[0],a[1],a[2],a[3],a[4],a[5], b[0],b[1],b[2],b[3],b[4],b[5]);
    }
    if (ai) {
      return CircularArcs.intersectSegment(a[0],a[1],a[2],a[3],a[4],a[5], b[0],b[1], b[4],b[5]);
    }
    if (bi) {
      return CircularArcs.intersectSegment(b[0],b[1],b[2],b[3],b[4],b[5], a[0],a[1], a[4],a[5]);
    }
    // segment / segment: a proper or endpoint crossing is one point (checked
    // against the allowed endpoints); a collinear overlap is two points and is
    // handled as a positive-measure overlap in piecesOverlap, so ignore it here.
    RobustLineIntersector li = new RobustLineIntersector();
    li.computeIntersection(new Coordinate(a[0],a[1]), new Coordinate(a[4],a[5]),
                           new Coordinate(b[0],b[1]), new Coordinate(b[4],b[5]));
    if (li.getIntersectionNum() != 1) return new double[0][];
    Coordinate c = li.getIntersection(0);
    return new double[][]{ { c.x, c.y } };
  }

  /**
   * True if pieces i and k share a positive-measure run rather than meeting only
   * at points: arcs on the same circle whose angular spans overlap, or collinear
   * segments whose extents overlap.
   */
  private static boolean piecesOverlap(CoordinateSequence seq, int i, int k) {
    double[] a = piece(seq, i), b = piece(seq, k);
    boolean ai = isArc(a), bi = isArc(b);
    if (ai && bi) {
      double[] ca = circle(a), cb = circle(b);
      if (ca == null || cb == null) return false;
      if (Math.hypot(ca[0] - cb[0], ca[1] - cb[1]) > EPS || Math.abs(ca[2] - cb[2]) > EPS) return false;
      return circularOverlap(ca[3], ca[4], cb[3], cb[4]) > EPS;
    }
    if (!ai && !bi) {
      RobustLineIntersector li = new RobustLineIntersector();
      li.computeIntersection(new Coordinate(a[0],a[1]), new Coordinate(a[4],a[5]),
                             new Coordinate(b[0],b[1]), new Coordinate(b[4],b[5]));
      if (li.getIntersectionNum() != 2) return false;       // collinear overlap run
      Coordinate p0 = li.getIntersection(0), p1 = li.getIntersection(1);
      return Math.hypot(p0.x - p1.x, p0.y - p1.y) > EPS;
    }
    return false;   // a straight segment cannot run along a circular arc
  }

  /** Length of the angular overlap of two CCW arcs given as (startAngle, signedSweep). */
  private static double circularOverlap(double a0, double ta, double b0, double tb) {
    double lo1 = ta >= 0 ? a0 : a0 + ta;          // CCW lower bound
    double len1 = Math.abs(ta);
    double lo2 = tb >= 0 ? b0 : b0 + tb;
    double len2 = Math.abs(tb);
    double twoPi = 2 * Math.PI;
    double s = ((lo2 - lo1) % twoPi + twoPi) % twoPi;   // shift so arc1 starts at 0
    // arc1 = [0, len1]; arc2 = [s, s+len2] and its wrap [s-2pi, s+len2-2pi]
    double ov = Math.max(0, Math.min(len1, s + len2) - Math.max(0, s))
              + Math.max(0, Math.min(len1, s + len2 - twoPi) - Math.max(0, s - twoPi));
    return ov;
  }

  private static Coordinate[] sharedEndpoints(CoordinateSequence seq, int n, int i, int k,
                                              int nArcs, boolean closed) {
    boolean adjacent = (k == i + 1);
    boolean closing = closed && i == 0 && k == nArcs - 1;
    int c = (adjacent ? 1 : 0) + (closing ? 1 : 0);
    Coordinate[] out = new Coordinate[c];
    int idx = 0;
    if (adjacent) out[idx++] = new Coordinate(seq.getX(2 * i + 2), seq.getY(2 * i + 2));
    if (closing)  out[idx++] = new Coordinate(seq.getX(0), seq.getY(0));
    return out;
  }

  private static boolean isAllowed(double[] p, Coordinate[] allowed) {
    for (Coordinate c : allowed)
      if (same(p[0], p[1], c.x, c.y)) return true;
    return false;
  }

  private static double[] piece(CoordinateSequence seq, int i) {
    int b = 2 * i;
    return new double[]{ seq.getX(b), seq.getY(b), seq.getX(b+1), seq.getY(b+1), seq.getX(b+2), seq.getY(b+2) };
  }

  private static boolean isArc(double[] p) {
    return 2 * (p[0]*(p[3]-p[5]) + p[2]*(p[5]-p[1]) + p[4]*(p[1]-p[3])) != 0.0;
  }

  /** Circle of an arc piece as {cx, cy, r, startAngle, signedSweep}, or null if degenerate. */
  private static double[] circle(double[] p) {
    double sx=p[0],sy=p[1],mx=p[2],my=p[3],ex=p[4],ey=p[5];
    double d = 2 * (sx*(my-ey) + mx*(ey-sy) + ex*(sy-my));
    if (d == 0.0) return null;
    double s2=sx*sx+sy*sy, m2=mx*mx+my*my, e2=ex*ex+ey*ey;
    double cx=(s2*(my-ey)+m2*(ey-sy)+e2*(sy-my))/d;
    double cy=(s2*(ex-mx)+m2*(sx-ex)+e2*(mx-sx))/d;
    double r=Math.hypot(sx-cx,sy-cy);
    if (!Double.isFinite(r) || r == 0.0) return null;
    double a0=Math.atan2(sy-cy,sx-cx);
    double am=Math.atan2(my-cy,mx-cx);
    double ae=Math.atan2(ey-cy,ex-cx);
    boolean ccw = d > 0;
    double theta = sweep(a0,am,ccw) + sweep(am,ae,ccw);
    return new double[]{ cx, cy, r, a0, ccw ? theta : -theta };
  }

  private static double sweep(double from, double to, boolean ccw) {
    double t = ccw ? (to - from) : (from - to);
    t %= 2 * Math.PI;
    if (t < 0) t += 2 * Math.PI;
    return t;
  }

  private static boolean same(double x1, double y1, double x2, double y2) {
    return Math.hypot(x1 - x2, y1 - y2) <= EPS;
  }
}
