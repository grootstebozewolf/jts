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

import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.awt.curve.CircularArcRenderer;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Triangle;
import org.locationtech.jtstest.testbuilder.AppConstants;
import org.locationtech.jtstest.testbuilder.GeometryEditPanel;
import org.locationtech.jtstest.testbuilder.JTSTestBuilder;
import org.locationtech.jtstest.testbuilder.JTSTestBuilderFrame;
import org.locationtech.jtstest.testbuilder.geom.GeometryCombiner;
import org.locationtech.jtstest.testbuilder.model.GeometryType;

/**
 * Stream-style mouse-draw tool for an ISO/IEC 13249-3 SQL/MM
 * {@link org.locationtech.jts.geom.curve.CurvePolygon} with an exterior
 * shell only (no holes this slice).
 *
 * <p>Double-click anywhere, or a click on the start vertex after at
 * least three captured points, auto-closes the in-progress shell.
 * One-arc (2–3 captured points) still commits
 * {@code CURVEPOLYGON (CIRCULARSTRING …)} with a complementary close
 * when needed. Five or more odd captured points close the rubber-band
 * line back to start as a LineString member:
 * {@code CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING …, LINESTRING …))}.
 * That must not invent a complementary-arc control. A mid-gesture click
 * that is not the start vertex only adds a point — it does not commit
 * and does not cancel, even when two points already close to a valid
 * ring. Only Escape cancels, and only Escape may write
 * {@link #CANCELLED_STATUS} on the Case/PM strip via
 * {@code setStatus}. That must not call {@code displayInfo} or
 * {@code showInfoTab} — Log auto-switch is not the lock and must not
 * steal the Input tab.
 * A close that is already a CompoundCurve shell is left as
 * {@code COMPOUNDCURVE}; it is never linearized to {@code POLYGON} or
 * a chord ring.
 */
public class CurvePolygonTool extends AbstractStreamDrawTool {

  /** Visible status on Escape. Exact text, including the period. */
  static final String CANCELLED_STATUS = "CurvePolygon cancelled.";

  /** Click-start / double-click commit clears a prior Escape. */
  static final String COMMIT_CLEARS_STATUS = "";

  private static CurvePolygonTool singleton = null;

  private boolean cancelling = false;
  private boolean pendingDoubleClickFinish = false;
  private boolean hasLastPress = false;
  private int lastPressX;
  private int lastPressY;

  public static CurvePolygonTool getInstance() {
    if (singleton == null)
      singleton = new CurvePolygonTool();
    return singleton;
  }

  private CurvePolygonTool() {
  }

  @Override
  protected int getGeometryType() {
    return GeometryType.CURVEPOLYGON;
  }

  @Override
  boolean isStreamAddOnDrag() {
    return false;
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
    resetGestureFlags();
    super.deactivate();
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (isCancelKey(e.getKeyCode())) {
      e.consume();
      cancelInProgress();
    }
  }

  /**
   * Click-start (first click on the start vertex, after three or more
   * captured points) auto-closes and commits. A mid-gesture click that
   * is not the start vertex only adds a point: after Clear A, clicks 1
   * and 2 stay in-progress and click 3 does not finish. Two captured
   * points already close to a 3-point ring, so a later click must not
   * be treated as start-commit. Click-start is never cancel.
   */
  @Override
  public void mousePressed(MouseEvent e) {
    if (panel() != null) {
      panel().requestFocusInWindow();
    }
    boolean trueDoubleClick = e.getClickCount() >= 2 && isNearLastPress(e);
    if (e.getClickCount() == 1
        && capturedCount() >= 3
        && isClickOnStartVertex(e)
        && canCommitCurrent()) {
      pendingDoubleClickFinish = false;
      try {
        finishGesture();
      } catch (Exception ignored) {
        // Click-start is a commit. Never emit cancel status here.
      }
      return;
    }
    super.mousePressed(e);
    if (e.getClickCount() >= 2 && !trueDoubleClick) {
      add(toModelSnapped(e.getPoint()));
    }
    if (e.getClickCount() == 1 || !trueDoubleClick) {
      lastPressX = e.getX();
      lastPressY = e.getY();
      hasLastPress = true;
    }
    pendingDoubleClickFinish = trueDoubleClick;
  }

  /**
   * Parent {@link LineBandTool} finishes on any {@code clickCount == 2},
   * including a third vertex clicked inside the OS multi-click interval.
   * Only a true same-spot double-click finishes.
   */
  @Override
  protected boolean isFinishingRelease(MouseEvent e) {
    return pendingDoubleClickFinish && e.getClickCount() >= 2;
  }

