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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Triangle;

/**
 * Shared analytical helpers for circular arcs (circumcentre, sweep, point-on-arc,
 * arc length contrib etc). Used by distance overrides (D-PT/D-AA), area (M-AREA-CP),
 * precision reduce (PRC-SN #66), and length.
 * <p>
 * Delegates circumcentre to core Triangle (exact match for grid-friendly checks).
 * Other primitives (sweep clamp, pointOnArcInterior) implemented here for curve module
 * (no core change).
 */
public final class CircularArcs {

  private CircularArcs() {}

  /** Circumcentre of 3 control points on a circle (delegates core for consistency). */
  public static Coordinate circumcentre(Coordinate p0, Coordinate p1, Coordinate p2) {
    return Triangle.circumcentre(p0, p1, p2);
  }

  // Additional helpers can be added for D-PT etc (sweep, pointOnArc) without
  // changing this file signature for PRC-SN harden.
}
