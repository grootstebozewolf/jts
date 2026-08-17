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
package org.locationtech.jts.algorithm.exactcurve;

import java.util.List;

import org.locationtech.jts.algorithm.exactcurve.AngleBetween.DirectedSweep;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.curve.CircularArcDensifier;

/**
 * Privileged ExactCurve* primitive: one 3-control circular window.
 * Closed-form {@code r·θ}, {@code chord ≤ arc}, in-arc, segment area,
 * arc-length centroid, {@code pointAt}.
 * <p>
 * Circumcircle is {@link CircularArcDensifier#circumcircle} — one
 * determinant, not a second copy. Sweep is {@link AngleBetween}.
 * Colinear triples degrade to an exact chord; never a silent flatten
 * flagged circular. Densify only via {@link #toLinear(double)}.
 * <p>
 * Canonical architecture: {@code doc/EXACT_CURVE_BIBLE.md}.
 */
public final class ExactCircularArc implements ExactCurve {

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
    this.start = start.copy();
    this.mid = mid.copy();
    this.end = end.copy();
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
    this.r = circ[2];
    this.a0 = Math.atan2(start.y - cy, start.x - cx);
    DirectedSweep sw = AngleBetween.through(cx, cy, start, mid, end);
    this.ccw = sw.isCcw();
    this.sweep = sw.radians();
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
    return circ[2] * AngleBetween.directedSweep(circ[0], circ[1], start, mid, end);
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

  /** Centre x; {@link Double#NaN} on a chord fallback. */
  public double centerX() {
    return cx;
  }

  /** Centre y; {@link Double#NaN} on a chord fallback. */
  public double centerY() {
    return cy;
  }

  public Coordinate center() {
    return arc ? new Coordinate(cx, cy) : null;
  }

  /** Central angle in {@code (0, 2π]}; {@code 0} on a chord fallback. */
  public double sweep() {
    return sweep;
  }

  /**
   * Whether {@code p}'s central angle lies on this directed window.
   * Chord fallback is always {@code false}.
   */
  public boolean isOnSweep(Coordinate p) {
    return onSweep(p);
  }

  /** Allocation-free sweep test at Cartesian {@code (x, y)}. */
  public boolean isOnSweep(double x, double y) {
    if (!arc) {
      return false;
    }
    double travelled = AngleBetween.travelled(ccw,
        start.x - cx, start.y - cy, x - cx, y - cy);
    return travelled <= sweep + Math.ulp(sweep);
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
   * Point at arc-length fraction {@code t ∈ [0, 1]}. Endpoints are the
   * original controls. Chord fallback is linear interpolation.
   */
  public Coordinate pointAt(double t) {
    if (!Double.isFinite(t) || t < 0.0 || t > 1.0) {
      throw new IllegalArgumentException("t must be in [0,1]: " + t);
    }
    if (t == 0.0) {
      return start.copy();
    }
    if (t == 1.0) {
      return end.copy();
    }
    if (!arc) {
      return new Coordinate(
          start.x + t * (end.x - start.x),
          start.y + t * (end.y - start.y));
    }
    double ang = a0 + (ccw ? sweep : -sweep) * t;
    return new Coordinate(cx + r * Math.cos(ang), cy + r * Math.sin(ang));
  }

  /**
   * Documented densify shim. Not used by {@link #length()} or
   * {@link #pointAt(double)}.
   */
  public Geometry toLinear(double tolerance) {
    List<Coordinate> pts = CircularArcDensifier.densifyArc(start, mid, end,
        tolerance);
    return new GeometryFactory().createLineString(
        pts.toArray(new Coordinate[0]));
  }

  /**
   * Closed-form (circular or exact chord). Never a hidden densify.
   */
  public boolean isExact() {
    return true;
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
   * Point-on-arc via {@code |d² − r²|} (no extra hypot) and
   * {@link AngleBetween#travelled}. Chord fallback is the segment.
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
   * Wire (arc-length) centroid. One signed-sweep formula for both
   * orientations: {@code (r/δ) (sin a1 − sin a0, −cos a1 + cos a0)}.
   */
  public Coordinate arcLengthCentroid() {
    if (!arc) {
      return new Coordinate(0.5 * (start.x + end.x), 0.5 * (start.y + end.y));
    }
    if (sweep == 0.0) {
      return start.copy();
    }
    double signed = ccw ? sweep : -sweep;
    double a1 = a0 + signed;
    double k = r / signed;
    return new Coordinate(
        cx + k * (Math.sin(a1) - Math.sin(a0)),
        cy + k * (-Math.cos(a1) + Math.cos(a0)));
  }

  boolean onSweep(Coordinate p) {
    if (!arc) {
      return false;
    }
    double travelled = AngleBetween.travelled(ccw,
        start.x - cx, start.y - cy, p.x - cx, p.y - cy);
    return travelled <= sweep + Math.ulp(sweep);
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
