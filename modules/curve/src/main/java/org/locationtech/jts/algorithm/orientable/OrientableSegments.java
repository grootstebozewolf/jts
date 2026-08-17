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

import org.locationtech.jts.algorithm.exactcurve.ExactCircularArc;
import org.locationtech.jts.geom.Coordinate;

/**
 * Sole public entry for the thin OrientableSegment adapter.
 */
public final class OrientableSegments {

  private OrientableSegments() { }

  public static OrientableSegment straight(Coordinate a, Coordinate b) {
    return new StraightOrientableSegment(a, b);
  }

  /** Preferred: compose an existing ExactCircularArc. */
  public static OrientableSegment arc(ExactCircularArc exact) {
    return new ArcOrientableSegment(exact);
  }

  public static OrientableSegment arc(Coordinate start, Coordinate mid,
      Coordinate end) {
    return arc(new ExactCircularArc(start, mid, end));
  }
}
