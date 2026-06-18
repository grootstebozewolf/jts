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

import org.locationtech.jts.geom.CoordinateSequence;

/**
 * Arc-aware topological relate predicates for two simple closed curved areal
 * rings (R-PR, JTS #1195). Each ring is a closed control-point sequence read as
 * consecutive arc pieces {@code (p[2i], p[2i+1], p[2i+2])} (a collinear triple is
 * a straight chord), bounding a filled region (its interior is the ray-cast
 * inside of {@link ArcRingLocation}).
 * <p>
 * The predicates are the OGC named relations — {@code intersects}, {@code disjoint},
 * {@code contains}, {@code within}, {@code covers}, {@code coveredBy},
 * {@code equals}, {@code overlaps}, {@code touches} — and reproduce the genuine
 * TRUE-OGC DE-9IM of the oracle {@code CURVE_RELATE_MATRIX} mode (verified
 * cell-derived against its curated matrices and by a densified cross-check). They
 * are computed exactly where it matters, with no densification of the regions:
 * <ul>
 *   <li>boundary contact (including measure-zero tangencies that decide
 *       {@code touches}) is detected by exact arc/arc, arc/segment and
 *       segment/segment piece intersection (the {@link ArcRingRelation} kernels);</li>
 *   <li>the 2-D interior relationships (containment, overlap) are decided by
 *       classifying dense boundary sample points of one ring against the other
 *       via the arc-aware {@link ArcRingLocation#isInteriorPoint} ray cast — a
 *       transversal crossing puts a whole sub-arc inside, an isolated tangency
 *       does not, so the generic samples separate {@code overlaps} from
 *       {@code touches} robustly.</li>
 * </ul>
 * Scope: a single closed ring per geometry (shells). Multi-ring {@code CurvePolygon}
 * (holes) and the full 9-cell matrix string are a documented follow-on.
 */
final class ArcRelate {

  private ArcRelate() {}

  private static final double EPS = 1e-7;
  // Interior samples per boundary piece. The 2-D interior relationships are
  // decided by whether a boundary sub-arc of one ring falls inside the other; a
  // transversal crossing always traps a positive-width sub-arc, so a sufficiently
  // fine grid cannot fall entirely between samples. 24/piece (7.5 deg on a
  // semicircle) comfortably resolves the lenses in scope.
  private static final int SAMPLES_PER_PIECE = 24;

  // point location of (x,y) relative to a region bounded by ring
  private static final int OUT = 0, ON = 1, IN = 2;

  // ---- public predicates (A, B as closed curved control-point rings) ----
  //
  // All predicates follow the TRUE-OGC DE-9IM patterns, decided from three facts
  // about two simple areal shells:
  //   bi          : boundaries meet at all (exact, catches measure-zero tangency)
  //   interiorsShareArea(A,B)  (II=2): regions overlap on positive area
  //   outBA / outAB : part of one boundary lies strictly outside the other region
  //                   (EI=2 / IE=2 — the other interior pokes outside)
  // From these: contains = II & EI=F ; within = II & IE=F ; overlaps = II & IE & EI ;
  // touches = bi & !II ; disjoint = !bi & !II ; equals = boundaries mutually on.

  static boolean equals(CoordinateSequence a, CoordinateSequence b) {
    Counts ba = classify(b, a), ab = classify(a, b);
    // every sample of each boundary lies on the other, and both have contact
    return ba.in == 0 && ba.out == 0 && ba.on > 0
        && ab.in == 0 && ab.out == 0 && ab.on > 0;
  }

  /** A contains B (reflexive: also true when equal): interiors share area and no part of B lies outside A. */
  static boolean contains(CoordinateSequence a, CoordinateSequence b) {
    return interiorsShareArea(a, b) && classify(b, a).out == 0;   // II && EI=F
  }

  static boolean within(CoordinateSequence a, CoordinateSequence b) {
    return contains(b, a);
  }

  static boolean covers(CoordinateSequence a, CoordinateSequence b) {
    return contains(a, b);                       // for areal shells covers coincides with contains
  }

  static boolean coveredBy(CoordinateSequence a, CoordinateSequence b) {
    return contains(b, a);
  }

  /** Interiors overlap on an area and each pokes outside the other (areal/areal OVERLAP). */
  static boolean overlaps(CoordinateSequence a, CoordinateSequence b) {
    if (!interiorsShareArea(a, b)) return false;             // II=2
    return classify(a, b).out > 0 && classify(b, a).out > 0; // IE=2 && EI=2
  }

  static boolean disjoint(CoordinateSequence a, CoordinateSequence b) {
    return !boundariesIntersect(a, b) && !interiorsShareArea(a, b);
  }

