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
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.geom.Coordinate;

/**
 * Thin exact-arc geometry helpers for the Proofs Option B predicate
 * seam. Keeps circle / sweep / intersect math in one place (same package
 * as {@link CircularArcDensifier}) so {@code OrientableSegment} carriers
 * stay small and do not re-implement quadratics.
 * <p>
 * Not a noder. Not OverlayNG. Package-facing for jts-curve consumers.
 */
public final class ArcGeometry {

  private ArcGeometry() { }

  /** True when {@code start,mid,end} determine a non-degenerate circle. */
  public static boolean isCircular(Coordinate start, Coordinate mid,
      Coordinate end) {
    return CircularArcDensifier.Circle.fromThreePoints(start, mid, end) != null;
  }

  /**
   * Nearest point on the arc window (or chord if colinear).
   */
  public static Coordinate nearestOnArc(Coordinate q, Coordinate start,
      Coordinate mid, Coordinate end) {
    return CircularArcDensifier.nearestPointOnArc(q, start, mid, end);
  }

  public static double distancePointToArc(Coordinate q, Coordinate start,
      Coordinate mid, Coordinate end) {
    return CircularArcDensifier.distancePointToArc(q, start, mid, end);
  }

  /**
   * Unit tangent at {@code onArc} oriented along the directed sweep
   * through {@code start → mid → end}. Returns {@code null} if the
   * triple is not circular.
   */
  public static double[] directedTangentAt(Coordinate onArc, Coordinate start,
      Coordinate mid, Coordinate end) {
    CircularArcDensifier.Circle c =
        CircularArcDensifier.Circle.fromThreePoints(start, mid, end);
    if (c == null) {
      return null;
    }
    boolean ccw = isCcw(c, start, mid, end);
    double tx = -(onArc.y - c.cy);
    double ty = onArc.x - c.cx;
    if (!ccw) {
      tx = -tx;
      ty = -ty;
    }
    double len = Math.hypot(tx, ty);
    if (len == 0.0) {
      return null;
    }
    return new double[] { tx / len, ty / len };
  }

  /**
   * Arc ∩ open segment, using the densifier quadratic + sweep filter.
   */
  public static boolean intersectsSegment(Coordinate a0, Coordinate a1,
      Coordinate a2, Coordinate s0, Coordinate s1) {
    CircularArcDensifier.Circle c =
        CircularArcDensifier.Circle.fromThreePoints(a0, a1, a2);
    if (c == null) {
      return false;
    }
    Coordinate[] hits = CircularArcDensifier.intersectSegmentCircle(c, s0, s1);
    for (int i = 0; i < hits.length; i++) {
      if (CircularArcDensifier.isOnSweep(hits[i], c, a0, a1, a2)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Arc ∩ arc via circle–circle nodes filtered to both sweeps
   * (delegates to densifier twin of N-AA).
   */
  public static boolean intersectsArc(Coordinate a0, Coordinate a1, Coordinate a2,
      Coordinate b0, Coordinate b1, Coordinate b2) {
    Coordinate[] nodes = CircularArcDensifier.intersectArcs(
        a0, a1, a2, b0, b1, b2);
    return nodes != null && nodes.length > 0;
  }

  /**
   * Sample the directed arc into {@code nChord} equal-angle steps
   * (reference only — never flagged exact).
   */
  public static Coordinate[] sampleArc(Coordinate start, Coordinate mid,
      Coordinate end, int nChord) {
    CircularArcDensifier.Circle c =
        CircularArcDensifier.Circle.fromThreePoints(start, mid, end);
    if (c == null || nChord < 1) {
      return new Coordinate[] { start.copy(), end.copy() };
    }
    boolean ccw = isCcw(c, start, mid, end);
    double a0 = Math.atan2(start.y - c.cy, start.x - c.cx);
    double a1 = Math.atan2(end.y - c.cy, end.x - c.cx);
    double sweep = ccw ? normPos(a1 - a0) : -normPos(a0 - a1);
    if (sweep == 0.0) {
      sweep = ccw ? 2.0 * Math.PI : -2.0 * Math.PI;
    }
    Coordinate[] pts = new Coordinate[nChord + 1];
    for (int i = 0; i <= nChord; i++) {
      double t = (double) i / (double) nChord;
      double ang = a0 + t * sweep;
      pts[i] = new Coordinate(
          c.cx + c.r * Math.cos(ang),
          c.cy + c.r * Math.sin(ang));
    }
    return pts;
  }

  private static boolean isCcw(CircularArcDensifier.Circle c, Coordinate start,
      Coordinate mid, Coordinate end) {
    double a0 = Math.atan2(start.y - c.cy, start.x - c.cx);
    double aM = Math.atan2(mid.y - c.cy, mid.x - c.cx);
    double a1 = Math.atan2(end.y - c.cy, end.x - c.cx);
    return normPos(aM - a0) < normPos(a1 - a0);
  }

  private static double normPos(double angle) {
    double twoPi = 2.0 * Math.PI;
    angle = angle % twoPi;
    if (angle < 0.0) {
      angle += twoPi;
    }
    return angle;
  }
}
