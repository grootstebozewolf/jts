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
import org.locationtech.jts.geom.Triangle;
import org.locationtech.jtstest.testbuilder.AppConstants;
import org.locationtech.jtstest.testbuilder.GeometryEditPanel;
import org.locationtech.jtstest.testbuilder.JTSTestBuilder;
import org.locationtech.jtstest.testbuilder.JTSTestBuilderFrame;
import org.locationtech.jtstest.testbuilder.model.GeometryType;

/**
 * Stream-style mouse-draw tool for an ISO/IEC 13249-3 SQL/MM
 * {@link org.locationtech.jts.geom.curve.CurvePolygon} with an exterior
 * shell only (no holes this slice).
 *
 * <p>Double-click anywhere, or a click on the start vertex, auto-closes
 * the in-progress shell and commits {@code CURVEPOLYGON (CIRCULARSTRING …)}.
 * This tool builds a CircularString shell only — not a mixed-shell
 * CompoundCurve editor. A close that is already a CompoundCurve shell
 * is left as {@code COMPOUNDCURVE}; it is never linearized to
 * {@code POLYGON} or a chord ring. Escape cancels with
 * {@link #CANCELLED_STATUS} on the bottom status bar.
 */
public class CurvePolygonTool extends AbstractStreamDrawTool {

  /** Visible status on Escape. Exact text, including the period. */
  static final String CANCELLED_STATUS = "CurvePolygon cancelled.";

  private static CurvePolygonTool singleton = null;

  private boolean cancelling = false;

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
    super.deactivate();
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
      cancelInProgress();
    }
  }

  /**
   * The first click on the start vertex auto-closes and commits, same
   * as a double-click anywhere. Click-start is never cancel: Escape
   * is the only cancel path. Hit-test uses the visible vertex (view
   * pixels), not a grid-snapped model point that can miss.
   */
  @Override
  public void mousePressed(MouseEvent e) {
    if (panel() != null) {
      panel().requestFocusInWindow();
    }
    if (e.getClickCount() != 1) {
      super.mousePressed(e);
      return;
    }
    try {
      if (isClickOnStartVertex(e) && canCommitCurrent()) {
        finishGesture();
        return;
      }
      super.mousePressed(e);
    } catch (Exception ex) {
      super.mousePressed(e);
    }
  }

  @Override
  protected void bandFinished() throws Exception {
    if (cancelling) {
      showCancelled();
      return;
    }
    if (panel() != null && panel().getGeomModel() != null) {
      panel().getGeomModel().setGeometryType(getGeometryType());
    }
    List<Coordinate> shell = closeCircularShell(copyCoords());
    if (shell == null) return;

    geomModel().addComponent(shell);
    if (panel() != null) {
      panel().updateGeom();
    }
  }

  /**
   * Auto-close an in-progress shell so it is a valid ISO/IEC 13249-3
   * CircularString ring: closed, odd count ≥ 3. Any finish (double-click
   * anywhere or click-start) appends the start vertex when needed. An
   * even leftover after that gets the complementary-arc control on the
   * last drawn circumcircle — a real closing arc, not a chord ring.
   * Returns {@code null} when there is no shell to commit.
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
   * True when a click on the start vertex should commit on this press
   * (enough points for {@link #closeCircularShell}). Not a cancel.
   */
  static boolean firstStartClickCommits(List<Coordinate> coords, Coordinate click,
      double tolerance) {
    return isStartVertexClick(coords, click, tolerance)
        && closeCircularShell(coords) != null;
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
    return closeCircularShell(copyCoords()) != null;
  }

  private List<Coordinate> copyCoords() {
    List<Coordinate> coords = new ArrayList<Coordinate>();
    for (Object o : getCoordinates()) {
      coords.add((Coordinate) o);
    }
    return coords;
  }

  private void cancelInProgress() {
    cancelling = true;
    try {
      finishGesture();
    } catch (Exception ignored) {
      showCancelled();
    } finally {
      cancelling = false;
    }
  }

  private void showCancelled() {
    if (!JTSTestBuilderFrame.isRunning()) {
      return;
    }
    JTSTestBuilder.controller().setStatus(CANCELLED_STATUS);
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
