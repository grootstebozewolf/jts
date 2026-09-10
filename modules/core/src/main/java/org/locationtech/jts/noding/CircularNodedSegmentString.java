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
 * OverlayNG's exact {@link NodedSegmentString}: {@code [i, i+1]}
 * names an {@link SegmentKind#ARC}, a {@link SegmentKind#CERTIFIED}
 * line, or an explicit {@link SegmentKind#LINEARIZED} fallback.
 * Coordinates stay the segment ends. An arc midpoint is metadata,
 * not a vertex, so a semicircle whose chord is also an input edge
 * is not collapsed into that chord.
 * <p>
 * OverlayNG (via {@code EdgeNodingBuilder}) is the only consumer.
 * Stock extract still builds linearized {@link NodedSegmentString}.
 * Not a rewrite of every noder. Not a public circular noder API.
 */
public class CircularNodedSegmentString extends NodedSegmentString {

  private final SegmentKind[] kinds;
  private final Coordinate[] mids;

  /**
   * One exact circular arc from {@code start} through {@code mid}
   * to {@code end}.
   *
   * @param start the arc start
   * @param mid a point on the arc between the ends
   * @param end the arc end
   * @param data the user-defined data of this segment string (may be null)
   */
  public CircularNodedSegmentString(Coordinate start, Coordinate mid,
      Coordinate end, Object data) {
    this(new Coordinate[] { start, end },
        new SegmentKind[] { SegmentKind.ARC },
        new Coordinate[] { mid }, data);
  }

  /**
   * Explicit linearized chords. Same linework as
   * {@link NodedSegmentString}; every segment is
   * {@link SegmentKind#LINEARIZED}.
   *
   * @param pts the vertices of the segment string
   * @param data the user-defined data of this segment string (may be null)
   */
  public CircularNodedSegmentString(Coordinate[] pts, Object data) {
    this(pts, linearizedKinds(pts.length - 1),
        new Coordinate[pts.length - 1], data);
  }

  private CircularNodedSegmentString(Coordinate[] pts, SegmentKind[] kinds,
      Coordinate[] mids, Object data) {
    super(pts, data);
    this.kinds = kinds;
    this.mids = mids;
  }

  /**
   * Exact circular arc. {@link #mayCollapseToChord(int)} is false.
   *
   * @param start the arc start
   * @param mid a point on the arc between the ends
   * @param end the arc end
   * @param data the user-defined data (may be null)
   * @return an exact-arc segment string
   */
  public static CircularNodedSegmentString arc(Coordinate start,
      Coordinate mid, Coordinate end, Object data) {
    return new CircularNodedSegmentString(start, mid, end, data);
  }

  /**
   * Exact certified line. Not a linearized curve.
   * {@link #mayCollapseToChord(int)} is false.
   *
   * @param start the segment start
   * @param end the segment end
   * @param data the user-defined data (may be null)
   * @return a certified line segment string
   */
  public static CircularNodedSegmentString certified(Coordinate start,
      Coordinate end, Object data) {
    return new CircularNodedSegmentString(
        new Coordinate[] { start, end },
        new SegmentKind[] { SegmentKind.CERTIFIED },
        new Coordinate[] { null }, data);
  }

  /**
   * Explicit linearized fallback. A noder may collapse to the chord.
   *
   * @param pts the vertices
   * @param data the user-defined data (may be null)
   * @return a linearized segment string
   */
  public static CircularNodedSegmentString linearized(Coordinate[] pts,
      Object data) {
    return new CircularNodedSegmentString(pts, data);
  }

  @Override
  public SegmentKind getSegmentKind(int i) {
    if (i < 0 || i >= kinds.length) {
      return SegmentKind.LINEARIZED;
    }
    return kinds[i];
  }

  @Override
  public Coordinate getArcMidpoint(int i) {
    if (getSegmentKind(i) != SegmentKind.ARC) {
      return null;
    }
    return mids[i];
  }

  /**
   * True when segment {@code [segIndex, segIndex+1]} is an
   * {@link SegmentKind#ARC}.
   *
   * @param segIndex the segment index
   * @return true if that segment is an arc
   */
  public boolean isCircularArc(int segIndex) {
    return getSegmentKind(segIndex) == SegmentKind.ARC;
  }

  private static SegmentKind[] linearizedKinds(int n) {
    SegmentKind[] k = new SegmentKind[Math.max(n, 0)];
    for (int i = 0; i < k.length; i++) {
      k[i] = SegmentKind.LINEARIZED;
    }
    return k;
  }
}
