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
package org.locationtech.jts.algorithm.exactcurve;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

/**
 * Thin ExactCurve* protocol. Conceptually sealed: only Exact* value
 * types implement it. Do not add methods. Do not introduce a rich
 * abstract base.
 * <p>
 * See {@code doc/EXACT_CURVE_BIBLE.md} §4.2.
 */
public interface ExactCurve {

  /** Start control of this piece. */
  Coordinate getStart();

  /** End control of this piece. */
  Coordinate getEnd();

  /**
   * Closed-form length. Must not call {@link #toLinear(double)}.
   */
  double length();

  /**
   * Point at arc-length fraction {@code t ∈ [0, 1]}.
   *
   * @throws IllegalArgumentException if {@code t} is not in {@code [0, 1]}
   */
  Coordinate pointAt(double t);

  /**
   * Documented densify shim. The only allowed linearisation path.
   * Implementations that claim {@link #isExact()} must not call this
   * from {@link #length()}, {@link #pointAt(double)}, or other exact
   * cells.
   */
  Geometry toLinear(double tolerance);

  /**
   * {@code true} when operations on this instance are closed-form (or
   * an exact chord fallback), never a hidden densify.
   */
  boolean isExact();
}
