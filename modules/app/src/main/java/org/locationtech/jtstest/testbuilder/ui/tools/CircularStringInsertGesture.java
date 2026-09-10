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
package org.locationtech.jtstest.testbuilder.ui.tools;

import java.awt.Color;
import java.awt.event.KeyEvent;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jtstest.testbuilder.AppConstants;
import org.locationtech.jtstest.testbuilder.geom.GeometryLocation;

/**
 * UX issue #82 two-click red insert on an existing
 * {@code CIRCULARSTRING}. First click does not write A. Red overlay
 * until the second click (overlay is not geometry B). Second click
 * commits +2 so ISO/IEC 13249-3 odd &ge; 3 WKT tokens stay odd. Escape
 * cancels. Never even, never flatten, never coincident consecutive.
 * Not #83 chord-mid.
 */
public class CircularStringInsertGesture {

  private GeometryLocation loc;
  private Coordinate first;
  private Coordinate previewSecond;

  public boolean isPending() {
    return loc != null;
  }

  public Coordinate getFirst() {
    return first;
  }

  public Coordinate getPreviewSecond() {
    return previewSecond;
  }

  public GeometryLocation getLocation() {
    return loc;
  }

  /**
   * First click: record the pair start. Does not write A (or B).
   */
  public boolean begin(GeometryLocation start) {
    if (start == null || start.isVertex() || !start.isCircularStringComponent()) {
      return false;
    }
    loc = start;
    first = start.getCoordinate();
    previewSecond = first;
    return true;
  }

  public void setPreview(Coordinate second) {
    if (isPending() && second != null) {
      previewSecond = second;
    }
  }

  /**
   * Second click. Returns the edited geometry, or {@code null} when
   * the pair must not be written (stay pending). Does not write B.
   */
  public Geometry commit(Coordinate second) {
    if (!isPending()) {
      return null;
    }
    Geometry edited = loc.insertPair(second);
    if (edited == null) {
      return null;
    }
    clear();
    return edited;
  }

  /** Escape: drop the overlay. A is unchanged because begin never wrote. */
  public void cancel() {
    clear();
  }

  private void clear() {
    loc = null;
    first = null;
    previewSecond = null;
  }

  /** Only Escape cancels. */
  public static boolean isCancelKey(int keyCode) {
    return keyCode == KeyEvent.VK_ESCAPE;
  }

  /**
   * Overlay paint. Red band — not A-blue, and not a write to B
   * (geometry B is also red).
   */
  public static Color overlayColor() {
    return AppConstants.BAND_CLR;
  }

  /** Overlay is XOR indicator, never {@code setGeometry(1, …)}. */
  public static boolean overlayWritesGeometryB() {
    return false;
  }

  public static boolean firstClickWritesA() {
    return false;
  }
}
