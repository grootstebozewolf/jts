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

/**
 * Analytical helpers for single circular arcs defined by three control points
 * (start, mid, end), per the SQL/MM CIRCULARSTRING model.
 */
final class CircularArcs {

  private CircularArcs() {}

  /**
   * Length of the circular arc through the three control points, i.e.
   * {@code r * theta}. The mid point disambiguates which of the two arcs through
   * the endpoints is meant, so the result is correct for arcs up to a full turn.
   * Collinear (or otherwise degenerate) triples fall back to the chord length
   * {@code |end - start|}, matching the limiting behaviour as the radius grows.
   */
  static double arcLength(double sx, double sy, double mx, double my, double ex, double ey) {
    double chord = Math.hypot(ex - sx, ey - sy);
    // 2 * signed area of (s, m, e); zero iff the three points are collinear.
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (d == 0.0) return chord;

    double s2 = sx * sx + sy * sy;
    double m2 = mx * mx + my * my;
    double e2 = ex * ex + ey * ey;
    double cx = (s2 * (my - ey) + m2 * (ey - sy) + e2 * (sy - my)) / d;
    double cy = (s2 * (ex - mx) + m2 * (sx - ex) + e2 * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (!Double.isFinite(r) || r == 0.0) return chord;

    // Central angle accumulated in the arc's rotational direction (CCW iff the
    // signed area d > 0), going start -> mid -> end. Each step is the positive
    // turn in that direction, so a sub-arc that sweeps more than pi is measured
    // the long way round (an unsigned angle-between-radii would wrongly take the
    // short way). The total is the true sweep, valid up to a full turn.
    double a0 = Math.atan2(sy - cy, sx - cx);
    double am = Math.atan2(my - cy, mx - cx);
    double ae = Math.atan2(ey - cy, ex - cx);
    boolean ccw = d > 0;
    double theta = directedSweep(a0, am, ccw) + directedSweep(am, ae, ccw);
    double len = r * theta;
    return Double.isFinite(len) ? len : chord;
  }

  /**
   * Intersection points of the circular arc through {@code (s, m, e)} with the
   * line segment {@code (p, q)} (N-AL, JTS #1195). Returns each {@code [x, y]}
   * lying on both the segment ({@code 0 <= t <= 1}) and the arc's swept span
   * (the directed sweep start->mid->end). Returns 0, 1, or 2 points; empty for a
   * tangent miss, a degenerate segment, or a collinear (non-circular) arc.
   */
  static double[][] intersectSegment(double sx, double sy, double mx, double my, double ex, double ey,
                                     double px, double py, double qx, double qy) {
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (d == 0.0) return new double[0][];               // collinear arc: no circle
    double s2 = sx * sx + sy * sy, m2 = mx * mx + my * my, e2 = ex * ex + ey * ey;
    double cx = (s2 * (my - ey) + m2 * (ey - sy) + e2 * (sy - my)) / d;
    double cy = (s2 * (ex - mx) + m2 * (sx - ex) + e2 * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (!Double.isFinite(r) || r == 0.0) return new double[0][];

    // segment X(t) = p + t*(q-p); solve |X - C|^2 = r^2
    double dx = qx - px, dy = qy - py;
    double a = dx * dx + dy * dy;
    if (a == 0.0) return new double[0][];               // degenerate segment
    double fx = px - cx, fy = py - cy;
    double bb = 2 * (fx * dx + fy * dy);
    double cc = fx * fx + fy * fy - r * r;
    double disc = bb * bb - 4 * a * cc;
    if (disc < 0) return new double[0][];               // line misses circle
    double sq = Math.sqrt(disc);
    double[] ts = (disc == 0.0) ? new double[]{ -bb / (2 * a) }
                                : new double[]{ (-bb - sq) / (2 * a), (-bb + sq) / (2 * a) };

    double a0 = Math.atan2(sy - cy, sx - cx);
    double am = Math.atan2(my - cy, mx - cx);
    double ae = Math.atan2(ey - cy, ex - cx);
    boolean ccw = d > 0;
    double theta = directedSweep(a0, am, ccw) + directedSweep(am, ae, ccw);

    final double EPS = 1e-9;
    double[][] out = new double[ts.length][];
    int n = 0;
    for (double t : ts) {
      if (t < -EPS || t > 1 + EPS) continue;            // off the segment
      double x = px + t * dx, y = py + t * dy;
      double sweep = directedSweep(a0, Math.atan2(y - cy, x - cx), ccw);
      // on the arc span iff 0 <= sweep <= theta (allow tiny wrap just before start)
      if (sweep <= theta + EPS || sweep >= 2 * Math.PI - EPS) {
        out[n++] = new double[]{ x, y };
      }
    }
    if (n == out.length) return out;
    double[][] trimmed = new double[n][];
    System.arraycopy(out, 0, trimmed, 0, n);
    return trimmed;
  }

  /**
   * Intersection points of the circular arc through {@code (sA, mA, eA)} with the
   * circular arc through {@code (sB, mB, eB)} (N-AA, JTS #1195). Returns each
   * {@code [x, y]} lying on both arcs' swept spans (each directed sweep
   * start-&gt;mid-&gt;end). Returns 0, 1, or 2 points; empty when the underlying
   * circles miss or are tangent off the spans, when either triple is collinear
   * (no circle), or when the crossings fall outside either span. Two arcs on the
   * same circle (concentric, including coincident) share a sub-arc rather than
   * isolated points and are reported as no intersections.
   */
  static double[][] intersectArc(double sax, double say, double max, double may, double eax, double eay,
                                 double sbx, double sby, double mbx, double mby, double ebx, double eby) {
    double dA = 2 * (sax * (may - eay) + max * (eay - say) + eax * (say - may));
    double dB = 2 * (sbx * (mby - eby) + mbx * (eby - sby) + ebx * (sby - mby));
    if (dA == 0.0 || dB == 0.0) return new double[0][];     // a collinear triple: no circle
    double a2 = sax * sax + say * say, b2 = max * max + may * may, c2 = eax * eax + eay * eay;
    double cax = (a2 * (may - eay) + b2 * (eay - say) + c2 * (say - may)) / dA;
    double cay = (a2 * (eax - max) + b2 * (sax - eax) + c2 * (max - sax)) / dA;
    double rA = Math.hypot(sax - cax, say - cay);
    double p2 = sbx * sbx + sby * sby, q2 = mbx * mbx + mby * mby, t2 = ebx * ebx + eby * eby;
    double cbx = (p2 * (mby - eby) + q2 * (eby - sby) + t2 * (sby - mby)) / dB;
    double cby = (p2 * (ebx - mbx) + q2 * (sbx - ebx) + t2 * (mbx - sbx)) / dB;
    double rB = Math.hypot(sbx - cbx, sby - cby);
    if (!Double.isFinite(rA) || !Double.isFinite(rB) || rA == 0.0 || rB == 0.0) return new double[0][];

    double dx = cbx - cax, dy = cby - cay;
    double dd = Math.hypot(dx, dy);
    final double EPS = 1e-9;
    if (dd == 0.0) return new double[0][];                  // concentric / coincident: no isolated points
    if (dd > rA + rB + EPS || dd < Math.abs(rA - rB) - EPS) return new double[0][];   // circles miss

    // radical line: |X-CA|^2 = rA^2, |X-CB|^2 = rB^2 -> X = mid +/- h * perp
    double a = (rA * rA - rB * rB + dd * dd) / (2 * dd);
    double h2 = rA * rA - a * a;
    double h = h2 > 0 ? Math.sqrt(h2) : 0.0;                // h2 ~ 0: tangent (single point)
    double mx = cax + a * dx / dd, my = cay + a * dy / dd;
    double[][] cand = (h == 0.0)
        ? new double[][]{ { mx - h * dy / dd, my + h * dx / dd } }
        : new double[][]{ { mx - h * dy / dd, my + h * dx / dd }, { mx + h * dy / dd, my - h * dx / dd } };

    double aa0 = Math.atan2(say - cay, sax - cax);
    double aam = Math.atan2(may - cay, max - cax);
    double aae = Math.atan2(eay - cay, eax - cax);
    boolean accw = dA > 0;
    double thetaA = directedSweep(aa0, aam, accw) + directedSweep(aam, aae, accw);
    double ba0 = Math.atan2(sby - cby, sbx - cbx);
    double bam = Math.atan2(mby - cby, mbx - cbx);
    double bae = Math.atan2(eby - cby, ebx - cbx);
    boolean bccw = dB > 0;
    double thetaB = directedSweep(ba0, bam, bccw) + directedSweep(bam, bae, bccw);

    double[][] out = new double[cand.length][];
    int n = 0;
    for (double[] pt : cand) {
      double swA = directedSweep(aa0, Math.atan2(pt[1] - cay, pt[0] - cax), accw);
      if (!(swA <= thetaA + EPS || swA >= 2 * Math.PI - EPS)) continue;
      double swB = directedSweep(ba0, Math.atan2(pt[1] - cby, pt[0] - cbx), bccw);
      if (!(swB <= thetaB + EPS || swB >= 2 * Math.PI - EPS)) continue;
      out[n++] = pt;
    }
    if (n == out.length) return out;
    double[][] trimmed = new double[n][];
    System.arraycopy(out, 0, trimmed, 0, n);
    return trimmed;
  }

  /**
   * Minimum distance between the circular arc through {@code (sA, mA, eA)} and the
   * arc through {@code (sB, mB, eB)} (D-AA, JTS #1195). Zero when the arcs
   * intersect; otherwise the smallest gap, taken over each arc's endpoints
   * projected onto the other arc and the interior closest approach along the line
   * of centres (each clamped to both swept spans). Concentric arcs whose spans
   * overlap in direction are {@code |rA - rB|} apart. A collinear (non-circular)
   * triple falls back to its chord endpoints.
   */
  static double distanceArcToArc(double sax, double say, double max, double may, double eax, double eay,
                                 double sbx, double sby, double mbx, double mby, double ebx, double eby) {
    if (intersectArc(sax,say,max,may,eax,eay, sbx,sby,mbx,mby,ebx,eby).length > 0) return 0.0;
    double[] cA = circleParams(sax,say,max,may,eax,eay);
    double[] cB = circleParams(sbx,sby,mbx,mby,ebx,eby);

    double best = Double.POSITIVE_INFINITY;
    // each arc's endpoints projected onto the other arc (or the other chord if degenerate)
    best = Math.min(best, pointToArcOrChord(cB, sbx,sby,ebx,eby, sax,say));
    best = Math.min(best, pointToArcOrChord(cB, sbx,sby,ebx,eby, eax,eay));
    best = Math.min(best, pointToArcOrChord(cA, sax,say,eax,eay, sbx,sby));
    best = Math.min(best, pointToArcOrChord(cA, sax,say,eax,eay, ebx,eby));

    if (cA != null && cB != null) {
      double dd = Math.hypot(cB[0] - cA[0], cB[1] - cA[1]);
      if (dd > 1e-12) {
        double ux = (cB[0] - cA[0]) / dd, uy = (cB[1] - cA[1]) / dd;
        // interior critical points are radially aligned -> on the line of centres
        for (int sa = -1; sa <= 1; sa += 2) {
          double pax = cA[0] + sa * cA[2] * ux, pay = cA[1] + sa * cA[2] * uy;
          if (!onSpan(cA, pax, pay)) continue;
          for (int sb = -1; sb <= 1; sb += 2) {
            double pbx = cB[0] + sb * cB[2] * ux, pby = cB[1] + sb * cB[2] * uy;
            if (!onSpan(cB, pbx, pby)) continue;
            best = Math.min(best, Math.hypot(pax - pbx, pay - pby));
          }
        }
      } else if (spansOverlap(cA, cB)) {
        best = Math.min(best, Math.abs(cA[2] - cB[2]));   // concentric, overlapping in direction
      }
    }
    return best;
  }

  /**
   * Minimum distance between the circular arc through {@code (s, m, e)} and the
   * line segment {@code (p, q)} (D-AA family, JTS #1195). Zero when they
   * intersect; otherwise the smallest gap over the arc endpoints projected onto
   * the segment, the segment endpoints projected onto the arc, and the interior
   * closest approach (the circle points whose tangent is parallel to the segment,
   * clamped to the arc sweep and the segment extent). A collinear (non-circular)
   * triple falls back to its chord segment.
   */
  static double distanceArcToSegment(double sx, double sy, double mx, double my, double ex, double ey,
                                     double px, double py, double qx, double qy) {
    if (intersectSegment(sx,sy,mx,my,ex,ey, px,py, qx,qy).length > 0) return 0.0;
    double[] c = circleParams(sx,sy,mx,my,ex,ey);
    if (c == null) {                                   // degenerate arc -> chord segment
      return Math.min(
          Math.min(distancePointToSegment(sx,sy, px,py,qx,qy), distancePointToSegment(ex,ey, px,py,qx,qy)),
          Math.min(distancePointToSegment(px,py, sx,sy,ex,ey), distancePointToSegment(qx,qy, sx,sy,ex,ey)));
    }
    double best = Math.min(distancePointToSegment(sx,sy, px,py,qx,qy),
                           distancePointToSegment(ex,ey, px,py,qx,qy));
    best = Math.min(best, pointToArcOrChord(c, sx,sy,ex,ey, px,py));
    best = Math.min(best, pointToArcOrChord(c, sx,sy,ex,ey, qx,qy));
    double dx = qx - px, dy = qy - py, len = Math.hypot(dx, dy);
    if (len > 0.0) {
      double nx = -dy / len, ny = dx / len;            // unit normal to the segment
      for (int s = -1; s <= 1; s += 2) {
        double ax = c[0] + s * c[2] * nx, ay = c[1] + s * c[2] * ny;
        if (onSpan(c, ax, ay)) best = Math.min(best, distancePointToSegment(ax, ay, px,py, qx,qy));
      }
    }
    return best;
  }

  /** Circle of an arc as {cx, cy, r, startAngle, signedSweep}, or null if collinear/degenerate. */
  private static double[] circleParams(double sx, double sy, double mx, double my, double ex, double ey) {
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (d == 0.0) return null;
    double s2 = sx*sx+sy*sy, m2 = mx*mx+my*my, e2 = ex*ex+ey*ey;
    double cx = (s2*(my-ey) + m2*(ey-sy) + e2*(sy-my)) / d;
    double cy = (s2*(ex-mx) + m2*(sx-ex) + e2*(mx-sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (!Double.isFinite(r) || r == 0.0) return null;
    double a0 = Math.atan2(sy-cy, sx-cx);
    double am = Math.atan2(my-cy, mx-cx);
    double ae = Math.atan2(ey-cy, ex-cx);
    boolean ccw = d > 0;
    double theta = directedSweep(a0, am, ccw) + directedSweep(am, ae, ccw);
    return new double[]{ cx, cy, r, a0, ccw ? theta : -theta };
  }

  /** Is the point on the arc's swept span (its foot already known to be on the circle)? */
  private static boolean onSpan(double[] c, double px, double py) {
    boolean ccw = c[4] >= 0;
    double sw = directedSweep(c[3], Math.atan2(py - c[1], px - c[0]), ccw);
    return sw <= Math.abs(c[4]) + 1e-9 || sw >= 2 * Math.PI - 1e-9;
  }

  /** Distance from a point to an arc (radial foot if on span, else nearer endpoint); chord if degenerate. */
  private static double pointToArcOrChord(double[] c, double sx, double sy, double ex, double ey,
                                          double px, double py) {
    if (c == null) return distancePointToSegment(px, py, sx, sy, ex, ey);
    double dd = Math.hypot(px - c[0], py - c[1]);
    if (dd != 0.0) {
      double fx = c[0] + (px - c[0]) / dd * c[2], fy = c[1] + (py - c[1]) / dd * c[2];
      if (onSpan(c, fx, fy)) return Math.abs(dd - c[2]);
    }
    return Math.min(Math.hypot(px - sx, py - sy), Math.hypot(px - ex, py - ey));
  }

  /** Whether the two arcs' angular spans overlap (positive measure); used for concentric arcs. */
  private static boolean spansOverlap(double[] cA, double[] cB) {
    double lo1 = cA[4] >= 0 ? cA[3] : cA[3] + cA[4], len1 = Math.abs(cA[4]);
    double lo2 = cB[4] >= 0 ? cB[3] : cB[3] + cB[4], len2 = Math.abs(cB[4]);
    double twoPi = 2 * Math.PI;
    double s = (((lo2 - lo1) % twoPi) + twoPi) % twoPi;
    double ov = Math.max(0, Math.min(len1, s + len2) - Math.max(0, s))
              + Math.max(0, Math.min(len1, s + len2 - twoPi) - Math.max(0, s - twoPi));
    return ov > 1e-9;
  }

  /** Distance from {@code (px,py)} to the segment {@code (ax,ay)-(bx,by)}. */
  private static double distancePointToSegment(double px, double py, double ax, double ay, double bx, double by) {
    double dx = bx - ax, dy = by - ay, l2 = dx*dx + dy*dy;
    if (l2 == 0.0) return Math.hypot(px - ax, py - ay);
    double t = ((px - ax) * dx + (py - ay) * dy) / l2;
    if (t < 0) t = 0; else if (t > 1) t = 1;
    return Math.hypot(px - (ax + t*dx), py - (ay + t*dy));
  }

  /** Positive angular turn from {@code from} to {@code to} in the given direction, in [0, 2*pi). */
  private static double directedSweep(double from, double to, boolean ccw) {
    double t = ccw ? (to - from) : (from - to);
    double twoPi = 2 * Math.PI;
    t %= twoPi;
    if (t < 0) t += twoPi;
    return t;
  }
}
