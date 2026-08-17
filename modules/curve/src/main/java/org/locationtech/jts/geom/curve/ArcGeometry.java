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

import org.locationtech.jts.algorithm.exactcurve.AngleBetween;
import org.locationtech.jts.geom.Coordinate;

/**
 * Intersect / sample helpers for the B predicate seam.
 * Circumcircle and sweep live on A's {@code ExactCircularArc} /
 * {@link AngleBetween}; this class does not own a second circle solver.
 */
public final class ArcGeometry {

  private ArcGeometry() { }

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

  public static boolean intersectsArc(Coordinate a0, Coordinate a1, Coordinate a2,
      Coordinate b0, Coordinate b1, Coordinate b2) {
    Coordinate[] nodes = CircularArcDensifier.intersectArcs(
        a0, a1, a2, b0, b1, b2);
    return nodes != null && nodes.length > 0;
  }

  public static double distancePointToArc(Coordinate q, Coordinate start,
      Coordinate mid, Coordinate end) {
    return CircularArcDensifier.distancePointToArc(q, start, mid, end);
  }

  /** Densify-reference sample only — never flagged exact. */
  public static Coordinate[] sampleArc(Coordinate start, Coordinate mid,
      Coordinate end, int nChord) {
    double[] circ = CircularArcDensifier.circumcircle(start, mid, end);
    if (circ == null || nChord < 1) {
      return new Coordinate[] { start.copy(), end.copy() };
    }
    double cx = circ[0];
    double cy = circ[1];
    double r = circ[2];
    AngleBetween.DirectedSweep sw = AngleBetween.through(cx, cy, start, mid, end);
    double signed = sw.signed();
    double a0 = Math.atan2(start.y - cy, start.x - cx);
    Coordinate[] pts = new Coordinate[nChord + 1];
    for (int i = 0; i <= nChord; i++) {
      double t = (double) i / (double) nChord;
      double ang = a0 + t * signed;
      pts[i] = new Coordinate(cx + r * Math.cos(ang), cy + r * Math.sin(ang));
    }
    return pts;
  }
}
