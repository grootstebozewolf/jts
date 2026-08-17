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
 * Proofs Option B — directed piece that can answer orientation and
 * intersection without forcing a single linear representation.
 * Package-private API surface for the B-team seam; not a public Noder.
 */
public interface OrientableSegment {

  Coordinate getStart();

  Coordinate getEnd();

  /**
   * {@link org.locationtech.jts.algorithm.Orientation} codes:
   * clockwise / collinear / counterclockwise of {@code q} relative to
   * this directed piece (infinite supporting line for straight;
   * tangent frame at nearest point for arcs).
   */
  int orientationIndex(Coordinate q);

  /**
   * True when this piece and {@code other} have a point in common
   * (including endpoints). Straight×straight matches RobustLineIntersector
   * "hasIntersection". Arc cases use circle–segment closed form + sweep.
   */
  boolean intersects(OrientableSegment other);

  /** Straight chord control polyline for densify-reference trials. */
  Coordinate[] densifyControls(int nChord);
}
