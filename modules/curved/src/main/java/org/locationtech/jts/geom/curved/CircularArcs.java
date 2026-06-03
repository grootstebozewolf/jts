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
import org.locationtech.jts.geom.CoordinateSequence;
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

  /**
   * Centroid of a circular arc (the curve, uniform density), for C-LIN etc.
   * Returns the point at distance (r * sin(alpha)/alpha) from center along bisector,
   * where alpha = theta/2.
   */
  public static Coordinate arcCentroid(Coordinate p0, Coordinate p1, Coordinate p2) {
    double sx = p0.x, sy = p0.y;
    double mx = p1.x, my = p1.y;
    double ex = p2.x, ey = p2.y;
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (Math.abs(d) < 1e-12) {
      return new Coordinate( (sx+ex)/2, (sy+ey)/2 );
    }
    double cx = ((sx*sx + sy*sy) * (my - ey)
               + (mx*mx + my*my) * (ey - sy)
               + (ex*ex + ey*ey) * (sy - my)) / d;
    double cy = ((sx*sx + sy*sy) * (ex - mx)
               + (mx*mx + my*my) * (sx - ex)
               + (ex*ex + ey*ey) * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (r < 1e-12) {
      return new Coordinate( (sx+ex)/2, (sy+ey)/2 );
    }
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
    if (theta < 1e-12) {
      return new Coordinate( (sx+ex)/2, (sy+ey)/2 );
    }
    double alpha = theta / 2;
    double dist = r * Math.sin(alpha) / alpha;
    double bis = a0 + sweep / 2;
    double acx = cx + dist * Math.cos(bis);
    double acy = cy + dist * Math.sin(bis);
    return new Coordinate(acx, acy);
  }

  /**
   * Returns the point at arc-length distance s (0 <= s <= arc length) along the
   * circular arc from p0 to p2 via p1.
   */
  public static Coordinate pointAlongArc(Coordinate p0, Coordinate p1, Coordinate p2, double s) {
    double[] p = arcParams(p0, p1, p2);
    double r = p[0];
    double theta = p[1];
    double cx = p[2];
    double cy = p[3];
    double a0 = p[4];
    double sweep = p[5];
    if (r < 1e-12 || theta < 1e-12) {
      // degenerate to line
      double chordLen = Math.hypot(p2.x - p0.x, p2.y - p0.y);
      double t = chordLen > 0 ? s / chordLen : 0;
      return new Coordinate(p0.x + t * (p2.x - p0.x), p0.y + t * (p2.y - p0.y));
    }
    double arcLen = r * theta;
    if (s <= 0) return p0;
    if (s >= arcLen) return p2;
    double phi = s / r;
    double angle = a0 + Math.signum(sweep) * phi;
    return new Coordinate(cx + r * Math.cos(angle), cy + r * Math.sin(angle));
  }

  /**
   * Point at arc-length s along a full CircularString (sum of subarcs).
   */
  public static Coordinate pointAlongCircularString(CoordinateSequence pts, double s) {
    if (pts.size() < 2) return pts.getCoordinate(0);
    double total = 0.0;
    for (int i = 0; i + 2 < pts.size(); i += 2) {
      Coordinate a = pts.getCoordinate(i);
      Coordinate b = pts.getCoordinate(i + 1);
      Coordinate c = pts.getCoordinate(i + 2);
      double alen = arcLength(a, b, c);
      if (total + alen >= s) {
        double local = s - total;
        return pointAlongArc(a, b, c, local);
      }
      total += alen;
    }
    return pts.getCoordinate(pts.size() - 1);
  }

  /** Point at fraction (0-1) along the arc. */
  public static Coordinate pointAlongArcFrac(Coordinate p0, Coordinate p1, Coordinate p2, double frac) {
    double len = arcLength(p0, p1, p2);
    return pointAlongArc(p0, p1, p2, frac * len);
  }

  /**
   * Area of the circular segment (area between arc and chord).
   */
  public static double segmentArea(double r, double theta) {
    if (theta < 0) theta = -theta;
    return (r * r / 2.0) * (theta - Math.sin(theta));
  }

  /** Segment area for 3-pt arc. */
  public static double segmentArea(Coordinate p0, Coordinate p1, Coordinate p2) {
    double[] p = arcParams(p0, p1, p2);
    return segmentArea(p[0], p[1]);
  }

  /** Returns [r, theta, cx, cy, a0, sweep] for the arc. */
  private static double[] arcParams(Coordinate p0, Coordinate p1, Coordinate p2) {
    double sx = p0.x, sy = p0.y;
    double mx = p1.x, my = p1.y;
    double ex = p2.x, ey = p2.y;
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (Math.abs(d) < 1e-12) {
      return new double[]{0, 0, 0, 0, 0, 0};
    }
    double cx = ((sx*sx + sy*sy) * (my - ey)
               + (mx*mx + my*my) * (ey - sy)
               + (ex*ex + ey*ey) * (sy - my)) / d;
    double cy = ((sx*sx + sy*sy) * (ex - mx)
               + (mx*mx + my*my) * (sx - ex)
               + (ex*ex + ey*ey) * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (r < 1e-12) {
      return new double[]{0, 0, 0, 0, 0, 0};
    }
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
    return new double[]{r, theta, cx, cy, a0, sweep};
  }

  /**
   * Centroid of the circular segment (the 'lune' area between chord and arc),
   * relative to center, along bisector.
   * Formula: d = 4 r sin^3(alpha) / (3 (theta - sin theta)) , alpha=theta/2 .
   */
  public static Coordinate segmentCentroid(Coordinate p0, Coordinate p1, Coordinate p2) {
    // reuse computations
    double sx = p0.x, sy = p0.y;
    double mx = p1.x, my = p1.y;
    double ex = p2.x, ey = p2.y;
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (Math.abs(d) < 1e-12) {
      return new Coordinate( (sx+ex)/2, (sy+ey)/2 );
    }
    double cx = ((sx*sx + sy*sy) * (my - ey)
               + (mx*mx + my*my) * (ey - sy)
               + (ex*ex + ey*ey) * (sy - my)) / d;
    double cy = ((sx*sx + sy*sy) * (ex - mx)
               + (mx*mx + my*my) * (sx - ex)
               + (ex*ex + ey*ey) * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (r < 1e-12) {
      return new Coordinate( (sx+ex)/2, (sy+ey)/2 );
    }
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
    if (theta < 1e-12) {
      return new Coordinate( (sx+ex)/2, (sy+ey)/2 );
    }
    double alpha = theta / 2;
    double denom = (theta - Math.sin(theta));
    if (Math.abs(denom) < 1e-12) {
      return new Coordinate( (sx+ex)/2, (sy+ey)/2 );
    }
    double dseg = (4 * r * Math.pow(Math.sin(alpha), 3)) / (3 * denom);
    double bis = a0 + sweep / 2;
    double scx = cx + dseg * Math.cos(bis);
    double scy = cy + dseg * Math.sin(bis);
    return new Coordinate(scx, scy);
  }

  // Additional helpers can be added for D-PT etc (sweep, pointOnArc) without
  // changing this file signature for PRC-SN harden.
}
