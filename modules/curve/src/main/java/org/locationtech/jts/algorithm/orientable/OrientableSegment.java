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
 * Proofs Option B — directed piece answering orientation and
 * intersection without forcing a single linear representation.
 * Densify references live in tests only — not on this interface.
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