  @Override
  protected void bandFinished() throws Exception {
    try {
      if (cancelling) {
        return;
      }
      clearStatusOnCommit();
      if (panel() != null && panel().getGeomModel() != null) {
        panel().getGeomModel().setGeometryType(getGeometryType());
      }
      List<Coordinate[]> pieces = closeShellPieces(copyCoords());
      if (pieces == null) return;

      GeometryCombiner creator =
          new GeometryCombiner(JTSTestBuilder.getGeometryFactory());
      Geometry orig = geomModel().getGeometry();
      Geometry next = creator.addCurvePolygon(
          orig, pieces.toArray(new Coordinate[0][]));
      if (next == orig || next == null) {
        return;
      }
      geomModel().setGeometry(next);
      if (panel() != null) {
        panel().updateGeom();
      }
    } finally {
      resetGestureFlags();
    }
  }

  /**
   * Auto-close an in-progress shell. Double-click anywhere or click-start
   * appends the start vertex when needed. Three captured points close to
   * {@code (A, B, C, A)} — a 4-control circumcircle; the complementary
   * arc is implicit (no extra mid). Other even leftovers still get an
   * explicit complementary-arc control. Returns {@code null} when there
   * is no shell to commit.
   */
  static List<Coordinate> closeCircularShell(List<Coordinate> input) {
    if (input == null || input.size() < 2) {
      return null;
    }
    List<Coordinate> coords = new ArrayList<Coordinate>(input);
    Coordinate start = coords.get(0);
    if (!start.equals2D(coords.get(coords.size() - 1))) {
      coords.add(new Coordinate(start));
    }
    if (coords.size() < 3) {
      return null;
    }
    // (A, B, C, A): three-click circle. Do not invent a 5th control.
    if (coords.size() == 4) {
      return coords;
    }
    if (coords.size() % 2 == 0) {
      Coordinate mid = circularCloseControl(coords);
      if (mid == null) {
        return null;
      }
      coords.add(coords.size() - 1, mid);
    }
    return coords;
  }

  /**
   * Finish pieces for a CurvePolygon shell.
   * Five or more odd unclosed controls close the rubber-band line to
   * start as a LineString member (no complementary-arc invention).
   * One-arc (2–3 points) still uses {@link #closeCircularShell}.
   */
  static List<Coordinate[]> closeShellPieces(List<Coordinate> input) {
    if (input == null || input.size() < 2) {
      return null;
    }
    Coordinate start = input.get(0);
    Coordinate last = input.get(input.size() - 1);
    boolean closed = start.equals2D(last);
    if (!closed && input.size() >= 5 && input.size() % 2 == 1) {
      List<Coordinate[]> pieces = new ArrayList<Coordinate[]>();
      pieces.add(input.toArray(new Coordinate[0]));
      pieces.add(new Coordinate[] { new Coordinate(last), new Coordinate(start) });
      return pieces;
    }
    List<Coordinate> shell = closeCircularShell(input);
    if (shell == null) {
      return null;
    }
    List<Coordinate[]> pieces = new ArrayList<Coordinate[]>();
    pieces.add(shell.toArray(new Coordinate[0]));
    return pieces;
  }

  /**
   * Closing CircularString control: opposite the last interior point
   * on its circumcircle with the open end. Not the chord midpoint.
   */
  static Coordinate circularCloseControl(List<Coordinate> closedEven) {
    int n = closedEven.size();
    Coordinate from = closedEven.get(n - 2);
    Coordinate to = closedEven.get(n - 1);
    for (int i = n - 3; i >= 0; i--) {
      Coordinate mid = complementaryArcMid(from, closedEven.get(i), to);
      if (mid != null) {
        return mid;
      }
    }
    return diameterSemicircleMid(from, to);
  }

  private static Coordinate complementaryArcMid(Coordinate from, Coordinate hint,
      Coordinate to) {
    if (Orientation.index(from, hint, to) == Orientation.COLLINEAR) {
      return null;
    }
    Coordinate center = Triangle.circumcentre(from, hint, to);
    if (center == null
        || !Double.isFinite(center.x)
        || !Double.isFinite(center.y)) {
      return null;
    }
    Coordinate mid = new Coordinate(
        2.0 * center.x - hint.x,
        2.0 * center.y - hint.y);
    if (!Double.isFinite(mid.x) || !Double.isFinite(mid.y)
        || mid.equals2D(from) || mid.equals2D(to)) {
      return null;
    }
    return mid;
  }

  /** Thales semicircle on {@code from..to} when every hint is colinear. */
  private static Coordinate diameterSemicircleMid(Coordinate from, Coordinate to) {
    double dx = to.x - from.x;
    double dy = to.y - from.y;
    if (dx == 0.0 && dy == 0.0) {
      return null;
    }
    return new Coordinate((from.x + to.x - dy) / 2.0, (from.y + to.y + dx) / 2.0);
  }

  /**
   * True when a click on the start vertex should commit on this press.
   * Needs three or more captured points so a third click after two
   * rubber-band points cannot finish just because
   * {@link #closeCircularShell} already forms a ring. Not a cancel.
   */
  static boolean firstStartClickCommits(List<Coordinate> coords, Coordinate click,
      double tolerance) {
    return coords != null
        && coords.size() >= 3
        && isStartVertexClick(coords, click, tolerance)
        && closeCircularShell(coords) != null;
  }

