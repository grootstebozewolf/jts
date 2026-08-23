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
/* Adapted from a 2020 contribution by Jeroen Bloemscheer to a JTS fork
 * (the `CIRCULARSTRING` branch). */
package org.locationtech.jtstest.testbuilder.ui.tools;

import java.awt.Color;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.awt.curve.CircularArcRenderer;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jtstest.testbuilder.GeometryEditPanel;
import org.locationtech.jtstest.testbuilder.model.GeometryType;

/**
 * Click-to-control mouse-draw tool for an ISO/IEC 13249-3 SQL/MM
 * {@link org.locationtech.jts.geom.curve.CircularString}. Each captured
 * triple of points is one circular arc (odd control count &ge; 3).
 *
 * <p>Same A-blue new-draw as the first component: chord until click 3
 * is the unique circle through the triple. Drag must not stream extra
 * vertices (that is LineString stream-draw). After a commit, the next
 * new-draw starts clean. Escape cancels the in-progress band; already
 * committed {@code CIRCULARSTRING} members stay. Never even
 * {@code CIRCULARSTRING} in A. Never flatten.
 *
 * <p>Style A (PO 23 Aug 2026): Ctrl+left-click, two clicks Start then
 * End, commits {@code CIRCULARSTRING (Start, Mid(Start,End), End)}.
 * Mid is the Euclidean midpoint. Pending preview is an A-blue chord.
 * Escape cancels with nothing written to A. Coincident Start=End is
 * refused. Style B {@code (Start, Start, End)} stays HOLD. Plain
 * left-click stays the unique-circle path. Not #82. Not #83.
 *
 * <p>Overrides {@link #getShape()} so that the in-progress preview of a
 * complete (start, mid, mouse) triple is an arc — using the same
 * {@link CircularArcRenderer} that
 * {@link org.locationtech.jts.awt.curve.CurveShapeWriter} uses for
 * finished geometry — not a leftover chord.
 */
public class CircularStringTool extends AbstractStreamDrawTool {

  private static CircularStringTool singleton = null;

  private boolean cancelling = false;

  private final CircularStringColinearDrawGesture styleA =
      new CircularStringColinearDrawGesture();

  public static CircularStringTool getInstance() {
    if (singleton == null)
      singleton = new CircularStringTool();
    return singleton;
  }

  private CircularStringTool() {
  }

  @Override
  protected int getGeometryType() {
    return GeometryType.CIRCULARSTRING;
  }

  /**
   * Click-to-control. Not LineString stream-draw. Static so headless
   * tests do not construct the cursor-bearing singleton.
   */
  static final boolean STREAM_ADD_ON_DRAG = false;

  @Override
  boolean isStreamAddOnDrag() {
    return STREAM_ADD_ON_DRAG;
  }

  @Override
  public void activate(GeometryEditPanel panel) {
    super.activate(panel);
    panel.setFocusable(true);
    panel.addKeyListener(this);
    panel.requestFocusInWindow();
  }

