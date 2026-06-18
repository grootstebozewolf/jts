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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

/**
 * Curve-aware Douglas–Peucker simplification (S-DP, JTS #1195).
 * <p>
 * The core {@link DouglasPeuckerSimplifier} works on the coordinate array, so on
 * a curved geometry it would simplify the <i>control-point polyline</i> — dropping
 * an arc's mid control point collapses the arc to its chord and destroys the arc
 * identity. A circular arc is already the minimal exact representation of its
 * curve (three control points), so there is nothing to simplify. This shadow
 * entry point therefore returns a {@link Linearizable} curved input unchanged
 * (preserving {@code CircularString} / {@code CompoundCurve} / {@code CurvePolygon}
 * type and arcs) and delegates any other geometry to the core simplifier.
 */
public final class CurvedDouglasPeuckerSimplifier {

  private CurvedDouglasPeuckerSimplifier() {}

  /**
   * Simplifies {@code geom} preserving arc identity: a curved input is returned
   * unchanged (a copy), everything else is simplified by the core
   * {@link DouglasPeuckerSimplifier}.
   */
  public static Geometry simplify(Geometry geom, double distanceTolerance) {
    if (geom instanceof Linearizable) {
      return geom.copy();
    }
    return DouglasPeuckerSimplifier.simplify(geom, distanceTolerance);
  }
}
