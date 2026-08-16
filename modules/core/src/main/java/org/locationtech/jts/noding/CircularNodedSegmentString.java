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

import org.locationtech.jts.geom.Coordinate;

/**
 * A {@link NodedSegmentString} that can carry a circular arc on
 * {@code [i, i+1]}. OverlayNG already accepts {@code NodedSegmentString};
 * this subtype is the honest form of that acceptance for circles:
 * a segment may be an arc, not a chord pretending to be one.
 * <p>
 * Coordinates stay the segment ends. The midpoint is metadata, not a
 * vertex, so a semicircle whose chord is also an input edge is not
 * collapsed into that chord. Stock {@link IntersectionAdder} must not
 * treat those ends as the arc; OverlayNGCurve supplies the noder that
 * asks {@link #isCircularArc(int)}.
 * <p>
 * Not a rewrite of every noder in this package. Not a public circular
 * noder API.
 */
public class CircularNodedSegmentString extends NodedSegmentString {

  private final Coordinate[] mids;

  /**
   * One circular arc from {@code start} through {@code mid} to
   * {@code end}.
   *
   * @param start the arc start
   * @param mid a point on the arc between the ends
   * @param end the arc end
   * @param data the user-defined data of this segment string (may be null)
   */
  public CircularNodedSegmentString(Coordinate start, Coordinate mid,
      Coordinate end, Object data) {
    super(new Coordinate[] { start, end }, data);
    this.mids = new Coordinate[] { mid };
  }

  /**
   * Chord segments. Same linework as {@link NodedSegmentString}; every
   * {@link #isCircularArc(int)} is false.
   *
   * @param pts the vertices of the segment string
   * @param data the user-defined data of this segment string (may be null)
   */
  public CircularNodedSegmentString(Coordinate[] pts, Object data) {
    super(pts, data);
    this.mids = new Coordinate[pts.length - 1];
  }

  /**
   * True when segment {@code [segIndex, segIndex+1]} is a circular
   * arc rather than a straight chord.
   *
   * @param segIndex the segment index
   * @return true if that segment is an arc
   */
  public boolean isCircularArc(int segIndex) {
    return segIndex >= 0 && segIndex < mids.length && mids[segIndex] != null;
  }

  /**
   * The on-arc midpoint of segment {@code segIndex}, or {@code null}
   * when that segment is a chord.
   *
   * @param segIndex the segment index
   * @return the midpoint, or {@code null}
   */
  public Coordinate getArcMidpoint(int segIndex) {
    if (!isCircularArc(segIndex)) {
      return null;
    }
    return mids[segIndex];
  }
}
