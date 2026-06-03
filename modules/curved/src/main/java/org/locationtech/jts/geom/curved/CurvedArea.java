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

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LineString;

/**
 * Analytical area for curve-bounded rings, using the exact circular-segment
 * correction rather than the area of the flattened chord polygon.
 *
 * <p>The area enclosed by a closed ring is evaluated with Green's theorem:
 * <pre>
 *   A = (1/2) &#8750; (x dy - y dx)
 * </pre>
 * The line integral is path-additive, so the ring is walked segment by
 * segment. A straight segment {@code (p, q)} contributes the usual shoelace
 * cross term {@code p.x*q.y - q.x*p.y}. For a circular arc with centre
 * {@code (cx, cy)}, radius {@code R} and signed sweep {@code &Delta;}
 * (positive counter-clockwise), the closed-form contribution to
 * {@code &#8750;(x dy - y dx)} is
 * <pre>
 *   R&sup2;&middot;&Delta; + cx&middot;(y_end - y_start) - cy&middot;(x_end - x_start)
 * </pre>
 * which already folds in the area of the circular segment between the arc
 * and its chord, with the correct sign for the direction of travel. A disk
 * of radius {@code R} expressed as a {@code CURVEPOLYGON} of arcs therefore
 * evaluates to exactly {@code &pi;R&sup2;}.
 *
 * <p>Supported ring forms: {@link CircularString} (consecutive control-point
 * triples, each sharing an endpoint with the next), {@link CompoundCurve}
 * (its members are walked in turn), and plain straight {@link LineString} /
 * {@code LinearRing} rings (degenerate case &mdash; the result matches the
 * standard polygon area).
 */
final class CurvedArea {

  private CurvedArea() {
  }

  /**
   * Area of a curve-bounded polygon: {@code |shell| - &Sigma;|hole|}, each
   * ring measured arc-analytically. Mirrors the shell-minus-holes convention
   * of {@link org.locationtech.jts.geom.Polygon#getArea()}.
   */
  static double ofCurvePolygon(LineString shell, LineString[] holes) {
    if (shell == null) return 0.0;
    double area = Math.abs(signedRingArea(shell));
    if (holes != null) {
      for (int i = 0; i < holes.length; i++) {
        area -= Math.abs(signedRingArea(holes[i]));
      }
    }
    return area;
  }

  /**
   * Signed area of a single closed curved ring. Positive when the ring is
   * traversed counter-clockwise, negative when clockwise. Callers that only
   * want magnitude should take {@link Math#abs(double)}.
   */
  static double signedRingArea(LineString ring) {
    if (ring == null || ring.isEmpty()) return 0.0;
    return accumulate(ring) / 2.0;
  }

  /** Sum of {@code &#8750;(x dy - y dx)} contributions over the ring. */
  private static double accumulate(LineString ring) {
    if (ring instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) ring;
      double sum = 0.0;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        sum += accumulateMember(cc.getMemberN(i));
      }
      return sum;
    }
    return accumulateMember(ring);
  }

  private static double accumulateMember(LineString member) {
    if (member instanceof CircularString) {
      return accumulateArcs(member.getCoordinateSequence());
    }
    return accumulateStraight(member.getCoordinateSequence());
  }

  /** Straight polyline: shoelace cross term per consecutive vertex pair. */
  private static double accumulateStraight(CoordinateSequence seq) {
    double sum = 0.0;
    for (int i = 0; i + 1 < seq.size(); i++) {
      double xs = seq.getX(i),     ys = seq.getY(i);
      double xe = seq.getX(i + 1), ye = seq.getY(i + 1);
      sum += xs * ye - xe * ys;
    }
    return sum;
  }

  /**
   * Circular-string polyline: each consecutive (start, mid, end) triple is
   * one arc, with the end of one arc shared as the start of the next, so the
   * control points advance two at a time.
   */
  private static double accumulateArcs(CoordinateSequence seq) {
    int n = seq.size();
    if (n < 3) return accumulateStraight(seq);
    double sum = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      sum += arcContribution(seq.getCoordinate(i),
                             seq.getCoordinate(i + 1),
                             seq.getCoordinate(i + 2));
    }
    return sum;
  }

  /** Contribution of one arc to {@code &#8750;(x dy - y dx)}. */
  private static double arcContribution(Coordinate start, Coordinate mid, Coordinate end) {
    // Colinear / coincident control points define no arc: treat as a chord.
    if (Orientation.index(start, mid, end) == Orientation.COLLINEAR) {
      return chord(start, end);
    }
    double[] centre = CircularArcs.circumcentre(start, mid, end);
    if (centre == null) {
      return chord(start, end);
    }
    double cx = centre[0], cy = centre[1];
    double dx = start.x - cx, dy = start.y - cy;
    double r2 = dx * dx + dy * dy;
    double sweep = CircularArcs.signedSweep(cx, cy, start, mid, end);
    return r2 * sweep + cx * (end.y - start.y) - cy * (end.x - start.x);
  }

  /** Shoelace cross term of the straight chord from {@code p} to {@code q}. */
  private static double chord(Coordinate p, Coordinate q) {
    return p.x * q.y - q.x * p.y;
  }
}
