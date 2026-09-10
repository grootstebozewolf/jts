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
import org.locationtech.jts.geom.LineSegment;

/**
 * Public arc–arc and arc–line intersection utilities (N-AA / N-AL, #1195).
 * Wraps the circular solvers in {@link CircularArcDensifier}. Returns
 * intersection coordinates lying on both arcs (or the arc and segment);
 * empty array when none.
 */
public final class CurveIntersection {

  private CurveIntersection() { }

  /**
   * Intersection points of two circular arcs defined by three control
   * points each (SQL/MM CIRCULARSTRING triples).
   *
   * @return 0, 1, or 2 points on both arcs
   */
  public static Coordinate[] arcArc(
      Coordinate a0, Coordinate a1, Coordinate a2,
      Coordinate b0, Coordinate b1, Coordinate b2) {
    CircularArcDensifier.Circle ca = CircularArcDensifier.Circle.fromThreePoints(
        a0, a1, a2);
    CircularArcDensifier.Circle cb = CircularArcDensifier.Circle.fromThreePoints(
        b0, b1, b2);
    if (ca == null || cb == null) {
      return new Coordinate[0];
    }
    Coordinate[] hits = CircularArcDensifier.intersectCircles(ca, cb);
    if (hits == null) {
      return new Coordinate[0];
    }
    return filterOnBothArcs(hits, a0, a1, a2, b0, b1, b2);
  }

  /**
   * Intersection of a circular arc with a line segment.
   *
   * @return 0, 1, or 2 points on both the arc and the segment
   */
  public static Coordinate[] arcLine(
      Coordinate a0, Coordinate a1, Coordinate a2,
      Coordinate s0, Coordinate s1) {
    CircularArcDensifier.Circle ca = CircularArcDensifier.Circle.fromThreePoints(
        a0, a1, a2);
    if (ca == null) {
      return new Coordinate[0];
    }
    Coordinate[] hits = CircularArcDensifier.intersectSegmentCircle(ca, s0, s1);
    if (hits == null) {
      return new Coordinate[0];
    }
    java.util.ArrayList<Coordinate> out = new java.util.ArrayList<Coordinate>();
    for (int i = 0; i < hits.length; i++) {
      if (hits[i] == null) {
        continue;
      }
      if (onArc(hits[i], a0, a1, a2) && onSegment(hits[i], s0, s1)) {
        out.add(hits[i]);
      }
    }
    return out.toArray(new Coordinate[0]);
  }

  private static Coordinate[] filterOnBothArcs(Coordinate[] hits,
      Coordinate a0, Coordinate a1, Coordinate a2,
      Coordinate b0, Coordinate b1, Coordinate b2) {
    java.util.ArrayList<Coordinate> out = new java.util.ArrayList<Coordinate>();
    for (int i = 0; i < hits.length; i++) {
      if (hits[i] == null) {
        continue;
      }
      if (onArc(hits[i], a0, a1, a2) && onArc(hits[i], b0, b1, b2)) {
        out.add(hits[i]);
      }
    }
    return out.toArray(new Coordinate[0]);
  }

  private static boolean onArc(Coordinate p, Coordinate a0, Coordinate a1,
      Coordinate a2) {
    return CircularArcDensifier.distancePointToArc(p, a0, a1, a2) <= 1.0e-9;
  }

  private static boolean onSegment(Coordinate p, Coordinate s0, Coordinate s1) {
    LineSegment seg = new LineSegment(s0, s1);
    return seg.distance(p) <= 1.0e-9;
  }
}