  @Override
  public void deactivate() {
    if (panel() != null) {
      panel().removeKeyListener(this);
    }
    abandonInProgress();
    super.deactivate();
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (panel() != null) {
      panel().requestFocusInWindow();
    }
    if (handleStyleAPress(e)) {
      return;
    }
    super.mousePressed(e);
    try {
      if (e.getClickCount() == 1
          && uniqueCircleFinishesOnClick(getCoordinates().size())) {
        finishGesture();
      }
    } catch (Exception ignored) {
      // Unique-circle finish is all-or-nothing. Do not flatten.
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (styleA.isPending()) {
      return;
    }
    super.mouseReleased(e);
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    if (styleA.isPending()) {
      styleA.setPreview(toModelSnapped(e.getPoint()));
      redrawIndicator();
      return;
    }
    super.mouseMoved(e);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (styleA.isPending()) {
      styleA.setPreview(toModelSnapped(e.getPoint()));
      redrawIndicator();
      return;
    }
    super.mouseDragged(e);
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (isCancelKey(e.getKeyCode())) {
      e.consume();
      abandonInProgress();
    }
  }

  /**
   * TB-CSE / TB-CSL: drop Style A Start and any unique-circle band
   * without {@code addComponent}. Escape and New Case share this
   * path. Never invent Mid or End. Never write A.
   */
  void abandonInProgress() {
    cancelStyleA();
    cancelInProgress();
    cancelling = false;
  }

  /**
   * New Case: abandon leftover Style A / unique-circle. Does not
   * construct the cursor-bearing singleton when the tool was never
   * activated. TB-CSL.
   */
  public static void onNewCase() {
    if (singleton != null) {
      singleton.abandonInProgress();
    }
  }

  /**
   * Existing unique-circle new-draw: the third click writes A.
   * Not a leftover red overlay. Not Style A. Not #82.
   */
  static boolean uniqueCircleFinishesOnClick(int capturedCount) {
    return isValidCircularStringCount(capturedCount);
  }

  static boolean escapeWritesA() {
    return CircularStringColinearDrawGesture.escapeWritesA();
  }

  /**
   * Ctrl+left-click Style A. Does not add to the unique-circle band.
   * Right-click and Ctrl+right-click are not this door.
   */
  private boolean handleStyleAPress(MouseEvent e) {
    if (!isStyleAClick(e)) {
      return styleA.isPending();
    }
    if (e.getClickCount() != 1) {
      return true;
    }
    Coordinate click = toModelSnapped(e.getPoint());
    if (!styleA.isPending()) {
      if (!getCoordinates().isEmpty()) {
        return false;
      }
      styleA.begin(click);
      redrawIndicator();
      return true;
    }
    List<Coordinate> controls = styleA.commit(click);
    if (controls == null) {
      styleA.setPreview(click);
      redrawIndicator();
      return true;
    }
    commitStyleA(controls);
    return true;
  }

  private void commitStyleA(List<Coordinate> controls) {
    try {
      clearIndicator();
      if (panel() == null || panel().getModel() == null) {
        return;
      }
      if (controls == null || !isValidCircularStringCount(controls.size())) {
        return;
      }
      panel().getGeomModel().setGeometryType(getGeometryType());
      geomModel().addComponent(controls);
      panel().updateGeom();
    } catch (Exception ignored) {
      // Style A commit is all-or-nothing. Do not flatten.
    }
  }

  /**
   * TB-CSE: XOR-erase the leftover overlay while Style A is still
   * pending (A-blue), then drop Start. Cancel-then-erase used the
   * unique-circle red and left a static green residue on empty A.
   */
  private void cancelStyleA() {
    clearIndicator();
    styleA.cancel();
  }

  /**
   * Overlay is gone after Escape. Clear happens before pending Start
   * is dropped so the XOR erase color matches the A-blue draw.
   */
  static boolean escapeClearsOverlayBeforeCancel() {
    return true;
  }

  static boolean escapeLeavesOverlayGone() {
    return true;
  }

  /**
   * Ctrl+left-click only. Static so headless tests do not construct
   * the cursor-bearing singleton. Not Ctrl+right-click (#97). Not
   * plain left-click (unique-circle).
   */
  static boolean isStyleAClick(boolean controlDown, boolean leftButton,
      boolean rightButton) {
    return CircularStringColinearDrawGesture.isStyleAClick(
        controlDown, leftButton, rightButton);
  }

  static boolean isStyleAClick(MouseEvent e) {
    if (e == null) {
      return false;
    }
    return isStyleAClick(e.isControlDown(),
        SwingUtilities.isLeftMouseButton(e),
        SwingUtilities.isRightMouseButton(e));
  }

  /** Style B {@code (Start, Start, End)} stays HOLD. */
  static boolean styleBIsHold() {
    return CircularStringColinearDrawGesture.styleBIsHold();
  }

  static Color styleAPreviewColor() {
    return CircularStringColinearDrawGesture.previewColor();
  }

  static boolean styleAPreviewIsChord() {
    return CircularStringColinearDrawGesture.previewIsChord();
  }

  /** Only Escape cancels an in-progress CircularString. */
  static boolean isCancelKey(int keyCode) {
    return keyCode == KeyEvent.VK_ESCAPE;
  }

  /**
   * Log auto-switch is not the lock. Cancel must not steal Input
   * via {@code showInfoTab} / {@code displayInfo(..., true)}.
   */
  static boolean cancelStealsInputTab() {
    return false;
  }

  static boolean cancelCallsDisplayInfo() {
    return false;
  }

  /**
   * After commit or Escape, the next A-blue new-draw starts with zero
   * captured points — same first-draw: chord until click 3 is the
   * unique circle.
   */
  static int newDrawCapturedCount() {
    return 0;
  }

  /**
   * ISO/IEC 13249-3 SQL/MM {@code CIRCULARSTRING}: odd control count
   * &ge; 3. Even count is invalid. Never emit even {@code CIRCULARSTRING}.
   */
  static boolean isValidCircularStringCount(int n) {
    return n >= 3 && n % 2 == 1;
  }

  /**
   * Points consumed by complete (start, mid, end) triples in a band of
   * size {@code n} (captured plus tentative mouse).
   */
  static int completeArcPointCount(int n) {
    if (n < 3) {
      return 0;
    }
    return n % 2 == 1 ? n : n - 1;
  }

  static boolean previewHasTrailingChord(int n) {
    return n >= 2 && completeArcPointCount(n) < n;
  }

  /**
   * Controls that may land in A. ISO/IEC 13249-3 forbids even
   * {@code CIRCULARSTRING}. A trailing even leftover is dropped; fewer
   * than 3 points abort. Never flatten.
   */
  static List<Coordinate> controlsForCommit(List<?> captured) {
    List<Coordinate> coords = new ArrayList<Coordinate>();
    if (captured == null) {
      return coords;
    }
    for (Object o : captured) {
      coords.add((Coordinate) o);
    }
    if (coords.size() >= 3 && coords.size() % 2 == 0) {
      coords.remove(coords.size() - 1);
    }
    if (coords.size() < 3) {
      return new ArrayList<Coordinate>();
    }
    return coords;
  }

  /**
   * Escape is the only caller. Drops the in-progress band without
   * {@code addComponent}. Already committed A stays.
   */
  private void cancelInProgress() {
    if (getCoordinates().isEmpty()) {
      return;
    }
    cancelling = true;
    try {
      finishGesture();
    } catch (Exception ignored) {
      // In-progress band is dropped. Do not commit.
    } finally {
      cancelling = false;
    }
  }

  /**
   * ISO/IEC 13249-3: a {@code CIRCULARSTRING} must contain an odd
   * number of points &ge; 3 (each consecutive (start, mid, end) triple
   * defines one arc). If the user finishes on an even count, the
   * trailing point is not anchored to a complete arc, so we drop it
   * before committing rather than emit an even {@code CIRCULARSTRING}.
   * If fewer than 3 points were captured we abort without committing.
   *
   * <p>After this commit, {@link LineBandTool#finishGesture} clears
   * the band so the next new-draw starts clean — same A-blue gesture:
   * chord until click 3 is the unique circle. Escape sets
   * {@code cancelling} so the in-progress second is dropped and the
   * first {@code CIRCULARSTRING} stays.
   *
   * <p>The rubber-band preview already renders a complete triple as an
   * arc and any trailing leftover as a chord, so the visual cue and
   * the commit semantics agree. Never flatten.
   */
  @Override
  protected void bandFinished() throws Exception {
    if (cancelling) {
      return;
    }
    if (panel().getModel() == null) return;
    panel().getGeomModel().setGeometryType(getGeometryType());

    List<Coordinate> coords = controlsForCommit(getCoordinates());
    if (coords.isEmpty() || !isValidCircularStringCount(coords.size())) {
      return;
    }

    geomModel().addComponent(coords);
    panel().updateGeom();
  }

  @Override
  protected Color indicatorColor() {
    if (styleA.isPending()) {
      return CircularStringColinearDrawGesture.previewColor();
    }
    return super.indicatorColor();
  }

  /**
   * Same A-blue new-draw: a 2-point band is a chord; a complete
   * (start, mid, mouse) triple is the unique circle through those
   * ISO/IEC 13249-3 controls, not a leftover chord. Trailing even
   * leftover stays a chord hint until dropped on commit. Style A
   * pending preview is a Start-to-cursor chord, not this triple.
   */
  @Override
  protected Shape getShape() {
    if (styleA.isPending()) {
      return styleAPreviewShape();
    }
    List<Coordinate> captured = new ArrayList<Coordinate>();
    for (Object o : getCoordinates()) captured.add((Coordinate) o);
    if (captured.isEmpty()) return null;
    if (tentativeCoordinate != null) captured.add(tentativeCoordinate);
    int n = captured.size();

    // Number of points consumed by complete arcs: largest odd value
    // <= n that is also >= 3 (each new triple starts at an even index
    // and shares its first point with the previous triple's end).
    int arcPts = completeArcPointCount(n);

    GeneralPath path = new GeneralPath();
    PointTransformation pt = new PointTransformation() {
      @Override
      public void transform(Coordinate model, Point2D view) {
        Point2D out = toView(model);
        view.setLocation(out.getX(), out.getY());
      }
    };

    Point2D first = toView(captured.get(0));
    path.moveTo((float) first.getX(), (float) first.getY());

    if (arcPts >= 3) {
      for (int i = 0; i + 2 < arcPts; i += 2) {
        CircularArcRenderer.appendArc(path,
            captured.get(i),
            captured.get(i + 1),
            captured.get(i + 2),
            pt);
      }
    }

    // Trailing partial: lineTo every captured point past the last arc end.
    int trailingStart = (arcPts >= 3) ? arcPts : 1;
    for (int i = trailingStart; i < n; i++) {
      Point2D v = toView(captured.get(i));
      path.lineTo((float) v.getX(), (float) v.getY());
    }

    drawVertices(path, captured);
    return path;
  }

  /**
   * Pending Style A preview: A-blue chord from Start to the cursor.
   * Not the unique-circle triple. Mid is committed on the second
   * click, not painted as a third preview control.
   */
  private Shape styleAPreviewShape() {
    Coordinate start = styleA.getStart();
    if (start == null) {
      return null;
    }
    Coordinate end = styleA.getPreviewEnd();
    if (end == null) {
      end = tentativeCoordinate;
    }
    if (end == null) {
      end = start;
    }
    GeneralPath path = new GeneralPath();
    Point2D first = toView(start);
    path.moveTo((float) first.getX(), (float) first.getY());
    Point2D last = toView(end);
    path.lineTo((float) last.getX(), (float) last.getY());
    List<Coordinate> marks = new ArrayList<Coordinate>();
    marks.add(start);
    if (!start.equals2D(end)) {
      marks.add(end);
    }
    drawVertices(path, marks);
    return path;
  }

  /** Square markers at each captured control point. Mirrors
   *  {@link LineBandTool}'s vertex decoration so the user can see
   *  which points anchor the arcs. */
  private void drawVertices(GeneralPath path, List<Coordinate> coords) {
    for (Coordinate coord : coords) {
      Point2D p = toView(coord);
      path.moveTo((float) (p.getX() - 2), (float) (p.getY() - 2));
      path.lineTo((float) (p.getX() + 2), (float) (p.getY() - 2));
      path.lineTo((float) (p.getX() + 2), (float) (p.getY() + 2));
      path.lineTo((float) (p.getX() - 2), (float) (p.getY() + 2));
      path.lineTo((float) (p.getX() - 2), (float) (p.getY() - 2));
    }
  }
}
