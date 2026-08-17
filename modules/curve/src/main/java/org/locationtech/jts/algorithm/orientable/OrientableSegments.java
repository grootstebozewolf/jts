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
 * Factories for Proofs Option B carriers. Arc form wraps A's
 * {@link ExactCircularArc}.
 */
public final class OrientableSegments {

  private OrientableSegments() { }

  public static OrientableSegment straight(Coordinate a, Coordinate b) {
    return new StraightOrientableSegment(a, b);
  }

  public static OrientableSegment arc(Coordinate start, Coordinate mid,
      Coordinate end) {
    return new ArcOrientableSegment(start, mid, end);
  }

  /** Preferred when the caller already built an A-team arc. */
  public static OrientableSegment arc(ExactCircularArc exact) {
    return new ArcOrientableSegment(exact);
  }
}