  static boolean intersects(CoordinateSequence a, CoordinateSequence b) {
    return !disjoint(a, b);
  }

  /** Boundaries meet but interiors share no area (external tangency / edge contact). */
  static boolean touches(CoordinateSequence a, CoordinateSequence b) {
    return boundariesIntersect(a, b) && !interiorsShareArea(a, b);
  }

  /** II=2: the two regions overlap on positive area (shared interior). */
  private static boolean interiorsShareArea(CoordinateSequence a, CoordinateSequence b) {
    // a transversal crossing or nesting puts a whole boundary sub-arc of one
    // strictly inside the other; equal rings share their whole interior.
    return classify(b, a).in > 0 || classify(a, b).in > 0 || equals(a, b);
  }

  // ---- boundary-sample classification ----

  private static final class Counts { int in, on, out; }

  /** Classify dense interior sample points of ring {@code edge} against the region of {@code region}. */
  private static Counts classify(CoordinateSequence edge, CoordinateSequence region) {
    Counts c = new Counts();
    int n = edge.size();
    for (int i = 0; i + 2 < n; i += 2) {
      double[] pc = piece(edge, i);
      for (int k = 1; k < SAMPLES_PER_PIECE; k++) {
        double[] pt = pointOnPiece(pc, (double) k / SAMPLES_PER_PIECE);
        switch (locate(region, pt[0], pt[1])) {
          case IN:  c.in++;  break;
          case ON:  c.on++;  break;
          default:  c.out++;
        }
      }
    }
    return c;
  }

  private static int locate(CoordinateSequence ring, double x, double y) {
    if (onBoundary(ring, x, y)) return ON;
    return ArcRingLocation.isInteriorPoint(ring, x, y) ? IN : OUT;
  }

  // ---- geometry: piece extraction, sampling, on-boundary, exact intersection ----

  private static double[] piece(CoordinateSequence s, int i) {
    return new double[]{ s.getX(i), s.getY(i), s.getX(i+1), s.getY(i+1), s.getX(i+2), s.getY(i+2) };
  }

  private static boolean isArc(double[] p) {
    return 2 * (p[0]*(p[3]-p[5]) + p[2]*(p[5]-p[1]) + p[4]*(p[1]-p[3])) != 0.0;
  }

  /** Point at parameter {@code t in (0,1)} along a piece (sweep fraction for an arc, lerp for a chord). */
  private static double[] pointOnPiece(double[] p, double t) {
    double[] c = circle(p);
    if (c == null) {                                  // chord s -> e
      return new double[]{ p[0] + t*(p[4]-p[0]), p[1] + t*(p[5]-p[1]) };
    }
    boolean ccw = c[4] >= 0;
    double a0 = Math.atan2(p[1]-c[1], p[0]-c[0]);
    double ang = a0 + (ccw ? 1 : -1) * Math.abs(c[4]) * t;
    return new double[]{ c[0] + c[2]*Math.cos(ang), c[1] + c[2]*Math.sin(ang) };
  }

  private static boolean onBoundary(CoordinateSequence ring, double x, double y) {
    int n = ring.size();
    for (int i = 0; i + 2 < n; i += 2) {
      double[] p = piece(ring, i);
      double[] c = circle(p);
      if (c == null) {                                // chord
        if (distToSeg(x, y, p[0], p[1], p[4], p[5]) <= EPS) return true;
      } else {
        if (Math.abs(Math.hypot(x - c[0], y - c[1]) - c[2]) <= EPS && onSpan(c, x, y)) return true;
      }
    }
    return false;
  }

  /** True if any boundary piece of {@code a} intersects any boundary piece of {@code b} (exact). */
  private static boolean boundariesIntersect(CoordinateSequence a, CoordinateSequence b) {
    int na = a.size(), nb = b.size();
    for (int i = 0; i + 2 < na; i += 2) {
      double[] pa = piece(a, i);
      for (int j = 0; j + 2 < nb; j += 2) {
        if (piecesIntersect(pa, piece(b, j))) return true;
      }
    }
    return false;
  }

  private static boolean piecesIntersect(double[] a, double[] b) {
    boolean aa = isArc(a), ba = isArc(b);
    if (aa && ba) return arcArc(a, b);
    if (aa)       return arcSeg(a, b[0], b[1], b[4], b[5]);
    if (ba)       return arcSeg(b, a[0], a[1], a[4], a[5]);
    return segSeg(a[0],a[1],a[4],a[5], b[0],b[1],b[4],b[5]);
  }

