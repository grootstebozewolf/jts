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
package org.locationtech.jts.noding;

/**
 * What {@code [i, i+1]} names on a {@link SegmentString}. Option B:
 * do not extend the interface to lie. Name the segment so the lie
 * is impossible. Linearization is an explicit choice, not the claim
 * that a chord is the curve.
 * <p>
 * Default earth is {@link #LINEARIZED}. OverlayNG (and only
 * OverlayNG, via {@code EdgeNodingBuilder}) may consume
 * {@link #ARC} or {@link #CERTIFIED}. Buffer, relateng, coverage,
 * prep, and snap-round stay on linearization.
 *
 * @see SegmentString#getSegmentKind(int)
 */
public enum SegmentKind {

  /**
   * Explicit linear fallback. The coordinates are a chord, and a
   * noder may collapse the curve to that chord.
   */
  LINEARIZED,

  /**
   * Exact circular arc. Ends are {@code getCoordinate(i)} and
   * {@code getCoordinate(i+1)}; the on-arc midpoint is
   * {@link SegmentString#getArcMidpoint(int)}.
   */
  ARC,

  /**
   * Exact certified primitive that is not an arc (a true line
   * member, or a kit-certified piece). Not a linearized curve.
   */
  CERTIFIED;

  /**
   * True for {@link #ARC} and {@link #CERTIFIED}.
   *
   * @return true if this kind is exact
   */
  public boolean isExact() {
    return this == ARC || this == CERTIFIED;
  }
}
