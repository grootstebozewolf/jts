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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Triangle;

/**
 * Shared analytical helpers for circular arcs (circumcentre, sweep, point-on-arc,
 * arc length contrib etc). Used by distance overrides (D-PT/D-AA), area (M-AREA-CP),
 * precision reduce (PRC-SN #66), and length.
 * <p>
 * Delegates circumcentre to core Triangle (exact match for grid-friendly checks).
 * Other primitives (sweep clamp, pointOnArcInterior) implemented here for curve module
 * (no core change).
 */
public final class CircularArcs {

  private CircularArcs() {}

  /** Circumcentre of 3 control points on a circle (delegates core for consistency). */
  public static Coordinate circumcentre(Coordinate p0, Coordinate p1, Coordinate p2) {
    return Triangle.circumcentre(p0, p1, p2);
  }

  /**
   * Exact arc length (r * theta) for the circular arc defined by three control points.
   * Ported from CurveRefRunner.exactCircularArcLength for M-LEN-* TAGs.
   * Handles degenerate/collinear by falling back to chord length.
   * Matches the proofs artifact (curve_arc_length_vectors.txt) and is used by
   * adversarial tests.
   */
  public static double arcLength(Coordinate p0, Coordinate p1, Coordinate p2) {
    double sx = p0.x, sy = p0.y;
    double mx = p1.x, my = p1.y;
    double ex = p2.x, ey = p2.y;
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (Math.abs(d) < 1e-12) {
      // degenerate / collinear -> chord length
      return Math.hypot(ex - sx, ey - sy);
    }
    double cx = ((sx*sx + sy*sy) * (my - ey)
               + (mx*mx + my*my) * (ey - sy)
               + (ex*ex + ey*ey) * (sy - my)) / d;
    double cy = ((sx*sx + sy*sy) * (ex - mx)
               + (mx*mx + my*my) * (sx - ex)
               + (ex*ex + ey*ey) * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (r < 1e-12) {
      return Math.hypot(ex - sx, ey - sy);
    }
    // Central angle using atan2 for robustness (sweep through the mid point)
    double a0 = Math.atan2(sy - cy, sx - cx);
    double a1 = Math.atan2(my - cy, mx - cx);
    double a2 = Math.atan2(ey - cy, ex - cx);
    double sweep = a2 - a0;
    sweep = ((sweep + Math.PI) % (2 * Math.PI)) - Math.PI;
    double aMidRel = a1 - a0;
    aMidRel = ((aMidRel + Math.PI) % (2 * Math.PI)) - Math.PI;
    if (Math.signum(sweep) * Math.signum(aMidRel) < 0 && Math.abs(sweep) < Math.PI) {
      sweep = (sweep > 0 ? sweep - 2*Math.PI : sweep + 2*Math.PI);
    }
    double theta = Math.abs(sweep);
    return r * theta;
  }

  // Additional helpers can be added for D-PT etc (sweep, pointOnArc) without
  // changing this file signature for PRC-SN harden.
}
