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
 * Disjointness of two closed curved rings (V-CP holes-disjoint building block,
 * JTS #1195). Each ring is a control-point sequence read as consecutive arc
 * pieces (a collinear triple is a chord). Two rings are {@link Relation#DISJOINT}
 * unless their boundaries cross ({@link Relation#CROSS}) or one is nested inside
 * the other ({@link Relation#A_IN_B} / {@link Relation#B_IN_A}) — the relations a
 * valid {@code CurvePolygon}'s holes must avoid with each other.
 */
final class ArcRingRelation {

  private ArcRingRelation() {}

  enum Relation { DISJOINT, CROSS, A_IN_B, B_IN_A }

  static Relation relate(CoordinateSequence a, CoordinateSequence b) {
    if (boundariesCross(a, b)) return Relation.CROSS;
    // no crossing: either disjoint or one nested in the other (test a vertex)
    if (ArcRingLocation.isInteriorPoint(a, b.getX(0), b.getY(0))) return Relation.B_IN_A;
    if (ArcRingLocation.isInteriorPoint(b, a.getX(0), a.getY(0))) return Relation.A_IN_B;
    return Relation.DISJOINT;
  }

  /** True if any piece of ring {@code a} crosses any piece of ring {@code b}. */
  private static boolean boundariesCross(CoordinateSequence a, CoordinateSequence b) {
    int na = a.size(), nb = b.size();
    for (int i = 0; i + 2 < na; i += 2) {
      double[] pa = piece(a, i);
      for (int j = 0; j + 2 < nb; j += 2) {
        if (piecesIntersect(pa, piece(b, j))) return true;
      }
    }
    return false;
  }

  private static double[] piece(CoordinateSequence s, int i) {
    return new double[]{ s.getX(i), s.getY(i), s.getX(i+1), s.getY(i+1), s.getX(i+2), s.getY(i+2) };
  }

  private static boolean isArc(double[] p) {
    return 2 * (p[0]*(p[3]-p[5]) + p[2]*(p[5]-p[1]) + p[4]*(p[1]-p[3])) != 0.0;
  }

  private static boolean piecesIntersect(double[] a, double[] b) {
    boolean aa = isArc(a), ba = isArc(b);
    if (aa && ba) return arcArc(a, b);
    if (aa)       return arcSeg(a, b[0], b[1], b[4], b[5]);
    if (ba)       return arcSeg(b, a[0], a[1], a[4], a[5]);
    return segSeg(a[0],a[1],a[4],a[5], b[0],b[1],b[4],b[5]);
  }

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
    if (Math.abs(d) < 1e-12) return false;     // parallel/collinear (overlap not a transversal cross)
    double t=((x3-x1)*(y4-y3)-(y3-y1)*(x4-x3))/d;
    double u=((x3-x1)*(y2-y1)-(y3-y1)*(x2-x1))/d;
    return t >= -1e-9 && t <= 1+1e-9 && u >= -1e-9 && u <= 1+1e-9;
  }

  private static double sweep(double from, double to, boolean ccw) {
    double t = ccw ? (to - from) : (from - to);
    t %= 2 * Math.PI;
    if (t < 0) t += 2 * Math.PI;
    return t;
  }
}
