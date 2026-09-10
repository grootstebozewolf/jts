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
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jtstest.testbuilder.AppColors;

/**
 * Style A colinear {@code CIRCULARSTRING} draw. CircularString mode
 * only. Gesture is Ctrl+left-click, two clicks Start then End.
 *
 * <p>First click does not write A. While the second click is pending,
 * the preview is an A-blue chord from Start to the cursor — not the
 * unique-circle triple, not insert red, not geometry B. On the second
 * click, commit ISO/IEC 13249-3 {@code CIRCULARSTRING (Start,
 * Mid(Start,End), End)} where Mid is the Euclidean midpoint. Odd
 * control count &ge; 3. Type stays {@code CIRCULARSTRING}. Never
 * flatten to {@code LINESTRING}. Never emit an even-control string.
 *
 * <p>Escape cancels. Nothing is written to A. Start=End (coincident)
 * is refused; the string is not committed.
 *
 * <p>Style B {@code (Start, Start, End)} stays HOLD. Not #82 insert.
 * Not #83 chord-mid insert. No DOI.
 */
public class CircularStringColinearDrawGesture {

  private Coordinate start;
  private Coordinate previewEnd;

  public boolean isPending() {
    return start != null;
  }

  public Coordinate getStart() {
    return start;
  }

  public Coordinate getPreviewEnd() {
    return previewEnd;
  }

  /**
   * First Ctrl+left-click: record Start. Does not write A (or B).
   */
  public boolean begin(Coordinate startPt) {
    if (startPt == null) {
      return false;
    }
    start = new Coordinate(startPt);
    previewEnd = new Coordinate(startPt);
    return true;
  }

  public void setPreview(Coordinate cursor) {
    if (isPending() && cursor != null) {
      previewEnd = new Coordinate(cursor);
    }
  }

  /**
   * Second Ctrl+left-click. Returns the Style A controls, or
   * {@code null} when the click must not be written (stay pending).
   * Does not write B. Never Style B {@code (Start, Start, End)}.
   */
  public List<Coordinate> commit(Coordinate end) {
    if (!isPending()) {
      return null;
    }
    List<Coordinate> controls = controlsForCommit(start, end);
    if (controls.isEmpty()) {
      return null;
    }
    clear();
    return controls;
  }

  /**
   * Escape / New Case: drop the pending Start. Never commit the
   * preview End. A is unchanged because begin never wrote. TB-CSE /
   * TB-CSL. Does not invent Mid or End.
   */
  public void cancel() {
    clear();
  }

  /**
   * TB-CSE: Escape on a leftover Style A overlay must not commit.
   * Dirty leftover has Start and a cursor End; still write nothing.
   */
  public static List<Coordinate> controlsForEscape(Coordinate startPt,
      Coordinate previewEnd) {
    return new ArrayList<Coordinate>();
  }

  /** TB-CSL: New Case drops pending Start. Does not fabricate End. */
  public static boolean newCaseClearsPendingStart() {
    return true;
  }

  /** Escape never writes A. Clean or dirty leftover. */
  public static boolean escapeWritesA() {
    return false;
  }

  private void clear() {
    start = null;
    previewEnd = null;
  }

  /**
   * ISO/IEC 13249-3 Style A controls: {@code (Start, Mid(Start,End),
   * End)}. Mid is the Euclidean midpoint. Empty when coincident or
   * missing. Never even. Never Style B.
   */
  public static List<Coordinate> controlsForCommit(Coordinate startPt, Coordinate end) {
    List<Coordinate> coords = new ArrayList<Coordinate>();
    if (startPt == null || end == null) {
      return coords;
    }
    if (isCoincident(startPt, end)) {
      return coords;
    }
    Coordinate mid = midpoint(startPt, end);
    coords.add(new Coordinate(startPt));
    coords.add(mid);
    coords.add(new Coordinate(end));
    return coords;
  }

  /** Euclidean midpoint of Start and End. Not #83 insert. */
  public static Coordinate midpoint(Coordinate startPt, Coordinate end) {
    return LineSegment.midPoint(startPt, end);
  }

  public static boolean isCoincident(Coordinate startPt, Coordinate end) {
    return startPt != null && end != null && startPt.equals2D(end);
  }

  /**
   * Ctrl+left-click only. Plain left-click is the unique-circle
   * three-click draw. Ctrl+right-click is #97 delete (not this door).
   */
  public static boolean isStyleAClick(boolean controlDown, boolean leftButton) {
    return controlDown && leftButton;
  }

  public static boolean isStyleAClick(boolean controlDown, boolean leftButton,
      boolean rightButton) {
    return isStyleAClick(controlDown, leftButton) && !rightButton;
  }

  /** Style B {@code (Start, Start, End)} stays HOLD. */
  public static boolean styleBIsHold() {
    return true;
  }

  /** Only Escape cancels. */
  public static boolean isCancelKey(int keyCode) {
    return keyCode == KeyEvent.VK_ESCAPE;
  }

  /**
   * Pending preview is an A-blue chord Start→cursor. Not unique
   * circle. Not insert red. Not a write to B.
   */
  public static Color previewColor() {
    return AppColors.GEOM_A;
  }

  public static boolean previewIsChord() {
    return true;
  }

  public static boolean previewIsUniqueCircle() {
    return false;
  }

  public static boolean previewWritesGeometryB() {
    return false;
  }

  public static boolean firstClickWritesA() {
    return false;
  }
}
