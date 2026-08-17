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

import org.locationtech.jts.geom.Coordinate;

/**
 * Thin optional directed-piece protocol for side and intersect only.
 * <p>
 * Per {@code doc/EXACT_CURVE_BIBLE.md} §3, this is <b>not</b> the
 * privileged curve representation. Prefer
 * {@link org.locationtech.jts.algorithm.exactcurve.ExactCircularArc}
 * (and later Exact* siblings). Densify oracles stay out of this
 * interface.
 *
 * @see org.locationtech.jts.algorithm.exactcurve.ExactCurve
 */
public interface OrientableSegment {

  Coordinate getStart();

  Coordinate getEnd();

  /**
   * {@link org.locationtech.jts.algorithm.Orientation} codes for
   * {@code q} relative to this directed piece.
   */
  int orientationIndex(Coordinate q);

  /** True when this piece and {@code other} share a point. */
  boolean intersects(OrientableSegment other);
}
