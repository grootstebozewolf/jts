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

import java.util.Collections;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

/**
 * Implemented by geometry types that can be approximated by a non-curved
 * (linear) geometry to within a given coordinate tolerance.
 *
 * <h3>Tolerance</h3>
 * Implementations should treat <code>tolerance</code> as the maximum
 * permissible perpendicular distance between the original curved
 * geometry and its linear approximation (the chord's <em>sagitta</em>).
 * A value of <code>0.0</code> means "use the implementation's default
 * tolerance". Negative values are reserved (an
 * {@link IllegalArgumentException} is the expected response).
 *
 * <h3>Must-include points</h3>
 * The {@link #toLinear(double, List)} overload accepts a list of
 * coordinates that the caller wants to be present in the linearised
 * output. A coordinate is honoured iff its perpendicular projection
 * onto the curve lies within {@code tolerance}; the projected point is
 * inserted into the polyline at its parametric position. Coordinates
 * further from the curve than {@code tolerance} are silently dropped —
 * the contract is "guarantee inclusion when the point is plausibly on
 * the curve," not "snap arbitrary points onto it."
 *
 * <p>The default implementation of the must-include overload delegates
 * to the single-argument variant. Phase-3 implementations that can do
 * better — currently only {@link CircularString} — override it.
 */
public interface Linearizable {

  /**
   * Returns a non-curved geometry that approximates this geometry to
   * within {@code tolerance} units of distance.
   *
   * @param tolerance maximum permissible chord-error distance;
   *                  <code>0.0</code> selects the implementation default
   * @return a linearised {@link Geometry} (never {@code null})
   * @throws IllegalArgumentException if {@code tolerance} is negative
   */
  Geometry toLinear(double tolerance);

  /**
   * Same as {@link #toLinear(double)}, additionally guaranteeing that
   * every coordinate in {@code mustInclude} that lies within
   * {@code tolerance} of the curve appears (at its projected position)
   * in the linearised output.
   *
   * <p>Coordinates whose perpendicular distance to the curve exceeds
   * {@code tolerance} are silently dropped. Implementations that do not
   * yet support must-include semantics fall back to
   * {@link #toLinear(double)} via the default implementation here.
   *
   * @param tolerance   maximum permissible chord-error distance
   * @param mustInclude coordinates that must appear in the output
   *                    polyline if they are close enough to the curve;
   *                    {@code null} is treated as the empty list
   * @return a linearised {@link Geometry} (never {@code null})
   */
  default Geometry toLinear(double tolerance, List<Coordinate> mustInclude) {
    return toLinear(tolerance);
  }

  /**
   * Convenience: empty must-include list.
   */
  default Geometry toLinear(double tolerance, Coordinate mustInclude) {
    return toLinear(tolerance,
        mustInclude == null ? Collections.<Coordinate>emptyList()
                             : Collections.singletonList(mustInclude));
  }
}
