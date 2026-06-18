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
 * Arc-aware orientation / signed area of a closed curved ring (V-CP building
 * block, JTS #1195). The ring is a control-point sequence read as consecutive
 * arc pieces {@code (p[2i], p[2i+1], p[2i+2])} (a collinear triple is a chord).
 * <p>
 * The signed area is the endpoint-polygon shoelace plus, for each arc, the signed
 * circular-segment area {@code ±(r²/2)(θ − sin θ)} (added when the arc bulges to
 * the left of its chord, subtracted when to the right) — so it is the true area
 * enclosed by the arcs, and its sign is the arc-aware orientation (CCW iff
 * positive). This can differ from the chord-polygon orientation when arcs bulge
 * across the chord-polygon's hull, which is exactly why a dedicated primitive is
 * needed for ring orientation.
 */
final class ArcRing {

  private ArcRing() {}

  /** Arc-aware signed area of the closed curved ring (CCW positive). */
  static double signedArea(CoordinateSequence ring) {
    int n = ring.size();
    if (n < 3) return 0.0;
    double area = 0.0;
    // shoelace over the arc endpoints (indices 0, 2, 4, ... — the chord polygon)
    for (int i = 0; i + 2 < n; i += 2) {
      double x1 = ring.getX(i),     y1 = ring.getY(i);
      double x2 = ring.getX(i + 2), y2 = ring.getY(i + 2);
      area += x1 * y2 - x2 * y1;
    }
    area *= 0.5;
    // per-arc circular-segment correction
    for (int i = 0; i + 2 < n; i += 2) {
      double sx = ring.getX(i),     sy = ring.getY(i);
      double mx = ring.getX(i + 1), my = ring.getY(i + 1);
      double ex = ring.getX(i + 2), ey = ring.getY(i + 2);
      double det = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
      if (det == 0.0) continue;                         // collinear: chord, no segment
      double s2 = sx*sx+sy*sy, m2 = mx*mx+my*my, e2 = ex*ex+ey*ey;
      double cx = (s2*(my-ey) + m2*(ey-sy) + e2*(sy-my)) / det;
      double cy = (s2*(ex-mx) + m2*(sx-ex) + e2*(mx-sx)) / det;
      double r = Math.hypot(sx - cx, sy - cy);
      if (!Double.isFinite(r) || r == 0.0) continue;
      double a0 = Math.atan2(sy - cy, sx - cx);
      double am = Math.atan2(my - cy, mx - cx);
      double ae = Math.atan2(ey - cy, ex - cx);
      boolean ccw = det > 0;
      double theta = sweep(a0, am, ccw) + sweep(am, ae, ccw);
      double seg = 0.5 * r * r * (theta - Math.sin(theta));
      area += ccw ? seg : -seg;
    }
    return area;
  }

  /** Arc-aware orientation: true iff the ring is counter-clockwise (signed area &gt; 0). */
  static boolean isCCW(CoordinateSequence ring) {
    return signedArea(ring) > 0.0;
  }

  private static double sweep(double from, double to, boolean ccw) {
    double t = ccw ? (to - from) : (from - to);
    t %= 2 * Math.PI;
    if (t < 0) t += 2 * Math.PI;
    return t;
  }
}