  // {cx, cy, r, a0, signedSweep} or null if collinear/degenerate
  private static double[] circle(double[] p) {
    double sx=p[0],sy=p[1],mx=p[2],my=p[3],ex=p[4],ey=p[5];
    double d = 2 * (sx*(my-ey) + mx*(ey-sy) + ex*(sy-my));
    if (d == 0.0) return null;
    double s2=sx*sx+sy*sy, m2=mx*mx+my*my, e2=ex*ex+ey*ey;
    double cx=(s2*(my-ey)+m2*(ey-sy)+e2*(sy-my))/d;
    double cy=(s2*(ex-mx)+m2*(sx-ex)+e2*(mx-sx))/d;
    double r=Math.hypot(sx-cx,sy-cy);
    if (!Double.isFinite(r) || r==0.0) return null;
    double a0=Math.atan2(sy-cy,sx-cx), am=Math.atan2(my-cy,mx-cx), ae=Math.atan2(ey-cy,ex-cx);
    boolean ccw=d>0;
    double theta=sweep(a0,am,ccw)+sweep(am,ae,ccw);
    return new double[]{ cx, cy, r, a0, ccw?theta:-theta };
  }

  private static boolean onSpan(double[] c, double px, double py) {
    double sw = sweep(c[3], Math.atan2(py-c[1], px-c[0]), c[4] >= 0);
    return sw <= Math.abs(c[4]) + 1e-9 || sw >= 2*Math.PI - 1e-9;
  }

  private static boolean arcArc(double[] a, double[] b) {
    double[] ca = circle(a), cb = circle(b);
    if (ca == null || cb == null) return false;
    double dx=cb[0]-ca[0], dy=cb[1]-ca[1], dd=Math.hypot(dx,dy);
    double rA=ca[2], rB=cb[2];
    if (dd == 0.0 || dd > rA+rB+1e-9 || dd < Math.abs(rA-rB)-1e-9) return false;
    double aa=(rA*rA-rB*rB+dd*dd)/(2*dd), h2=rA*rA-aa*aa, h=h2>0?Math.sqrt(h2):0.0;
    double mx=ca[0]+aa*dx/dd, my=ca[1]+aa*dy/dd;
    for (int s=-1; s<=1; s+=2) {
      double x=mx - s*h*dy/dd, y=my + s*h*dx/dd;
      if (onSpan(ca,x,y) && onSpan(cb,x,y)) return true;
      if (h == 0.0) break;
    }
    return false;
  }

  private static boolean arcSeg(double[] arc, double px, double py, double qx, double qy) {
    double[] c = circle(arc);
    if (c == null) return false;
    double cx=c[0], cy=c[1], r=c[2];
    double dx=qx-px, dy=qy-py, A=dx*dx+dy*dy;
    if (A == 0.0) return false;
    double fx=px-cx, fy=py-cy, B=2*(fx*dx+fy*dy), C=fx*fx+fy*fy-r*r, disc=B*B-4*A*C;
    if (disc < 0) return false;
    double sq=Math.sqrt(disc);
    double[] ts = (disc==0.0) ? new double[]{ -B/(2*A) } : new double[]{ (-B-sq)/(2*A), (-B+sq)/(2*A) };
    for (double t : ts) {
      if (t < -1e-9 || t > 1+1e-9) continue;
      double x=px+t*dx, y=py+t*dy;
      if (onSpan(c, x, y)) return true;
    }
    return false;
  }

  private static boolean segSeg(double x1,double y1,double x2,double y2, double x3,double y3,double x4,double y4) {
    double d=(x2-x1)*(y4-y3)-(y2-y1)*(x4-x3);
    if (Math.abs(d) < 1e-12) return false;
    double t=((x3-x1)*(y4-y3)-(y3-y1)*(x4-x3))/d;
    double u=((x3-x1)*(y2-y1)-(y3-y1)*(x2-x1))/d;
    return t >= -1e-9 && t <= 1+1e-9 && u >= -1e-9 && u <= 1+1e-9;
  }

  private static double distToSeg(double px,double py, double x1,double y1, double x2,double y2) {
    double dx=x2-x1, dy=y2-y1, l2=dx*dx+dy*dy;
    if (l2 == 0.0) return Math.hypot(px-x1, py-y1);
    double t=((px-x1)*dx+(py-y1)*dy)/l2;
    t = Math.max(0, Math.min(1, t));
    return Math.hypot(px-(x1+t*dx), py-(y1+t*dy));
  }

  private static double sweep(double from, double to, boolean ccw) {
    double t = ccw ? (to - from) : (from - to);
    t %= 2 * Math.PI;
    if (t < 0) t += 2 * Math.PI;
    return t;
  }
}
