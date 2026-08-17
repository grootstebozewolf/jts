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
package org.locationtech.jts.algorithm.orientable;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.curve.ArcGeometry;

/**
 * Densify-chord reference for B-team statistical trials only.
 * Never production — never flagged exact.
 */
final class OrientableDensifyReference {

  private OrientableDensifyReference() { }

  static int orientationIndex(ArcOrientableSegment arc, Coordinate q,
      int nChord) {
    Coordinate[] pts = ArcGeometry.sampleArc(
        arc.getStart(), arc.getMid(), arc.getEnd(), nChord);
    double best = Double.POSITIVE_INFINITY;
    int bestOri = Orientation.COLLINEAR;
    for (int i = 1; i < pts.length; i++) {
      Coordinate a = pts[i - 1];
      Coordinate b = pts[i];
      Coordinate foot = closestOnSeg(q, a, b);
      double d = q.distance(foot);
      if (d < best) {
        best = d;
        bestOri = Orientation.index(a, b, q);
      }
    }
    return bestOri;
  }

  static boolean intersectsStraight(ArcOrientableSegment arc,
      StraightOrientableSegment seg, int nChord) {
    Coordinate[] pts = ArcGeometry.sampleArc(
        arc.getStart(), arc.getMid(), arc.getEnd(), nChord);
    RobustLineIntersector li = new RobustLineIntersector();
    for (int i = 1; i < pts.length; i++) {
      li.computeIntersection(pts[i - 1], pts[i], seg.getStart(), seg.getEnd());
      if (li.hasIntersection()) {
        return true;
      }
    }
    return false;
  }

  private static Coordinate closestOnSeg(Coordinate p, Coordinate a,
      Coordinate b) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double len2 = dx * dx + dy * dy;
    if (len2 == 0.0) {
      return a.copy();
    }
    double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2;
    if (t < 0.0) {
      t = 0.0;
    }
    else if (t > 1.0) {
      t = 1.0;
    }
    return new Coordinate(a.x + t * dx, a.y + t * dy);
  }
}
