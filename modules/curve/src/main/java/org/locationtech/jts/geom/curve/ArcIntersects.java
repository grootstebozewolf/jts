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

import org.locationtech.jts.algorithm.exactcurve.ExactCircularArc;
import org.locationtech.jts.geom.Coordinate;

/**
 * Named densifier bridge for optional OrientableSegment intersect.
 * Lives beside {@link CircularArcDensifier} so package-private circle
 * helpers stay unexported. Not an ExactCurve* cell; not a second
 * geometry owner ({@code doc/EXACT_CURVE_BIBLE.md} §2–§3).
 * <p>
 * Exact arc×arc / arc×segment closed forms belong on
 * {@link ExactCircularArc} (or a sibling Exact* cell), not here.
 */
public final class ArcIntersects {

  private ArcIntersects() { }

  public static boolean segment(ExactCircularArc arc, Coordinate s0,
      Coordinate s1) {
    if (arc == null || !arc.isArc()) {
      return false;
    }
    CircularArcDensifier.Circle c = CircularArcDensifier.Circle.fromThreePoints(
        arc.getStart(), arc.getMid(), arc.getEnd());
    if (c == null) {
      return false;
    }
    Coordinate[] hits = CircularArcDensifier.intersectSegmentCircle(c, s0, s1);
    for (int i = 0; i < hits.length; i++) {
      if (arc.isOnSweep(hits[i])) {
        return true;
      }
    }
    return false;
  }

  public static boolean arcs(ExactCircularArc a, ExactCircularArc b) {
    if (a == null || b == null || !a.isArc() || !b.isArc()) {
      return false;
    }
    Coordinate[] nodes = CircularArcDensifier.intersectArcs(
        a.getStart(), a.getMid(), a.getEnd(),
        b.getStart(), b.getMid(), b.getEnd());
    if (nodes == null) {
      return false;
    }
    for (int i = 0; i < nodes.length; i++) {
      if (a.isOnSweep(nodes[i]) && b.isOnSweep(nodes[i])) {
        return true;
      }
    }
    return false;
  }
}
