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

import org.locationtech.jts.algorithm.CGAlgorithmsDD;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.curve.ArcGeometry;

/**
 * Circular 3-control arc window — Proofs Option B.
 * <p>
 * Side predicate: robust cross of the directed unit tangent at the
 * nearest arc point with {@code (q − on)} via
 * {@link CGAlgorithmsDD#signOfDet2x2}. Intersection delegates to
 * {@link ArcGeometry} (one quadratic / sweep implementation).
 * Degenerate triples fall through to {@link StraightOrientableSegment}.
 */
public final class ArcOrientableSegment implements OrientableSegment {

  private final Coordinate start;
  private final Coordinate mid;
  private final Coordinate end;
  private final boolean circular;
  private final StraightOrientableSegment straight;

  public ArcOrientableSegment(Coordinate start, Coordinate mid, Coordinate end) {
    this.start = start;
    this.mid = mid;
    this.end = end;
    this.circular = ArcGeometry.isCircular(start, mid, end);
    this.straight = circular ? null : new StraightOrientableSegment(start, end);
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

  public boolean isCircular() {
    return circular;
  }

  public int orientationIndex(Coordinate q) {
    if (!circular) {
      return straight.orientationIndex(q);
    }
    Coordinate on = ArcGeometry.nearestOnArc(q, start, mid, end);
    double[] t = ArcGeometry.directedTangentAt(on, start, mid, end);
    if (t == null) {
      return Orientation.COLLINEAR;
    }
    // Robust 2×2 determinant sign — same filter family as Orientation.
    int s = CGAlgorithmsDD.signOfDet2x2(t[0], t[1], q.x - on.x, q.y - on.y);
    if (s > 0) {
      return Orientation.COUNTERCLOCKWISE;
    }
    if (s < 0) {
      return Orientation.CLOCKWISE;
    }
    return Orientation.COLLINEAR;
  }

  public boolean intersects(OrientableSegment other) {
    if (!circular) {
      return straight.intersects(other);
    }
    if (other instanceof StraightOrientableSegment) {
      StraightOrientableSegment s = (StraightOrientableSegment) other;
      if (ArcGeometry.intersectsSegment(start, mid, end, s.getStart(), s.getEnd())) {
        return true;
      }
      return endpointTouch(s);
    }
    if (other instanceof ArcOrientableSegment) {
      ArcOrientableSegment a = (ArcOrientableSegment) other;
      if (!a.circular) {
        return intersects(a.straight);
      }
      return ArcGeometry.intersectsArc(start, mid, end, a.start, a.mid, a.end)
          || endpointTouchArc(a);
    }
    return other.intersects(this);
  }

  private boolean endpointTouch(StraightOrientableSegment s) {
    RobustLineIntersector li = new RobustLineIntersector();
    // Degenerate zero-length “segments” at endpoints vs s
    li.computeIntersection(start, start, s.getStart(), s.getEnd());
    if (li.hasIntersection()) {
      return true;
    }
    li.computeIntersection(end, end, s.getStart(), s.getEnd());
    return li.hasIntersection();
  }

  private boolean endpointTouchArc(ArcOrientableSegment a) {
    return ArcGeometry.distancePointToArc(start, a.start, a.mid, a.end) == 0.0
        || ArcGeometry.distancePointToArc(end, a.start, a.mid, a.end) == 0.0
        || ArcGeometry.distancePointToArc(a.start, start, mid, end) == 0.0
        || ArcGeometry.distancePointToArc(a.end, start, mid, end) == 0.0;
  }
}
