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
 * Thin optional adapter for side and intersect only
 * ({@code doc/EXACT_CURVE_BIBLE.md} §3). Prefer
 * {@link org.locationtech.jts.algorithm.exactcurve.ExactCircularArc}.
 * Public members: start, end, length, orientationIndex, intersects.
 */
public interface OrientableSegment {

  Coordinate getStart();

  Coordinate getEnd();

  double length();

  int orientationIndex(Coordinate q);

  boolean intersects(OrientableSegment other);
}
