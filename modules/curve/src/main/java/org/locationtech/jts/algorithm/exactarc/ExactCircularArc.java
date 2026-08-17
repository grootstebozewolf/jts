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
package org.locationtech.jts.algorithm.exactarc;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;

/**
 * Proofs Option A front-end: exact closed-form cells for one 3-control
 * circular window. Mirrors Proofs #64 Atan2 / AngleBetween / ArcLength
 * ({@code r·θ}, {@code chord ≤ arc}).
 * <p>
 * Package is the product seam. {@code CircularArcDensifier} stays the
 * sagitta/chord tool; this class owns length, sweep, in-arc, and the
 * circular-segment area. Colinear triples degrade to the chord — never
 * a silent flatten flagged exact.
 * <p>
 * Not a public JTS API. Sister of Proofs Option B
 * ({@code OrientableSegment} predicate seam).
 */
public final class ExactCircularArc {

  private static final double TWO_PI = 2.0 * Math.PI;

  private final Coordinate start;
  private final Coordinate mid;
  private final Coordinate end;
  private final double cx;
  private final double cy;
  private final double r;
  private final boolean ccw;
  private final double sweep;
  private final boolean arc;

  public ExactCircularArc(Coordinate start, Coordinate mid, Coordinate end) {
    this.start = start;
    this.mid = mid;
    this.end = end;
    double[] circ = circumcircle(start, mid, end);
    if (circ == null) {
      this.cx = Double.NaN;
      this.cy = Double.NaN;
      this.r = 0.0;
      this.ccw = true;
      this.sweep = 0.0;
      this.arc = false;
    }
    else {
      this.cx = circ[0];
      this.cy = circ[1];
      this.r = circ[2];
      double a0 = Math.atan2(start.y - cy, start.x - cx);
      double aM = Math.atan2(mid.y - cy, mid.x - cx);
      double a1 = Math.atan2(end.y - cy, end.x - cx);
      this.ccw = normPos(aM - a0) < normPos(a1 - a0);
      double s = ccw ? normPos(a1 - a0) : normPos(a0 - a1);
      this.sweep = s == 0.0 ? TWO_PI : s;
      this.arc = true;
    }
  }

  public static double length(Coordinate start, Coordinate mid, Coordinate end) {
    return new ExactCircularArc(start, mid, end).length();
  }

  public Coordinate getStart() {
    return start;
  }

  public Coordinate getMid() {
    return mid;
  }

  public Coordinate getEnd() {
    return end;
  }

  /** True when the triple defines a circle (not colinear / coincident). */
  public boolean isArc() {
    return arc;
  }

  public boolean isCcw() {
    return ccw;
  }

  public double radius() {
    return r;
  }

  /** Circumcentre, or {@code null} on a chord fallback. */
  public Coordinate center() {
    return arc ? new Coordinate(cx, cy) : null;
  }

  /** Central angle in {@code (0, 2π]}; {@code 0} on a chord fallback. */
  public double sweep() {
    return sweep;
  }

  /**
   * Exact arc length {@code r·θ}, or the straight chord when the triple
   * is degenerate. Never negative.
   */
  public double length() {
    if (!arc) {
      return start.distance(end);
    }
    return r * sweep;
  }

  public double chordLength() {
    return start.distance(end);
  }

  /**
   * Proofs {@code chord_le_arc_length}. True for every finite triple
   * (equality on a chord or a zero-length collapse).
   */
  public boolean chordLeArc() {
    return chordLength() <= length() + 1.0e-12;
  }

  /**
   * Point-on-arc: on the circle within {@code radialTol} and inside the
   * directed sweep (inclusive ends). Chord fallback is the segment.
   */
  public boolean inArc(Coordinate p, double radialTol) {
    if (p == null) {
      return false;
    }
    if (!arc) {
      return onSegment(p, start, end, radialTol);
    }
    double d = Math.hypot(p.x - cx, p.y - cy);
    if (Math.abs(d - r) > radialTol) {
      return false;
    }
    return onSweep(p);
  }

  /**
   * Circular-segment area {@code r²/2 · (θ − sin θ)} (unsigned). Zero
   * on a chord fallback.
   */
  public double circularSegmentArea() {
    if (!arc) {
      return 0.0;
    }
    return 0.5 * r * r * (sweep - Math.sin(sweep));
  }

  /**
   * Wire (arc-length) centroid of this window. Chord fallback is the
   * midpoint. Proofs {@code ArcCentroid}: offset {@code 2r·sin(θ/2)/θ}
   * along the angle bisector, specialised to the directed sweep.
   */
  public Coordinate arcLengthCentroid() {
    if (!arc) {
      return new Coordinate(0.5 * (start.x + end.x), 0.5 * (start.y + end.y));
    }
    double a0 = Math.atan2(start.y - cy, start.x - cx);
    double a1 = a0 + (ccw ? sweep : -sweep);
    double len = r * sweep;
    if (len == 0.0) {
      return start.copy();
    }
    double x;
    double y;
    if (ccw) {
      x = cx + (r / sweep) * (Math.sin(a1) - Math.sin(a0));
      y = cy + (r / sweep) * (-Math.cos(a1) + Math.cos(a0));
    }
    else {
      x = cx + (r / sweep) * (Math.sin(a0) - Math.sin(a1));
      y = cy + (r / sweep) * (-Math.cos(a0) + Math.cos(a1));
    }
    return new Coordinate(x, y);
  }

  boolean onSweep(Coordinate p) {
    if (!arc) {
      return false;
    }
    double a0 = Math.atan2(start.y - cy, start.x - cx);
    double ap = Math.atan2(p.y - cy, p.x - cx);
    double travelled = ccw ? normPos(ap - a0) : normPos(a0 - ap);
    return travelled <= sweep + 1.0e-12;
  }

  private static double[] circumcircle(Coordinate a, Coordinate b, Coordinate c) {
    if (Orientation.index(a, b, c) == Orientation.COLLINEAR) {
      return null;
    }
    double ax = a.x, ay = a.y;
    double bx = b.x, by = b.y;
    double cxp = c.x, cyp = c.y;
    double d = 2.0 * (ax * (by - cyp) + bx * (cyp - ay) + cxp * (ay - by));
    if (d == 0.0) {
      return null;
    }
    double a2 = ax * ax + ay * ay;
    double b2 = bx * bx + by * by;
    double c2 = cxp * cxp + cyp * cyp;
    double ux = (a2 * (by - cyp) + b2 * (cyp - ay) + c2 * (ay - by)) / d;
    double uy = (a2 * (cxp - bx) + b2 * (ax - cxp) + c2 * (bx - ax)) / d;
    double rad = Math.hypot(ax - ux, ay - uy);
    if (!Double.isFinite(rad) || rad == 0.0) {
      return null;
    }
    return new double[] { ux, uy, rad };
  }

  private static boolean onSegment(Coordinate p, Coordinate a, Coordinate b,
      double tol) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double len2 = dx * dx + dy * dy;
    if (len2 == 0.0) {
      return p.distance(a) <= tol;
    }
    double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2;
    if (t < -1.0e-12 || t > 1.0 + 1.0e-12) {
      return false;
    }
    Coordinate proj = new Coordinate(a.x + t * dx, a.y + t * dy);
    return p.distance(proj) <= tol;
  }

  private static double normPos(double angle) {
    angle = angle % TWO_PI;
    if (angle < 0.0) {
      angle += TWO_PI;
    }
    return angle;
  }
}
