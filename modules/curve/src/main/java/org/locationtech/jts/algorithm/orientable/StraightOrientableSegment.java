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

/**
 * Package-private straight adapter. Construct via {@link OrientableSegments}.
 */
final class StraightOrientableSegment implements OrientableSegment {

  private final Coordinate p0;
  private final Coordinate p1;

  StraightOrientableSegment(Coordinate p0, Coordinate p1) {
    this.p0 = p0;
    this.p1 = p1;
  }

  public Coordinate getStart() {
    return p0;
  }

  public Coordinate getEnd() {
    return p1;
  }

  public double length() {
    return p0.distance(p1);
  }

  public int orientationIndex(Coordinate q) {
    return Orientation.index(p0, p1, q);
  }

  public boolean intersects(OrientableSegment other) {
    if (other instanceof StraightOrientableSegment) {
      StraightOrientableSegment s = (StraightOrientableSegment) other;
      RobustLineIntersector li = new RobustLineIntersector();
      li.computeIntersection(p0, p1, s.p0, s.p1);
      return li.hasIntersection();
    }
    return other.intersects(this);
  }
}
