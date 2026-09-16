/*
 * Copyright (c) 2026 Martin Davis and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.algorithm.exactcurve;

import org.locationtech.jts.geom.Envelope;

/**
 * Thin Year-1 contract for an exact curve primitive.
 * <p>
 * This is intentionally not a rich abstract base: it exists so
 * {@link org.locationtech.jts.geom.CircularString} can compose
 * {@link ExactCircularArc} windows. Do not grow extra curve kinds here.
 *
 * @author Martin Davis
 */
public interface ExactCurve {

  /**
   * Returns the exact length of this primitive.
   * Collinear / degenerate windows use the control-point chord path,
   * not a densified approximation.
   *
   * @return the length
   */
  double getLength();

  /**
   * Tests whether this primitive degenerates to a straight chord
   * (collinear or otherwise non-circular controls).
   *
   * @return {@code true} if the window is a chord
   */
  boolean isCollinear();

  /**
   * Expands {@code envelope} to include this primitive
   * (arc extrema, not only control points).
   *
   * @param envelope the envelope to expand
   */
  void expandEnvelope(Envelope envelope);
}