  /**
   * Commit is only a true same-spot double-click, or click-start after
   * three or more points. A mid-gesture click (including click 3 after
   * Clear A) is neither.
   */
  static boolean isFinishClick(int capturedCount, boolean onStartVertex,
      boolean trueDoubleClick) {
    if (trueDoubleClick) {
      return capturedCount >= 2;
    }
    return onStartVertex && capturedCount >= 3;
  }

  /** Only Escape may cancel. */
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

  /**
   * Cancel writes the Case/PM strip only ({@code setStatus}).
   * It does not call {@code displayInfo}. If Log is ever also written,
   * it must be {@code displayInfo(s, false)} so the tab does not switch.
   */
  static boolean cancelCallsDisplayInfo() {
    return false;
  }

  static boolean isSameViewClick(int x0, int y0, int x1, int y1, int slopPx) {
    int dx = x0 - x1;
    int dy = y0 - y1;
    return dx * dx + dy * dy <= slopPx * slopPx;
  }

  static boolean isStartVertexClick(List<Coordinate> coords, Coordinate click,
      double tolerance) {
    if (coords == null || coords.size() < 2 || click == null) {
      return false;
    }
    return coords.get(0).distance(click) <= tolerance;
  }

  private boolean isClickOnStartVertex(MouseEvent e) {
    List<Coordinate> coords = copyCoords();
    if (coords.size() < 2) {
      return false;
    }
    Coordinate start = coords.get(0);
    Point2D startView = toView(start);
    double dx = e.getX() - startView.getX();
    double dy = e.getY() - startView.getY();
    double slop = AppConstants.TOLERANCE_PIXELS;
    return dx * dx + dy * dy <= slop * slop;
  }

  private boolean canCommitCurrent() {
    return capturedCount() >= 3 && closeCircularShell(copyCoords()) != null;
  }

  private int capturedCount() {
    return getCoordinates().size();
  }

  private boolean isNearLastPress(MouseEvent e) {
    return hasLastPress && isSameViewClick(
        lastPressX, lastPressY, e.getX(), e.getY(), AppConstants.TOLERANCE_PIXELS);
  }

  private List<Coordinate> copyCoords() {
    List<Coordinate> coords = new ArrayList<Coordinate>();
    for (Object o : getCoordinates()) {
      coords.add((Coordinate) o);
    }
    return coords;
  }

  /**
   * Escape is the only caller. Click-start and double-click commit
   * must never reach this. Writes {@link #CANCELLED_STATUS} on the
   * Case/PM strip only — never {@code displayInfo} / {@code showInfoTab}.
   */
  private void cancelInProgress() {
    if (capturedCount() == 0) {
      return;
    }
    showCancelled();
    cancelling = true;
    try {
      finishGesture();
    } catch (Exception ignored) {
      // Status already written on Escape. Do not emit it again.
    } finally {
      cancelling = false;
      resetGestureFlags();
    }
  }

  /**
   * Always-on Case/PM strip via {@code setStatus}. Do not SIGN a Log
   * tab switch. Do not call {@code displayInfo} or {@code showInfoTab}.
   * Only Escape writes {@link #CANCELLED_STATUS}.
   */
  private void showCancelled() {
    if (!JTSTestBuilderFrame.isRunning()) {
      return;
    }
    JTSTestBuilder.controller().setStatus(CANCELLED_STATUS);
  }

  /** Click-start and double-click must not leave a prior Escape on the strip. */
  private void clearStatusOnCommit() {
    if (!JTSTestBuilderFrame.isRunning()) {
      return;
    }
    JTSTestBuilder.controller().setStatus(COMMIT_CLEARS_STATUS);
  }

  private void resetGestureFlags() {
    pendingDoubleClickFinish = false;
    hasLastPress = false;
  }

  @Override
  protected Shape getShape() {
    List<Coordinate> captured = new ArrayList<Coordinate>();
    for (Object o : getCoordinates()) captured.add((Coordinate) o);
    if (captured.isEmpty()) return null;
    if (tentativeCoordinate != null) captured.add(tentativeCoordinate);
    int n = captured.size();

    int arcPts = (n >= 3) ? (n % 2 == 1 ? n : n - 1) : 0;

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

    int trailingStart = (arcPts >= 3) ? arcPts : 1;
    for (int i = trailingStart; i < n; i++) {
      Point2D v = toView(captured.get(i));
      path.lineTo((float) v.getX(), (float) v.getY());
    }

    if (n >= 3) {
      Point2D last = toView(captured.get(n - 1));
      path.moveTo((float) last.getX(), (float) last.getY());
      path.lineTo((float) first.getX(), (float) first.getY());
      path.closePath();
    }

    drawVertices(path, captured);
    return path;
  }

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
