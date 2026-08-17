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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.curve.CircularArcDensifier;

/**
 * Proofs Option A front-end: exact closed-form cells for one 3-control
 * circular window ({@code r·θ}, {@code chord ≤ arc}, in-arc, segment
 * area, arc-length centroid).
 * <p>
 * Circumcircle is {@link CircularArcDensifier#circumcircle} — one
 * determinant, not a second copy. Sweep is {@link AngleBetween}.
 * Colinear triples degrade to the chord; never a silent flatten
 * flagged exact.
 */
public final class ExactCircularArc {

  private final Coordinate start;
  private final Coordinate mid;
  private final Coordinate end;
  private final double cx;
  private final double cy;
  private final double r;
  private final double a0;
  private final boolean ccw;
  private final double sweep;
  private final boolean arc;

  public ExactCircularArc(Coordinate start, Coordinate mid, Coordinate end) {
    this.start = start;
    this.mid = mid;
    this.end = end;
    double[] circ = CircularArcDensifier.circumcircle(start, mid, end);
    if (circ == null) {
      this.cx = Double.NaN;
      this.cy = Double.NaN;
      this.r = 0.0;
      this.a0 = 0.0;
      this.ccw = true;
      this.sweep = 0.0;
      this.arc = false;
      return;
    }
    this.cx = circ[0];
    this.cy = circ[1];
    this.a0 = Math.atan2(start.y - cy, start.x - cx);
    double aMid = Math.atan2(mid.y - cy, mid.x - cx);
    double a1 = Math.atan2(end.y - cy, end.x - cx);
    this.ccw = AngleBetween.isCcw(a0, aMid, a1);
    this.sweep = AngleBetween.directedSweepFromAngles(a0, aMid, a1);
    // Mean of the three control radii — one float circle cannot hit
    // all three exactly; the mean is the honest r for r·θ.
    this.r = meanRadius(circ[0], circ[1], start, mid, end);
    this.arc = true;
  }

  /**
   * Allocation-light {@code r·θ} (or chord). Used by
   * {@code CircularString.getLength()} and the 1M PERF cell.
   */
  public static double length(Coordinate start, Coordinate mid, Coordinate end) {
    double[] circ = CircularArcDensifier.circumcircle(start, mid, end);
    if (circ == null) {
      return start.distance(end);
    }
    double sweep = AngleBetween.directedSweep(circ[0], circ[1], start, mid, end);
    return meanRadius(circ[0], circ[1], start, mid, end) * sweep;
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

  public boolean isArc() {
    return arc;
  }

  public boolean isCcw() {
    return ccw;
  }

  public double radius() {
    return r;
  }

  public Coordinate center() {
    return arc ? new Coordinate(cx, cy) : null;
  }

  /** Central angle in {@code (0, 2π]}; {@code 0} on a chord fallback. */
  public double sweep() {
    return sweep;
  }

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
   * Proofs {@code chord_le_arc_length}. Uses the real identity
   * {@code 2 r sin(θ/2) ≤ r θ} plus one ulp — not a fixed 1e-12 slack.
   */
  public boolean chordLeArc() {
    double chord = chordLength();
    if (!arc) {
      return true;
    }
    double arcLen = r * sweep;
    if (chord <= arcLen) {
      return true;
    }
    double chordFromSweep = 2.0 * r * Math.sin(0.5 * sweep);
    double bound = Math.max(arcLen, chordFromSweep);
    return chord <= bound + Math.ulp(Math.max(bound, chord));
  }

  /**
   * Point-on-arc via {@code |d² − r²|} (no extra hypot) and the cached
   * start angle. Chord fallback is the segment.
   */
  public boolean inArc(Coordinate p, double radialTol) {
    if (p == null) {
      return false;
    }
    if (!arc) {
      return onSegment(p, start, end, radialTol);
    }
    double dx = p.x - cx;
    double dy = p.y - cy;
    double d2 = dx * dx + dy * dy;
    double r2 = r * r;
    double tol2 = radialTol * (2.0 * r + radialTol);
    if (Math.abs(d2 - r2) > tol2) {
      return false;
    }
    return onSweep(p);
  }

  /** Circular-segment area {@code r²/2 · (θ − sin θ)}. Zero on a chord. */
  public double circularSegmentArea() {
    if (!arc) {
      return 0.0;
    }
    return 0.5 * r * r * (sweep - Math.sin(sweep));
  }

  /**
   * Wire (arc-length) centroid. Uses the cached start angle.
   */
  public Coordinate arcLengthCentroid() {
    if (!arc) {
      return new Coordinate(0.5 * (start.x + end.x), 0.5 * (start.y + end.y));
    }
    if (sweep == 0.0) {
      return start.copy();
    }
    double a1 = a0 + (ccw ? sweep : -sweep);
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
    double ap = Math.atan2(p.y - cy, p.x - cx);
    double travelled = ccw
        ? AngleBetween.normalizePositive(ap - a0)
        : AngleBetween.normalizePositive(a0 - ap);
    return travelled <= sweep + Math.ulp(sweep);
  }

  private static double meanRadius(double cx, double cy,
      Coordinate a, Coordinate b, Coordinate c) {
    return (Math.hypot(a.x - cx, a.y - cy)
        + Math.hypot(b.x - cx, b.y - cy)
        + Math.hypot(c.x - cx, c.y - cy)) / 3.0;
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
    if (t < 0.0) {
      t = 0.0;
    }
    else if (t > 1.0) {
      t = 1.0;
    }
    double px = a.x + t * dx - p.x;
    double py = a.y + t * dy - p.y;
    return px * px + py * py <= tol * tol;
  }
}
