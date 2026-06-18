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
 * Arc-aware point-in-ring location (V-CP / R-CONT building block, JTS #1195).
 * <p>
 * The ring is a closed curved control-point sequence read as consecutive arc
 * pieces {@code (p[2i], p[2i+1], p[2i+2])} (a collinear triple is a straight
 * chord). {@link #isInteriorPoint} casts a horizontal ray to {@code +x} and
 * counts boundary crossings — for a chord the usual half-open segment rule, for
 * an arc the points where the scan line {@code y = qy} meets the circle that lie
 * on the arc's swept span and to the right of the query — returning {@code true}
 * for a strictly interior point (an odd crossing count).
 * <p>
 * As with any horizontal ray-cast, a query whose {@code y} exactly equals a ring
 * vertex's {@code y} grazes that vertex and is degenerate; callers locating
 * interior points (e.g. holes-in-shell tests) choose points in general position.
 */
final class ArcRingLocation {

  private ArcRingLocation() {}

  /**
   * Strictly-interior test, robust to the horizontal-ray vertex-scanline
   * degeneracy: when the query y aligns with a ring vertex y the scan line grazes
   * the vertex (and a shared vertex of two arcs can be double-counted within the
   * sweep tolerance). Probe just above and below the query; a strictly
   * interior/exterior point agrees on both. On disagreement the query lies within
   * the offset of the boundary, so it is not strictly interior.
   */
  static boolean isInteriorPoint(CoordinateSequence ring, double qx, double qy) {
    boolean above = rayParityOdd(ring, qx, qy + 1e-6);
    boolean below = rayParityOdd(ring, qx, qy - 1e-6);
    return above == below && above;
  }

  private static boolean rayParityOdd(CoordinateSequence ring, double qx, double qy) {
    int n = ring.size();
    int crossings = 0;
    for (int i = 0; i + 2 < n; i += 2) {
      double sx = ring.getX(i),     sy = ring.getY(i);
      double mx = ring.getX(i + 1), my = ring.getY(i + 1);
      double ex = ring.getX(i + 2), ey = ring.getY(i + 2);
      double det = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
      if (det == 0.0) {                                 // straight chord s -> e
        if ((sy > qy) != (ey > qy)) {
          double xint = sx + (qy - sy) / (ey - sy) * (ex - sx);
          if (xint > qx) crossings++;
        }
        continue;
      }
      double s2 = sx*sx+sy*sy, m2 = mx*mx+my*my, e2 = ex*ex+ey*ey;
      double cx = (s2*(my-ey) + m2*(ey-sy) + e2*(sy-my)) / det;
      double cy = (s2*(ex-mx) + m2*(sx-ex) + e2*(mx-sx)) / det;
      double r = Math.hypot(sx - cx, sy - cy);
      double disc = r * r - (qy - cy) * (qy - cy);
      if (disc <= 0.0) continue;                        // scan line misses the circle (or tangent)
      double sq = Math.sqrt(disc);
      double a0 = Math.atan2(sy - cy, sx - cx);
      double am = Math.atan2(my - cy, mx - cx);
      double ae = Math.atan2(ey - cy, ex - cx);
      boolean ccw = det > 0;
      double theta = sweep(a0, am, ccw) + sweep(am, ae, ccw);
      for (int s = -1; s <= 1; s += 2) {
        double x = cx + s * sq;
        if (x <= qx) continue;                          // crossing not to the right of the query
        double sw = sweep(a0, Math.atan2(qy - cy, x - cx), ccw);
        if (sw <= theta + 1e-9 || sw >= 2 * Math.PI - 1e-9) crossings++;
      }
    }
    return (crossings & 1) == 1;
  }

  private static double sweep(double from, double to, boolean ccw) {
    double t = ccw ? (to - from) : (from - to);
    t %= 2 * Math.PI;
    if (t < 0) t += 2 * Math.PI;
    return t;
  }
}
