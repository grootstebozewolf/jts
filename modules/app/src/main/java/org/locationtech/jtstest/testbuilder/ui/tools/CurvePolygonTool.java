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

import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.awt.curve.CircularArcRenderer;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jtstest.testbuilder.GeometryEditPanel;
import org.locationtech.jtstest.testbuilder.JTSTestBuilder;
import org.locationtech.jtstest.testbuilder.JTSTestBuilderFrame;
import org.locationtech.jtstest.testbuilder.model.GeometryType;

/**
 * Stream-style mouse-draw tool for an ISO/IEC 13249-3 SQL/MM
 * {@link org.locationtech.jts.geom.curve.CurvePolygon} with an exterior
 * shell only (no holes this slice).
 *
 * <p>A non-closing double-click, or a click on the start vertex, auto-closes
 * the in-progress shell and commits {@code CURVEPOLYGON (CIRCULARSTRING …)}.
 * An even leftover after close gets a chord-midpoint control so the
 * CircularString count stays odd — the tool never emits a linearized
 * {@code POLYGON} and never drops the in-progress geom with no message.
 * Escape cancels with {@link #CANCELLED_STATUS}.
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
   * Click on the start vertex auto-closes and commits, same as a
   * non-closing double-click. The start point is not added again;
   * {@link #closeCircularShell} appends it.
   */
  @Override
  public void mousePressed(MouseEvent e) {
    if (panel() != null) {
      panel().requestFocusInWindow();
    }
    if (e.getClickCount() == 1) {
      try {
        if (isClickOnStart(toModelSnapped(e.getPoint()))) {
          finishGesture();
          return;
        }
      } catch (Exception ignored) {
        return;
      }
    }
    super.mousePressed(e);
  }

  @Override
  protected void bandFinished() throws Exception {
    if (cancelling) {
      showCancelled();
      return;
    }
    if (panel().getModel() == null) return;
    panel().getGeomModel().setGeometryType(getGeometryType());

    List<Coordinate> coords = new ArrayList<Coordinate>();
    for (Object o : getCoordinates()) {
      coords.add((Coordinate) o);
    }
    List<Coordinate> shell = closeCircularShell(coords);
    if (shell == null) return;

    geomModel().addComponent(shell);
    panel().updateGeom();
  }

  /**
   * Auto-close an in-progress shell so it is a valid ISO/IEC 13249-3
   * CircularString ring: closed, odd count ≥ 3. A non-closing finish
   * appends the start vertex. An even leftover after that inserts a
   * chord-midpoint control (straight closing arc, matching the preview
   * close line) so the count stays odd. Returns {@code null} when there
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
    if (coords.size() % 2 == 0) {
      Coordinate prev = coords.get(coords.size() - 2);
      Coordinate last = coords.get(coords.size() - 1);
      Coordinate mid = new Coordinate(
          (prev.x + last.x) / 2.0,
          (prev.y + last.y) / 2.0);
      coords.add(coords.size() - 1, mid);
    }
    return coords;
  }

  private boolean isClickOnStart(Coordinate click) {
    List coords = getCoordinates();
    if (coords.size() < 2 || click == null) {
      return false;
    }
    Coordinate start = (Coordinate) coords.get(0);
    return start.distance(click) <= getModelSnapTolerance();
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
    JTSTestBuilder.controller().displayInfo(CANCELLED_STATUS, true);
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
