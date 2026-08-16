/*
 * Copyright (c) 2016 Vivid Solutions.
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
 * An interface for classes which represent a sequence of contiguous line segments.
 * SegmentStrings can carry a context object, which is useful
 * for preserving topological or parentage information.
 * <p>
 * <b>Option B (P2.5.5).</b> {@code [i, i+1]} may name an
 * {@linkplain SegmentKind#ARC arc}, a
 * {@linkplain SegmentKind#CERTIFIED certified primitive}, or a
 * {@linkplain SegmentKind#LINEARIZED linear fallback}, explicitly.
 * The default is linearized: that is a choice, not a claim that the
 * chord is the curve. A noder may collapse a segment to its chord
 * only when {@link #mayCollapseToChord(int)} is true. OverlayNG
 * (via {@code EdgeNodingBuilder}) is the only consumer of exact
 * circular edges. Everyone else stays on linearization.
 *
 * @version 1.7
 * @see SegmentKind
 */
public interface SegmentString
{
  /**
   * Gets the user-defined data for this segment string.
   *
   * @return the user-defined data
   */
  public Object getData();

  /**
   * Sets the user-defined data for this segment string.
   *
   * @param data an Object containing user-defined data
   */
  public void setData(Object data);

  /**
   * Gets the number of coordinates in this segment string.
   * 
   * @return the number of coordinates
   */
  public int size();
  
  /**
   * Gets the segment string coordinate at a given index.
   *  
   * @param i the coordinate index
   * @return the coordinate at the index
   */
  public Coordinate getCoordinate(int i);
  
  /**
   * Gets the coordinates in this segment string.
   * 
   * @return the coordinates as an array
   */
  public Coordinate[] getCoordinates();
  
  /**
   * Tests if a segment string is a closed ring.
   * 
   * @return true if the segment string is closed
   */
  public boolean isClosed();
  
  /**
   * Gets the previous vertex in a ring from a vertex index.
   * 
   * @param ringSS a segment string forming a ring
   * @param index the vertex index
   * @return the previous vertex in the ring
   * 
   * @see #isClosed
   */
  public default Coordinate prevInRing(int index) {
    int prevIndex = index - 1;
    if (prevIndex < 0) {
      prevIndex = size() - 2;
    }
    return getCoordinate( prevIndex );
  }

  /**
   * Gets the next vertex in a ring from a vertex index.
   * 
   * @param ringSS a segment string forming a ring
   * @param index the vertex index
   * @return the next vertex in the ring
   * 
   * @see #isClosed
   */
  public default Coordinate nextInRing(int index) {
    int nextIndex = index + 1;
    if (nextIndex > size() - 1) {
      nextIndex = 1;
    }
    return getCoordinate( nextIndex );
  }

  /**
   * What segment {@code [i, i+1]} names. Default
   * {@link SegmentKind#LINEARIZED}: linearization is the explicit
   * fallback, not a lie that the chord is the curve.
   *
   * @param i the segment index
   * @return the kind of that segment
   */
  public default SegmentKind getSegmentKind(int i) {
    return SegmentKind.LINEARIZED;
  }

  /**
   * True when segment {@code [i, i+1]} is an exact arc or a
   * certified primitive, not a linearized fallback.
   *
   * @param i the segment index
   * @return true if that segment is exact
   */
  public default boolean isExact(int i) {
    return getSegmentKind(i).isExact();
  }

  /**
   * True when segment {@code [i, i+1]} is the explicit linear
   * fallback.
   *
   * @param i the segment index
   * @return true if that segment is linearized
   */
  public default boolean isLinearized(int i) {
    return getSegmentKind(i) == SegmentKind.LINEARIZED;
  }

  /**
   * True when a noder that only understands chords may replace
   * this segment with its linearized ends. Default is true
   * (linearization is default earth). Exact arcs and certified
   * primitives return false: collapsing them to a chord is the lie.
   *
   * @param i the segment index
   * @return true if a noder may collapse this segment to a chord
   */
  public default boolean mayCollapseToChord(int i) {
    return isLinearized(i);
  }

  /**
   * The on-arc midpoint of segment {@code [i, i+1]}, or
   * {@code null} when that segment is not an {@link SegmentKind#ARC}.
   *
   * @param i the segment index
   * @return the midpoint, or {@code null}
   */
  public default Coordinate getArcMidpoint(int i) {
    return null;
  }
}
